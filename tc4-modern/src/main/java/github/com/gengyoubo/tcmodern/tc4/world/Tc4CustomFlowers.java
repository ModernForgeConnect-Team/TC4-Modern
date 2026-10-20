package github.com.gengyoubo.tcmodern.tc4.world;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** WorldGenCustomFlowers: 18 tries around a point, on grass or sand with air above. */
final class Tc4CustomFlowers {
    private Tc4CustomFlowers() { }

    static void place(Tc4GenRegion region, RandomSource random, BlockPos center, BlockState plant) {
        if (plant == null) return;
        for (int i = 0; i < 18; i++) {
            int x = center.getX() + random.nextInt(8) - random.nextInt(8);
            int y = center.getY() + random.nextInt(4) - random.nextInt(4);
            int z = center.getZ() + random.nextInt(8) - random.nextInt(8);
            var pos = new BlockPos(x, y, z);
            BlockState below = region.get(pos.below());
            if (region.get(pos).isAir() && (below.is(Blocks.GRASS_BLOCK) || below.is(Blocks.SAND) || below.is(Blocks.RED_SAND))) region.set(pos, plant);
        }
    }
}
