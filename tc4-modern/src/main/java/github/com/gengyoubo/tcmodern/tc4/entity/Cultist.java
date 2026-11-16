package github.com.gengyoubo.tcmodern.tc4.entity;

import github.com.gengyoubo.mfcl.api.LegacyObject;
import github.com.gengyoubo.mfcl.api.LegacyRuntime;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import org.jetbrains.annotations.Nullable;

/** The Crimson Cult's foot soldiers: they open doors, stay near their camp and never fight each other. */
public abstract class Cultist extends Monster {
    protected final LegacyHost legacy;

    protected Cultist(EntityType<? extends Cultist> type, Level level, String legacyClass) {
        super(type, level);
        legacy = new LegacyHost(legacyClass);
        xpReward = 10;
        if (getNavigation() instanceof GroundPathNavigation ground) { ground.setCanOpenDoors(true); ground.setCanPassDoors(true); }
    }

    public static AttributeSupplier.Builder cultistAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.FOLLOW_RANGE, 32).add(Attributes.MOVEMENT_SPEED, 0.3).add(Attributes.ATTACK_DAMAGE, 4);
    }

    /** setCurrentItemOrArmor with a TC4 item, never dropped. */
    protected void wear(EquipmentSlot slot, String key) {
        var stack = Tc4Legacy.stack(key, 0, 1);
        if (!stack.isEmpty()) { setItemSlot(slot, stack); setDropChance(slot, 0.0F); }
    }

    protected abstract void equip(DifficultyInstance difficulty);

    /** For a host of an original: its equipment and camp when it had them, otherwise a freshly equipped cultist. */
    protected void adopt(LegacyRuntime runtime, LegacyObject original) {
        if (!Tc4Legacy.copyEquipment(runtime, original, this)) equip(level().getCurrentDifficultyAt(blockPosition()));
        for (var slot : EquipmentSlot.values()) setDropChance(slot, 0.0F);
        Tc4Legacy.copyHome(runtime, original, this);
        Tc4Legacy.copyName(runtime, original, this);
    }

    @Override public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason,
            @Nullable SpawnGroupData group, @Nullable CompoundTag tag) {
        var result = super.finalizeSpawn(level, difficulty, reason, group, tag);
        equip(difficulty);
        return result;
    }

    @Override protected void populateDefaultEquipmentSlots(net.minecraft.util.RandomSource random, DifficultyInstance difficulty) { }
    @Override public boolean canPickUpLoot() { return false; }

    @Override protected void dropCustomDeathLoot(DamageSource source, int looting, boolean recentlyHit) {
        int r = random.nextInt(10);
        if (r == 0) Tc4Legacy.drop(this, Tc4Legacy.stack("Thaumcraft:ItemResource", 9, 1), 1.5F);
        else if (r <= 1) Tc4Legacy.drop(this, Tc4Legacy.stack("Thaumcraft:ItemResource", 17, 1), 1.5F);
        else if (r <= 3 + looting) Tc4Legacy.drop(this, Tc4Legacy.stack("Thaumcraft:ItemResource", 18, 1), 1.5F);
        super.dropCustomDeathLoot(source, looting, recentlyHit);
        if (recentlyHit && random.nextFloat() < 0.025F + looting * 0.01F) Tc4Legacy.drop(this, Tc4Legacy.stack("Thaumcraft:ItemEldritchObject", 1, 1), 1.0F);
    }

    @Override public boolean isAlliedTo(Entity other) { return other instanceof Cultist || other instanceof CultistLeader || super.isAlliedTo(other); }
    @Override public boolean canAttack(LivingEntity target) { return !(target instanceof Cultist) && !(target instanceof CultistLeader) && super.canAttack(target); }

    @Override public void tick() { super.tick(); legacy.tick(this); }
    @Override public void addAdditionalSaveData(CompoundTag tag) { super.addAdditionalSaveData(tag); Tc4Legacy.saveHome(this, tag); legacy.save(this, tag); }
    @Override public void readAdditionalSaveData(CompoundTag tag) { super.readAdditionalSaveData(tag); Tc4Legacy.loadHome(this, tag); legacy.load(tag); }
}
