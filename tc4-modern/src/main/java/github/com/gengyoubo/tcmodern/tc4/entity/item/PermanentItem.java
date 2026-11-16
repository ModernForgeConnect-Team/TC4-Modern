package github.com.gengyoubo.tcmodern.tc4.entity.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/** A special item that never despawns; TC4 uses it for drops that must not be lost, such as eldritch keys. */
public final class PermanentItem extends SpecialItem {
    static final String LEGACY_CLASS = "thaumcraft.common.entities.EntityPermanentItem";

    public PermanentItem(EntityType<? extends PermanentItem> type, Level level) { super(type, level, LEGACY_CLASS); setUnlimitedLifetime(); }

    @Override public void readAdditionalSaveData(CompoundTag tag) { super.readAdditionalSaveData(tag); setUnlimitedLifetime(); }
}
