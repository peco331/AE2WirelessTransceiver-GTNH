package cn.gtnh.ae2wtx.mixin;

import java.io.IOException;
import java.util.ArrayList;

import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

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
            remap = false),
        require = 1
    )
    private PacketNetworkVisualiserData ae2wtx$addWirelessVisualisationLinks(
        ArrayList<VNode> nodes,
        ArrayList<VLink> links,
        ItemStack stack,
        World world,
        Entity entity,
        int slot,
        boolean active
    ) throws IOException {
        NetworkVisualiserCompat.appendWirelessVisualisationLinks(world, nodes, links);
        return new PacketNetworkVisualiserData(nodes, links);
    }
}
