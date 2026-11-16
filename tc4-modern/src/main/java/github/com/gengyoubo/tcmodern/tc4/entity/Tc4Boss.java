package github.com.gengyoubo.tcmodern.tc4.entity;

import github.com.gengyoubo.tcmodern.tc4.entity.item.SpecialItem;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * EntityThaumcraftBoss: a boss bar, slow regeneration, aggro kept per attacker, invulnerability while it rises, and
 * a rage when one hit exceeds 35 damage (the hit itself is capped at 35).
 */
public abstract class Tc4Boss extends Monster {
    private static final EntityDataAccessor<Integer> ANGER = SynchedEntityData.defineId(Tc4Boss.class, EntityDataSerializers.INT);
    protected final LegacyHost legacy;
    protected final ServerBossEvent bossBar = new ServerBossEvent(Component.empty(), BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.PROGRESS);
    private final Map<Integer, Integer> aggro = new HashMap<>();
    public int spawnTimer;

    protected Tc4Boss(EntityType<? extends Tc4Boss> type, Level level, String legacyClass) {
        super(type, level);
        legacy = new LegacyHost(legacyClass);
        xpReward = 50;
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder bossAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.KNOCKBACK_RESISTANCE, 0.95).add(Attributes.FOLLOW_RANGE, 40);
    }

    @Override protected void defineSynchedData() { super.defineSynchedData(); entityData.define(ANGER, 0); }
    public int anger() { return entityData.get(ANGER); }
    public void setAnger(int anger) { entityData.set(ANGER, anger); }
    public int spawnTimer() { return spawnTimer; }

    @Override public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason,
            @Nullable SpawnGroupData group, @Nullable CompoundTag tag) {
        restrictTo(blockPosition(), 24);
        return super.finalizeSpawn(level, difficulty, reason, group, tag);
    }

    @Override protected void customServerAiStep() {
        super.customServerAiStep();
        if (getTarget() != null && !getTarget().isAlive()) setTarget(null);
    }

    /** Bosses do nothing while they rise. */
    @Override protected boolean isImmobile() { return super.isImmobile() || spawnTimer > 0; }

    @Override public void tick() {
        super.tick();
        if (spawnTimer > 0) spawnTimer--;
        if (anger() > 0 && !level().isClientSide) setAnger(anger() - 1);
        if (level().isClientSide && random.nextInt(15) == 0 && anger() > 0)
            level().addParticle(ParticleTypes.ANGRY_VILLAGER, getX() + random.nextFloat() * getBbWidth() - getBbWidth() / 2.0,
                    getBoundingBox().minY + getBbHeight() + random.nextDouble() * 0.5, getZ() + random.nextFloat() * getBbWidth() - getBbWidth() / 2.0,
                    random.nextGaussian() * 0.02, random.nextGaussian() * 0.02, random.nextGaussian() * 0.02);
        if (!level().isClientSide) {
            if (tickCount % 30 == 0) heal(1.0F);
            if (getTarget() != null && tickCount % 20 == 0) retarget();
            bossBar.setProgress(getHealth() / getMaxHealth());
            bossBar.setName(getDisplayName());
        }
        legacy.tick(this);
    }

    /** Switches to the attacker that has dealt clearly more damage than the current target. */
    private void retarget() {
        int current = getTarget().getId();
        int best = aggro.getOrDefault(current, 0), bestId = current;
        for (var entry : Map.copyOf(aggro).entrySet()) {
            var entity = level().getEntity(entry.getKey());
            if (entity == null || !entity.isAlive() || distanceToSqr(entity) > 16384.0) { aggro.remove(entry.getKey()); continue; }
            if (entry.getValue() > best + 25 && entry.getValue() > best * 1.1) { best = entry.getValue(); bestId = entry.getKey(); }
        }
        if (bestId != current && level().getEntity(bestId) instanceof LivingEntity living) setTarget(living);
    }

    @Override public boolean isInvulnerable() { return super.isInvulnerable() || spawnTimer > 0; }
    @Override public boolean isInvulnerableTo(DamageSource source) { return spawnTimer > 0 && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY) || super.isInvulnerableTo(source); }
    @Override public boolean isPushable() { return super.isPushable() && spawnTimer == 0; }
    @Override public boolean canBreatheUnderwater() { return true; }
    @Override protected int decreaseAirSupply(int air) { return air; }
    @Override public void makeStuckInBlock(BlockState state, Vec3 motion) { }
    @Override public boolean canPickUpLoot() { return false; }
    @Override public boolean removeWhenFarAway(double distance) { return false; }
    @Override protected void populateDefaultEquipmentSlots(net.minecraft.util.RandomSource random, DifficultyInstance difficulty) { }
    @Override public boolean isAlliedTo(Entity other) { return other instanceof EldritchMob || super.isAlliedTo(other); }

    @Override protected void dropCustomDeathLoot(DamageSource source, int looting, boolean recentlyHit) {
        if (level() instanceof ServerLevel server) {
            var pearl = Tc4Legacy.stack("Thaumcraft:ItemEldritchObject", 3, 1);
            if (!pearl.isEmpty()) SpecialItem.drop(server, getX(), getY() + getBbHeight() / 2.0, getZ(), pearl);
        }
        Tc4Legacy.drop(this, Tc4Legacy.stack("Thaumcraft:ItemLootBag", 2, 1), 1.5F);
    }

    @Override public boolean hurt(DamageSource source, float amount) {
        if (!level().isClientSide) {
            if (source.getEntity() instanceof LivingEntity attacker) aggro.merge(attacker.getId(), (int) amount, Integer::sum);
            if (amount > 35.0F) {
                if (anger() == 0) {
                    addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, (int) (amount / 15.0F)));
                    addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 200, (int) (amount / 40.0F)));
                    addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 200, (int) (amount / 40.0F)));
                    setAnger(200);
                    if (source.getEntity() instanceof Player player)
                        player.sendSystemMessage(Component.empty().append(getDisplayName()).append(" ").append(Component.translatable("tc.boss.enrage")));
                }
                amount = 35.0F;
            }
        }
        return super.hurt(source, amount);
    }

    @Override public void startSeenByPlayer(ServerPlayer player) { super.startSeenByPlayer(player); bossBar.addPlayer(player); }
    @Override public void stopSeenByPlayer(ServerPlayer player) { super.stopSeenByPlayer(player); bossBar.removePlayer(player); }

    @Override public void addAdditionalSaveData(CompoundTag tag) { super.addAdditionalSaveData(tag); Tc4Legacy.saveHome(this, tag); legacy.save(this, tag); }
    @Override public void readAdditionalSaveData(CompoundTag tag) { super.readAdditionalSaveData(tag); Tc4Legacy.loadHome(this, tag); legacy.load(tag); }

    protected static BlockPos feet(Entity entity) { return BlockPos.containing(entity.getX(), entity.getY(), entity.getZ()); }
}
