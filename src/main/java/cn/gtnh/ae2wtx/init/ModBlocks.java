package cn.gtnh.ae2wtx.init;

import net.minecraft.block.Block;

import cn.gtnh.ae2wtx.content.wireless.LabeledWirelessTransceiverBlock;
import cpw.mods.fml.common.registry.GameRegistry;

public final class ModBlocks {

    private ModBlocks() {}

    public static Block blockWirelessTransceiver;

    public static void register() {
        // The only transceiver: registered under the legacy "labeled_wireless_transceiver"
        // name so existing worlds keep their blocks (display name is "Wireless Transceiver").
        blockWirelessTransceiver = new LabeledWirelessTransceiverBlock();
        GameRegistry.registerBlock(blockWirelessTransceiver, "labeled_wireless_transceiver");
    }
}
