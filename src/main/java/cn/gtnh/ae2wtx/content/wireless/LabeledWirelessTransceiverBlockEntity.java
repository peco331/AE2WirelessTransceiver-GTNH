package cn.gtnh.ae2wtx.content.wireless;

import java.util.EnumSet;
import java.util.UUID;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

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
    private boolean beingRemoved = false;

    private UUID placerId;
    private String placerName;

    private boolean firstTickDone = false;

    private final LabelLink labelLink = new LabelLink(this);

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
                node.updateState();
            }
            refreshLabel(true);
        }
        labelLink.updateStatus();
        updateVisualState();
    }

    @Override
    public void invalidate() {
        super.invalidate();
        cleanupForRemoval();
    }

    @Override
    public void onChunkUnload() {
        super.onChunkUnload();
        cleanupForRemoval();
    }

    public void cleanupForRemoval() {
        if (beingRemoved) {
            return;
        }
        beingRemoved = true;
        labelLink.onUnloadOrRemove();
        LabelNetworkRegistry reg = LabelNetworkRegistry.get(worldObj);
        if (reg != null) {
            reg.unregister(this);
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
        return new ItemStack(ModBlocks.blockLabeledWirelessTransceiver);
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

    public String getLabelForDisplay() {
        return labelForDisplay;
    }

    public void applyLabel(String rawLabel) {
        LabelNetworkRegistry reg = LabelNetworkRegistry.get(worldObj);
        if (reg == null) {
            return;
        }
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
    }

    public void clearLabel() {
        LabelNetworkRegistry reg = LabelNetworkRegistry.get(worldObj);
        if (reg != null) {
            reg.unregister(this);
        }
        this.labelForDisplay = null;
        this.frequency = 0L;
        this.labelLink.clearTarget();
        updateVisualState();
        markDirty();
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
    }

    /* ===================== visual state (metadata 0/1 online) ===================== */

    private void updateVisualState() {
        if (worldObj == null || worldObj.isRemote || beingRemoved || isInvalid()) {
            return;
        }
        IGridNode n = node;
        boolean online = false;
        if (n != null && n.isActive()) {
            try {
                IGrid grid = n.getGrid();
                online = grid != null && ((IEnergyGrid) grid.getCache(IEnergyGrid.class)).isNetworkPowered();
            } catch (Throwable ignored) {
                online = false;
            }
        }
        int meta = online ? 1 : 0;
        if (worldObj.getBlockMetadata(xCoord, yCoord, zCoord) != meta) {
            worldObj.setBlockMetadataWithNotify(xCoord, yCoord, zCoord, meta, 3);
        }
    }

    /* ===================== NBT ===================== */

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setLong("frequency", frequency);
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
        if (tag.hasKey("placerId")) {
            this.placerId = UUID.fromString(tag.getString("placerId"));
        }
        if (tag.hasKey("placerName")) {
            this.placerName = tag.getString("placerName");
        }
    }
}
