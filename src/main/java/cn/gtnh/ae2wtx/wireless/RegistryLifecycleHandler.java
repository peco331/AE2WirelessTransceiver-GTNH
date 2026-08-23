package cn.gtnh.ae2wtx.wireless;

import net.minecraftforge.event.world.WorldEvent;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/** Releases runtime-only AE nodes without touching persisted band metadata. */
public final class RegistryLifecycleHandler {

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        if (event.world == null || event.world.isRemote) {
            return;
        }
        LabelNetworkRegistry registry = LabelNetworkRegistry.getExisting(event.world);
        if (registry != null) {
            registry.unloadRuntimeNodes(event.world.provider.dimensionId);
        }
    }
}
