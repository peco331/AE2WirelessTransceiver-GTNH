package cn.gtnh.ae2wtx.compat;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

import cn.gtnh.ae2wtx.init.ModItems;
import cn.gtnh.ae2wtx.item.ChannelCardItem;

/**
 * Sneak + right-click on air with a channel card in hand binds/unbinds the
 * card's owner (1.7.10 has no left-click-empty hook, so this mirrors EAEP's
 * sneak+left bind on the right-click slot instead). Server-side only: the
 * client must not cancel the event or the packet never reaches the server.
 */
public class ChannelCardBindHandler {

    public static void register() {
        MinecraftForge.EVENT_BUS.register(new ChannelCardBindHandler());
    }

    @SubscribeEvent
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.action != PlayerInteractEvent.Action.RIGHT_CLICK_AIR) {
            return;
        }
        if (event.entityPlayer == null || event.world == null) {
            return;
        }
        EntityPlayer player = event.entityPlayer;
        if (!player.isSneaking()) {
            return;
        }
        ItemStack held = player.getHeldItem();
        if (held == null || held.getItem() != ModItems.itemChannelCard) {
            return;
        }
        if (player.worldObj.isRemote) {
            return; // server handles the binding
        }
        event.setCanceled(true);
        ChannelCardItem.bindOrUnbind(held, player);
    }
}
