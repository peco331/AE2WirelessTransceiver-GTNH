package cn.gtnh.ae2wtx.wireless;

import net.minecraft.world.World;

import appeng.api.networking.IGridNode;

/**
 * Minimal wireless endpoint interface. The wireless transceiver tile entities
 * implement this so the wireless logic can access world, position and the AE2 node.
 */
public interface IWirelessEndpoint {

    /** Server world the block lives in. */
    World getWorld();

    int getX();

    int getY();

    int getZ();

    /** The AE2 node used for connections (usually the primary node). */
    IGridNode getGridNode();

    /** Whether the endpoint was removed/destroyed; used to stop connections on unload. */
    boolean isEndpointRemoved();
}
