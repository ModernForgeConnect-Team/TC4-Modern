package github.com.gengyoubo.tcmodern.tc4.entity.projectile;

import github.com.gengyoubo.mfcl.api.LegacyRuntime;
import github.com.gengyoubo.tcmodern.tc4.entity.LegacyHost;
import github.com.gengyoubo.tcmodern.tc4.entity.Tc4Legacy;
import github.com.gengyoubo.tcmodern.tc4.init.Tc4Entities;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** The small dart fired by golems with the dart launcher; it cannot be picked up. */
public final class Dart extends AbstractArrow {
    static final String LEGACY_CLASS = "thaumcraft.common.entities.projectile.EntityDart";
    private final LegacyHost legacy = new LegacyHost(LEGACY_CLASS);
    private boolean first = true;

    public Dart(EntityType<? extends Dart> type, Level level) { super(type, level); setBaseDamage(1.0); pickup = Pickup.DISALLOWED; }

    /** EntityDart(world, shooter, target, speed, spread): aimed at the target's upper body from the shooter's eyes. */
    public static Dart create(Level level, LivingEntity shooter, LivingEntity target, float speed, float spread) {
        var dart = new Dart(Tc4Entities.DART.get(), level);
        dart.setOwner(shooter);
        dart.setPos(shooter.getX(), shooter.getEyeY() - 0.1, shooter.getZ());
        double dx = target.getX() - shooter.getX(), dy = target.getEyeY() - 0.7 - dart.getY(), dz = target.getZ() - shooter.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        if (horizontal >= 1.0E-7) {
            dart.setPos(shooter.getX() + dx / horizontal / 5.0, dart.getY(), shooter.getZ() + dz / horizontal / 5.0);
            dart.shoot(dx, dy + horizontal * 0.2, dz, speed, spread);
        }
        return dart;
    }

    public static void host(LegacyRuntime runtime) {
        Tc4Legacy.host(runtime, LEGACY_CLASS, (level, original) -> {
            var dart = Tc4Entities.DART.get().create(level);
            if (dart == null) return null;
            var old = runtime.accessor(Tc4Legacy.OldArrow.class, original);
            dart.setBaseDamage(old.damage());
            dart.setCritArrow(old.critical());
            dart.setOwner(Tc4Legacy.shooter(runtime, level, original));
            dart.pickup = Pickup.DISALLOWED;
            return dart;
        });
    }

    @Override public void tick() {
        if (first && level().isClientSide) {
            first = false;
            var m = getDeltaMovement();
            for (int a = 0; a < 5; a++) level().addParticle(ParticleTypes.SMOKE, getX() - m.x / 1.5, getY() - m.y / 1.5, getZ() - m.z / 1.5,
                    m.x / 9 + random.nextGaussian() * 0.01, m.y / 9 + random.nextGaussian() * 0.01, m.z / 9 + random.nextGaussian() * 0.01);
        }
        super.tick();
        legacy.tick(this);
    }

    @Override protected ItemStack getPickupItem() { return ItemStack.EMPTY; }
    @Override public void addAdditionalSaveData(CompoundTag tag) { super.addAdditionalSaveData(tag); legacy.save(this, tag); }
    @Override public void readAdditionalSaveData(CompoundTag tag) { super.readAdditionalSaveData(tag); legacy.load(tag); }
}
