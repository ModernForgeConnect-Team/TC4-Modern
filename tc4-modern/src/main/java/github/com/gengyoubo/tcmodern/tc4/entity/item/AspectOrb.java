package github.com.gengyoubo.tcmodern.tc4.entity.item;

import github.com.gengyoubo.mfcl.api.LegacyFeatures;
import github.com.gengyoubo.mfcl.api.LegacyObject;
import github.com.gengyoubo.mfcl.api.LegacyRuntime;
import github.com.gengyoubo.mfcl.api.mla.LegacyField;
import github.com.gengyoubo.mfcl.api.mla.LegacyMethod;
import github.com.gengyoubo.mfcl.api.mla.LegacyType;
import github.com.gengyoubo.tcmodern.api.TcModernApi;
import github.com.gengyoubo.tcmodern.tc4.entity.LegacyHost;
import github.com.gengyoubo.tcmodern.tc4.entity.Tc4Legacy;
import github.com.gengyoubo.tcmodern.tc4.init.Tc4Entities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** A small orb of primal vis that flies to a nearby player and fills a wand in their hotbar that still has room. */
public final class AspectOrb extends Entity {
    static final String LEGACY_CLASS = "thaumcraft.common.entities.EntityAspectOrb";
    private static final EntityDataAccessor<String> ASPECT = SynchedEntityData.defineId(AspectOrb.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> AGE = SynchedEntityData.defineId(AspectOrb.class, EntityDataSerializers.INT);
    public static final int MAX_AGE = 150;
    private final LegacyHost legacy = new LegacyHost(LEGACY_CLASS);
    private int cooldown, health = 5, value;
    @Nullable private Player closest;

    public AspectOrb(EntityType<? extends AspectOrb> type, Level level) { super(type, level); }

    public static AspectOrb create(Level level, double x, double y, double z, String aspect, int value) {
        var orb = new AspectOrb(Tc4Entities.ASPECT_ORB.get(), level);
        orb.setPos(x, y, z);
        orb.setYRot(orb.random.nextFloat() * 360.0F);
        orb.setDeltaMovement((orb.random.nextFloat() * 0.2 - 0.1) * 2.0, orb.random.nextFloat() * 0.2 * 2.0, (orb.random.nextFloat() * 0.2 - 0.1) * 2.0);
        orb.entityData.set(ASPECT, aspect);
        orb.value = value;
        return orb;
    }

    public static void host(LegacyRuntime runtime) {
        Tc4Legacy.host(runtime, LEGACY_CLASS, (level, original) -> {
            var orb = Tc4Entities.ASPECT_ORB.get().create(level);
            if (orb == null) return null;
            var old = runtime.accessor(Old.class, original);
            var aspect = old.aspect();
            if (aspect == null) return null;
            orb.entityData.set(ASPECT, runtime.accessor(Aspects.class, aspect).tag());
            orb.value = old.value();
            return orb;
        });
    }

    @Override protected void defineSynchedData() { entityData.define(ASPECT, ""); entityData.define(AGE, 0); }
    public String aspect() { return entityData.get(ASPECT); }
    public int age() { return entityData.get(AGE); }
    public int value() { return value; }
    @Override protected MovementEmission getMovementEmission() { return MovementEmission.NONE; }
    @Override public boolean isAttackable() { return false; }
    @Override public boolean shouldRenderAtSqrDistance(double distance) { double d = 32.0 * getViewScale(); return distance < d * d; }

    @Override public void tick() {
        super.tick();
        if (cooldown > 0) cooldown--;
        xo = getX(); yo = getY(); zo = getZ();
        setDeltaMovement(getDeltaMovement().add(0, -0.03, 0));
        if (level().getFluidState(blockPosition()).is(FluidTags.LAVA)) {
            setDeltaMovement((random.nextFloat() - random.nextFloat()) * 0.2, 0.2, (random.nextFloat() - random.nextFloat()) * 0.2);
            playSound(SoundEvents.FIRE_EXTINGUISH, 0.4F, 2.0F + random.nextFloat() * 0.4F);
        }
        moveTowardsClosestSpace(getX(), (getBoundingBox().minY + getBoundingBox().maxY) / 2.0, getZ());
        if (!level().isClientSide) {
            if (tickCount % 5 == 0 && closest == null) {
                double best = Double.MAX_VALUE;
                for (var player : level().getEntitiesOfClass(Player.class, getBoundingBox().inflate(8.0))) {
                    double d = player.distanceToSqr(this);
                    if (d < best && wandSlot(player) >= 0) { best = d; closest = player; }
                }
            }
            if (closest != null && (!closest.isAlive() || closest.distanceToSqr(this) > 64)) closest = null;
            if (closest != null) {
                Vec3 to = new Vec3(closest.getX() - getX(), closest.getEyeY() - getY(), closest.getZ() - getZ()).scale(1.0 / 8.0);
                double length = to.length(), pull = 1.0 - length;
                if (pull > 0 && length > 0) setDeltaMovement(getDeltaMovement().add(to.scale(1.0 / length * pull * pull * 0.1)));
            }
        }
        move(MoverType.SELF, getDeltaMovement());
        float friction = onGround() ? level().getBlockState(getBlockPosBelowThatAffectsMyMovement()).getFriction(level(), getBlockPosBelowThatAffectsMyMovement(), this) * 0.98F : 0.98F;
        setDeltaMovement(getDeltaMovement().multiply(friction, 0.98, friction));
        if (onGround()) setDeltaMovement(getDeltaMovement().multiply(1, -0.9, 1));
        if (!level().isClientSide) {
            entityData.set(AGE, age() + 1);
            if (age() >= MAX_AGE) discard();
        }
        legacy.tick(this);
    }

    @Override public boolean hurt(DamageSource source, float amount) {
        if (isInvulnerableTo(source)) return false;
        markHurt();
        health -= (int) amount;
        if (health <= 0) discard();
        return false;
    }

    @Override public void playerTouch(Player player) {
        if (level().isClientSide || cooldown > 0 || player.takeXpDelay > 0) return;
        var aspect = Tc4Legacy.aspect(aspect());
        if (aspect == null || !aspect.primal()) return;
        if (fill(player)) {
            player.takeXpDelay = 2;
            playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.1F, 0.5F * ((random.nextFloat() - random.nextFloat()) * 0.7F + 1.8F));
            discard();
        }
    }

    /** InventoryUtils.isWandInHotbarWithRoom on the original player, or -1. */
    private int wandSlot(Player player) {
        var runtime = TcModernApi.runtime().orElse(null);
        if (runtime == null || !runtime.supports(LegacyFeatures.PLAYER) || aspect().isEmpty()) return -1;
        try {
            return runtime.withPlayer(player, old -> {
                var aspect = runtime.accessor(AspectLookup.class).get(aspect());
                return aspect == null ? -1 : runtime.accessor(Inventories.class).wandSlot(aspect, value, old);
            });
        } catch (RuntimeException failure) {
            return -1;
        }
    }

    /** ItemWandCasting.addVis on the hotbar wand; MFCL writes the changed wand back to the host inventory. */
    private boolean fill(Player player) {
        var runtime = TcModernApi.runtime().orElse(null);
        if (runtime == null || !runtime.supports(LegacyFeatures.PLAYER)) return false;
        try {
            return runtime.withPlayer(player, old -> {
                var aspect = runtime.accessor(AspectLookup.class).get(aspect());
                if (aspect == null) return false;
                int slot = runtime.accessor(Inventories.class).wandSlot(aspect, value, old);
                if (slot < 0) return false;
                var inventory = runtime.accessor(OldPlayer.class, old).inventory();
                var wand = runtime.accessor(OldInventory.class, inventory).get(slot);
                if (wand == null) return false;
                var item = runtime.accessor(OldStack.class, wand).item();
                runtime.accessor(Wands.class, item).addVis(wand, aspect, value, true);
                return true;
            });
        } catch (RuntimeException failure) {
            return false;
        }
    }

    @Override protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putShort("Health", (short) health); tag.putShort("Age", (short) age()); tag.putShort("Value", (short) value); tag.putString("Aspect", aspect());
        legacy.save(this, tag);
    }
    @Override protected void readAdditionalSaveData(CompoundTag tag) {
        health = tag.getShort("Health") & 255; entityData.set(AGE, (int) tag.getShort("Age")); value = tag.getShort("Value"); entityData.set(ASPECT, tag.getString("Aspect"));
        legacy.load(tag);
    }

    @LegacyType(LEGACY_CLASS) public interface Old {
        @LegacyMethod(name = "getAspect", descriptor = "()Lthaumcraft/api/aspects/Aspect;") LegacyObject aspect();
        @LegacyMethod(name = "getAspectValue", descriptor = "()I") int value();
    }
    @LegacyType("thaumcraft.api.aspects.Aspect") public interface Aspects {
        @LegacyMethod(name = "getTag", descriptor = "()Ljava/lang/String;") String tag();
    }
    /** Static members are bound without a receiver, so they cannot share a contract with instance members. */
    @LegacyType("thaumcraft.api.aspects.Aspect") public interface AspectLookup {
        @LegacyMethod(name = "getAspect", descriptor = "(Ljava/lang/String;)Lthaumcraft/api/aspects/Aspect;", isStatic = true) LegacyObject get(String tag);
    }
    @LegacyType("thaumcraft.common.lib.utils.InventoryUtils") public interface Inventories {
        @LegacyMethod(name = "isWandInHotbarWithRoom", descriptor = "(Lthaumcraft/api/aspects/Aspect;ILnet/minecraft/entity/player/EntityPlayer;)I", isStatic = true)
        int wandSlot(LegacyObject aspect, int amount, LegacyObject player);
    }
    @LegacyType("net.minecraft.entity.player.EntityPlayer") public interface OldPlayer {
        @LegacyField(name = "field_71071_by", descriptor = "Lnet/minecraft/entity/player/InventoryPlayer;") LegacyObject inventory();
    }
    @LegacyType("net.minecraft.entity.player.InventoryPlayer") public interface OldInventory {
        @LegacyMethod(name = "func_70301_a", descriptor = "(I)Lnet/minecraft/item/ItemStack;") LegacyObject get(int slot);
    }
    @LegacyType("net.minecraft.item.ItemStack") public interface OldStack {
        @LegacyMethod(name = "func_77973_b", descriptor = "()Lnet/minecraft/item/Item;") LegacyObject item();
    }
    @LegacyType("thaumcraft.common.items.wands.ItemWandCasting") public interface Wands {
        @LegacyMethod(name = "addVis", descriptor = "(Lnet/minecraft/item/ItemStack;Lthaumcraft/api/aspects/Aspect;IZ)I")
        int addVis(LegacyObject stack, LegacyObject aspect, int amount, boolean doit);
    }
}
