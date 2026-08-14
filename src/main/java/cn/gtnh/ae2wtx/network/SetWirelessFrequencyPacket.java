package cn.gtnh.ae2wtx.network;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import cn.gtnh.ae2wtx.content.wireless.WirelessTransceiverBlockEntity;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/** C2S: set the frequency of a plain wireless transceiver (from the frequency input GUI). */
public class SetWirelessFrequencyPacket implements IMessage {

    private int x;
    private int y;
    private int z;
    private long frequency;

    public SetWirelessFrequencyPacket() {}

    public SetWirelessFrequencyPacket(int x, int y, int z, long frequency) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.frequency = frequency;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        x = buf.readInt();
        y = buf.readInt();
        z = buf.readInt();
        frequency = buf.readLong();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(x);
        buf.writeInt(y);
        buf.writeInt(z);
        buf.writeLong(frequency);
    }

    public static class Handler implements IMessageHandler<SetWirelessFrequencyPacket, IMessage> {

        @Override
        public IMessage onMessage(SetWirelessFrequencyPacket msg, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            World world = player.worldObj;
            TileEntity te = world.getTileEntity(msg.x, msg.y, msg.z);
            if (te instanceof WirelessTransceiverBlockEntity) {
                ((WirelessTransceiverBlockEntity) te).setFrequency(msg.frequency);
            }
            return null;
        }
    }
}
