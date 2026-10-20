package github.com.gengyoubo.tcmodern.tc4.world;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraftforge.common.Tags;

/**
 * Original ThaumcraftWorldGenerator.generateVegetation, once per overworld chunk with the original rolls: a rare
 * silverwood in magical biomes, a greatwood weighted by BiomeHandler's per-type chance, and cinderpearls in hot
 * sandy biomes. Gated by Thaumcraft.cfg generate_trees and skipped in flat worlds, as the original was.
 */
public final class Tc4VegetationFeature extends Feature<NoneFeatureConfiguration> {
    /** Original Thaumcraft.cfg generate_trees, read once the runtime is ready. */
    public static volatile boolean trees = true;
    /**
     * Original BiomeHandler greatwood chances. The original took the first greatwood-capable type in the biome's
     * BiomeDictionary order, where the specific types came before FOREST; the list keeps that precedence.
     */
    private static final List<Entry<TagKey<Biome>, Float>> GREATWOOD = List.of(
            Map.entry(Tags.Biomes.IS_CONIFEROUS, 0.2F), Map.entry(BiomeTags.IS_SAVANNA, 0.2F), Map.entry(Tags.Biomes.IS_PLAINS, 0.2F),
            Map.entry(Tags.Biomes.IS_SWAMP, 0.2F), Map.entry(Tags.Biomes.IS_LUSH, 0.5F), Map.entry(BiomeTags.IS_FOREST, 1.0F),
            Map.entry(Tags.Biomes.IS_MAGICAL, 1.0F));

    public Tc4VegetationFeature() { super(NoneFeatureConfiguration.CODEC); }

    @Override public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        if (!trees || context.chunkGenerator() instanceof FlatLevelSource) return false;
        var level = context.level(); var random = context.random();
        var region = new Tc4GenRegion(level, context.origin(), Tc4ModernWorldgen.blocks);
        int chunkX = context.origin().getX() & ~15, chunkZ = context.origin().getZ() & ~15;
        boolean placed = false;
        if (random.nextInt(60) == 3) {
            var pos = surface(context, chunkX + random.nextInt(16), chunkZ + random.nextInt(16));
            var biome = level.getBiome(pos);
            // Magical Forest and Tainted Land grow theirs as part of their own tree mix.
            if (biome.is(Tags.Biomes.IS_MAGICAL) && !biome.is(Tc4ModernWorldgen.MAGICAL_FOREST) && !biome.is(Tc4ModernWorldgen.TAINTED_LAND))
                placed |= Tc4SilverwoodTree.grow(region, random, pos, 7, 4);
        }
        if (random.nextInt(25) == 7) {
            var pos = surface(context, chunkX + random.nextInt(16), chunkZ + random.nextInt(16));
            if (greatwoodChance(level.getBiome(pos)) > random.nextFloat())
                placed |= Tc4BranchedTree.greatwood(region, random, pos, random.nextInt(8) == 0);
        }
        var pos = surface(context, chunkX + random.nextInt(16), chunkZ + random.nextInt(16));
        var biome = level.getBiome(pos);
        if (pos.getY() <= level.getMaxBuildHeight() && biome.value().getBaseTemperature() > 1.0F
                && (biome.is(Tags.Biomes.IS_DESERT) || biome.is(BiomeTags.IS_BADLANDS)) && random.nextInt(30) == 0) {
            Tc4CustomFlowers.place(region, random, pos, region.blocks.cinderpearl());
            placed = true;
        }
        return placed;
    }

    private static BlockPos surface(FeaturePlaceContext<?> context, int x, int z) {
        return new BlockPos(x, context.level().getHeight(Heightmap.Types.WORLD_SURFACE, x, z), z);
    }

    private static float greatwoodChance(Holder<Biome> biome) {
        for (var entry : GREATWOOD) if (biome.is(entry.getKey())) return entry.getValue();
        return 0.0F;
    }
}
