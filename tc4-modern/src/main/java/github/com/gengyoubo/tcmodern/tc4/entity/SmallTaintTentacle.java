package github.com.gengyoubo.tcmodern.tc4.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

/** Temporary small tentacle spawned by a larger one. */
public final class SmallTaintTentacle extends TaintTentacle {
    static final String LEGACY_CLASS = "thaumcraft.common.entities.monster.EntityTaintacleSmall";
    private int lifetime = 200;
    public SmallTaintTentacle(EntityType<? extends SmallTaintTentacle> type, Level level) { super(type, level); xpReward = 0; }
    @Override protected String legacyClass() { return LEGACY_CLASS; }
    public static AttributeSupplier.Builder smallAttributes() { return TaintTentacle.tentacleAttributes().add(Attributes.MAX_HEALTH, 8).add(Attributes.ATTACK_DAMAGE, 2); }
    @Override public void tick() { super.tick(); if (!level().isClientSide && isAlive() && --lifetime <= 0) hurt(damageSources().generic(), 10); }
    @Override public void addAdditionalSaveData(CompoundTag tag) { super.addAdditionalSaveData(tag); tag.putInt("Tc4TentacleLifetime", lifetime); }
    @Override public void readAdditionalSaveData(CompoundTag tag) { super.readAdditionalSaveData(tag); if (tag.contains("Tc4TentacleLifetime")) lifetime = tag.getInt("Tc4TentacleLifetime"); }
}
