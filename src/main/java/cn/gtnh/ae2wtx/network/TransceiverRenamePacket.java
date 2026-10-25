package cn.gtnh.ae2wtx.network;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentTranslation;

import cn.gtnh.ae2wtx.content.wireless.LabeledWirelessTransceiverBlockEntity;
import cn.gtnh.ae2wtx.content.wireless.TransceiverSecurity;
import cn.gtnh.ae2wtx.wireless.TransceiverNames;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/** C2S: name (or, with an empty name, unname) the transceiver whose GUI is open. */
public class TransceiverRenamePacket implements IMessage {

    private int dimension;
    private int x;
    private int y;
    private int z;
    private int windowId;
    private String name = "";
    private boolean valid = true;

    public TransceiverRenamePacket() {}

    public TransceiverRenamePacket(int dimension, int x, int y, int z, int windowId, String name) {
        this.dimension = dimension;
        this.x = x;
        this.y = y;
        this.z = z;
        this.windowId = windowId;
        this.name = name == null ? "" : name;
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
        name = NetworkBufferUtils.tryReadUtf8(buf, TransceiverNames.MAX_BYTES);
        valid = name != null;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(dimension);
        buf.writeInt(x);
        buf.writeInt(y);
        buf.writeInt(z);
        buf.writeInt(windowId);
        // clean first so the byte limit never cuts a character in half
        String clean = TransceiverNames.clean(name);
        NetworkBufferUtils.writeUtf8(buf, clean == null ? "" : clean, TransceiverNames.MAX_BYTES);
    }

    /** The cleaned name, null for "no name"; only meaningful when the packet was valid. */
    String getName() {
        return valid ? TransceiverNames.clean(name) : null;
    }

    boolean isValid() {
        return valid;
    }

    public static class Handler implements IMessageHandler<TransceiverRenamePacket, IMessage> {

        @Override
        public IMessage onMessage(TransceiverRenamePacket msg, MessageContext ctx) {
            if (!msg.valid || ctx == null || ctx.getServerHandler() == null) {
                return null;
            }
            EntityPlayerMP sender = ctx.getServerHandler().playerEntity;
            final int dimension = msg.dimension;
            final int x = msg.x;
            final int y = msg.y;
            final int z = msg.z;
            final int windowId = msg.windowId;
            final String name = msg.getName();
            ServerTaskQueue.enqueue(
                sender,
                ServerTaskQueue.TaskType.MUTATION,
                player -> apply(player, dimension, x, y, z, windowId, name));
            return null;
        }

        private static void apply(EntityPlayerMP player, int dimension, int x, int y, int z, int windowId,
            String name) {
            LabeledWirelessTransceiverBlockEntity lte = ServerPacketValidation
                .getTransceiver(player, dimension, x, y, z, windowId);
            if (lte == null) {
                return;
            }
            if (lte.isLocked() && !TransceiverSecurity.canManage(player, lte)) {
                player.addChatMessage(new ChatComponentTranslation("ae2wtx.chat.transceiver.locked"));
                return;
            }
            lte.setCustomName(name);
        }
    }
}
