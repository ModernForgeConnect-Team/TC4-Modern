package github.com.gengyoubo.tcmodern.tc4.entity;

import github.com.gengyoubo.mfcl.api.LegacyRuntime;
import github.com.gengyoubo.tcmodern.api.TcModernApi;
import github.com.gengyoubo.tcmodern.tc4.init.Tc4Entities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.level.Level;

/** Small TC4 tainted spider, including the MFCL identity of old-created instances. */
public final class TaintSpider extends Spider {
    private static final String LEGACY_CLASS = "thaumcraft.common.entities.monster.EntityTaintSpider";
    private static final String LEGACY_DATA = "Tc4LegacyTaintSpider";
    private byte[] pendingLegacyData;
    public TaintSpider(EntityType<? extends Spider> type, Level level) { super(type, level); xpReward = 2; }
    public static AttributeSupplier.Builder attributes() {
        return Spider.createAttributes().add(Attributes.MAX_HEALTH, 5).add(Attributes.ATTACK_DAMAGE, 2);
    }
    public static void registerLegacyHost(LegacyRuntime runtime) {
        runtime.entityHosts().register(LEGACY_CLASS, (level, original) -> Tc4Entities.TAINT_SPIDER.get().create((ServerLevel) level));
    }
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
