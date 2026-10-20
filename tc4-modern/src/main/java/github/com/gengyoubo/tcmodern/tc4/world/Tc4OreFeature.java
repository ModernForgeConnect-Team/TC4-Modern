package github.com.gengyoubo.tcmodern.tc4.world;

import github.com.gengyoubo.tcmodern.api.world.OriginalOre;
import github.com.gengyoubo.tcmodern.tc4.content.Tc4ModernContent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import net.minecraft.world.level.levelgen.structure.templatesystem.TagMatchTest;
import net.minecraftforge.common.Tags;
import net.minecraftforge.registries.RegistryObject;

/**
 * Original ThaumcraftWorldGenerator.generateOres, run once per chunk with one random stream for both layers: the
 * original Thaumcraft:blockCustomOre where it replaces stone, the deepslate variant where it replaces deepslate.
 */
public final class Tc4OreFeature extends Feature<NoneFeatureConfiguration> {
    /** Original toggles from Thaumcraft.cfg (genCinnibar, genAmber, genInfusedStone), read once the runtime is ready. */
    public static volatile boolean cinnabar = true, amber = true, infused = true;
    /**
     * MFCL-mirrored Thaumcraft:blockCustomOre states, resolved once the runtime is ready. Null until then, or
     * without MFCL: only the deepslate variants generate.
     */
    public static volatile Map<OriginalOre, BlockState> original;
    private static final TagMatchTest STONE = new TagMatchTest(BlockTags.STONE_ORE_REPLACEABLES);
    private static final TagMatchTest DEEPSLATE = new TagMatchTest(BlockTags.DEEPSLATE_ORE_REPLACEABLES);
    /** Original infused metadata 1..6: air, fire, water, earth, order, entropy. */
    private static final List<RegistryObject<Block>> INFUSED = List.of(Tc4ModernContent.DEEPSLATE_AIR_INFUSED_STONE,
            Tc4ModernContent.DEEPSLATE_FIRE_INFUSED_STONE, Tc4ModernContent.DEEPSLATE_WATER_INFUSED_STONE,
            Tc4ModernContent.DEEPSLATE_EARTH_INFUSED_STONE, Tc4ModernContent.DEEPSLATE_ORDER_INFUSED_STONE,
            Tc4ModernContent.DEEPSLATE_ENTROPY_INFUSED_STONE);
    /**
     * Original Config.registerBiomeInfo, with BiomeDictionary types as their 1.20 biome tags. The value is the
     * infused metadata, or 0 for a non-primal biome aspect, which keeps the random element as the original did.
     */
    private static final List<Entry<TagKey<Biome>, Integer>> BIOME_ASPECTS = List.of( // Fixed order keeps a seed reproducible.
            Map.entry(Tags.Biomes.IS_WATER, 3), Map.entry(BiomeTags.IS_OCEAN, 3), Map.entry(BiomeTags.IS_RIVER, 3), Map.entry(Tags.Biomes.IS_WET, 3),
            Map.entry(Tags.Biomes.IS_HOT, 2), Map.entry(Tags.Biomes.IS_DESERT, 2), Map.entry(BiomeTags.IS_NETHER, 2), Map.entry(BiomeTags.IS_BADLANDS, 2),
            Map.entry(Tags.Biomes.IS_DENSE, 5), Map.entry(Tags.Biomes.IS_SNOWY, 5), Map.entry(Tags.Biomes.IS_COLD, 5), Map.entry(Tags.Biomes.IS_MUSHROOM, 5),
            Map.entry(Tags.Biomes.IS_CONIFEROUS, 4), Map.entry(BiomeTags.IS_FOREST, 4), Map.entry(Tags.Biomes.IS_SANDY, 4), Map.entry(BiomeTags.IS_BEACH, 4),
            Map.entry(BiomeTags.IS_SAVANNA, 1), Map.entry(BiomeTags.IS_MOUNTAIN, 1), Map.entry(BiomeTags.IS_HILL, 1), Map.entry(Tags.Biomes.IS_PLAINS, 1),
            Map.entry(Tags.Biomes.IS_DRY, 6), Map.entry(Tags.Biomes.IS_SPARSE, 6), Map.entry(Tags.Biomes.IS_SWAMP, 6), Map.entry(Tags.Biomes.IS_WASTELAND, 6),
            Map.entry(BiomeTags.IS_JUNGLE, 0), Map.entry(Tags.Biomes.IS_LUSH, 0), Map.entry(Tags.Biomes.IS_MAGICAL, 0), Map.entry(BiomeTags.IS_END, 0),
            Map.entry(Tags.Biomes.IS_SPOOKY, 0), Map.entry(Tags.Biomes.IS_DEAD, 0));

    public Tc4OreFeature() { super(NoneFeatureConfiguration.CODEC); }

    @Override public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level(); RandomSource random = context.random(); Map<OriginalOre, BlockState> stone = original;
        int chunkX = context.origin().getX() & ~15, chunkZ = context.origin().getZ() & ~15;
        int bottom = level.getMinBuildHeight();
        boolean placed = false;
        if (cinnabar) for (int attempt = 0; attempt < 18; attempt++) {
            var pos = new BlockPos(chunkX + random.nextInt(16), bottom + random.nextInt(level.getHeight() / 5), chunkZ + random.nextInt(16));
            placed |= replace(level, random, pos, stone == null ? null : stone.get(OriginalOre.CINNABAR), Tc4ModernContent.DEEPSLATE_CINNABAR_ORE.get());
        }
        if (amber) for (int attempt = 0; attempt < 20; attempt++) {
            int x = chunkX + random.nextInt(16), z = chunkZ + random.nextInt(16);
            var pos = new BlockPos(x, level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - random.nextInt(25), z);
            placed |= replace(level, random, pos, stone == null ? null : stone.get(OriginalOre.AMBER), Tc4ModernContent.DEEPSLATE_AMBER_BEARING_STONE.get());
        }
        if (infused) for (int vein = 0; vein < 8; vein++) {
            int x = chunkX + random.nextInt(16), z = chunkZ + random.nextInt(16);
            int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - bottom;
            var pos = new BlockPos(x, bottom + random.nextInt(Math.max(5, surface - 5)), z);
            int meta = random.nextInt(6) + 1;
            if (random.nextInt(3) == 0) {
                Integer aspect = biomeAspect(level.getBiome(pos), random);
                if (aspect == null) meta = random.nextInt(6) + 1;
                else if (aspect != 0) meta = aspect;
            }
            var targets = new ArrayList<OreConfiguration.TargetBlockState>(2);
            if (stone != null) targets.add(OreConfiguration.target(STONE, stone.get(OriginalOre.byMetadata(meta))));
            targets.add(OreConfiguration.target(DEEPSLATE, INFUSED.get(meta - 1).get().defaultBlockState()));
            var config = new OreConfiguration(targets, 6);
            placed |= Feature.ORE.place(config, level, context.chunkGenerator(), random, pos);
        }
        return placed;
    }

    /** Original single-block placement, which only replaced the base stone (here: stone or deepslate). */
    private static boolean replace(WorldGenLevel level, RandomSource random, BlockPos pos, BlockState stoneOre, Block deepslateOre) {
        BlockState current = level.getBlockState(pos);
        if (DEEPSLATE.test(current, random)) return level.setBlock(pos, deepslateOre.defaultBlockState(), 2);
        if (stoneOre != null && STONE.test(current, random)) return level.setBlock(pos, stoneOre, 2);
        return false;
    }

    /** BiomeHandler.getRandomBiomeTag: one of the biome's registered aspects at random, or null if it has none. */
    private static Integer biomeAspect(Holder<Biome> biome, RandomSource random) {
        var aspects = new ArrayList<Integer>();
        for (var entry : BIOME_ASPECTS) if (biome.is(entry.getKey())) aspects.add(entry.getValue());
        return aspects.isEmpty() ? null : aspects.get(random.nextInt(aspects.size()));
    }
}
