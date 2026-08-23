package cn.gtnh.ae2wtx.content.wireless;

import java.util.EnumSet;
import java.util.UUID;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import cn.gtnh.ae2wtx.AE2Wtx;
import cn.gtnh.ae2wtx.config.ModConfig;
import cn.gtnh.ae2wtx.init.ModBlocks;
import cn.gtnh.ae2wtx.wireless.IWirelessEndpoint;
import cn.gtnh.ae2wtx.wireless.LabelLink;
import cn.gtnh.ae2wtx.wireless.LabelNetworkRegistry;
import appeng.api.AEApi;
import appeng.api.networking.GridFlags;
import appeng.api.networking.GridNotification;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridBlock;
import appeng.api.networking.IGridConnection;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.api.util.AECableType;
import appeng.api.util.AEColor;
import appeng.api.util.DimensionalCoord;

/**
 * Labeled wireless transceiver tile entity: connects to a label network
 * (a virtual node) instead of using numeric frequencies.
 */
public class LabeledWirelessTransceiverBlockEntity extends TileEntity
    implements IGridHost, IGridBlock, IWirelessEndpoint {

    private IGridNode node;

    private long frequency = 0L;
    private String labelForDisplay;
    private boolean locked = false;
    private boolean beingRemoved = false;

    private UUID placerId;
    private String placerName;

    private boolean firstTickDone = false;

    private final LabelLink labelLink = new LabelLink(this);

    /* ===================== node lifecycle ===================== */

    @Override
    public void updateEntity() {
        if (worldObj == null || worldObj.isRemote) {
            // NOTE: rv3 forbids createGridNode() on the client ("Grid features
            // are server side only") - never build a client-side grid here.
            return;
        }
        if (!firstTickDone) {
            firstTickDone = true;
            if (node == null) {
                node = AEApi.instance().createGridNode(this);
            }
            refreshLabel(true);
            // immediate first neighbor scan so a freshly placed transceiver
            // connects to adjacent cable without waiting for the 5-tick loop
            maintainLocalConnections();
        }
        if (node != null) {
            // rv3 Grid.update() never calls GridNode.updateState(); periodic
            // re-runs are what join/keep this node in the local ME grid.
            node.updateState();
            syncSecurityKey();
            applyNodeIdentity();
            maintainLocalConnections();
        }
        labelLink.updateStatus();
        updateNetworkChannelStats();
        updateVisualState();
    }

    private long lastSecurityKey = Long.MIN_VALUE;
    private int secKeyTick = 0;

    /** Join the grid's security realm so GridConnection's securityCheck passes. */
    private void syncSecurityKey() {
        if (node == null) {
            return;
        }
        // grid security keys change rarely - re-check every 10 ticks
        if (++secKeyTick < 10) {
            return;
        }
        secKeyTick = 0;
        IGrid grid = node.getGrid();
        if (grid == null) {
            return;
        }
        try {
            appeng.me.cache.SecurityCache sc = (appeng.me.cache.SecurityCache) grid
                .getCache(appeng.api.networking.security.ISecurityGrid.class);
            if (sc == null) {
                return;
            }
            long key = sc.getSecurityKey();
            if (key != lastSecurityKey) {
                lastSecurityKey = key;
                if (node instanceof appeng.me.GridNode) {
                    ((appeng.me.GridNode) node).setLastSecurityKey(key);
                    AE2Wtx.LOG.debug("LWTX security key synced: " + key + " at " + xCoord + "," + yCoord + "," + zCoord);
                }
            }
        } catch (Exception t) {
            // grid cache hiccup - retry next tick
        }
    }

    private int cachedPlayerId = -1;
    private UUID cachedPlayerIdOwner = null;

    /** Give the node the placer's AE2 player id (needed for Security Station grids). */
    private void applyNodeIdentity() {
        if (node == null || placerId == null) {
            return;
        }
        try {
            // players().getID() walks a registry every call - cache per owner
            if (cachedPlayerIdOwner == null || !cachedPlayerIdOwner.equals(placerId) || cachedPlayerId < 0) {
                com.mojang.authlib.GameProfile profile = new com.mojang.authlib.GameProfile(placerId,
                    placerName == null ? "" : placerName);
                cachedPlayerId = appeng.api.AEApi.instance().registries().players().getID(profile);
                cachedPlayerIdOwner = placerId;
            }
            if (cachedPlayerId >= 0 && node.getPlayerID() != cachedPlayerId) {
                node.setPlayerID(cachedPlayerId);
            }
        } catch (Exception t) {
            // registry hiccup - retry next tick
        }
    }

    private int localConnTick = 0;

    /**
     * Actively create grid connections to adjacent IGridHosts (see plain TE).
     * Neighboring transceivers are deliberately SKIPPED: two transceivers
     * placed next to each other must never bridge channels directly - they may
     * only communicate through their label network (virtual node), so labels
     * cannot bleed into each other.
     */
    private void maintainLocalConnections() {
        if (node == null || beingRemoved || isInvalid()) {
            return;
        }
        if (++localConnTick < 5) {
            return;
        }
        localConnTick = 0;
        for (ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS) {
            TileEntity te = worldObj.getTileEntity(xCoord + dir.offsetX, yCoord + dir.offsetY, zCoord + dir.offsetZ);
            if (!(te instanceof IGridHost) || te == this) {
                continue;
            }
            if (te instanceof LabeledWirelessTransceiverBlockEntity) {
                continue; // never direct-connect transceiver to transceiver
            }
            IGridNode other = ((IGridHost) te).getGridNode(dir.getOpposite());
            if (other == null || other == node) {
                continue;
            }
            boolean has = false;
            for (IGridConnection c : node.getConnections()) {
                if (c.a() == other || c.b() == other) {
                    has = true;
                    break;
                }
            }
            if (has) {
                continue;
            }
            try {
                // rv3 securityCheck rejects key mismatches (fresh node key = -1
                // vs loaded AE2 nodes key = 0 / security-station keys). Align
                // both nodes to the neighbor grid's key before connecting -
                // the same fix the label link uses.
                alignSecurityKey(node, other);
                // CRITICAL: the rv3 API only offers direction-less
                // createGridConnection (dir=UNKNOWN -> hasDirection()=false ->
                // GridNode.addConnection skips the ConnectionsChanged
                // notification -> cable visuals never refresh and
                // getConnectedSides() stays empty). Construct the connection
                // WITH the direction exactly like FindConnections does, so both
                // nodes get onGridNotification(ConnectionsChanged) and the
                // cable updates its connection appearance automatically.
                if (node instanceof appeng.me.GridNode && other instanceof appeng.me.GridNode) {
                    new appeng.me.GridConnection((appeng.me.GridNode) node, (appeng.me.GridNode) other, dir);
                } else {
                    AEApi.instance().createGridConnection(node, other);
                }
                AE2Wtx.LOG.debug("WTX manual connect OK: " + te.getClass().getSimpleName() + " at "
                    + (xCoord + dir.offsetX) + "," + (yCoord + dir.offsetY) + "," + (zCoord + dir.offsetZ));
            } catch (Exception t) {
                AE2Wtx.LOG.warn("WTX manual connect FAILED to " + te.getClass().getSimpleName() + " at "
                    + (xCoord + dir.offsetX) + "," + (yCoord + dir.offsetY) + "," + (zCoord + dir.offsetZ) + ": " + t);
            }
        }
    }

    /** Set both nodes to the same security key (grid key if one side has a grid). */
    private static void alignSecurityKey(IGridNode a, IGridNode b) {
        if (!(a instanceof appeng.me.GridNode) || !(b instanceof appeng.me.GridNode)) {
            return;
        }
        long key = Long.MIN_VALUE;
        IGrid g = a.getGrid() != null ? a.getGrid() : b.getGrid();
        if (g != null) {
            try {
                appeng.me.cache.SecurityCache sc = (appeng.me.cache.SecurityCache) g
                    .getCache(appeng.api.networking.security.ISecurityGrid.class);
                if (sc != null) {
                    key = sc.getSecurityKey();
                }
            } catch (Exception t) {
                // keep default
            }
        }
        if (key == Long.MIN_VALUE) {
            key = ((appeng.me.GridNode) a).getLastSecurityKey();
            if (key == -1) {
                key = ((appeng.me.GridNode) b).getLastSecurityKey();
            }
        }
        ((appeng.me.GridNode) a).setLastSecurityKey(key);
        ((appeng.me.GridNode) b).setLastSecurityKey(key);
    }

    private boolean chunkUnloading = false;

    @Override
    public void validate() {
        super.validate();
        this.chunkUnloading = false;
        this.beingRemoved = false;
    }

    @Override
    public void invalidate() {
        super.invalidate();
        if (!chunkUnloading) {
            cleanup(true);
        } else {
            cleanup(false);
        }
    }

    @Override
    public void onChunkUnload() {
        this.chunkUnloading = true;
        super.onChunkUnload();
        cleanup(false);
    }

    public void cleanupForRemoval() {
        cleanup(true);
    }

    public void cleanup(boolean unregisterEndpoint) {
        if (beingRemoved) {
            return;
        }
        beingRemoved = true;
        labelLink.onUnloadOrRemove();
        if (unregisterEndpoint) {
            LabelNetworkRegistry reg = LabelNetworkRegistry.get(worldObj);
            if (reg != null) {
                reg.cleanupStalePendingClear(worldObj, xCoord, yCoord, zCoord);
                reg.unregister(this);
            }
        }
        if (node != null) {
            try {
                node.destroy();
            } catch (Exception ignored) {
            }
            node = null;
        }
    }

    /* ===================== IGridHost ===================== */

    @Override
    public IGridNode getGridNode(ForgeDirection dir) {
        // Anti channel-bleed: when a neighbor (FindConnections or a manual
        // scan) asks for our node from the direction of another transceiver,
        // report null so the two transceivers never link directly. Cable and
        // machine neighbors always get the node.
        if (dir != null && dir != ForgeDirection.UNKNOWN && worldObj != null) {
            TileEntity te = worldObj.getTileEntity(xCoord + dir.offsetX, yCoord + dir.offsetY, zCoord + dir.offsetZ);
            if (te instanceof LabeledWirelessTransceiverBlockEntity) {
                return null;
            }
        }
        return node;
    }

    @Override
    public AECableType getCableConnectionType(ForgeDirection dir) {
        return AECableType.GLASS;
    }

    @Override
    public void securityBreak() {}

    /* ===================== IGridBlock ===================== */

    @Override
    public double getIdlePowerUsage() {
        return ModConfig.wirelessTransceiverIdlePower;
    }

    @Override
    public EnumSet<GridFlags> getFlags() {
        return EnumSet.of(GridFlags.DENSE_CAPACITY);
    }

    @Override
    public boolean isWorldAccessible() {
        // true so the AE2 Network Visualisation Tool (and FindConnections)
        // treat this as a real world node; anti channel-bleed is enforced in
        // getGridNode(dir) which withholds the node from transceiver neighbors.
        return true;
    }

    @Override
    public DimensionalCoord getLocation() {
        return new DimensionalCoord(worldObj, xCoord, yCoord, zCoord);
    }

    @Override
    public AEColor getGridColor() {
        return AEColor.Transparent;
    }

    @Override
    public void onGridNotification(GridNotification notification) {}

    @Override
    public void setNetworkStatus(IGrid grid, int channelsInUse) {}

    @Override
    public EnumSet<ForgeDirection> getConnectableSides() {
        return EnumSet.allOf(ForgeDirection.class);
    }

    @Override
    public IGridHost getMachine() {
        return this;
    }

    @Override
    public void gridChanged() {}

    @Override
    public ItemStack getMachineRepresentation() {
        return new ItemStack(ModBlocks.blockWirelessTransceiver);
    }

    /* ===================== IWirelessEndpoint ===================== */

    @Override
    public World getWorld() {
        return worldObj;
    }

    @Override
    public int getX() {
        return xCoord;
    }

    @Override
    public int getY() {
        return yCoord;
    }

    @Override
    public int getZ() {
        return zCoord;
    }

    @Override
    public IGridNode getGridNode() {
        return node;
    }

    @Override
    public boolean isEndpointRemoved() {
        return beingRemoved || worldObj == null || isInvalid();
    }

    /* ===================== label management ===================== */

    public void setPlacerId(UUID placerId, String placerName) {
        this.placerId = placerId;
        this.placerName = placerName;
        this.cachedPlayerIdOwner = null; // invalidate player-id cache
        markDirty();
        syncToClients();
    }

    public UUID getPlacerId() {
        return placerId;
    }

    public String getPlacerName() {
        return placerName;
    }

    /* ===================== lock (ported from the removed plain transceiver) ===================== */

    public boolean isLocked() {
        return locked;
    }

    public void setLocked(boolean locked) {
        if (this.locked == locked) {
            return;
        }
        this.locked = locked;
        markDirty();
        syncToClients();
    }

    public long getFrequency() {
        return frequency;
    }

    /**
     * Channel usage for Waila/GUI display (server side reads the throttled
     * cache; the cache = number of channel consumers in the local network).
     */
    public int getUsedChannelsForDisplay() {
        if (worldObj != null && worldObj.isRemote) {
            return channelsSync;
        }
        return serverUsedCache;
    }

    /**
     * Device count: traverses the real AE2 grid connections (cables and machines)
     * starting from this transceiver's local node. Every connected block/part
     * carrying REQUIRE_CHANNEL counts as ONE channel consumer. Multi-node machines
     * (e.g. Dual ME Interface) deduplicate by IGridHost. Devices that could not
     * get a channel still carry REQUIRE_CHANNEL, so over capacity naturally shows
     * as 33/32 (or more).
     *
     * Transceiver-to-transceiver links, virtual label nodes, and ME controllers
     * (boundary anchor) are not crossed, confining the count to this transceiver's
     * local branch. Un-cabled / unconnected blocks in the physical world are never
     * visited.
     */
    private int countDeviceConsumers() {
        if (node == null || beingRemoved || isInvalid()) {
            return 0;
        }
        java.util.Set<IGridNode> visitedNodes = new java.util.HashSet<>();
        java.util.Set<Object> countedDevices = new java.util.HashSet<>();
        java.util.ArrayDeque<IGridNode> queue = new java.util.ArrayDeque<>();

        visitedNodes.add(node);
        queue.add(node);

        while (!queue.isEmpty()) {
            IGridNode cur = queue.poll();
            for (IGridConnection conn : cur.getConnections()) {
                IGridNode other = conn.getOtherSide(cur);
                if (other == null || !visitedNodes.add(other)) {
                    continue;
                }
                // Never traverse across virtual label nodes or other wireless transceivers
                if (other.getGridBlock() instanceof LabeledWirelessTransceiverBlockEntity
                    || other.getGridBlock() instanceof LabelNetworkRegistry.VirtualLabelNodeHost) {
                    continue;
                }
                // ME Controller acts as a channel source / network boundary: stop traversal
                if (other.getMachine() != null && other.getMachine().getClass().getSimpleName().equals("TileController")) {
                    continue;
                }
                // Count channel consumers
                if (other.hasFlag(GridFlags.REQUIRE_CHANNEL)) {
                    IGridHost host = other.getMachine();
                    countedDevices.add(host != null ? host : other);
                }
                queue.add(other);
            }
        }
        return countedDevices.size();
    }

    public int getMaxChannelsForDisplay() {
        if (worldObj != null && worldObj.isRemote) {
            return maxChannelsSync;
        }
        if (node instanceof appeng.me.GridNode) {
            return ((appeng.me.GridNode) node).getMaxChannels();
        }
        return 32;
    }

    /** Whether the label network link is up (Waila online/offline). */
    public boolean isOnline() {
        if (worldObj != null && worldObj.isRemote) {
            return onlineSync;
        }
        return labelLink != null && labelLink.isConnected();
    }

    public String getLabelForDisplay() {
        return labelForDisplay;
    }

    /* ===================== whole-network channel stats (Waila) ===================== */

    private int networkChannelsSync = 0;
    private int onlineCountSync = 0;
    private int netStatsTick = 0;

    /** Total channels used by ALL endpoints of this label (server-side, throttled). */
    private void updateNetworkChannelStats() {
        if (worldObj == null || worldObj.isRemote || beingRemoved || isInvalid()) {
            return;
        }
        if (++netStatsTick < 20) {
            return;
        }
        netStatsTick = 0;
        int total = 0;
        int online = 0;
        if (labelForDisplay != null && !labelForDisplay.isEmpty()) {
            LabelNetworkRegistry reg = LabelNetworkRegistry.get(worldObj);
            if (reg != null) {
                LabelNetworkRegistry.LabelNetwork net = reg.getNetwork(worldObj, labelForDisplay, placerId);
                if (net != null) {
                    total = net.totalUsedChannels();
                    online = net.onlineEndpointCount();
                }
            }
        }
        if (total != networkChannelsSync || online != onlineCountSync) {
            networkChannelsSync = total;
            onlineCountSync = online;
            syncToClients();
        }
    }

    /** Total channels used by all endpoints of this label (Waila display). */
    public int getNetworkChannelsForDisplay() {
        return networkChannelsSync;
    }

    /** Number of transceivers online in this band (Waila display). */
    public int getOnlineCountForDisplay() {
        return onlineCountSync;
    }

    public void applyLabel(String rawLabel) {
        LabelNetworkRegistry reg = LabelNetworkRegistry.get(worldObj);
        if (reg == null) {
            return;
        }
        reg.cleanupStalePendingClear(worldObj, xCoord, yCoord, zCoord);
        reg.unregister(this);
        LabelNetworkRegistry.LabelNetwork network = reg.register(worldObj, rawLabel, placerId, this);
        if (network == null) {
            clearLabel();
            return;
        }
        this.labelForDisplay = LabelNetworkRegistry.normalizeLabel(rawLabel);
        this.frequency = network.channel();
        this.labelLink.setTarget(network);
        updateVisualState();
        markDirty();
        syncToClients();
    }

    public void clearLabel() {
        LabelNetworkRegistry reg = LabelNetworkRegistry.get(worldObj);
        if (reg != null) {
            reg.cleanupStalePendingClear(worldObj, xCoord, yCoord, zCoord);
            reg.unregister(this);
        }
        this.labelForDisplay = null;
        this.frequency = 0L;
        this.labelLink.clearTarget();
        updateVisualState();
        markDirty();
        syncToClients();
    }

    public void clearLabelAfterNetworkDeletion() {
        this.labelForDisplay = null;
        this.frequency = 0L;
        this.labelLink.clearTarget();
        updateVisualState();
        markDirty();
        syncToClients();
    }

    public void refreshLabel(boolean ensureRegister) {
        LabelNetworkRegistry reg = LabelNetworkRegistry.get(worldObj);
        if (reg == null) {
            return;
        }
        if (labelForDisplay == null || labelForDisplay.isEmpty()) {
            this.frequency = 0L;
            this.labelLink.clearTarget();
            updateVisualState();
            return;
        }
        if (reg.checkAndConsumePendingClear(worldObj, xCoord, yCoord, zCoord, labelForDisplay, placerId)) {
            clearLabelAfterNetworkDeletion();
            return;
        }
        LabelNetworkRegistry.LabelNetwork network = reg.getNetwork(worldObj, labelForDisplay, placerId);
        if (network == null && ensureRegister) {
            network = reg.register(worldObj, labelForDisplay, placerId, this);
        }
        if (network == null) {
            this.frequency = 0L;
            this.labelLink.clearTarget();
        } else {
            network.ensureVirtualNode(worldObj);
            this.frequency = network.channel();
            this.labelLink.setTarget(network);
        }
        updateVisualState();
        markDirty();
        syncToClients();
    }

    /* ===================== visual state (metadata 0/1 online) ===================== */

    private boolean onlineSync = false;
    private int channelsSync = 0;
    private int maxChannelsSync = 32;
    private int usedCalcTick = 0;
    /** Server-side BFS result, refreshed every 10 ticks; read by Waila/GUI/band stats. */
    private int serverUsedCache = 0;

    private void updateVisualState() {
        if (worldObj == null || worldObj.isRemote || beingRemoved || isInvalid()) {
            return;
        }
        boolean linkUp = labelLink != null && labelLink.isConnected();
        // heavy work (channel counts + grid cache lookups) runs every 10 ticks;
        // linkUp check is a few field reads, safe every tick
        if (++usedCalcTick < 10) {
            return;
        }
        usedCalcTick = 0;
        serverUsedCache = node == null ? 0 : countDeviceConsumers();
        int max = 32;
        if (node instanceof appeng.me.GridNode) {
            max = ((appeng.me.GridNode) node).getMaxChannels();
        }
        if (linkUp != onlineSync || serverUsedCache != channelsSync || max != maxChannelsSync) {
            onlineSync = linkUp;
            channelsSync = serverUsedCache;
            maxChannelsSync = max;
            syncToClients();
        }
        IGridNode n = node;
        boolean online = false;
        if (n != null && n.isActive()) {
            try {
                IGrid grid = n.getGrid();
                online = grid != null && ((IEnergyGrid) grid.getCache(IEnergyGrid.class)).isNetworkPowered();
            } catch (Exception ignored) {
                online = false;
            }
        }
        int meta = online ? 1 : 0;
        if (worldObj.getBlockMetadata(xCoord, yCoord, zCoord) != meta) {
            worldObj.setBlockMetadataWithNotify(xCoord, yCoord, zCoord, meta, 3);
            // block emits light while online - recalc lighting when the
            // emissive state flips
            worldObj.updateLightByType(net.minecraft.world.EnumSkyBlock.Block, xCoord, yCoord, zCoord);
        }
    }

    /* ===================== NBT ===================== */

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setLong("frequency", frequency);
        tag.setBoolean("online", onlineSync);
        tag.setInteger("chSync", channelsSync);
        tag.setInteger("maxCh", maxChannelsSync);
        tag.setBoolean("locked", locked);
        tag.setInteger("netCh", networkChannelsSync);
        tag.setInteger("netOn", onlineCountSync);
        if (labelForDisplay != null) {
            tag.setString("label", labelForDisplay);
        }
        if (placerId != null) {
            tag.setString("placerId", placerId.toString());
        }
        if (placerName != null) {
            tag.setString("placerName", placerName);
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        this.frequency = tag.getLong("frequency");
        this.labelForDisplay = tag.hasKey("label") ? tag.getString("label") : null;
        this.locked = tag.getBoolean("locked");
        if (tag.hasKey("placerId")) {
            this.placerId = UUID.fromString(tag.getString("placerId"));
        }
        if (tag.hasKey("placerName")) {
            this.placerName = tag.getString("placerName");
        }
    }

    /* ===================== client sync ===================== */

    /** Push current state to clients so Waila / block displays stay fresh. */
    private void syncToClients() {
        if (worldObj != null && !worldObj.isRemote && !beingRemoved && !isInvalid()) {
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
        }
    }

    @Override
    public Packet getDescriptionPacket() {
        NBTTagCompound tag = new NBTTagCompound();
        writeToNBT(tag);
        return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 1, tag);
    }

    @Override
    public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity pkt) {
        NBTTagCompound tag = pkt.func_148857_g();
        // read client-visible state only (no server-side registry side effects)
        this.frequency = tag.getLong("frequency");
        this.onlineSync = tag.getBoolean("online");
        this.channelsSync = tag.getInteger("chSync");
        this.maxChannelsSync = tag.getInteger("maxCh");
        this.locked = tag.getBoolean("locked");
        this.networkChannelsSync = tag.getInteger("netCh");
        this.onlineCountSync = tag.getInteger("netOn");
        this.labelForDisplay = tag.hasKey("label") ? tag.getString("label") : null;
        this.placerId = tag.hasKey("placerId") ? UUID.fromString(tag.getString("placerId")) : null;
        this.placerName = tag.hasKey("placerName") ? tag.getString("placerName") : null;
        if (worldObj != null) {
            worldObj.markBlockRangeForRenderUpdate(xCoord, yCoord, zCoord, xCoord, yCoord, zCoord);
        }
    }
}
