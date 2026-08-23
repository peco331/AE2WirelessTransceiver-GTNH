package cn.gtnh.ae2wtx;

import net.minecraft.item.ItemStack;

import cn.gtnh.ae2wtx.config.ModConfig;
import cn.gtnh.ae2wtx.gui.ModGuiHandler;
import cn.gtnh.ae2wtx.init.ModBlockEntities;
import cn.gtnh.ae2wtx.init.ModBlocks;
import cn.gtnh.ae2wtx.network.NetworkHandler;
import cn.gtnh.ae2wtx.network.ServerTaskQueueTickHandler;
import cn.gtnh.ae2wtx.compat.LockedTransceiverBreakHandler;
import cn.gtnh.ae2wtx.wireless.RegistryLifecycleHandler;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLInterModComms;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import gregtech.api.util.GTModHandler;
import net.minecraftforge.common.MinecraftForge;

public class CommonProxy {

    public void preInit(FMLPreInitializationEvent event) {
        ModConfig.load(event.getSuggestedConfigurationFile());
        ModBlocks.register();
        ModBlockEntities.register();
        NetworkHandler.init();
        ModGuiHandler.register();
    }

    public void init(FMLInitializationEvent event) {
        FMLInterModComms.sendMessage("Waila", "register", "cn.gtnh.ae2wtx.compat.WailaProvider.callback");
        cn.gtnh.ae2wtx.compat.WrenchHandler.register();
        FMLCommonHandler.instance().bus().register(new ServerTaskQueueTickHandler());
        MinecraftForge.EVENT_BUS.register(new LockedTransceiverBreakHandler());
        MinecraftForge.EVENT_BUS.register(new RegistryLifecycleHandler());
    }

    public void postInit(FMLPostInitializationEvent event) {
        registerRecipes();
    }

    private void registerRecipes() {
        // Wireless transceiver = Quantum Ring x8 + Quantum Link (the classic recipe)
        ItemStack ring = appeng.api.AEApi.instance().blocks().blockQuantumRing.stack(1);
        ItemStack link = appeng.api.AEApi.instance().blocks().blockQuantumLink.stack(1);
        if (ring == null || link == null) {
            AE2Wtx.LOG.warn("AE2 quantum ring/link not found, skipping wireless transceiver recipe");
        } else {
            GTModHandler.addCraftingRecipe(
                new ItemStack(ModBlocks.blockWirelessTransceiver),
                GTModHandler.RecipeBits.BITS_STD,
                new Object[] { "RRR", "RLR", "RRR", 'R', ring, 'L', link });
        }
    }
}
