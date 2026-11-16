package github.com.gengyoubo.tcmodern.tc4.entity.projectile;

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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.Nullable;

/** The homing energy orb of eldritch golems and cultist clerics; red orbs are the stronger cultist kind. */
public final class GolemOrb extends Tc4Throwable {
    static final String LEGACY_CLASS = "thaumcraft.common.entities.projectile.EntityGolemOrb";
    private static final EntityDataAccessor<Integer> TARGET = SynchedEntityData.defineId(GolemOrb.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> RED = SynchedEntityData.defineId(GolemOrb.class, EntityDataSerializers.BOOLEAN);

    public GolemOrb(EntityType<? extends GolemOrb> type, Level level) { super(type, level, LEGACY_CLASS); }

    public static GolemOrb create(Level level, LivingEntity thrower, @Nullable LivingEntity target, boolean red) {
        var orb = new GolemOrb(Tc4Entities.GOLEM_ORB.get(), level);
        orb.throwFrom(thrower, 1.5F, 0.0F, 1.0F);
        orb.entityData.set(TARGET, target == null ? -1 : target.getId());
        orb.entityData.set(RED, red);
        return orb;
    }

    public static void host(LegacyRuntime runtime) {
        Tc4Legacy.host(runtime, LEGACY_CLASS, (level, original) -> {
            var orb = Tc4Entities.GOLEM_ORB.get().create(level);
            if (orb == null) return null;
            var old = runtime.accessor(Old.class, original);
            var target = Tc4Legacy.hostOf(runtime, level, old.target());
            orb.entityData.set(TARGET, target == null ? -1 : target.getId());
            orb.entityData.set(RED, old.red());
            orb.setOwner(Tc4Legacy.thrower(runtime, level, original));
            return orb;
        });
    }

    @Override protected void defineSynchedData() { entityData.define(TARGET, -1); entityData.define(RED, false); }
    public boolean red() { return entityData.get(RED); }
    @Override protected float getGravity() { return 0.0F; }
    @Override protected int lifetime() { return red() ? 240 : 160; }
    @Override public boolean isPickable() { return true; }
    @Override public boolean hurt(DamageSource source, float amount) { return deflect(source, Tc4Legacy.sound("zap")); }

    @Override public void tick() {
        super.tick();
        if (!(level().getEntity(entityData.get(TARGET)) instanceof LivingEntity target) || !target.isAlive()) return;
        double distance = distanceToSqr(target);
        if (distance <= 0) return;
        double dx = (target.getX() - getX()) / distance, dy = (target.getY() + target.getBbHeight() * 0.6 - getY()) / distance,
                dz = (target.getZ() - getZ()) / distance;
        var motion = getDeltaMovement().add(dx * 0.2, dy * 0.2, dz * 0.2);
        setDeltaMovement(Mth.clamp(motion.x, -0.25, 0.25), Mth.clamp(motion.y, -0.25, 0.25), Mth.clamp(motion.z, -0.25, 0.25));
    }

    @Override protected void onHit(HitResult hit) {
        if (!level().isClientSide && getOwner() instanceof LivingEntity owner && hit instanceof EntityHitResult entityHit) {
            var attack = owner.getAttribute(Attributes.ATTACK_DAMAGE);
            float damage = (float) (attack == null ? 2.0 : attack.getValue()) * (red() ? 1.0F : 0.6F);
            entityHit.getEntity().hurt(damageSources().indirectMagic(this, owner), damage);
        }
        if (level() instanceof ServerLevel server) {
            server.playSound(null, getX(), getY(), getZ(), Tc4Legacy.sound("shock"), getSoundSource(), 1.0F, 1.0F + (random.nextFloat() - random.nextFloat()) * 0.2F);
            server.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY(), getZ(), 12, 0.2, 0.2, 0.2, 0.2);
        }
        discard();
    }

    @Override public void addAdditionalSaveData(CompoundTag tag) { super.addAdditionalSaveData(tag); tag.putBoolean("red", red()); }
    @Override public void readAdditionalSaveData(CompoundTag tag) { super.readAdditionalSaveData(tag); entityData.set(RED, tag.getBoolean("red")); }

    @LegacyType(LEGACY_CLASS) public interface Old {
        @LegacyField(name = "target", descriptor = "Lnet/minecraft/entity/EntityLivingBase;") LegacyObject target();
        @LegacyField(name = "red", descriptor = "Z") boolean red();
    }
}
