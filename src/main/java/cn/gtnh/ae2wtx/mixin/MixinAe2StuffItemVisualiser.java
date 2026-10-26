package cn.gtnh.ae2wtx.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import cn.gtnh.ae2wtx.compat.Ae2StuffVisualiserCompat;
import net.bdew.ae2stuff.items.visualiser.ItemVisualiser$;

/**
 * Display-only: marks the world while the AE2 Stuff visualiser builds its data,
 * see {@link MixinAe2StuffVisualisationData}. Names both the dev and SRG method
 * name since AE2 Stuff is not remapped here. A late mixin (LateMixinLoader);
 * skipped by MixinConfigPlugin when AE2 Stuff has no visualiser.
 */
@Mixin(value = ItemVisualiser$.class, remap = false)
public abstract class MixinAe2StuffItemVisualiser {

    @Inject(method = { "onUpdate", "func_77663_a" }, at = @At("HEAD"), require = 0)
    private void ae2wtx$beginUpdate(ItemStack stack, World world, Entity entity, int slot, boolean active,
        CallbackInfo ci) {
        Ae2StuffVisualiserCompat.beginUpdate(world);
    }

    @Inject(method = { "onUpdate", "func_77663_a" }, at = @At("RETURN"), require = 0)
    private void ae2wtx$endUpdate(ItemStack stack, World world, Entity entity, int slot, boolean active,
        CallbackInfo ci) {
        Ae2StuffVisualiserCompat.endUpdate();
    }
}
