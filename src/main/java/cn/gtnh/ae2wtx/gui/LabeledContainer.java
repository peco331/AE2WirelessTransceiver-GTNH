package cn.gtnh.ae2wtx.gui;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;

/** Empty container for the labeled wireless transceiver GUI (no slots). */
public class LabeledContainer extends Container {

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return true;
    }
}
