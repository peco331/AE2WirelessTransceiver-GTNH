package cn.gtnh.ae2wtx.gui;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

import cn.gtnh.ae2wtx.AE2Wtx;
import cn.gtnh.ae2wtx.client.screen.FrequencyInputGui;
import cn.gtnh.ae2wtx.client.screen.LabeledTransceiverGui;
import cn.gtnh.ae2wtx.content.wireless.LabeledWirelessTransceiverBlock;
import cn.gtnh.ae2wtx.content.wireless.WirelessTransceiverBlock;
import cpw.mods.fml.common.network.IGuiHandler;

public class ModGuiHandler implements IGuiHandler {

    public static final int GUI_FREQUENCY = 0;
    public static final int GUI_LABELED = 1;

    @Override
    public Object getServerGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        switch (id) {
            case GUI_FREQUENCY:
                return world.getBlock(x, y, z) instanceof WirelessTransceiverBlock ? new FrequencyContainer() : null;
            case GUI_LABELED:
                return world.getBlock(x, y, z) instanceof LabeledWirelessTransceiverBlock ? new LabeledContainer() : null;
            default:
                return null;
        }
    }

    @Override
    public Object getClientGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        switch (id) {
            case GUI_FREQUENCY:
                return world.getBlock(x, y, z) instanceof WirelessTransceiverBlock
                    ? new FrequencyInputGui(new FrequencyContainer(), player, x, y, z)
                    : null;
            case GUI_LABELED:
                return world.getBlock(x, y, z) instanceof LabeledWirelessTransceiverBlock
                    ? new LabeledTransceiverGui(new LabeledContainer(), player, x, y, z)
                    : null;
            default:
                return null;
        }
    }

    public static void register() {
        cpw.mods.fml.common.network.NetworkRegistry.INSTANCE.registerGuiHandler(AE2Wtx.instance, new ModGuiHandler());
    }
}
