package github.com.gengyoubo.tcmodern.tc4.world;

import com.mojang.logging.LogUtils;
import github.com.gengyoubo.mfcl.api.LegacyRuntime;
import github.com.gengyoubo.tcmodern.api.world.OriginalOre;
import github.com.gengyoubo.tcmodern.api.world.TcWorldgenAccessors;
import github.com.gengyoubo.tcmodern.tc4.Tc4Modern;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Original TC4 world generation on 1.20 features: ores, the global tree/plant pass and the decoration of the TC4
 * biomes MFCL places. data/tc4modern/forge/biome_modifier adds them to biomes and strips the vanilla decoration
 * MFCL copied into the TC4 biomes from its forest/plains templates.
 */
public final class Tc4ModernWorldgen {
    private static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(ForgeRegistries.FEATURES, Tc4Modern.MOD_ID);
    public static final RegistryObject<Feature<NoneFeatureConfiguration>> ORES = FEATURES.register("ores", Tc4OreFeature::new);
    public static final RegistryObject<Feature<NoneFeatureConfiguration>> VEGETATION = FEATURES.register("vegetation", Tc4VegetationFeature::new);
    public static final RegistryObject<Feature<Tc4TreeFeature.Config>> TREE = FEATURES.register("tree", Tc4TreeFeature::new);
    public static final RegistryObject<Feature<Tc4DecorationFeature.Config>> DECORATION = FEATURES.register("decoration", Tc4DecorationFeature::new);
    /** The MFCL-generated TC4 biomes, as optional tags so this pack still loads if one is missing. */
    public static final TagKey<Biome> MAGICAL_FOREST = biomeTag("magical_forest"), TAINTED_LAND = biomeTag("tainted_land"), EERIE = biomeTag("eerie");
    /** Original blocks the trees and decorators place; empty until the runtime is ready. */
    static volatile Tc4WorldBlocks blocks = new Tc4WorldBlocks(null, null, null, null, null, null, null, null, null);
    private Tc4ModernWorldgen() { }

    public static void register(IEventBus modBus) { FEATURES.register(modBus); }

    private static TagKey<Biome> biomeTag(String name) {
        return TagKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath(Tc4Modern.MOD_ID, name));
    }

    /**
     * Applies the original Thaumcraft.cfg toggles and resolves the mirrored original block states; generation runs
     * off-thread, so both are copied once here.
     */
    public static void install(LegacyRuntime runtime) {
        var config = runtime.accessor(TcWorldgenAccessors.Config.class);
        Tc4OreFeature.cinnabar = config.cinnabar();
        Tc4OreFeature.amber = config.amber();
        Tc4OreFeature.infused = config.infused();
        Tc4VegetationFeature.trees = config.trees();
        try {
            blocks = Tc4WorldBlocks.resolve(runtime);
            Tc4OreFeature.original = OriginalOre.hostStates(runtime, BlockState.class);
        } catch (UnsupportedOperationException older) {
            LogUtils.getLogger().warn("TC4: Modern needs MFCL 0.2.3+ to generate original blocks; only deepslate ores and vanilla-block decoration will generate.");
        }
    }
}
