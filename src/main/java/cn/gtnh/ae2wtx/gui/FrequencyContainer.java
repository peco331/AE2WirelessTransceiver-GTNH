package cn.gtnh.ae2wtx.gui;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;

import net.minecraft.entity.player.InventoryPlayer;

/** Empty container for the frequency input GUI (no slots). */
public class FrequencyContainer extends Container {

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return true;
    }
}
