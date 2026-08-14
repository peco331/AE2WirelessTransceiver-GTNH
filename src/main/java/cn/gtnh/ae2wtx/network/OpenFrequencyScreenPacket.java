package cn.gtnh.ae2wtx.network;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;

import cn.gtnh.ae2wtx.client.screen.FrequencyInputGui;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

/**
 * S2C: ask the client to open the frequency input screen for a transceiver
 * (sent by the server-side wrench left-click handler, mirroring EAEP's
 * WrenchHook which opens the screen client-side).
 */
public class OpenFrequencyScreenPacket implements IMessage {

    private int x;
    private int y;
    private int z;
    private long frequency;

    public OpenFrequencyScreenPacket() {}

    public OpenFrequencyScreenPacket(int x, int y, int z, long frequency) {
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

    public static class Handler implements IMessageHandler<OpenFrequencyScreenPacket, IMessage> {

        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(OpenFrequencyScreenPacket msg, MessageContext ctx) {
            EntityPlayer player = Minecraft.getMinecraft().thePlayer;
            if (player != null) {
                Minecraft.getMinecraft().displayGuiScreen(
                    new FrequencyInputGui(msg.x, msg.y, msg.z, msg.frequency));
            }
            return null;
        }
    }
}
