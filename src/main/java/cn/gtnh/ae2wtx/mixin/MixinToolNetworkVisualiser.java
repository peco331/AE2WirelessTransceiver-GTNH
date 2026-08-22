package cn.gtnh.ae2wtx.mixin;

import java.io.IOException;
import java.util.ArrayList;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import appeng.core.sync.packets.PacketNetworkVisualiserData;
import appeng.items.tools.ToolNetworkVisualiser;
import appeng.items.tools.ToolNetworkVisualiser.VLink;
import appeng.items.tools.ToolNetworkVisualiser.VNode;
import cn.gtnh.ae2wtx.compat.NetworkVisualiserCompat;

@Mixin(ToolNetworkVisualiser.class)
public abstract class MixinToolNetworkVisualiser {

    @Redirect(
        method = "onUpdate",
        at = @At(
            value = "NEW",
            target = "(Ljava/util/ArrayList;Ljava/util/ArrayList;)Lappeng/core/sync/packets/PacketNetworkVisualiserData;",
            remap = false
        )
    )
    private PacketNetworkVisualiserData ae2wtx$addWirelessVisualisationLinks(
        ArrayList<VNode> nodes,
        ArrayList<VLink> links
    ) throws IOException {
        NetworkVisualiserCompat.appendWirelessVisualisationLinks(nodes, links);
        return new PacketNetworkVisualiserData(nodes, links);
    }
}