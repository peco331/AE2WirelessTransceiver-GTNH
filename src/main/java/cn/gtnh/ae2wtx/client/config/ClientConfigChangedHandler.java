package cn.gtnh.ae2wtx.client.config;

import net.minecraft.client.Minecraft;

import cn.gtnh.ae2wtx.AE2Wtx;
import cn.gtnh.ae2wtx.config.ModConfig;
import cpw.mods.fml.client.event.ConfigChangedEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/** Applies and saves the values the client-side Forge config screen has just edited. */
public final class ClientConfigChangedHandler {

    @SubscribeEvent
    public void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
        if (!AE2Wtx.MODID.equals(event.modID)) {
            return;
        }
        boolean light = ModConfig.clientDefaultThemeLight;
        boolean simple = ModConfig.clientSimpleBlockModel;
        int panel = ModConfig.clientPanelOpacity;
        boolean arcs = ModConfig.clientGlassArcs;
        // the screen wrote into the in-memory properties: apply them, do not re-read the file
        ModConfig.syncValues(false, false);
        // the block shape and textures are baked into the chunk meshes
        Minecraft mc = Minecraft.getMinecraft();
        boolean meshChanged = light != ModConfig.clientDefaultThemeLight || simple != ModConfig.clientSimpleBlockModel
            || panel != ModConfig.clientPanelOpacity || arcs != ModConfig.clientGlassArcs;
        if (meshChanged && mc.renderGlobal != null) {
            mc.renderGlobal.loadRenderers();
        }
    }
}
