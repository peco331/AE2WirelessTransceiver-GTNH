package cn.gtnh.ae2wtx.init;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;

public final class ModCreativeTab {

    private ModCreativeTab() {}

    public static final CreativeTabs TAB = new CreativeTabs("ae2wtx") {

        @Override
        public Item getTabIconItem() {
            return Item.getItemFromBlock(ModBlocks.blockWirelessTransceiver);
        }
    };
}
