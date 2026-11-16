package github.com.gengyoubo.tcmodern.tc4.entity;

import github.com.gengyoubo.mfcl.api.LegacyRuntime;
import github.com.gengyoubo.tcmodern.api.TcModernApi;
import github.com.gengyoubo.tcmodern.tc4.init.Tc4Entities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.*;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public final class TaintCow extends Monster {
    private static final String LEGACY_CLASS = "thaumcraft.common.entities.monster.EntityTaintCow";
    private static final String LEGACY_DATA = "Tc4LegacyTaintCow";
    private byte[] pendingLegacyData;
    public TaintCow(EntityType<? extends Monster> type, Level level) { super(type, level); }
    public static AttributeSupplier.Builder attributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 40).add(Attributes.ATTACK_DAMAGE, 6).add(Attributes.MOVEMENT_SPEED, 0.27);
    }
    public static void registerLegacyHost(LegacyRuntime runtime) {
        runtime.entityHosts().register(LEGACY_CLASS, (level, original) -> Tc4Entities.TAINT_COW.get().create((ServerLevel) level));
    }
    @Override protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, false));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 1.0));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 6));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(0, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Villager.class, false));
        targetSelector.addGoal(8, new NearestAttackableTargetGoal<>(this, Animal.class, false));
    }
    @Override protected SoundEvent getAmbientSound() { return SoundEvents.COW_AMBIENT; }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.COW_HURT; }
    @Override protected SoundEvent getDeathSound() { return SoundEvents.COW_HURT; }
    @Override public void tick() {
        super.tick();
        if (level().isClientSide) return;
        TcModernApi.runtime().ifPresent(runtime -> {
            var hosts = runtime.entityHosts();
            if (pendingLegacyData != null) { hosts.restore(this, LEGACY_CLASS, pendingLegacyData); pendingLegacyData = null; }
            if (hosts.original(this).isPresent()) hosts.sync(this);
        });
    }
    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (pendingLegacyData != null) tag.putByteArray(LEGACY_DATA, pendingLegacyData);
        else if (!level().isClientSide) TcModernApi.runtime().ifPresent(runtime -> {
            var hosts = runtime.entityHosts();
            if (hosts.original(this).isPresent()) tag.putByteArray(LEGACY_DATA, hosts.save(this));
        });
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        pendingLegacyData = tag.contains(LEGACY_DATA, 7) ? tag.getByteArray(LEGACY_DATA) : null;
    }
}
