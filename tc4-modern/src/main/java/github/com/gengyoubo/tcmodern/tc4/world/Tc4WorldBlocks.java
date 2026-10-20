package github.com.gengyoubo.tcmodern.tc4.world;

import github.com.gengyoubo.mfcl.api.LegacyRuntime;
import net.minecraft.world.level.block.state.BlockState;

/**
 * MFCL host states of the original blocks the TC4 decorators place, resolved once the runtime is ready because
 * generation runs off-thread. A null state means MFCL has no host block for it, and whatever places it is skipped.
 *
 * @param greatwoodLog   Thaumcraft:blockMagicalLog meta 0, 4, 8 (upright, east-west, north-south)
 * @param silverwoodLog  Thaumcraft:blockMagicalLog meta 1, 5, 9
 */
record Tc4WorldBlocks(BlockState[] greatwoodLog, BlockState[] silverwoodLog, BlockState greatwoodLeaves,
                      BlockState silverwoodLeaves, BlockState shimmerleaf, BlockState cinderpearl, BlockState vishroom,
                      BlockState crustedTaint, BlockState taintFibres) {
    private static final String LOG = "Thaumcraft:blockMagicalLog", LEAVES = "Thaumcraft:blockMagicalLeaves",
            PLANT = "Thaumcraft:blockCustomPlant", TAINT = "Thaumcraft:blockTaint", FIBRES = "Thaumcraft:blockTaintFibres";

    static Tc4WorldBlocks resolve(LegacyRuntime runtime) {
        return new Tc4WorldBlocks(logs(runtime, 0), logs(runtime, 1), state(runtime, LEAVES, 0), state(runtime, LEAVES, 1),
                state(runtime, PLANT, 2), state(runtime, PLANT, 3), state(runtime, PLANT, 5),
                state(runtime, TAINT, 0), state(runtime, FIBRES, 0));
    }

    private static BlockState[] logs(LegacyRuntime runtime, int wood) {
        var states = new BlockState[] { state(runtime, LOG, wood), state(runtime, LOG, wood | 4), state(runtime, LOG, wood | 8) };
        for (var state : states) if (state == null) return null;
        return states;
    }

    private static BlockState state(LegacyRuntime runtime, String key, int meta) {
        return runtime.hostBlock(key, meta, BlockState.class).orElse(null);
    }

    boolean isMagicalLog(BlockState state) {
        return greatwoodLog != null && state.is(greatwoodLog[0].getBlock()) || silverwoodLog != null && state.is(silverwoodLog[0].getBlock());
    }

    boolean isMagicalLeaves(BlockState state) {
        return greatwoodLeaves != null && state.is(greatwoodLeaves.getBlock()) || silverwoodLeaves != null && state.is(silverwoodLeaves.getBlock());
    }
}
