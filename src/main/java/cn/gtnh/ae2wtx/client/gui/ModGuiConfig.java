package cn.gtnh.ae2wtx.client.gui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.StatCollector;

import cpw.mods.fml.client.config.DummyConfigElement;
import cpw.mods.fml.client.config.GuiConfig;
import cpw.mods.fml.client.config.GuiConfigEntries;
import cpw.mods.fml.client.config.IConfigElement;
import net.minecraftforge.common.config.ConfigElement;

import cn.gtnh.ae2wtx.AE2Wtx;
import cn.gtnh.ae2wtx.config.ModConfig;

/** In-game config screen for ae2wtx (opened from the Mod List -> Config). */
public class ModGuiConfig extends GuiConfig {

    public ModGuiConfig(GuiScreen parent) {
        super(parent, getConfigElements(), AE2Wtx.MODID, false, false,
            StatCollector.translateToLocal("ae2wtx.config.title"));
    }

    @SuppressWarnings("rawtypes")
    private static List<IConfigElement> getConfigElements() {
        List<IConfigElement> list = new ArrayList<>();
        if (ModConfig.config == null) {
            return list;
        }
        // a slider reads better than a number box for a percentage (client-only class, so set here)
        ModConfig.config.getCategory(ModConfig.CATEGORY_CLIENT).get("panelOpacity")
            .setConfigEntryClass(GuiConfigEntries.NumberSliderEntry.class);
        // The look options sit on the first page: Forge saves only when that
        // page's Done is pressed, so edits made on a sub-page were easy to lose.
        list.addAll(new ConfigElement(ModConfig.config.getCategory(ModConfig.CATEGORY_CLIENT)).getChildElements());
        // the server-side settings stay behind a sub-page; its title shows the element name
        String langKey = "config.wireless";
        list.add(new DummyConfigElement.DummyCategoryElement(StatCollector.translateToLocal(langKey), langKey,
            new ConfigElement(ModConfig.config.getCategory("wireless")).getChildElements()));
        return list;
    }
}
