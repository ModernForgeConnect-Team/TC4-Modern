package github.com.gengyoubo.tcmodern.tc4.entity;

import github.com.gengyoubo.tcmodern.api.TcModernApi;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;

/** TC4's tainted creeper explodes without destroying blocks and poisons nearby living creatures. */
public final class TaintCreeper extends TaintCow {
    static final String LEGACY_CLASS = "thaumcraft.common.entities.monster.EntityTaintCreeper";
    private static final EntityDataAccessor<Integer> FUSE = SynchedEntityData.defineId(TaintCreeper.class, EntityDataSerializers.INT);
    private int previousFuse;
    public TaintCreeper(EntityType<? extends TaintCreeper> type, Level level) { super(type, level); }
    @Override protected String legacyClass() { return LEGACY_CLASS; }
    public static AttributeSupplier.Builder creeperAttributes() {
        return TaintCow.attributes().add(Attributes.MAX_HEALTH, 30).add(Attributes.ATTACK_DAMAGE, 2).add(Attributes.MOVEMENT_SPEED, 0.25);
    }
    @Override protected void defineSynchedData() { super.defineSynchedData(); entityData.define(FUSE, 0); }
    @Override protected void registerGoals() { super.registerGoals(); goalSelector.addGoal(3, new AvoidEntityGoal<>(this, Cat.class, 6, 1.2, 1.2)); }
    public float swelling(float partialTick) { return (previousFuse + (entityData.get(FUSE) - previousFuse) * partialTick) / 28.0F; }
    @Override public void tick() {
        previousFuse = entityData.get(FUSE);
        super.tick();
        if (level().isClientSide || !isAlive()) return;
        var target = getTarget();
        boolean close = target != null && target.isAlive() && distanceToSqr(target) < (entityData.get(FUSE) > 0 ? 49 : 9) && getSensing().hasLineOfSight(target);
        int old = entityData.get(FUSE);
        int fuse = Math.max(0, Math.min(30, old + (close ? 1 : -1)));
        if (old == 0 && fuse == 1) level().playSound(null, blockPosition(), SoundEvents.CREEPER_PRIMED, SoundSource.HOSTILE, 1, 0.5F);
        entityData.set(FUSE, fuse);
        if (fuse == 30) explodeTaint();
    }
    private void explodeTaint() {
        level().explode(this, getX(), getY() + getBbHeight() / 2.0, getZ(), 1.5F, Level.ExplosionInteraction.NONE);
        var effect = ForgeRegistries.MOB_EFFECTS.getValues().stream().filter(e -> "potion.fluxtaint".equals(e.getDescriptionId())).findFirst().orElse(null);
        if (effect != null) for (LivingEntity target : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(6))) {
            if (target != this && !(target instanceof TaintCow) && !(target instanceof TaintSpider) && !target.isInvertedHealAndHarm())
                target.addEffect(new MobEffectInstance(effect, 100, 0));
        }
        var fibres = TcModernApi.runtime().flatMap(runtime -> runtime.hostBlock("Thaumcraft:blockTaintFibres", 0, net.minecraft.world.level.block.state.BlockState.class)).orElse(null);
        if (fibres != null) for (int i = 0; i < 10; i++) {
            if (!random.nextBoolean()) continue;
            int x = (int)getX() + (int)((random.nextFloat() - random.nextFloat()) * 5);
            int z = (int)getZ() + (int)((random.nextFloat() - random.nextFloat()) * 5);
            BlockPos pos = new BlockPos(x, (int)getY(), z);
            if (level().hasChunkAt(pos) && level().getBlockState(pos).canBeReplaced() && level().getBlockState(pos.below()).isSolidRender(level(), pos.below()))
                level().setBlock(pos, fibres, 3);
        }
        discard();
    }
    @Override public void addAdditionalSaveData(CompoundTag tag) { super.addAdditionalSaveData(tag); tag.putInt("Tc4Fuse", entityData.get(FUSE)); }
    @Override public void readAdditionalSaveData(CompoundTag tag) { super.readAdditionalSaveData(tag); entityData.set(FUSE, Math.max(0, Math.min(29, tag.getInt("Tc4Fuse")))); }
    @Override protected SoundEvent getAmbientSound() { return null; }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.CREEPER_HURT; }
    @Override protected SoundEvent getDeathSound() { return SoundEvents.CREEPER_DEATH; }
}
