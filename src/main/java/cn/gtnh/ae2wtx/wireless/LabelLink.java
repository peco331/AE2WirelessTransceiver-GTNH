package cn.gtnh.ae2wtx.wireless;

import java.util.Objects;

import net.minecraft.world.World;

import appeng.api.AEApi;
import appeng.api.networking.IGridConnection;
import appeng.api.networking.IGridNode;

/**
 * Labeled wireless transceiver connector: maintains the grid connection between
 * the block entity's in-world node and the label network's virtual node.
 */
public class LabelLink {

    private static final long RETRY_DELAY_TICKS = 100L;

    private final IWirelessEndpoint host;
    private LabelNetworkRegistry.LabelNetwork target;
    private IGridConnection connection;
    private long retryNotBeforeTick = Long.MIN_VALUE;

    public LabelLink(IWirelessEndpoint host) {
        this.host = Objects.requireNonNull(host);
    }

    public void setTarget(LabelNetworkRegistry.LabelNetwork target) {
        if (this.target != target) {
            destroyConnection();
            retryNotBeforeTick = Long.MIN_VALUE;
        }
        this.target = target;
        updateStatus();
    }

    public void clearTarget() {
        setTarget(null);
    }

    public boolean isConnected() {
        return connection != null;
    }

    /** Call from serverTick or on label changes. */
    public void updateStatus() {
        if (host.isEndpointRemoved()) {
            destroyConnection();
            return;
        }
        if (target == null) {
            destroyConnection();
            return;
        }
        World world = host.getWorld();
        if (world == null) {
            destroyConnection();
            return;
        }
        long now = world.getTotalWorldTime();
        // dimension check: if not cross-dim and the network is scoped to another dimension, disconnect
        Integer targetDim = target.dim();
        if (targetDim != null && targetDim != world.provider.dimensionId) {
            destroyConnection();
            return;
        }

        IGridNode hostNode = host.getGridNode();
        IGridNode targetNode = target.node();
        if (targetNode == null) {
            if (now < retryNotBeforeTick) {
                destroyConnection();
                return;
            }
            if (target.ensureVirtualNode(world)) {
                targetNode = target.node();
            } else {
                retryNotBeforeTick = now + RETRY_DELAY_TICKS;
            }
        }
        if (hostNode == null || targetNode == null) {
            destroyConnection();
            return;
        }

        if (connection == null && now < retryNotBeforeTick) {
            return;
        }

        try {
            IGridConnection current = connection;
            if (current != null) {
                IGridNode a = current.a();
                IGridNode b = current.b();
                if ((a == hostNode || b == hostNode) && (a == targetNode || b == targetNode)) {
                    retryNotBeforeTick = Long.MIN_VALUE;
                    return; // already connected correctly
                }
                current.destroy();
                connection = null;
            }
            // Do not copy or align AE2 security keys. createGridConnection must
            // perform the normal permission check; a denial is handled below
            // by leaving this link disconnected.
            connection = AEApi.instance().createGridConnection(hostNode, targetNode);
            retryNotBeforeTick = Long.MIN_VALUE;
        } catch (Exception ignored) {
            destroyConnection();
            retryNotBeforeTick = now + RETRY_DELAY_TICKS;
        }
    }

    public void onUnloadOrRemove() {
        this.target = null;
        retryNotBeforeTick = Long.MIN_VALUE;
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
    }
}
