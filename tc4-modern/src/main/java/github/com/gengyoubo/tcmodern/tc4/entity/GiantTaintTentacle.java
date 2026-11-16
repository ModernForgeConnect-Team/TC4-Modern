package github.com.gengyoubo.tcmodern.tc4.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;

/** TC4 boss: regenerates, enrages on heavy hits, and drops its reward for the last nearby boss. */
public final class GiantTaintTentacle extends TaintTentacle {
    static final String LEGACY_CLASS = "thaumcraft.common.entities.monster.boss.EntityTaintacleGiant";
    private static final EntityDataAccessor<Integer> ANGER = SynchedEntityData.defineId(GiantTaintTentacle.class, EntityDataSerializers.INT);
    private final ServerBossEvent bossBar = new ServerBossEvent(getDisplayName(), BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.PROGRESS);
    public GiantTaintTentacle(EntityType<? extends GiantTaintTentacle> type, Level level) { super(type, level); xpReward = 20; }
    @Override protected String legacyClass() { return LEGACY_CLASS; }
    public static AttributeSupplier.Builder giantAttributes() {
        return TaintTentacle.tentacleAttributes().add(Attributes.MAX_HEALTH, 125).add(Attributes.ATTACK_DAMAGE, 9);
    }
    @Override protected void defineSynchedData() { super.defineSynchedData(); entityData.define(ANGER, 0); }
    @Override public void tick() {
        super.tick();
        if (level().isClientSide) {
            if (entityData.get(ANGER) > 0 && random.nextInt(15) == 0)
                level().addParticle(ParticleTypes.ANGRY_VILLAGER, getX() + (random.nextDouble() - 0.5) * getBbWidth(), getY() + getBbHeight(), getZ() + (random.nextDouble() - 0.5) * getBbWidth(), 0, 0, 0);
            return;
        }
        bossBar.setProgress(getHealth() / getMaxHealth());
        bossBar.setName(getDisplayName());
        if (entityData.get(ANGER) > 0) entityData.set(ANGER, entityData.get(ANGER) - 1);
        if (tickCount % 30 == 0 && isAlive()) heal(1);
    }
    @Override public boolean hurt(DamageSource source, float amount) {
        if (!level().isClientSide && amount > 35) {
            if (entityData.get(ANGER) == 0) {
                addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, (int)(amount / 15)));
                addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 200, (int)(amount / 40)));
                addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 200, (int)(amount / 40)));
                entityData.set(ANGER, 200);
                if (source.getEntity() instanceof Player player) player.sendSystemMessage(Component.empty().append(getDisplayName()).append(" ").append(Component.translatable("tc.boss.enrage")));
            }
            amount = 35;
        }
        return super.hurt(source, amount);
    }
    @Override public void startSeenByPlayer(ServerPlayer player) { super.startSeenByPlayer(player); bossBar.addPlayer(player); }
    @Override public void stopSeenByPlayer(ServerPlayer player) { super.stopSeenByPlayer(player); bossBar.removePlayer(player); }
    @Override protected void dropCustomDeathLoot(DamageSource source, int looting, boolean recentlyHit) {
        super.dropCustomDeathLoot(source, looting, recentlyHit);
        if (!level().getEntitiesOfClass(GiantTaintTentacle.class, getBoundingBox().inflate(48), other -> other != this && other.isAlive()).isEmpty()) return;
        var item = ForgeRegistries.ITEMS.getValue(ResourceLocation.fromNamespaceAndPath("thaumcraft", "item_eldritch_object/meta_3"));
        if (item != null) spawnAtLocation(new ItemStack(item));
    }
    @Override public void addAdditionalSaveData(CompoundTag tag) { super.addAdditionalSaveData(tag); tag.putInt("Tc4TentacleAnger", entityData.get(ANGER)); }
    @Override public void readAdditionalSaveData(CompoundTag tag) { super.readAdditionalSaveData(tag); entityData.set(ANGER, Math.max(0, tag.getInt("Tc4TentacleAnger"))); }
}
