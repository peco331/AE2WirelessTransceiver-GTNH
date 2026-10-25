package cn.gtnh.ae2wtx.network;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentTranslation;

import cn.gtnh.ae2wtx.content.wireless.LabeledWirelessTransceiverBlockEntity;
import cn.gtnh.ae2wtx.content.wireless.TransceiverSecurity;
import cn.gtnh.ae2wtx.content.wireless.TransceiverTheme;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/** C2S: choose the colour scheme of the transceiver whose GUI is open. */
public class TransceiverThemePacket implements IMessage {

    private int dimension;
    private int x;
    private int y;
    private int z;
    private int windowId;
    private int theme;
    private boolean valid = true;

    public TransceiverThemePacket() {}

    public TransceiverThemePacket(int dimension, int x, int y, int z, int windowId, TransceiverTheme theme) {
        this.dimension = dimension;
        this.x = x;
        this.y = y;
        this.z = z;
        this.windowId = windowId;
        this.theme = theme.ordinal();
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        if (buf == null || buf.readableBytes() < 21) {
            valid = false;
            return;
        }
        dimension = buf.readInt();
        x = buf.readInt();
        y = buf.readInt();
        z = buf.readInt();
        windowId = buf.readInt();
        theme = buf.readByte();
        valid = theme >= 0 && theme < TransceiverTheme.values().length;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(dimension);
        buf.writeInt(x);
        buf.writeInt(y);
        buf.writeInt(z);
        buf.writeInt(windowId);
        buf.writeByte(theme);
    }

    TransceiverTheme getTheme() {
        return valid ? TransceiverTheme.fromOrdinal(theme) : null;
    }

    public static class Handler implements IMessageHandler<TransceiverThemePacket, IMessage> {

        @Override
        public IMessage onMessage(TransceiverThemePacket msg, MessageContext ctx) {
            if (!msg.valid || ctx == null || ctx.getServerHandler() == null) {
                return null;
            }
            EntityPlayerMP sender = ctx.getServerHandler().playerEntity;
            final int dimension = msg.dimension;
            final int x = msg.x;
            final int y = msg.y;
            final int z = msg.z;
            final int windowId = msg.windowId;
            final TransceiverTheme theme = msg.getTheme();
            ServerTaskQueue.enqueue(
                sender,
                ServerTaskQueue.TaskType.MUTATION,
                player -> apply(player, dimension, x, y, z, windowId, theme));
            return null;
        }

        private static void apply(EntityPlayerMP player, int dimension, int x, int y, int z, int windowId,
            TransceiverTheme theme) {
            LabeledWirelessTransceiverBlockEntity lte = ServerPacketValidation
                .getTransceiver(player, dimension, x, y, z, windowId);
            if (lte == null) {
                return;
            }
            if (lte.isLocked() && !TransceiverSecurity.canManage(player, lte)) {
                player.addChatMessage(new ChatComponentTranslation("ae2wtx.chat.transceiver.locked"));
                return;
            }
            lte.setTheme(theme);
        }
    }
}
