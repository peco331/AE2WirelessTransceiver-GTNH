package cn.gtnh.ae2wtx.init;

import net.minecraft.block.Block;

import cn.gtnh.ae2wtx.content.wireless.LabeledWirelessTransceiverBlock;
import cn.gtnh.ae2wtx.content.wireless.WirelessTransceiverBlock;
import cpw.mods.fml.common.registry.GameRegistry;

public final class ModBlocks {

    private ModBlocks() {}

    public static Block blockWirelessTransceiver;
    public static Block blockLabeledWirelessTransceiver;

    public static void register() {
        blockWirelessTransceiver = new WirelessTransceiverBlock();
        GameRegistry.registerBlock(blockWirelessTransceiver, "wireless_transceiver");

        blockLabeledWirelessTransceiver = new LabeledWirelessTransceiverBlock();
        GameRegistry.registerBlock(blockLabeledWirelessTransceiver, "labeled_wireless_transceiver");
    }
}
