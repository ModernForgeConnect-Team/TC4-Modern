package github.com.gengyoubo.tcmodern.tc4.entity;

import github.com.gengyoubo.tcmodern.tc4.init.Tc4Entities;
import github.com.gengyoubo.tcmodern.tc4.world.Tc4ModernWorldgen;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/** Rooted TC4 taintacle, with close attacks and small tentacles for distant targets. */
public class TaintTentacle extends TaintCow {
    static final String LEGACY_CLASS = "thaumcraft.common.entities.monster.EntityTaintacle";
    private int attackCooldown;
    public TaintTentacle(EntityType<? extends TaintTentacle> type, Level level) { super(type, level); setNoGravity(false); }
    @Override protected String legacyClass() { return LEGACY_CLASS; }
    public static AttributeSupplier.Builder tentacleAttributes() {
        return TaintCow.attributes().add(Attributes.MAX_HEALTH, 50).add(Attributes.ATTACK_DAMAGE, 7).add(Attributes.MOVEMENT_SPEED, 0);
    }
    @Override protected void registerGoals() { }
    @Override public void tick() {
        super.tick();
        if (level().isClientSide || !isAlive()) return;
        setDeltaMovement(0, Math.min(0, getDeltaMovement().y), 0);
        if (tickCount % 20 == 0 && !level().getBiome(blockPosition()).is(Tc4ModernWorldgen.TAINTED_LAND)) hurt(damageSources().magic(), 1);
        if (attackCooldown > 0) { attackCooldown--; return; }
        double height = getBbHeight();
        AABB search = getBoundingBox().inflate(height * 6, height * 3, height * 6);
        var target = level().getEntitiesOfClass(LivingEntity.class, search, e -> e != this && e.isAlive() && !(e instanceof TaintCow) && !(e instanceof TaintSpider)
                && (!(e instanceof net.minecraft.world.entity.player.Player p) || !p.isCreative() && !p.isSpectator()))
                .stream().min(java.util.Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
        if (target == null || !hasLineOfSight(target)) return;
        if (distanceTo(target) <= height && target.getBoundingBox().maxY > getBoundingBox().minY && target.getBoundingBox().minY < getBoundingBox().maxY) {
            attackCooldown = 20;
            doHurtTarget(target);
        } else if (!(this instanceof SmallTaintTentacle) && target.onGround() && level().getBiome(target.blockPosition()).is(Tc4ModernWorldgen.TAINTED_LAND)) {
            attackCooldown = 40;
            var small = Tc4Entities.SMALL_TAINT_TENTACLE.get().create(level());
            if (small != null && level().isEmptyBlock(target.blockPosition())) {
                small.moveTo(target.getX(), target.getY(), target.getZ(), 0, 0);
                level().addFreshEntity(small);
            }
        }
    }
    @Override public boolean hurt(DamageSource source, float amount) { return super.hurt(source, amount); }
}
