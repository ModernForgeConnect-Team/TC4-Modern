package github.com.gengyoubo.tcmodern.tc4.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Feature-time block access clipped to the 3x3 chunks a {@link WorldGenRegion} lets a feature write. The original
 * generators wrote wherever they reached; here anything outside reads as bedrock (an obstacle, so trees shrink
 * instead of being cut) and writes to it are dropped.
 */
final class Tc4GenRegion {
    final WorldGenLevel level;
    final Tc4WorldBlocks blocks;
    private final int minX, maxX, minZ, maxZ;

    Tc4GenRegion(WorldGenLevel level, BlockPos origin, Tc4WorldBlocks blocks) {
        this.level = level; this.blocks = blocks;
        ChunkPos center = level instanceof WorldGenRegion region ? region.getCenter() : new ChunkPos(origin);
        minX = (center.x - 1) << 4; maxX = ((center.x + 2) << 4) - 1;
        minZ = (center.z - 1) << 4; maxZ = ((center.z + 2) << 4) - 1;
    }

    boolean inside(BlockPos pos) {
        return pos.getX() >= minX && pos.getX() <= maxX && pos.getZ() >= minZ && pos.getZ() <= maxZ && !level.isOutsideBuildHeight(pos);
    }

    BlockState get(BlockPos pos) { return inside(pos) ? level.getBlockState(pos) : Blocks.BEDROCK.defaultBlockState(); }

    boolean set(BlockPos pos, BlockState state) { return state != null && inside(pos) && level.setBlock(pos, state, 3); }

    /** Block.canSustainPlant for a sapling. */
    static boolean soil(BlockState state) { return state.is(BlockTags.DIRT) || state.is(Blocks.FARMLAND); }

    /** Block.onPlantGrow: grass and mycelium under a growing tree become dirt. */
    void plantGrow(BlockPos soil) {
        BlockState state = get(soil);
        if (state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.MYCELIUM) || state.is(Blocks.PODZOL)) set(soil, Blocks.DIRT.defaultBlockState());
    }

    boolean leaves(BlockState state) { return state.is(BlockTags.LEAVES) || blocks.isMagicalLeaves(state); }

    boolean log(BlockState state) { return state.is(BlockTags.LOGS) || blocks.isMagicalLog(state); }

    /** The original isAir || isLeaves || isReplaceable test trees used for the space they grow into. */
    boolean treeReplaceable(BlockState state) { return state.isAir() || leaves(state) || state.canBeReplaced(); }

    boolean touching(BlockPos pos, java.util.function.Predicate<BlockState> test) {
        for (Direction side : Direction.values()) if (test.test(get(pos.relative(side)))) return true;
        return false;
    }
}
