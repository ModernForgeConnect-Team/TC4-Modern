package github.com.gengyoubo.tcmodern.tc4.entity.projectile;

import github.com.gengyoubo.mfcl.api.LegacyRuntime;
import github.com.gengyoubo.tcmodern.tc4.entity.Tc4Legacy;
import github.com.gengyoubo.tcmodern.tc4.init.Tc4Entities;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;

/** Thrown alumentum: a small explosion that breaks blocks only when mob griefing is on. */
public final class Alumentum extends Tc4Throwable {
    static final String LEGACY_CLASS = "thaumcraft.common.entities.projectile.EntityAlumentum";

    public Alumentum(EntityType<? extends Alumentum> type, Level level) { super(type, level, LEGACY_CLASS); }

    public static void host(LegacyRuntime runtime) {
        Tc4Legacy.host(runtime, LEGACY_CLASS, (level, original) -> {
            var alumentum = Tc4Entities.ALUMENTUM.get().create(level);
            if (alumentum != null) alumentum.setOwner(Tc4Legacy.thrower(runtime, level, original));
            return alumentum;
        });
    }

    @Override public void tick() {
        super.tick();
        if (level().isClientSide) for (int a = 0; a < 3; a++) {
            level().addParticle(ParticleTypes.FLAME, getX() + (random.nextFloat() - random.nextFloat()) * 0.3, getY() + (random.nextFloat() - random.nextFloat()) * 0.3,
                    getZ() + (random.nextFloat() - random.nextFloat()) * 0.3, 0, 0, 0);
            level().addParticle(ParticleTypes.SMALL_FLAME, (getX() + xo) / 2, (getY() + yo) / 2, (getZ() + zo) / 2, 0, 0.02, 0);
        }
    }

    @Override protected void onHit(HitResult hit) {
        if (level().isClientSide) return;
        level().explode(null, getX(), getY(), getZ(), 1.66F, Level.ExplosionInteraction.MOB);
        discard();
    }
}
