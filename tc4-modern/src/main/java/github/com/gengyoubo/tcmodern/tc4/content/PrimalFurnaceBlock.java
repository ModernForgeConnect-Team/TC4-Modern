package github.com.gengyoubo.tcmodern.tc4.content;

import github.com.gengyoubo.tcmodern.api.TcModernApi;
import github.com.gengyoubo.tcmodern.tc4.init.Tc4BlockEntities;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;
import net.minecraftforge.network.NetworkHooks;

/** Bottom essentia input; an alembic stack above the furnace extracts its output. */
public final class PrimalFurnaceBlock extends BaseEntityBlock {
    private static final Map<Direction, BooleanProperty> TUBES = Map.of(
            Direction.UP,BooleanProperty.create("tube_up"),Direction.DOWN,BooleanProperty.create("tube_down"),
            Direction.NORTH,BooleanProperty.create("tube_north"),Direction.SOUTH,BooleanProperty.create("tube_south"),
            Direction.WEST,BooleanProperty.create("tube_west"),Direction.EAST,BooleanProperty.create("tube_east"));
    public PrimalFurnaceBlock() {
        super(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.5F).sound(SoundType.METAL));
        var state=stateDefinition.any(); for (var property:TUBES.values()) state=state.setValue(property,false); registerDefaultState(state);
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder) { TUBES.values().forEach(builder::add); }
    @Nullable @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        var state=defaultBlockState();
        for (var entry:TUBES.entrySet()) state=state.setValue(entry.getValue(),entry.getKey()==Direction.DOWN && isTube(context.getLevel().getBlockState(context.getClickedPos().relative(entry.getKey()))));
        return state;
    }
    @Override public BlockState updateShape(BlockState state,Direction direction,BlockState neighbor,LevelAccessor level,BlockPos pos,BlockPos neighborPos) {
        var property=TUBES.get(direction); return property==null?state:state.setValue(property,direction==Direction.DOWN && isTube(neighbor));
    }
    private static boolean isTube(BlockState state) {
        return TcModernApi.runtime().flatMap(api->api.hostBlock("Thaumcraft:blockTube",0,BlockState.class)).map(tube->state.is(tube.getBlock())).orElse(false);
    }
    static void refreshConnections(Level level,BlockPos pos,BlockState state) {
        var updated=state;
        for (var entry:TUBES.entrySet()) { var neighbor=pos.relative(entry.getKey()); if (level.isLoaded(neighbor)) updated=updated.setValue(entry.getValue(),entry.getKey()==Direction.DOWN && isTube(level.getBlockState(neighbor))); }
        if (updated!=state) level.setBlock(pos,updated,Block.UPDATE_CLIENTS);
    }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Nullable @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state) { return new PrimalFurnaceBlockEntity(pos,state); }
    @Nullable @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type) {
        return level.isClientSide?null:createTickerHelper(type,Tc4BlockEntities.PRIMAL_FURNACE.get(),PrimalFurnaceBlockEntity::tick);
    }
    @Override public InteractionResult use(BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer server && level.getBlockEntity(pos) instanceof PrimalFurnaceBlockEntity furnace)
            NetworkHooks.openScreen(server,furnace,pos);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override public void onRemove(BlockState state,Level level,BlockPos pos,BlockState replacement,boolean moved) {
        if (!state.is(replacement.getBlock()) && level.getBlockEntity(pos) instanceof PrimalFurnaceBlockEntity furnace) furnace.dropItems();
        super.onRemove(state,level,pos,replacement,moved);
    }
}
