package cn.gtnh.ae2wtx.content.wireless;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Item form of the transceiver; only adds a usage tooltip. */
public class LabeledWirelessTransceiverItemBlock extends ItemBlock {

    private static final String[] TOOLTIP_KEYS = { "ae2wtx.tooltip.transceiver.use",
        "ae2wtx.tooltip.transceiver.wrench", "ae2wtx.tooltip.transceiver.dismantle" };

    public LabeledWirelessTransceiverItemBlock(Block block) {
        super(block);
    }

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void addInformation(ItemStack stack, EntityPlayer player, List tooltip, boolean advanced) {
        for (String key : TOOLTIP_KEYS) {
            tooltip.add("§7" + StatCollector.translateToLocal(key));
        }
    }
}
