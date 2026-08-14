package cn.gtnh.ae2wtx.compat;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.World;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

import cn.gtnh.ae2wtx.content.wireless.LabeledWirelessTransceiverBlockEntity;
import cn.gtnh.ae2wtx.content.wireless.WirelessTransceiverBlock;
import cn.gtnh.ae2wtx.content.wireless.WirelessTransceiverBlockEntity;
import cn.gtnh.ae2wtx.network.NetworkHandler;
import cn.gtnh.ae2wtx.network.OpenFrequencyScreenPacket;

/**
 * 1.7.10 port of ExtendedAE_Plus' WrenchHook. Forge events give us what the
 * rv3 block callbacks cannot: sneaking + left click with a wrench must NOT
 * break the block (1.7.10 {@code onBlockClicked} returns void and cannot
 * cancel the harvest), so the frequency screen opens via a server-to-client
 * packet instead.
 * <ul>
 * <li>sneak + right + wrench: disassemble (AE2-style: item dropped on the ground)</li>
 * <li>right + wrench (not sneaking): toggle lock (lever sound + status message)</li>
 * <li>sneak + left + wrench: open frequency input screen (client side)</li>
 * </ul>
 * Applies to both the plain transceiver and the labeled transceiver (disassemble only).
 */
public class WrenchHandler {

    private static final Logger LOG = LogManager.getLogger("ae2wtx");

    public static void register() {
        MinecraftForge.EVENT_BUS.register(new WrenchHandler());
    }

    @SubscribeEvent
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.entityPlayer == null || event.world == null) {
            return;
        }
        EntityPlayer player = event.entityPlayer;
        ItemStack held = player.getHeldItem();
        if (held == null || !WirelessTransceiverBlock.isWrench(held)) {
            return;
        }
        TileEntity te = event.world.getTileEntity(event.x, event.y, event.z);
        if (te instanceof WirelessTransceiverBlockEntity) {
            WirelessTransceiverBlockEntity wte = (WirelessTransceiverBlockEntity) te;
            LOG.info("Wrench event: action=" + event.action + " sneak=" + player.isSneaking()
                + " at " + event.x + "," + event.y + "," + event.z + " remote=" + event.world.isRemote);
            if (event.action == PlayerInteractEvent.Action.LEFT_CLICK_BLOCK && player.isSneaking()) {
                // sneak + left + wrench: open frequency input screen
                event.setCanceled(true); // prevents the block from being mined
                if (!player.worldObj.isRemote) {
                    NetworkHandler.CHANNEL.sendTo(
                        new OpenFrequencyScreenPacket(event.x, event.y, event.z, wte.getFrequency()),
                        (EntityPlayerMP) player);
                }
            } else if (event.action == PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK) {
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
                    boolean newLocked = !wte.isLocked();
                    wte.setLocked(newLocked);
                    event.world.playSoundEffect(event.x + 0.5D, event.y + 0.5D, event.z + 0.5D,
                        "random.lever", 0.5F, newLocked ? 0.6F : 0.9F);
                    player.addChatMessage(new ChatComponentTranslation(
                        newLocked ? "extendedae_plus.chat.wireless_transceiver.locked_status"
                            : "extendedae_plus.chat.wireless_transceiver.unlocked_status"));
                }
            }
        } else if (te instanceof LabeledWirelessTransceiverBlockEntity) {
            if (event.action == PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK && player.isSneaking()) {
                event.setCanceled(true);
                if (!player.worldObj.isRemote) {
                    disassemble(event.world, event.x, event.y, event.z);
                }
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
