package cn.gtnh.ae2wtx.network;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import cn.gtnh.ae2wtx.content.wireless.LabeledWirelessTransceiverBlockEntity;
import cn.gtnh.ae2wtx.wireless.LabelNetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/** C2S: request the list of label networks visible to the player, plus transceiver info. */
public class LabelListRequestPacket implements IMessage {

    private int x;
    private int y;
    private int z;

    public LabelListRequestPacket() {}

    public LabelListRequestPacket(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        x = buf.readInt();
        y = buf.readInt();
        z = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(x);
        buf.writeInt(y);
        buf.writeInt(z);
    }

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

            String currentLabel = "";
            String ownerName = "";
            int onlineCount = 0;
            int usedChannels = 0;
            int maxChannels = 0;

            TileEntity te = world.getTileEntity(msg.x, msg.y, msg.z);
            if (te instanceof LabeledWirelessTransceiverBlockEntity) {
                LabeledWirelessTransceiverBlockEntity lte = (LabeledWirelessTransceiverBlockEntity) te;
                if (lte.getLabelForDisplay() != null) {
                    currentLabel = lte.getLabelForDisplay();
                }
                if (lte.getPlacerName() != null) {
                    ownerName = lte.getPlacerName();
                }
                LabelNetworkRegistry.LabelNetwork net = lte.getLabelForDisplay() == null
                    ? null
                    : reg.getNetwork(world, lte.getLabelForDisplay(), lte.getPlacerId());
                if (net != null) {
                    onlineCount = net.endpointCount();
                    maxChannels = 32;
                    if (lte.getGridNode() != null && lte.getGridNode().isActive()) {
                        for (appeng.api.networking.IGridConnection c : lte.getGridNode().getConnections()) {
                            usedChannels = Math.max(c.getUsedChannels(), usedChannels);
                        }
                    }
                }
            }

            NetworkHandler.CHANNEL.sendTo(new LabelListResponsePacket(list, currentLabel, ownerName, onlineCount, usedChannels, maxChannels), player);
            return null;
        }
    }
}
