package github.com.gengyoubo.tcmodern.tc4.entity;

import github.com.gengyoubo.mfcl.api.LegacyRuntime;
import github.com.gengyoubo.mfcl.api.mla.LegacyMethod;
import github.com.gengyoubo.mfcl.api.mla.LegacyType;
import github.com.gengyoubo.tcmodern.tc4.entity.projectile.GolemOrb;
import github.com.gengyoubo.tcmodern.tc4.init.Tc4Entities;
import java.util.EnumSet;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.level.Level;

/**
 * A cult cleric in crimson robes: hurls fireballs and homing orbs, and ritualists stand chanting over their camp's
 * eldritch altar until something disturbs them.
 */
public final class CultistCleric extends Cultist implements RangedAttackMob {
    static final String LEGACY_CLASS = "thaumcraft.common.entities.monster.EntityCultistCleric";
    private static final EntityDataAccessor<Boolean> RITUALIST = SynchedEntityData.defineId(CultistCleric.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Optional<BlockPos>> ALTAR = SynchedEntityData.defineId(CultistCleric.class, EntityDataSerializers.OPTIONAL_BLOCK_POS);

    public CultistCleric(EntityType<? extends CultistCleric> type, Level level) { super(type, level, LEGACY_CLASS); }

    public static AttributeSupplier.Builder attributes() { return cultistAttributes().add(Attributes.MAX_HEALTH, 30); }

    public static void host(LegacyRuntime runtime) {
        Tc4Legacy.host(runtime, LEGACY_CLASS, (level, original) -> {
            var cleric = Tc4Entities.CULTIST_CLERIC.get().create(level);
            if (cleric == null) return null;
            cleric.adopt(runtime, original);
            cleric.setRitualist(runtime.accessor(Old.class, original).ritualist());
            return cleric;
        });
    }

    @Override protected void defineSynchedData() { super.defineSynchedData(); entityData.define(RITUALIST, false); entityData.define(ALTAR, Optional.empty()); }
    public boolean isRitualist() { return entityData.get(RITUALIST); }
    public void setRitualist(boolean ritualist) { entityData.set(RITUALIST, ritualist); }
    /** The altar the cleric chants over: its home centre, synchronized for the ritual line and head tracking. */
    public Optional<BlockPos> altar() { return entityData.get(ALTAR); }

    @Override public void restrictTo(BlockPos pos, int distance) { super.restrictTo(pos, distance); entityData.set(ALTAR, Optional.of(pos.immutable())); }

    @Override protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new AltarFocus());
        goalSelector.addGoal(2, new Tc4Goals.LongRangeAttack<>(this, 2.0, 1.0, 20, 40, 24.0F));
        goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.0, false));
        goalSelector.addGoal(5, new OpenDoorGoal(this, true));
        goalSelector.addGoal(6, new MoveTowardsRestrictionGoal(this, 0.8));
        goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new Tc4Goals.CultistHurtByTarget(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override protected void equip(DifficultyInstance difficulty) {
        wear(EquipmentSlot.HEAD, "Thaumcraft:ItemHelmetCultistRobe");
        wear(EquipmentSlot.CHEST, "Thaumcraft:ItemChestplateCultistRobe");
        wear(EquipmentSlot.LEGS, "Thaumcraft:ItemLeggingsCultistRobe");
        if (random.nextFloat() < (level().getDifficulty() == Difficulty.HARD ? 0.3F : 0.1F)) wear(EquipmentSlot.FEET, "Thaumcraft:ItemBootsCultist");
    }

    @Override public void performRangedAttack(LivingEntity target, float power) {
        double dx = target.getX() - getX(), dy = target.getBoundingBox().minY + target.getBbHeight() / 2.0 - (getY() + getBbHeight() / 2.0),
                dz = target.getZ() - getZ();
        swing(InteractionHand.MAIN_HAND);
        if (random.nextFloat() > 0.66F) {
            var orb = GolemOrb.create(level(), this, target, true);
            var motion = orb.getDeltaMovement();
            orb.setPos(orb.getX() + motion.x / 2.0, orb.getY(), orb.getZ() + motion.z / 2.0);
            orb.heading(dx, dy + 2.0, dz, 0.66F, 3.0F);
            playSound(Tc4Legacy.sound("egattack"), 1.0F, 1.0F + random.nextFloat() * 0.1F);
            level().addFreshEntity(orb);
        } else {
            float spread = Mth.sqrt(power) * 0.5F;
            level().levelEvent(null, 1018, blockPosition(), 0);
            for (int i = 0; i < 3; i++) {
                var fireball = new SmallFireball(level(), this, dx + random.nextGaussian() * spread, dy, dz + random.nextGaussian() * spread);
                fireball.setPos(fireball.getX(), getY() + getBbHeight() / 2.0 + 0.5, fireball.getZ());
                level().addFreshEntity(fireball);
            }
        }
    }

    @Override public boolean hurt(DamageSource source, float amount) {
        if (isInvulnerableTo(source)) return false;
        setRitualist(false);
        return super.hurt(source, amount);
    }

    @Override public boolean removeWhenFarAway(double distance) { return !isRitualist(); }

    @Override public void tick() {
        super.tick();
        if (!level().isClientSide || !isRitualist() || altar().isEmpty()) return;
        var altar = altar().get();
        double dx = altar.getX() + 0.5 - getX(), dy = altar.getY() + 1.5 - getEyeY(), dz = altar.getZ() + 0.5 - getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F, pitch = (float) -(Mth.atan2(dy, horizontal) * Mth.RAD_TO_DEG);
        setXRot(rotateTowards(getXRot(), pitch, 10.0F));
        yHeadRot = rotateTowards(yHeadRot, yaw, getMaxHeadYRot());
    }

    private static float rotateTowards(float from, float to, float step) { return from + Mth.clamp(Mth.wrapDegrees(to - from), -step, step); }

    @Override protected SoundEvent getAmbientSound() { return Tc4Legacy.sound("chant"); }
    @Override public int getAmbientSoundInterval() { return 500; }

    @Override public void addAdditionalSaveData(CompoundTag tag) { super.addAdditionalSaveData(tag); tag.putByte("Flags", (byte) (isRitualist() ? 1 : 0)); }
    @Override public void readAdditionalSaveData(CompoundTag tag) { super.readAdditionalSaveData(tag); setRitualist((tag.getByte("Flags") & 1) != 0); }

    /** AIAltarFocus: a ritualist stays put while its altar stands; straying over four blocks or losing it ends the ritual. */
    private final class AltarFocus extends Goal {
        AltarFocus() { setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP)); }
        @Override public boolean canUse() { return isRitualist() && hasRestriction(); }
        @Override public boolean canContinueToUse() { return canUse(); }
        @Override public void tick() {
            if (tickCount % 40 != 0) return;
            var home = getRestrictCenter();
            var altarBlock = Tc4Legacy.block("Thaumcraft:blockEldritch", 0);
            boolean altarGone = altarBlock != null && !level().getBlockState(home).is(altarBlock.getBlock());
            if (home.distToCenterSqr(getX(), getY(), getZ()) > 16.0 || altarGone) setRitualist(false);
        }
    }

    @LegacyType(LEGACY_CLASS) public interface Old { @LegacyMethod(name = "getIsRitualist", descriptor = "()Z") boolean ritualist(); }
}
