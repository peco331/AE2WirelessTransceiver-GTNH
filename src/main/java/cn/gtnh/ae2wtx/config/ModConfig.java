package cn.gtnh.ae2wtx.config;

import java.io.File;
import java.math.BigInteger;

import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

import cn.gtnh.ae2wtx.AE2Wtx;
import cn.gtnh.ae2wtx.network.ServerTaskQueueTickHandler;

public class ModConfig {

    /** The live Configuration (kept for the in-game config GUI). */
    public static Configuration config;

    public static final int DEFAULT_MAX_BANDS_PER_OWNER = 128;
    public static final int MAX_BANDS_PER_OWNER_LIMIT = 10_000;
    public static final int DEFAULT_MAX_BANDS_PER_WORLD = 4_096;
    public static final int MAX_BANDS_PER_WORLD_LIMIT = 100_000;

    /** Active runtime value; changes require a full restart because it changes registry key semantics. */
    public static boolean wirelessCrossDimEnable = true;

    /** Active runtime values; safe to update while the game is running. */
    public static double wirelessTransceiverIdlePower = 10.0;
    public static int wirelessMaxBandsPerOwner = DEFAULT_MAX_BANDS_PER_OWNER;
    public static int wirelessMaxBandsPerWorld = DEFAULT_MAX_BANDS_PER_WORLD;

    /**
     * Client look (category "client"; read but unused on a dedicated server).
     * Changing one rebuilds the rendered chunks.
     */
    public static final String CATEGORY_CLIENT = "client";
    public static final int DEFAULT_EFFECT_RENDER_DISTANCE = 32;
    public static boolean clientDefaultThemeLight = false;
    public static boolean clientRenderEffects = true;
    public static int clientEffectRenderDistance = DEFAULT_EFFECT_RENDER_DISTANCE;
    public static boolean clientSimpleBlockModel = false;

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
    public static synchronized void syncValues(boolean initialLoad) {
        if (config == null) {
            return;
        }
        double previousIdlePower = wirelessTransceiverIdlePower;
        try {
            config.load();
            config.getCategory("wireless").remove("wirelessMaxRange");

            Property propCrossDim = config.get("wireless", "wirelessCrossDimEnable", true,
                "Allow wireless transceivers to bridge channels across dimensions. (Requires full game/server restart)");
            propCrossDim.setRequiresMcRestart(true);
            propCrossDim.setLanguageKey("ae2wtx.config.wirelessCrossDimEnable");
            boolean newCrossDim = propCrossDim.getBoolean(true);

            Property propIdlePower = config.get("wireless", "wirelessTransceiverIdlePower", 10.0,
                "Idle AE power usage per tick of the transceiver grid node (AE/t).");
            propIdlePower.setMinValue(0.0D);
            propIdlePower.setLanguageKey("ae2wtx.config.wirelessTransceiverIdlePower");
            double newIdlePower = propIdlePower.getDouble(10.0D);
            if (!Double.isFinite(newIdlePower) || newIdlePower < 0.0D) {
                AE2Wtx.LOG.warn(
                    "Invalid wireless.wirelessTransceiverIdlePower value '{}'; using 10.0 (must be finite and non-negative).",
                    propIdlePower.getString());
                newIdlePower = 10.0D;
                propIdlePower.set(newIdlePower);
            }

            int newMaxBandsPerOwner = getClampedInt(
                "wirelessMaxBandsPerOwner",
                DEFAULT_MAX_BANDS_PER_OWNER,
                MAX_BANDS_PER_OWNER_LIMIT,
                "Maximum frequencies owned by one owner in the active scope (global when cross-dimension is enabled, "
                    + "otherwise per dimension). 0 disables creation of new frequencies for all owners.");
            int newMaxBandsPerWorld = getClampedInt(
                "wirelessMaxBandsPerWorld",
                DEFAULT_MAX_BANDS_PER_WORLD,
                MAX_BANDS_PER_WORLD_LIMIT,
                "Maximum total number of frequencies in this world save. 0 disables creation of all new frequencies.");

            syncClientValues();

            wirelessTransceiverIdlePower = newIdlePower;
            wirelessMaxBandsPerOwner = newMaxBandsPerOwner;
            wirelessMaxBandsPerWorld = newMaxBandsPerWorld;
            if (Double.compare(previousIdlePower, newIdlePower) != 0) {
                ServerTaskQueueTickHandler.requestIdlePowerRefresh();
            }

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

    private static void syncClientValues() {
        Property theme = config.get(CATEGORY_CLIENT, "defaultTheme", "dark",
            "Look of transceivers that have no theme chosen in their GUI: dark or light.",
            new String[] { "dark", "light" });
        theme.setLanguageKey("ae2wtx.config.defaultTheme");
        String themeValue = theme.getString().trim();
        if (!"dark".equals(themeValue) && !"light".equals(themeValue)) {
            AE2Wtx.LOG.warn("Invalid client.defaultTheme value '{}'; using dark.", themeValue);
            theme.set("dark");
            themeValue = "dark";
        }
        clientDefaultThemeLight = "light".equals(themeValue);

        Property effects = config.get(CATEGORY_CLIENT, "renderEffects", true,
            "Draw the black hole and channel sparks inside online transceivers.");
        effects.setLanguageKey("ae2wtx.config.renderEffects");
        clientRenderEffects = effects.getBoolean(true);

        Property distance = config.get(CATEGORY_CLIENT, "effectRenderDistance", DEFAULT_EFFECT_RENDER_DISTANCE,
            "Blocks within which the black hole and sparks are drawn.", 8, 128);
        distance.setLanguageKey("ae2wtx.config.effectRenderDistance");
        clientEffectRenderDistance = Math.max(8, Math.min(128, distance.getInt(DEFAULT_EFFECT_RENDER_DISTANCE)));

        Property simple = config.get(CATEGORY_CLIENT, "simpleBlockModel", false,
            "Render transceivers as a plain textured cube (vanilla renderer, no effects).");
        simple.setLanguageKey("ae2wtx.config.simpleBlockModel");
        clientSimpleBlockModel = simple.getBoolean(false);
    }

    private static int getClampedInt(String key, int defaultValue, int maxValue, String comment) {
        Property property = config.get("wireless", key, defaultValue, comment);
        property.setMinValue(0);
        property.setMaxValue(maxValue);
        property.setLanguageKey("ae2wtx.config." + key);

        String rawText = property.getString().trim();
        BigInteger rawValue;
        try {
            rawValue = new BigInteger(rawText);
        } catch (NumberFormatException ignored) {
            AE2Wtx.LOG.warn(
                "Invalid wireless.{} value '{}'; using {} (must be an integer in range 0..{}).",
                key,
                rawText,
                defaultValue,
                maxValue);
            property.set(defaultValue);
            return defaultValue;
        }

        BigInteger clampedValue = rawValue.max(BigInteger.ZERO).min(BigInteger.valueOf(maxValue));
        if (!rawValue.equals(clampedValue)) {
            AE2Wtx.LOG.warn(
                "Clamped wireless.{} from {} to {} (valid range: 0..{}).",
                key,
                rawValue,
                clampedValue,
                maxValue);
            property.set(clampedValue.intValue());
        }
        return clampedValue.intValue();
    }
}
