package github.com.gengyoubo.tcmodern.tc4.entity;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.monster.RangedAttackMob;

/** TC4's own AI tasks where vanilla has no exact equivalent. */
public final class Tc4Goals {
    private Tc4Goals() { }

    /** AILongRangeAttack: a ranged attack that is only used beyond {@code minDistance}. */
    public static final class LongRangeAttack<T extends Mob & RangedAttackMob> extends RangedAttackGoal {
        private final T mob;
        private final double minDistance;

        public LongRangeAttack(T mob, double minDistance, double speed, int minInterval, int maxInterval, float range) {
            super(mob, speed, minInterval, maxInterval, range);
            this.mob = mob;
            this.minDistance = minDistance;
        }

        @Override public boolean canUse() {
            if (!super.canUse()) return false;
            var target = mob.getTarget();
            if (target == null) return false;
            if (!target.isAlive()) { mob.setTarget(null); return false; }
            return mob.distanceToSqr(target.getX(), target.getBoundingBox().minY, target.getZ()) >= minDistance * minDistance;
        }
    }

    /**
     * AICultistHurtByTarget: retaliates and calls every cultist within range to help, except that a cleric absorbed in
     * a ritual only joins one time in three.
     */
    public static final class CultistHurtByTarget extends HurtByTargetGoal {
        public CultistHurtByTarget(PathfinderMob mob) { super(mob); }

        @Override protected void alertOthers() {
            var attacker = mob.getLastHurtByMob();
            if (attacker == null) return;
            double range = getFollowDistance();
            for (var cultist : mob.level().getEntitiesOfClass(Cultist.class, mob.getBoundingBox().inflate(range, 10.0, range))) {
                if (cultist == mob || cultist.getTarget() != null || cultist.isAlliedTo(attacker)) continue;
                if (cultist instanceof CultistCleric cleric && cleric.isRitualist()) {
                    if (mob.getRandom().nextInt(3) != 0) continue;
                    cleric.setRitualist(false);
                }
                cultist.setTarget(attacker);
            }
        }

        @Override public void start() { super.start(); alertOthers(); }
    }
}
