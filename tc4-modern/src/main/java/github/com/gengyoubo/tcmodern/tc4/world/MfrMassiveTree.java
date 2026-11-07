package github.com.gengyoubo.tcmodern.tc4.world;

import java.util.ArrayList;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.IPlantable;

/**
 * Port of the 1.7.10 MFR WorldGenMassiveTree algorithm.
 * Reference: https://github.com/skyboy/MineFactoryReloaded/blob/1.7.10/src/powercrystals/minefactoryreloaded/world/WorldGenMassiveTree.java
 * The three factory settings come from MineFactoryReloadedWorldGen. World access and TC4 states are host-side;
 * the original generator's ordering, random stream, sampling density and unsafe/safe behavior are retained.
 */
public final class MfrMassiveTree {
    public enum Kind { MEGA, SACRED, ENCHANTED_SACRED }
    public record Result(boolean generated, int heightLimit, int trunkHeight, int trunkRadius, boolean sloped, long seed,
                         int density, int leafNodes) { }
    private record LeafNode(int x, int y, int z, int branchY) { }
    private static final int[] OTHER = {2, 0, 0, 1, 2, 1};
    private final Level level;
    private final BlockPos origin;
    private final BlockState[] logs;
    private final BlockState leaves;
    private final IPlantable sapling;
    private final Random random;
    private final long seed;
    private final ArrayList<LeafNode> nodes = new ArrayList<>();
    private float heightAttenuation = .45F, branchSlope = .45F, scaleWidth = 4F, branchDensity = 3F;
    private int trunkRadius = 11, heightLimitLimit = 250, leafDistance = 4, minHeight = 80;
    private int heightLimit, height, leafBases, density;
    private boolean sloped, safe;

    private MfrMassiveTree(Level level, BlockPos origin, IPlantable sapling, BlockState[] logs, BlockState leaves,
                           Kind kind, int scale, long seed) {
        this.level = level; this.origin = origin; this.sapling = sapling; this.logs = logs; this.leaves = leaves;
        this.seed = seed; this.random = new Random(seed);
        if (kind != Kind.ENCHANTED_SACRED) {
            heightLimitLimit = scale * 12;
            minHeight = heightLimitLimit / 2;
            trunkRadius = (int)Math.round(scale / 2.0);
            leafDistance = minHeight > 30 ? 5 : minHeight / 8;
            if (kind == Kind.MEGA) {
                scaleWidth = .8F; branchDensity = .7F; heightAttenuation = .6F; sloped = true; safe = true;
            } else {
                scaleWidth = 1F; branchDensity = .9F; heightAttenuation = .35F; trunkRadius = Math.max(4, trunkRadius);
            }
        } else sloped = true;
    }

    /** A generator instance is single-use; as in MFR, generation itself is synchronous and has no density cap. */
    public static Result generate(Level level, BlockPos origin, IPlantable sapling, BlockState[] logs, BlockState leaves,
                                  Kind kind, int scale, long seed) {
        var tree = new MfrMassiveTree(level, origin, sapling, logs, leaves, kind, scale, seed);
        if (!tree.validLocation()) return new Result(false, 0, 0, tree.trunkRadius, tree.sloped, seed, 0, 0);
        tree.setup();
        tree.generateLeafNodes();
        tree.generateLeaves();
        tree.generateBranches();
        tree.generateTrunk();
        return new Result(true, tree.heightLimit, tree.height, tree.trunkRadius, tree.sloped, seed, tree.density, tree.nodes.size());
    }

    private void setup() {
        leafBases = Mth.ceil(heightLimit * heightAttenuation);
        density = candidateDensity(branchDensity, heightLimit);
    }
    public static int candidateDensity(float branchDensity, int heightLimit) {
        return Math.max(1, (int)(1.382D + Math.pow(branchDensity * heightLimit / 13.0D, 2.0D)));
    }
    private float layerSize(int y) {
        if (y < leafBases) return -1.618F;
        float half = heightLimit * .5F, delta = half - y;
        if (Math.abs(delta) >= half) return 0F;
        return .5F * (float)Math.sqrt(half * half - delta * delta);
    }
    private void generateLeafNodes() {
        int top = origin.getY() + heightLimit - leafDistance;
        int trunkTop = origin.getY() + height;
        nodes.add(new LeafNode(origin.getX(), top, origin.getZ(), trunkTop));
        int relativeY = top - origin.getY();
        int y = top - 1;
        while (relativeY >= 0) {
            float layer = layerSize(relativeY);
            if (layer > 0) for (int n = 0; n < density; n++) {
                float distance = scaleWidth * layer * (random.nextFloat() + .328F);
                float angle = random.nextFloat() * 2F * (float)Math.PI;
                int x = Mth.floor(distance * Math.sin(angle) + origin.getX() + .5F);
                int z = Mth.floor(distance * Math.cos(angle) + origin.getZ() + .5F);
                int[] leaf = {x, y, z}, above = {x, y + leafDistance, z};
                if (checkLine(leaf, above) != -1) continue;
                int dx = origin.getX() - x, dz = origin.getZ() - z;
                int drop = (int)(Math.sqrt(dx * dx + dz * dz) * branchSlope);
                int branchY = Math.min(y - drop, trunkTop);
                if (checkLine(new int[]{origin.getX(), branchY, origin.getZ()}, leaf) == -1)
                    nodes.add(new LeafNode(x, y, z, branchY));
            }
            y--; relativeY--;
        }
    }
    private void generateLeaves() {
        for (var node : nodes) for (int layer = 0; layer < leafDistance; layer++) {
            int radius = layer != 0 && layer != leafDistance - 1 ? 3 : 2;
            for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
                if (dx*dx + Math.abs(dx) + dz*dz + Math.abs(dz) + .5F > radius*radius) continue;
                var at = new BlockPos(node.x + dx, node.y + layer, node.z + dz);
                var old = level.getBlockState(at);
                if (safe ? leafReplaceable(old, at) : !old.is(Blocks.BEDROCK)) put(at, leaves);
            }
        }
    }
    private void generateBranches() {
        int minimum = (int)(heightLimit * .2F);
        for (var node : nodes) if (node.branchY - origin.getY() >= minimum)
            placeLine(new int[]{origin.getX(), node.branchY, origin.getZ()}, new int[]{node.x, node.y, node.z}, true);
    }
    private void generateTrunk() {
        int x0 = origin.getX(), y0 = origin.getY(), z0 = origin.getZ();
        double factor = 400F / trunkRadius;
        for (int dx = -trunkRadius; dx <= trunkRadius; dx++) for (int dz = -trunkRadius; dz <= trunkRadius; dz++) {
            if ((dx*dx + dz*dz)*4 >= trunkRadius*trunkRadius*5) continue;
            int endY = y0 + height;
            if (sloped) endY = y0 + sinc(factor * dx, factor * dz, height) - (random.nextInt(3) - 1);
            int[] start = {x0+dx, y0, z0+dz}, end = {x0+dx, endY, z0+dz};
            placeLine(start, end, false);
            put(new BlockPos(end[0], end[1], end[2]), logs[3]);
            var soil = new BlockPos(start[0], y0-1, start[2]);
            var ground = level.getBlockState(soil);
            if (ground.is(Blocks.GRASS_BLOCK) || ground.is(Blocks.MYCELIUM) || ground.is(Blocks.PODZOL))
                put(soil, Blocks.DIRT.defaultBlockState());
        }
    }
    private static int sinc(double x, double z, int height) {
        double r = Math.hypot(x, z) / 180.0;
        if (r == 0) return height;
        double r2 = r * Math.PI / 1.5;
        return (int)Math.round(height * (Math.sin(r)/r + Math.sin(r2)/r2) / 2);
    }
    private boolean validLocation() {
        heightLimit = Math.min(heightLimitLimit + origin.getY(), level.getMaxBuildHeight() - 1) - origin.getY();
        if (heightLimit < minHeight || origin.getY() < level.getMinBuildHeight() + 1) return false;
        var below = origin.below();
        if (!level.getBlockState(below).canSustainPlant(level, below, Direction.UP, sapling)) return false;
        int[] start = {origin.getX(), origin.getY(), origin.getZ()};
        int[] end = {origin.getX(), origin.getY()+heightLimit-1, origin.getZ()};
        int clear = checkLine(start, end);
        if (clear != -1 && clear < minHeight) return false;
        if (clear != -1) heightLimit = clear;
        heightLimit = Math.min(heightLimit, heightLimitLimit);
        height = (int)(heightLimit * heightAttenuation);
        if (height >= heightLimit) height = heightLimit - 1;
        height += random.nextInt(heightLimit - height);
        if (safe) {
            double factor = 400F / trunkRadius;
            for (int dx = -trunkRadius; dx <= trunkRadius; dx++) for (int dz = -trunkRadius; dz <= trunkRadius; dz++) {
                if ((dx*dx + dz*dz)*4 >= trunkRadius*trunkRadius*5) continue;
                int top = origin.getY() + (sloped ? sinc(factor*dx, factor*dz, height) : height);
                if (checkLine(new int[]{origin.getX()+dx, origin.getY(), origin.getZ()+dz},
                        new int[]{origin.getX()+dx, top, origin.getZ()+dz}) != -1) return false;
            }
        }
        return true;
    }
    private boolean leafReplaceable(BlockState state, BlockPos pos) {
        return state.isAir() || state.is(BlockTags.LEAVES) || state.canBeReplaced();
    }
    private boolean lineReplaceable(BlockState state, BlockPos pos) {
        return state.isAir() || state.canBeReplaced() || state.canBeReplaced()
                || state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS) || state.getBlock() instanceof SaplingBlock;
    }
    /** Matches MFR's dominant-axis line scan and floor rounding. */
    private int checkLine(int[] a, int[] b) {
        int[] delta = {b[0]-a[0], b[1]-a[1], b[2]-a[2]};
        int major = 0; for (int i=1;i<3;i++) if (Math.abs(delta[i]) > Math.abs(delta[major])) major=i;
        if (delta[major] == 0) return -1;
        int u=OTHER[major], v=OTHER[major+3], sign=delta[major]>0?1:-1;
        float du=(float)delta[u]/delta[major], dv=(float)delta[v]/delta[major];
        int[] p = new int[3];
        for (int n=0; n != delta[major]+sign; n+=sign) {
            p[major]=a[major]+n; p[u]=Mth.floor(a[u]+n*du); p[v]=Mth.floor(a[v]+n*dv);
            var pos = new BlockPos(p[0],p[1],p[2]); var state = level.getBlockState(pos);
            if (safe ? !lineReplaceable(state,pos) : state.is(Blocks.BEDROCK)) return Math.abs(n);
        }
        return -1;
    }
    /** Matches MFR's dominant-axis line placement, including its coordinate and axis rounding. */
    private void placeLine(int[] a, int[] b, boolean branches) {
        int[] delta = {b[0]-a[0], b[1]-a[1], b[2]-a[2]};
        int major=0; for (int i=1;i<3;i++) if (Math.abs(delta[i]) > Math.abs(delta[major])) major=i;
        if (delta[major] == 0) return;
        int u=OTHER[major],v=OTHER[major+3],sign=delta[major]>0?1:-1;
        float du=(float)delta[u]/delta[major],dv=(float)delta[v]/delta[major];
        int[] p=new int[3];
        for (int n=0;n!=delta[major]+sign;n+=sign) {
            p[major]=Mth.floor(a[major]+n+.5F); p[u]=Mth.floor(a[u]+n*du+.5F); p[v]=Mth.floor(a[v]+n*dv+.5F);
            int dx=Math.abs(p[0]-a[0]), dz=Math.abs(p[2]-a[2]);
            int axis=branches?3:(Math.max(dx,dz)==0?0:dx>=dz?1:2);
            put(new BlockPos(p[0],p[1],p[2]),logs[axis]);
        }
    }
    private void put(BlockPos pos, BlockState state) {
        if (!level.isOutsideBuildHeight(pos)) level.setBlock(pos, state, Block.UPDATE_CLIENTS);
    }
}
