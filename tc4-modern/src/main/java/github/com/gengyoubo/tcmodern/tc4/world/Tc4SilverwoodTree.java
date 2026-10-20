package github.com.gengyoubo.tcmodern.tc4.world;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

/**
 * WorldGenSilverwoodTrees as world generation ran it, shimmerleaf patch included. The original turned one trunk
 * block into a silverwood knot holding a pure aura node; MFCL cannot fill a node's aspects from host generation
 * yet, so that block stays plain silverwood (its random roll is kept so the rest of the tree is unchanged).
 */
final class Tc4SilverwoodTree {
    private Tc4SilverwoodTree() { }

    static boolean grow(Tc4GenRegion region, RandomSource random, BlockPos origin, int minHeight, int randomHeight) {
        var blocks = region.blocks;
        if (blocks.silverwoodLog() == null || blocks.silverwoodLeaves() == null) return false;
        BlockState log = blocks.silverwoodLog()[0], eastWest = blocks.silverwoodLog()[1], northSouth = blocks.silverwoodLog()[2];
        int height = random.nextInt(randomHeight) + minHeight;
        int x = origin.getX(), y = origin.getY(), z = origin.getZ();
        var level = region.level;
        if (y < level.getMinBuildHeight() + 1 || y + height + 1 > level.getMaxBuildHeight()) return false;
        for (int yy = y; yy <= y + 1 + height; yy++) {
            int spread = yy == y ? 0 : 1;
            if (yy >= y + 1 + height - 2) spread = 3;
            for (int xx = x - spread; xx <= x + spread; xx++) for (int zz = z - spread; zz <= z + spread; zz++) {
                var pos = new BlockPos(xx, yy, zz);
                if (level.isOutsideBuildHeight(pos)) return false;
                if (yy > y && !region.treeReplaceable(region.get(pos))) return false;
            }
        }
        if (!Tc4GenRegion.soil(region.get(origin.below())) || y >= level.getMaxBuildHeight() - height - 1) return false;
        region.plantGrow(origin.below());

        int top = y + height + 3 + random.nextInt(3);
        for (int yy = y + height - 5; yy <= top; yy++) {
            int core = Mth.clamp(yy, y + height - 3, y + height);
            for (int xx = x - 5; xx <= x + 5; xx++) for (int zz = z - 5; zz <= z + 5; zz++) {
                double dx = xx - x, dy = yy - core, dz = zz - z;
                var pos = new BlockPos(xx, yy, zz);
                if (dx * dx + dy * dy + dz * dz < 10 + random.nextInt(8) && region.treeReplaceable(region.get(pos))) region.set(pos, blocks.silverwoodLeaves());
            }
        }

        int chance = (int) (height * 1.5);
        boolean knot = false;
        int i = 0;
        for (; i < height; i++) {
            var pos = new BlockPos(x, y + i, z);
            if (!region.treeReplaceable(region.get(pos))) continue;
            if (i > 0 && !knot && random.nextInt(chance) == 0) { chance += height; knot = true; }
            else knot = false;
            region.set(pos, log);
            region.set(pos.west(), log); region.set(pos.east(), log); region.set(pos.north(), log); region.set(pos.south(), log);
        }
        region.set(new BlockPos(x, y + i, z), log);
        corners(region, x, y, z, log);
        for (int[] corner : CORNERS) if (random.nextInt(3) != 0) region.set(new BlockPos(x + corner[0], y + 1, z + corner[1]), log);
        roots(region, x, y, z, eastWest, northSouth);
        region.set(new BlockPos(x - 2, y - 1, z), log); region.set(new BlockPos(x + 2, y - 1, z), log);
        region.set(new BlockPos(x, y - 1, z - 2), log); region.set(new BlockPos(x, y - 1, z + 2), log);
        corners(region, x, y + height - 4, z, log);
        for (int[] corner : CORNERS) if (random.nextInt(3) == 0) region.set(new BlockPos(x + corner[0], y + height - 5, z + corner[1]), log);
        roots(region, x, y + height - 4, z, eastWest, northSouth);
        Tc4CustomFlowers.place(region, random, origin, blocks.shimmerleaf());
        return true;
    }

    /** The original diagonal order: (-1,-1), (+1,+1), (-1,+1), (+1,-1). */
    private static final int[][] CORNERS = { { -1, -1 }, { 1, 1 }, { -1, 1 }, { 1, -1 } };

    private static void corners(Tc4GenRegion region, int x, int y, int z, BlockState log) {
        for (int[] corner : CORNERS) region.set(new BlockPos(x + corner[0], y, z + corner[1]), log);
    }

    private static void roots(Tc4GenRegion region, int x, int y, int z, BlockState eastWest, BlockState northSouth) {
        region.set(new BlockPos(x - 2, y, z), eastWest); region.set(new BlockPos(x + 2, y, z), eastWest);
        region.set(new BlockPos(x, y, z - 2), northSouth); region.set(new BlockPos(x, y, z + 2), northSouth);
    }
}
