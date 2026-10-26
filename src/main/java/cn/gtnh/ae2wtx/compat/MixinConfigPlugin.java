package cn.gtnh.ae2wtx.compat;

import java.util.List;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.lib.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;

/**
 * Skips mixins whose target class is absent from the installed AE2, so older
 * AE2 builds (no network tool, e.g. GTNH 2.8.x) load without the tool links.
 * Lives outside the mixin package: classes there cannot be loaded normally.
 */
public final class MixinConfigPlugin implements IMixinConfigPlugin {

    private static final Logger LOG = LogManager.getLogger("ae2wtx");

    @Override
    public void onLoad(String mixinPackage) {}

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        try {
            MixinService.getService()
                .getBytecodeProvider()
                .getClassNode(targetClassName);
            return true;
        } catch (ClassNotFoundException | java.io.IOException e) {
            LOG.info("Skipping {}: {} is not present", mixinClassName, targetClassName);
            return false;
        }
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
