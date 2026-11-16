package github.com.gengyoubo.tcmodern.tc4.entity.projectile;

import github.com.gengyoubo.mfcl.api.LegacyRuntime;
import github.com.gengyoubo.tcmodern.tc4.entity.Tc4Legacy;
import github.com.gengyoubo.tcmodern.tc4.init.Tc4Entities;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;

/** The eldritch guardian's and warden's dark orb: weakens and hurts the living, but never the undead. */
public final class EldritchOrb extends Tc4Throwable {
    static final String LEGACY_CLASS = "thaumcraft.common.entities.projectile.EntityEldritchOrb";

    public EldritchOrb(EntityType<? extends EldritchOrb> type, Level level) { super(type, level, LEGACY_CLASS); }

    public static EldritchOrb create(Level level, LivingEntity thrower) {
        var orb = new EldritchOrb(Tc4Entities.ELDRITCH_ORB.get(), level);
        orb.throwFrom(thrower, 1.5F, 0.0F, 1.0F);
        return orb;
    }

    public static void host(LegacyRuntime runtime) {
        Tc4Legacy.host(runtime, LEGACY_CLASS, (level, original) -> {
            var orb = Tc4Entities.ELDRITCH_ORB.get().create(level);
            if (orb != null) orb.setOwner(Tc4Legacy.thrower(runtime, level, original));
            return orb;
        });
    }

    @Override protected float getGravity() { return 0.0F; }
    @Override protected int lifetime() { return 100; }

    @Override public void handleEntityEvent(byte event) {
        if (event != 16) { super.handleEntityEvent(event); return; }
        for (int a = 0; a < 30; a++) {
            float fx = (random.nextFloat() - random.nextFloat()) * 0.3F, fy = (random.nextFloat() - random.nextFloat()) * 0.3F,
                    fz = (random.nextFloat() - random.nextFloat()) * 0.3F;
            level().addParticle(ParticleTypes.PORTAL, getX() + fx, getY() + fy, getZ() + fz, fx * 2, fy * 2, fz * 2);
        }
    }

    @Override protected void onHit(HitResult hit) {
        if (level().isClientSide || !(getOwner() instanceof LivingEntity owner)) return;
        var attack = owner.getAttribute(Attributes.ATTACK_DAMAGE);
        float damage = (float) (attack == null ? 2.0 : attack.getValue()) * 0.666F;
        for (var entity : level().getEntities(owner, getBoundingBox().inflate(2.0))) {
            if (!(entity instanceof LivingEntity living) || living.isInvertedHealAndHarm()) continue;
            living.hurt(damageSources().indirectMagic(this, owner), damage);
            living.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 160, 0));
        }
        playSound(SoundEvents.FIRE_EXTINGUISH, 0.5F, 2.6F + (random.nextFloat() - random.nextFloat()) * 0.8F);
        tickCount = 100;
        level().broadcastEntityEvent(this, (byte) 16);
    }
}
