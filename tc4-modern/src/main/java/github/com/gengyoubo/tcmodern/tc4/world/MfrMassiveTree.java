package github.com.gengyoubo.tcmodern.tc4.world;

import java.util.ArrayList;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
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
    // Same dx-major, dz-minor order as the original leaf-layer loops.
    private static final int[] LEAF_OFFSETS_2 = leafOffsets(2), LEAF_OFFSETS_3 = leafOffsets(3);
    private final Level level;
    private final BlockPos origin;
    private final BlockState[] logs;
    private final BlockState leaves;
    private final boolean nativeLeaves, nativeBark;
    private final IPlantable sapling;
    private final Random random;
    private final long seed;
    private final ArrayList<LeafNode> nodes = new ArrayList<>();
    // These cursors are used only by synchronous read-only scans; writes still receive immutable positions.
    private final BlockPos.MutableBlockPos readPos = new BlockPos.MutableBlockPos();
    private final int[] scanStart = new int[3], scanDelta = new int[3], scanPosition = new int[3];
    private final int[] lineStart = new int[3], lineDelta = new int[3], linePosition = new int[3];
    private boolean readCached;
    private int cachedX, cachedY, cachedZ;
    private BlockState cachedState;
    // Only used by the unsafe bedrock scan, while this synchronous generator owns the server thread.
    private LevelChunk scanChunk;
    private int scanChunkX, scanChunkZ;
    private float heightAttenuation = .45F, branchSlope = .45F, scaleWidth = 4F, branchDensity = 3F;
    private int trunkRadius = 11, heightLimitLimit = 250, leafDistance = 4, minHeight = 80;
    private int heightLimit, height, leafBases, density;
    private boolean sloped, safe;

    private MfrMassiveTree(Level level, BlockPos origin, IPlantable sapling, BlockState[] logs, BlockState leaves,
                           Kind kind, int scale, long seed) {
        this.level = level; this.origin = origin; this.sapling = sapling; this.logs = logs; this.leaves = leaves;
        this.nativeLeaves = leaves.getBlock() instanceof github.com.gengyoubo.tcmodern.tc4.content.MagicalLeavesBlock;
        this.nativeBark = logs[3].is(github.com.gengyoubo.tcmodern.tc4.init.Tc4Blocks.GREATWOOD_WOOD.get())
                || logs[3].is(github.com.gengyoubo.tcmodern.tc4.init.Tc4Blocks.SILVERWOOD_WOOD.get());
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
                if (checkLine(x, y, z, x, y + leafDistance, z) != -1) continue;
                int dx = origin.getX() - x, dz = origin.getZ() - z;
                int drop = (int)(Math.sqrt(dx * dx + dz * dz) * branchSlope);
                int branchY = Math.min(y - drop, trunkTop);
                if (checkLine(origin.getX(), branchY, origin.getZ(), x, y, z) == -1)
                    nodes.add(new LeafNode(x, y, z, branchY));
            }
            y--; relativeY--;
        }
    }
    private static int[] leafOffsets(int radius) {
        var offsets = new ArrayList<Integer>();
        for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
            if (dx*dx + Math.abs(dx) + dz*dz + Math.abs(dz) + .5F > radius*radius) continue;
            offsets.add(dx); offsets.add(dz);
        }
        return offsets.stream().mapToInt(Integer::intValue).toArray();
    }
    private void generateLeaves() {
        for (var node : nodes) for (int layer = 0; layer < leafDistance; layer++) {
            int[] offsets = layer != 0 && layer != leafDistance - 1 ? LEAF_OFFSETS_3 : LEAF_OFFSETS_2;
            int y = node.y + layer;
            for (int i = 0; i < offsets.length; i += 2) {
                int x = node.x + offsets[i], z = node.z + offsets[i + 1];
                var old = readState(x, y, z);
                if ((safe ? leafReplaceable(old) : !old.is(Blocks.BEDROCK))
                        && !(nativeLeaves && old == leaves && !level.captureBlockSnapshots))
                    put(new BlockPos(x, y, z), leaves);
            }
        }
    }
    private void generateBranches() {
        int minimum = (int)(heightLimit * .2F);
        for (var node : nodes) if (node.branchY - origin.getY() >= minimum)
            placeLine(origin.getX(), node.branchY, origin.getZ(), node.x, node.y, node.z, true);
    }
    private void generateTrunk() {
        int x0 = origin.getX(), y0 = origin.getY(), z0 = origin.getZ();
        double factor = 400F / trunkRadius;
        for (int dx = -trunkRadius; dx <= trunkRadius; dx++) for (int dz = -trunkRadius; dz <= trunkRadius; dz++) {
            if ((dx*dx + dz*dz)*4 >= trunkRadius*trunkRadius*5) continue;
            int endY = y0 + height;
            if (sloped) endY = y0 + sinc(factor * dx, factor * dz, height) - (random.nextInt(3) - 1);
            int x = x0 + dx, z = z0 + dz;
            placeLine(x, y0, z, x, endY, z, false);
            put(new BlockPos(x, endY, z), logs[3]);
            var soil = new BlockPos(x, y0-1, z);
            var ground = readState(x, y0-1, z);
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
        int clear = checkLine(origin.getX(), origin.getY(), origin.getZ(),
                origin.getX(), origin.getY()+heightLimit-1, origin.getZ());
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
                if (checkLine(origin.getX()+dx, origin.getY(), origin.getZ()+dz,
                        origin.getX()+dx, top, origin.getZ()+dz) != -1) return false;
            }
        }
        return true;
    }
    private boolean leafReplaceable(BlockState state) {
        return state.isAir() || state.is(BlockTags.LEAVES) || state.canBeReplaced();
    }
    private boolean lineReplaceable(BlockState state) {
        return state.isAir() || state.canBeReplaced()
                || state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS) || state.getBlock() instanceof SaplingBlock;
    }
    /** Only the last read is cached; all writes invalidate it before any block callbacks can run. */
    private BlockState readState(int x, int y, int z) {
        if (readCached && x == cachedX && y == cachedY && z == cachedZ) return cachedState;
        var state = level.getBlockState(readPos.set(x, y, z));
        cachedX = x; cachedY = y; cachedZ = z; cachedState = state; readCached = true;
        return state;
    }
    /** Matches MFR's dominant-axis line scan and floor rounding. */
    private int checkLine(int ax, int ay, int az, int bx, int by, int bz) {
        int[] a=scanStart, delta=scanDelta, p=scanPosition;
        a[0]=ax; a[1]=ay; a[2]=az;
        delta[0]=bx-ax; delta[1]=by-ay; delta[2]=bz-az;
        int major = 0; for (int i=1;i<3;i++) if (Math.abs(delta[i]) > Math.abs(delta[major])) major=i;
        if (delta[major] == 0) return -1;
        int u=OTHER[major], v=OTHER[major+3], sign=delta[major]>0?1:-1;
        float du=(float)delta[u]/delta[major], dv=(float)delta[v]/delta[major];
        for (int n=0; n != delta[major]+sign; n+=sign) {
            p[major]=a[major]+n; p[u]=Mth.floor(a[u]+n*du); p[v]=Mth.floor(a[v]+n*dv);
            if (safe ? !lineReplaceable(readState(p[0],p[1],p[2])) : isBedrock(p[0],p[1],p[2]))
                return Math.abs(n);
        }
        return -1;
    }
    /** Empty sections cannot contain bedrock; avoid per-block lookups without caching their contents. */
    private boolean isBedrock(int x, int y, int z) {
        if (y < level.getMinBuildHeight() || y >= level.getMaxBuildHeight()) return false;
        // Subclasses may override getBlockState, so retain the normal query for them.
        if (level.getClass() != ServerLevel.class) return readState(x, y, z).is(Blocks.BEDROCK);
        int cx = x >> 4, cz = z >> 4;
        if (scanChunk == null || cx != scanChunkX || cz != scanChunkZ) {
            // Loading/populating a chunk can mutate another position previously cached.
            readCached = false;
            scanChunk = level.getChunk(cx, cz);
            scanChunkX = cx; scanChunkZ = cz;
        }
        if (scanChunk.getSection(scanChunk.getSectionIndex(y)).hasOnlyAir()) return false;
        return readState(x, y, z).is(Blocks.BEDROCK);
    }
    /** Matches MFR's dominant-axis line placement, including its coordinate and axis rounding. */
    private void placeLine(int ax, int ay, int az, int bx, int by, int bz, boolean branches) {
        int[] a=lineStart, delta=lineDelta, p=linePosition;
        a[0]=ax; a[1]=ay; a[2]=az;
        delta[0]=bx-ax; delta[1]=by-ay; delta[2]=bz-az;
        int major=0; for (int i=1;i<3;i++) if (Math.abs(delta[i]) > Math.abs(delta[major])) major=i;
        if (delta[major] == 0) return;
        int u=OTHER[major],v=OTHER[major+3],sign=delta[major]>0?1:-1;
        float du=(float)delta[u]/delta[major],dv=(float)delta[v]/delta[major];
        for (int n=0;n!=delta[major]+sign;n+=sign) {
            p[major]=Mth.floor(a[major]+n+.5F); p[u]=Mth.floor(a[u]+n*du+.5F); p[v]=Mth.floor(a[v]+n*dv+.5F);
            int axis=3;
            if (!branches) {
                int dx=Math.abs(p[0]-a[0]), dz=Math.abs(p[2]-a[2]);
                axis=Math.max(dx,dz)==0?0:dx>=dz?1:2;
            }
            // Bark branches overlap heavily in the massive tree. A same-state LevelChunk write
            // returns without notifications; snapshots must still go through Level.setBlock.
            if (branches && nativeBark && !level.captureBlockSnapshots
                    && readState(p[0],p[1],p[2]) == logs[axis]) continue;
            put(new BlockPos(p[0],p[1],p[2]),logs[axis]);
        }
    }
    private void put(BlockPos pos, BlockState state) {
        // onPlace and neighbouring callbacks can change any block, so even a cached hit is invalid after a write.
        readCached = false;
        if (!level.isOutsideBuildHeight(pos)) level.setBlock(pos, state, Block.UPDATE_CLIENTS);
    }
}
