package cn.gtnh.ae2wtx.client.config;

import net.minecraft.client.Minecraft;

import cn.gtnh.ae2wtx.AE2Wtx;
import cn.gtnh.ae2wtx.config.ModConfig;
import cpw.mods.fml.client.event.ConfigChangedEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/** Reloads runtime-safe values after the client-side Forge config screen saves them. */
public final class ClientConfigChangedHandler {

    @SubscribeEvent
    public void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
        if (!AE2Wtx.MODID.equals(event.modID)) {
            return;
        }
        boolean light = ModConfig.clientDefaultThemeLight;
        boolean simple = ModConfig.clientSimpleBlockModel;
        ModConfig.syncValues(false);
        // the block shape and textures are baked into the chunk meshes
        Minecraft mc = Minecraft.getMinecraft();
        if ((light != ModConfig.clientDefaultThemeLight || simple != ModConfig.clientSimpleBlockModel)
            && mc.renderGlobal != null) {
            mc.renderGlobal.loadRenderers();
        }
    }
}
