package cn.gtnh.ae2wtx.client;

import cn.gtnh.ae2wtx.client.render.TransceiverRenderer;
import cpw.mods.fml.client.registry.RenderingRegistry;

/** Registers the custom 3D block renderer (Light Mode model). */
public final class ClientRenderHandler {

    private ClientRenderHandler() {}

    public static int renderId = -1;

    public static void init() {
        renderId = RenderingRegistry.getNextAvailableRenderId();
        RenderingRegistry.registerBlockHandler(renderId, new TransceiverRenderer());
    }
}
