package github.com.gengyoubo.tcmodern.tc4.entity;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

/** Hostile tainted villager using the shared tainted-creature targeting. */
public final class TaintVillager extends TaintCow {
    static final String LEGACY_CLASS = "thaumcraft.common.entities.monster.EntityTaintVillager";
    public TaintVillager(EntityType<? extends TaintVillager> type, Level level) { super(type, level); }
    @Override protected String legacyClass() { return LEGACY_CLASS; }
    public static AttributeSupplier.Builder villagerAttributes() {
        return TaintCow.attributes().add(Attributes.MAX_HEALTH, 30).add(Attributes.ATTACK_DAMAGE, 4).add(Attributes.MOVEMENT_SPEED, 0.3);
    }
    @Override protected SoundEvent getAmbientSound() { return SoundEvents.VILLAGER_AMBIENT; }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.VILLAGER_HURT; }
    @Override protected SoundEvent getDeathSound() { return SoundEvents.VILLAGER_DEATH; }
}
