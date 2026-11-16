package github.com.gengyoubo.tcmodern.tc4.entity;

import github.com.gengyoubo.mfcl.api.LegacyObject;
import github.com.gengyoubo.mfcl.api.LegacyRuntime;
import github.com.gengyoubo.mfcl.api.mla.LegacyField;
import github.com.gengyoubo.mfcl.api.mla.LegacyType;
import github.com.gengyoubo.tcmodern.api.TcModernApi;
import github.com.gengyoubo.tcmodern.tc4.init.Tc4Entities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.network.NetworkHooks;

/** A TC4 taint block in flight, preserving its original source and metadata. */
public final class FallingTaint extends Entity {
    private static final String LEGACY_CLASS = "thaumcraft.common.entities.EntityFallingTaint";
    private static final EntityDataAccessor<Integer> STATE = SynchedEntityData.defineId(FallingTaint.class, EntityDataSerializers.INT);
    private BlockPos source;
    private int fallTime;
    private byte[] pendingLegacyData;
    public FallingTaint(EntityType<? extends FallingTaint> type, Level level) { super(type, level); blocksBuilding = true; }
    public static void registerLegacyHost(LegacyRuntime runtime) {
        runtime.entityHosts().register(LEGACY_CLASS, (level, original) -> {
            var old = runtime.accessor(OldFallingTaint.class, original);
            LegacyObject block = old.block();
            if (block == null || !runtime.values().isInstance(block, "thaumcraft.common.blocks.BlockTaint")) return null;
            var state = runtime.hostBlock("Thaumcraft:blockTaint", old.metadata(), BlockState.class).orElse(null);
            if (state == null) return null;
            var entity = Tc4Entities.FALLING_TAINT.get().create((ServerLevel) level);
            if (entity != null) {
                entity.setBlockState(state);
                entity.source = new BlockPos(old.oldX(), old.oldY(), old.oldZ());
                entity.fallTime = old.fallTime();
            }
            return entity;
        });
    }
    @Override protected void defineSynchedData() { entityData.define(STATE, Block.getId(Blocks.AIR.defaultBlockState())); }
    public BlockState blockState() { return Block.stateById(entityData.get(STATE)); }
    private void setBlockState(BlockState state) { entityData.set(STATE, Block.getId(state)); }
    @Override public boolean isPickable() { return !isRemoved(); }
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket() { return NetworkHooks.getEntitySpawningPacket(this); }
    @Override public void tick() {
        super.tick();
        if (level().isClientSide) { move(MoverType.SELF, getDeltaMovement()); setDeltaMovement(getDeltaMovement().add(0, -0.04, 0).scale(0.98)); return; }
        var runtime = TcModernApi.runtime().orElse(null);
        if (runtime != null) {
            var hosts = runtime.entityHosts();
            if (pendingLegacyData != null) { hosts.restore(this, LEGACY_CLASS, pendingLegacyData); pendingLegacyData = null; }
            if (hosts.original(this).isPresent()) hosts.sync(this);
        }
        BlockState falling = blockState();
        if (falling.isAir() || source == null) { discard(); return; }
        if (fallTime == 0) {
            if (!level().hasChunkAt(source)) return;
            if (!level().getBlockState(source).is(falling.getBlock())) { discard(); return; }
            level().removeBlock(source, false);
        }
        fallTime++;
        setDeltaMovement(getDeltaMovement().add(0, -0.04, 0));
        move(MoverType.SELF, getDeltaMovement());
        setDeltaMovement(getDeltaMovement().scale(0.98));
        if (onGround()) {
            BlockPos pos = blockPosition();
            BlockState target = level().getBlockState(pos);
            if (target.is(Blocks.MOVING_PISTON) || target.is(Blocks.PISTON_HEAD) || target.is(Blocks.PISTON)) return;
            boolean replaceable = target.canBeReplaced();
            if (runtime != null) {
                var fibres = runtime.hostBlock("Thaumcraft:blockTaintFibres", 0, BlockState.class).orElse(null);
                var goo = runtime.hostBlock("Thaumcraft:blockFluxGoo", 0, BlockState.class).orElse(null);
                replaceable |= fibres != null && target.is(fibres.getBlock()) || goo != null && target.is(goo.getBlock());
            }
            if (replaceable && !level().getBlockState(pos.below()).canBeReplaced() && falling.canSurvive(level(), pos)) level().setBlock(pos, falling, 3);
            discard();
        } else if (fallTime > 600 || fallTime > 100 && level().isOutsideBuildHeight(blockPosition())) discard();
    }
    @Override protected void addAdditionalSaveData(CompoundTag tag) {
        tag.put("Tc4BlockState", net.minecraft.nbt.NbtUtils.writeBlockState(blockState())); tag.putInt("Tc4FallTime", fallTime);
        if (source != null) tag.putLong("Tc4Source", source.asLong());
        if (pendingLegacyData != null) tag.putByteArray("Tc4LegacyFallingTaint", pendingLegacyData);
        else if (!level().isClientSide) TcModernApi.runtime().ifPresent(runtime -> {
            var hosts = runtime.entityHosts(); if (hosts.original(this).isPresent()) tag.putByteArray("Tc4LegacyFallingTaint", hosts.save(this));
        });
    }
    @Override protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.contains("Tc4BlockState", 10)) setBlockState(net.minecraft.nbt.NbtUtils.readBlockState(level().holderLookup(net.minecraft.core.registries.Registries.BLOCK), tag.getCompound("Tc4BlockState")));
        fallTime = tag.getInt("Tc4FallTime");
        source = tag.contains("Tc4Source") ? BlockPos.of(tag.getLong("Tc4Source")) : null;
        pendingLegacyData = tag.contains("Tc4LegacyFallingTaint", 7) ? tag.getByteArray("Tc4LegacyFallingTaint") : null;
    }
    @LegacyType("thaumcraft.common.entities.EntityFallingTaint") public interface OldFallingTaint {
        @LegacyField(name="block", descriptor="Lnet/minecraft/block/Block;") LegacyObject block();
        @LegacyField(name="metadata", descriptor="I") int metadata();
        @LegacyField(name="oldX", descriptor="I") int oldX();
        @LegacyField(name="oldY", descriptor="I") int oldY();
        @LegacyField(name="oldZ", descriptor="I") int oldZ();
        @LegacyField(name="fallTime", descriptor="I") int fallTime();
    }
}
