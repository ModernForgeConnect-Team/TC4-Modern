package github.com.gengyoubo.tcmodern.tc4.entity.item;

import github.com.gengyoubo.mfcl.api.LegacyObject;
import github.com.gengyoubo.mfcl.api.LegacyRuntime;
import github.com.gengyoubo.mfcl.api.mla.LegacyField;
import github.com.gengyoubo.mfcl.api.mla.LegacyType;
import github.com.gengyoubo.tcmodern.tc4.entity.Tc4Legacy;
import github.com.gengyoubo.tcmodern.tc4.init.Tc4Entities;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * An item flying to an entity or a point (the equal trade focus, infusion and crucible effects), passing through
 * blocks while it travels and trailing TC4 sparkles; it lands as an ordinary drop once there.
 */
public final class FollowingItem extends SpecialItem {
    static final String LEGACY_CLASS = "thaumcraft.common.entities.EntityFollowingItem";
    private static final EntityDataAccessor<Integer> TARGET = SynchedEntityData.defineId(FollowingItem.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> TX = SynchedEntityData.defineId(FollowingItem.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> TY = SynchedEntityData.defineId(FollowingItem.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> TZ = SynchedEntityData.defineId(FollowingItem.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> TYPE = SynchedEntityData.defineId(FollowingItem.class, EntityDataSerializers.INT);
    private int approach = 20;
    public double gravity = 0.04F;

    public FollowingItem(EntityType<? extends FollowingItem> type, Level level) { super(type, level, LEGACY_CLASS); }

    public static void host(LegacyRuntime runtime) {
        Tc4Legacy.host(runtime, LEGACY_CLASS, (level, original) -> {
            var item = copy(runtime, original, Tc4Entities.FOLLOWING_ITEM.get().create(level));
            if (item == null) return null;
            var old = runtime.accessor(Old.class, original);
            var target = Tc4Legacy.hostOf(runtime, level, old.target());
            item.entityData.set(TYPE, (int) old.type());
            item.gravity = old.gravity();
            if (target != null) item.follow(target);
            else item.goTo(new Vec3(old.targetX(), old.targetY(), old.targetZ()));
            return item;
        });
    }

    @Override protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(TARGET, -1); entityData.define(TX, 0.0F); entityData.define(TY, 0.0F); entityData.define(TZ, 0.0F); entityData.define(TYPE, 3);
    }

    public void follow(Entity target) {
        entityData.set(TARGET, target.getId());
        goTo(new Vec3(target.getX(), target.getBoundingBox().minY + target.getBbHeight() / 2.0, target.getZ()));
        noPhysics = true;
    }

    public void goTo(Vec3 target) { entityData.set(TX, (float) target.x); entityData.set(TY, (float) target.y); entityData.set(TZ, (float) target.z); }
    private boolean hasDestination() { return entityData.get(TX) != 0 || entityData.get(TY) != 0 || entityData.get(TZ) != 0; }

    @Override public void tick() {
        var target = level().getEntity(entityData.get(TARGET));
        if (target != null) goTo(new Vec3(target.getX(), target.getBoundingBox().minY + target.getBbHeight() / 2.0, target.getZ()));
        if (!hasDestination()) {
            setDeltaMovement(getDeltaMovement().add(0, -gravity, 0));
        } else {
            double xd = entityData.get(TX) - getX(), yd = entityData.get(TY) - getY(), zd = entityData.get(TZ) - getZ();
            if (approach > 1) approach--;
            double distance = Math.sqrt(xd * xd + yd * yd + zd * zd);
            if (distance > 0.5) {
                distance *= approach;
                setDeltaMovement(xd / distance, yd / distance, zd / distance);
            } else {
                setDeltaMovement(getDeltaMovement().scale(0.1));
                goTo(Vec3.ZERO);
                entityData.set(TARGET, -1);
                noPhysics = false;
            }
            if (level().isClientSide) {
                var particle = entityData.get(TYPE) == 10 ? ParticleTypes.BUBBLE_POP : ParticleTypes.END_ROD;
                level().addParticle(particle, xo + (random.nextFloat() - random.nextFloat()) * 0.125, yo + 0.125 + (random.nextFloat() - random.nextFloat()) * 0.125,
                        zo + (random.nextFloat() - random.nextFloat()) * 0.125, 0, 0, 0);
            }
        }
        // EntitySpecialItem's upward drift is part of the original; the travel above overrides it while moving.
        boolean travelling = noPhysics;
        super.tick();
        noPhysics = travelling;
    }

    @Override public void addAdditionalSaveData(CompoundTag tag) { super.addAdditionalSaveData(tag); tag.putShort("type", (short) (int) entityData.get(TYPE)); }
    @Override public void readAdditionalSaveData(CompoundTag tag) { super.readAdditionalSaveData(tag); entityData.set(TYPE, (int) tag.getShort("type")); }

    @LegacyType(LEGACY_CLASS) public interface Old {
        @LegacyField(name = "target", descriptor = "Lnet/minecraft/entity/Entity;") LegacyObject target();
        @LegacyField(name = "targetX", descriptor = "D") double targetX();
        @LegacyField(name = "targetY", descriptor = "D") double targetY();
        @LegacyField(name = "targetZ", descriptor = "D") double targetZ();
        @LegacyField(name = "type", descriptor = "I") int type();
        @LegacyField(name = "gravity", descriptor = "D") double gravity();
    }
}
