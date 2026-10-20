package github.com.gengyoubo.tcmodern.tc4.world;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Random;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;

/**
 * The 1.7 branched "big tree" that TC4 reused for WorldGenGreatwoodTrees (2x2 trunk, a second crown stacked on
 * the first) and WorldGenBigMagicTree (a taller big oak, as the biome decorator scaled it). Leaves are written last
 * so vanilla leaves get their real log distance instead of decaying.
 */
final class Tc4BranchedTree {
    enum Kind {
        GREATWOOD(0.618, 0.38, 1.2, 0.9, 2, 11, 4),
        BIG_MAGIC(0.6618, 0.381, 1.0, 0.8, 1, 12, 3);

        final double attenuation, slope, scaleWidth, leafDensity;
        final int trunk, heightRange, leafDistance;

        Kind(double attenuation, double slope, double scaleWidth, double leafDensity, int trunk, int heightRange, int leafDistance) {
            this.attenuation = attenuation; this.slope = slope; this.scaleWidth = scaleWidth; this.leafDensity = leafDensity;
            this.trunk = trunk; this.heightRange = heightRange; this.leafDistance = leafDistance;
        }
    }

    /** For each major axis, the other two axes. */
    private static final byte[] OTHER = { 2, 0, 0, 1, 2, 1 };
    private final Tc4GenRegion region;
    private final Kind kind;
    /** Upright, east-west and north-south logs. */
    private final BlockState[] log;
    private final BlockState leaves;
    private final Random rand = new Random();
    private final int[] base = new int[3];
    private int heightLimit, height;
    private double scaleWidth;
    private int[][] leafNodes;
    private final Set<BlockPos> logs = new HashSet<>(), pendingLeaves = new LinkedHashSet<>();

    private Tc4BranchedTree(Tc4GenRegion region, Kind kind, BlockState[] log, BlockState leaves) {
        this.region = region; this.kind = kind; this.log = log; this.leaves = leaves; scaleWidth = kind.scaleWidth;
    }

    /** WorldGenGreatwoodTrees.generate; a spider tree hides a cave spider spawner and a dungeon chest under it. */
    static boolean greatwood(Tc4GenRegion region, RandomSource random, BlockPos origin, boolean spiders) {
        var blocks = region.blocks;
        if (blocks.greatwoodLog() == null || blocks.greatwoodLeaves() == null) return false;
        var tree = new Tc4BranchedTree(region, Kind.GREATWOOD, blocks.greatwoodLog(), blocks.greatwoodLeaves());
        tree.rand.setSeed(random.nextLong());
        tree.setBase(origin.getX(), origin.getY(), origin.getZ());
        tree.heightLimit = Kind.GREATWOOD.heightRange + tree.rand.nextInt(Kind.GREATWOOD.heightRange);
        boolean valid = false;
        search:
        for (int a = -1; a < 2; a++) {
            next:
            for (int b = -1; b < 2; b++) {
                for (int i = 0; i < tree.kind.trunk; i++) for (int j = 0; j < tree.kind.trunk; j++) if (!tree.validLocation(i + a, j + b)) continue next;
                valid = true;
                tree.base[0] += a; tree.base[2] += b;
                break search;
            }
        }
        if (!valid) return false;
        tree.crown(true);
        tree.scaleWidth = 1.66;
        tree.setBase(origin.getX(), origin.getY() + tree.height, origin.getZ());
        tree.crown(true);
        tree.settleLeaves();
        if (spiders) tree.spiderNest(random, origin);
        return true;
    }

    /** WorldGenBigMagicTree after BiomeDecorator.setScale(1, 1, 1), in oak. */
    static boolean bigMagic(Tc4GenRegion region, RandomSource random, BlockPos origin) {
        var log = new BlockState[] { Blocks.OAK_LOG.defaultBlockState(),
                Blocks.OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X),
                Blocks.OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Z) };
        var tree = new Tc4BranchedTree(region, Kind.BIG_MAGIC, log, Blocks.OAK_LEAVES.defaultBlockState());
        tree.rand.setSeed(random.nextLong());
        tree.setBase(origin.getX(), origin.getY(), origin.getZ());
        tree.heightLimit = 11 + tree.rand.nextInt(Kind.BIG_MAGIC.heightRange);
        if (!tree.validLocation(0, 0)) return false;
        tree.crown(false);
        tree.settleLeaves();
        return true;
    }

    private void setBase(int x, int y, int z) { base[0] = x; base[1] = y; base[2] = z; }

    /** Greatwood placed its branches before the trunk, the big oak after; the result differs where they cross. */
    private void crown(boolean branchesFirst) {
        leafNodeList();
        for (int[] node : leafNodes) leafNode(node[0], node[1], node[2]);
        if (branchesFirst) { branches(); trunk(); } else { trunk(); branches(); }
    }

    private boolean open(BlockState state) { return state.isAir() || state.is(leaves.getBlock()); }

    private boolean open(BlockPos pos) { return !logs.contains(pos) && (pendingLeaves.contains(pos) || open(region.get(pos))); }

    private void leafNodeList() {
        height = (int) (heightLimit * kind.attenuation);
        if (height >= heightLimit) height = heightLimit - 1;
        int perLayer = Math.max(1, (int) (1.382 + Math.pow(kind.leafDensity * heightLimit / 13.0, 2.0)));
        int[][] nodes = new int[perLayer * heightLimit][];
        int y = base[1] + heightLimit - kind.leafDistance, trunkTop = base[1] + height, layer = y - base[1], count = 1;
        nodes[0] = new int[] { base[0], y, base[2], trunkTop };
        y--;
        for (; layer >= 0; y--, layer--) {
            float size = layerSize(layer);
            if (size < 0) continue;
            for (int i = 0; i < perLayer; i++) {
                double radius = scaleWidth * size * (rand.nextFloat() + 0.328);
                double angle = rand.nextFloat() * 2.0 * Math.PI;
                int x = Mth.floor(radius * Math.sin(angle) + base[0] + 0.5), z = Mth.floor(radius * Math.cos(angle) + base[2] + 0.5);
                int[] node = { x, y, z };
                if (checkLine(node, new int[] { x, y + kind.leafDistance, z }) != -1) continue;
                double drop = Math.sqrt(Math.pow(Math.abs(base[0] - x), 2.0) + Math.pow(Math.abs(base[2] - z), 2.0)) * kind.slope;
                int[] fork = { base[0], y - drop > trunkTop ? trunkTop : (int) (y - drop), base[2] };
                if (checkLine(fork, node) == -1) nodes[count++] = new int[] { x, y, z, fork[1] };
            }
        }
        leafNodes = Arrays.copyOf(nodes, count);
    }

    private float layerSize(int layer) {
        if (layer < heightLimit * 0.3) return -1.618F;
        float half = heightLimit / 2.0F, offset = half - layer, size;
        if (offset == 0.0F) size = half;
        else if (Math.abs(offset) >= half) size = 0.0F;
        else size = (float) Math.sqrt(half * half - offset * offset);
        return size * 0.5F;
    }

    private void leafNode(int x, int y, int z) {
        for (int i = 0; i < kind.leafDistance; i++) {
            float size = i == 0 || i == kind.leafDistance - 1 ? 2.0F : 3.0F;
            int reach = (int) (size + 0.618);
            for (int dx = -reach; dx <= reach; dx++) for (int dz = -reach; dz <= reach; dz++) {
                if (Math.pow(Math.abs(dx) + 0.5, 2.0) + Math.pow(Math.abs(dz) + 0.5, 2.0) > size * size) continue;
                var pos = new BlockPos(x + dx, y + i, z + dz);
                if (region.inside(pos) && open(pos)) pendingLeaves.add(pos);
            }
        }
    }

    private void trunk() {
        int[] from = { base[0], base[1], base[2] }, to = { base[0], base[1] + height, base[2] };
        placeLine(from, to);
        if (kind.trunk == 2) {
            from[0]++; to[0]++; placeLine(from, to);
            from[2]++; to[2]++; placeLine(from, to);
            from[0]--; to[0]--; placeLine(from, to);
        }
    }

    private void branches() {
        for (int[] node : leafNodes) {
            if (node[3] - base[1] < heightLimit * 0.2) continue;
            placeLine(new int[] { base[0], node[3], base[2] }, new int[] { node[0], node[1], node[2] });
        }
    }

    /** Distance along the line at which something other than air or this tree's leaves blocks it, or -1 if clear. */
    private int checkLine(int[] from, int[] to) {
        int[] delta = new int[3];
        int major = 0;
        for (int i = 0; i < 3; i++) { delta[i] = to[i] - from[i]; if (Math.abs(delta[i]) > Math.abs(delta[major])) major = i; }
        if (delta[major] == 0) return -1;
        int a = OTHER[major], b = OTHER[major + 3], step = delta[major] > 0 ? 1 : -1, end = delta[major] + step, i = 0;
        double da = (double) delta[a] / delta[major], db = (double) delta[b] / delta[major];
        int[] at = new int[3];
        for (; i != end; i += step) {
            at[major] = from[major] + i; at[a] = Mth.floor(from[a] + i * da); at[b] = Mth.floor(from[b] + i * db);
            if (!open(new BlockPos(at[0], at[1], at[2]))) break;
        }
        return i == end ? -1 : Math.abs(i);
    }

    private void placeLine(int[] from, int[] to) {
        int[] delta = new int[3];
        int major = 0;
        for (int i = 0; i < 3; i++) { delta[i] = to[i] - from[i]; if (Math.abs(delta[i]) > Math.abs(delta[major])) major = i; }
        if (delta[major] == 0) return;
        int a = OTHER[major], b = OTHER[major + 3], step = delta[major] > 0 ? 1 : -1, end = delta[major] + step;
        double da = (double) delta[a] / delta[major], db = (double) delta[b] / delta[major];
        int[] at = new int[3];
        for (int i = 0; i != end; i += step) {
            at[major] = Mth.floor(from[major] + i + 0.5); at[a] = Mth.floor(from[a] + i * da + 0.5); at[b] = Mth.floor(from[b] + i * db + 0.5);
            int dx = Math.abs(at[0] - from[0]), dz = Math.abs(at[2] - from[2]), reach = Math.max(dx, dz);
            var pos = new BlockPos(at[0], at[1], at[2]);
            if (region.set(pos, reach == 0 ? log[0] : dx == reach ? log[1] : log[2])) { logs.add(pos); pendingLeaves.remove(pos); }
        }
    }

    private boolean validLocation(int dx, int dz) {
        int[] from = { base[0] + dx, base[1], base[2] + dz }, to = { base[0] + dx, base[1] + heightLimit - 1, base[2] + dz };
        if (!Tc4GenRegion.soil(region.get(new BlockPos(from[0], from[1] - 1, from[2])))) return false;
        int clear = checkLine(from, to);
        if (clear == -1) return true;
        if (clear < 6) return false;
        heightLimit = clear;
        return true;
    }

    private void settleLeaves() {
        if (!leaves.hasProperty(LeavesBlock.DISTANCE)) {
            for (var pos : pendingLeaves) region.set(pos, leaves);
            return;
        }
        var distance = new HashMap<BlockPos, Integer>();
        var queue = new ArrayDeque<BlockPos>();
        for (var pos : logs) for (var side : Direction.values()) {
            var next = pos.relative(side);
            if (pendingLeaves.contains(next) && distance.putIfAbsent(next, 1) == null) queue.add(next);
        }
        while (!queue.isEmpty()) {
            var pos = queue.poll();
            int d = distance.get(pos);
            if (d >= LeavesBlock.DECAY_DISTANCE - 1) continue;
            for (var side : Direction.values()) {
                var next = pos.relative(side);
                if (pendingLeaves.contains(next) && distance.putIfAbsent(next, d + 1) == null) queue.add(next);
            }
        }
        for (var pos : pendingLeaves) region.set(pos, leaves.setValue(LeavesBlock.DISTANCE, distance.getOrDefault(pos, LeavesBlock.DECAY_DISTANCE)));
    }

    private void spiderNest(RandomSource random, BlockPos origin) {
        var spawner = origin.below();
        if (!region.set(spawner, Blocks.SPAWNER.defaultBlockState())) return;
        if (!(region.level.getBlockEntity(spawner) instanceof SpawnerBlockEntity tile)) return;
        tile.setEntityId(EntityType.CAVE_SPIDER, random);
        for (int i = 0; i < 50; i++) {
            var pos = origin.offset(-7 + random.nextInt(14), random.nextInt(10), -7 + random.nextInt(14));
            if (region.get(pos).isAir() && region.touching(pos, state -> state.is(leaves.getBlock()) || state.is(log[0].getBlock())))
                region.set(pos, Blocks.COBWEB.defaultBlockState());
        }
        var chest = origin.below(2);
        if (region.set(chest, Blocks.CHEST.defaultBlockState()))
            RandomizableContainerBlockEntity.setLootTable(region.level, random, chest, BuiltInLootTables.SIMPLE_DUNGEON);
    }
}
