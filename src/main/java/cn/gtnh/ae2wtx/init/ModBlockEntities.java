package cn.gtnh.ae2wtx.init;

import cn.gtnh.ae2wtx.content.wireless.WirelessTransceiverBlockEntity;
import cpw.mods.fml.common.registry.GameRegistry;

public final class ModBlockEntities {

    private ModBlockEntities() {}

    public static void register() {
        GameRegistry.registerTileEntity(WirelessTransceiverBlockEntity.class, "ae2wtx_wireless_transceiver");
    }
}
