package github.com.gengyoubo.tcmodern.tc4.entity;

import github.com.gengyoubo.mfcl.api.LegacyRuntime;
import github.com.gengyoubo.tcmodern.tc4.entity.projectile.GolemOrb;
import github.com.gengyoubo.tcmodern.tc4.init.Tc4Entities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import org.jetbrains.annotations.Nullable;

/** The cult's leader, summoned by the crimson portal: a crimson blade, homing orbs, and regeneration for nearby cultists. */
public final class CultistLeader extends Tc4Boss implements RangedAttackMob {
    static final String LEGACY_CLASS = "thaumcraft.common.entities.monster.boss.EntityCultistLeader";
    private static final String[] TITLES = { "Alberic", "Anselm", "Bastian", "Beturian", "Chabier", "Chorache", "Chuse", "Dodorol", "Ebardo",
            "Ferrando", "Fertus", "Guillen", "Larpe", "Obano", "Zelipe" };
    private static final EntityDataAccessor<Integer> TITLE = SynchedEntityData.defineId(CultistLeader.class, EntityDataSerializers.INT);

    public CultistLeader(EntityType<? extends CultistLeader> type, Level level) { super(type, level, LEGACY_CLASS); xpReward = 40; bossBar.setColor(net.minecraft.world.BossEvent.BossBarColor.RED); }

    public static AttributeSupplier.Builder attributes() {
        return bossAttributes().add(Attributes.MOVEMENT_SPEED, 0.32).add(Attributes.MAX_HEALTH, 125).add(Attributes.ATTACK_DAMAGE, 5);
    }

    public static void host(LegacyRuntime runtime) {
        Tc4Legacy.host(runtime, LEGACY_CLASS, (level, original) -> {
            var leader = Tc4Entities.CULTIST_LEADER.get().create(level);
            if (leader == null) return null;
            if (!Tc4Legacy.copyEquipment(runtime, original, leader)) leader.equip(level.getCurrentDifficultyAt(leader.blockPosition()));
            for (var slot : EquipmentSlot.values()) leader.setDropChance(slot, 0.0F);
            Tc4Legacy.copyHome(runtime, original, leader);
            leader.entityData.set(TITLE, Math.max(0, Math.min(TITLES.length - 1, Tc4Legacy.watcherByte(runtime, original, 16))));
            return leader;
        });
    }

    @Override protected void defineSynchedData() { super.defineSynchedData(); entityData.define(TITLE, 0); }

    @Override protected Component getTypeName() {
        return Component.translatable("entity.tc4modern.cultist_leader.titled", TITLES[Math.max(0, Math.min(TITLES.length - 1, entityData.get(TITLE)))]);
    }

    @Override protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new Tc4Goals.LongRangeAttack<>(this, 16.0, 1.0, 30, 40, 24.0F));
        goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.1, false));
        goalSelector.addGoal(6, new MoveTowardsRestrictionGoal(this, 0.8));
        goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new Tc4Goals.CultistHurtByTarget(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    private void wear(EquipmentSlot slot, String key) {
        var stack = Tc4Legacy.stack(key, 0, 1);
        if (!stack.isEmpty()) { setItemSlot(slot, stack); setDropChance(slot, 0.0F); }
    }

    void equip(DifficultyInstance difficulty) {
        wear(EquipmentSlot.HEAD, "Thaumcraft:ItemHelmetCultistLeaderPlate");
        wear(EquipmentSlot.CHEST, "Thaumcraft:ItemChestplateCultistLeaderPlate");
        wear(EquipmentSlot.LEGS, "Thaumcraft:ItemLeggingsCultistLeaderPlate");
        wear(EquipmentSlot.FEET, "Thaumcraft:ItemBootsCultist");
        wear(EquipmentSlot.MAINHAND, level().getDifficulty() == Difficulty.EASY ? "Thaumcraft:ItemSwordVoid" : "Thaumcraft:ItemSwordCrimson");
        float f = difficulty.getSpecialMultiplier();
        var weapon = getMainHandItem();
        if (!weapon.isEmpty() && random.nextFloat() < 0.5F * f)
            setItemSlot(EquipmentSlot.MAINHAND, EnchantmentHelper.enchantItem(random, weapon, (int) (7.0F + f * random.nextInt(22)), false));
    }

    @Override public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason,
            @Nullable SpawnGroupData group, @Nullable CompoundTag tag) {
        equip(difficulty);
        entityData.set(TITLE, random.nextInt(TITLES.length));
        return super.finalizeSpawn(level, difficulty, reason, group, tag);
    }

    @Override protected void customServerAiStep() {
        super.customServerAiStep();
        for (var cultist : level().getEntitiesOfClass(Cultist.class, getBoundingBox().inflate(8.0)))
            if (!cultist.hasEffect(MobEffects.REGENERATION)) cultist.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 1));
    }

    @Override public void performRangedAttack(LivingEntity target, float power) {
        if (!hasLineOfSight(target)) return;
        swing(InteractionHand.MAIN_HAND);
        getLookControl().setLookAt(target.getX(), target.getBoundingBox().minY + target.getBbHeight() / 2.0, target.getZ(), 30.0F, 30.0F);
        var orb = GolemOrb.create(level(), this, target, true);
        var motion = orb.getDeltaMovement();
        orb.setPos(orb.getX() + motion.x / 2.0, orb.getY(), orb.getZ() + motion.z / 2.0);
        orb.heading(target.getX() - getX(), target.getBoundingBox().minY + target.getBbHeight() / 2.0 - (getY() + getBbHeight() / 2.0) + 2.0, target.getZ() - getZ(), 0.66F, 3.0F);
        playSound(Tc4Legacy.sound("egattack"), 1.0F, 1.0F + random.nextFloat() * 0.1F);
        level().addFreshEntity(orb);
    }

    /** The leader drops only its loot bag, not the pearl other TC4 bosses leave. */
    @Override protected void dropCustomDeathLoot(DamageSource source, int looting, boolean recentlyHit) {
        Tc4Legacy.drop(this, Tc4Legacy.stack("Thaumcraft:ItemLootBag", 2, 1), 1.5F);
    }

    @Override public boolean isAlliedTo(Entity other) { return other instanceof Cultist || other instanceof CultistLeader || super.isAlliedTo(other); }
    @Override public boolean canAttack(LivingEntity target) { return !(target instanceof Cultist) && !(target instanceof CultistLeader) && super.canAttack(target); }

    @Override public void addAdditionalSaveData(CompoundTag tag) { super.addAdditionalSaveData(tag); tag.putByte("title", (byte) (int) entityData.get(TITLE)); }
    @Override public void readAdditionalSaveData(CompoundTag tag) { super.readAdditionalSaveData(tag); entityData.set(TITLE, Math.max(0, Math.min(TITLES.length - 1, (int) tag.getByte("title")))); }
}
