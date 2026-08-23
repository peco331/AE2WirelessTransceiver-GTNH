package cn.gtnh.ae2wtx;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import cn.gtnh.ae2wtx.client.config.ClientConfigChangedHandler;
import cn.gtnh.ae2wtx.content.wireless.LabeledWirelessTransceiverBlock;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Loader;
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
        enableOptionalGtnhLibModel();
    }

    @Override
    public void postInit(FMLPostInitializationEvent event) {
        super.postInit(event);
    }

    /**
     * Enables GTNHLib's JSON model renderer when a compatible version is
     * present. Reflection keeps both the dedicated server and clients without
     * GTNHLib free of hard class links; render type 0 is the vanilla cube
     * fallback backed by the block's registered off/on icons.
     */
    private static void enableOptionalGtnhLibModel() {
        if (!Loader.isModLoaded("gtnhlib")) {
            AE2Wtx.LOG.info("GTNHLib is not installed; using the vanilla wireless transceiver renderer");
            return;
        }

        try {
            ClassLoader loader = ClientProxy.class.getClassLoader();
            Class<?> registryClass = Class.forName(
                "com.gtnewhorizon.gtnhlib.client.model.loading.ModelRegistry",
                false,
                loader);
            Method registerModid = registryClass.getMethod("registerModid", String.class);
            Class<?> rendererClass = Class.forName(
                "com.gtnewhorizon.gtnhlib.client.model.ModelISBRH",
                false,
                loader);
            Field renderTypeField = rendererClass.getField("JSON_ISBRH_ID");

            registerModid.invoke(null, AE2Wtx.MODID);
            int renderType = renderTypeField.getInt(null);
            if (renderType <= 0) {
                throw new IllegalStateException("GTNHLib returned invalid JSON render type " + renderType);
            }
            LabeledWirelessTransceiverBlock.setOptionalModelRenderType(renderType);
            AE2Wtx.LOG.info("Enabled optional GTNHLib model renderer (render type {})", renderType);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            AE2Wtx.LOG.warn(
                "GTNHLib is present but its model API is incompatible; using the vanilla wireless transceiver renderer ({})",
                e.toString());
            AE2Wtx.LOG.debug("GTNHLib optional model integration failure", e);
        }
    }
}
