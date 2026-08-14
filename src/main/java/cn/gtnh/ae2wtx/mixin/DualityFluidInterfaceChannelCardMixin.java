package cn.gtnh.ae2wtx.mixin;

import net.minecraft.inventory.IInventory;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.TickRateModulation;
import cn.gtnh.ae2wtx.AE2Wtx;
import cn.gtnh.ae2wtx.wireless.DeviceWirelessLinkManager;
import com.glodblock.github.util.DualityFluidInterface;

/**
 * Channel-card wireless link for ae2fc's dual (item+fluid) ME Interface
 * (DualityFluidInterface backs both the tile and the cable part). Same logic
 * as the vanilla DualityInterface mixin: a channel card in the upgrade
 * inventory wirelessly joins the interface's node to the matching master
 * transceiver.
 */
@Mixin(value = DualityFluidInterface.class, remap = false)
public abstract class DualityFluidInterfaceChannelCardMixin {

    @org.spongepowered.asm.mixin.Unique
    private int ae2wtx$tickCounter = 0;

    @Inject(method = "tickingRequest", at = @At("HEAD"), remap = false)
    private void ae2wtx$channelCardLink(IGridNode node, int ticksSinceLastCall,
        CallbackInfoReturnable<TickRateModulation> cir) {
        // TEMP DIAG: throttled tick confirmation
        if (ae2wtx$tickCounter++ % 400 == 0) {
            AE2Wtx.LOG.info("CardLinkMixin tick: DualityFluidInterface");
        }
        DualityFluidInterface self = (DualityFluidInterface) (Object) this;
        TileEntity tile = self.getTile();
        if (tile == null || tile.isInvalid() || tile.getWorldObj() == null || tile.getWorldObj().isRemote) {
            return;
        }
        IInventory upgrades = self.getInventoryByName("upgrades");
        IGridHost host = tile instanceof IGridHost ? (IGridHost) tile : null;
        if (host == null) {
            return;
        }
        DeviceWirelessLinkManager.tick(self, upgrades, tile.getWorldObj(), tile.xCoord, tile.yCoord, tile.zCoord,
            () -> host.getGridNode(ForgeDirection.UNKNOWN), tile::isInvalid);
    }
}
