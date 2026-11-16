package github.com.gengyoubo.tcmodern.tc4.entity.projectile;

import github.com.gengyoubo.mfcl.api.LegacyRuntime;
import github.com.gengyoubo.mfcl.api.mla.LegacyField;
import github.com.gengyoubo.mfcl.api.mla.LegacyType;
import github.com.gengyoubo.tcmodern.tc4.entity.Pech;
import github.com.gengyoubo.tcmodern.tc4.entity.Tc4Legacy;
import github.com.gengyoubo.tcmodern.tc4.init.Tc4Entities;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;

/** The pech mage's curse, also cast by the pech focus: poison, slowness or weakness on everything nearby. */
public final class PechBlast extends Tc4Throwable {
    static final String LEGACY_CLASS = "thaumcraft.common.entities.projectile.EntityPechBlast";
    public int strength, duration;
    public boolean nightshade;

    public PechBlast(EntityType<? extends PechBlast> type, Level level) { super(type, level, LEGACY_CLASS); }

    public static PechBlast create(Level level, LivingEntity thrower, int strength, int duration, boolean nightshade) {
        var blast = new PechBlast(Tc4Entities.PECH_BLAST.get(), level);
        blast.throwFrom(thrower, 1.5F, 0.0F, 1.0F);
        blast.strength = strength; blast.duration = duration; blast.nightshade = nightshade;
        return blast;
    }

    public static void host(LegacyRuntime runtime) {
        Tc4Legacy.host(runtime, LEGACY_CLASS, (level, original) -> {
            var blast = Tc4Entities.PECH_BLAST.get().create(level);
            if (blast == null) return null;
            var old = runtime.accessor(Old.class, original);
            blast.strength = old.strength(); blast.duration = old.duration(); blast.nightshade = old.nightshade();
            blast.setOwner(Tc4Legacy.thrower(runtime, level, original));
            return blast;
        });
    }

    @Override protected float getGravity() { return 0.025F; }
    @Override protected int lifetime() { return 500; }

    @Override public void tick() {
        if (level().isClientSide) for (int a = 0; a < 3; a++) level().addParticle(ParticleTypes.WITCH,
                (getX() + xo) / 2 + (random.nextFloat() - random.nextFloat()) * 0.2, (getY() + yo) / 2 + (random.nextFloat() - random.nextFloat()) * 0.2,
                (getZ() + zo) / 2 + (random.nextFloat() - random.nextFloat()) * 0.2, 0, 0, 0);
        super.tick();
    }

    @Override protected void onHit(HitResult hit) {
        if (!(level() instanceof ServerLevel server)) return;
        server.sendParticles(ParticleTypes.WITCH, getX(), getY(), getZ(), 27, 0.3, 0.3, 0.3, 0.1);
        int time = 100 + duration * 40;
        for (var entity : server.getEntities(getOwner(), getBoundingBox().inflate(2.0))) {
            if (entity instanceof Pech || !(entity instanceof LivingEntity living)) continue;
            living.hurt(damageSources().thrown(this, getOwner()), strength + 2);
            if (nightshade) {
                living.addEffect(new MobEffectInstance(MobEffects.POISON, time, strength));
                living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, time, strength + 1));
                living.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, time, strength));
            } else switch (random.nextInt(3)) {
                case 0 -> living.addEffect(new MobEffectInstance(MobEffects.POISON, time, strength));
                case 1 -> living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, time, strength + 1));
                default -> living.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, time, strength));
            }
        }
        discard();
    }

    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag); tag.putInt("strength", strength); tag.putInt("duration", duration); tag.putBoolean("nightshade", nightshade);
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag); strength = tag.getInt("strength"); duration = tag.getInt("duration"); nightshade = tag.getBoolean("nightshade");
    }

    @LegacyType(LEGACY_CLASS) public interface Old {
        @LegacyField(name = "strength", descriptor = "I") int strength();
        @LegacyField(name = "duration", descriptor = "I") int duration();
        @LegacyField(name = "nightshade", descriptor = "Z") boolean nightshade();
    }
}
