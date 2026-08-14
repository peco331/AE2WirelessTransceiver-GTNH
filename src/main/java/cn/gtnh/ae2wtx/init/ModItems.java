package cn.gtnh.ae2wtx.init;

import net.minecraft.item.Item;

import cn.gtnh.ae2wtx.item.ChannelCardItem;
import cpw.mods.fml.common.registry.GameRegistry;

public final class ModItems {

    private ModItems() {}

    public static Item itemChannelCard;

    public static void register() {
        itemChannelCard = new ChannelCardItem();
        GameRegistry.registerItem(itemChannelCard, "channel_card");
    }
}
