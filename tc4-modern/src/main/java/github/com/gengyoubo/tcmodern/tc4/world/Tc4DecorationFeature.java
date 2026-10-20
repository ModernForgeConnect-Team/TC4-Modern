package github.com.gengyoubo.tcmodern.tc4.world;

import com.mojang.serialization.Codec;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.BlockStateConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;

/**
 * Single attempts of the original biome decorators that place TC4 blocks. The per-chunk counts live in
 * worldgen/placed_feature; the origin is a surface position (heightmap MOTION_BLOCKING, which like the original getHeightValue
 * skips plants but stops at leaves and water).
 */
public final class Tc4DecorationFeature extends Feature<Tc4DecorationFeature.Config> {
    public enum Decoration implements StringRepresentable {
        /** BiomeGenMagicalForest: a vishroom on grass next to a log. */
        VISHROOM,
        /** BiomeGenTaint: a crusted taint boulder (WorldGenBlockBlob, radius 0). */
        CRUSTED_TAINT,
        /** BiomeGenTaint: taint fibres on top of grass. */
        TAINT_FIBRES,
        /** BiomeGenTaint: taint fibres in surface air touching a solid face. */
        CLINGING_TAINT_FIBRES;
        static final Codec<Decoration> CODEC = StringRepresentable.fromEnum(Decoration::values);
        @Override public String getSerializedName() { return name().toLowerCase(Locale.ROOT); }
    }

    public record Config(Decoration decoration) implements FeatureConfiguration {
        public static final Codec<Config> CODEC = Decoration.CODEC.fieldOf("decoration").xmap(Config::new, Config::decoration).codec();
    }

    public Tc4DecorationFeature() { super(Config.CODEC); }

    @Override public boolean place(FeaturePlaceContext<Config> context) {
        var blocks = Tc4ModernWorldgen.blocks;
        var region = new Tc4GenRegion(context.level(), context.origin(), blocks);
        BlockPos origin = context.origin();
        return switch (context.config().decoration()) {
            case VISHROOM -> {
                var ground = origin;
                while (ground.getY() > 50 && !region.get(ground).is(Blocks.GRASS_BLOCK)) ground = ground.below();
                var pos = ground.above();
                yield blocks.vishroom() != null && region.get(ground).is(Blocks.GRASS_BLOCK) && region.get(pos).canBeReplaced()
                        && besideLog(region, pos) && region.set(pos, blocks.vishroom());
            }
            case CRUSTED_TAINT -> blocks.crustedTaint() != null && Feature.FOREST_ROCK.place(
                    new BlockStateConfiguration(blocks.crustedTaint()), context.level(), context.chunkGenerator(), context.random(), origin);
            case TAINT_FIBRES -> {
                var top = origin.below();
                var state = region.get(top);
                if (blocks.taintFibres() == null || state.isAir()) yield false;
                if (state.is(Blocks.GRASS_BLOCK)) yield region.set(origin, blocks.taintFibres());
                yield state.canBeReplaced() && region.get(top.below()).is(Blocks.GRASS_BLOCK) && region.set(top, blocks.taintFibres());
            }
            case CLINGING_TAINT_FIBRES -> blocks.taintFibres() != null && region.get(origin).isAir() && besideSolid(region, origin)
                    && region.set(origin, blocks.taintFibres());
        };
    }

    /** BiomeGenMagicalForest.isBlockAdjacentToWood: any of the 26 surrounding blocks is a log. */
    private static boolean besideLog(Tc4GenRegion region, BlockPos pos) {
        for (int x = -1; x <= 1; x++) for (int y = -1; y <= 1; y++) for (int z = -1; z <= 1; z++)
            if ((x != 0 || y != 0 || z != 0) && region.log(region.get(pos.offset(x, y, z)))) return true;
        return false;
    }

    /** BlockUtils.isAdjacentToSolidBlock: a neighbour's facing side is solid. */
    private static boolean besideSolid(Tc4GenRegion region, BlockPos pos) {
        for (Direction side : Direction.values()) {
            var next = pos.relative(side);
            if (region.inside(next) && region.get(next).isFaceSturdy(region.level, next, side.getOpposite())) return true;
        }
        return false;
    }
}
