package cn.gtnh.ae2wtx.network;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import cn.gtnh.ae2wtx.content.wireless.LabeledWirelessTransceiverBlockEntity;
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

    public LabelApplyPacket() {}

    public LabelApplyPacket(int x, int y, int z, String label) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.label = label;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        x = buf.readInt();
        y = buf.readInt();
        z = buf.readInt();
        int len = buf.readInt();
        label = new String(buf.readBytes(len).array(), java.nio.charset.StandardCharsets.UTF_8);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(x);
        buf.writeInt(y);
        buf.writeInt(z);
        byte[] bytes = label == null ? new byte[0] : label.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        buf.writeInt(bytes.length);
        buf.writeBytes(bytes);
    }

    public static class Handler implements IMessageHandler<LabelApplyPacket, IMessage> {

        @Override
        public IMessage onMessage(LabelApplyPacket msg, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            World world = player.worldObj;
            TileEntity te = world.getTileEntity(msg.x, msg.y, msg.z);
            if (te instanceof LabeledWirelessTransceiverBlockEntity) {
                LabeledWirelessTransceiverBlockEntity lte = (LabeledWirelessTransceiverBlockEntity) te;
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
