package cn.gtnh.ae2wtx.mixin;

import net.minecraft.inventory.IInventory;
import net.minecraft.tileentity.TileEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.parts.misc.PartStorageBus;
import cn.gtnh.ae2wtx.wireless.DeviceWirelessLinkManager;

/**
 * Channel-card wireless link for the storage bus. A channel card in the bus
 * upgrade inventory wirelessly joins the bus's node to the matching master
 * transceiver.
 */
@Mixin(value = PartStorageBus.class, remap = false)
public abstract class StorageBusChannelCardMixin {

    @Inject(method = "tickingRequest", at = @At("HEAD"), remap = false)
    private void ae2wtx$channelCardLink(IGridNode node, int ticksSinceLastCall,
        CallbackInfoReturnable<TickRateModulation> cir) {
        PartStorageBus self = (PartStorageBus) (Object) this;
        TileEntity tile = self.getTile();
        if (tile == null || tile.isInvalid() || tile.getWorldObj() == null || tile.getWorldObj().isRemote) {
            return;
        }
        IInventory upgrades = self.getInventoryByName("upgrades");
        DeviceWirelessLinkManager.tick(self, upgrades, tile.getWorldObj(), tile.xCoord, tile.yCoord, tile.zCoord,
            self::getGridNode, tile::isInvalid);
    }
}
