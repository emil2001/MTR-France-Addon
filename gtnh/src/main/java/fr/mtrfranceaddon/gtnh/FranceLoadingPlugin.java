package fr.mtrfranceaddon.gtnh;

import com.gtnewhorizon.gtnhmixins.IEarlyMixinLoader;
import cpw.mods.fml.relauncher.IFMLLoadingPlugin;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

@IFMLLoadingPlugin.MCVersion("1.7.10")
@IFMLLoadingPlugin.TransformerExclusions({"fr.mtrfranceaddon.gtnh.FranceLoadingPlugin"})
public final class FranceLoadingPlugin implements IFMLLoadingPlugin, IEarlyMixinLoader {
    @Override public String getMixinConfig() { return "mixins.france.gtnh.json"; }
    @Override public List<String> getMixins(Set<String> loadedCoreMods) {
        // UniMixins replaces the JSON common list with this hook's result.
        return Arrays.asList("RailTypeMixin", "ArrivalsCacheServerMixin");
    }
    @Override public String[] getASMTransformerClass() { return new String[0]; }
    @Override public String getModContainerClass() { return null; }
    @Override public String getSetupClass() { return null; }
    @Override public void injectData(Map<String, Object> data) {}
    @Override public String getAccessTransformerClass() { return null; }
}
