package cn.gtnh.ae2wtx.content.wireless;

import java.util.EnumSet;
import java.util.UUID;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
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
        if (!masterMode) {
            // Slaves periodically maintain their connection.
            slaveLink.updateStatus();
        }
        updateVisualState();
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
    }

    /* ===================== visual state (metadata 0-5) ===================== */

    private void updateVisualState() {
        if (worldObj == null || worldObj.isRemote || beingRemoved || isInvalid()) {
            return;
        }
        IGridNode n = node;
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
}
