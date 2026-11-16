package github.com.gengyoubo.tcmodern.tc4.entity.projectile;

import github.com.gengyoubo.mfcl.api.LegacyRuntime;
import github.com.gengyoubo.mfcl.api.mla.LegacyField;
import github.com.gengyoubo.mfcl.api.mla.LegacyType;
import github.com.gengyoubo.tcmodern.tc4.entity.Tc4Legacy;
import github.com.gengyoubo.tcmodern.tc4.init.Tc4Entities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** The shock focus orb: shocks everything it can see around the impact and leaves sparking electric air. */
public final class ShockOrb extends Tc4Throwable {
    static final String LEGACY_CLASS = "thaumcraft.common.entities.projectile.EntityShockOrb";
    public int area = 4;
    public int damage = 5;

    public ShockOrb(EntityType<? extends ShockOrb> type, Level level) { super(type, level, LEGACY_CLASS); }

    public static void host(LegacyRuntime runtime) {
        Tc4Legacy.host(runtime, LEGACY_CLASS, (level, original) -> {
            var orb = Tc4Entities.SHOCK_ORB.get().create(level);
            if (orb == null) return null;
            var old = runtime.accessor(Old.class, original);
            orb.area = Math.max(1, old.area()); orb.damage = old.damage();
            orb.setOwner(Tc4Legacy.thrower(runtime, level, original));
            return orb;
        });
    }

    @Override protected float getGravity() { return 0.05F; }
    @Override protected int lifetime() { return 500; }
    @Override public boolean isPickable() { return true; }
    @Override public boolean hurt(DamageSource source, float amount) { return deflect(source, Tc4Legacy.sound("zap")); }

    private boolean sees(Vec3 target) {
        return level().clip(new ClipContext(position(), target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() == HitResult.Type.MISS;
    }

    @Override protected void onHit(HitResult hit) {
        if (level() instanceof ServerLevel server) {
            for (Entity entity : server.getEntities(this, getBoundingBox().inflate(area), e -> e.distanceTo(this) <= area))
                if (sees(entity.getBoundingBox().getCenter())) entity.hurt(damageSources().indirectMagic(this, getOwner()), damage);
            var electric = Tc4Legacy.block("Thaumcraft:blockAiry", 10);
            if (electric != null) for (int a = 0; a < 20; a++) {
                int x = Mth.floor(getX()) + random.nextInt(area) - random.nextInt(area);
                int y = Mth.floor(getY()) + area;
                int z = Mth.floor(getZ()) + random.nextInt(area) - random.nextInt(area);
                while (level().isEmptyBlock(new BlockPos(x, y, z)) && y > Mth.floor(getY()) - area) y--;
                var above = new BlockPos(x, y + 1, z);
                if (level().isEmptyBlock(above) && !level().isEmptyBlock(new BlockPos(x, y, z)) && sees(Vec3.atCenterOf(above)))
                    level().setBlock(above, electric, 3);
            }
            server.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY(), getZ(), 40, 0.6, 0.6, 0.6, 0.3);
            level().playSound(null, getX(), getY(), getZ(), Tc4Legacy.sound("shock"), getSoundSource(), 1.0F, 1.0F + (random.nextFloat() - random.nextFloat()) * 0.2F);
        }
        discard();
    }

    @Override public void addAdditionalSaveData(CompoundTag tag) { super.addAdditionalSaveData(tag); tag.putInt("area", area); tag.putInt("damage", damage); }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag); if (tag.contains("area")) area = Math.max(1, tag.getInt("area")); if (tag.contains("damage")) damage = tag.getInt("damage");
    }

    @LegacyType(LEGACY_CLASS) public interface Old {
        @LegacyField(name = "area", descriptor = "I") int area();
        @LegacyField(name = "damage", descriptor = "I") int damage();
    }
}
