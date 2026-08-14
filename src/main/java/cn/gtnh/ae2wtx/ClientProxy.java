package cn.gtnh.ae2wtx;

import net.minecraft.client.Minecraft;

import cn.gtnh.ae2wtx.client.screen.FrequencyInputGui;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

public class ClientProxy extends CommonProxy {

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);
    }

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);
    }

    @Override
    public void postInit(FMLPostInitializationEvent event) {
        super.postInit(event);
    }

    @Override
    public void openFrequencyScreen(int x, int y, int z, long currentFrequency) {
        Minecraft.getMinecraft().displayGuiScreen(new FrequencyInputGui(x, y, z, currentFrequency));
    }
}
