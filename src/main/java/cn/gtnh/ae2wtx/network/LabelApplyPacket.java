package cn.gtnh.ae2wtx.network;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.World;

import cn.gtnh.ae2wtx.content.wireless.LabeledWirelessTransceiverBlockEntity;
import cn.gtnh.ae2wtx.content.wireless.TransceiverSecurity;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/** C2S: apply or clear the label of a labeled wireless transceiver. */
public class LabelApplyPacket implements IMessage {

    private int x;
    private int y;
    private int z;
    private String label;
    private boolean valid = true;

    public LabelApplyPacket() {}

    public LabelApplyPacket(int x, int y, int z, String label) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.label = label;
        this.valid = true;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        if (buf == null || buf.readableBytes() < 16) {
            valid = false;
            return;
        }
        x = buf.readInt();
        y = buf.readInt();
        z = buf.readInt();
        label = NetworkBufferUtils.tryReadUtf8(buf, 256);
        if (label == null) {
            valid = false;
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(x);
        buf.writeInt(y);
        buf.writeInt(z);
        NetworkBufferUtils.writeUtf8(buf, label, 256);
    }

    public static class Handler implements IMessageHandler<LabelApplyPacket, IMessage> {

        @Override
        public IMessage onMessage(LabelApplyPacket msg, MessageContext ctx) {
            if (!msg.valid) {
                return null;
            }
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            World world = player.worldObj;
            if (player.getDistanceSq(msg.x + 0.5D, msg.y + 0.5D, msg.z + 0.5D) > 64.0D) {
                return null;
            }
            TileEntity te = world.getTileEntity(msg.x, msg.y, msg.z);
            if (te instanceof LabeledWirelessTransceiverBlockEntity) {
                LabeledWirelessTransceiverBlockEntity lte = (LabeledWirelessTransceiverBlockEntity) te;
                if (lte.isLocked() && !TransceiverSecurity.canManage(player, lte)) {
                    player.addChatMessage(new ChatComponentTranslation(
                        "extendedae_plus.chat.wireless_transceiver.locked"));
                    return null;
                }
                if (msg.label == null || msg.label.isEmpty()) {
                    lte.clearLabel();
                } else {
                    lte.applyLabel(msg.label);
                }
            }
            return null;
        }
    }
}
