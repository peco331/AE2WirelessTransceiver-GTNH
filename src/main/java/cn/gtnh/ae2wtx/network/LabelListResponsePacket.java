package cn.gtnh.ae2wtx.network;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;

import cn.gtnh.ae2wtx.client.screen.LabeledTransceiverGui;
import cn.gtnh.ae2wtx.wireless.LabelNetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

/** S2C: label network list + current transceiver info. */
public class LabelListResponsePacket implements IMessage {

    private static final int MAX_ENTRIES = LabelNetworkRegistry.MAX_PAGE_SIZE;

    public static final class Entry {

        public final String label;
        public final long channel;

        Entry(String label, long channel) {
            this.label = label;
            this.channel = channel;
        }
    }

    private List<Entry> entries = new ArrayList<>();
    private int dimension;
    private int x;
    private int y;
    private int z;
    private int windowId;
    private int requestId;
    private int page;
    private int pageSize;
    private int totalEntries;
    private int pageCount = 1;
    private String currentLabel = "";
    private String ownerName = "";
    private String inspectedLabel = "";
    private int onlineCount;
    private int endpointCount;
    private int usedChannels;
    private int maxChannels;
    private int networkChannels;

    private boolean valid = true;

    public LabelListResponsePacket() {}

    public LabelListResponsePacket(int dimension, int x, int y, int z, int windowId, int requestId,
        LabelNetworkRegistry.PagedSnapshots snapshots, String currentLabel, String ownerName, String inspectedLabel,
        int onlineCount, int endpointCount, int usedChannels, int maxChannels, int networkChannels) {
        this.dimension = dimension;
        this.x = x;
        this.y = y;
        this.z = z;
        this.windowId = windowId;
        this.requestId = requestId;
        if (snapshots != null) {
            this.page = snapshots.page;
            this.pageSize = snapshots.pageSize;
            this.totalEntries = snapshots.totalEntries;
            this.pageCount = snapshots.pageCount;
            int limit = Math.min(snapshots.entries.size(), MAX_ENTRIES);
            for (int i = 0; i < limit; i++) {
                LabelNetworkRegistry.Snapshot s = snapshots.entries.get(i);
                entries.add(new Entry(s.label, s.channel));
            }
        }
        this.currentLabel = currentLabel == null ? "" : currentLabel;
        this.ownerName = ownerName == null ? "" : ownerName;
        this.inspectedLabel = inspectedLabel == null ? "" : inspectedLabel;
        this.onlineCount = onlineCount;
        this.endpointCount = endpointCount;
        this.usedChannels = usedChannels;
        this.maxChannels = maxChannels;
        this.networkChannels = networkChannels;
        this.valid = true;
    }

    public List<Entry> getEntries() {
        return entries;
    }

    public int getDimension() {
        return dimension;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }

    public int getWindowId() {
        return windowId;
    }

    public int getRequestId() {
        return requestId;
    }

    public int getPage() {
        return page;
    }

    public int getPageSize() {
        return pageSize;
    }

    public int getTotalEntries() {
        return totalEntries;
    }

    public int getPageCount() {
        return pageCount;
    }

    public String getCurrentLabel() {
        return currentLabel;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public String getInspectedLabel() {
        return inspectedLabel;
    }

    public int getOnlineCount() {
        return onlineCount;
    }

    public int getEndpointCount() {
        return endpointCount;
    }

    public int getUsedChannels() {
        return usedChannels;
    }

    public int getMaxChannels() {
        return maxChannels;
    }

    public int getNetworkChannels() {
        return networkChannels;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        if (buf == null || buf.readableBytes() < 76) {
            valid = false;
            return;
        }
        dimension = buf.readInt();
        x = buf.readInt();
        y = buf.readInt();
        z = buf.readInt();
        windowId = buf.readInt();
        requestId = buf.readInt();
        page = buf.readInt();
        pageSize = buf.readInt();
        totalEntries = buf.readInt();
        pageCount = buf.readInt();
        int count = buf.readInt();
        if (page < 0 || pageSize < 1 || pageSize > MAX_ENTRIES || totalEntries < 0 || pageCount < 1
            || page >= pageCount || count < 0 || count > pageSize || count > MAX_ENTRIES) {
            valid = false;
            return;
        }
        entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String label = NetworkBufferUtils.tryReadUtf8(buf, 256);
            if (label == null || buf.readableBytes() < 8) {
                valid = false;
                return;
            }
            long channel = buf.readLong();
            entries.add(new Entry(label, channel));
        }
        currentLabel = NetworkBufferUtils.tryReadUtf8(buf, 256);
        if (currentLabel == null) {
            valid = false;
            return;
        }
        ownerName = NetworkBufferUtils.tryReadUtf8(buf, 256);
        inspectedLabel = NetworkBufferUtils.tryReadUtf8(buf, 256);
        if (ownerName == null || inspectedLabel == null || buf.readableBytes() < 20) {
            valid = false;
            return;
        }
        onlineCount = buf.readInt();
        endpointCount = buf.readInt();
        usedChannels = buf.readInt();
        maxChannels = buf.readInt();
        networkChannels = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(dimension);
        buf.writeInt(x);
        buf.writeInt(y);
        buf.writeInt(z);
        buf.writeInt(windowId);
        buf.writeInt(requestId);
        buf.writeInt(page);
        buf.writeInt(pageSize);
        buf.writeInt(totalEntries);
        buf.writeInt(pageCount);
        buf.writeInt(entries.size());
        for (Entry e : entries) {
            NetworkBufferUtils.writeUtf8(buf, e.label, 256);
            buf.writeLong(e.channel);
        }
        NetworkBufferUtils.writeUtf8(buf, currentLabel, 256);
        NetworkBufferUtils.writeUtf8(buf, ownerName, 256);
        NetworkBufferUtils.writeUtf8(buf, inspectedLabel, 256);
        buf.writeInt(onlineCount);
        buf.writeInt(endpointCount);
        buf.writeInt(usedChannels);
        buf.writeInt(maxChannels);
        buf.writeInt(networkChannels);
    }

    public static class Handler implements IMessageHandler<LabelListResponsePacket, IMessage> {

        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(LabelListResponsePacket msg, MessageContext ctx) {
            if (!msg.valid) {
                return null;
            }
            Minecraft.getMinecraft().func_152344_a(() -> {
                if (Minecraft.getMinecraft().currentScreen instanceof LabeledTransceiverGui) {
                    LabeledTransceiverGui gui = (LabeledTransceiverGui) Minecraft.getMinecraft().currentScreen;
                    if (gui.acceptsResponse(msg.getDimension(), msg.getX(), msg.getY(), msg.getZ(), msg.getWindowId(),
                        msg.getRequestId())) {
                        gui.updateList(msg.getEntries(), msg.getCurrentLabel(), msg.getOwnerName(),
                            msg.getInspectedLabel(), msg.getUsedChannels(), msg.getMaxChannels(), msg.getOnlineCount(),
                            msg.getEndpointCount(),
                            msg.getNetworkChannels(), msg.getPage(), msg.getPageSize(), msg.getTotalEntries(),
                            msg.getPageCount());
                    }
                }
            });
            return null;
        }
    }
}
