package cn.gtnh.ae2wtx.gui;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import cn.gtnh.ae2wtx.content.wireless.LabeledWirelessTransceiverBlock;
import cn.gtnh.ae2wtx.content.wireless.LabeledWirelessTransceiverBlockEntity;

/** Container for the labeled wireless transceiver GUI (validates distance and target). */
public class LabeledContainer extends Container {

    private final World world;
    private final int x;
    private final int y;
    private final int z;

    public LabeledContainer() {
        this(null, 0, 0, 0);
    }

    public LabeledContainer(World world, int x, int y, int z) {
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        if (world == null) {
            return true;
        }
        if (!(world.getBlock(x, y, z) instanceof LabeledWirelessTransceiverBlock)) {
            return false;
        }
        TileEntity te = world.getTileEntity(x, y, z);
        if (!(te instanceof LabeledWirelessTransceiverBlockEntity) || te.isInvalid()) {
            return false;
        }
        return player.getDistanceSq(x + 0.5D, y + 0.5D, z + 0.5D) <= 64.0D;
    }
}
