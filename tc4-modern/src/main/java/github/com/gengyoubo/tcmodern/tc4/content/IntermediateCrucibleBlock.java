package github.com.gengyoubo.tcmodern.tc4.content;

import github.com.gengyoubo.mfcl.api.LegacyMappings;
import github.com.gengyoubo.mfcl.api.LegacyObject;
import github.com.gengyoubo.mfcl.api.mla.LegacyMethod;
import github.com.gengyoubo.mfcl.api.mla.LegacyType;
import github.com.gengyoubo.tcmodern.api.TcModernApi;
import github.com.gengyoubo.tcmodern.tc4.init.Tc4BlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A crucible shaped like the original: fill it with a water bucket and heat it from below. A wand switches its mode,
 * sneaking with a wand empties it, and an empty hand starts a synthesis at once.
 */
public final class IntermediateCrucibleBlock extends BaseEntityBlock {
    private static final VoxelShape INSIDE = box(2, 4, 2, 14, 16, 14);
    private static final VoxelShape SHAPE = Shapes.join(Shapes.block(),
            Shapes.or(box(0, 0, 4, 16, 3, 12), box(4, 0, 0, 12, 3, 16), box(2, 0, 2, 14, 3, 14), INSIDE), BooleanOp.ONLY_FIRST);
    private static final String WAND = "thaumcraft.common.items.wands.ItemWandCasting";

    @LegacyType("net.minecraft.item.ItemStack") public interface Holder {
        @LegacyMethod(name = "func_77973_b", descriptor = "()Lnet/minecraft/item/Item;") LegacyObject item();
    }

    public IntermediateCrucibleBlock() {
        super(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0F, 17.0F).sound(SoundType.METAL)
                .requiresCorrectToolForDrops().noOcclusion());
    }

    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return SHAPE; }
    @Override public VoxelShape getInteractionShape(BlockState state, BlockGetter level, BlockPos pos) { return INSIDE; }
    @Override public boolean isPathfindable(BlockState state, BlockGetter level, BlockPos pos, PathComputationType type) { return false; }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Nullable @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new IntermediateCrucibleBlockEntity(pos, state); }

    @Nullable @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, Tc4BlockEntities.INTERMEDIATE_CRUCIBLE.get(), IntermediateCrucibleBlockEntity::tick);
    }

    @Override public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (level.isClientSide || entity.getY() >= pos.getY() + 0.9375 || !(level.getBlockEntity(pos) instanceof IntermediateCrucibleBlockEntity crucible)
                || !crucible.boiling()) return;
        if (entity instanceof ItemEntity item) crucible.accept(item);
        else if (entity instanceof LivingEntity living) crucible.scald(living);
    }

    @Override public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof IntermediateCrucibleBlockEntity crucible)) return InteractionResult.PASS;
        var held = player.getItemInHand(hand);
        if (held.is(Items.WATER_BUCKET)) {
            if (crucible.water() >= github.com.gengyoubo.tcmodern.tc4.crucible.CrucibleRules.WATER_CAPACITY) return InteractionResult.PASS;
            if (!level.isClientSide && crucible.fill(held))
                player.setItemInHand(hand, ItemUtils.createFilledResult(held, player, new ItemStack(Items.BUCKET)));
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (isWand(held)) {
            if (!level.isClientSide) {
                if (player.isShiftKeyDown()) crucible.spillRemnants(); else crucible.toggleMode(player);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (held.isEmpty() && hand == InteractionHand.MAIN_HAND && crucible.mode() == IntermediateCrucibleBlockEntity.Mode.SYNTHESIS
                && !crucible.materials().isEmpty()) {
            if (player instanceof ServerPlayer server) crucible.synthesize(server);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return InteractionResult.PASS;
    }

    /** Vanilla skips {@link #use} when a sneaking player holds an item; a sneaking wand still has to reach the crucible. */
    public static void onRightClickBlock(net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity().isShiftKeyDown() && event.getLevel().getBlockState(event.getPos()).getBlock() instanceof IntermediateCrucibleBlock
                && isWand(event.getItemStack())) event.setUseBlock(net.minecraftforge.eventbus.api.Event.Result.ALLOW);
    }

    /** Whether the stack is an original wand, staff or sceptre (ItemWandCasting). */
    static boolean isWand(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return TcModernApi.runtime().map(runtime -> {
            try {
                var legacy = runtime.mappings().toLegacy(LegacyMappings.ITEM_STACK, stack.copyWithCount(1)).orElse(null);
                if (legacy == null) return false;
                var item = runtime.accessor(Holder.class, legacy).item();
                return item != null && runtime.values().isInstance(item, WAND);
            } catch (RuntimeException unmapped) {
                return false;
            }
        }).orElse(false);
    }

    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moved) {
        if (!state.is(replacement.getBlock()) && !level.isClientSide && level.getBlockEntity(pos) instanceof IntermediateCrucibleBlockEntity crucible)
            crucible.onBroken();
        super.onRemove(state, level, pos, replacement, moved);
    }
}
