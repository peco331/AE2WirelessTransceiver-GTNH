package cn.gtnh.ae2wtx.wireless;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import net.minecraft.world.World;

import cn.gtnh.ae2wtx.config.ModConfig;

/**
 * Registry of wireless master endpoints, keyed by (dimension, frequency, owner).
 * A master registers itself on load/frequency-change and unregisters on unload/removal.
 * Slaves look up their master here by frequency (one master serves many slaves).
 * <p>
 * Owner isolation: the owner UUID is the placing player's UUID (public mode uses
 * PUBLIC_NETWORK_UUID when no owner is set). GTNH has no FTB Teams, so no team layer.
 */
public final class WirelessMasterRegistry {

    private WirelessMasterRegistry() {}

    private static final Map<Key, WeakReference<IWirelessEndpoint>> MASTERS = new HashMap<>();

    /** UUID used for transceivers without an owner (public mode). */
    public static final UUID PUBLIC_NETWORK_UUID = new UUID(0, 0);

    public static synchronized boolean register(World world, long frequency, UUID placerId, IWirelessEndpoint endpoint) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(endpoint, "endpoint");
        if (frequency == 0L) {
            return false;
        }
        UUID owner = placerId != null ? placerId : PUBLIC_NETWORK_UUID;
        Key key = new Key(useGlobal() ? null : world.provider.dimensionId, frequency, owner);

        cleanupIfCleared(key);
        WeakReference<IWirelessEndpoint> ref = MASTERS.get(key);
        IWirelessEndpoint existing = ref == null ? null : ref.get();
        if (existing != null && !existing.isEndpointRemoved()) {
            // Same dimension + frequency + owner already has a master.
            return false;
        }
        MASTERS.put(key, new WeakReference<>(endpoint));
        return true;
    }

    public static synchronized void unregister(World world, long frequency, UUID placerId, IWirelessEndpoint endpoint) {
        if (frequency == 0L || world == null) {
            return;
        }
        UUID owner = placerId != null ? placerId : PUBLIC_NETWORK_UUID;
        Key key = new Key(useGlobal() ? null : world.provider.dimensionId, frequency, owner);
        WeakReference<IWirelessEndpoint> ref = MASTERS.get(key);
        if (ref != null) {
            IWirelessEndpoint cur = ref.get();
            if (cur == null || cur == endpoint) {
                MASTERS.remove(key);
            }
        }
    }

    public static synchronized IWirelessEndpoint get(World world, long frequency, UUID placerId) {
        if (frequency == 0L || world == null) {
            return null;
        }
        UUID owner = placerId != null ? placerId : PUBLIC_NETWORK_UUID;
        Key key = new Key(useGlobal() ? null : world.provider.dimensionId, frequency, owner);
        cleanupIfCleared(key);
        WeakReference<IWirelessEndpoint> ref = MASTERS.get(key);
        return ref == null ? null : ref.get();
    }

    private static void cleanupIfCleared(Key key) {
        WeakReference<IWirelessEndpoint> ref = MASTERS.get(key);
        if (ref != null && ref.get() == null) {
            MASTERS.remove(key);
        }
    }

    private static boolean useGlobal() {
        return ModConfig.wirelessCrossDimEnable;
    }

    private static final class Key {

        final Integer dim; // null = global (cross-dimension)
        final long freq;
        final UUID owner;

        Key(Integer dim, long freq, UUID owner) {
            this.dim = dim;
            this.freq = freq;
            this.owner = owner;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Key)) {
                return false;
            }
            Key k = (Key) o;
            return Objects.equals(dim, k.dim) && freq == k.freq && Objects.equals(owner, k.owner);
        }

        @Override
        public int hashCode() {
            return Objects.hash(dim, freq, owner);
        }
    }
}
