package cn.gtnh.ae2wtx.content.wireless;

import java.util.UUID;

import net.minecraft.block.Block;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;
import net.minecraftforge.oredict.OreDictionary;

import cn.gtnh.ae2wtx.AE2Wtx;
import cn.gtnh.ae2wtx.init.ModItems;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Wireless transceiver block. Interactions (1.7.10):
 * <ul>
 * <li>right click: toggle master/slave mode</li>
 * <li>shift+right click: frequency +1 (redstone torch/stick held: +10)</li>
 * <li>shift+left click: frequency -1 (redstone torch/stick held: -10)</li>
 * <li>GT wrench + shift+right: lock/unlock</li>
 * <li>channel card + shift+left: write card owner into the transceiver</li>
 * </ul>
 * Metadata 0-5 holds the channel-usage indicator state.
 */
public class WirelessTransceiverBlock extends Block implements ITileEntityProvider {

    @SideOnly(Side.CLIENT)
    private IIcon icon;

    public WirelessTransceiverBlock() {
        super(Material.iron);
        setHardness(3.0F);
        setResistance(10.0F);
        setStepSound(soundTypeMetal);
        setCreativeTab(AE2Wtx.CREATIVE_TAB);
        setBlockName("ae2wtx.wireless_transceiver");
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

    @Override
    public boolean hasTileEntity(int metadata) {
        return true;
    }

    @Override
    public TileEntity createNewTileEntity(World world, int metadata) {
        return new WirelessTransceiverBlockEntity();
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX,
        float hitY, float hitZ) {
        if (world.isRemote) {
            return true;
        }
        TileEntity te = world.getTileEntity(x, y, z);
        if (!(te instanceof WirelessTransceiverBlockEntity)) {
            return false;
        }
        WirelessTransceiverBlockEntity wte = (WirelessTransceiverBlockEntity) te;
        ItemStack held = player.getHeldItem();
        boolean sneaking = player.isSneaking();
        boolean wrench = isGTWrench(held);

        if (wrench && sneaking) {
            wte.setLocked(!wte.isLocked());
            player.addChatMessage(new ChatComponentText(
                wte.isLocked() ? "extendedae_plus.chat.wireless_transceiver.locked" : "extendedae_plus.chat.wireless_transceiver.unlocked"));
            return true;
        }

        if (wrench) {
            // open the frequency input GUI
            cpw.mods.fml.common.network.internal.FMLNetworkHandler
                .openGui(player, cn.gtnh.ae2wtx.AE2Wtx.instance, cn.gtnh.ae2wtx.gui.ModGuiHandler.GUI_FREQUENCY, world, x, y, z);
            return true;
        }

        if (wte.isLocked()) {
            player.addChatMessage(new ChatComponentText("extendedae_plus.chat.wireless_transceiver.locked"));
            return true;
        }

        if (sneaking) {
            // frequency +
            int step = getStep(held);
            long f = wte.getFrequency() + step;
            wte.setFrequency(f);
            player.addChatMessage(new ChatComponentText("extendedae_plus.chat.wireless_transceiver.channel " + wte.getFrequency()));
        } else {
            wte.setMasterMode(!wte.isMasterMode());
            String modeKey = wte.isMasterMode()
                ? "extendedae_plus.chat.wireless_transceiver.mode_master"
                : "extendedae_plus.chat.wireless_transceiver.mode_slave";
            player.addChatMessage(new ChatComponentText("extendedae_plus.chat.wireless_transceiver.mode " + modeKey));
        }
        return true;
    }

    @Override
    public void onBlockClicked(World world, int x, int y, int z, EntityPlayer player) {
        if (world.isRemote) {
            return;
        }
        TileEntity te = world.getTileEntity(x, y, z);
        if (!(te instanceof WirelessTransceiverBlockEntity)) {
            return;
        }
        WirelessTransceiverBlockEntity wte = (WirelessTransceiverBlockEntity) te;
        ItemStack held = player.getHeldItem();
        boolean sneaking = player.isSneaking();

        // channel card shift+left: write owner info into the transceiver
        if (sneaking && held != null && held.getItem() == ModItems.itemChannelCard) {
            handleChannelCardBinding(wte, held, player);
            return;
        }

        if (sneaking) {
            if (wte.isLocked()) {
                player.addChatMessage(new ChatComponentText("extendedae_plus.chat.wireless_transceiver.locked"));
                return;
            }
            int step = getStep(held);
            long f = wte.getFrequency() - step;
            if (f < 0) {
                f = 0;
            }
            wte.setFrequency(f);
            player.addChatMessage(new ChatComponentText("extendedae_plus.chat.wireless_transceiver.channel " + wte.getFrequency()));
        }
    }

    private static int getStep(ItemStack held) {
        if (held != null && held.getItem() != null) {
            Item heldItem = held.getItem();
            if (heldItem == Item.getItemFromBlock(Blocks.redstone_torch) || heldItem == Item.getItemFromBlock(Blocks.torch)) {
                return 10;
            }
        }
        return 1;
    }

    private void handleChannelCardBinding(WirelessTransceiverBlockEntity wte, ItemStack card, EntityPlayer player) {
        UUID cardOwner = cn.gtnh.ae2wtx.item.ChannelCardItem.getOwnerUUID(card);
        if (cardOwner != null) {
            wte.setPlacerId(cardOwner, player.getCommandSenderName());
            player.addChatMessage(new ChatComponentText("extendedae_plus.chat.wireless_transceiver.bound_to " + cardOwner.toString().substring(0, 8)));
        } else {
            wte.setPlacerId(player.getUniqueID(), player.getCommandSenderName());
            player.addChatMessage(new ChatComponentText("extendedae_plus.chat.wireless_transceiver.card_unbound"));
        }
    }

    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase placer, ItemStack stack) {
        super.onBlockPlacedBy(world, x, y, z, placer, stack);
        if (!world.isRemote && placer instanceof EntityPlayer) {
            TileEntity te = world.getTileEntity(x, y, z);
            if (te instanceof WirelessTransceiverBlockEntity) {
                ((WirelessTransceiverBlockEntity) te).setPlacerId(
                    ((EntityPlayer) placer).getUniqueID(),
                    ((EntityPlayer) placer).getCommandSenderName());
            }
        }
    }

    @Override
    public void breakBlock(World world, int x, int y, int z, Block block, int meta) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof WirelessTransceiverBlockEntity) {
            ((WirelessTransceiverBlockEntity) te).destroyNodeAndLinks();
        }
        super.breakBlock(world, x, y, z, block, meta);
    }

    /* ===================== rendering ===================== */

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister reg) {
        icon = reg.registerIcon("ae2wtx:wireless_transceiver");
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int side, int meta) {
        return icon;
    }
}
