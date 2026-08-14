package cn.gtnh.ae2wtx.gui;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

import cn.gtnh.ae2wtx.AE2Wtx;
import cn.gtnh.ae2wtx.client.screen.LabeledTransceiverGui;
import cn.gtnh.ae2wtx.content.wireless.LabeledWirelessTransceiverBlock;
import cpw.mods.fml.common.network.IGuiHandler;

public class ModGuiHandler implements IGuiHandler {

    public static final int GUI_LABELED = 1;

    @Override
    public Object getServerGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        if (id == GUI_LABELED && world.getBlock(x, y, z) instanceof LabeledWirelessTransceiverBlock) {
            return new LabeledContainer();
        }
        return null;
    }

    @Override
    public Object getClientGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        if (id == GUI_LABELED && world.getBlock(x, y, z) instanceof LabeledWirelessTransceiverBlock) {
            return new LabeledTransceiverGui(new LabeledContainer(), player, x, y, z);
        }
        return null;
    }

    public static void register() {
        cpw.mods.fml.common.network.NetworkRegistry.INSTANCE.registerGuiHandler(AE2Wtx.instance, new ModGuiHandler());
    }
}
