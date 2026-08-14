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
        registerChannelCardUpgradeSupport();
    }

    public void postInit(FMLPostInitializationEvent event) {
        registerRecipes();
    }

    /** Client-only hook for the frequency input screen (overridden in ClientProxy). */
    public void openFrequencyScreen(int x, int y, int z, long currentFrequency) {}

    /**
     * Makes the channel card insertable into AE2 upgrade slots (like EAEP):
     * ME Interface (block + part), import/export/storage bus parts, max 1 each.
     * The card claims Upgrades.PATTERN_CAPACITY, which no rv3 machine reads,
     * so it has no fake-upgrade side effects.
     */
    private void registerChannelCardUpgradeSupport() {
        try {
            appeng.api.IAppEngApi api = appeng.api.AEApi.instance();
            appeng.api.config.Upgrades u = appeng.api.config.Upgrades.PATTERN_CAPACITY;
            u.registerItem(api.definitions().blocks().iface(), 1);
            u.registerItem(api.definitions().parts().iface(), 1);
            u.registerItem(api.definitions().parts().importBus(), 1);
            u.registerItem(api.definitions().parts().exportBus(), 1);
            u.registerItem(api.definitions().parts().storageBus(), 1);
            AE2Wtx.LOG.info("Channel card registered as AE2 upgrade (ME Interface + buses, max 1 per device)");
        } catch (Throwable t) {
            AE2Wtx.LOG.warn("Failed to register channel card AE2 upgrade support", t);
        }
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
