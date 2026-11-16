package github.com.gengyoubo.tcmodern.tc4.entity.projectile;

import github.com.gengyoubo.mfcl.api.LegacyRuntime;
import github.com.gengyoubo.mfcl.api.mla.LegacyField;
import github.com.gengyoubo.mfcl.api.mla.LegacyType;
import github.com.gengyoubo.tcmodern.tc4.entity.LegacyHost;
import github.com.gengyoubo.tcmodern.tc4.entity.Tc4Legacy;
import github.com.gengyoubo.tcmodern.tc4.init.Tc4Entities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;

/**
 * The bone bow's primal arrows: 0 air (armour-piercing), 1 fire, 2 water (slowness), 3 earth (heavy), 4 order
 * (armour-piercing, weakness), 5 entropy (wither). They vanish five seconds after sticking.
 */
public final class PrimalArrow extends AbstractArrow {
    static final String LEGACY_CLASS = "thaumcraft.common.entities.projectile.EntityPrimalArrow";
    private static final EntityDataAccessor<Integer> TYPE = SynchedEntityData.defineId(PrimalArrow.class, EntityDataSerializers.INT);
    private final LegacyHost legacy = new LegacyHost(LEGACY_CLASS);

    public PrimalArrow(EntityType<? extends PrimalArrow> type, Level level) { super(type, level); setBaseDamage(2.1); pickup = Pickup.DISALLOWED; }

    public static void host(LegacyRuntime runtime) {
        Tc4Legacy.host(runtime, LEGACY_CLASS, (level, original) -> {
            var arrow = Tc4Entities.PRIMAL_ARROW.get().create(level);
            if (arrow == null) return null;
            var old = runtime.accessor(Old.class, original);
            arrow.entityData.set(TYPE, Math.max(0, Math.min(5, old.type())));
            arrow.setCritArrow(runtime.accessor(Tc4Legacy.OldArrow.class, original).critical());
            arrow.setOwner(Tc4Legacy.shooter(runtime, level, original));
            if (old.burning()) arrow.setSecondsOnFire(100);
            return arrow;
        });
    }

    @Override protected void defineSynchedData() { super.defineSynchedData(); entityData.define(TYPE, 0); }
    public int arrowType() { return entityData.get(TYPE); }

    private double typeDamage() {
        return switch (arrowType()) { case 3 -> getBaseDamage() * 1.5; case 4, 5 -> getBaseDamage() * 0.8; default -> getBaseDamage(); };
    }

    @Override protected void onHitEntity(EntityHitResult hit) {
        var target = hit.getEntity();
        var owner = getOwner();
        int damage = Mth.ceil(Mth.clamp(getDeltaMovement().length() * typeDamage(), 0.0, Integer.MAX_VALUE));
        if (isCritArrow()) damage += random.nextInt(damage / 2 + 2);
        int fire = isOnFire() && arrowType() != 2 ? 5 : 0;
        var attacker = owner == null ? this : owner;
        DamageSource source = switch (arrowType()) {
            case 0, 4 -> damageSources().indirectMagic(this, attacker);
            case 1 -> Tc4Legacy.damage(level(), DamageTypes.FIREBALL, this, attacker);
            default -> damageSources().arrow(this, attacker);
        };
        if (arrowType() == 1) fire += 5;
        if (target instanceof LivingEntity living) switch (arrowType()) {
            case 2 -> living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200, 4));
            case 4 -> living.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 4));
            case 5 -> living.addEffect(new MobEffectInstance(MobEffects.WITHER, 100));
            default -> { }
        }
        if (fire > 0 && !(target instanceof EnderMan)) target.setSecondsOnFire(fire);
        if (target.hurt(source, damage)) {
            if (target instanceof LivingEntity living) {
                if (!level().isClientSide && owner instanceof LivingEntity shooter) {
                    EnchantmentHelper.doPostHurtEffects(living, shooter);
                    EnchantmentHelper.doPostDamageEffects(shooter, living);
                }
                doPostHurtEffects(living);
            }
            playSound(SoundEvents.ARROW_HIT, 1.0F, 1.2F / (random.nextFloat() * 0.2F + 0.9F));
            if (!(target instanceof EnderMan)) discard();
        } else {
            setDeltaMovement(getDeltaMovement().scale(-0.1));
            setYRot(getYRot() + 180.0F);
            yRotO += 180.0F;
        }
    }

    private int stuck;
    @Override protected void tickDespawn() { if (++stuck >= 100) discard(); }

    @Override public void tick() { super.tick(); legacy.tick(this); }
    @Override protected ItemStack getPickupItem() { return ItemStack.EMPTY; }
    @Override public void playerTouch(net.minecraft.world.entity.player.Player player) { }
    @Override public void addAdditionalSaveData(CompoundTag tag) { super.addAdditionalSaveData(tag); tag.putByte("type", (byte) arrowType()); legacy.save(this, tag); }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag); entityData.set(TYPE, Math.max(0, Math.min(5, tag.getByte("type")))); legacy.load(tag);
    }

    @LegacyType(LEGACY_CLASS) public interface Old {
        @LegacyField(name = "type", descriptor = "I") int type();
        @github.com.gengyoubo.mfcl.api.mla.LegacyMethod(name = "func_70027_ad", descriptor = "()Z") boolean burning();
    }
}
