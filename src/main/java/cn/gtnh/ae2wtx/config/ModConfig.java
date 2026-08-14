package cn.gtnh.ae2wtx.config;

import java.io.File;

import net.minecraftforge.common.config.Configuration;
import cpw.mods.fml.client.event.ConfigChangedEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

import cn.gtnh.ae2wtx.AE2Wtx;

public class ModConfig {

    /** The live Configuration (kept for the in-game config GUI). */
    public static Configuration config;

    public static boolean wirelessCrossDimEnable = true;
    public static double wirelessTransceiverIdlePower = 10.0;

    public static void load(File file) {
        config = new Configuration(file);
        syncValues();
    }

    /** (Re)read values from the Configuration; called at startup and after in-game config edits. */
    public static void syncValues() {
        if (config == null) {
            return;
        }
        try {
            config.load();
            // legacy option removed when the plain frequency transceiver was dropped
            config.getCategory("wireless").remove("wirelessMaxRange");
            wirelessCrossDimEnable = config
                .get("wireless", "wirelessCrossDimEnable", true,
                    "Allow wireless transceivers to bridge channels across dimensions.")
                .getBoolean(true);
            wirelessTransceiverIdlePower = config
                .get("wireless", "wirelessTransceiverIdlePower", 10.0,
                    "Idle AE power usage per tick of the transceiver grid node (AE/t).")
                .getDouble(10.0);
        } finally {
            if (config.hasChanged()) {
                config.save();
            }
        }
    }

    /** Reload values when the player changes them through the in-game mod list config screen. */
    @SubscribeEvent
    public static void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
        if (event.modID.equals(AE2Wtx.MODID)) {
            syncValues();
        }
    }
}
