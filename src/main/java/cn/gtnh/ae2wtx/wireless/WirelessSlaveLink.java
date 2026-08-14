package cn.gtnh.ae2wtx.wireless;

import java.util.Objects;
import java.util.UUID;

import net.minecraft.world.World;

import cn.gtnh.ae2wtx.config.ModConfig;
import appeng.api.AEApi;
import appeng.api.exceptions.FailedConnection;
import appeng.api.networking.IGridConnection;
import appeng.api.networking.IGridNode;

/**
 * Slave transceiver link logic:
 * - looks up the master by frequency;
 * - validates distance (<= maxRange) unless cross-dimension is enabled;
 * - dynamically creates/destroys the AE2 grid connection between the slave node
 *   and the master node ("one master, many slaves").
 */
public class WirelessSlaveLink {

    private final IWirelessEndpoint host;
    private long frequency; // 0 = unset
    private UUID placerId;

    private IGridConnection connection;
    private boolean shutdown = true;
    private double distance;

    public WirelessSlaveLink(IWirelessEndpoint host) {
        this.host = Objects.requireNonNull(host);
    }

    public void setPlacerId(UUID placerId) {
        this.placerId = placerId;
    }

    public void setFrequency(long frequency) {
        if (this.frequency != frequency) {
            this.frequency = frequency;
            updateStatus();
        }
    }

    public long getFrequency() {
        return frequency;
    }

    public boolean isConnected() {
        return !shutdown && connection != null;
    }

    public double getDistance() {
        return distance;
    }

    /** Call from serverTick or on frequency/load-state changes. */
    public void updateStatus() {
        if (host.isEndpointRemoved()) {
            destroyConnection();
            return;
        }
        final World world = host.getWorld();
        if (world == null || frequency == 0L) {
            destroyConnection();
            return;
        }

        IWirelessEndpoint master = WirelessMasterRegistry.get(world, frequency, placerId);
        shutdown = false;
        distance = 0.0D;

        boolean crossDim = ModConfig.wirelessCrossDimEnable;
        if (master != null && !master.isEndpointRemoved() && (crossDim || master.getWorld() == world)) {
            if (!crossDim) {
                double dx = master.getX() - host.getX();
                double dy = master.getY() - host.getY();
                double dz = master.getZ() - host.getZ();
                distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            }
            double maxRange = ModConfig.wirelessMaxRange;
            if (crossDim || distance <= maxRange) {
                try {
                    IGridConnection current = connection;
                    IGridNode a = host.getGridNode(); // slave
                    IGridNode b = master.getGridNode(); // master
                    if (a == null || b == null) {
                        shutdown = true;
                    } else {
                        if (current != null) {
                            IGridNode ca = current.a();
                            IGridNode cb = current.b();
                            if ((ca == a || cb == a) && (ca == b || cb == b)) {
                                return; // already connected to the right master
                            }
                            current.destroy();
                            connection = null;
                        }
                        connection = AEApi.instance().createGridConnection(a, b);
                        return;
                    }
                } catch (FailedConnection | IllegalStateException ignored) {
                    // Connection failed/duplicate - fall through to destroy.
                } catch (RuntimeException ignored) {
                    // Any grid hiccup should not crash the server; try again next tick.
                }
            } else {
                shutdown = true; // out of range
            }
        } else {
            shutdown = true; // no master or master unavailable
        }

        destroyConnection();
    }

    public void onUnloadOrRemove() {
        destroyConnection();
    }

    private void destroyConnection() {
        IGridConnection current = connection;
        if (current != null) {
            try {
                current.destroy();
            } catch (RuntimeException ignored) {
                // already gone
            }
            connection = null;
        }
        shutdown = true;
    }
}
