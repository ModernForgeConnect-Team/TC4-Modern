package github.com.gengyoubo.tcmodern.tc4.init;

import github.com.gengyoubo.tcmodern.tc4.Tc4Modern;
import github.com.gengyoubo.tcmodern.tc4.world.Tc4DecorationFeature;
import github.com.gengyoubo.tcmodern.tc4.world.Tc4OreFeature;
import github.com.gengyoubo.tcmodern.tc4.world.Tc4TreeFeature;
import github.com.gengyoubo.tcmodern.tc4.world.Tc4VegetationFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** World generation features; data/tc4modern/forge/biome_modifier places them by id. */
public final class Tc4Features {
    private static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(ForgeRegistries.FEATURES, Tc4Modern.MOD_ID);
    public static final RegistryObject<Feature<NoneFeatureConfiguration>> ORES = FEATURES.register("ores", Tc4OreFeature::new);
    public static final RegistryObject<Feature<NoneFeatureConfiguration>> VEGETATION = FEATURES.register("vegetation", Tc4VegetationFeature::new);
    public static final RegistryObject<Feature<Tc4TreeFeature.Config>> TREE = FEATURES.register("tree", Tc4TreeFeature::new);
    public static final RegistryObject<Feature<Tc4DecorationFeature.Config>> DECORATION = FEATURES.register("decoration", Tc4DecorationFeature::new);

    private Tc4Features() { }

    public static void register(IEventBus modBus) { FEATURES.register(modBus); }
}
