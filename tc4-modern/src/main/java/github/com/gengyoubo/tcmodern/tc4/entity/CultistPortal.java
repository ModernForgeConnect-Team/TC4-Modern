package github.com.gengyoubo.tcmodern.tc4.entity;

import github.com.gengyoubo.mfcl.api.LegacyRuntime;
import github.com.gengyoubo.mfcl.api.mla.LegacyField;
import github.com.gengyoubo.mfcl.api.mla.LegacyType;
import github.com.gengyoubo.tcmodern.tc4.entity.item.SpecialItem;
import github.com.gengyoubo.tcmodern.tc4.init.Tc4Entities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The crimson portal of a cultist camp. When a player comes near it raises banners and loot crates, then sends waves
 * of knights and clerics and finally the cult leader; after that every wave costs it health.
 */
public final class CultistPortal extends Monster {
    static final String LEGACY_CLASS = "thaumcraft.common.entities.monster.boss.EntityCultistPortal";
    private final LegacyHost legacy = new LegacyHost(LEGACY_CLASS);
    private final ServerBossEvent bossBar = new ServerBossEvent(Component.empty(), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS);
    private int stage, stageCounter = 200;
    public int pulse;

    public CultistPortal(EntityType<? extends CultistPortal> type, Level level) { super(type, level); xpReward = 30; setPersistenceRequired(); }

    public static AttributeSupplier.Builder attributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 500).add(Attributes.ATTACK_DAMAGE, 0).add(Attributes.KNOCKBACK_RESISTANCE, 1);
    }

    public static void host(LegacyRuntime runtime) {
        Tc4Legacy.host(runtime, LEGACY_CLASS, (level, original) -> {
            var portal = Tc4Entities.CULTIST_PORTAL.get().create(level);
            if (portal != null) portal.stage = runtime.accessor(Old.class, original).stage();
            return portal;
        });
    }

    @Override public int getArmorValue() { return 5; }
    @Override public boolean isPushable() { return false; }
    @Override public boolean isPickable() { return true; }
    @Override public void move(MoverType type, Vec3 motion) { }
    @Override protected void registerGoals() { }
    @Override public boolean removeWhenFarAway(double distance) { return false; }
    @Override public boolean shouldRenderAtSqrDistance(double distance) { return distance < 4096.0; }
    @Override public float getLightLevelDependentMagicValue() { return 1.0F; }
    @Override public boolean addEffect(MobEffectInstance effect, @Nullable Entity source) { return false; }
    @Override public boolean causeFallDamage(float distance, float multiplier, DamageSource source) { return false; }

    @Override public void tick() {
        super.tick();
        if (!level().isClientSide) {
            if (stageCounter <= 0) {
                if (level().getNearestPlayer(this, 48.0) != null) {
                    level().broadcastEntityEvent(this, (byte) 16);
                    if (stage < 5) { stageCounter = 15 + random.nextInt(10 - stage) - stage; spawnMinion(); }
                    else if (stage == 12) { stageCounter = 50 + timing() * 2 + random.nextInt(50); spawnLeader(); }
                    else { int t = timing(); stageCounter = t + random.nextInt(5 + t / 3); spawnMinion(); }
                    stage++;
                } else stageCounter = 30 + random.nextInt(30);
            } else {
                stageCounter--;
                if (stageCounter == 160 && stage == 0) raiseBanners();
                if (stageCounter > 20 && stageCounter < 150 && stage == 0 && stageCounter % 13 == 0) placeCrate();
            }
            if (stage < 12) heal(1.0F);
            bossBar.setProgress(getHealth() / getMaxHealth());
            bossBar.setName(getDisplayName());
        }
        if (pulse > 0) pulse--;
        legacy.tick(this);
    }

    /** Cultists within 32 blocks slow the waves down: twenty ticks each. */
    private int timing() { return level().getEntitiesOfClass(Cultist.class, getBoundingBox().inflate(32.0), e -> e.distanceTo(this) <= 32.0).size() * 20; }

    private void raiseBanners() {
        level().broadcastEntityEvent(this, (byte) 16);
        var banner = Tc4Legacy.block("Thaumcraft:blockWoodenDevice", 8);
        if (banner == null) return;
        for (var dir : Direction.Plane.HORIZONTAL) {
            var pos = BlockPos.containing(getX() - dir.getStepX() * 6, getY(), getZ() + dir.getStepZ() * 6);
            level().setBlock(pos, banner, 3);
            arcTo(pos);
        }
    }

    private void placeCrate() {
        int x = (int) getX() + random.nextInt(5) - random.nextInt(5), z = (int) getZ() + random.nextInt(5) - random.nextInt(5);
        var pos = new BlockPos(x, (int) getY(), z);
        if (x == (int) getX() || z == (int) getZ() || !level().isEmptyBlock(pos)) return;
        level().broadcastEntityEvent(this, (byte) 16);
        float roll = random.nextFloat();
        var crate = Tc4Legacy.block("Thaumcraft:blockLootCrate", roll < 0.05F ? 2 : roll < 0.2F ? 1 : 0);
        if (crate == null) return;
        level().setBlock(pos, crate, 3);
        arcTo(pos);
    }

    private void arcTo(BlockPos pos) {
        if (level() instanceof ServerLevel server) {
            var from = position().add(0, getBbHeight() / 2.0, 0);
            var to = Vec3.atCenterOf(pos);
            for (int i = 0; i <= 12; i++) { var at = from.lerp(to, i / 12.0); server.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 1, 0.05, 0.05, 0.05, 0); }
        }
        playSound(Tc4Legacy.sound("wandfail"), 1.0F, 1.0F);
    }

    private void spawnMinion() {
        if (!(level() instanceof ServerLevel server)) return;
        Cultist cultist = random.nextFloat() > 0.33F ? Tc4Entities.CULTIST_KNIGHT.get().create(server) : Tc4Entities.CULTIST_CLERIC.get().create(server);
        if (cultist == null) return;
        cultist.moveTo(getX() + random.nextFloat() - random.nextFloat(), getY() + 0.25, getZ() + random.nextFloat() - random.nextFloat(), random.nextFloat() * 360.0F, 0);
        cultist.finalizeSpawn(server, server.getCurrentDifficultyAt(cultist.blockPosition()), MobSpawnType.MOB_SUMMONED, null, null);
        cultist.spawnAnim();
        cultist.restrictTo(blockPosition(), 32);
        server.addFreshEntity(cultist);
        cultist.playSound(Tc4Legacy.sound("wandfail"), 1.0F, 1.0F);
        if (stage > 12) hurt(damageSources().fellOutOfWorld(), 5 + random.nextInt(5));
    }

    private void spawnLeader() {
        if (!(level() instanceof ServerLevel server)) return;
        var leader = Tc4Entities.CULTIST_LEADER.get().create(server);
        if (leader == null) return;
        leader.moveTo(getX() + random.nextFloat() - random.nextFloat(), getY() + 0.25, getZ() + random.nextFloat() - random.nextFloat(), random.nextFloat() * 360.0F, 0);
        leader.finalizeSpawn(server, server.getCurrentDifficultyAt(leader.blockPosition()), MobSpawnType.MOB_SUMMONED, null, null);
        leader.restrictTo(blockPosition(), 32);
        leader.spawnAnim();
        server.addFreshEntity(leader);
        leader.playSound(Tc4Legacy.sound("wandfail"), 1.0F, 1.0F);
    }

    @Override public void playerTouch(Player player) {
        if (distanceToSqr(player) < 3.0 && player.hurt(damageSources().indirectMagic(this, this), 8.0F))
            playSound(Tc4Legacy.sound("zap"), 1.0F, (random.nextFloat() - random.nextFloat()) * 0.1F + 1.0F);
    }

    @Override public void handleEntityEvent(byte event) {
        if (event == 16) { pulse = 10; spawnAnim(); }
        else super.handleEntityEvent(event);
    }

    @Override public void die(DamageSource source) {
        if (!level().isClientSide) level().explode(this, getX(), getY(), getZ(), 2.0F, Level.ExplosionInteraction.NONE);
        super.die(source);
    }

    @Override protected void dropCustomDeathLoot(DamageSource source, int looting, boolean recentlyHit) {
        var pearl = Tc4Legacy.stack("Thaumcraft:ItemEldritchObject", 3, 1);
        if (!pearl.isEmpty() && level() instanceof ServerLevel server) SpecialItem.drop(server, getX(), getY() + getBbHeight() / 2.0, getZ(), pearl);
    }

    @Override protected float getSoundVolume() { return 0.75F; }
    @Override public int getAmbientSoundInterval() { return 540; }
    @Override protected SoundEvent getAmbientSound() { return Tc4Legacy.sound("monolith"); }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return Tc4Legacy.sound("zap"); }
    @Override protected SoundEvent getDeathSound() { return Tc4Legacy.sound("shock"); }

    @Override public void startSeenByPlayer(ServerPlayer player) { super.startSeenByPlayer(player); bossBar.addPlayer(player); }
    @Override public void stopSeenByPlayer(ServerPlayer player) { super.stopSeenByPlayer(player); bossBar.removePlayer(player); }

    @Override public void addAdditionalSaveData(CompoundTag tag) { super.addAdditionalSaveData(tag); tag.putInt("stage", stage); legacy.save(this, tag); }
    @Override public void readAdditionalSaveData(CompoundTag tag) { super.readAdditionalSaveData(tag); stage = tag.getInt("stage"); legacy.load(tag); }

    @LegacyType(LEGACY_CLASS) public interface Old { @LegacyField(name = "stage", descriptor = "I") int stage(); }
}
