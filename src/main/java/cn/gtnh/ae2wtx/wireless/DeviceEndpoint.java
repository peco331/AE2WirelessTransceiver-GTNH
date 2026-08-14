package cn.gtnh.ae2wtx.wireless;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import net.minecraft.world.World;

import appeng.api.networking.IGridNode;

/**
 * IWirelessEndpoint adapter wrapping an AE2 device (interface part/block,
 * import/export/storage bus part). The device's own grid node is used to
 * establish the wireless grid connection to the master transceiver.
 */
public final class DeviceEndpoint implements IWirelessEndpoint {

    private final Supplier<World> worldSupplier;
    private final Supplier<IGridNode> nodeSupplier;
    private final BooleanSupplier removedSupplier;
    private final int x;
    private final int y;
    private final int z;

    public DeviceEndpoint(Supplier<World> worldSupplier, Supplier<IGridNode> nodeSupplier,
        BooleanSupplier removedSupplier, int x, int y, int z) {
        this.worldSupplier = worldSupplier;
        this.nodeSupplier = nodeSupplier;
        this.removedSupplier = removedSupplier;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public World getWorld() {
        return worldSupplier.get();
    }

    @Override
    public int getX() {
        return x;
    }

    @Override
    public int getY() {
        return y;
    }

    @Override
    public int getZ() {
        return z;
    }

    @Override
    public IGridNode getGridNode() {
        return nodeSupplier.get();
    }

    @Override
    public boolean isEndpointRemoved() {
        return removedSupplier.getAsBoolean();
    }
}
