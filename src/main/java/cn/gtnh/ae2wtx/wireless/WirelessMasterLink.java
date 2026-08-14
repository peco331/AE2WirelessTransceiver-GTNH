package cn.gtnh.ae2wtx.wireless;

import java.util.UUID;

import net.minecraft.world.World;

/**
 * Master transceiver link logic: registers the unique master for (dimension,
 * frequency, owner) on load/frequency-change, unregisters on unload/removal.
 */
public class WirelessMasterLink {

    private final IWirelessEndpoint host;
    private long frequency; // 0 = unset
    private boolean registered;
    private UUID placerId;

    public WirelessMasterLink(IWirelessEndpoint host) {
        this.host = host;
    }

    public void setPlacerId(UUID placerId) {
        this.placerId = placerId;
    }

    public long getFrequency() {
        return frequency;
    }

    public void setFrequency(long frequency) {
        if (this.frequency != frequency) {
            if (registered) {
                unregister();
            }
            this.frequency = frequency;
        }
        // Correct registration state even if frequency did not change:
        // - switching from slave back to master re-registers;
        // - frequency 0 or removed endpoint stays unregistered.
        if (frequency != 0L && !host.isEndpointRemoved()) {
            if (!registered) {
                register();
            }
        } else if (registered) {
            unregister();
        }
    }

    private boolean register() {
        World world = host.getWorld();
        if (world == null || frequency == 0L) {
            return false;
        }
        boolean ok = WirelessMasterRegistry.register(world, frequency, placerId, host);
        this.registered = ok;
        return ok;
    }

    private void unregister() {
        World world = host.getWorld();
        if (!registered || world == null || frequency == 0L) {
            return;
        }
        WirelessMasterRegistry.unregister(world, frequency, placerId, host);
        registered = false;
    }

    public void onUnloadOrRemove() {
        unregister();
    }
}
