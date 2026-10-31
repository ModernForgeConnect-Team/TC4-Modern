package github.com.gengyoubo.tcmodern.tc4.content;

import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

/**
 * The modern research desk: two blocks placed together like a bed. The main half holds the inventory and the
 * floating drawer unit; the side half, on the user's right, carries the desk lamp and books. Archive cabinets touching the
 * desk, directly or through other cabinets, join it as one structure.
 */
public final class ResearchDeskBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public enum Half implements StringRepresentable {
        MAIN, SIDE;
        @Override public String getSerializedName() { return this == MAIN ? "main" : "side"; }
    }
    public static final EnumProperty<Half> HALF = EnumProperty.create("half", Half.class);
    // Model space faces north; the side half lies to the west there.
    private static final Map<Direction, VoxelShape> MAIN_SHAPES = shapes(new double[][]{
            {0, 13.5, 0, 16, 15.25, 16}, {9, 5, 1, 15.5, 13.5, 14.5}, {14.25, 0, 1.5, 15.25, 5, 14.5}});
    private static final Map<Direction, VoxelShape> SIDE_SHAPES = shapes(new double[][]{
            {0, 13.5, 0, 16, 15.25, 16}, {0.75, 0, 1.5, 1.75, 13.5, 14.5}, {1.5, 15, 11.5, 10, 22, 15}, {11, 15, 12, 15, 16.8, 15}});
    /** The desk lamp on the side half lights the desk. */
    private static final int LAMP_LIGHT = 10;

    public ResearchDeskBlock() {
        super(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BROWN).strength(2.5F).sound(SoundType.WOOD).noOcclusion()
                .lightLevel(state -> state.getValue(HALF) == Half.SIDE ? LAMP_LIGHT : 0));
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(HALF, Half.MAIN));
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING, HALF); }

    /** The side half lies to the right of someone facing the desk's front. */
    public static Direction toSide(Direction facing) { return facing.getCounterClockWise(); }

    public static BlockPos partner(BlockPos pos, BlockState state) {
        var toSide = toSide(state.getValue(FACING));
        return state.getValue(HALF) == Half.MAIN ? pos.relative(toSide) : pos.relative(toSide.getOpposite());
    }

    public static BlockPos main(BlockPos pos, BlockState state) {
        return state.getValue(HALF) == Half.MAIN ? pos : partner(pos, state);
    }

    @Nullable @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        var facing = context.getHorizontalDirection().getOpposite();
        var side = context.getClickedPos().relative(toSide(facing));
        var level = context.getLevel();
        return level.getBlockState(side).canBeReplaced(context) && level.getWorldBorder().isWithinBounds(side)
                ? defaultBlockState().setValue(FACING, facing) : null;
    }

    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide) {
            level.setBlock(partner(pos, state), state.setValue(HALF, Half.SIDE), Block.UPDATE_ALL);
            level.blockUpdated(pos, Blocks.AIR);
            state.updateNeighbourShapes(level, pos, Block.UPDATE_ALL);
        }
    }

    /**
     * Losing one half destroys the other. Only the main half's loot table drops the desk, so exactly one desk
     * drops whichever half breaks first, as with beds.
     */
    @Override public BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level,
                                            BlockPos pos, BlockPos neighborPos) {
        if (neighborPos.equals(partner(pos, state)) && !(neighbor.is(this) && neighbor.getValue(HALF) != state.getValue(HALF)
                && neighbor.getValue(FACING) == state.getValue(FACING))) return Blocks.AIR.defaultBlockState();
        return super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }

    /** In creative, breaking the side half must not let the main half drop the desk. */
    @Override public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && player.isCreative() && state.getValue(HALF) == Half.SIDE) {
            var main = partner(pos, state);
            var other = level.getBlockState(main);
            if (other.is(this) && other.getValue(HALF) == Half.MAIN) {
                level.setBlock(main, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
                level.levelEvent(player, 2001, main, Block.getId(other));
            }
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof ResearchDeskBlockEntity desk) desk.dropContents();
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        var main = main(pos, state);
        if (player instanceof ServerPlayer server && level.getBlockEntity(main) instanceof ResearchDeskBlockEntity desk)
            NetworkHooks.openScreen(server, desk, main);
        return InteractionResult.CONSUME;
    }

    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return (state.getValue(HALF) == Half.MAIN ? MAIN_SHAPES : SIDE_SHAPES).get(state.getValue(FACING));
    }

    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

    @Nullable @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(HALF) == Half.MAIN ? new ResearchDeskBlockEntity(pos, state) : null;
    }

    static Map<Direction, VoxelShape> shapes(double[][] boxes) {
        var result = new EnumMap<Direction, VoxelShape>(Direction.class);
        for (var facing : Direction.Plane.HORIZONTAL) {
            VoxelShape shape = Shapes.empty();
            for (var box : boxes) {
                double[] a = rotate(box[0], box[2], facing), b = rotate(box[3], box[5], facing);
                shape = Shapes.or(shape, Block.box(Math.min(a[0], b[0]), box[1], Math.min(a[1], b[1]),
                        Math.max(a[0], b[0]), box[4], Math.max(a[1], b[1])));
            }
            result.put(facing, shape.optimize());
        }
        return result;
    }

    /** Clockwise about the block centre, as blockstate {@code y} rotations turn north-facing models. */
    private static double[] rotate(double x, double z, Direction facing) {
        return switch (facing) {
            case EAST -> new double[]{16 - z, x};
            case SOUTH -> new double[]{16 - x, 16 - z};
            case WEST -> new double[]{z, 16 - x};
            default -> new double[]{x, z};
        };
    }
}
