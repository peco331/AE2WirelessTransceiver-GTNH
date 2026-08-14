package cn.gtnh.ae2wtx;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import cn.gtnh.ae2wtx.config.ModConfig;
import cn.gtnh.ae2wtx.gui.ModGuiHandler;
import cn.gtnh.ae2wtx.init.ModBlockEntities;
import cn.gtnh.ae2wtx.init.ModBlocks;
import cn.gtnh.ae2wtx.init.ModItems;
import cn.gtnh.ae2wtx.network.NetworkHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLInterModComms;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import gregtech.api.util.GTModHandler;

public class CommonProxy {

    public void preInit(FMLPreInitializationEvent event) {
        ModConfig.load(event.getSuggestedConfigurationFile());
        ModBlocks.register();
        ModItems.register();
        ModBlockEntities.register();
        NetworkHandler.init();
        ModGuiHandler.register();
    }

    public void init(FMLInitializationEvent event) {
        FMLInterModComms.sendMessage("Waila", "register", "cn.gtnh.ae2wtx.compat.WailaProvider.callback");
    }

    public void postInit(FMLPostInitializationEvent event) {
        registerRecipes();
    }

    private void registerRecipes() {
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

        // Channel card = Advanced Card + Wireless Transceiver (shapeless)
        ItemStack advCard = appeng.items.materials.MaterialType.AdvCard.stack(1);
        if (advCard == null) {
            AE2Wtx.LOG.warn("AE2 advanced card not found, skipping channel card recipe");
        } else {
            GTModHandler.addShapelessCraftingRecipe(
                new ItemStack(ModItems.itemChannelCard),
                new Object[] { advCard, new ItemStack(ModBlocks.blockWirelessTransceiver) });
        }

        // Labeled transceiver = paper x4 + emerald x4 + wireless transceiver
        GTModHandler.addCraftingRecipe(
            new ItemStack(ModBlocks.blockLabeledWirelessTransceiver),
            GTModHandler.RecipeBits.BITS_STD,
            new Object[] { "CAC", "ABA", "CAC", 'A', new ItemStack(Items.paper), 'B',
                new ItemStack(ModBlocks.blockWirelessTransceiver), 'C', new ItemStack(Items.emerald) });
    }
}
