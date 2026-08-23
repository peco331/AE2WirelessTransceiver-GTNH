package cn.gtnh.ae2wtx.compat;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;

import cn.gtnh.ae2wtx.content.wireless.LabeledWirelessTransceiverBlockEntity;
import cn.gtnh.ae2wtx.content.wireless.TransceiverSecurity;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.event.world.BlockEvent;

/** Register an instance on the Forge event bus to enforce locked-block mining protection. */
public final class LockedTransceiverBreakHandler {

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event == null || event.isCanceled() || event.world == null || event.world.isRemote) {
            return;
        }
        EntityPlayer player = event.getPlayer();
        TileEntity te = event.world.getTileEntity(event.x, event.y, event.z);
        if (!(te instanceof LabeledWirelessTransceiverBlockEntity)) {
            return;
        }
        LabeledWirelessTransceiverBlockEntity transceiver = (LabeledWirelessTransceiverBlockEntity) te;
        if (transceiver.isLocked() && !TransceiverSecurity.canManage(player, transceiver)) {
            event.setCanceled(true);
            if (player != null) {
                player.addChatMessage(new ChatComponentTranslation(
                    "extendedae_plus.chat.wireless_transceiver.locked"));
            }
        }
    }
}
