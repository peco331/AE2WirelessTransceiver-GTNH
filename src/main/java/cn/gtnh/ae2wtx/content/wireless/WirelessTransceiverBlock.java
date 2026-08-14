package cn.gtnh.ae2wtx.content.wireless;

import java.util.UUID;

import net.minecraft.block.Block;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;
import net.minecraftforge.oredict.OreDictionary;

import cn.gtnh.ae2wtx.AE2Wtx;
import cn.gtnh.ae2wtx.init.ModItems;
import cn.gtnh.ae2wtx.item.ChannelCardItem;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Wireless transceiver block. Interactions match ExtendedAE_Plus:
 * <ul>
 * <li>right click (empty/other): toggle master/slave</li>
 * <li>shift+right click: frequency +1 (redstone torch/stick held: +10)</li>
 * <li>shift+left click: frequency -1 (redstone torch/stick held: -10)</li>
 * <li>GT wrench right click (not sneaking): toggle lock</li>
 * <li>GT wrench shift+right click: disassemble into inventory</li>
 * <li>GT wrench shift+left click: open frequency input screen (client)</li>
 * <li>channel card shift+left click: write card owner into the transceiver</li>
 * <li>locked transceiver: 10% mining speed</li>
 * </ul>
 * Metadata 0-5 holds the channel-usage indicator state (texture per state).
 */
public class WirelessTransceiverBlock extends Block implements ITileEntityProvider {

    @SideOnly(Side.CLIENT)
    private IIcon[] sideIcons;

    @SideOnly(Side.CLIENT)
    private IIcon[] topIcons;

    @SideOnly(Side.CLIENT)
    private IIcon itemIcon;

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

    /**
     * True when the held item is an AE2 wrench (Certus Quartz or Nether Quartz
     * wrench). Matches AE2 vanilla behavior where these can rotate/disassemble.
     */
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

    public static boolean isQuartzKnife(ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return false;
        }
        Item knife = appeng.api.AEApi.instance().items().itemCertusQuartzKnife.item();
        return knife != null && stack.getItem() == knife;
    }

    private static int getStep(ItemStack held) {
        if (held != null && held.getItem() != null) {
            Item heldItem = held.getItem();
            // matches EAEP: redstone torch or stick -> step 10
            if (heldItem == Item.getItemFromBlock(Blocks.redstone_torch) || heldItem == net.minecraft.init.Items.stick) {
                return 10;
            }
        }
        return 1;
    }

    @Override
    public boolean hasTileEntity(int metadata) {
        return true;
    }

    @Override
    public TileEntity createNewTileEntity(World world, int metadata) {
        return new WirelessTransceiverBlockEntity();
    }

    /* ===================== interactions ===================== */

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
        boolean wrench = isWrench(held);

        // wrench + sneaking: disassemble into inventory (matches EAEP disassemble behavior)
        if (wrench && sneaking) {
            world.playSoundEffect(x + 0.5D, y + 0.5D, z + 0.5D, "random.wood_click", 0.7F, 1.0F);
            ItemStack drop = new ItemStack(this);
            if (!player.inventory.addItemStackToInventory(drop)) {
                EntityItem ei = new EntityItem(world, x + 0.5D, y + 0.5D, z + 0.5D, drop);
                world.spawnEntityInWorld(ei);
            }
            world.setBlockToAir(x, y, z);
            return true;
        }

        // wrench (not sneaking): toggle lock (matches EAEP rotate -> lock)
        if (wrench) {
            boolean newLocked = !wte.isLocked();
            wte.setLocked(newLocked);
            world.playSoundEffect(x + 0.5D, y + 0.5D, z + 0.5D, "random.lever", 0.5F, newLocked ? 0.6F : 0.9F);
            player.addChatMessage(new ChatComponentTranslation(
                newLocked ? "extendedae_plus.chat.wireless_transceiver.locked_status"
                    : "extendedae_plus.chat.wireless_transceiver.unlocked_status"));
            return true;
        }

        if (wte.isLocked()) {
            player.addChatMessage(new ChatComponentTranslation("extendedae_plus.chat.wireless_transceiver.locked"));
            return true;
        }

        if (sneaking) {
            int step = getStep(held);
            long f = wte.getFrequency() + step;
            wte.setFrequency(f);
            player.addChatMessage(new ChatComponentTranslation("extendedae_plus.chat.wireless_transceiver.channel", wte.getFrequency()));
        } else {
            wte.setMasterMode(!wte.isMasterMode());
            String modeKey = wte.isMasterMode()
                ? "extendedae_plus.chat.wireless_transceiver.mode_master"
                : "extendedae_plus.chat.wireless_transceiver.mode_slave";
            player.addChatMessage(new ChatComponentTranslation(
                "extendedae_plus.chat.wireless_transceiver.mode",
                new ChatComponentTranslation(modeKey)));
        }
        return true;
    }

    @Override
    public void onBlockClicked(World world, int x, int y, int z, EntityPlayer player) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (!(te instanceof WirelessTransceiverBlockEntity)) {
            return;
        }
        WirelessTransceiverBlockEntity wte = (WirelessTransceiverBlockEntity) te;
        ItemStack held = player.getHeldItem();
        boolean sneaking = player.isSneaking();

        // wrench + sneaking: open frequency input screen (client side)
        if (sneaking && isWrench(held)) {
            if (world.isRemote) {
                cn.gtnh.ae2wtx.AE2Wtx.proxy.openFrequencyScreen(x, y, z, wte.getFrequency());
            }
            return;
        }

        if (world.isRemote) {
            return;
        }

        // channel card shift+left: write owner info into the transceiver
        if (sneaking && held != null && held.getItem() == ModItems.itemChannelCard) {
            handleChannelCardBinding(wte, held, player);
            return;
        }

        if (sneaking) {
            if (wte.isLocked()) {
                player.addChatMessage(new ChatComponentTranslation("extendedae_plus.chat.wireless_transceiver.locked"));
                return;
            }
            int step = getStep(held);
            long f = wte.getFrequency() - step;
            if (f < 0) {
                f = 0;
            }
            wte.setFrequency(f);
            player.addChatMessage(new ChatComponentTranslation("extendedae_plus.chat.wireless_transceiver.channel", wte.getFrequency()));
        }
    }

    private void handleChannelCardBinding(WirelessTransceiverBlockEntity wte, ItemStack card, EntityPlayer player) {
        UUID cardOwner = ChannelCardItem.getOwnerUUID(card);
        if (cardOwner != null) {
            wte.setPlacerId(cardOwner, player.getCommandSenderName());
            player.addChatMessage(new ChatComponentTranslation(
                "extendedae_plus.chat.wireless_transceiver.bound_to", cardOwner.toString().substring(0, 8)));
        } else {
            wte.setPlacerId(player.getUniqueID(), player.getCommandSenderName());
            player.addChatMessage(new ChatComponentTranslation("extendedae_plus.chat.wireless_transceiver.card_unbound"));
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

    /* ===================== locked mining slowdown ===================== */

    @Override
    public float getBlockHardness(World world, int x, int y, int z) {
        float base = blockHardness;
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof WirelessTransceiverBlockEntity && ((WirelessTransceiverBlockEntity) te).isLocked()) {
            return base * 0.1F;
        }
        return base;
    }

    /* ===================== rendering ===================== */

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister reg) {
        itemIcon = reg.registerIcon("ae2wtx:block/wireless_transceiver");
        sideIcons = new IIcon[6];
        topIcons = new IIcon[6];
        for (int i = 0; i < 6; i++) {
            sideIcons[i] = reg.registerIcon("ae2wtx:block/wireless_transceiver/wireless_transceiver_" + i);
            topIcons[i] = reg.registerIcon("ae2wtx:block/wireless_transceiver/wireless_transceiver_" + i + "_top");
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int side, int meta) {
        int state = Math.max(0, Math.min(5, meta));
        if (side == 0 || side == 1) {
            return topIcons[state];
        }
        return sideIcons[state];
    }
}
