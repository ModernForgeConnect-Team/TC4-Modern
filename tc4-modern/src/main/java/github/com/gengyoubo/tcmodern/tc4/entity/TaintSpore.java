package github.com.gengyoubo.tcmodern.tc4.entity;

import github.com.gengyoubo.mfcl.api.LegacyObject;
import github.com.gengyoubo.mfcl.api.LegacyRuntime;
import github.com.gengyoubo.mfcl.api.mla.LegacyMethod;
import github.com.gengyoubo.mfcl.api.mla.LegacyType;
import github.com.gengyoubo.tcmodern.api.TcModernApi;
import github.com.gengyoubo.tcmodern.tc4.init.Tc4Entities;
import github.com.gengyoubo.tcmodern.tc4.world.Tc4ModernWorldgen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/** Stationary TC4 spore pod: grows on taint fibres and bursts into taint spiders. */
public final class TaintSpore extends TaintCow {
    static final String LEGACY_CLASS = "thaumcraft.common.entities.monster.EntityTaintSpore";
    private static final EntityDataAccessor<Integer> SIZE = SynchedEntityData.defineId(TaintSpore.class, EntityDataSerializers.INT);
    private int growth;
    private boolean burst;
    public TaintSpore(EntityType<? extends TaintSpore> type, Level level) { super(type, level); xpReward = 2; }
    @Override protected String legacyClass() { return LEGACY_CLASS; }
    @Override protected net.minecraft.sounds.SoundEvent getAmbientSound() { return Tc4Legacy.sound("swarm"); }
    @Override protected net.minecraft.sounds.SoundEvent getHurtSound(DamageSource source) { return Tc4Legacy.sound("gore"); }
    @Override protected net.minecraft.sounds.SoundEvent getDeathSound() { return Tc4Legacy.sound("gore"); }
    @Override protected float getSoundVolume() { return 0.1F; }
    public static AttributeSupplier.Builder sporeAttributes() { return TaintCow.attributes().add(Attributes.MAX_HEALTH, 1).add(Attributes.ATTACK_DAMAGE, 1).add(Attributes.MOVEMENT_SPEED, 0); }
    public static void registerLegacyHost(LegacyRuntime runtime) {
        runtime.entityHosts().register(LEGACY_CLASS, (level, original) -> {
            var host = Tc4Entities.TAINT_SPORE.get().create((ServerLevel) level);
            if (host != null) host.setSporeSize(runtime.accessor(OldSpore.class, original).size());
            return host;
        });
    }
    @Override protected void defineSynchedData() { super.defineSynchedData(); entityData.define(SIZE, 2); }
    @Override protected void registerGoals() { }
    public int sporeSize() { return entityData.get(SIZE); }
    private void setSporeSize(int size) { entityData.set(SIZE, Math.max(1, Math.min(10, size))); xpReward = sporeSize(); }
    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> key) { super.onSyncedDataUpdated(key); if (SIZE.equals(key)) refreshDimensions(); }
    @Override public EntityDimensions getDimensions(Pose pose) { float size = Math.max(0.5F, 0.15F * sporeSize()); return EntityDimensions.scalable(size, size); }
    @Override public void tick() {
        super.tick();
        if (level().isClientSide || burst || !isAlive()) return;
        if (sporeSize() < 10 && ++growth > 1200) { setSporeSize(sporeSize() + 1); growth = 0; }
        if (tickCount % 20 == 0 && !level().getBiome(blockPosition()).is(Tc4ModernWorldgen.TAINTED_LAND)) hurt(damageSources().magic(), 1.0F);
        var base = TcModernApi.runtime().flatMap(rt -> rt.hostBlock("Thaumcraft:blockTaintFibres", 4, net.minecraft.world.level.block.state.BlockState.class)).orElse(null);
        if (base != null && !level().getBlockState(blockPosition().below()).equals(base)) burst();
    }
    @Override public void playerTouch(Player player) { if (!level().isClientSide) burst(); }
    @Override public void die(DamageSource source) { if (!level().isClientSide) burst(); else super.die(source); }
    private void burst() {
        if (burst || !(level() instanceof ServerLevel server)) return;
        burst = true;
        int count = sporeSize() / 3 + random.nextInt(sporeSize() / 2 + 1);
        for (int i = 0; i < count; i++) {
            var spider = Tc4Entities.TAINT_SPIDER.get().create(server);
            if (spider != null) {
                spider.moveTo(getX() + random.nextFloat() - random.nextFloat(), getY() + random.nextFloat(), getZ() + random.nextFloat() - random.nextFloat(), random.nextFloat() * 360, 0);
                server.addFreshEntity(spider);
            }
        }
        TcModernApi.runtime().ifPresent(runtime -> {
            var pod = runtime.hostBlock("Thaumcraft:blockTaintFibres", 4, net.minecraft.world.level.block.state.BlockState.class).orElse(null);
            var fibres = runtime.hostBlock("Thaumcraft:blockTaintFibres", 3, net.minecraft.world.level.block.state.BlockState.class).orElse(null);
            var below = blockPosition().below();
            if (pod != null && fibres != null && level().getBlockState(below).equals(pod)) level().setBlock(below, fibres, 3);
        });
        discard();
    }
    @Override public void addAdditionalSaveData(CompoundTag tag) { super.addAdditionalSaveData(tag); tag.putInt("Tc4SporeSize", sporeSize()); tag.putInt("Tc4SporeGrowth", growth); }
    @Override public void readAdditionalSaveData(CompoundTag tag) { super.readAdditionalSaveData(tag); if (tag.contains("Tc4SporeSize")) setSporeSize(tag.getInt("Tc4SporeSize")); growth = tag.getInt("Tc4SporeGrowth"); }
    @LegacyType("thaumcraft.common.entities.monster.EntityTaintSpore") public interface OldSpore { @LegacyMethod(name="getSporeSize", descriptor="()I") int size(); }
}
