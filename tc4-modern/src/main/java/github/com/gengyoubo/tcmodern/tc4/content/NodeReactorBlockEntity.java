package github.com.gengyoubo.tcmodern.tc4.content;

import com.mojang.logging.LogUtils;
import github.com.gengyoubo.mfcl.api.*;
import github.com.gengyoubo.tcmodern.api.TcModernApi;
import github.com.gengyoubo.tcmodern.tc4.init.Tc4BlockEntities;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Owns one original node above the block and pulls essentia from adjacent TC4 transports. */
public final class NodeReactorBlockEntity extends BlockEntity {
    private static final int CAPACITY = 1000;
    private static final String NODE_CLASS = "thaumcraft.common.tiles.TileNode";
    private static final String TRANSPORT_CLASS = "thaumcraft.api.aspects.IEssentiaTransport";
    private String nodeId = "";
    private boolean loggedFailure;

    public NodeReactorBlockEntity(BlockPos pos, BlockState state) { super(Tc4BlockEntities.NODE_REACTOR.get(), pos, state); }
    @Override protected void saveAdditional(CompoundTag tag) { super.saveAdditional(tag); tag.putString("NodeId", nodeId); }
    @Override public void load(CompoundTag tag) { super.load(tag); nodeId = tag.getString("NodeId"); }

    public static void tick(Level level, BlockPos pos, BlockState state, NodeReactorBlockEntity reactor) {
        if (level.isClientSide || !level.isLoaded(pos.above())) return;
        if (level.getGameTime() % 20 == 0) NodeReactorBlock.refreshConnections(level, pos, state);
        var runtime = TcModernApi.runtime().orElse(null);
        if (runtime == null) return;
        try {
            var world = runtime.mappings().toLegacy(LegacyMappings.WORLD, level).orElse(null);
            if (world == null) return;
            var access = runtime.accessor(ReactorAccessors.World.class, world);
            var node = reactor.owned(runtime, access);
            if (node != null) stabilize(runtime, node);
            else if (!reactor.nodeId.isEmpty() && level.isEmptyBlock(pos.above())) {
                reactor.nodeId = "";
                reactor.setChanged();
            }
            if (level.getGameTime() % 10 != 0) return;
            reactor.pull(runtime, access, node);
        } catch (RuntimeException failure) {
            if (!reactor.loggedFailure) {
                reactor.loggedFailure = true;
                LogUtils.getLogger().error("Node reactor at {} could not access TC4", pos, failure);
            }
        }
    }

    private LegacyObject tile(ReactorAccessors.World world, BlockPos pos) { return world.tile(pos.getX(), pos.getY(), pos.getZ()); }

    private LegacyObject owned(LegacyRuntime runtime, ReactorAccessors.World world) {
        if (nodeId.isEmpty()) return null;
        var value = tile(world, worldPosition.above());
        if (value == null || !runtime.values().isInstance(value, NODE_CLASS)) return null;
        return nodeId.equals(runtime.accessor(ReactorAccessors.Node.class, value).id()) ? value : null;
    }

    /** Stop TC4's recharge, offline catch-up, and its 1200-tick depleted-aspect erosion. */
    private static void stabilize(LegacyRuntime runtime, LegacyObject node) {
        var control = runtime.accessor(ReactorAccessors.Node.class, node);
        control.count(1);
        control.regeneration(Integer.MAX_VALUE);
        control.catchUp(false);
    }

    private void pull(LegacyRuntime runtime, ReactorAccessors.World world, LegacyObject node) {
        for (Direction direction : Direction.values()) {
            if (direction == Direction.UP) continue;
            BlockPos sourcePos = worldPosition.relative(direction);
            if (!level.isLoaded(sourcePos)) continue;
            var source = tile(world, sourcePos);
            if (source == null || !runtime.values().isInstance(source, TRANSPORT_CLASS)) continue;
            var side = runtime.accessor(ReactorAccessors.Directions.class).of(direction.getOpposite().get3DDataValue());
            var transport = runtime.accessor(ReactorAccessors.Transport.class, source);
            if (!transport.canOutput(side) || transport.amount(side) <= 0) continue;
            var aspect = transport.type(side);
            if (aspect == null) continue;
            boolean created = false;
            if (node == null) {
                if (!nodeId.isEmpty() || !level.isEmptyBlock(worldPosition.above())) return;
                var state = runtime.hostBlock("Thaumcraft:blockAiry", 0, BlockState.class).orElse(null);
                if (state == null || !level.setBlock(worldPosition.above(), state, Block.UPDATE_ALL)) return;
                node = tile(world, worldPosition.above());
                if (node == null || !runtime.values().isInstance(node, NODE_CLASS)) {
                    level.removeBlock(worldPosition.above(), false);
                    return;
                }
                nodeId = "tc4modern:" + UUID.randomUUID();
                runtime.accessor(ReactorAccessors.Node.class, node).id(nodeId);
                stabilize(runtime, node);
                setChanged();
                created = true;
            }
            var control = runtime.accessor(ReactorAccessors.Node.class, node);
            var list = runtime.accessor(ReactorAccessors.Aspects.class, control.aspects());
            if (list.amount(aspect) >= CAPACITY) continue;
            int taken = transport.take(aspect, 1, side);
            if (taken == 1) {
                list.add(aspect, 1);
                control.dirty();
                var at = worldPosition.above();
                world.update(at.getX(), at.getY(), at.getZ());
                var sourceTile = level.getBlockEntity(sourcePos);
                if (sourceTile != null) sourceTile.setChanged();
                world.update(sourcePos.getX(), sourcePos.getY(), sourcePos.getZ());
                return;
            }
            if (created) { removeOwnedNode(); node = null; }
        }
    }

    public void removeOwnedNode() {
        if (level == null || nodeId.isEmpty() || !level.isLoaded(worldPosition.above())) return;
        var runtime = TcModernApi.runtime().orElse(null);
        if (runtime == null) return;
        var legacy = runtime.mappings().toLegacy(LegacyMappings.WORLD, level).orElse(null);
        if (legacy != null && owned(runtime, runtime.accessor(ReactorAccessors.World.class, legacy)) != null)
            level.removeBlock(worldPosition.above(), false);
        nodeId = "";
        setChanged();
    }
}
