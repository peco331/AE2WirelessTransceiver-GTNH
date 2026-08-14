package cn.gtnh.ae2wtx.mixin;

import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.helpers.DualityInterface;
import cn.gtnh.ae2wtx.wireless.DeviceWirelessLinkManager;

/**
 * Channel-card wireless link for the ME Interface. DualityInterface backs both
 * the interface block and the interface cable part, so one mixin covers both.
 * Runs on the interface's grid tick: a channel card in the upgrade inventory
 * wirelessly joins the interface's node to the matching master transceiver.
 */
@Mixin(value = DualityInterface.class, remap = false)
public abstract class DualityInterfaceChannelCardMixin {

    @Inject(method = "tickingRequest", at = @At("HEAD"), remap = false)
    private void ae2wtx$channelCardLink(IGridNode node, int ticksSinceLastCall,
        CallbackInfoReturnable<TickRateModulation> cir) {
        DualityInterface self = (DualityInterface) (Object) this;
        TileEntity tile = self.getTile();
        if (tile == null || tile.isInvalid() || tile.getWorldObj() == null || tile.getWorldObj().isRemote) {
            return;
        }
        DeviceWirelessLinkManager.tick(self, self.getUpgrades(), tile.getWorldObj(), tile.xCoord, tile.yCoord,
            tile.zCoord, () -> self.getGridNode(ForgeDirection.UNKNOWN), tile::isInvalid);
    }
}
