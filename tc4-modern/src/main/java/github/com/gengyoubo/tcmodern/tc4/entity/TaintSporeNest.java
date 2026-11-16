package github.com.gengyoubo.tcmodern.tc4.entity;

import com.mojang.logging.LogUtils;
import github.com.gengyoubo.mfcl.api.LegacyMappings;
import github.com.gengyoubo.mfcl.api.LegacyObject;
import github.com.gengyoubo.mfcl.api.mla.LegacyConstructor;
import github.com.gengyoubo.mfcl.api.mla.LegacyMethod;
import github.com.gengyoubo.mfcl.api.mla.LegacyType;
import github.com.gengyoubo.tcmodern.api.TcModernApi;
import github.com.gengyoubo.tcmodern.tc4.world.Tc4ModernWorldgen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

/** TC4 spore nest, which periodically releases a swarm when a player is nearby. */
public final class TaintSporeNest extends TaintCow {
    static final String LEGACY_CLASS = "thaumcraft.common.entities.monster.EntityTaintSporeSwarmer";
    private int spawnCounter = 500;
    private boolean deathSwarm;
    public TaintSporeNest(EntityType<? extends TaintSporeNest> type, Level level) { super(type, level); xpReward = 10; }
    @Override protected String legacyClass() { return LEGACY_CLASS; }
    @Override protected net.minecraft.sounds.SoundEvent getAmbientSound() { return Tc4Legacy.sound("roots"); }
    @Override protected net.minecraft.sounds.SoundEvent getHurtSound(DamageSource source) { return Tc4Legacy.sound("gore"); }
    @Override protected net.minecraft.sounds.SoundEvent getDeathSound() { return Tc4Legacy.sound("gore"); }
    @Override protected float getSoundVolume() { return 0.1F; }
    public static AttributeSupplier.Builder nestAttributes() {
        return TaintCow.attributes().add(Attributes.MAX_HEALTH, 75).add(Attributes.ATTACK_DAMAGE, 1).add(Attributes.MOVEMENT_SPEED, 0);
    }
    @Override protected void registerGoals() { }
    @Override public void tick() {
        super.tick();
        if (level().isClientSide || !isAlive()) return;
        if (tickCount % 20 == 0 && !level().getBiome(blockPosition()).is(Tc4ModernWorldgen.TAINTED_LAND)) hurt(damageSources().magic(), 1.0F);
        if (spawnCounter > 0) spawnCounter--;
        if (spawnCounter == 0 && level().getNearestPlayer(this, 16) != null) {
            spawnCounter = 500;
            releaseSwarm();
        }
    }
    @Override public void die(DamageSource source) {
        if (!level().isClientSide && !deathSwarm) { deathSwarm = true; releaseSwarm(); }
        super.die(source);
    }
    private boolean releaseSwarm() {
        var runtime = TcModernApi.runtime().orElse(null);
        if (runtime == null) return false;
        try {
            var world = runtime.mappings().toLegacy(LegacyMappings.WORLD, level()).orElseThrow();
            var swarm = runtime.accessor(OldSwarmFactory.class).create(world);
            runtime.accessor(OldSwarm.class, swarm).position(getX(), getY() + 0.5, getZ(), random.nextFloat() * 360, 0);
            return runtime.accessor(OldWorld.class, world).spawn(swarm);
        } catch (RuntimeException failure) {
            LogUtils.getLogger().warn("TC4 taint nest could not release its swarm", failure);
            return false;
        }
    }
    @Override public void addAdditionalSaveData(CompoundTag tag) { super.addAdditionalSaveData(tag); tag.putInt("Tc4SpawnCounter", spawnCounter); tag.putBoolean("Tc4DeathSwarm", deathSwarm); }
    @Override public void readAdditionalSaveData(CompoundTag tag) { super.readAdditionalSaveData(tag); if (tag.contains("Tc4SpawnCounter")) spawnCounter = Math.max(0, tag.getInt("Tc4SpawnCounter")); deathSwarm = tag.getBoolean("Tc4DeathSwarm"); }
    @LegacyType("thaumcraft.common.entities.monster.EntityTaintSwarm") public interface OldSwarmFactory {
        @LegacyConstructor(descriptor="(Lnet/minecraft/world/World;)V") LegacyObject create(LegacyObject world);
    }
    @LegacyType("thaumcraft.common.entities.monster.EntityTaintSwarm") public interface OldSwarm {
        @LegacyMethod(name="func_70012_b", descriptor="(DDDFF)V") void position(double x, double y, double z, float yaw, float pitch);
    }
    @LegacyType("net.minecraft.world.World") public interface OldWorld {
        @LegacyMethod(name="func_72838_d", descriptor="(Lnet/minecraft/entity/Entity;)Z") boolean spawn(LegacyObject entity);
    }
}
