package github.com.gengyoubo.tcmodern.tc4.entity.item;

import github.com.gengyoubo.tcmodern.tc4.entity.LegacyHost;
import github.com.gengyoubo.tcmodern.tc4.entity.Tc4Legacy;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;

/** An item falling through TC4's arcane grate: it is not pushed back out while inside an open grate. */
public final class ItemGrate extends ItemEntity {
    static final String LEGACY_CLASS = "thaumcraft.common.entities.EntityItemGrate";
    private final LegacyHost legacy = new LegacyHost(LEGACY_CLASS);

    public ItemGrate(EntityType<? extends ItemGrate> type, Level level) { super(type, level); }

    private boolean inGrate(double x, double y, double z) {
        var state = level().getBlockState(BlockPos.containing(x, y, z));
        for (int meta = 5; meta <= 6; meta++) {
            var grate = Tc4Legacy.block("Thaumcraft:blockMetalDevice", meta);
            if (grate != null && grate.equals(state)) return true;
        }
        return false;
    }

    @Override protected void moveTowardsClosestSpace(double x, double y, double z) {
        if (!inGrate(x, y, z)) super.moveTowardsClosestSpace(x, y, z);
    }

    @Override public void tick() { super.tick(); legacy.tick(this); }
    @Override public void addAdditionalSaveData(CompoundTag tag) { super.addAdditionalSaveData(tag); legacy.save(this, tag); }
    @Override public void readAdditionalSaveData(CompoundTag tag) { super.readAdditionalSaveData(tag); legacy.load(tag); }
}
