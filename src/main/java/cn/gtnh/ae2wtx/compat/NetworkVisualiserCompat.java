package cn.gtnh.ae2wtx.compat;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import net.minecraft.world.World;

import appeng.items.tools.ToolNetworkVisualiser.VLink;
import appeng.items.tools.ToolNetworkVisualiser.VLinkFlags;
import appeng.items.tools.ToolNetworkVisualiser.VNode;

public final class NetworkVisualiserCompat {

    private NetworkVisualiserCompat() {}

    public static void appendWirelessVisualisationLinks(World world, ArrayList<VNode> nodes, ArrayList<VLink> links) {
        if (nodes == null || links == null) {
            return;
        }
        WirelessVisualLinks.forEachWirelessLink(
            world,
            nodes,
            n -> n.x,
            n -> n.y,
            n -> n.z,
            (a, b) -> hasExistingLink(links, a, b),
            (anchor, peer, channels) -> links.add(new VLink(anchor, peer, channels, EnumSet.of(VLinkFlags.DENSE))));
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

}
