package github.com.gengyoubo.tcmodern.tc4.entity;

import github.com.gengyoubo.mfcl.api.LegacyRuntime;
import github.com.gengyoubo.mfcl.api.mla.LegacyMethod;
import github.com.gengyoubo.mfcl.api.mla.LegacyType;
import github.com.gengyoubo.tcmodern.tc4.init.Tc4Entities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.level.Level;

/**
 * A spider of the mind: warp sends harmless ones that only the haunted player can see; the real kind is a tiny, weak
 * spider. Neither drops anything or triggers pressure plates.
 */
public final class MindSpider extends Spider {
    static final String LEGACY_CLASS = "thaumcraft.common.entities.monster.EntityMindSpider";
    private static final EntityDataAccessor<Boolean> HARMLESS = SynchedEntityData.defineId(MindSpider.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<String> VIEWER = SynchedEntityData.defineId(MindSpider.class, EntityDataSerializers.STRING);
    private final LegacyHost legacy = new LegacyHost(LEGACY_CLASS);
    private int lifeSpan = Integer.MAX_VALUE;

    public MindSpider(EntityType<? extends MindSpider> type, Level level) { super(type, level); xpReward = 1; }

    public static AttributeSupplier.Builder attributes() { return Spider.createAttributes().add(Attributes.MAX_HEALTH, 1).add(Attributes.ATTACK_DAMAGE, 1); }

    public static void host(LegacyRuntime runtime) {
        Tc4Legacy.host(runtime, LEGACY_CLASS, (level, original) -> {
            var spider = Tc4Entities.MIND_SPIDER.get().create(level);
            if (spider == null) return null;
            var old = runtime.accessor(Old.class, original);
            spider.setHarmless(old.harmless());
            spider.setViewer(old.viewer());
            return spider;
        });
    }

    @Override protected void defineSynchedData() { super.defineSynchedData(); entityData.define(HARMLESS, false); entityData.define(VIEWER, ""); }
    public boolean harmless() { return entityData.get(HARMLESS); }
    public void setHarmless(boolean harmless) { if (harmless) lifeSpan = 1200; entityData.set(HARMLESS, harmless); if (harmless) xpReward = 0; }
    public String viewer() { return entityData.get(VIEWER); }
    public void setViewer(String name) { entityData.set(VIEWER, name == null ? "" : name); }

    @Override public float getVoicePitch() { return 0.7F; }
    @Override public void tick() {
        super.tick();
        if (!level().isClientSide && tickCount > lifeSpan) discard();
        legacy.tick(this);
    }
    @Override public boolean doHurtTarget(Entity target) { return !harmless() && super.doHurtTarget(target); }
    @Override public boolean isIgnoringBlockTriggers() { return true; }
    @Override protected MovementEmission getMovementEmission() { return MovementEmission.NONE; }

    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag); tag.putBoolean("harmless", harmless()); tag.putString("viewer", viewer()); legacy.save(this, tag);
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag); setHarmless(tag.getBoolean("harmless")); setViewer(tag.getString("viewer")); legacy.load(tag);
    }

    @LegacyType(LEGACY_CLASS) public interface Old {
        @LegacyMethod(name = "isHarmless", descriptor = "()Z") boolean harmless();
        @LegacyMethod(name = "getViewer", descriptor = "()Ljava/lang/String;") String viewer();
    }
}
