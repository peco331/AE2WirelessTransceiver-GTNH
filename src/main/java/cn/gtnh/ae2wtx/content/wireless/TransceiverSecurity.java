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
        if (isOwnerOrOp(player, te)) {
            return true;
        }
        // A locked legacy/corrupt tile without an owner must fail closed. An
        // operator can still unlock it for world recovery through the fallback.
        return false;
    }

    /** Destructive band-wide actions are restricted even when the block itself is unlocked. */
    public static boolean isOwnerOrOp(EntityPlayer player, LabeledWirelessTransceiverBlockEntity te) {
        if (te == null || player == null) {
            return false;
        }
        UUID owner = te.getPlacerId();
        return owner != null && player.getUniqueID().equals(owner) || isOp(player);
    }
}
