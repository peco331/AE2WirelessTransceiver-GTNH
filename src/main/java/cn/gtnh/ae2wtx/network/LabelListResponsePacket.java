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

    public static final class Entry {

        public final String label;
        public final long channel;

        Entry(String label, long channel) {
            this.label = label;
            this.channel = channel;
        }
    }

    private List<Entry> entries = new ArrayList<>();
    private String currentLabel = "";
    private String ownerName = "";
    private int onlineCount;
    private int usedChannels;
    private int maxChannels;
    private int networkChannels;

    public LabelListResponsePacket() {}

    public LabelListResponsePacket(List<LabelNetworkRegistry.Snapshot> snapshots, String currentLabel, String ownerName,
        int onlineCount, int usedChannels, int maxChannels, int networkChannels) {
        for (LabelNetworkRegistry.Snapshot s : snapshots) {
            entries.add(new Entry(s.label, s.channel));
        }
        this.currentLabel = currentLabel == null ? "" : currentLabel;
        this.ownerName = ownerName == null ? "" : ownerName;
        this.onlineCount = onlineCount;
        this.usedChannels = usedChannels;
        this.maxChannels = maxChannels;
        this.networkChannels = networkChannels;
    }

    public List<Entry> getEntries() {
        return entries;
    }

    public String getCurrentLabel() {
        return currentLabel;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public int getOnlineCount() {
        return onlineCount;
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
        int count = buf.readInt();
        entries = new ArrayList<>();
        if (count > 0 && count <= 10000) {
            for (int i = 0; i < count; i++) {
                String label = NetworkBufferUtils.readUtf8(buf, 256);
                long channel = buf.readLong();
                entries.add(new Entry(label, channel));
            }
        }
        currentLabel = NetworkBufferUtils.readUtf8(buf, 256);
        ownerName = NetworkBufferUtils.readUtf8(buf, 256);
        onlineCount = buf.readInt();
        usedChannels = buf.readInt();
        maxChannels = buf.readInt();
        networkChannels = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(entries.size());
        for (Entry e : entries) {
            NetworkBufferUtils.writeUtf8(buf, e.label);
            buf.writeLong(e.channel);
        }
        NetworkBufferUtils.writeUtf8(buf, currentLabel);
        NetworkBufferUtils.writeUtf8(buf, ownerName);
        buf.writeInt(onlineCount);
        buf.writeInt(usedChannels);
        buf.writeInt(maxChannels);
        buf.writeInt(networkChannels);
    }

    public static class Handler implements IMessageHandler<LabelListResponsePacket, IMessage> {

        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(LabelListResponsePacket msg, MessageContext ctx) {
            Minecraft.getMinecraft().func_152344_a(() -> {
                if (Minecraft.getMinecraft().currentScreen instanceof LabeledTransceiverGui) {
                    ((LabeledTransceiverGui) Minecraft.getMinecraft().currentScreen)
                        .updateList(msg.getEntries(), msg.getCurrentLabel(), msg.getOwnerName(), msg.getUsedChannels(),
                            msg.getMaxChannels(), msg.getOnlineCount(), msg.getNetworkChannels());
                }
            });
            return null;
        }
    }
}
