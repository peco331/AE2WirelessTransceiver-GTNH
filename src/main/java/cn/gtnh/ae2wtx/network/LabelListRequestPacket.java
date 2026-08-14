package cn.gtnh.ae2wtx.network;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;

import cn.gtnh.ae2wtx.wireless.LabelNetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/** C2S: request the list of label networks visible to the player. */
public class LabelListRequestPacket implements IMessage {

    public LabelListRequestPacket() {}

    @Override
    public void fromBytes(ByteBuf buf) {}

    @Override
    public void toBytes(ByteBuf buf) {}

    public static class Handler implements IMessageHandler<LabelListRequestPacket, IMessage> {

        @Override
        public IMessage onMessage(LabelListRequestPacket msg, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            World world = player.worldObj;
            LabelNetworkRegistry reg = LabelNetworkRegistry.get(world);
            if (reg == null) {
                return null;
            }
            java.util.UUID owner = player.getUniqueID();
            var list = reg.listNetworks(owner);
            NetworkHandler.CHANNEL.sendTo(new LabelListResponsePacket(list), player);
            return null;
        }
    }
}
