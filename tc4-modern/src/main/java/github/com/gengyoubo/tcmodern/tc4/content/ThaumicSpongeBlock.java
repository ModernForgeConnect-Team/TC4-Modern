package github.com.gengyoubo.tcmodern.tc4.content;

import github.com.gengyoubo.tcmodern.api.TcModernApi;
import github.com.gengyoubo.tcmodern.tc4.init.Tc4Blocks;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/** Absorbs finite TC4 flux in eighths of a bucket, retaining partial capacity in block state. */
public final class ThaumicSpongeBlock extends Block {
    public static final IntegerProperty ABSORBED = IntegerProperty.create("absorbed", 0, 7);
    private static final int RADIUS = 4, CAPACITY = 8, INTERVAL = 20;
    private record Flux(boolean goo, int quanta) { }

    public ThaumicSpongeBlock() {
        super(BlockBehaviour.Properties.copy(Blocks.SPONGE));
        registerDefaultState(stateDefinition.any().setValue(ABSORBED, 0));
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(ABSORBED); }

    @Override public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moving) {
        super.onPlace(state, level, pos, oldState, moving);
        if (!level.isClientSide && !oldState.is(state.getBlock())) level.scheduleTick(pos, this, 1);
    }

    @Override public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        var runtime = TcModernApi.runtime().orElse(null);
        if (runtime != null) {
            var types = new HashMap<BlockState, Flux>();
            for (int meta = 0; meta < CAPACITY; meta++) {
                final int quanta = meta + 1;
                runtime.hostBlock("Thaumcraft:blockFluxGoo", meta, BlockState.class).ifPresent(s -> types.put(s, new Flux(true, quanta)));
                runtime.hostBlock("Thaumcraft:blockFluxGas", meta, BlockState.class).ifPresent(s -> types.put(s, new Flux(false, quanta)));
            }
            var candidates = new ArrayList<BlockPos>();
            for (var candidate : BlockPos.betweenClosed(pos.offset(-RADIUS, -RADIUS, -RADIUS), pos.offset(RADIUS, RADIUS, RADIUS))) {
                if (level.hasChunkAt(candidate) && types.containsKey(level.getBlockState(candidate))) candidates.add(candidate.immutable());
            }
            candidates.sort(Comparator.comparingDouble(pos::distSqr));
            int absorbed = state.getValue(ABSORBED);
            for (boolean goo : new boolean[]{true, false}) for (var target : candidates) {
                if (absorbed == CAPACITY) break;
                var flux = types.get(level.getBlockState(target));
                if (flux == null || flux.goo() != goo) continue;
                int taken = Math.min(CAPACITY - absorbed, flux.quanta());
                int left = flux.quanta() - taken;
                BlockState replacement = left == 0 ? Blocks.AIR.defaultBlockState()
                        : runtime.hostBlock(goo ? "Thaumcraft:blockFluxGoo" : "Thaumcraft:blockFluxGas", left - 1, BlockState.class).orElse(null);
                if (replacement != null && level.setBlock(target, replacement, Block.UPDATE_ALL)) absorbed += taken;
            }
            if (absorbed == CAPACITY) {
                level.setBlock(pos, Tc4Blocks.SATURATED_THAUMIC_SPONGE.get().defaultBlockState(), Block.UPDATE_ALL);
                return;
            }
            if (absorbed != state.getValue(ABSORBED)) level.setBlock(pos, state.setValue(ABSORBED, absorbed), Block.UPDATE_CLIENTS);
        }
        level.scheduleTick(pos, this, INTERVAL);
    }
}
