package cn.gtnh.ae2wtx.network;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;

import cn.gtnh.ae2wtx.content.wireless.LabeledWirelessTransceiverBlockEntity;
import cn.gtnh.ae2wtx.wireless.LabelNetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/** C2S: request the list of label networks visible to the player, plus transceiver info. */
public class LabelListRequestPacket implements IMessage {

    public static final int DEFAULT_PAGE_SIZE = 64;

    private int dimension;
    private int x;
    private int y;
    private int z;
    private int windowId;
    private int requestId;
    private int page;
    private int pageSize;
    private String query = "";
    private String inspectLabel = "";
    private boolean valid = true;

    public LabelListRequestPacket() {}

    public LabelListRequestPacket(
        int dimension,
        int x,
        int y,
        int z,
        int windowId,
        int requestId,
        int page,
        int pageSize,
        String query,
        String inspectLabel) {
        this.dimension = dimension;
        this.x = x;
        this.y = y;
        this.z = z;
        this.windowId = windowId;
        this.requestId = requestId;
        this.page = page;
        this.pageSize = pageSize;
        this.query = query == null ? "" : query;
        this.inspectLabel = inspectLabel == null ? "" : inspectLabel;
        this.valid = true;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        if (buf == null || buf.readableBytes() < 40) {
            valid = false;
            return;
        }
        dimension = buf.readInt();
        x = buf.readInt();
        y = buf.readInt();
        z = buf.readInt();
        windowId = buf.readInt();
        requestId = buf.readInt();
        page = buf.readInt();
        pageSize = buf.readInt();
        query = NetworkBufferUtils.tryReadUtf8(buf, 192);
        inspectLabel = NetworkBufferUtils.tryReadUtf8(buf, 256);
        if (query == null || inspectLabel == null) {
            valid = false;
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(dimension);
        buf.writeInt(x);
        buf.writeInt(y);
        buf.writeInt(z);
        buf.writeInt(windowId);
        buf.writeInt(requestId);
        buf.writeInt(page);
        buf.writeInt(pageSize);
        NetworkBufferUtils.writeUtf8(buf, query, 192);
        NetworkBufferUtils.writeUtf8(buf, inspectLabel, 256);
    }

    public static class Handler implements IMessageHandler<LabelListRequestPacket, IMessage> {

        @Override
        public IMessage onMessage(LabelListRequestPacket msg, MessageContext ctx) {
            if (!msg.valid || ctx == null || ctx.getServerHandler() == null) {
                return null;
            }
            EntityPlayerMP sender = ctx.getServerHandler().playerEntity;
            final int dimension = msg.dimension;
            final int x = msg.x;
            final int y = msg.y;
            final int z = msg.z;
            final int windowId = msg.windowId;
            final int requestId = msg.requestId;
            final int page = msg.page;
            final int pageSize = msg.pageSize;
            final String query = msg.query;
            final String inspectLabel = msg.inspectLabel;
            ServerTaskQueue.enqueue(sender, ServerTaskQueue.TaskType.LIST,
                player -> respond(player, dimension, x, y, z, windowId, requestId, page, pageSize, query, inspectLabel));
            return null;
        }

        static void respond(
            EntityPlayerMP player,
            int dimension,
            int x,
            int y,
            int z,
            int windowId,
            int requestId,
            int requestedPage,
            int requestedPageSize,
            String query,
            String requestedInspectLabel) {
            LabeledWirelessTransceiverBlockEntity lte = ServerPacketValidation
                .getTransceiver(player, dimension, x, y, z, windowId);
            if (lte == null) {
                return;
            }
            World world = player.worldObj;
            LabelNetworkRegistry reg = LabelNetworkRegistry.get(world);
            if (reg == null) {
                return;
            }
            java.util.UUID owner = lte.getPlacerId();
            LabelNetworkRegistry.PagedSnapshots page =
                reg.listNetworks(world, owner, query, requestedPage, requestedPageSize);

            String currentLabel = "";
            String ownerName = "";
            int onlineCount = 0;
            int usedChannels = 0;
            int maxChannels = 0;
            int networkChannels = 0;
            int endpointCount = 0;

            if (lte.getLabelForDisplay() != null) {
                currentLabel = lte.getLabelForDisplay();
            }
            if (lte.getPlacerName() != null) {
                ownerName = lte.getPlacerName();
            }
            String inspectedLabel = LabelNetworkRegistry.normalizeLabel(requestedInspectLabel);
            if (inspectedLabel == null) {
                inspectedLabel = LabelNetworkRegistry.normalizeLabel(currentLabel);
            }
            LabelNetworkRegistry.LabelNetwork net = inspectedLabel == null
                ? null
                : reg.getNetwork(world, inspectedLabel, lte.getPlacerId());
            maxChannels = lte.getMaxChannelsForDisplay();
            usedChannels = lte.getUsedChannelsForDisplay();
            if (net != null) {
                onlineCount = net.onlineEndpointCount();
                networkChannels = net.totalUsedChannels();
                endpointCount = net.endpointCount();
            }

            NetworkHandler.CHANNEL.sendTo(new LabelListResponsePacket(dimension, x, y, z, windowId, requestId, page,
                currentLabel, ownerName, inspectedLabel, onlineCount, endpointCount, usedChannels, maxChannels,
                networkChannels), player);
        }
    }
}
