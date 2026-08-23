package cn.gtnh.ae2wtx.content.wireless;

import java.util.EnumSet;
import java.util.Iterator;
import java.util.Set;
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
import appeng.api.networking.IGridMultiblock;
import appeng.api.networking.IGridNode;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.api.networking.events.MENetworkPowerIdleChange;
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
        if (worldObj == null || worldObj.isRemote || isInvalid() || beingRemoved) {
            // NOTE: rv3 forbids createGridNode() on the client ("Grid features
            // are server side only") - never build a client-side grid here.
            return;
        }
        // Forge's dormant chunk cache can resume the same TileEntity instance
        // without calling validate(). A temporary unload must therefore be
        // recoverable from updateEntity() itself.
        if (chunkUnloading) {
            chunkUnloading = false;
            firstTickDone = false;
        }
        if (node == null) {
            node = AEApi.instance().createGridNode(this);
            markChannelCountDirty();
        }
        // Player identity participates in AE2's security check. It must be set
        // before refreshLabel can create the wireless connection, before the
        // manual physical connection scan, and before updateState discovers
        // neighbors on its own.
        applyNodeIdentity();
        if (!firstTickDone) {
            firstTickDone = true;
            refreshLabel(true);
            // immediate first neighbor scan so a freshly placed transceiver
            // connects to adjacent cable without waiting for the 5-tick loop
            localConnTick = 4;
            maintainLocalConnections();
        }
        if (node != null) {
            // rv3 Grid.update() never calls GridNode.updateState(); periodic
            // re-runs are what join/keep this node in the local ME grid.
            if (!isLocalConnectionRetryDeferred()) {
                applyNodeIdentity();
                node.updateState();
                if (!isLocalConnectionRetryDeferred()) {
                    maintainLocalConnections();
                }
            }
        }
        labelLink.updateStatus();
        updateNetworkChannelStats();
        updateVisualState();
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
    private long localConnectionRetryNotBeforeTick = Long.MIN_VALUE;
    private long lastManualConnectionWarningTick = Long.MIN_VALUE;

    private boolean isLocalConnectionRetryDeferred() {
        return worldObj != null && worldObj.getTotalWorldTime() < localConnectionRetryNotBeforeTick;
    }

    /**
     * Actively create grid connections to adjacent IGridHosts (see plain TE).
     * Neighboring transceivers are deliberately SKIPPED: two transceivers
     * placed next to each other must never bridge channels directly - they may
     * only communicate through their label network (virtual node), so labels
     * cannot bleed into each other.
     */
    private void maintainLocalConnections() {
        if (node == null || beingRemoved || isInvalid() || isLocalConnectionRetryDeferred()) {
            return;
        }
        if (++localConnTick < 5) {
            return;
        }
        localConnTick = 0;
        applyNodeIdentity();
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
                long now = worldObj.getTotalWorldTime();
                localConnectionRetryNotBeforeTick = Math.max(localConnectionRetryNotBeforeTick, now + 100L);
                if (lastManualConnectionWarningTick == Long.MIN_VALUE
                    || now - lastManualConnectionWarningTick >= 1200L) {
                    lastManualConnectionWarningTick = now;
                    AE2Wtx.LOG.warn("WTX manual connect denied/failed to " + te.getClass().getSimpleName() + " at "
                        + (xCoord + dir.offsetX) + "," + (yCoord + dir.offsetY) + "," + (zCoord + dir.offsetZ)
                        + "; retrying in 100 ticks: " + t);
                }
                break;
            }
        }
    }

    private boolean chunkUnloading = false;

    @Override
    public void validate() {
        super.validate();
        this.chunkUnloading = false;
        this.beingRemoved = false;
        this.firstTickDone = false;
        markChannelCountDirty();
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
        if (unregisterEndpoint && beingRemoved) {
            return;
        }
        if (unregisterEndpoint) {
            beingRemoved = true;
            chunkUnloading = false;
        }
        labelLink.onUnloadOrRemove();
        LabelNetworkRegistry reg = LabelNetworkRegistry.get(worldObj);
        if (reg != null) {
            if (unregisterEndpoint) {
                reg.cleanupStalePendingClear(worldObj, xCoord, yCoord, zCoord);
                reg.unregister(this);
            } else {
                reg.suspend(this);
            }
        }
        if (node != null) {
            try {
                node.destroy();
            } catch (Exception ignored) {
            }
            node = null;
        }
        firstTickDone = false;
        markChannelCountDirty();
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
    public void securityBreak() {
        long now = worldObj == null ? 0L : worldObj.getTotalWorldTime();
        long retryAt = now + 100L;
        localConnectionRetryNotBeforeTick = Math.max(localConnectionRetryNotBeforeTick, retryAt);
    }

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
    public void onGridNotification(GridNotification notification) {
        if (notification == GridNotification.ConnectionsChanged) {
            markChannelCountDirty();
        }
    }

    @Override
    public void setNetworkStatus(IGrid grid, int channelsInUse) {
        // channelsInUse is AE2's allocated count, not demand: a starved 33rd
        // device contributes zero. Use this callback only as invalidation.
        markChannelCountDirty();
    }

    @Override
    public EnumSet<ForgeDirection> getConnectableSides() {
        return EnumSet.allOf(ForgeDirection.class);
    }

    @Override
    public IGridHost getMachine() {
        return this;
    }

    @Override
    public void gridChanged() {
        markChannelCountDirty();
    }

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
        return beingRemoved || chunkUnloading || worldObj == null || isInvalid();
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

    /** Refresh AE2's cached idle drain after the runtime-safe power setting changes. */
    public void refreshIdlePowerUsage() {
        if (worldObj == null || worldObj.isRemote || node == null || beingRemoved || isInvalid()) {
            return;
        }
        IGrid grid = node.getGrid();
        if (grid != null) {
            grid.postEvent(new MENetworkPowerIdleChange(node));
        }
    }

    /**
     * Device count: traverses the real AE2 grid connections (cables and machines)
     * starting from this transceiver's local node. Every connected node carrying
     * REQUIRE_CHANNEL counts as one channel consumer; only AE2 IGridMultiblock
     * node sets are collapsed to their shared single-channel demand. Devices that could not
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
        Set<IGridNode> visitedNodes = new java.util.HashSet<>();
        Set<IGridNode> countedConsumers = new java.util.HashSet<>();
        java.util.ArrayDeque<IGridNode> queue = new java.util.ArrayDeque<>();
        int consumers = 0;

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
                IGridBlock gridBlock = other.getGridBlock();
                IGridHost machine = other.getMachine();
                if (gridBlock instanceof LabeledWirelessTransceiverBlockEntity
                    || machine instanceof LabeledWirelessTransceiverBlockEntity
                    || gridBlock instanceof LabelNetworkRegistry.VirtualLabelNodeHost
                    || machine instanceof LabelNetworkRegistry.VirtualLabelNodeHost) {
                    continue;
                }
                // ME Controller acts as a channel source / network boundary: stop traversal
                // TileCreativeEnergyController extends TileController in the locked AE2 build.
                if (isControllerBoundary(machine)) {
                    continue;
                }
                // Count demand rather than allocated channels, so missing-channel
                // nodes are retained and an overloaded branch can show 33/32.
                if (other.hasFlag(GridFlags.REQUIRE_CHANNEL)) {
                    if (addChannelConsumer(other, countedConsumers)) {
                        consumers++;
                    }
                }
                queue.add(other);
            }
        }
        return consumers;
    }

    /**
     * Avoid a hard compile-time reference to TileController: the GTNH AE2 dev
     * jar exposes optional RotaryCraft interfaces on that class, while those
     * interfaces are not part of this mod's compile classpath. Matching the
     * class hierarchy by its stable AE2 name also covers controller subclasses.
     */
    private static boolean isControllerBoundary(IGridHost machine) {
        if (machine == null) {
            return false;
        }
        for (Class<?> type = machine.getClass(); type != null; type = type.getSuperclass()) {
            if ("appeng.tile.networking.TileController".equals(type.getName())) {
                return true;
            }
        }
        return false;
    }

    /** Add one AE2 demand unit, collapsing the complete node set of a multiblock. */
    private static boolean addChannelConsumer(IGridNode consumer, Set<IGridNode> countedConsumers) {
        IGridBlock gridBlock = consumer.getGridBlock();
        if (!consumer.hasFlag(GridFlags.MULTIBLOCK) || !(gridBlock instanceof IGridMultiblock)) {
            return countedConsumers.add(consumer);
        }

        Set<IGridNode> members = new java.util.HashSet<>();
        members.add(consumer);
        try {
            Iterator<IGridNode> iterator = ((IGridMultiblock) gridBlock).getMultiblockNodes();
            while (iterator != null && iterator.hasNext()) {
                IGridNode member = iterator.next();
                if (member != null) {
                    members.add(member);
                }
            }
        } catch (RuntimeException ignored) {
            // A temporarily rebuilding multiblock still counts via the node we saw.
        }

        boolean alreadyCounted = false;
        for (IGridNode member : members) {
            if (countedConsumers.contains(member)) {
                alreadyCounted = true;
                break;
            }
        }
        countedConsumers.addAll(members);
        return !alreadyCounted;
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

    public LabelNetworkRegistry.RegistrationResult applyLabel(String rawLabel) {
        LabelNetworkRegistry reg = LabelNetworkRegistry.get(worldObj);
        if (reg == null) {
            return LabelNetworkRegistry.RegistrationResult.createFailed();
        }
        reg.cleanupStalePendingClear(worldObj, xCoord, yCoord, zCoord);
        LabelNetworkRegistry.RegistrationResult result = reg.registerForPlayer(worldObj, rawLabel, placerId, this);
        if (!result.succeeded()) {
            return result;
        }
        LabelNetworkRegistry.LabelNetwork network = result.network;
        this.labelForDisplay = LabelNetworkRegistry.normalizeLabel(rawLabel);
        this.frequency = network.channel();
        markChannelCountDirty();
        this.labelLink.setTarget(network);
        updateVisualState();
        markDirty();
        syncToClients();
        return result;
    }

    public void clearLabel() {
        LabelNetworkRegistry reg = LabelNetworkRegistry.get(worldObj);
        if (reg != null) {
            reg.cleanupStalePendingClear(worldObj, xCoord, yCoord, zCoord);
            reg.unregister(this);
        }
        this.labelForDisplay = null;
        this.frequency = 0L;
        markChannelCountDirty();
        this.labelLink.clearTarget();
        updateVisualState();
        markDirty();
        syncToClients();
    }

    public void clearLabelAfterNetworkDeletion() {
        this.labelForDisplay = null;
        this.frequency = 0L;
        markChannelCountDirty();
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
            LabelNetworkRegistry.LabelNetwork saved = reg
                .getNetworkForEndpoint(worldObj.provider.dimensionId, xCoord, yCoord, zCoord, placerId);
            if (saved == null) {
                this.frequency = 0L;
                markChannelCountDirty();
                this.labelLink.clearTarget();
                updateVisualState();
                return;
            }
            this.labelForDisplay = saved.label();
            AE2Wtx.LOG.warn(
                "ae2wtx: restored missing tile label '{}' from the saved endpoint registry at {}:{},{},{}",
                labelForDisplay,
                worldObj.provider.dimensionId,
                xCoord,
                yCoord,
                zCoord);
        }
        if (reg.checkAndConsumePendingClear(worldObj, xCoord, yCoord, zCoord, labelForDisplay, placerId)) {
            clearLabelAfterNetworkDeletion();
            return;
        }
        LabelNetworkRegistry.LabelNetwork network = ensureRegister
            ? reg.register(worldObj, labelForDisplay, placerId, this)
            : reg.getNetwork(worldObj, labelForDisplay, placerId);
        if (network == null) {
            this.frequency = 0L;
            markChannelCountDirty();
            this.labelLink.clearTarget();
        } else {
            network.ensureVirtualNode(worldObj);
            this.frequency = network.channel();
            markChannelCountDirty();
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
    private boolean channelCountDirty = false;
    private long channelCountNotBeforeTick = Long.MIN_VALUE;
    private long lastChannelCountTick = Long.MIN_VALUE;
    /** Server-side physical-demand snapshot; read by Waila/GUI/band stats. */
    private int serverUsedCache = 0;

    /**
     * Invalidate the demand snapshot without traversing the grid inside an AE2
     * callback. The first event opens a two-tick debounce window; subsequent
     * events in the same burst do not postpone it indefinitely.
     */
    private void markChannelCountDirty() {
        if (channelCountDirty) {
            return;
        }
        channelCountDirty = true;
        long now = worldObj == null ? 0L : worldObj.getTotalWorldTime();
        channelCountNotBeforeTick = now + 2L;
    }

    private int channelRefreshPhase() {
        int hash = xCoord * 73428767 ^ yCoord * 912931 ^ zCoord * 438289;
        return Math.floorMod(hash, 10);
    }

    private void updateVisualState() {
        if (worldObj == null || worldObj.isRemote || beingRemoved || isInvalid()) {
            return;
        }
        boolean linkUp = labelLink != null && labelLink.isConnected();
        long now = worldObj.getTotalWorldTime();
        boolean dirtyReady = channelCountDirty && now >= channelCountNotBeforeTick;
        boolean fallbackDue = !channelCountDirty && Math.floorMod(now + channelRefreshPhase(), 10L) == 0L;
        boolean refreshSnapshot = lastChannelCountTick != now && (dirtyReady || fallbackDue);

        boolean syncNeeded = linkUp != onlineSync;
        if (syncNeeded) {
            onlineSync = linkUp;
        }

        if (refreshSnapshot) {
            lastChannelCountTick = now;
            channelCountDirty = false;
            int previousUsed = serverUsedCache;
            serverUsedCache = node == null ? 0 : countDeviceConsumers();
            int max = 32;
            if (node instanceof appeng.me.GridNode) {
                max = ((appeng.me.GridNode) node).getMaxChannels();
            }
            if (serverUsedCache != channelsSync || max != maxChannelsSync) {
                channelsSync = serverUsedCache;
                maxChannelsSync = max;
                syncNeeded = true;
            }
            if (serverUsedCache != previousUsed && labelForDisplay != null && !labelForDisplay.isEmpty()) {
                LabelNetworkRegistry registry = LabelNetworkRegistry.get(worldObj);
                if (registry != null) {
                    registry.invalidateStatsFor(this);
                }
            }
        }

        if (syncNeeded) {
            syncToClients();
        }
        if (!refreshSnapshot) {
            return;
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
