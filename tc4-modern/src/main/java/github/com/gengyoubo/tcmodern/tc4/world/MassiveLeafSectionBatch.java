package github.com.gengyoubo.tcmodern.tc4.world;

import github.com.gengyoubo.tcmodern.tc4.content.MagicalLeavesBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.lighting.LightEngine;

/** A bounded, server-thread-only batch for isolated permanent magical leaves. */
final class MassiveLeafSectionBatch {
    private static final int LIMIT = 64;
    private static final Heightmap.Types[] HEIGHTMAPS = { Heightmap.Types.MOTION_BLOCKING,
            Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Heightmap.Types.OCEAN_FLOOR, Heightmap.Types.WORLD_SURFACE };
    private final ServerLevel level;
    private final BlockState leaves;
    private final BlockState air = Blocks.AIR.defaultBlockState();
    private final BlockPos[] positions = new BlockPos[LIMIT];
    private final LevelChunk[] chunks = new LevelChunk[LIMIT];
    private final boolean[] checkLight = new boolean[LIMIT];
    private int count;
    long fastWrites, fallbackWrites, maxFlushNanos;

    MassiveLeafSectionBatch(ServerLevel level, BlockState leaves) { this.level = level; this.leaves = leaves; }

    boolean tryPlace(BlockPos pos) {
        if (level.captureBlockSnapshots || level.isDebug() || level.getClass() != ServerLevel.class
                || !(leaves.getBlock() instanceof MagicalLeavesBlock) || !leaves.getValue(MagicalLeavesBlock.MASSIVE_STATIC)
                || !leaves.getValue(LeavesBlock.PERSISTENT) || leaves.getValue(LeavesBlock.WATERLOGGED)) return false;
        var cache = level.getChunkSource();
        var chunk = cache.getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null || chunk.getBlockState(pos) != air) return false;
        // Skipping shape propagation is safe only when all six neighbours have no relevant shape behaviour.
        for (var direction : Direction.values()) {
            var neighborPos = pos.relative(direction);
            if (level.isOutsideBuildHeight(neighborPos)) return false;
            var neighborChunk = cache.getChunkNow(neighborPos.getX() >> 4, neighborPos.getZ() >> 4);
            if (neighborChunk == null) return false;
            var neighbor = neighborChunk.getBlockState(neighborPos);
            if (neighbor != air && (!(neighbor.getBlock() instanceof MagicalLeavesBlock)
                    || !neighbor.getValue(MagicalLeavesBlock.MASSIVE_STATIC) || neighbor.getValue(LeavesBlock.WATERLOGGED))) return false;
        }
        var section = chunk.getSection(chunk.getSectionIndex(pos.getY()));
        boolean wasEmpty = section.hasOnlyAir();
        var old = section.setBlockState(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15, leaves);
        if (old != air) throw new IllegalStateException("Leaf batch target changed during section write");
        int index = count++; positions[index] = pos.immutable(); chunks[index] = chunk;
        chunk.setUnsaved(true);
        int x = pos.getX() & 15, z = pos.getZ() & 15, y = pos.getY();
        for (var type : HEIGHTMAPS) chunk.getOrCreateHeightmapUnprimed(type).update(x, y, z, leaves);
        boolean isEmpty = section.hasOnlyAir();
        if (wasEmpty != isEmpty) cache.getLightEngine().updateSectionStatus(pos, isEmpty);
        if (LightEngine.hasDifferentLightProperties(chunk, pos, old, leaves)) {
            chunk.getSkyLightSources().update(chunk, x, y, z);
            checkLight[index] = true;
        }
        // MagicalLeavesBlock.onPlace skips its extra scheduled tick for this exact state.
        leaves.onPlace(level, pos, old, false);
        fastWrites++;
        if (count == LIMIT) flush();
        return true;
    }

    void flush() {
        if (count == 0) return;
        long started=System.nanoTime();
        try {
            for (int i = 0; i < count; i++) if (checkLight[i]) level.getLightEngine().checkBlock(positions[i]);
            for (int i = 0; i < count; i++) {
                var chunk = chunks[i];
                // The guarded neighbours need no shape propagation. Forge still performs dirty,
                // client, POI and block-state notifications through the normal hook.
                level.markAndNotifyBlock(positions[i], chunk, air, leaves,
                        Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE, 512);
            }
        } finally {
            for (int i = 0; i < count; i++) { positions[i] = null; chunks[i] = null; checkLight[i] = false; }
            count = 0;
            maxFlushNanos=Math.max(maxFlushNanos,System.nanoTime()-started);
        }
    }
}
