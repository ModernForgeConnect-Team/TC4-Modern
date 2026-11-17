package github.com.gengyoubo.tcmodern.tc4.entity;

import github.com.gengyoubo.mfcl.api.LegacyRuntime;
import github.com.gengyoubo.tcmodern.tc4.entity.projectile.EldritchOrb;
import github.com.gengyoubo.tcmodern.tc4.init.Tc4Entities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraftforge.event.ForgeEventFactory;
import org.jetbrains.annotations.Nullable;

/**
 * The eldritch warden, a guardian grown into a boss: a large regenerating shield, and when it breaks the warden
 * returns to its seal and unleashes rings of arcing void around itself while invulnerable.
 */
public final class EldritchWarden extends Tc4Boss implements RangedAttackMob, EldritchMob {
    static final String LEGACY_CLASS = "thaumcraft.common.entities.monster.boss.EntityEldritchWarden";
    private static final String[] TITLES = { "Aphoom-Zhah", "Basatan", "Chaugnar Faugn", "Mnomquah", "Nyogtha", "Oorn", "Shaikorth", "Rhan-Tegoth",
            "Rhogog", "Shudde M'ell", "Vulthoom", "Yag-Kosha", "Yibb-Tstll", "Zathog", "Zushakon" };
    private static final EntityDataAccessor<Integer> TITLE = SynchedEntityData.defineId(EldritchWarden.class, EntityDataSerializers.INT);
    private boolean fieldFrenzy, lastBlast;
    private int fieldFrenzyCounter;
    public float armLiftL, armLiftR;

    public EldritchWarden(EntityType<? extends EldritchWarden> type, Level level) { super(type, level, LEGACY_CLASS); }

    public static AttributeSupplier.Builder attributes() {
        return bossAttributes().add(Attributes.MAX_HEALTH, 200).add(Attributes.MOVEMENT_SPEED, 0.33).add(Attributes.ATTACK_DAMAGE, 10);
    }

    public static void host(LegacyRuntime runtime) {
        Tc4Legacy.host(runtime, LEGACY_CLASS, (level, original) -> {
            var warden = Tc4Entities.ELDRITCH_WARDEN.get().create(level);
            if (warden == null) return null;
            Tc4Legacy.copyHome(runtime, original, warden);
            warden.entityData.set(TITLE, Math.max(0, Math.min(TITLES.length - 1, Tc4Legacy.watcherByte(runtime, original, 16))));
            warden.rise();
            return warden;
        });
    }

    @Override protected void defineSynchedData() { super.defineSynchedData(); entityData.define(TITLE, 0); }
    @Override protected Component getTypeName() {
        return Component.translatable("entity.tc4modern.eldritch_warden.titled", TITLES[Math.max(0, Math.min(TITLES.length - 1, entityData.get(TITLE)))]);
    }

    @Override protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new Tc4Goals.LongRangeAttack<>(this, 3.0, 1.0, 20, 40, 24.0F));
        goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.1, false));
        goalSelector.addGoal(5, new MoveTowardsRestrictionGoal(this, 0.8));
        goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 1.0));
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Cultist.class, true));
    }

    private void rise() {
        spawnTimer = 150;
        setAbsorptionAmount((float) (getAbsorptionAmount() + getAttributeBaseValue(Attributes.MAX_HEALTH) * 0.66));
    }

    @Override public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason,
            @Nullable SpawnGroupData group, @Nullable CompoundTag tag) {
        rise();
        entityData.set(TITLE, random.nextInt(TITLES.length));
        return super.finalizeSpawn(level, difficulty, reason, group, tag);
    }

    @Override public int getArmorValue() { return super.getArmorValue() + 4; }
    @Override public float getStandingEyeHeight(Pose pose, EntityDimensions dimensions) { return 3.1F; }
    @Override protected boolean isImmobile() { return super.isImmobile() || fieldFrenzyCounter > 0; }

    @Override protected void customServerAiStep() {
        super.customServerAiStep();
        if (invulnerableTime <= 0 && tickCount % 25 == 0) {
            int shield = (int) (getAttributeBaseValue(Attributes.MAX_HEALTH) * 0.66);
            if (getAbsorptionAmount() < shield) setAbsorptionAmount(getAbsorptionAmount() + 1.0F);
        }
    }

    @Override public void tick() {
        if (spawnTimer == 150 && !level().isClientSide) level().broadcastEntityEvent(this, (byte) 18);
        super.tick();
        if (level().isClientSide) {
            if (armLiftL > 0) armLiftL -= 0.05F;
            if (armLiftR > 0) armLiftR -= 0.05F;
            net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> github.com.gengyoubo.tcmodern.tc4.client.entity.EldritchAuraParticle.spawn(this));
        }
    }

    @Override public void aiStep() {
        super.aiStep();
        if (level().isClientSide) return;
        var field = Tc4Legacy.block("Thaumcraft:blockAiry", 11);
        if (field != null) for (int l = 0; l < 4; l++) {
            var pos = BlockPos.containing(getX() + (l % 2 * 2 - 1) * 0.25F, getY(), getZ() + (l / 2 % 2 * 2 - 1) * 0.25F);
            if (level().isEmptyBlock(pos)) level().setBlock(pos, field, 3);
        }
        if (fieldFrenzyCounter > 0) {
            if (fieldFrenzyCounter == 150) teleportHome();
            performFieldFrenzy(field);
        }
    }

    private void performFieldFrenzy(@Nullable net.minecraft.world.level.block.state.BlockState field) {
        if (fieldFrenzyCounter < 121 && fieldFrenzyCounter % 10 == 0 && level() instanceof ServerLevel server) {
            level().broadcastEntityEvent(this, (byte) 17);
            double radius = (150 - fieldFrenzyCounter) / 8.0;
            int d = 1 + fieldFrenzyCounter / 8;
            var centre = blockPosition();
            for (int q = 0; q < 180 / d; q++) {
                double radians = Math.toRadians(q * 2 * d);
                var pos = centre.offset((int) (radius * Math.cos(radians)), 0, (int) (radius * Math.sin(radians)));
                if (!level().isEmptyBlock(pos) || !level().getBlockState(pos.below()).isRedstoneConductor(level(), pos.below())) continue;
                if (field != null) {
                    level().setBlock(pos, field, 3);
                    level().scheduleTick(pos, field.getBlock(), 250 + random.nextInt(150));
                }
                server.sendParticles(random.nextFloat() < 0.3F ? ParticleTypes.ELECTRIC_SPARK : ParticleTypes.WITCH, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 4, 0.3, 0.3, 0.3, 0.05);
            }
            level().playSound(null, getX(), getY(), getZ(), Tc4Legacy.sound("zap"), getSoundSource(), 1.0F, 0.9F + random.nextFloat() * 0.1F);
        }
        fieldFrenzyCounter--;
    }

    private void teleportHome() {
        if (!hasRestriction()) return;
        var home = getRestrictCenter();
        var event = ForgeEventFactory.onEnderTeleport(this, home.getX(), home.getY(), home.getZ());
        if (event.isCanceled()) return;
        double ox = getX(), oy = getY(), oz = getZ();
        int x = Mth.floor(event.getTargetX()), y = Mth.floor(event.getTargetY()), z = Mth.floor(event.getTargetZ());
        for (int tries = 20; tries > 0; tries--) {
            var pos = new BlockPos(x, y, z);
            if (level().hasChunkAt(pos) && level().getBlockState(pos.below()).blocksMotion() && !level().getBlockState(pos).blocksMotion()) {
                setPos(x + 0.5, y + 0.1, z + 0.5);
                if (level().noCollision(this)) {
                    if (level() instanceof ServerLevel server) server.sendParticles(ParticleTypes.PORTAL, getX(), getY() + getBbHeight() / 2, getZ(), 128, getBbWidth(), getBbHeight() / 2, getBbWidth(), 0.2);
                    level().playSound(null, ox, oy, oz, SoundEvents.ENDERMAN_TELEPORT, getSoundSource(), 1.0F, 1.0F);
                    playSound(SoundEvents.ENDERMAN_TELEPORT, 1.0F, 1.0F);
                    return;
                }
                setPos(ox, oy, oz);
                return;
            }
            x = Mth.floor(event.getTargetX()) + random.nextInt(8) - random.nextInt(8);
            z = Mth.floor(event.getTargetZ()) + random.nextInt(8) - random.nextInt(8);
        }
    }

    @Override public boolean isInvulnerable() { return fieldFrenzyCounter > 0 || super.isInvulnerable(); }

    @Override public boolean hurt(DamageSource source, float amount) {
        if (fieldFrenzyCounter > 0 || isInvulnerableTo(source) || source.is(DamageTypes.DROWN) || source.is(DamageTypes.WITHER)) return false;
        boolean hurt = super.hurt(source, amount);
        if (!level().isClientSide && hurt && !fieldFrenzy && getAbsorptionAmount() <= 0.0F) { fieldFrenzy = true; fieldFrenzyCounter = 150; }
        return hurt;
    }

    @Override public void performRangedAttack(LivingEntity target, float power) {
        if (random.nextFloat() > 0.2F) {
            var orb = EldritchOrb.create(level(), this);
            lastBlast = !lastBlast;
            level().broadcastEntityEvent(this, (byte) (lastBlast ? 16 : 15));
            float angle = (getYRot() + (lastBlast ? 90 : 180)) % 360.0F * Mth.DEG_TO_RAD;
            orb.setPos(orb.getX() - Mth.cos(angle) * 0.5, orb.getY() - 0.13, orb.getZ() - Mth.sin(angle) * 0.5);
            var motion = target.getDeltaMovement();
            orb.heading(target.getX() + motion.x - getX(), target.getY() - getY() - target.getBbHeight() / 2.0F, target.getZ() + motion.z - getZ(), 1.0F, 2.0F);
            playSound(Tc4Legacy.sound("egattack"), 2.0F, 1.0F + random.nextFloat() * 0.1F);
            level().addFreshEntity(orb);
        } else if (hasLineOfSight(target)) EldritchGuardian.screech(this, target, 400, 3, true);
    }

    @Override public void handleEntityEvent(byte event) {
        switch (event) {
            case 15 -> armLiftL = 0.5F;
            case 16 -> armLiftR = 0.5F;
            case 17 -> { armLiftL = 0.9F; armLiftR = 0.9F; }
            case 18 -> spawnTimer = 150;
            default -> super.handleEntityEvent(event);
        }
    }

    @Override public boolean canAttack(LivingEntity target) { return !(target instanceof EldritchGuardian) && super.canAttack(target); }
    @Override protected SoundEvent getAmbientSound() { return Tc4Legacy.sound("egidle"); }
    @Override protected SoundEvent getDeathSound() { return Tc4Legacy.sound("egdeath"); }
    @Override public int getAmbientSoundInterval() { return 500; }

    @Override public void addAdditionalSaveData(CompoundTag tag) { super.addAdditionalSaveData(tag); tag.putByte("title", (byte) (int) entityData.get(TITLE)); }
    @Override public void readAdditionalSaveData(CompoundTag tag) { super.readAdditionalSaveData(tag); entityData.set(TITLE, Math.max(0, Math.min(TITLES.length - 1, (int) tag.getByte("title")))); }
}
