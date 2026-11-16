package github.com.gengyoubo.tcmodern.tc4.entity;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.util.RandomSource;

/** Utils.setBiomeAt with TC4's taint biome, and the fibre splash several TC4 effects leave behind. */
public final class TaintSpread {
    private static final ResourceKey<Biome> TAINT = ResourceKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath("thaumcraft", "tainted_land"));
    private TaintSpread() { }

    public static boolean isTainted(ServerLevel level, BlockPos pos) { return level.getBiome(pos).is(TAINT); }

    /** Changes the whole column to the taint biome and resends its biomes to watching players. */
    public static boolean setTaintBiome(ServerLevel level, int x, int z) {
        var holder = level.registryAccess().registryOrThrow(Registries.BIOME).getHolder(TAINT).orElse(null);
        if (holder == null) return false;
        var pos = new BlockPos(x, level.getMinBuildHeight(), z);
        if (!level.hasChunkAt(pos)) return false;
        var chunk = level.getChunkAt(pos);
        int qx = (x >> 2) & 3, qz = (z >> 2) & 3;
        for (var section : chunk.getSections()) {
            if (!(section.getBiomes() instanceof PalettedContainer<Holder<Biome>> biomes)) continue;
            for (int qy = 0; qy < 4; qy++) biomes.getAndSetUnchecked(qx, qy, qz, holder);
        }
        chunk.setUnsaved(true);
        level.getChunkSource().chunkMap.resendBiomesForChunks(List.of(chunk));
        return true;
    }

    /**
     * The taint bottle and primal orb splash: ten tries within {@code radius} to taint a column and, on solid ground,
     * leave taint fibres at {@code y}.
     */
    public static void splash(ServerLevel level, int x, int y, int z, float radius, boolean onTop) {
        var fibres = Tc4Legacy.block("Thaumcraft:blockTaintFibres", 0);
        RandomSource random = level.random;
        for (int a = 0; a < 10; a++) {
            int xx = x + (int) ((random.nextFloat() - random.nextFloat()) * radius);
            int zz = z + (int) ((random.nextFloat() - random.nextFloat()) * radius);
            if (!random.nextBoolean() || isTainted(level, new BlockPos(xx, y, zz))) continue;
            setTaintBiome(level, xx, zz);
            if (fibres == null) continue;
            int yy = onTop ? level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, xx, zz) : y;
            var at = new BlockPos(xx, yy, zz);
            var below = at.below();
            if (level.getBlockState(below).isFaceSturdy(level, below, net.minecraft.core.Direction.UP) && level.getBlockState(at).canBeReplaced())
                level.setBlock(at, fibres, 3);
        }
    }

    /** Living things TC4 treats as tainted are immune to its taint effects. */
    public static boolean isTaintedMob(LivingEntity entity) {
        return entity instanceof TaintCow || entity instanceof TaintSpider || entity instanceof ThaumicSlime;
    }
}
