package github.com.gengyoubo.tcmodern.tc4.content;

import github.com.gengyoubo.tcmodern.tc4.init.Tc4BlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import java.util.Map;
import github.com.gengyoubo.tcmodern.api.TcModernApi;
import net.minecraft.world.level.material.MapColor;
import org.jetbrains.annotations.Nullable;

public final class NodeReactorBlock extends BaseEntityBlock {
    private static final Map<Direction, BooleanProperty> TUBES = Map.of(
            Direction.DOWN, BooleanProperty.create("tube_down"), Direction.NORTH, BooleanProperty.create("tube_north"),
            Direction.SOUTH, BooleanProperty.create("tube_south"), Direction.WEST, BooleanProperty.create("tube_west"),
            Direction.EAST, BooleanProperty.create("tube_east"));

    public NodeReactorBlock() {
        super(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.5F).sound(SoundType.METAL));
        var state = stateDefinition.any();
        for (var property : TUBES.values()) state = state.setValue(property, false);
        registerDefaultState(state);
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        TUBES.values().forEach(builder::add);
    }
    @Nullable @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        var state = defaultBlockState();
        for (var entry : TUBES.entrySet())
            state = state.setValue(entry.getValue(), isTube(context.getLevel().getBlockState(context.getClickedPos().relative(entry.getKey()))));
        return state;
    }
    @Override public BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level,
                                            BlockPos pos, BlockPos neighborPos) {
        var property = TUBES.get(direction);
        return property == null ? state : state.setValue(property, isTube(neighbor));
    }
    private static boolean isTube(BlockState state) {
        return TcModernApi.runtime().flatMap(runtime -> runtime.hostBlock("Thaumcraft:blockTube", 0, BlockState.class))
                .map(tube -> state.is(tube.getBlock())).orElse(false);
    }
    public static boolean hasTubeConnection(BlockState state, Direction direction) {
        var property = TUBES.get(direction);
        return property != null && state.hasProperty(property) && state.getValue(property);
    }
    public static void refreshConnections(Level level, BlockPos pos, BlockState state) {
        var updated = state;
        for (var entry : TUBES.entrySet()) {
            var neighbor = pos.relative(entry.getKey());
            if (level.isLoaded(neighbor)) updated = updated.setValue(entry.getValue(), isTube(level.getBlockState(neighbor)));
        }
        if (updated != state) level.setBlock(pos, updated, Block.UPDATE_CLIENTS);
    }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Nullable @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new NodeReactorBlockEntity(pos, state); }
    @Nullable @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, Tc4BlockEntities.NODE_REACTOR.get(), NodeReactorBlockEntity::tick);
    }
    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moved) {
        if (!state.is(replacement.getBlock()) && !level.isClientSide && level.getBlockEntity(pos) instanceof NodeReactorBlockEntity reactor)
            reactor.removeOwnedNode();
        super.onRemove(state, level, pos, replacement, moved);
    }
}
