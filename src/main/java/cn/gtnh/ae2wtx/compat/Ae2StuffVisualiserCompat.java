package cn.gtnh.ae2wtx.compat;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.World;

import net.bdew.ae2stuff.items.visualiser.VLink;
import net.bdew.ae2stuff.items.visualiser.VLinkFlags$;
import net.bdew.ae2stuff.items.visualiser.VNode;
import net.bdew.ae2stuff.items.visualiser.VisualisationData;
import scala.Enumeration;
import scala.collection.JavaConversions;

/**
 * Adds wireless band links to the AE2 Stuff network visualiser (GTNH 2.8.x).
 * Its node list has no world, so the world is captured while the visualiser
 * item updates on the server and the links are added when it builds its data.
 */
public final class Ae2StuffVisualiserCompat {

    private static final ThreadLocal<World> UPDATING_WORLD = new ThreadLocal<>();

    private Ae2StuffVisualiserCompat() {}

    public static void beginUpdate(World world) {
        if (world != null && !world.isRemote) {
            UPDATING_WORLD.set(world);
        }
    }

    public static void endUpdate() {
        UPDATING_WORLD.remove();
    }

    public static void appendWirelessLinks(VisualisationData data) {
        World world = UPDATING_WORLD.get();
        if (world == null || data == null || data.nodes() == null || data.links() == null) {
            return;
        }

        List<VNode> nodes = JavaConversions.seqAsJavaList(data.nodes());
        List<VLink> links = new ArrayList<>(JavaConversions.seqAsJavaList(data.links()));
        int before = links.size();
        Enumeration.ValueSet dense = VLinkFlags$.MODULE$.ValueSet()
            .empty()
            .$plus(VLinkFlags$.MODULE$.DENSE());

        WirelessVisualLinks.forEachWirelessLink(
            world,
            nodes,
            VNode::x,
            VNode::y,
            VNode::z,
            (a, b) -> hasLink(links, a, b),
            (anchor, peer, channels) -> links.add(new VLink(anchor, peer, channels, dense)));

        if (links.size() != before) {
            data.links_$eq(JavaConversions.asScalaBuffer(links).toList());
        }
    }

    private static boolean hasLink(List<VLink> links, VNode a, VNode b) {
        for (VLink link : links) {
            if ((samePos(link.node1(), a) && samePos(link.node2(), b))
                || (samePos(link.node1(), b) && samePos(link.node2(), a))) {
                return true;
            }
        }
        return false;
    }

    private static boolean samePos(VNode a, VNode b) {
        return a != null && b != null && a.x() == b.x() && a.y() == b.y() && a.z() == b.z();
    }
}
