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
    private boolean valid = true;

    public LabelDeletePacket() {}

    public LabelDeletePacket(String label) {
        this.label = label;
        this.valid = true;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        label = NetworkBufferUtils.tryReadUtf8(buf, 256);
        if (label == null || label.isEmpty()) {
            valid = false;
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        NetworkBufferUtils.writeUtf8(buf, label, 256);
    }

    public static class Handler implements IMessageHandler<LabelDeletePacket, IMessage> {

        @Override
        public IMessage onMessage(LabelDeletePacket msg, MessageContext ctx) {
            if (!msg.valid) {
                return null;
            }
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            World world = player.worldObj;
            LabelNetworkRegistry reg = LabelNetworkRegistry.get(world);
            if (reg != null && msg.label != null && !msg.label.isEmpty()) {
                reg.removeNetwork(world, msg.label, player.getUniqueID());
            }
            return null;
        }
    }
}
