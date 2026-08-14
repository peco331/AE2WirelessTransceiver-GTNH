package cn.gtnh.ae2wtx.config;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

public class ModConfig {

    public static boolean wirelessCrossDimEnable = true;
    public static double wirelessTransceiverIdlePower = 10.0;

    public static void load(File file) {
        Configuration cfg = new Configuration(file);
        try {
            cfg.load();
            wirelessCrossDimEnable = cfg
                .get("wireless", "wirelessCrossDimEnable", true, "Allow wireless transceivers to bridge channels across dimensions.")
                .getBoolean(true);
            wirelessTransceiverIdlePower = cfg
                .get("wireless", "wirelessTransceiverIdlePower", 10.0, "Idle AE power usage per tick of the transceiver grid node (AE/t).")
                .getDouble(10.0);
        } finally {
            if (cfg.hasChanged()) {
                cfg.save();
            }
        }
    }
}
