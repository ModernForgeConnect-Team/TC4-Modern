package github.com.gengyoubo.tcmodern.tc4.entity.projectile;

import github.com.gengyoubo.mfcl.api.LegacyRuntime;
import github.com.gengyoubo.tcmodern.tc4.entity.TaintSpread;
import github.com.gengyoubo.tcmodern.tc4.entity.Tc4Legacy;
import github.com.gengyoubo.tcmodern.tc4.init.Tc4Entities;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;

/** A thrown bottle of taint: poisons nearby creatures and taints the ground where it breaks. */
public final class BottleTaint extends Tc4Throwable implements ItemSupplier {
    static final String LEGACY_CLASS = "thaumcraft.common.entities.projectile.EntityBottleTaint";

    public BottleTaint(EntityType<? extends BottleTaint> type, Level level) { super(type, level, LEGACY_CLASS); }

    public static void host(LegacyRuntime runtime) {
        Tc4Legacy.host(runtime, LEGACY_CLASS, (level, original) -> {
            var bottle = Tc4Entities.BOTTLE_TAINT.get().create(level);
            if (bottle != null) bottle.setOwner(Tc4Legacy.thrower(runtime, level, original));
            return bottle;
        });
    }

    @Override public ItemStack getItem() { return Tc4Legacy.stack("Thaumcraft:ItemBottleTaint", 0, 1); }
    @Override protected float getGravity() { return 0.05F; }

    @Override protected void onHit(HitResult hit) {
        if (!(level() instanceof ServerLevel server)) return;
        var poison = Tc4Legacy.effect("potion.fluxtaint");
        if (poison != null) for (var living : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(5.0)))
            if (!TaintSpread.isTaintedMob(living) && !living.isInvertedHealAndHarm()) living.addEffect(new MobEffectInstance(poison, 100, 0, false, true));
        TaintSpread.splash(server, (int) getX(), (int) getY(), (int) getZ(), 5.0F, false);
        var bottle = getItem();
        if (!bottle.isEmpty()) server.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, bottle), getX(), getY(), getZ(), 8, 0.1, 0.1, 0.1, 0.1);
        server.sendParticles(ParticleTypes.WITCH, getX(), getY(), getZ(), 60, 1.5, 0.5, 1.5, 0.1);
        level().levelEvent(2002, blockPosition(), 0x8A2BE2);
        discard();
    }
}
