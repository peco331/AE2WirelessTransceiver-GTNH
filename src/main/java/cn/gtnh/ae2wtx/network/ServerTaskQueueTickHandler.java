package cn.gtnh.ae2wtx.network;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;

import cn.gtnh.ae2wtx.config.ModConfig;
import cn.gtnh.ae2wtx.content.wireless.LabeledWirelessTransceiverBlockEntity;
import cn.gtnh.ae2wtx.wireless.LabelNetworkRegistry;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/** Register an instance on the FML event bus. */
public final class ServerTaskQueueTickHandler {

    private static final AtomicBoolean IDLE_POWER_REFRESH_REQUESTED = new AtomicBoolean();

    /** Ticks between sweeps for saved transceivers that are gone (30 s). */
    private static final int PRUNE_INTERVAL_TICKS = 600;

    private int cleanupTick;
    private int pruneTick;

    /** Safe to call from the client config event or the server command thread. */
    public static void requestIdlePowerRefresh() {
        IDLE_POWER_REFRESH_REQUESTED.set(true);
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            ServerTaskQueue.drain(MinecraftServer.getServer());
            if (IDLE_POWER_REFRESH_REQUESTED.getAndSet(false)) {
                refreshLoadedIdlePowerUsage();
            }
            if (++cleanupTick >= 1200) {
                cleanupTick = 0;
                ServerTaskQueue.cleanRateLimiters();
            }
            if (++pruneTick >= PRUNE_INTERVAL_TICKS) {
                pruneTick = 0;
                pruneMissingTransceivers();
            }
        }
    }

    /** Only loaded chunks are checked, so a transceiver is judged once a player is near it. */
    private static void pruneMissingTransceivers() {
        if (!ModConfig.wirelessPruneMissingTransceivers) {
            return;
        }
        WorldServer overworld = DimensionManager.getWorld(0);
        LabelNetworkRegistry registry = overworld == null ? null : LabelNetworkRegistry.getExisting(overworld);
        if (registry != null) {
            registry.pruneEndpoints(LabelNetworkRegistry::isMissingInLoadedChunk);
        }
    }

    /** Notify AE2's energy cache for every loaded transceiver after a runtime config reload. */
    private static void refreshLoadedIdlePowerUsage() {
        for (WorldServer world : DimensionManager.getWorlds()) {
            if (world == null) {
                continue;
            }
            for (Object tile : new ArrayList<>(world.loadedTileEntityList)) {
                if (tile instanceof LabeledWirelessTransceiverBlockEntity) {
                    ((LabeledWirelessTransceiverBlockEntity) tile).refreshIdlePowerUsage();
                }
            }
        }
    }
}
