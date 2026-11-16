package github.com.gengyoubo.tcmodern.tc4.entity;

import github.com.gengyoubo.mfcl.api.LegacyRuntime;
import github.com.gengyoubo.tcmodern.api.TcModernApi;
import github.com.gengyoubo.tcmodern.tc4.init.Tc4Entities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.Level;

/** TC4's brainy zombie, using the host game's zombie movement and combat. */
public class BrainyZombie extends Zombie {
    private static final String LEGACY_CLASS = "thaumcraft.common.entities.monster.EntityBrainyZombie";
    private static final String LEGACY_DATA = "Tc4LegacyBrainyZombie";
    private byte[] pendingLegacyData;

    public BrainyZombie(EntityType<? extends Zombie> type, Level level) {
        super(type, level);
        xpReward = 8;
    }

    public static void registerLegacyHost(LegacyRuntime runtime) {
        runtime.entityHosts().register(LEGACY_CLASS, (level, original) ->
                Tc4Entities.BRAINY_ZOMBIE.get().create((ServerLevel) level));
    }

    public static AttributeSupplier.Builder attributes() {
        return Zombie.createAttributes()
                .add(Attributes.MAX_HEALTH, 25.0)
                .add(Attributes.ATTACK_DAMAGE, 5.0)
                .add(Attributes.SPAWN_REINFORCEMENTS_CHANCE, 0.0);
    }

    @Override public void tick() {
        super.tick();
        if (level().isClientSide) return;
        TcModernApi.runtime().ifPresent(runtime -> {
            var hosts = runtime.entityHosts();
            if (pendingLegacyData != null) {
                hosts.restore(this, LEGACY_CLASS, pendingLegacyData);
                pendingLegacyData = null;
            }
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
