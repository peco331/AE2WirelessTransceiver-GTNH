package cn.gtnh.ae2wtx.compat;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.World;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.oredict.OreDictionary;

import cn.gtnh.ae2wtx.content.wireless.LabeledWirelessTransceiverBlockEntity;

/**
 * Wrench interactions for the wireless transceiver (1.7.10 port of
 * ExtendedAE_Plus' WrenchHook):
 * <ul>
 * <li>right + wrench (not sneaking): toggle lock (lever sound + status message)</li>
 * <li>sneak + right + wrench: disassemble (AE2-style: item dropped on the ground)</li>
 * </ul>
 * NOTE: 1.7.10 fires RIGHT_CLICK_BLOCK on the CLIENT before sending the
 * right-click packet; canceling on the client suppresses the packet, so only
 * handle on the server side.
 */
public class WrenchHandler {

    private static final Logger LOG = LogManager.getLogger("ae2wtx");

    public static void register() {
        MinecraftForge.EVENT_BUS.register(new WrenchHandler());
    }

    public static boolean isGTWrench(ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return false;
        }
        int[] ids = OreDictionary.getOreIDs(stack);
        for (int id : ids) {
            if ("craftingToolWrench".equals(OreDictionary.getOreName(id))) {
                return true;
            }
        }
        return false;
    }

    public static boolean isAE2Wrench(ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return false;
        }
        Item certus = appeng.api.AEApi.instance().items().itemCertusQuartzWrench.item();
        if (certus != null && stack.getItem() == certus) {
            return true;
        }
        Item nether = appeng.api.AEApi.instance().items().itemNetherQuartzWrench.item();
        return nether != null && stack.getItem() == nether;
    }

    /** Any wrench (GT ore-dict wrench or AE2 quartz wrench). */
    public static boolean isWrench(ItemStack stack) {
        return isGTWrench(stack) || isAE2Wrench(stack);
    }

    @SubscribeEvent
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.entityPlayer == null || event.world == null) {
            return;
        }
        EntityPlayer player = event.entityPlayer;
        ItemStack held = player.getHeldItem();
        if (held == null || !isWrench(held)) {
            return;
        }
        TileEntity te = event.world.getTileEntity(event.x, event.y, event.z);
        if (!(te instanceof LabeledWirelessTransceiverBlockEntity)) {
            return;
        }
        if (event.action == PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK) {
            // CRITICAL: 1.7.10 fires this event on the CLIENT before sending
            // the right-click packet (Minecraft.func_147121_ag); canceling on
            // the client suppresses the packet so the server never sees the
            // interaction. Only handle on the server side.
            if (player.worldObj.isRemote) {
                return;
            }
            event.setCanceled(true);
            if (player.isSneaking()) {
                // sneak + right: disassemble (AE2-style ground drop)
                disassemble(event.world, event.x, event.y, event.z);
            } else {
                // right (not sneaking): toggle lock
                LabeledWirelessTransceiverBlockEntity lte = (LabeledWirelessTransceiverBlockEntity) te;
                boolean newLocked = !lte.isLocked();
                lte.setLocked(newLocked);
                event.world.playSoundEffect(event.x + 0.5D, event.y + 0.5D, event.z + 0.5D,
                    "random.lever", 0.5F, newLocked ? 0.6F : 0.9F);
                player.addChatMessage(new ChatComponentTranslation(
                    newLocked ? "extendedae_plus.chat.wireless_transceiver.locked_status"
                        : "extendedae_plus.chat.wireless_transceiver.unlocked_status"));
            }
        }
    }

    /** AE2 vanilla-style disassembly: drop the block item as an entity, remove the block. */
    private static void disassemble(World world, int x, int y, int z) {
        net.minecraft.block.Block block = world.getBlock(x, y, z);
        ItemStack drop = new ItemStack(block);
        float f = 0.7F;
        double dx = world.rand.nextFloat() * f + (1.0F - f) * 0.5D;
        double dy = world.rand.nextFloat() * f + (1.0F - f) * 0.5D;
        double dz = world.rand.nextFloat() * f + (1.0F - f) * 0.5D;
        EntityItem ei = new EntityItem(world, x + dx, y + dy, z + dz, drop);
        ei.delayBeforeCanPickup = 10;
        world.spawnEntityInWorld(ei);
        world.playSoundEffect(x + 0.5D, y + 0.5D, z + 0.5D, "random.wood_click", 0.7F, 1.0F);
        world.setBlockToAir(x, y, z);
    }
}
