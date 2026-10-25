package cn.gtnh.ae2wtx.client.render;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;

import cn.gtnh.ae2wtx.config.ModConfig;
import cn.gtnh.ae2wtx.content.wireless.LabeledWirelessTransceiverBlockEntity;
import cn.gtnh.ae2wtx.content.wireless.TransceiverTheme;

/** Block-atlas icons of one theme; the PNGs come from tools/art/generate_assets.py. */
public final class TransceiverIcons {

    private static final TransceiverIcons DARK = new TransceiverIcons("dark");
    private static final TransceiverIcons LIGHT = new TransceiverIcons("light");

    private final String dir;
    IIcon beam;
    IIcon bevelOn;
    IIcon bevelOff;
    IIcon portOn;
    IIcon portOff;
    IIcon glass;
    IIcon arcs;
    IIcon fallbackOn;
    IIcon fallbackOff;

    private TransceiverIcons(String theme) {
        this.dir = "ae2wtx:transceiver/" + theme + "/";
    }

    public static void register(IIconRegister reg) {
        DARK.registerAll(reg);
        LIGHT.registerAll(reg);
    }

    private void registerAll(IIconRegister reg) {
        // 1.7.10 prepends "textures/blocks/"
        beam = reg.registerIcon(dir + "beam");
        bevelOn = reg.registerIcon(dir + "bevel_on");
        bevelOff = reg.registerIcon(dir + "bevel_off");
        portOn = reg.registerIcon(dir + "port_on");
        portOff = reg.registerIcon(dir + "port_off");
        glass = reg.registerIcon(dir + "glass");
        arcs = reg.registerIcon(dir + "arcs");
        fallbackOn = reg.registerIcon(dir + "fallback_on");
        fallbackOff = reg.registerIcon(dir + "fallback_off");
    }

    public static TransceiverIcons forTheme(boolean light) {
        return light ? LIGHT : DARK;
    }

    public static TransceiverIcons forTheme(TransceiverTheme theme) {
        return forTheme(theme.isLight(ModConfig.clientDefaultThemeLight));
    }

    public static TransceiverIcons forBlock(IBlockAccess world, int x, int y, int z) {
        TileEntity te = world.getTileEntity(x, y, z);
        return forTheme(te instanceof LabeledWirelessTransceiverBlockEntity
            ? ((LabeledWirelessTransceiverBlockEntity) te).getTheme()
            : TransceiverTheme.DEFAULT);
    }

    public IIcon fallback(boolean on) {
        return on ? fallbackOn : fallbackOff;
    }
}
