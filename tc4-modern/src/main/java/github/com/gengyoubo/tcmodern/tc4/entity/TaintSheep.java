package github.com.gengyoubo.tcmodern.tc4.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/** A hostile tainted sheep with persistent, synchronized sheared state. */
public final class TaintSheep extends TaintCow {
    static final String LEGACY_CLASS = "thaumcraft.common.entities.monster.EntityTaintSheep";
    private static final EntityDataAccessor<Boolean> SHEARED = SynchedEntityData.defineId(TaintSheep.class, EntityDataSerializers.BOOLEAN);
    public TaintSheep(EntityType<? extends TaintSheep> type, Level level) { super(type, level); xpReward = 2; }
    @Override protected String legacyClass() { return LEGACY_CLASS; }
    public static AttributeSupplier.Builder sheepAttributes() {
        return TaintCow.attributes().add(Attributes.MAX_HEALTH, 20).add(Attributes.ATTACK_DAMAGE, 3).add(Attributes.MOVEMENT_SPEED, 0.25);
    }
    @Override protected void defineSynchedData() { super.defineSynchedData(); entityData.define(SHEARED, false); }
    public boolean isSheared() { return entityData.get(SHEARED); }
    @Override public InteractionResult mobInteract(Player player, InteractionHand hand) {
        var tool = player.getItemInHand(hand);
        if (tool.is(Items.SHEARS) && isAlive() && !isSheared()) {
            if (!level().isClientSide) {
                entityData.set(SHEARED, true);
                level().playSound(null, blockPosition(), SoundEvents.SHEEP_SHEAR, SoundSource.NEUTRAL, 1.0F, 1.0F);
                int count = 1 + random.nextInt(3);
                for (int i = 0; i < count; i++) spawnAtLocation(Items.PURPLE_WOOL);
                tool.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(hand));
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        return super.mobInteract(player, hand);
    }
    @Override public void addAdditionalSaveData(CompoundTag tag) { super.addAdditionalSaveData(tag); tag.putBoolean("Sheared", isSheared()); }
    @Override public void readAdditionalSaveData(CompoundTag tag) { super.readAdditionalSaveData(tag); entityData.set(SHEARED, tag.getBoolean("Sheared")); }
    @Override protected SoundEvent getAmbientSound() { return SoundEvents.SHEEP_AMBIENT; }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.SHEEP_HURT; }
    @Override protected SoundEvent getDeathSound() { return SoundEvents.SHEEP_DEATH; }
}
