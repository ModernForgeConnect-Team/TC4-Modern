package github.com.gengyoubo.tcmodern.tc4.entity.golem;

import github.com.gengyoubo.tcmodern.tc4.init.Tc4Entities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * EntityGolemBobber for modern fishing golems: cast in an arc onto the water, it bobs on the surface, dips when a
 * fish bites and disappears once its golem stops fishing. It is never saved; the golem casts again after loading.
 */
public final class GolemBobber extends Entity {
    private static final EntityDataAccessor<Integer> FISHER = SynchedEntityData.defineId(GolemBobber.class, EntityDataSerializers.INT);
    private int biting;

    public GolemBobber(EntityType<? extends GolemBobber> type, Level level) { super(type, level); noCulling = true; }

    /** Casts from the golem toward the water block, as the original's constructor aimed it. */
    public static GolemBobber cast(ModernGolem golem, BlockPos water) {
        var bobber = new GolemBobber(Tc4Entities.GOLEM_BOBBER.get(), golem.level());
        bobber.entityData.set(FISHER, golem.getId());
        double dx = water.getX() + 0.5 - golem.getX(), dy = water.getY() + 1 - golem.getY(), dz = water.getZ() + 0.5 - golem.getZ();
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        bobber.setPos(golem.getX(), golem.getY() + golem.getBbHeight() * 0.8, golem.getZ());
        bobber.setDeltaMovement(dx * 0.1, dy * 0.1 + Math.sqrt(distance) * 0.08, dz * 0.1);
        return bobber;
    }

    @Override protected void defineSynchedData() { entityData.define(FISHER, -1); }
    @Nullable public ModernGolem fisher() { return level().getEntity(entityData.get(FISHER)) instanceof ModernGolem golem ? golem : null; }

    /** A fish took the bait: the bobber is pulled under for a moment. */
    public void bite() { biting = 10; if (level() instanceof ServerLevel server) server.sendParticles(ParticleTypes.SPLASH, getX(), getY() + 0.2, getZ(), 6, 0.1, 0.0, 0.1, 0.1); }

    @Override public void tick() {
        super.tick();
        var fisher = fisher();
        if (!level().isClientSide) {
            if (fisher == null || !fisher.isAlive() || fisher.fishingPosition().isEmpty() || tickCount > 4000) { discard(); return; }
            if (random.nextFloat() < 0.02F && level() instanceof ServerLevel server)
                server.sendParticles(ParticleTypes.SPLASH, getX() + random.nextFloat() - random.nextFloat(), getY() + random.nextFloat(),
                        getZ() + random.nextFloat() - random.nextFloat(), 2 + random.nextInt(2), 0.1, 0.0, 0.1, 0.0);
        }
        move(MoverType.SELF, getDeltaMovement());
        var box = getBoundingBox();
        double submerged = 0;
        for (int j = 0; j < 5; j++) {
            double from = box.minY + (box.maxY - box.minY) * j / 5.0, to = box.minY + (box.maxY - box.minY) * (j + 1) / 5.0;
            var slice = new AABB(box.minX, from, box.minZ, box.maxX, to, box.maxZ);
            if (BlockPos.betweenClosedStream(slice).anyMatch(pos -> level().getFluidState(pos).is(FluidTags.WATER))) submerged += 0.2;
        }
        if (!level().isClientSide && submerged == 0 && onGround()) { discard(); return; }
        float drag = onGround() || horizontalCollision ? 0.5F : 0.92F;
        double my = getDeltaMovement().y + 0.04 * (submerged * 2.0 - 1.0);
        if (biting > 0) { biting--; my -= 0.08; }
        if (submerged > 0) { drag *= 0.9F; my *= 0.8; }
        setDeltaMovement(getDeltaMovement().x * drag, my * drag, getDeltaMovement().z * drag);
    }

    @Override public boolean shouldBeSaved() { return false; }
    @Override public boolean isPickable() { return false; }
    @Override protected void readAdditionalSaveData(CompoundTag tag) { }
    @Override protected void addAdditionalSaveData(CompoundTag tag) { }
    @Override public boolean shouldRenderAtSqrDistance(double distance) { double d = 64.0 * getViewScale(); return distance < d * d; }
}
