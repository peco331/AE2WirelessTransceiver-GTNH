package cn.gtnh.ae2wtx.content.wireless;

import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.server.MinecraftServer;

public final class TransceiverSecurity {

    private TransceiverSecurity() {}

    public static boolean isOp(EntityPlayer player) {
        if (player == null) {
            return false;
        }
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null || server.getConfigurationManager() == null) {
            return false;
        }
        return server.getConfigurationManager().func_152596_g(player.getGameProfile());
    }

    public static boolean canManage(EntityPlayer player, LabeledWirelessTransceiverBlockEntity te) {
        if (te == null || player == null) {
            return false;
        }
        if (!te.isLocked()) {
            return true;
        }
        UUID owner = te.getPlacerId();
        if (owner == null || player.getUniqueID().equals(owner)) {
            return true;
        }
        return isOp(player);
    }
}