package cn.gtnh.ae2wtx;

import net.minecraftforge.common.MinecraftForge;

import cn.gtnh.ae2wtx.client.config.ClientConfigChangedHandler;
import cn.gtnh.ae2wtx.client.locate.TransceiverLocator;
import cn.gtnh.ae2wtx.client.render.TransceiverBlockRenderer;
import cn.gtnh.ae2wtx.client.render.TransceiverEffectsRenderer;
import cn.gtnh.ae2wtx.content.wireless.LabeledWirelessTransceiverBlock;
import cn.gtnh.ae2wtx.content.wireless.LabeledWirelessTransceiverBlockEntity;
import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.client.registry.RenderingRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
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
        FMLCommonHandler.instance().bus().register(new ClientConfigChangedHandler());
        MinecraftForge.EVENT_BUS.register(TransceiverLocator.INSTANCE);
        int renderId = RenderingRegistry.getNextAvailableRenderId();
        RenderingRegistry.registerBlockHandler(new TransceiverBlockRenderer(renderId));
        LabeledWirelessTransceiverBlock.setRenderType(renderId);
        ClientRegistry.bindTileEntitySpecialRenderer(LabeledWirelessTransceiverBlockEntity.class,
            new TransceiverEffectsRenderer());
    }

    @Override
    public void postInit(FMLPostInitializationEvent event) {
        super.postInit(event);
    }
}
