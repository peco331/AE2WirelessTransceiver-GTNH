package cn.gtnh.ae2wtx.compat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.api.networking.IGridConnection;
import appeng.api.networking.IGridNode;
import cn.gtnh.ae2wtx.content.wireless.LabeledWirelessTransceiverBlockEntity;
import cn.gtnh.ae2wtx.wireless.LabelNetworkRegistry;

/**
 * Wireless band links for network visualisers, independent of which mod's
 * visualiser node type is used. Must not reference any visualiser class: AE2 and
 * AE2 Stuff each provide one only in some GTNH versions.
 */
public final class WirelessVisualLinks {

    private WirelessVisualLinks() {}

    /** Receives one wireless link to draw, from the band's anchor endpoint to a peer. */
    public interface WirelessLinkSink<N> {

        void accept(N anchor, N peer, int channels);
    }

    private static final class WirelessVisualNode<N> {

        final N vnode;
        final int x;
        final int y;
        final int z;
        final LabeledWirelessTransceiverBlockEntity transceiver;

        WirelessVisualNode(N vnode, int x, int y, int z, LabeledWirelessTransceiverBlockEntity transceiver) {
            this.vnode = vnode;
            this.x = x;
            this.y = y;
            this.z = z;
            this.transceiver = transceiver;
        }
    }

    /**
     * Finds the wireless links missing from a visualiser's node list, independent of
     * which mod's visualiser node type {@code N} is. Nodes not at a transceiver are ignored.
     */
    public static <N> void forEachWirelessLink(World world, List<N> nodes, ToIntFunction<N> getX,
        ToIntFunction<N> getY, ToIntFunction<N> getZ, BiPredicate<N, N> alreadyLinked, WirelessLinkSink<N> sink) {
        if (world == null || nodes == null || nodes.isEmpty()) {
            return;
        }

        Map<Long, List<WirelessVisualNode<N>>> byFrequency = new HashMap<>();

        for (N vnode : nodes) {
            if (vnode == null) {
                continue;
            }
            int x = getX.applyAsInt(vnode);
            int y = getY.applyAsInt(vnode);
            int z = getZ.applyAsInt(vnode);
            if (world.blockExists(x, y, z)) {
                TileEntity te = world.getTileEntity(x, y, z);
                if (te instanceof LabeledWirelessTransceiverBlockEntity) {
                    LabeledWirelessTransceiverBlockEntity transceiver = (LabeledWirelessTransceiverBlockEntity) te;
                    if (!transceiver.isInvalid()) {
                        long freq = transceiver.getFrequency();
                        if (freq > 0L) {
                            byFrequency.computeIfAbsent(freq, k -> new ArrayList<>())
                                .add(new WirelessVisualNode<>(vnode, x, y, z, transceiver));
                        }
                    }
                }
            }
        }

        for (List<WirelessVisualNode<N>> group : byFrequency.values()) {
            if (group.size() < 2) {
                continue;
            }

            // Stable deterministic sorting by (x, y, z) ascending
            group.sort((n1, n2) -> {
                if (n1.x != n2.x) return Integer.compare(n1.x, n2.x);
                if (n1.y != n2.y) return Integer.compare(n1.y, n2.y);
                return Integer.compare(n1.z, n2.z);
            });

            // The real hub is the band's virtual node, which has no world
            // position. Draw the star from the endpoint that feeds the band its
            // channels (the one whose local branch reaches an ME controller);
            // a plain coordinate-ordered hub would make an arbitrary remote
            // transceiver look like the centre of the network.
            WirelessVisualNode<N> anchor = group.get(selectAnchorIndex(group, n -> n.transceiver.hasControllerOnLocalBranch()));
            for (WirelessVisualNode<N> peer : group) {
                if (peer == anchor || alreadyLinked.test(anchor.vnode, peer.vnode)) {
                    continue;
                }
                sink.accept(anchor.vnode, peer.vnode, getWirelessConnectionChannels(peer.transceiver));
            }
        }
    }

    /** First channel-source endpoint in the (already sorted) group, else the first endpoint. */
    static <T> int selectAnchorIndex(List<T> sortedGroup, Predicate<T> isChannelSource) {
        for (int i = 0; i < sortedGroup.size(); i++) {
            if (isChannelSource.test(sortedGroup.get(i))) {
                return i;
            }
        }
        return 0;
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
