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
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;

/** Runs MFR tree generation and resumes incremental tasks saved by earlier versions. */
public final class GiantSaplingEntity extends BlockEntity {
    private static final int[] HEIGHT = {54, 96, 160}, RADIUS = {18, 52, 140}, BRANCHES = {40, 180, 600}, NODES = {2, 5, 12};
    private static final int BUDGET = 256;
    private boolean active;
    // Kept for incremental enchanted trees already present in saved worlds.
    private boolean tickTreeActive;
    private github.com.gengyoubo.tcmodern.tc4.world.MfrTickTree.Job tickTree;
    private CompoundTag savedTickTree;
    private int phase, index, step;
    private long seed;
    private boolean mfrShape, massiveShape;
    private int heightLimit, trunkHeight, nodeCount;
    private boolean spreadNodes, randomNodes;
    public GiantSaplingEntity(BlockPos pos, BlockState state) { super(Tc4BlockEntities.GIANT_SAPLING.get(), pos, state); }
    public boolean isTreeGenerating() { return tickTreeActive; }
    public Component generationStatus() {
        int current = tickTree != null ? tickTree.phase() : savedTickTree != null ? savedTickTree.getInt("Phase") : 0;
        return Component.translatable("message.tc4modern.giant_tree.phase." + Math.max(0, Math.min(3, current)));
    }
    private void notifyNearbyBonemealUser(String key) {
        var player = level.getNearestPlayer(worldPosition.getX() + .5, worldPosition.getY() + .5, worldPosition.getZ() + .5, 8, false);
        if (player != null && (player.getMainHandItem().is(Items.BONE_MEAL) || player.getOffhandItem().is(Items.BONE_MEAL)
                || player.getMainHandItem().getItem() instanceof MagicBoneMealItem || player.getOffhandItem().getItem() instanceof MagicBoneMealItem
                || GiantSaplingBlock.isGrowthHoe(player.getMainHandItem()) || GiantSaplingBlock.isGrowthHoe(player.getOffhandItem())))
            player.displayClientMessage(Component.translatable(key), true);
    }
    /** Enchanted sacred saplings use the resumable hybrid generator. */
    public void start() {
        if (active || tickTreeActive || level == null || level.isClientSide || !(getBlockState().getBlock() instanceof GiantSaplingBlock sapling)) return;
        var runtime = TcModernApi.runtime().orElse(null);
        if (runtime == null) return;
        int species = sapling.silver() ? 1 : 0;
        BlockState[] logs = new BlockState[4];
        for (int axis = 0; axis < 3; axis++) {
            logs[axis] = runtime.hostBlock("Thaumcraft:blockMagicalLog", species | axis * 4, BlockState.class).orElse(null);
            if (logs[axis] == null) return;
        }
        // MFR uses a six-bark-face wood state on branch and trunk ends.
        logs[3] = (sapling.silver() ? github.com.gengyoubo.tcmodern.tc4.init.Tc4Blocks.SILVERWOOD_WOOD.get()
                : github.com.gengyoubo.tcmodern.tc4.init.Tc4Blocks.GREATWOOD_WOOD.get()).defaultBlockState();
        var leaves = runtime.hostBlock("Thaumcraft:blockMagicalLeaves", species, BlockState.class).orElse(null);
        if (leaves == null) return;
        int variant = sapling.size();
        var kind = variant == 0 ? github.com.gengyoubo.tcmodern.tc4.world.MfrMassiveTree.Kind.MEGA
                : variant == 1 ? github.com.gengyoubo.tcmodern.tc4.world.MfrMassiveTree.Kind.SACRED
                : github.com.gengyoubo.tcmodern.tc4.world.MfrMassiveTree.Kind.ENCHANTED_SACRED;
        int scale = variant == 0 ? 4 + level.random.nextInt(3) : variant == 1 ? 6 + level.random.nextInt(4) : 0;
        long treeSeed = level.random.nextLong();
        if (variant == 2) {
            try {
                tickTree = github.com.gengyoubo.tcmodern.tc4.world.MfrTickTree.beginHybrid(level, worldPosition, sapling, logs, leaves, treeSeed);
                if (tickTree == null) { notifyNearbyBonemealUser("message.tc4modern.giant_tree.no_space"); return; }
                tickTreeActive = true; setChanged();
                notifyNearbyBonemealUser("message.tc4modern.giant_tree.started");
                LogUtils.getLogger().info("Enchanted sacred hybrid generation started at {}", worldPosition);
            } catch (RuntimeException failure) {
                LogUtils.getLogger().error("Could not start enchanted sacred tree at {}", worldPosition, failure);
            }
            return;
        }
        Level world = level;
        BlockPos origin = worldPosition;
        BlockState original = getBlockState();
        // MFR removes the sapling before checking the central line and restores it if validation fails.
        world.removeBlock(origin, false);
        try {
            var result = github.com.gengyoubo.tcmodern.tc4.world.MfrMassiveTree.generate(
                    world, origin, sapling, logs, leaves, kind, scale, treeSeed);
            if (!result.generated()) {
                world.setBlock(origin, original, Block.UPDATE_ALL);
                if (variant == 2) notifyNearbyBonemealUser("message.tc4modern.giant_tree.no_space");
                return;
            }
            if (sapling.silver()) createSilverwoodNodes(world, origin, result, runtime, logs[0]);
        } catch (RuntimeException failure) {
            if (world.isEmptyBlock(origin)) world.setBlock(origin, original, Block.UPDATE_ALL);
            LogUtils.getLogger().error("MFR-style magical tree generation failed at {}", origin, failure);
        }
    }

    private static void createSilverwoodNodes(Level level, BlockPos origin,
            github.com.gengyoubo.tcmodern.tc4.world.MfrMassiveTree.Result tree, LegacyRuntime runtime, BlockState log) {
        int count = randomNodeCount(tree.seed(), tree.heightLimit());
        var knot = runtime.hostBlock("Thaumcraft:blockMagicalLog", 2, BlockState.class).orElse(null);
        if (knot == null) return;
        var legacyWorld = runtime.mappings().toLegacy(LegacyMappings.WORLD, level).orElseThrow();
        var legacyRandom = runtime.accessor(MagicBoneMealItem.OldWorld.class, legacyWorld).random();
        for (int i = 0; i < count; i++) {
            var pos = randomNodePosition(origin, tree.trunkHeight(), tree.trunkRadius(), tree.sloped(), tree.seed(), i);
            if (!level.getBlockState(pos).is(log.getBlock())) continue;
            level.setBlock(pos, knot, Block.UPDATE_ALL);
            runtime.accessor(Nodes.class).create(legacyWorld, pos.getX(), pos.getY(), pos.getZ(), legacyRandom, true, false, false);
        }
    }
    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (tickTreeActive) {
            tag.putBoolean("TickTreeActive", true);
            if (tickTree != null) { var progress = new CompoundTag(); tickTree.save(progress); tag.put("TickTree", progress); }
            else if (savedTickTree != null) tag.put("TickTree", savedTickTree.copy());
        }
        tag.putBoolean("Active", active); tag.putInt("Phase", phase); tag.putInt("Index", index); tag.putInt("Step", step);
        tag.putLong("Seed", seed); tag.putBoolean("MfrShape", mfrShape); tag.putBoolean("MassiveShape", massiveShape);
        tag.putInt("HeightLimit", heightLimit); tag.putInt("TrunkHeight", trunkHeight); tag.putInt("NodeCount", nodeCount);
        tag.putBoolean("SpreadNodes", spreadNodes); tag.putBoolean("RandomNodes", randomNodes);
    }
    @Override public void load(CompoundTag tag) { super.load(tag);
        tickTreeActive = tag.getBoolean("TickTreeActive");
        savedTickTree = tickTreeActive && tag.contains("TickTree", 10) ? tag.getCompound("TickTree").copy() : null;
        active = tag.getBoolean("Active"); phase = tag.getInt("Phase"); index = tag.getInt("Index"); step = tag.getInt("Step"); seed = tag.getLong("Seed"); mfrShape = tag.getBoolean("MfrShape");
        // Saves from the earlier mapping did not store the shape; preserve their progress.
        massiveShape = tag.contains("MassiveShape") ? tag.getBoolean("MassiveShape")
                : getBlockState().getBlock() instanceof GiantSaplingBlock sapling && sapling.size() == 1;
        heightLimit = tag.getInt("HeightLimit"); trunkHeight = tag.getInt("TrunkHeight");
        int size = getBlockState().getBlock() instanceof GiantSaplingBlock sapling ? sapling.size() : 0;
        nodeCount = tag.contains("NodeCount") ? tag.getInt("NodeCount") : mfrShape ? (massiveShape ? 12 : 5) : NODES[size];
        spreadNodes = tag.getBoolean("SpreadNodes");
        randomNodes = tag.getBoolean("RandomNodes");
    }

    @Override public void setRemoved() {
        if (tickTree != null) {
            if (tickTreeActive) { savedTickTree = new CompoundTag(); tickTree.save(savedTickTree); }
            tickTree.cancel(); tickTree = null;
        }
        super.setRemoved();
    }

    private void tickBudgeted(GiantSaplingBlock sapling, LegacyRuntime runtime) {
        try {
            int species = sapling.silver() ? 1 : 0;
            BlockState[] logs = new BlockState[4];
            for (int axis = 0; axis < 3; axis++) logs[axis] = runtime.hostBlock("Thaumcraft:blockMagicalLog", species | axis * 4, BlockState.class).orElseThrow();
            logs[3] = (sapling.silver() ? github.com.gengyoubo.tcmodern.tc4.init.Tc4Blocks.SILVERWOOD_WOOD.get()
                    : github.com.gengyoubo.tcmodern.tc4.init.Tc4Blocks.GREATWOOD_WOOD.get()).defaultBlockState();
            var leaves = runtime.hostBlock("Thaumcraft:blockMagicalLeaves", species, BlockState.class).orElseThrow();
            if (tickTree == null) {
                if (savedTickTree == null) throw new IllegalStateException("Missing saved tree progress");
                tickTree = github.com.gengyoubo.tcmodern.tc4.world.MfrTickTree.Job.restore(level, worldPosition, sapling, logs, leaves, savedTickTree);
                savedTickTree = null;
            }
            boolean finished = tickTree.tick(System.nanoTime() + 4_000_000L);
            if (tickTree.steps % 600 == 0) LogUtils.getLogger().info("Enchanted sacred tree at {}: {}", worldPosition, tickTree.progress());
            setChanged();
            if (finished) {
                var result = tickTree.result();
                String statistics = tickTree.progress();
                tickTree.cancel(); tickTree = null; tickTreeActive = false;
                Level world = level;
                BlockPos origin = worldPosition;
                world.setBlock(origin, logs[0], Block.UPDATE_CLIENTS);
                if (sapling.silver()) createSilverwoodNodes(world, origin, result, runtime, logs[0]);
                LogUtils.getLogger().info("Enchanted sacred tree generation completed at {}: {}", origin, statistics);
            }
        } catch (RuntimeException failure) {
            if (tickTree != null) { tickTree.cancel(); tickTree = null; }
            tickTreeActive = false; savedTickTree = null; setChanged();
            LogUtils.getLogger().error("Tick-budgeted tree failed at {}", worldPosition, failure);
        }
    }

    public static void tick(Level level, BlockPos origin, BlockState state, GiantSaplingEntity tree) {
        if (tree.tickTreeActive && state.getBlock() instanceof GiantSaplingBlock sapling) {
            var runtime = TcModernApi.runtime().orElse(null);
            if (runtime != null) tree.tickBudgeted(sapling, runtime);
            return;
        }
        if (!tree.active || !(state.getBlock() instanceof GiantSaplingBlock sapling)) return;
        var runtime = TcModernApi.runtime().orElse(null); if (runtime == null) return;
        int species = sapling.silver() ? 1 : 0, size = sapling.size();
        var log = runtime.hostBlock("Thaumcraft:blockMagicalLog", species, BlockState.class).orElse(null);
        var leaves = runtime.hostBlock("Thaumcraft:blockMagicalLeaves", species, BlockState.class).orElse(null);
        if (log == null || leaves == null) return;
        int nodesProcessed = 0;
        for (int work = 0; work < BUDGET && tree.active; work++) {
            BlockPos target;
            BlockState placing;
            if (tree.phase == 0) {
                int width = tree.mfrShape ? (tree.massiveShape ? 11 : springTrunkRadius(tree.seed)) : size + 2, side = width * 2 + 1;
                int y = tree.index / (side * side), rem = tree.index % (side * side);
                int top = tree.mfrShape ? tree.trunkHeight : HEIGHT[size] - 1;
                if (y > top) { tree.phase = 1; tree.index = tree.step = 0; continue; }
                int x = rem / side - width, z = rem % side - width;
                if (tree.mfrShape ? (x*x + z*z)*4 >= width*width*5 : x*x + z*z > width*width) { tree.index++; continue; }
                if (tree.mfrShape && tree.massiveShape && y > slopedTrunk(x, z, width, top)) { tree.index++; continue; }
                target = origin.offset(x, y, z); placing = log;
                if (target.equals(origin)) { tree.index++; continue; }
                if (!place(level, target, placing)) break;
                tree.index++;
            } else if (tree.phase == 1 && tree.mfrShape) {
                int leafBase = (int)Math.ceil(tree.heightLimit * (tree.massiveShape ? 0.45F : 0.35F));
                // The enormous original uses thousands of candidates per layer. Cap its sampling density;
                // the height-dependent envelope and branch angles remain those of the original generator.
                int density = Math.max(1, (int)(1.382 + Math.pow((tree.massiveShape ? 3.0 : 0.9) * tree.heightLimit / 13.0, 2)));
                if (tree.massiveShape) density = Math.min(density, 24);
                int layers = Math.max(0, tree.heightLimit - 4 - leafBase);
                if (tree.index >= 1 + layers * density) { tree.phase = 2; tree.index = tree.step = 0; continue; }
                int layer = tree.index == 0 ? tree.heightLimit - 4 : tree.heightLimit - 5 - (tree.index - 1) / density;
                java.util.Random random = new java.util.Random(tree.seed + tree.index * 0x9E3779B97F4A7C15L);
                double half = tree.heightLimit * 0.5;
                double shape = 0.5 * Math.sqrt(Math.max(0, half*half - (half-layer)*(half-layer)));
                double radius = tree.index == 0 ? 0 : (tree.massiveShape ? 4.0 : 1.0) * shape * (random.nextFloat() + 0.328);
                double angle = random.nextFloat() * Math.PI * 2;
                int dx = (int)Math.floor(radius * Math.sin(angle) + 0.5), dz = (int)Math.floor(radius * Math.cos(angle) + 0.5);
                int startY = Math.min(layer - (int)(Math.hypot(dx, dz) * 0.45), tree.trunkHeight);
                int length = Math.max(Math.max(Math.abs(dx), Math.abs(dz)), Math.abs(layer-startY));
                if (tree.step <= length) {
                    double t = length == 0 ? 0 : (double)tree.step / length;
                    target = origin.offset((int)Math.floor(dx*t+0.5), (int)Math.floor(startY+(layer-startY)*t+0.5), (int)Math.floor(dz*t+0.5));
                    placing = log;
                } else {
                    int n = tree.step - length - 1;
                    if (n >= 4*7*7) { tree.index++; tree.step = 0; continue; }
                    int x = n%7-3, z = n/7%7-3, y = n/49;
                    int r = y == 0 || y == 3 ? 2 : 3;
                    if (x*x + Math.abs(x) + z*z + Math.abs(z) + 0.5 > r*r) { tree.step++; continue; }
                    target = origin.offset(dx+x, layer+y, dz+z); placing = leaves;
                }
                if (!place(level, target, placing)) break;
                tree.step++;
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
                int height = tree.mfrShape ? tree.trunkHeight : HEIGHT[size];
                if (!sapling.silver() || tree.index >= tree.nodeCount) { tree.phase = 3; continue; }
                if (nodesProcessed >= 2) break;
                int radius = tree.mfrShape ? (tree.massiveShape ? 11 : springTrunkRadius(tree.seed)) : size + 2;
                target = tree.randomNodes ? randomNodePosition(origin, height, radius, tree.mfrShape && tree.massiveShape, tree.seed, tree.index)
                        : tree.spreadNodes ? nodePosition(origin, height, tree.nodeCount, tree.index)
                        : origin.above(3 + tree.index * Math.max(4, (height-8)/tree.nodeCount));
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
                nodesProcessed++;
            } else {
                tree.active = false;
                level.setBlock(origin, log, Block.UPDATE_ALL);
            }
        }
        if (tree.active) tree.setChanged();
    }
    /** Uniformly choose a count from zero through the height-based upper bound. */
    public static int randomNodeCount(long seed, int height) {
        int maximum = Math.max(0, height * 3 / 10);
        return new java.util.Random(seed ^ 0x4E4F4445434F554EL).nextInt(maximum + 1);
    }
    /** Seeded shuffle without replacement keeps placement irregular, distinct, and stable across saves. */
    public static BlockPos randomNodePosition(BlockPos origin, int height, int radius, boolean sloped, long seed, int index) {
        var random = new java.util.Random(seed ^ 0x4E4F4445504F534CL);
        // Limit the number of sampled columns so shuffling a very large trunk stays cheap.
        var columns = new java.util.ArrayList<int[]>();
        for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++)
            if (x*x + z*z <= radius*radius) columns.add(new int[] {x,z});
        java.util.Collections.shuffle(columns, random);
        if (columns.size() > 25) columns.subList(25, columns.size()).clear();
        var candidates = new java.util.ArrayList<BlockPos>();
        for (var column : columns) {
            int top = sloped ? slopedTrunk(column[0], column[1], radius, height) : height;
            int first = 2 + random.nextInt(2);
            for (int y = first; y < top; y += 2)
                candidates.add(origin.offset(column[0], y, column[1]));
        }
        java.util.Collections.shuffle(candidates, random);
        return candidates.get(index);
    }
    /** Distribute knots across trunk columns when height alone cannot provide four blocks of spacing. */
    public static BlockPos nodePosition(BlockPos origin, int height, int count, int index) {
        int perColumn = Math.max(1, (height - 4) / 4);
        int columns = (count + perColumn - 1) / perColumn;
        int rows = (count + columns - 1) / columns;
        int[][] offsets = {{0,0}, {2,0}, {-2,0}, {0,2}, {0,-2}, {2,2}, {-2,-2}, {2,-2}, {-2,2}};
        int[] offset = offsets[index % columns];
        int y = 3 + (index / columns) * Math.max(4, (height - 8) / rows);
        return origin.offset(offset[0], y, offset[1]);
    }
    private static int springTrunkRadius(long seed) {
        int scale = 6 + new java.util.Random(seed).nextInt(4);
        return Math.max(4, (int)Math.round(scale / 2.0));
    }
    /** MFR's sloped trunk profile: the massive tree flares down toward its outside. */
    private static int slopedTrunk(int x, int z, int width, int height) {
        double r = Math.hypot(400.0 * x / width, 400.0 * z / width) / 180.0;
        if (r == 0) return height;
        double r2 = r * Math.PI / 1.5;
        return (int)Math.round(height * (Math.sin(r)/r + Math.sin(r2)/r2) / 2);
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
