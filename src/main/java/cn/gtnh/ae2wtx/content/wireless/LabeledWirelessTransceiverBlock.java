package cn.gtnh.ae2wtx.content.wireless;

import net.minecraft.block.Block;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;

import cn.gtnh.ae2wtx.AE2Wtx;
import cpw.mods.fml.common.network.internal.FMLNetworkHandler;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Wireless transceiver (the only transceiver block; legacy registry name
 * "labeled_wireless_transceiver" kept for world compatibility):
 * right-click opens the frequency (label) management GUI.
 * Metadata 0/1 = offline/online indicator.
 */
public class LabeledWirelessTransceiverBlock extends Block implements ITileEntityProvider {

    public static final int GUI_ID = 1;

    /** Vanilla standard-block renderer until optional GTNHLib integration succeeds. */
    private static volatile int optionalModelRenderType;

    @SideOnly(Side.CLIENT)
    private IIcon iconOff;

    @SideOnly(Side.CLIENT)
    private IIcon iconOn;

    public LabeledWirelessTransceiverBlock() {
        super(Material.iron);
        setHardness(3.0F);
        setResistance(10.0F);
        setStepSound(soundTypeMetal);
        setCreativeTab(AE2Wtx.CREATIVE_TAB);
        setBlockName("ae2wtx.labeled_wireless_transceiver");
    }

    @Override
    public boolean hasTileEntity(int metadata) {
        return true;
    }

    @Override
    public TileEntity createNewTileEntity(World world, int metadata) {
        return new LabeledWirelessTransceiverBlockEntity();
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX,
        float hitY, float hitZ) {
        if (world.isRemote) {
            return true;
        }
        TileEntity te = world.getTileEntity(x, y, z);
        if (!(te instanceof LabeledWirelessTransceiverBlockEntity)) {
            return false;
        }
        // NOTE: wrench + sneak + right (disassemble) is handled by
        // cn.gtnh.ae2wtx.compat.WrenchHandler (Forge event).
        if (player.isSneaking()) {
            return false;
        }
        FMLNetworkHandler.openGui(player, AE2Wtx.instance, GUI_ID, world, x, y, z);
        return true;
    }

    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase placer, ItemStack stack) {
        super.onBlockPlacedBy(world, x, y, z, placer, stack);
        if (!world.isRemote && placer instanceof EntityPlayer) {
            TileEntity te = world.getTileEntity(x, y, z);
            if (te instanceof LabeledWirelessTransceiverBlockEntity) {
                ((LabeledWirelessTransceiverBlockEntity) te).setPlacerId(
                    ((EntityPlayer) placer).getUniqueID(),
                    ((EntityPlayer) placer).getCommandSenderName());
            }
        }
    }

    @Override
    public void breakBlock(World world, int x, int y, int z, Block block, int meta) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof LabeledWirelessTransceiverBlockEntity) {
            ((LabeledWirelessTransceiverBlockEntity) te).cleanupForRemoval();
        }
        super.breakBlock(world, x, y, z, block, meta);
    }

    /* ===================== locked mining slowdown ===================== */

    @Override
    public float getBlockHardness(World world, int x, int y, int z) {
        float base = blockHardness;
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof LabeledWirelessTransceiverBlockEntity
            && ((LabeledWirelessTransceiverBlockEntity) te).isLocked()) {
            return base * 10.0F;
        }
        return base;
    }

    /* ===================== self-illumination ===================== */

    /**
     * Online transceivers emit full light (15/15), offline ones stay dark.
     */
    @Override
    public int getLightValue(net.minecraft.world.IBlockAccess world, int x, int y, int z) {
        return world.getBlockMetadata(x, y, z) == 1 ? 15 : 0;
    }

    /* ===================== rendering ===================== */

    /** Optional GTNHLib JSON model, otherwise vanilla standard-block rendering. */
    @Override
    public int getRenderType() {
        return optionalModelRenderType;
    }

    @SideOnly(Side.CLIENT)
    public static void setOptionalModelRenderType(int renderType) {
        optionalModelRenderType = renderType;
    }

    @Override
    public boolean isOpaqueCube() {
        return false;
    }

    @Override
    public boolean renderAsNormalBlock() {
        return false;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister reg) {
        // 1.7.10 prepends "textures/blocks/" automatically (no "block/" segment)
        iconOff = reg.registerIcon("ae2wtx:wireless_transceiver/lable_wireless_transceiver_off");
        iconOn = reg.registerIcon("ae2wtx:wireless_transceiver/lable_wireless_transceiver_on");
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int side, int meta) {
        return meta == 1 ? iconOn : iconOff;
    }
}
