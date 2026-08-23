package cn.gtnh.ae2wtx;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;

import cn.gtnh.ae2wtx.init.ModBlocks;
import cn.gtnh.ae2wtx.command.AE2WtxCommand;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.event.FMLServerStoppedEvent;
import cn.gtnh.ae2wtx.network.ServerTaskQueue;

@Mod(
    modid = AE2Wtx.MODID,
    version = Tags.VERSION,
    name = AE2Wtx.NAME,
    acceptedMinecraftVersions = "[1.7.10]",
    dependencies = "required-after:appliedenergistics2@[rv3-beta-1034-GTNH,);"
        + "required-after:gregtech@[5.09.52.594,);after:gtnhlib",
    guiFactory = "cn.gtnh.ae2wtx.client.gui.ModGuiFactory")
public class AE2Wtx {

    public static final String MODID = "ae2wtx";
    public static final String NAME = "AE2 Wireless Transceiver (GTNH)";
    public static final Logger LOG = LogManager.getLogger(MODID);

    @Mod.Instance(MODID)
    public static AE2Wtx instance;

    public static final CreativeTabs CREATIVE_TAB = new CreativeTabs(MODID) {

        @Override
        public Item getTabIconItem() {
            return Item.getItemFromBlock(ModBlocks.blockWirelessTransceiver);
        }
    };

    @SidedProxy(clientSide = "cn.gtnh.ae2wtx.ClientProxy", serverSide = "cn.gtnh.ae2wtx.CommonProxy")
    public static CommonProxy proxy;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        proxy.preInit(event);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init(event);
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        proxy.postInit(event);
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new AE2WtxCommand());
    }

    @Mod.EventHandler
    public void serverStopped(FMLServerStoppedEvent event) {
        ServerTaskQueue.shutdown();
    }
}
