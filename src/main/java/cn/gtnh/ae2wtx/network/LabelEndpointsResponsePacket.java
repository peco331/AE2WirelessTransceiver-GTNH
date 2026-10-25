package cn.gtnh.ae2wtx.network;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;

import cn.gtnh.ae2wtx.client.screen.LabeledTransceiverGui;
import cn.gtnh.ae2wtx.wireless.LabelNetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

/** S2C: saved transceiver positions of one frequency, with display names of their dimensions. */
public class LabelEndpointsResponsePacket implements IMessage {

    private int dimension;
    private int x;
    private int y;
    private int z;
    private int windowId;
    private int requestId;
    private String label = "";
    private List<LabelNetworkRegistry.EndpointInfo> entries = new ArrayList<>();
    private int total;
    private Map<Integer, String> dimensionNames = new HashMap<>();
    private boolean valid = true;

    public LabelEndpointsResponsePacket() {}

    public LabelEndpointsResponsePacket(int dimension, int x, int y, int z, int windowId, int requestId,
        String label, List<LabelNetworkRegistry.EndpointInfo> entries, int total,
        Map<Integer, String> dimensionNames) {
        this.dimension = dimension;
        this.x = x;
        this.y = y;
        this.z = z;
        this.windowId = windowId;
        this.requestId = requestId;
        this.label = label == null ? "" : label;
        this.entries = entries == null ? new ArrayList<>() : entries;
        this.total = total;
        this.dimensionNames = dimensionNames == null ? new HashMap<>() : dimensionNames;
    }

    public String getLabel() {
        return label;
    }

    public List<LabelNetworkRegistry.EndpointInfo> getEntries() {
        return Collections.unmodifiableList(entries);
    }

    public int getTotal() {
        return total;
    }

    public Map<Integer, String> getDimensionNames() {
        return Collections.unmodifiableMap(dimensionNames);
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
        if (label == null || buf.readableBytes() < 8) {
            valid = false;
            return;
        }
        total = buf.readInt();
        int count = buf.readInt();
        if (total < 0 || count < 0 || count > total || count > LabelEndpointsRequestPacket.MAX_ENDPOINTS
            || buf.readableBytes() < count * 17 + 4) {
            valid = false;
            return;
        }
        entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            entries.add(new LabelNetworkRegistry.EndpointInfo(buf.readInt(), buf.readInt(), buf.readInt(),
                buf.readInt(), buf.readBoolean()));
        }
        int names = buf.readInt();
        if (names < 0 || names > count) {
            valid = false;
            return;
        }
        dimensionNames = new HashMap<>();
        for (int i = 0; i < names; i++) {
            if (buf.readableBytes() < 4) {
                valid = false;
                return;
            }
            int dim = buf.readInt();
            String name = NetworkBufferUtils.tryReadUtf8(buf, 128);
            if (name == null) {
                valid = false;
                return;
            }
            dimensionNames.put(dim, name);
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
        buf.writeInt(total);
        buf.writeInt(entries.size());
        for (LabelNetworkRegistry.EndpointInfo e : entries) {
            buf.writeInt(e.dim);
            buf.writeInt(e.x);
            buf.writeInt(e.y);
            buf.writeInt(e.z);
            buf.writeBoolean(e.online);
        }
        buf.writeInt(dimensionNames.size());
        for (Map.Entry<Integer, String> e : dimensionNames.entrySet()) {
            buf.writeInt(e.getKey());
            NetworkBufferUtils.writeUtf8(buf, e.getValue(), 128);
        }
    }

    public static class Handler implements IMessageHandler<LabelEndpointsResponsePacket, IMessage> {

        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(LabelEndpointsResponsePacket msg, MessageContext ctx) {
            if (!msg.valid) {
                return null;
            }
            Minecraft.getMinecraft().func_152344_a(() -> {
                if (Minecraft.getMinecraft().currentScreen instanceof LabeledTransceiverGui) {
                    LabeledTransceiverGui gui = (LabeledTransceiverGui) Minecraft.getMinecraft().currentScreen;
                    if (gui.acceptsEndpointResponse(msg.dimension, msg.x, msg.y, msg.z, msg.windowId,
                        msg.requestId)) {
                        gui.updateEndpoints(msg.getLabel(), msg.getEntries(), msg.getTotal(),
                            msg.getDimensionNames());
                    }
                }
            });
            return null;
        }
    }
}
