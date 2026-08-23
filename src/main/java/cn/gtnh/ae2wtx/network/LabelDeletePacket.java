package cn.gtnh.ae2wtx.network;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.World;

import cn.gtnh.ae2wtx.content.wireless.LabeledWirelessTransceiverBlockEntity;
import cn.gtnh.ae2wtx.content.wireless.TransceiverSecurity;
import cn.gtnh.ae2wtx.wireless.LabelNetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/** C2S: delete a label network owned by the player. */
public class LabelDeletePacket implements IMessage {

    private static final int MAX_LABELS = LabelNetworkRegistry.MAX_PAGE_SIZE;

    private int dimension;
    private int x;
    private int y;
    private int z;
    private int windowId;
    private List<String> labels = new ArrayList<>();
    private boolean valid = true;

    public LabelDeletePacket() {}

    public LabelDeletePacket(int dimension, int x, int y, int z, int windowId, String label) {
        this(dimension, x, y, z, windowId, Collections.singletonList(label));
    }

    public LabelDeletePacket(int dimension, int x, int y, int z, int windowId, List<String> labels) {
        this.dimension = dimension;
        this.x = x;
        this.y = y;
        this.z = z;
        this.windowId = windowId;
        if (labels != null) {
            this.labels.addAll(labels.subList(0, Math.min(labels.size(), MAX_LABELS)));
        }
        this.valid = true;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        if (buf == null || buf.readableBytes() < 24) {
            valid = false;
            return;
        }
        dimension = buf.readInt();
        x = buf.readInt();
        y = buf.readInt();
        z = buf.readInt();
        windowId = buf.readInt();
        int count = buf.readInt();
        if (count < 1 || count > MAX_LABELS) {
            valid = false;
            return;
        }
        labels = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String label = NetworkBufferUtils.tryReadUtf8(buf, 192);
            if (label == null || label.isEmpty()) {
                valid = false;
                return;
            }
            labels.add(label);
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(dimension);
        buf.writeInt(x);
        buf.writeInt(y);
        buf.writeInt(z);
        buf.writeInt(windowId);
        buf.writeInt(labels.size());
        for (String label : labels) {
            NetworkBufferUtils.writeUtf8(buf, label, 192);
        }
    }

    public static class Handler implements IMessageHandler<LabelDeletePacket, IMessage> {

        @Override
        public IMessage onMessage(LabelDeletePacket msg, MessageContext ctx) {
            if (!msg.valid || ctx == null || ctx.getServerHandler() == null) {
                return null;
            }
            EntityPlayerMP sender = ctx.getServerHandler().playerEntity;
            final int dimension = msg.dimension;
            final int x = msg.x;
            final int y = msg.y;
            final int z = msg.z;
            final int windowId = msg.windowId;
            final List<String> labels = new ArrayList<>(msg.labels);
            ServerTaskQueue.enqueue(
                sender,
                ServerTaskQueue.TaskType.MUTATION,
                player -> delete(player, dimension, x, y, z, windowId, labels));
            return null;
        }

        private static void delete(EntityPlayerMP player, int dimension, int x, int y, int z, int windowId,
            List<String> labels) {
            LabeledWirelessTransceiverBlockEntity lte = ServerPacketValidation
                .getTransceiver(player, dimension, x, y, z, windowId);
            if (lte == null) {
                return;
            }
            if (!TransceiverSecurity.isOwnerOrOp(player, lte)) {
                player.addChatMessage(new ChatComponentTranslation(
                    "ae2wtx.chat.band.delete_denied"));
                return;
            }
            World world = player.worldObj;
            LabelNetworkRegistry reg = LabelNetworkRegistry.get(world);
            if (reg != null) {
                for (String label : labels) {
                    reg.removeNetwork(world, label, lte.getPlacerId());
                }
            }
        }
    }
}
