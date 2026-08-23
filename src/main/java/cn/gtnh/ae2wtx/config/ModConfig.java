package cn.gtnh.ae2wtx.config;

import java.io.File;

import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;
import cpw.mods.fml.client.event.ConfigChangedEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

import cn.gtnh.ae2wtx.AE2Wtx;

public class ModConfig {

    /** The live Configuration (kept for the in-game config GUI). */
    public static Configuration config;

    /** Active runtime value (loaded once at game/server startup; requires full restart to change). */
    public static boolean wirelessCrossDimEnable = true;
    public static double wirelessTransceiverIdlePower = 10.0;

    private static boolean initialLoadDone = false;

    public static void load(File file) {
        config = new Configuration(file);
        initialLoadDone = false;
        syncValues(true);
    }

    /**
     * (Re)read values from the Configuration.
     * @param initialLoad true on game/server startup; false on in-game config GUI changes.
     */
    public static void syncValues(boolean initialLoad) {
        if (config == null) {
            return;
        }
        try {
            config.load();
            config.getCategory("wireless").remove("wirelessMaxRange");

            Property propCrossDim = config.get("wireless", "wirelessCrossDimEnable", true,
                "Allow wireless transceivers to bridge channels across dimensions. (Requires full game/server restart)");
            propCrossDim.setRequiresMcRestart(true);
            boolean newCrossDim = propCrossDim.getBoolean(true);

            Property propIdlePower = config.get("wireless", "wirelessTransceiverIdlePower", 10.0,
                "Idle AE power usage per tick of the transceiver grid node (AE/t).");
            wirelessTransceiverIdlePower = propIdlePower.getDouble(10.0);

            if (initialLoad || !initialLoadDone) {
                wirelessCrossDimEnable = newCrossDim;
                initialLoadDone = true;
            } else if (newCrossDim != wirelessCrossDimEnable) {
                AE2Wtx.LOG.info("Wireless cross-dimension setting changed in config (to {}); full game/server restart required to apply.", newCrossDim);
            }
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
            syncValues(false);
        }
    }
}
