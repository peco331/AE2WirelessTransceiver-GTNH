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
import cn.gtnh.ae2wtx.wireless.WirelessMasterLink;
import cn.gtnh.ae2wtx.wireless.WirelessSlaveLink;
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
 * Wireless transceiver tile entity.
 * <ul>
 * <li>master/slave mode toggle</li>
 * <li>frequency setting</li>
 * <li>AE2 grid node integration (classic rv3 lifecycle: create on first tick, destroy on unload)</li>
 * <li>wireless master/slave link logic</li>
 * </ul>
 * The tile implements IGridBlock directly (classic rv3 pattern).
 */
public class WirelessTransceiverBlockEntity extends TileEntity
    implements IGridHost, IGridBlock, IWirelessEndpoint {

    private IGridNode node;

    private long frequency = 1L;
    private boolean masterMode = false;
    private boolean locked = false;
    private boolean beingRemoved = false;

    private UUID placerId;
    private String placerName;

    private boolean firstTickDone = false;

    // TEMP DEBUG: grid diagnostics synced to client for Waila
    private boolean debugNodeActive = false;
    private boolean debugNodeHasGrid = false;
    private int debugNodeConns = 0;
    private boolean debugNodeActivePrev = false;

    public boolean isDebugNodeActive() {
        return debugNodeActive;
    }

    public boolean isDebugNodeHasGrid() {
        return debugNodeHasGrid;
    }

    public int getDebugNodeConns() {
        return debugNodeConns;
    }

    private WirelessMasterLink masterLink;
    private WirelessSlaveLink slaveLink;

    public WirelessTransceiverBlockEntity() {
        this.masterLink = new WirelessMasterLink(this);
        this.slaveLink = new WirelessSlaveLink(this);
    }

    /* ===================== node lifecycle ===================== */

    @Override
    public void updateEntity() {
        if (worldObj == null || worldObj.isRemote) {
            return;
        }
        if (!firstTickDone) {
            firstTickDone = true;
            if (node == null) {
                node = AEApi.instance().createGridNode(this);
            }
            // Re-apply mode & frequency after (re)load.
            if (masterMode) {
                masterLink.setFrequency(frequency);
            } else {
                slaveLink.setFrequency(frequency);
            }
        }
        if (node != null) {
            // rv3 Grid.update() never calls GridNode.updateState(), so the
            // neighbor scan (FindConnections) only runs when we call it.
            // Periodically re-running it is what actually joins/keeps this node
            // in the local ME grid (EAEP's managed node does this internally).
            node.updateState();
            syncSecurityKey();
            applyNodeIdentity();
            maintainLocalConnections();
        }
        if (!masterMode) {
            // Slaves periodically maintain their connection.
            slaveLink.updateStatus();
        }
        updateVisualState();
    }

    private long lastSecurityKey = Long.MIN_VALUE;

    /**
     * Root-cause fix for "cannot connect to ME network": rv3's
     * GridConnection/securityCheck compares per-node security keys. AE2's own
     * nodes load their key from NBT (getLong("k"), which is 0 when absent),
     * while freshly created nodes default to -1 — so a powered grid (controller)
     * rejects our node with SecurityConnectionException. Joining the grid's
     * security realm (SecurityCache key) makes the keys equal and the
     * connection allowed.
     */
    private void syncSecurityKey() {
        if (node == null) {
            return;
        }
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
                    AE2Wtx.LOG.info("WTX security key synced: " + key + " at " + xCoord + "," + yCoord + "," + zCoord);
                }
            }
        } catch (Throwable t) {
            // grid cache hiccup - retry next tick
        }
    }

    /**
     * Give the node the placer's AE2 player id. Needed when the target grid has
     * a Security Station: securityCheck then verifies the node's player
     * permissions instead of outright rejecting the connection, and once the
     * node joins the grid syncSecurityKey() takes over.
     */
    private void applyNodeIdentity() {
        if (node == null || placerId == null) {
            return;
        }
        try {
            com.mojang.authlib.GameProfile profile = new com.mojang.authlib.GameProfile(placerId,
                placerName == null ? "" : placerName);
            int pid = appeng.api.AEApi.instance().registries().players().getID(profile);
            if (pid >= 0 && node.getPlayerID() != pid) {
                node.setPlayerID(pid);
            }
        } catch (Throwable t) {
            // registry hiccup - retry next tick
        }
    }

    private int localConnTick = 0;

    /**
     * Belt-and-braces neighbor discovery: rv3's FindConnections can silently
     * fail to join the node into a neighbor grid, so we actively scan the six
     * adjacent blocks and create the grid connections ourselves (the same
     * mechanism the wireless slave link uses successfully).
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
                AEApi.instance().createGridConnection(node, other);
                AE2Wtx.LOG.info("WTX manual connect OK: " + te.getClass().getSimpleName() + " at "
                    + (xCoord + dir.offsetX) + "," + (yCoord + dir.offsetY) + "," + (zCoord + dir.offsetZ));
            } catch (Throwable t) {
                AE2Wtx.LOG.warn("WTX manual connect FAILED to " + te.getClass().getSimpleName() + " at "
                    + (xCoord + dir.offsetX) + "," + (yCoord + dir.offsetY) + "," + (zCoord + dir.offsetZ) + ": " + t);
            }
        }
    }

    @Override
    public void invalidate() {
        super.invalidate();
        destroyNodeAndLinks();
    }

    @Override
    public void onChunkUnload() {
        super.onChunkUnload();
        destroyNodeAndLinks();
    }

    public void destroyNodeAndLinks() {
        if (beingRemoved) {
            return;
        }
        beingRemoved = true;
        if (masterMode) {
            masterLink.onUnloadOrRemove();
        } else {
            slaveLink.onUnloadOrRemove();
        }
        if (node != null) {
            node.destroy();
            node = null;
        }
    }

    /* ===================== IGridHost ===================== */

    @Override
    public IGridNode getGridNode(ForgeDirection dir) {
        return node;
    }

    @Override
    public AECableType getCableConnectionType(ForgeDirection dir) {
        return AECableType.GLASS;
    }

    @Override
    public void securityBreak() {
        // no security in GTNH's AE2 fork for this machine
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
    public void gridChanged() {
        // no-op
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
        return beingRemoved || worldObj == null || isInvalid();
    }

    /* ===================== state accessors ===================== */

    public void setPlacerId(UUID placerId, String placerName) {
        boolean ownerChanged = !java.util.Objects.equals(this.placerId, placerId);
        this.placerId = placerId;
        this.placerName = placerName;
        this.masterLink.setPlacerId(placerId);
        this.slaveLink.setPlacerId(placerId);
        if (ownerChanged && node != null) {
            // re-apply links under the new owner
            if (masterMode) {
                masterLink.setFrequency(frequency);
            } else {
                slaveLink.setFrequency(frequency);
            }
        }
        markDirty();
        syncToClients();
    }

    public UUID getPlacerId() {
        return placerId;
    }

    public String getPlacerName() {
        return placerName;
    }

    public long getFrequency() {
        return frequency;
    }

    public void setFrequency(long frequency) {
        if (locked) {
            return;
        }
        if (this.frequency == frequency) {
            return;
        }
        this.frequency = frequency;
        if (masterMode) {
            masterLink.setFrequency(frequency);
        } else {
            slaveLink.setFrequency(frequency);
        }
        markDirty();
        syncToClients();
    }

    public boolean isMasterMode() {
        return masterMode;
    }

    public void setMasterMode(boolean masterMode) {
        if (locked) {
            return;
        }
        if (this.masterMode == masterMode) {
            return;
        }
        // clean up the old mode first
        if (this.masterMode) {
            masterLink.onUnloadOrRemove();
        } else {
            slaveLink.onUnloadOrRemove();
        }
        this.masterMode = masterMode;
        if (this.masterMode) {
            masterLink.setFrequency(frequency);
        } else {
            slaveLink.setFrequency(frequency);
        }
        markDirty();
        syncToClients();
    }

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

    /* ===================== visual state (metadata 0-5) ===================== */

    private void updateVisualState() {
        if (worldObj == null || worldObj.isRemote || beingRemoved || isInvalid()) {
            return;
        }
        IGridNode n = node;
        // TEMP DEBUG: refresh diagnostics
        boolean dbgGrid = n != null && n.getGrid() != null;
        boolean dbgActive = n != null && n.isActive();
        int dbgConns = n == null ? 0 : n.getConnections().size();
        if (dbgGrid != debugNodeHasGrid || dbgActive != debugNodeActive || dbgConns != debugNodeConns) {
            debugNodeHasGrid = dbgGrid;
            debugNodeActive = dbgActive;
            debugNodeConns = dbgConns;
            syncToClients();
        }
        int newState = 5; // default: no connection
        if (n != null && n.isActive()) {
            int usedChannels = 0;
            for (IGridConnection c : n.getConnections()) {
                usedChannels = Math.max(c.getUsedChannels(), usedChannels);
            }
            if (usedChannels >= 32) {
                newState = 4;
            } else if (usedChannels >= 24) {
                newState = 3;
            } else if (usedChannels >= 16) {
                newState = 2;
            } else if (usedChannels >= 8) {
                newState = 1;
            } else if (usedChannels > 0) {
                newState = 0;
            }
        }
        // TEMP DEBUG: log first connection state change
        if (n != null && debugNodeActive != debugNodeActivePrev) {
            debugNodeActivePrev = debugNodeActive;
            cn.gtnh.ae2wtx.AE2Wtx.LOG.info("WTX state: active=" + debugNodeActive + " grid=" + debugNodeHasGrid
                + " conns=" + debugNodeConns + " at " + xCoord + "," + yCoord + "," + zCoord);
        }
        if (worldObj.getBlockMetadata(xCoord, yCoord, zCoord) != newState) {
            worldObj.setBlockMetadataWithNotify(xCoord, yCoord, zCoord, newState, 3);
        }
    }

    /* ===================== NBT ===================== */

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setLong("frequency", frequency);
        tag.setBoolean("master", masterMode);
        tag.setBoolean("locked", locked);
        if (placerId != null) {
            tag.setString("placerId", placerId.toString());
        }
        if (placerName != null) {
            tag.setString("placerName", placerName);
        }
        // TEMP DEBUG
        tag.setBoolean("dbgGrid", debugNodeHasGrid);
        tag.setBoolean("dbgActive", debugNodeActive);
        tag.setInteger("dbgConns", debugNodeConns);
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        this.frequency = tag.getLong("frequency");
        this.masterMode = tag.getBoolean("master");
        this.locked = tag.getBoolean("locked");
        if (tag.hasKey("placerId")) {
            this.placerId = UUID.fromString(tag.getString("placerId"));
            this.masterLink.setPlacerId(this.placerId);
            this.slaveLink.setPlacerId(this.placerId);
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
        // read client-visible state only (no server-side link re-application)
        this.frequency = tag.getLong("frequency");
        this.masterMode = tag.getBoolean("master");
        this.locked = tag.getBoolean("locked");
        this.placerId = tag.hasKey("placerId") ? UUID.fromString(tag.getString("placerId")) : null;
        this.placerName = tag.hasKey("placerName") ? tag.getString("placerName") : null;
        // TEMP DEBUG
        this.debugNodeHasGrid = tag.getBoolean("dbgGrid");
        this.debugNodeActive = tag.getBoolean("dbgActive");
        this.debugNodeConns = tag.getInteger("dbgConns");
        if (worldObj != null) {
            worldObj.markBlockRangeForRenderUpdate(xCoord, yCoord, zCoord, xCoord, yCoord, zCoord);
        }
    }
}
