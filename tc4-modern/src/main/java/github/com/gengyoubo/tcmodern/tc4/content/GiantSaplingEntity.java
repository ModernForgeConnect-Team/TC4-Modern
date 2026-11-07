package github.com.gengyoubo.tcmodern.tc4.content;

import com.mojang.logging.LogUtils;
import github.com.gengyoubo.mfcl.api.*;
import github.com.gengyoubo.mfcl.api.mla.*;
import github.com.gengyoubo.tcmodern.api.TcModernApi;
import github.com.gengyoubo.tcmodern.tc4.init.Tc4BlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Builds one bounded amount of work per tick. Progress is persisted in the sapling's chunk. */
public final class GiantSaplingEntity extends BlockEntity {
    private static final int[] HEIGHT = {54, 96, 160}, RADIUS = {18, 52, 140}, BRANCHES = {40, 180, 600}, NODES = {2, 5, 12};
    private static final int BUDGET = 256;
    private boolean active;
    private int phase, index, step;
    private long seed;
    public GiantSaplingEntity(BlockPos pos, BlockState state) { super(Tc4BlockEntities.GIANT_SAPLING.get(), pos, state); }
    public void start() {
        if (active || level == null || !(getBlockState().getBlock() instanceof GiantSaplingBlock sapling)) return;
        if (worldPosition.getY() + HEIGHT[sapling.size()] + 6 >= level.getMaxBuildHeight()) return;
        // A blocked central trunk must not consume the sapling or start an incomplete tree.
        for (int y = 1; y < HEIGHT[sapling.size()]; y++) {
            var at = worldPosition.above(y);
            var existing = level.getBlockState(at);
            if (!existing.isAir() && !existing.is(BlockTags.LEAVES) && !existing.canBeReplaced()) return;
            if (!existing.getFluidState().isEmpty() || existing.hasBlockEntity()) return;
        }
        seed = level.random.nextLong(); active = true; setChanged();
    }
    @Override protected void saveAdditional(CompoundTag tag) { super.saveAdditional(tag); tag.putBoolean("Active", active); tag.putInt("Phase", phase); tag.putInt("Index", index); tag.putInt("Step", step); tag.putLong("Seed", seed); }
    @Override public void load(CompoundTag tag) { super.load(tag); active = tag.getBoolean("Active"); phase = tag.getInt("Phase"); index = tag.getInt("Index"); step = tag.getInt("Step"); seed = tag.getLong("Seed"); }

    public static void tick(Level level, BlockPos origin, BlockState state, GiantSaplingEntity tree) {
        if (!tree.active || !(state.getBlock() instanceof GiantSaplingBlock sapling)) return;
        var runtime = TcModernApi.runtime().orElse(null); if (runtime == null) return;
        int species = sapling.silver() ? 1 : 0, size = sapling.size();
        var log = runtime.hostBlock("Thaumcraft:blockMagicalLog", species, BlockState.class).orElse(null);
        var leaves = runtime.hostBlock("Thaumcraft:blockMagicalLeaves", species, BlockState.class).orElse(null);
        if (log == null || leaves == null) return;
        for (int work = 0; work < BUDGET && tree.active; work++) {
            BlockPos target;
            BlockState placing;
            if (tree.phase == 0) {
                int width = size + 2, side = width * 2 + 1;
                int y = tree.index / (side * side), rem = tree.index % (side * side);
                if (y >= HEIGHT[size]) { tree.phase = 1; tree.index = tree.step = 0; continue; }
                int x = rem / side - width, z = rem % side - width;
                if (x*x + z*z > width*width) { tree.index++; continue; }
                target = origin.offset(x, y, z); placing = log;
                if (target.equals(origin)) { tree.index++; continue; }
                if (!place(level, target, placing)) break;
                tree.index++;
            } else if (tree.phase == 1) {
                if (tree.index >= BRANCHES[size]) { tree.phase = 2; tree.index = tree.step = 0; continue; }
                // Each deterministic branch carries a small, naturally decaying leaf cluster.
                java.util.Random random = new java.util.Random(tree.seed + tree.index * 0x9E3779B97F4A7C15L);
                double angle = random.nextDouble() * Math.PI * 2;
                double distance = RADIUS[size] * Math.sqrt(random.nextDouble());
                int dx = (int)Math.round(Math.cos(angle)*distance), dz = (int)Math.round(Math.sin(angle)*distance);
                int y = HEIGHT[size] * (size == 0 ? 3 : 2) / (size == 0 ? 4 : 3) + random.nextInt(HEIGHT[size]/4);
                int length = Math.max(1, Math.max(Math.abs(dx), Math.abs(dz)));
                if (tree.step <= length) {
                    double t = (double)tree.step / length;
                    target = origin.offset((int)Math.round(dx*t), y - (int)(distance*0.18*(1-t)), (int)Math.round(dz*t)); placing = log;
                } else {
                    int n = tree.step - length - 1;
                    if (n >= 9*9*9) { tree.index++; tree.step = 0; continue; }
                    int x = n/81-4, yy = n/9%9-4, z = n%9-4;
                    if (x*x + yy*yy + z*z > 16) { tree.step++; continue; }
                    target = origin.offset(dx+x, y+yy, dz+z); placing = leaves;
                }
                if (!place(level, target, placing)) break;
                tree.step++;
            } else if (tree.phase == 2) {
                if (!sapling.silver() || tree.index >= NODES[size]) { tree.phase = 3; continue; }
                target = origin.above(3 + tree.index * Math.max(4, (HEIGHT[size]-8)/NODES[size]));
                if (!level.hasChunkAt(target)) break;
                var knot = runtime.hostBlock("Thaumcraft:blockMagicalLog", 2, BlockState.class).orElse(null);
                if (knot != null && level.getBlockState(target).is(log.getBlock())) {
                    try {
                        var world = runtime.mappings().toLegacy(LegacyMappings.WORLD, level).orElseThrow();
                        var random = runtime.accessor(MagicBoneMealItem.OldWorld.class, world).random();
                        level.setBlock(target, knot, Block.UPDATE_ALL);
                        runtime.accessor(Nodes.class).create(world, target.getX(), target.getY(), target.getZ(), random, true, false, false);
                    } catch (RuntimeException failure) { LogUtils.getLogger().error("Could not create silverwood node at {}", target, failure); }
                }
                tree.index++;
            } else {
                tree.active = false;
                level.setBlock(origin, log, Block.UPDATE_ALL);
            }
        }
        if (tree.active) tree.setChanged();
    }
    /** Never load a chunk or overwrite structures, fluids, block entities or other saplings. */
    private static boolean place(Level level, BlockPos pos, BlockState state) {
        if (level.isOutsideBuildHeight(pos)) return true;
        if (!level.hasChunkAt(pos)) return false;
        var old = level.getBlockState(pos);
        if (old.isAir() || old.is(BlockTags.LEAVES) || old.canBeReplaced() && old.getFluidState().isEmpty() && !old.hasBlockEntity())
            level.setBlock(pos, state, Block.UPDATE_CLIENTS);
        return true;
    }
    @LegacyType("thaumcraft.common.lib.world.ThaumcraftWorldGenerator") public interface Nodes {
        @LegacyMethod(name="createRandomNodeAt", descriptor="(Lnet/minecraft/world/World;IIILjava/util/Random;ZZZ)V", isStatic=true)
        void create(LegacyObject world, int x, int y, int z, LegacyObject random, boolean pure, boolean dark, boolean small);
    }
}
