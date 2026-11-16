package github.com.gengyoubo.tcmodern.tc4.entity;

import github.com.gengyoubo.mfcl.api.LegacyObject;
import github.com.gengyoubo.mfcl.api.LegacyRuntime;
import github.com.gengyoubo.mfcl.api.mla.LegacyField;
import github.com.gengyoubo.mfcl.api.mla.LegacyMethod;
import github.com.gengyoubo.mfcl.api.mla.LegacyType;
import github.com.gengyoubo.tcmodern.tc4.init.Tc4Entities;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * The traveling trunk: a hopping, loyal chest that follows its owner and teleports to them when left behind. Golem
 * upgrades change it: air hops faster, earth adds a row, fire defends the owner, water makes it untouchable and
 * owner-only, order keeps its contents when rung away with a golem bell, entropy pulls in nearby items.
 */
public final class TravelingTrunk extends Mob implements OwnableEntity {
    static final String LEGACY_CLASS = "thaumcraft.common.entities.golems.EntityTravelingTrunk";
    public static final String PORTABLE = "tc4modern_trunk";
    public static final int AIR = 0, EARTH = 1, FIRE = 2, WATER = 3, ORDER = 4, ENTROPY = 5;
    private static final EntityDataAccessor<Boolean> OPEN = SynchedEntityData.defineId(TravelingTrunk.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> STAY = SynchedEntityData.defineId(TravelingTrunk.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> UPGRADE = SynchedEntityData.defineId(TravelingTrunk.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ANGER = SynchedEntityData.defineId(TravelingTrunk.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Optional<UUID>> OWNER = SynchedEntityData.defineId(TravelingTrunk.class, EntityDataSerializers.OPTIONAL_UUID);
    private final LegacyHost legacy = new LegacyHost(LEGACY_CLASS);
    private final SimpleContainer inventory = new SimpleContainer(36) {
        private int openers;
        @Override public void startOpen(Player player) { openers++; entityData.set(OPEN, true); }
        @Override public void stopOpen(Player player) { if (--openers <= 0) { openers = 0; entityData.set(OPEN, false); } }
        @Override public boolean stillValid(Player player) { return isAlive() && player.distanceToSqr(TravelingTrunk.this) < 64.0; }
    };
    public float lidrot, squish, previousSquish;
    private int jumpDelay, attackTime;

    public TravelingTrunk(EntityType<? extends TravelingTrunk> type, Level level) {
        super(type, level);
        jumpDelay = random.nextInt(20) + 10;
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder attributes() { return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 75).add(Attributes.ATTACK_DAMAGE, 4); }

    public static void host(LegacyRuntime runtime) {
        Tc4Legacy.host(runtime, LEGACY_CLASS, (level, original) -> {
            var trunk = Tc4Entities.TRAVELING_TRUNK.get().create(level);
            if (trunk == null) return null;
            var old = runtime.accessor(Old.class, original);
            trunk.setUpgrade(old.upgrade());
            trunk.entityData.set(STAY, old.stay());
            var name = old.ownerName();
            if (name != null && !name.isEmpty()) {
                var player = level.getServer().getPlayerList().getPlayerByName(name);
                if (player != null) trunk.setOwner(player.getUUID());
                else level.getServer().getProfileCache().get(name).ifPresent(profile -> trunk.setOwner(profile.getId()));
            }
            var slots = old.inventory();
            if (slots != null) {
                var inventory = runtime.accessor(OldInventory.class, slots);
                for (int i = 0; i < Math.min(36, inventory.size()); i++) trunk.inventory.setItem(i, Tc4Legacy.hostStack(runtime, inventory.get(i)));
            }
            Tc4Legacy.copyName(runtime, original, trunk);
            return trunk;
        });
    }

    @Override protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(OPEN, false); entityData.define(STAY, false); entityData.define(UPGRADE, -1); entityData.define(ANGER, 0); entityData.define(OWNER, Optional.empty());
    }
    public boolean isOpen() { return entityData.get(OPEN); }
    public boolean stays() { return entityData.get(STAY); }
    public int upgrade() { return entityData.get(UPGRADE); }
    public void setUpgrade(int upgrade) { entityData.set(UPGRADE, Math.max(-1, Math.min(5, upgrade))); }
    public int rows() { return upgrade() == EARTH ? 4 : 3; }
    public int anger() { return entityData.get(ANGER); }
    public SimpleContainer inventory() { return inventory; }
    @Override @Nullable public UUID getOwnerUUID() { return entityData.get(OWNER).orElse(null); }
    public void setOwner(@Nullable UUID owner) { entityData.set(OWNER, Optional.ofNullable(owner)); }
    private boolean isOwner(Player player) { return getOwnerUUID() == null || player.getUUID().equals(getOwnerUUID()); }

    @Override public boolean hurt(DamageSource source, float amount) {
        if (source.is(DamageTypes.CACTUS)) return false;
        if (upgrade() == WATER && !source.is(DamageTypes.FELL_OUT_OF_WORLD) && !source.is(DamageTypes.GENERIC_KILL)) return false;
        return super.hurt(source, amount);
    }
    @Override public boolean fireImmune() { return true; }
    @Override public int getMaxAirSupply() { return 10; }
    @Override public boolean canBreatheUnderwater() { return true; }
    @Override public boolean removeWhenFarAway(double distance) { return false; }
    @Override public boolean causeFallDamage(float distance, float multiplier, DamageSource source) { return false; }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.WOOD_HIT; }
    @Override protected SoundEvent getDeathSound() { return SoundEvents.ITEM_BREAK; }
    @Override protected float getSoundVolume() { return 0.5F; }

    @Override public void tick() {
        super.tick();
        if (isInWater()) setDeltaMovement(getDeltaMovement().add(0, 0.033, 0));
        if (level().isClientSide) {
            previousSquish = squish;
            squish *= 0.6F;
            if (!onGround() && getDeltaMovement().y < 0 && !isInWater()) lidrot += 0.015F;
            if ((onGround() || isInWater()) && !isOpen()) lidrot = Math.max(0.0F, lidrot - 0.1F);
            if (isOpen()) lidrot += 0.035F;
            lidrot = Math.min(lidrot, isOpen() ? 0.5F : 0.2F);
        } else if (getHealth() < getMaxHealth() && (upgrade() == WATER || tickCount % 50 == 0)) heal(1.0F);
        legacy.tick(this);
    }

    @Override public void aiStep() {
        super.aiStep();
        if (upgrade() == ENTROPY && !level().isClientSide) pullItems();
    }

    @Override protected void customServerAiStep() {
        if (anger() > 0) entityData.set(ANGER, anger() - 1);
        if (attackTime > 0) attackTime--;
        fallDistance = 0;
        var owner = getOwner();
        if (owner == null) { zza = xxa = 0; return; }
        if (!stays() && distanceTo(owner) > 20.0F && teleportToOwner(owner)) { setTarget(null); return; }
        if (getTarget() != null && !getTarget().isAlive()) { setTarget(null); entityData.set(ANGER, 5); }
        if (!stays() && upgrade() == FIRE && anger() == 0 && getTarget() == null) {
            var threat = owner.getLastHurtByMob();
            if (threat != null && threat.isAlive() && threat != this && hasLineOfSight(threat)) { entityData.set(ANGER, 600); setTarget(threat); }
        }
        boolean move = false;
        var target = getTarget();
        if (anger() > 0 && target != null && target.isAlive() && target != owner) {
            lookAt(target, 10.0F, 20.0F);
            move = true;
            if (attackTime <= 0 && distanceTo(target) < 1.5F && target.getBoundingBox().maxY > getBoundingBox().minY && target.getBoundingBox().minY < getBoundingBox().maxY) {
                attackTime = 10 + random.nextInt(5);
                target.hurt(damageSources().mobAttack(this), 4.0F);
                level().broadcastEntityEvent(this, (byte) 17);
                playSound(SoundEvents.BLAZE_HURT, 0.5F, random.nextFloat() * 0.1F + 0.9F);
            }
        }
        if (distanceTo(owner) > 5.0F && anger() == 0 && !stays()) { lookAt(owner, 10.0F, 20.0F); move = true; }
        if ((onGround() || isInWater()) && jumpDelay-- <= 0 && move) {
            boolean fast = upgrade() == AIR;
            jumpDelay = (random.nextInt(10) + 5) / 3;
            float forward = (fast ? 0.4F : 0.3F) * (isInWater() ? 0.75F : 1.0F), strafe = (1.0F - random.nextFloat() * 2.0F) * 0.05F;
            float yaw = getYRot() * net.minecraft.util.Mth.DEG_TO_RAD;
            setDeltaMovement(-net.minecraft.util.Mth.sin(yaw) * forward + net.minecraft.util.Mth.cos(yaw) * strafe, 0.42,
                    net.minecraft.util.Mth.cos(yaw) * forward + net.minecraft.util.Mth.sin(yaw) * strafe);
            hasImpulse = true;
            level().broadcastEntityEvent(this, (byte) 20);
            playSound(SoundEvents.CHEST_CLOSE, 0.1F, random.nextFloat() * 0.1F + 0.9F);
        }
    }

    private boolean teleportToOwner(LivingEntity owner) {
        int x = net.minecraft.util.Mth.floor(owner.getX()) - 2, z = net.minecraft.util.Mth.floor(owner.getZ()) - 2, y = net.minecraft.util.Mth.floor(owner.getBoundingBox().minY);
        for (int l = 0; l <= 4; l++) for (int i = 0; i <= 4; i++) {
            if (l >= 1 && i >= 1 && l <= 3 && i <= 3) continue;
            var floor = new BlockPos(x + l, y - 1, z + i);
            var feet = floor.above();
            boolean ground = level().getBlockState(floor).isFaceSturdy(level(), floor, net.minecraft.core.Direction.UP) || !level().getFluidState(floor).isEmpty();
            if (ground && !level().getBlockState(feet).isSuffocating(level(), feet) && !level().getBlockState(feet.above()).isSuffocating(level(), feet.above())) {
                playSound(SoundEvents.ENDERMAN_TELEPORT, 0.5F, 1.0F);
                moveTo(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, getYRot(), getXRot());
                return true;
            }
        }
        return false;
    }

    private void pullItems() {
        if (!isAlive()) return;
        for (var item : level().getEntitiesOfClass(ItemEntity.class, getBoundingBox().inflate(0.5))) {
            var stack = item.getItem().copy();
            var rest = inventory.addItem(stack);
            if (rest.getCount() != item.getItem().getCount()) {
                playSound(SoundEvents.GENERIC_EAT, 0.5F, random.nextFloat() * 0.5F + 0.5F);
                level().broadcastEntityEvent(this, (byte) 17);
                if (rest.isEmpty()) item.discard(); else item.setItem(rest);
            }
        }
        for (var item : level().getEntitiesOfClass(ItemEntity.class, getBoundingBox().inflate(3.0))) {
            double dx = item.getX() - getX(), dy = item.getY() - getY() + getBbHeight() * 0.8, dz = item.getZ() - getZ();
            double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (length > 0) item.setDeltaMovement(item.getDeltaMovement().subtract(dx / length * 0.075, dy / length * 0.075, dz / length * 0.075));
        }
    }

    @Override protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (player.isShiftKeyDown()) {
            if (!level().isClientSide && isOwner(player)) {
                entityData.set(STAY, !stays());
                player.displayClientMessage(Component.translatable(stays() ? "message.tc4modern.trunk_stay" : "message.tc4modern.trunk_follow"), true);
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        var stack = player.getItemInHand(hand);
        if (Tc4Legacy.isNamed(stack, "golem_bell")) return InteractionResult.PASS;
        var legacyStack = Tc4Legacy.legacy(stack).orElse(null);
        if (upgrade() == -1 && legacyStack != null && legacyStack.key().equals("Thaumcraft:ItemGolemUpgrade")) {
            if (!level().isClientSide) {
                setUpgrade(legacyStack.metadata());
                if (!player.getAbilities().instabuild) stack.shrink(1);
                playSound(Tc4Legacy.sound("upgrade"), 0.5F, 1.0F);
            }
            player.swing(hand);
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (stack.isEdible() && getHealth() < getMaxHealth()) {
            if (!level().isClientSide) {
                var food = stack.getFoodProperties(this);
                heal(food == null ? 1 : food.getNutrition());
                playSound(getHealth() >= getMaxHealth() ? SoundEvents.PLAYER_BURP : SoundEvents.GENERIC_EAT, 0.5F, random.nextFloat() * 0.5F + 0.5F);
                level().broadcastEntityEvent(this, (byte) 18);
                if (!player.getAbilities().instabuild) stack.shrink(1);
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (upgrade() == WATER && !isOwner(player)) return InteractionResult.sidedSuccess(level().isClientSide);
        if (!level().isClientSide) {
            int rows = rows();
            player.openMenu(new SimpleMenuProvider((id, playerInventory, p) -> new ChestMenu(rows == 4 ? MenuType.GENERIC_9x4 : MenuType.GENERIC_9x3,
                    id, playerInventory, inventory, rows), getDisplayName()));
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    @Override public void handleEntityEvent(byte event) {
        switch (event) {
            case 17 -> lidrot = 0.15F;
            case 18 -> {
                lidrot = 0.15F;
                level().addParticle(ParticleTypes.HEART, getX() + random.nextFloat() * getBbWidth() * 2 - getBbWidth(), getY() + 0.5 + random.nextFloat() * getBbHeight(),
                        getZ() + random.nextFloat() * getBbWidth() * 2 - getBbWidth(), random.nextGaussian() * 0.02, random.nextGaussian() * 0.02, random.nextGaussian() * 0.02);
            }
            case 20 -> squish = 1.0F;
            default -> super.handleEntityEvent(event);
        }
    }

    /** The golem bell's pick-up: the trunk becomes a trunk spawner again, keeping its contents with the order upgrade. */
    public void ringAway(Player player) {
        if (!(level() instanceof ServerLevel server) || !isAlive()) return;
        if (upgrade() == WATER && !isOwner(player)) return;
        var dropped = Tc4Legacy.stack("Thaumcraft:TrunkSpawner", 0, 1, player.isShiftKeyDown() ? "" : "{upgrade:" + upgrade() + "b}");
        if (dropped.isEmpty()) return;
        if (player.isShiftKeyDown()) {
            if (upgrade() > -1 && random.nextBoolean()) Tc4Legacy.drop(this, Tc4Legacy.stack("Thaumcraft:ItemGolemUpgrade", upgrade(), 1), 0.5F);
        } else {
            if (hasCustomName()) dropped.setHoverName(getCustomName());
            var payload = new CompoundTag();
            payload.putInt("upgrade", upgrade());
            if (upgrade() == ORDER) payload.put("Items", saveInventory());
            dropped.getOrCreateTag().put(PORTABLE, payload);
        }
        spawnAtLocation(dropped, 0.5F);
        if (upgrade() != ORDER || player.isShiftKeyDown()) dropContents();
        else inventory.clearContent();
        server.playSound(null, getX(), getY(), getZ(), Tc4Legacy.sound("zap"), getSoundSource(), 0.5F, 1.0F);
        discard();
    }

    /** Places a trunk rung away by {@link #ringAway}, without the original placer. */
    public void unpack(CompoundTag payload) {
        setUpgrade(payload.contains("upgrade") ? payload.getInt("upgrade") : -1);
        if (payload.contains("Items", 9)) loadInventory(payload.getList("Items", 10));
    }

    private ListTag saveInventory() {
        var list = new ListTag();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            var stack = inventory.getItem(i);
            if (stack.isEmpty()) continue;
            var entry = new CompoundTag();
            entry.putByte("Slot", (byte) i);
            stack.save(entry);
            list.add(entry);
        }
        return list;
    }

    private void loadInventory(ListTag list) {
        inventory.clearContent();
        for (int i = 0; i < list.size(); i++) {
            var entry = list.getCompound(i);
            int slot = entry.getByte("Slot") & 255;
            if (slot < inventory.getContainerSize()) inventory.setItem(slot, ItemStack.of(entry));
        }
    }

    private void dropContents() {
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            var stack = inventory.removeItemNoUpdate(i);
            if (!stack.isEmpty()) spawnAtLocation(stack);
        }
    }

    @Override public void die(DamageSource source) {
        if (!level().isClientSide) dropContents();
        super.die(source);
    }

    @Override protected void playStepSound(BlockPos pos, BlockState state) { }

    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Stay", stays()); tag.putByte("upgrade", (byte) upgrade());
        if (getOwnerUUID() != null) tag.putUUID("Tc4Owner", getOwnerUUID());
        tag.put("Tc4Inventory", saveInventory());
        legacy.save(this, tag);
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(STAY, tag.getBoolean("Stay")); setUpgrade(tag.contains("upgrade") ? tag.getByte("upgrade") : -1);
        setOwner(tag.hasUUID("Tc4Owner") ? tag.getUUID("Tc4Owner") : null);
        if (tag.contains("Tc4Inventory", 9)) loadInventory(tag.getList("Tc4Inventory", 10));
        legacy.load(tag);
    }

    @LegacyType(LEGACY_CLASS) public interface Old {
        @LegacyMethod(name = "getUpgrade", descriptor = "()I") int upgrade();
        @LegacyMethod(name = "getStay", descriptor = "()Z") boolean stay();
        @LegacyMethod(name = "func_152113_b", descriptor = "()Ljava/lang/String;") String ownerName();
        @LegacyField(name = "inventory", descriptor = "Lthaumcraft/common/entities/golems/InventoryTrunk;") LegacyObject inventory();
    }
    @LegacyType("net.minecraft.inventory.IInventory") public interface OldInventory {
        @LegacyMethod(name = "func_70302_i_", descriptor = "()I") int size();
        @LegacyMethod(name = "func_70301_a", descriptor = "(I)Lnet/minecraft/item/ItemStack;") LegacyObject get(int slot);
    }
}
