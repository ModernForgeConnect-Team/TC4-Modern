package github.com.gengyoubo.tcmodern.tc4.entity.item;

import github.com.gengyoubo.mfcl.api.LegacyObject;
import github.com.gengyoubo.mfcl.api.LegacyRuntime;
import github.com.gengyoubo.tcmodern.tc4.entity.LegacyHost;
import github.com.gengyoubo.tcmodern.tc4.entity.Tc4Legacy;
import github.com.gengyoubo.tcmodern.tc4.init.Tc4Entities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** TC4's glowing drop (primordial pearls, boss loot): it drifts upward, ignores explosions and is drawn with light rays. */
public class SpecialItem extends ItemEntity {
    static final String LEGACY_CLASS = "thaumcraft.common.entities.EntitySpecialItem";
    protected final LegacyHost legacy;

    public SpecialItem(EntityType<? extends SpecialItem> type, Level level) { this(type, level, LEGACY_CLASS); }
    protected SpecialItem(EntityType<? extends SpecialItem> type, Level level, String legacyClass) { super(type, level); legacy = new LegacyHost(legacyClass); }

    /** EntityUtils.entityDropSpecialItem: the item pops up from {@code height} above the entity's feet. */
    public static SpecialItem drop(ServerLevel level, double x, double y, double z, ItemStack stack) {
        var item = new SpecialItem(Tc4Entities.SPECIAL_ITEM.get(), level);
        item.launch(x, y, z, stack);
        level.addFreshEntity(item);
        return item;
    }

    protected void launch(double x, double y, double z, ItemStack stack) {
        setPos(x, y, z);
        setItem(stack);
        setYRot(random.nextFloat() * 360.0F);
        setDeltaMovement(random.nextFloat() * 0.2 - 0.1, 0.2, random.nextFloat() * 0.2 - 0.1);
        setDefaultPickUpDelay();
    }

    public static void host(LegacyRuntime runtime) {
        Tc4Legacy.host(runtime, LEGACY_CLASS, (level, original) -> copy(runtime, original, Tc4Entities.SPECIAL_ITEM.get().create(level)));
        Tc4Legacy.host(runtime, PermanentItem.LEGACY_CLASS, (level, original) -> copy(runtime, original, Tc4Entities.PERMANENT_ITEM.get().create(level)));
        Tc4Legacy.host(runtime, ItemGrate.LEGACY_CLASS, (level, original) -> copy(runtime, original, Tc4Entities.ITEM_GRATE.get().create(level)));
    }

    static <T extends ItemEntity> T copy(LegacyRuntime runtime, LegacyObject original, T host) {
        if (host == null) return null;
        var old = runtime.accessor(Tc4Legacy.OldItem.class, original);
        var stack = Tc4Legacy.hostStack(runtime, old.item());
        if (stack.isEmpty()) return null;
        host.setItem(stack);
        host.setPickUpDelay(Math.max(0, old.pickupDelay()));
        return host;
    }

    @Override public void tick() {
        var motion = getDeltaMovement();
        double my = motion.y > 0 ? motion.y * 0.9 : motion.y;
        setDeltaMovement(motion.x, my + 0.04, motion.z);
        super.tick();
        legacy.tick(this);
    }

    @Override public boolean hurt(DamageSource source, float amount) { return !source.is(DamageTypeTags.IS_EXPLOSION) && super.hurt(source, amount); }
    @Override public boolean ignoreExplosion() { return true; }
    @Override public void addAdditionalSaveData(CompoundTag tag) { super.addAdditionalSaveData(tag); legacy.save(this, tag); }
    @Override public void readAdditionalSaveData(CompoundTag tag) { super.readAdditionalSaveData(tag); legacy.load(tag); }
}
