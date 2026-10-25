package cn.gtnh.ae2wtx.network;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraftforge.common.DimensionManager;

import cn.gtnh.ae2wtx.content.wireless.LabeledWirelessTransceiverBlockEntity;
import cn.gtnh.ae2wtx.wireless.LabelNetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/** C2S: list the saved transceivers of one frequency, for the GUI's locate view. */
public class LabelEndpointsRequestPacket implements IMessage {

    /** Upper bound of positions sent back; the response still carries the real total. */
    public static final int MAX_ENDPOINTS = 512;

    private int dimension;
    private int x;
    private int y;
    private int z;
    private int windowId;
    private int requestId;
    private String label = "";
    private boolean valid = true;

    public LabelEndpointsRequestPacket() {}

    public LabelEndpointsRequestPacket(int dimension, int x, int y, int z, int windowId, int requestId,
        String label) {
        this.dimension = dimension;
        this.x = x;
        this.y = y;
        this.z = z;
        this.windowId = windowId;
        this.requestId = requestId;
        this.label = label == null ? "" : label;
        this.valid = true;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        if (buf == null || buf.readableBytes() < 26) {
            valid = false;
            return;
        }
        dimension = buf.readInt();
        x = buf.readInt();
        y = buf.readInt();
        z = buf.readInt();
        windowId = buf.readInt();
        requestId = buf.readInt();
        label = NetworkBufferUtils.tryReadUtf8(buf, 256);
        if (label == null) {
            valid = false;
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(dimension);
        buf.writeInt(x);
        buf.writeInt(y);
        buf.writeInt(z);
        buf.writeInt(windowId);
        buf.writeInt(requestId);
        NetworkBufferUtils.writeUtf8(buf, label, 256);
    }

    public static class Handler implements IMessageHandler<LabelEndpointsRequestPacket, IMessage> {

        @Override
        public IMessage onMessage(LabelEndpointsRequestPacket msg, MessageContext ctx) {
            if (!msg.valid || ctx == null || ctx.getServerHandler() == null) {
                return null;
            }
            EntityPlayerMP sender = ctx.getServerHandler().playerEntity;
            final int dimension = msg.dimension;
            final int x = msg.x;
            final int y = msg.y;
            final int z = msg.z;
            final int windowId = msg.windowId;
            final int requestId = msg.requestId;
            final String label = msg.label;
            ServerTaskQueue.enqueue(sender, ServerTaskQueue.TaskType.LIST,
                player -> respond(player, dimension, x, y, z, windowId, requestId, label));
            return null;
        }

        static void respond(EntityPlayerMP player, int dimension, int x, int y, int z, int windowId, int requestId,
            String rawLabel) {
            LabeledWirelessTransceiverBlockEntity lte = ServerPacketValidation
                .getTransceiver(player, dimension, x, y, z, windowId);
            if (lte == null) {
                return;
            }
            World world = player.worldObj;
            LabelNetworkRegistry reg = LabelNetworkRegistry.get(world);
            String label = LabelNetworkRegistry.normalizeLabel(rawLabel);
            if (reg == null || label == null) {
                return;
            }
            LabelNetworkRegistry.EndpointList list = reg.listEndpoints(world, label, lte.getPlacerId(), MAX_ENDPOINTS);
            Map<Integer, String> dimensionNames = new HashMap<>();
            for (LabelNetworkRegistry.EndpointInfo e : list.entries) {
                if (!dimensionNames.containsKey(e.dim)) {
                    dimensionNames.put(e.dim, dimensionName(e.dim));
                }
            }
            NetworkHandler.CHANNEL.sendTo(new LabelEndpointsResponsePacket(dimension, x, y, z, windowId, requestId,
                label, list.entries, list.total, dimensionNames), player);
        }

        /** Provider name of a dimension, loaded or not; "DIM n" when it cannot be resolved. */
        private static String dimensionName(int dim) {
            try {
                World loaded = DimensionManager.getWorld(dim);
                WorldProvider provider = loaded != null ? loaded.provider
                    : DimensionManager.isDimensionRegistered(dim) ? DimensionManager.createProviderFor(dim) : null;
                String name = provider == null ? null : provider.getDimensionName();
                if (name != null && !name.trim().isEmpty()) {
                    name = name.trim();
                    return name.length() > 40 ? name.substring(0, 40) : name;
                }
            } catch (RuntimeException | LinkageError ignored) {
                // a broken third-party provider must not break the locate view
            }
            return "DIM " + dim;
        }
    }
}
