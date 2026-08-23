package cn.gtnh.ae2wtx.client.config;

import cn.gtnh.ae2wtx.AE2Wtx;
import cn.gtnh.ae2wtx.config.ModConfig;
import cpw.mods.fml.client.event.ConfigChangedEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/** Reloads runtime-safe values after the client-side Forge config screen saves them. */
public final class ClientConfigChangedHandler {

    @SubscribeEvent
    public void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
        if (AE2Wtx.MODID.equals(event.modID)) {
            ModConfig.syncValues(false);
        }
    }
}
