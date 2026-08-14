package cn.gtnh.ae2wtx.client.gui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.StatCollector;

import cpw.mods.fml.client.config.GuiConfig;
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

    private static List<IConfigElement> getConfigElements() {
        List<IConfigElement> list = new ArrayList<>();
        if (ModConfig.config != null) {
            for (String category : new String[] { "wireless" }) {
                list.add(new ConfigElement(ModConfig.config.getCategory(category)));
            }
        }
        return list;
    }
}
