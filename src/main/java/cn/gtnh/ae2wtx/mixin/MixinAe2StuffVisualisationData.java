package cn.gtnh.ae2wtx.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import cn.gtnh.ae2wtx.compat.Ae2StuffVisualiserCompat;
import net.bdew.ae2stuff.items.visualiser.VisualisationData;

/**
 * Display-only: adds wireless band links to the data the AE2 Stuff visualiser
 * sends to the client. Only acts inside the visualiser's server-side update.
 */
@Mixin(value = VisualisationData.class, remap = false)
public abstract class MixinAe2StuffVisualisationData {

    @Inject(method = "<init>(Lscala/collection/Seq;Lscala/collection/Seq;)V", at = @At("RETURN"), require = 0)
    private void ae2wtx$appendWirelessLinks(CallbackInfo ci) {
        Ae2StuffVisualiserCompat.appendWirelessLinks((VisualisationData) (Object) this);
    }
}
