package cn.gtnh.ae2wtx.network;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;

import cn.gtnh.ae2wtx.wireless.LabelNetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/** C2S: delete a label network owned by the player. */
public class LabelDeletePacket implements IMessage {

    private String label;

    public LabelDeletePacket() {}

    public LabelDeletePacket(String label) {
        this.label = label;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int len = buf.readInt();
        label = new String(buf.readBytes(len).array(), java.nio.charset.StandardCharsets.UTF_8);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        byte[] bytes = label == null ? new byte[0] : label.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        buf.writeInt(bytes.length);
        buf.writeBytes(bytes);
    }

    public static class Handler implements IMessageHandler<LabelDeletePacket, IMessage> {

        @Override
        public IMessage onMessage(LabelDeletePacket msg, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            World world = player.worldObj;
            LabelNetworkRegistry reg = LabelNetworkRegistry.get(world);
            if (reg != null) {
                reg.removeNetwork(world, msg.label, player.getUniqueID());
            }
            return null;
        }
    }
}
