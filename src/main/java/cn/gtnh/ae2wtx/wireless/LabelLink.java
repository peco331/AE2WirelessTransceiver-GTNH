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

    private final IWirelessEndpoint host;
    private LabelNetworkRegistry.LabelNetwork target;
    private IGridConnection connection;

    public LabelLink(IWirelessEndpoint host) {
        this.host = Objects.requireNonNull(host);
    }

    public void setTarget(LabelNetworkRegistry.LabelNetwork target) {
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
        // dimension check: if not cross-dim and the network is scoped to another dimension, disconnect
        Integer targetDim = target.dim();
        if (targetDim != null && targetDim != world.provider.dimensionId) {
            destroyConnection();
            return;
        }

        IGridNode hostNode = host.getGridNode();
        IGridNode targetNode = target.node();
        if (hostNode == null || targetNode == null) {
            destroyConnection();
            return;
        }

        try {
            IGridConnection current = connection;
            if (current != null) {
                IGridNode a = current.a();
                IGridNode b = current.b();
                if ((a == hostNode || b == hostNode) && (a == targetNode || b == targetNode)) {
                    return; // already connected correctly
                }
                current.destroy();
                connection = null;
            }
            connection = AEApi.instance().createGridConnection(hostNode, targetNode);
        } catch (Exception ignored) {
            destroyConnection();
        }
    }

    public void onUnloadOrRemove() {
        this.target = null;
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
