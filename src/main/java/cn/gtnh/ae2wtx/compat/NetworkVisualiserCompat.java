package cn.gtnh.ae2wtx.compat;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.api.networking.IGridConnection;
import appeng.api.networking.IGridNode;
import appeng.items.tools.ToolNetworkVisualiser.VLink;
import appeng.items.tools.ToolNetworkVisualiser.VLinkFlags;
import appeng.items.tools.ToolNetworkVisualiser.VNode;
import cn.gtnh.ae2wtx.content.wireless.LabeledWirelessTransceiverBlockEntity;
import cn.gtnh.ae2wtx.wireless.LabelNetworkRegistry;

public final class NetworkVisualiserCompat {

    private NetworkVisualiserCompat() {}

    private static final class WirelessVisualNode {

        final VNode vnode;
        final LabeledWirelessTransceiverBlockEntity transceiver;
        final long frequency;

        WirelessVisualNode(VNode vnode, LabeledWirelessTransceiverBlockEntity transceiver, long frequency) {
            this.vnode = vnode;
            this.transceiver = transceiver;
            this.frequency = frequency;
        }
    }

    public static void appendWirelessVisualisationLinks(World world, ArrayList<VNode> nodes, ArrayList<VLink> links) {
        if (world == null || nodes == null || nodes.isEmpty() || links == null) {
            return;
        }

        Map<Long, List<WirelessVisualNode>> byFrequency = new HashMap<>();

        for (VNode vnode : nodes) {
            if (vnode == null) {
                continue;
            }
            if (world.blockExists(vnode.x, vnode.y, vnode.z)) {
                TileEntity te = world.getTileEntity(vnode.x, vnode.y, vnode.z);
                if (te instanceof LabeledWirelessTransceiverBlockEntity) {
                    LabeledWirelessTransceiverBlockEntity transceiver = (LabeledWirelessTransceiverBlockEntity) te;
                    if (!transceiver.isInvalid()) {
                        long freq = transceiver.getFrequency();
                        if (freq > 0L) {
                            byFrequency.computeIfAbsent(freq, k -> new ArrayList<>())
                                .add(new WirelessVisualNode(vnode, transceiver, freq));
                        }
                    }
                }
            }
        }

        for (List<WirelessVisualNode> group : byFrequency.values()) {
            if (group.size() < 2) {
                continue;
            }

            // Stable deterministic sorting by (x, y, z) ascending
            group.sort((n1, n2) -> {
                if (n1.vnode.x != n2.vnode.x) return Integer.compare(n1.vnode.x, n2.vnode.x);
                if (n1.vnode.y != n2.vnode.y) return Integer.compare(n1.vnode.y, n2.vnode.y);
                return Integer.compare(n1.vnode.z, n2.vnode.z);
            });

            WirelessVisualNode anchor = group.get(0);
            for (int i = 1; i < group.size(); i++) {
                WirelessVisualNode peer = group.get(i);
                if (hasExistingLink(links, anchor.vnode, peer.vnode)) {
                    continue;
                }

                int channels = getWirelessConnectionChannels(peer.transceiver);
                EnumSet<VLinkFlags> flags = EnumSet.of(VLinkFlags.DENSE);

                links.add(new VLink(anchor.vnode, peer.vnode, channels, flags));
            }
        }
    }

    private static boolean isSameLocation(VNode a, VNode b) {
        return a.x == b.x && a.y == b.y && a.z == b.z;
    }

    private static boolean hasExistingLink(List<VLink> links, VNode a, VNode b) {
        for (VLink link : links) {
            if (link != null && link.node1 != null && link.node2 != null) {
                if ((isSameLocation(link.node1, a) && isSameLocation(link.node2, b))
                    || (isSameLocation(link.node1, b) && isSameLocation(link.node2, a))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static int getWirelessConnectionChannels(LabeledWirelessTransceiverBlockEntity transceiver) {
        if (transceiver == null) {
            return 0;
        }
        IGridNode self = transceiver.getGridNode(ForgeDirection.UNKNOWN);
        if (self != null) {
            for (IGridConnection connection : self.getConnections()) {
                IGridNode other = connection.getOtherSide(self);
                if (other != null && other.getGridBlock() != null) {
                    Object machine = other.getGridBlock().getMachine();
                    if (machine != null) {
                        Class<?> cls = machine.getClass();
                        if (cls.getEnclosingClass() == LabelNetworkRegistry.class
                            && "VirtualLabelNodeHost".equals(cls.getSimpleName())) {
                            return connection.getUsedChannels();
                        }
                    }
                }
            }
        }
        return transceiver.getUsedChannelsForDisplay();
    }
}