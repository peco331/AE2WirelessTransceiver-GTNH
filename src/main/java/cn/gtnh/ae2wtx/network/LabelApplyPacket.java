package cn.gtnh.ae2wtx.network;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentTranslation;

import cn.gtnh.ae2wtx.content.wireless.LabeledWirelessTransceiverBlockEntity;
import cn.gtnh.ae2wtx.content.wireless.TransceiverSecurity;
import cn.gtnh.ae2wtx.wireless.LabelNetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/** C2S: apply or clear the label of a labeled wireless transceiver. */
public class LabelApplyPacket implements IMessage {

    private int dimension;
    private int x;
    private int y;
    private int z;
    private int windowId;
    private String label;
    private boolean valid = true;

    public LabelApplyPacket() {}

    public LabelApplyPacket(int dimension, int x, int y, int z, int windowId, String label) {
        this.dimension = dimension;
        this.x = x;
        this.y = y;
        this.z = z;
        this.windowId = windowId;
        this.label = label;
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
        NetworkBufferUtils.writeUtf8(buf, label, 256);
    }

    public static class Handler implements IMessageHandler<LabelApplyPacket, IMessage> {

        @Override
        public IMessage onMessage(LabelApplyPacket msg, MessageContext ctx) {
            if (!msg.valid || ctx == null || ctx.getServerHandler() == null) {
                return null;
            }
            EntityPlayerMP sender = ctx.getServerHandler().playerEntity;
            final int dimension = msg.dimension;
            final int x = msg.x;
            final int y = msg.y;
            final int z = msg.z;
            final int windowId = msg.windowId;
            final String label = msg.label;
            ServerTaskQueue.enqueue(
                sender,
                ServerTaskQueue.TaskType.MUTATION,
                player -> apply(player, dimension, x, y, z, windowId, label));
            return null;
        }

        private static void apply(EntityPlayerMP player, int dimension, int x, int y, int z, int windowId,
            String label) {
            LabeledWirelessTransceiverBlockEntity lte = ServerPacketValidation
                .getTransceiver(player, dimension, x, y, z, windowId);
            if (lte == null) {
                return;
            }
            if (lte.isLocked() && !TransceiverSecurity.canManage(player, lte)) {
                player.addChatMessage(new ChatComponentTranslation(
                    "extendedae_plus.chat.wireless_transceiver.locked"));
                return;
            }
            if (label == null || label.isEmpty()) {
                lte.clearLabel();
            } else {
                LabelNetworkRegistry.RegistrationResult result = lte.applyLabel(label);
                if (!result.succeeded()) {
                    notifyFailure(player, result);
                }
            }
        }

        private static void notifyFailure(EntityPlayerMP player, LabelNetworkRegistry.RegistrationResult result) {
            switch (result.status) {
                case OWNER_LIMIT_REACHED:
                    player.addChatMessage(new ChatComponentTranslation(
                        "ae2wtx.chat.band.owner_limit",
                        result.currentCount,
                        result.limit));
                    break;
                case WORLD_LIMIT_REACHED:
                    player.addChatMessage(new ChatComponentTranslation(
                        "ae2wtx.chat.band.world_limit",
                        result.currentCount,
                        result.limit));
                    break;
                case INVALID_LABEL:
                    player.addChatMessage(new ChatComponentTranslation("ae2wtx.chat.band.invalid_label"));
                    break;
                default:
                    player.addChatMessage(new ChatComponentTranslation("ae2wtx.chat.band.create_failed"));
                    break;
            }
        }
    }
}
