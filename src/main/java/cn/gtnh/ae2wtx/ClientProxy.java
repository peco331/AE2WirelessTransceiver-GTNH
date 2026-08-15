package cn.gtnh.ae2wtx;

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
        // GTNHLib modern JSON model system: register our assets so the
        // transceiver renders its blockbench 3D model.
        com.gtnewhorizon.gtnhlib.client.model.loading.ModelRegistry.registerModid(cn.gtnh.ae2wtx.AE2Wtx.MODID);
    }

    @Override
    public void postInit(FMLPostInitializationEvent event) {
        super.postInit(event);
    }
}
