package github.com.gengyoubo.tcmodern.tc4.content;

import github.com.gengyoubo.tcmodern.tc4.init.Tc4BlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.shapes.*;
import org.jetbrains.annotations.Nullable;

/** A sapling with a persistent, tick-budgeted generator. */
public final class GiantSaplingBlock extends BushBlock implements EntityBlock, BonemealableBlock {
    private final boolean silver;
    private final int size;
    public GiantSaplingBlock(boolean silver, int size) {
        super(BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).noCollission().randomTicks().instabreak().sound(SoundType.GRASS));
        this.silver = silver; this.size = size;
    }
    public boolean silver() { return silver; }
    public int size() { return size; }
    @Override public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) { return Block.box(2, 0, 2, 14, 14, 14); }
    @Override public void randomTick(BlockState s, ServerLevel l, BlockPos p, RandomSource r) {
        if (l.getMaxLocalRawBrightness(p.above()) >= 9 && r.nextInt(7) == 0) start(l, p);
    }
    private void start(Level l, BlockPos p) {
        if (l.getBlockEntity(p) instanceof GiantSaplingEntity entity) entity.start();
    }
    @Override public boolean isValidBonemealTarget(LevelReader l, BlockPos p, BlockState s, boolean client) { return true; }
    @Override public boolean isBonemealSuccess(Level l, RandomSource r, BlockPos p, BlockState s) { return r.nextFloat() < 0.45F; }
    @Override public void performBonemeal(ServerLevel l, RandomSource r, BlockPos p, BlockState s) { start(l, p); }
    @Override public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        var held = player.getItemInHand(hand);
        if (size == 2 && (held.isEmpty() || held.is(Items.BONE_MEAL) || held.getItem() instanceof MagicBoneMealItem)
                && level.getBlockEntity(pos) instanceof GiantSaplingEntity tree && tree.isTreeGenerating()) {
            if (!level.isClientSide) player.displayClientMessage(tree.generationStatus(), true);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return InteractionResult.PASS;
    }
    @Nullable @Override public BlockEntity newBlockEntity(BlockPos p, BlockState s) { return new GiantSaplingEntity(p, s); }
    @Nullable @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level l, BlockState s, BlockEntityType<T> type) {
        if (l.isClientSide || type != Tc4BlockEntities.GIANT_SAPLING.get()) return null;
        return (world, pos, state, entity) -> GiantSaplingEntity.tick(world, pos, state, (GiantSaplingEntity) entity);
    }
}
