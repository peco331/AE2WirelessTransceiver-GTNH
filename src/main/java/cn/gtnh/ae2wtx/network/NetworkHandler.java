package cn.gtnh.ae2wtx.network;

import cn.gtnh.ae2wtx.AE2Wtx;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;

public final class NetworkHandler {

    private NetworkHandler() {}

    public static final SimpleNetworkWrapper CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel("ae2wtx|net");

    private static int nextId = 0;

    public static void init() {
        CHANNEL.registerMessage(
            LabelApplyPacket.Handler.class,
            LabelApplyPacket.class,
            nextId++,
            Side.SERVER);
        CHANNEL.registerMessage(
            LabelListRequestPacket.Handler.class,
            LabelListRequestPacket.class,
            nextId++,
            Side.SERVER);
        CHANNEL.registerMessage(
            LabelDeletePacket.Handler.class,
            LabelDeletePacket.class,
            nextId++,
            Side.SERVER);
        CHANNEL.registerMessage(
            LabelListResponsePacket.Handler.class,
            LabelListResponsePacket.class,
            nextId++,
            Side.CLIENT);
        AE2Wtx.LOG.info("ae2wtx network channel initialized");
    }
}
