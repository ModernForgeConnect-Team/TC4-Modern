package github.com.gengyoubo.tcmodern.tc4.entity;

import github.com.gengyoubo.mfcl.api.LegacyRuntime;
import github.com.gengyoubo.mfcl.api.mla.LegacyField;
import github.com.gengyoubo.mfcl.api.mla.LegacyMethod;
import github.com.gengyoubo.mfcl.api.mla.LegacyType;
import github.com.gengyoubo.tcmodern.tc4.init.Tc4Entities;
import github.com.gengyoubo.tcmodern.tc4.world.Tc4ModernWorldgen;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Flying cloud of taint particles; attacks nearby players and applies weakness. */
public final class TaintSwarm extends TaintCow {
    private static final String LEGACY_CLASS = "thaumcraft.common.entities.monster.EntityTaintSwarm";
    private static final SoundEvent SWARM_ATTACK = SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("thaumcraft", "swarmattack"));
    private int attackCooldown;
    private int damageBonus;
    private boolean summoned;
    private Vec3 flightTarget;
    public TaintSwarm(EntityType<? extends TaintSwarm> type, Level level) { super(type, level); setNoGravity(true); }
    @Override protected String legacyClass() { return LEGACY_CLASS; }
    @Override protected SoundEvent getAmbientSound() { return null; }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return SWARM_ATTACK; }
    @Override protected SoundEvent getDeathSound() { return SWARM_ATTACK; }
    @Override protected float getSoundVolume() { return 0.1F; }
    @Override protected void playStepSound(net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state) { }
    public static AttributeSupplier.Builder swarmAttributes() {
        return TaintCow.attributes().add(Attributes.MAX_HEALTH, 30).add(Attributes.ATTACK_DAMAGE, 2).add(Attributes.MOVEMENT_SPEED, 0.25);
    }
    public static void registerLegacyHost(LegacyRuntime runtime) {
        runtime.entityHosts().register(LEGACY_CLASS, (level, original) -> {
            var host = Tc4Entities.TAINT_SWARM.get().create((ServerLevel) level);
            if (host != null) {
                var old = runtime.accessor(OldSwarm.class, original);
                host.summoned = old.summoned(); host.damageBonus = old.damageBonus();
                host.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(2 + host.damageBonus);
            }
            return host;
        });
    }
    @Override protected void registerGoals() { }
    @Override public void tick() {
        super.tick();
        if (level().isClientSide) {
            for (int i = 0; i < 3; i++) level().addParticle(ParticleTypes.WITCH,
                    getX() + (random.nextDouble() - 0.5) * 2, getY() + random.nextDouble() * 2,
                    getZ() + (random.nextDouble() - 0.5) * 2, 0, 0, 0);
            return;
        }
        if (!isAlive()) return;
        if (attackCooldown > 0) attackCooldown--;
        Player target = summoned ? null : level().getNearestPlayer(this, 12);
        if (target != null && !target.isSpectator() && !target.isCreative()) {
            flightTarget = target.position().add(0, target.getBbHeight() * 0.5, 0);
            if (distanceToSqr(target) < 9 && getBoundingBox().maxY > target.getBoundingBox().minY && getBoundingBox().minY < target.getBoundingBox().maxY && attackCooldown == 0) {
                attackCooldown = 10 + random.nextInt(5);
                Vec3 motion = target.getDeltaMovement();
                if (doHurtTarget(target)) target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100));
                target.setDeltaMovement(motion);
                playSound(SWARM_ATTACK, 0.3F, 0.9F + random.nextFloat() * 0.2F);
            }
        } else {
            if (summoned) { hurt(damageSources().generic(), 5); return; }
            if (flightTarget == null || distanceToSqr(flightTarget) < 4 || random.nextInt(30) == 0) {
                var candidate = blockPosition().offset(random.nextInt(7) - random.nextInt(7), random.nextInt(6) - 2, random.nextInt(7) - random.nextInt(7));
                if (level().isEmptyBlock(candidate) && level().getBiome(candidate).is(Tc4ModernWorldgen.TAINTED_LAND)) flightTarget = Vec3.atCenterOf(candidate);
            }
        }
        if (flightTarget != null) {
            Vec3 difference = flightTarget.subtract(position());
            Vec3 desired = new Vec3(Math.signum(difference.x) * 0.5, Math.signum(difference.y) * 0.7, Math.signum(difference.z) * 0.5);
            setDeltaMovement(getDeltaMovement().add(desired.subtract(getDeltaMovement()).scale(0.1)));
        }
    }
    @Override public boolean causeFallDamage(float distance, float multiplier, DamageSource source) { return false; }
    @Override public void addAdditionalSaveData(CompoundTag tag) { super.addAdditionalSaveData(tag); tag.putBoolean("Tc4Summoned", summoned); tag.putInt("Tc4DamageBonus", damageBonus); }
    @Override public void readAdditionalSaveData(CompoundTag tag) { super.readAdditionalSaveData(tag); summoned = tag.getBoolean("Tc4Summoned"); damageBonus = tag.getInt("Tc4DamageBonus"); getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(2 + damageBonus); }
    @LegacyType("thaumcraft.common.entities.monster.EntityTaintSwarm") public interface OldSwarm {
        @LegacyMethod(name="getIsSummoned", descriptor="()Z") boolean summoned();
        @LegacyField(name="damBonus", descriptor="I") int damageBonus();
    }
}
