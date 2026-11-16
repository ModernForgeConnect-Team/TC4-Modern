package github.com.gengyoubo.tcmodern.tc4.entity;

import github.com.gengyoubo.mfcl.api.LegacyRuntime;
import github.com.gengyoubo.mfcl.api.mla.LegacyMethod;
import github.com.gengyoubo.mfcl.api.mla.LegacyType;
import github.com.gengyoubo.tcmodern.tc4.init.Tc4Entities;
import github.com.gengyoubo.tcmodern.tc4.world.Tc4ModernWorldgen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.FlyingMob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * A wisp of one aspect: it drifts between random waypoints, rarely notices players, and zaps whatever hurt it.
 * It leaves wisp essence of its aspect behind.
 */
public final class Wisp extends FlyingMob implements Enemy {
    static final String LEGACY_CLASS = "thaumcraft.common.entities.monster.EntityWisp";
    private static final EntityDataAccessor<String> TYPE = SynchedEntityData.defineId(Wisp.class, EntityDataSerializers.STRING);
    private static final String[] EERIE = { "tenebrae", "exanimis", "perditio", "alienis", "venenum", "mortuus" };
    private final LegacyHost legacy = new LegacyHost(LEGACY_CLASS);
    private int courseChangeCooldown, aggroCooldown, attackCounter;
    private double waypointX, waypointY, waypointZ;
    @Nullable private LivingEntity targeted;

    public Wisp(EntityType<? extends Wisp> type, Level level) { super(type, level); xpReward = 5; }

    public static AttributeSupplier.Builder attributes() {
        return FlyingMob.createMobAttributes().add(Attributes.MAX_HEALTH, 22).add(Attributes.ATTACK_DAMAGE, 3).add(Attributes.FOLLOW_RANGE, 16);
    }

    public static void host(LegacyRuntime runtime) {
        Tc4Legacy.host(runtime, LEGACY_CLASS, (level, original) -> {
            var wisp = Tc4Entities.WISP.get().create(level);
            if (wisp != null) wisp.setAspect(runtime.accessor(Old.class, original).type());
            return wisp;
        });
    }

    @Override protected void defineSynchedData() { super.defineSynchedData(); entityData.define(TYPE, ""); }
    public String aspect() { return entityData.get(TYPE); }
    public void setAspect(String aspect) { entityData.set(TYPE, aspect == null ? "" : aspect); }

    /** The aspect colour, white while the aspect is unknown on this side. */
    public int color() { var aspect = Tc4Legacy.aspect(aspect()); return aspect == null ? 0xFFFFFF : aspect.color(); }

    @Override public boolean hurt(DamageSource source, float amount) {
        if (source.getDirectEntity() instanceof LivingEntity direct) { targeted = direct; aggroCooldown = 200; }
        if (source.getEntity() instanceof LivingEntity attacker) { targeted = attacker; aggroCooldown = 200; }
        return super.hurt(source, amount);
    }

    @Override public void tick() {
        super.tick();
        if (level().isClientSide && (tickCount <= 1 || isDeadOrDying() && deathTime == 1)) burst();
        if (level().isClientSide && random.nextBoolean() && Tc4Legacy.aspect(aspect()) != null) {
            int c = color();
            level().addParticle(new DustParticleOptions(new Vector3f((c >> 16 & 255) / 255.0F, (c >> 8 & 255) / 255.0F, (c & 255) / 255.0F), 1.0F),
                    getX() + (random.nextFloat() - random.nextFloat()) * 0.7, getY() + 0.45 + (random.nextFloat() - random.nextFloat()) * 0.7,
                    getZ() + (random.nextFloat() - random.nextFloat()) * 0.7, 0, 0, 0);
        }
        legacy.tick(this);
    }

    private void burst() {
        for (int i = 0; i < 12; i++) level().addParticle(ParticleTypes.END_ROD, getX(), getY() + 0.45, getZ(),
                (random.nextFloat() - 0.5) * 0.2, (random.nextFloat() - 0.5) * 0.2, (random.nextFloat() - 0.5) * 0.2);
    }

    private void chooseAspect() {
        if (level().getBiome(blockPosition()).is(Tc4ModernWorldgen.EERIE)) { setAspect(EERIE[random.nextInt(EERIE.length)]); return; }
        var aspects = Tc4Legacy.aspects();
        boolean primal = random.nextInt(10) != 0;
        var choices = aspects.stream().filter(a -> a.primal() == primal).toList();
        if (!choices.isEmpty()) setAspect(choices.get(random.nextInt(choices.size())).tag());
    }

    @Override protected void customServerAiStep() {
        if (Tc4Legacy.aspect(aspect()) == null) chooseAspect();
        if (level().getDifficulty() == Difficulty.PEACEFUL) { discard(); return; }
        double dx = waypointX - getX(), dy = waypointY - getY(), dz = waypointZ - getZ();
        double d3 = dx * dx + dy * dy + dz * dz;
        if (d3 < 1.0 || d3 > 3600.0) {
            waypointX = getX() + (random.nextFloat() * 2 - 1) * 16;
            waypointY = getY() + (random.nextFloat() * 2 - 1) * 16;
            waypointZ = getZ() + (random.nextFloat() * 2 - 1) * 16;
        }
        if (courseChangeCooldown-- <= 0) {
            courseChangeCooldown += random.nextInt(5) + 2;
            double length = Math.sqrt(d3);
            if (length > 0 && traversable(length)) setDeltaMovement(getDeltaMovement().add(dx / length * 0.1, dy / length * 0.1, dz / length * 0.1));
            else { waypointX = getX(); waypointY = getY(); waypointZ = getZ(); }
        }
        if (targeted != null && !targeted.isAlive()) targeted = null;
        aggroCooldown--;
        if (random.nextInt(1000) == 0 && (targeted == null || aggroCooldown-- <= 0)) {
            Player player = level().getNearestPlayer(this, 16.0);
            targeted = player != null && !player.isCreative() && !player.isSpectator() ? player : null;
            if (targeted != null) aggroCooldown = 50;
        }
        if (targeted != null && targeted.distanceToSqr(this) < 256.0) {
            double tx = targeted.getX() - getX(), tz = targeted.getZ() - getZ();
            setYRot(-(float) Mth.atan2(tx, tz) * Mth.RAD_TO_DEG);
            yBodyRot = getYRot();
            if (hasLineOfSight(targeted)) {
                if (++attackCounter == 20) zap(targeted);
            } else if (attackCounter > 0) attackCounter--;
        } else {
            var motion = getDeltaMovement();
            setYRot(-(float) Mth.atan2(motion.x, motion.z) * Mth.RAD_TO_DEG);
            yBodyRot = getYRot();
            if (attackCounter > 0) attackCounter--;
        }
    }

    private void zap(LivingEntity target) {
        playSound(Tc4Legacy.sound("zap"), 1.0F, 1.1F);
        if (level() instanceof ServerLevel server) {
            Vec3 from = position().add(0, getBbHeight() / 2, 0), to = target.position().add(0, target.getBbHeight() / 2, 0);
            for (int i = 0; i <= 10; i++) {
                var at = from.lerp(to, i / 10.0);
                server.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 1, 0.05, 0.05, 0.05, 0);
            }
        }
        float damage = (float) getAttributeValue(Attributes.ATTACK_DAMAGE);
        var motion = target.getDeltaMovement();
        boolean still = Math.abs(motion.x) <= 0.1F && Math.abs(motion.y) <= 0.1F && Math.abs(motion.z) <= 0.1F;
        if (still) { if (random.nextFloat() < 0.66F) target.hurt(damageSources().mobAttack(this), damage + 1.0F); }
        else if (random.nextFloat() < 0.4F) target.hurt(damageSources().mobAttack(this), damage);
        attackCounter = -20 + random.nextInt(20);
    }

    private boolean traversable(double length) {
        double sx = (waypointX - getX()) / length, sy = (waypointY - getY()) / length, sz = (waypointZ - getZ()) / length;
        var box = getBoundingBox();
        for (int i = 1; i < length; i++) {
            box = box.move(sx, sy, sz);
            if (!level().noCollision(this, box)) return false;
        }
        var target = BlockPos.containing(waypointX, waypointY, waypointZ);
        if (!level().getFluidState(target).isEmpty()) return false;
        for (int a = 0; a < 11; a++) if (!level().isEmptyBlock(target.below(a))) return true;
        return false;
    }

    @Override protected void dropCustomDeathLoot(DamageSource source, int looting, boolean recentlyHit) {
        super.dropCustomDeathLoot(source, looting, recentlyHit);
        if (Tc4Legacy.aspect(aspect()) != null) Tc4Legacy.drop(this, Tc4Legacy.withAspect("Thaumcraft:ItemWispEssence", aspect(), 2), 0.0F);
    }

    @Override protected SoundEvent getAmbientSound() { return Tc4Legacy.sound("wisplive"); }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.FIRE_EXTINGUISH; }
    @Override protected SoundEvent getDeathSound() { return Tc4Legacy.sound("wispdead"); }
    @Override protected float getSoundVolume() { return 0.25F; }
    @Override public int getMaxSpawnClusterSize() { return 2; }
    @Override protected MovementEmission getMovementEmission() { return MovementEmission.NONE; }
    @Override public boolean isAlliedTo(Entity other) { return other instanceof Wisp || super.isAlliedTo(other); }

    @Override public void addAdditionalSaveData(CompoundTag tag) { super.addAdditionalSaveData(tag); tag.putString("Type", aspect()); legacy.save(this, tag); }
    @Override public void readAdditionalSaveData(CompoundTag tag) { super.readAdditionalSaveData(tag); setAspect(tag.getString("Type")); legacy.load(tag); }

    @LegacyType(LEGACY_CLASS) public interface Old { @LegacyMethod(name = "getType", descriptor = "()Ljava/lang/String;") String type(); }
}
