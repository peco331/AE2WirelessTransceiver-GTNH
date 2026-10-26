package cn.gtnh.ae2wtx.compat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.gtnewhorizon.gtnhmixins.ILateMixinLoader;
import com.gtnewhorizon.gtnhmixins.LateMixin;

/**
 * Mixins into regular (non-coremod) mods, applied once their jars are on the
 * classpath; the early config is prepared before that and cannot see them.
 */
@LateMixin
public final class LateMixinLoader implements ILateMixinLoader {

    @Override
    public String getMixinConfig() {
        return "mixins.ae2wtx.late.json";
    }

    @Override
    public List<String> getMixins(Set<String> loadedMods) {
        List<String> mixins = new ArrayList<>();
        if (loadedMods.contains("ae2stuff")) {
            mixins.add("MixinAe2StuffItemVisualiser");
            mixins.add("MixinAe2StuffVisualisationData");
        }
        return mixins;
    }
}
