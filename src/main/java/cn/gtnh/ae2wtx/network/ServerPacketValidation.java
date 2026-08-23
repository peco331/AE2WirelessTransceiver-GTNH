package cn.gtnh.ae2wtx.network;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import cn.gtnh.ae2wtx.content.wireless.LabeledWirelessTransceiverBlockEntity;
import cn.gtnh.ae2wtx.gui.LabeledContainer;

/** Shared, server-thread-only validation for transceiver GUI packets. */
final class ServerPacketValidation {

    private ServerPacketValidation() {}

    static LabeledWirelessTransceiverBlockEntity getTransceiver(EntityPlayerMP player, int dimension, int x, int y,
        int z, int windowId) {
        if (player == null || player.isDead || player.worldObj == null || player.worldObj.isRemote) {
            return null;
        }
        World world = player.worldObj;
        if (world.provider == null || world.provider.dimensionId != dimension || y < 0 || y >= 256) {
            return null;
        }
        if (player.getDistanceSq(x + 0.5D, y + 0.5D, z + 0.5D) > 64.0D || !world.blockExists(x, y, z)) {
            return null;
        }
        if (!(player.openContainer instanceof LabeledContainer) || player.openContainer.windowId != windowId) {
            return null;
        }
        LabeledContainer container = (LabeledContainer) player.openContainer;
        if (!container.matches(world, x, y, z) || !container.canInteractWith(player)) {
            return null;
        }
        TileEntity te = world.getTileEntity(x, y, z);
        return te instanceof LabeledWirelessTransceiverBlockEntity
            ? (LabeledWirelessTransceiverBlockEntity) te
            : null;
    }
}
