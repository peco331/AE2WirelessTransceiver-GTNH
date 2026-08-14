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

/** S2C: response with the player's label network list. */
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

    public LabelListResponsePacket() {}

    public LabelListResponsePacket(List<LabelNetworkRegistry.Snapshot> snapshots) {
        for (LabelNetworkRegistry.Snapshot s : snapshots) {
            entries.add(new Entry(s.label, s.channel));
        }
    }

    public List<Entry> getEntries() {
        return entries;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int count = buf.readInt();
        entries = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int len = buf.readInt();
            String label = new String(buf.readBytes(len).array(), java.nio.charset.StandardCharsets.UTF_8);
            long channel = buf.readLong();
            entries.add(new Entry(label, channel));
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(entries.size());
        for (Entry e : entries) {
            byte[] bytes = e.label.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            buf.writeInt(bytes.length);
            buf.writeBytes(bytes);
            buf.writeLong(e.channel);
        }
    }

    public static class Handler implements IMessageHandler<LabelListResponsePacket, IMessage> {

        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(LabelListResponsePacket msg, MessageContext ctx) {
            Minecraft.getMinecraft().func_152344_a(() -> {
                if (Minecraft.getMinecraft().currentScreen instanceof LabeledTransceiverGui) {
                    ((LabeledTransceiverGui) Minecraft.getMinecraft().currentScreen).setNetworkList(msg.getEntries());
                }
            });
            return null;
        }
    }
}
