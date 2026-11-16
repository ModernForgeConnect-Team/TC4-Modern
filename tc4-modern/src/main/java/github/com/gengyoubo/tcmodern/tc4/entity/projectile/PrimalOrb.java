package github.com.gengyoubo.tcmodern.tc4.entity.projectile;

import com.mojang.logging.LogUtils;
import github.com.gengyoubo.mfcl.api.LegacyMappings;
import github.com.gengyoubo.mfcl.api.LegacyObject;
import github.com.gengyoubo.mfcl.api.LegacyRuntime;
import github.com.gengyoubo.mfcl.api.mla.LegacyField;
import github.com.gengyoubo.mfcl.api.mla.LegacyMethod;
import github.com.gengyoubo.mfcl.api.mla.LegacyType;
import github.com.gengyoubo.tcmodern.api.TcModernApi;
import github.com.gengyoubo.tcmodern.tc4.entity.TaintSpread;
import github.com.gengyoubo.tcmodern.tc4.entity.Tc4Legacy;
import github.com.gengyoubo.tcmodern.tc4.init.Tc4Entities;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/** The primal focus orb: a wandering (or seeking) explosion that sometimes taints the land or leaves an aura node. */
public final class PrimalOrb extends Tc4Throwable {
    static final String LEGACY_CLASS = "thaumcraft.common.entities.projectile.EntityPrimalOrb";
    private static final EntityDataAccessor<Boolean> SEEKER = SynchedEntityData.defineId(PrimalOrb.class, EntityDataSerializers.BOOLEAN);
    private int count;
    private int ownerId = -1;

    public PrimalOrb(EntityType<? extends PrimalOrb> type, Level level) { super(type, level, LEGACY_CLASS); }

    public static void host(LegacyRuntime runtime) {
        Tc4Legacy.host(runtime, LEGACY_CLASS, (level, original) -> {
            var orb = Tc4Entities.PRIMAL_ORB.get().create(level);
            if (orb == null) return null;
            orb.entityData.set(SEEKER, runtime.accessor(Old.class, original).seeker());
            var owner = Tc4Legacy.thrower(runtime, level, original);
            orb.setOwner(owner);
            if (owner != null) orb.ownerId = owner.getId();
            return orb;
        });
    }

    @Override protected void defineSynchedData() { entityData.define(SEEKER, false); }
    public boolean seeker() { return entityData.get(SEEKER); }
    @Override protected float getGravity() { return 0.001F; }
    @Override protected int lifetime() { return 5000; }

    @Override public void tick() {
        count++;
        if (level().isClientSide) for (int a = 0; a < 2; a++) level().addParticle(ParticleTypes.END_ROD,
                getX() + (random.nextFloat() - random.nextFloat()) * 0.2, getY() + (random.nextFloat() - random.nextFloat()) * 0.2,
                getZ() + (random.nextFloat() - random.nextFloat()) * 0.2, 0, 0, 0);
        if (tickCount > 20) {
            if (!seeker()) {
                RandomSource drift = RandomSource.create(getId() + count);
                setDeltaMovement(getDeltaMovement().add((drift.nextFloat() - drift.nextFloat()) * 0.01, (drift.nextFloat() - drift.nextFloat()) * 0.01,
                        (drift.nextFloat() - drift.nextFloat()) * 0.01));
            } else if (!level().isClientSide) seek();
        }
        super.tick();
    }

    private void seek() {
        if (ownerId < 0 && getOwner() != null) ownerId = getOwner().getId();
        LivingEntity closest = null;
        double best = Double.MAX_VALUE;
        for (var living : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(16), e -> e.getId() != ownerId && e.isAlive())) {
            double d = distanceToSqr(living);
            if (d < best && d <= 256) { best = d; closest = living; }
        }
        if (closest == null || best <= 0) return;
        double dx = (closest.getX() - getX()) / best, dy = (closest.getY() + closest.getBbHeight() * 0.9 - getY()) / best, dz = (closest.getZ() - getZ()) / best;
        var motion = getDeltaMovement().add(dx * 0.2, dy * 0.2, dz * 0.2);
        setDeltaMovement(Mth.clamp(motion.x, -0.2, 0.2), Mth.clamp(motion.y, -0.2, 0.2), Mth.clamp(motion.z, -0.2, 0.2));
    }

    @Override protected void onHit(HitResult hit) {
        if (!(level() instanceof ServerLevel server)) return;
        server.sendParticles(ParticleTypes.END_ROD, getX(), getY(), getZ(), 36, 0.5, 0.5, 0.5, 0.2);
        level().explode(null, getX(), getY(), getZ(), 2.0F, Level.ExplosionInteraction.TNT);
        if (!seeker() && random.nextInt(100) <= 1) {
            if (random.nextBoolean()) TaintSpread.splash(server, (int) getX(), (int) getY(), (int) getZ(), 6.0F, true);
            else if (hit instanceof BlockHitResult blockHit) createNode(server, blockHit);
        }
        discard();
    }

    /** ThaumcraftWorldGenerator.createRandomNodeAt in the original world, exactly as the orb did. */
    private void createNode(ServerLevel server, BlockHitResult hit) {
        TcModernApi.runtime().ifPresent(runtime -> {
            try {
                var world = runtime.mappings().toLegacy(LegacyMappings.WORLD, server).orElseThrow();
                var pos = hit.getBlockPos();
                runtime.accessor(Nodes.class).create(world, pos.getX(), pos.getY(), pos.getZ(), runtime.accessor(Randoms.class).create(server.random.nextLong()), false, false, true);
            } catch (RuntimeException failure) {
                LogUtils.getLogger().warn("The TC4 primal orb could not create an aura node", failure);
            }
        });
    }

    @Override public void addAdditionalSaveData(CompoundTag tag) { super.addAdditionalSaveData(tag); tag.putBoolean("seeker", seeker()); }
    @Override public void readAdditionalSaveData(CompoundTag tag) { super.readAdditionalSaveData(tag); entityData.set(SEEKER, tag.getBoolean("seeker")); }

    @LegacyType(LEGACY_CLASS) public interface Old { @LegacyField(name = "seeker", descriptor = "Z") boolean seeker(); }
    @LegacyType("thaumcraft.common.lib.world.ThaumcraftWorldGenerator") public interface Nodes {
        @LegacyMethod(name = "createRandomNodeAt", descriptor = "(Lnet/minecraft/world/World;IIILjava/util/Random;ZZZ)V", isStatic = true)
        void create(LegacyObject world, int x, int y, int z, LegacyObject random, boolean silverwood, boolean eerie, boolean small);
    }
    @LegacyType("java.util.Random") public interface Randoms {
        @github.com.gengyoubo.mfcl.api.mla.LegacyConstructor(descriptor = "(J)V") LegacyObject create(long seed);
    }
}
