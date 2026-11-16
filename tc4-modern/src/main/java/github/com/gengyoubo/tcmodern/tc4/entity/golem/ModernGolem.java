package github.com.gengyoubo.tcmodern.tc4.entity.golem;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import org.jetbrains.annotations.Nullable;

/** Modern golem with TC4 gather and guard core jobs. */
public final class ModernGolem extends PathfinderMob {
    private static final int NO_CORE = -1, GATHER_CORE = 2, GUARD_CORE = 4;
    private static final net.minecraft.network.syncher.EntityDataAccessor<Integer> CORE = net.minecraft.network.syncher.SynchedEntityData.defineId(ModernGolem.class, net.minecraft.network.syncher.EntityDataSerializers.INT);
    private static final net.minecraft.network.syncher.EntityDataAccessor<net.minecraft.world.item.ItemStack> CARRIED = net.minecraft.network.syncher.SynchedEntityData.defineId(ModernGolem.class, net.minecraft.network.syncher.EntityDataSerializers.ITEM_STACK);
    private final GolemMaterial material;
    private BlockPos destination;
    private net.minecraft.core.Direction destinationFace;
    private UUID ownerId;
    private BlockPos home;
    private int regenerationTimer;
    private int attackAnimationTicks;
    public ModernGolem(EntityType<? extends ModernGolem> type, Level level, GolemMaterial material) {
        super(type, level); this.material = material; setPersistenceRequired();
    }
    public GolemMaterial material() { return material; }
    @Nullable public UUID ownerId() { return ownerId; }
    @Nullable public BlockPos home() { return home; }
    public void setOwner(Player player) { ownerId = player.getUUID(); }
    public void setHome(BlockPos position) { home = position.immutable(); restrictTo(home, 32); }
    public void resetPlacement(BlockPos position) { destination = null; destinationFace = null; setHome(position); }
    public void setDestination(BlockPos position, net.minecraft.core.Direction face) {
        destination = position.immutable(); destinationFace = face; setHome(position);
    }
    @Override protected void defineSynchedData() {
        super.defineSynchedData(); entityData.define(CORE, NO_CORE); entityData.define(CARRIED, net.minecraft.world.item.ItemStack.EMPTY);
    }
    public static AttributeSupplier.Builder attributes(GolemMaterial material) {
        return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, material.health)
                .add(Attributes.ARMOR, material.armor).add(Attributes.MOVEMENT_SPEED, material.speed)
                .add(Attributes.ATTACK_DAMAGE, 2 + material.strength);
    }
    @Override protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new net.minecraft.world.entity.ai.goal.MeleeAttackGoal(this, 1.0, true) {
            @Override public boolean canUse() { return core() == GUARD_CORE && super.canUse(); }
            @Override public boolean canContinueToUse() { return core() == GUARD_CORE && super.canContinueToUse(); }
        });
        targetSelector.addGoal(1, new net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal<>(this, net.minecraft.world.entity.Mob.class, 10, true, false,
                target -> target instanceof net.minecraft.world.entity.monster.Enemy && !(target instanceof net.minecraft.world.entity.monster.Creeper)
                        && isWithinRestriction(target.blockPosition())) {
            @Override public boolean canUse() { return core() == GUARD_CORE && super.canUse(); }
            @Override public boolean canContinueToUse() { return core() == GUARD_CORE && getTarget() != null && isWithinRestriction(getTarget().blockPosition()) && super.canContinueToUse(); }
        });
        goalSelector.addGoal(2, new GatherGoal());
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.6));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 6));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }
    @Override public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason,
            @Nullable SpawnGroupData group, @Nullable CompoundTag data) {
        var result = super.finalizeSpawn(level, difficulty, reason, group, data);
        if (home == null) setHome(blockPosition());
        return result;
    }
    @Override protected net.minecraft.world.InteractionResult mobInteract(Player player, net.minecraft.world.InteractionHand hand) {
        var stack = player.getItemInHand(hand);
        if (stack.isEmpty() || player.isShiftKeyDown() || core() != NO_CORE
                || (ownerId != null && !ownerId.equals(player.getUUID()))) return super.mobInteract(player, hand);
        var runtime = github.com.gengyoubo.tcmodern.api.TcModernApi.runtime().orElse(null);
        var original = runtime == null ? null : runtime.mappings().toLegacy(github.com.gengyoubo.mfcl.api.LegacyMappings.ITEM_STACK, stack).map(runtime::snapshot).orElse(null);
        boolean supported = original != null && original.key().equals("Thaumcraft:ItemGolemCore")
                && (original.metadata() == GATHER_CORE || original.metadata() == GUARD_CORE);
        if (supported) {
            if (!level().isClientSide) { entityData.set(CORE, original.metadata()); if (!player.getAbilities().instabuild) stack.shrink(1); }
            return net.minecraft.world.InteractionResult.sidedSuccess(level().isClientSide);
        }
        return super.mobInteract(player, hand);
    }
    private net.minecraftforge.items.IItemHandler destinationInventory() {
        if (destination == null || !level().hasChunkAt(destination)) return null;
        var blockEntity = level().getBlockEntity(destination);
        if (blockEntity == null) return null;
        return blockEntity.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER, destinationFace).orElse(null);
    }
    public net.minecraft.world.item.ItemStack carriedForDisplay() { return entityData.get(CARRIED).copy(); }
    public int core() { return entityData.get(CORE); }
    public boolean hasGatherCore() { return core() == GATHER_CORE; }
    public float attackAnimation(float partialTick) {
        if (attackAnimationTicks <= 0) return 0.0F;
        float progress = (10.0F - attackAnimationTicks + partialTick) / 10.0F;
        return net.minecraft.util.Mth.sin(net.minecraft.util.Mth.clamp(progress, 0.0F, 1.0F) * net.minecraft.util.Mth.PI);
    }
    @Override public boolean doHurtTarget(net.minecraft.world.entity.Entity target) {
        if (!level().isClientSide) level().broadcastEntityEvent(this, (byte) 4);
        return super.doHurtTarget(target);
    }
    @Override public void handleEntityEvent(byte event) {
        if (event == 4) attackAnimationTicks = 10;
        else super.handleEntityEvent(event);
    }
    private net.minecraft.world.item.ItemStack carried() { return entityData.get(CARRIED); }
    private void setCarried(net.minecraft.world.item.ItemStack stack) { entityData.set(CARRIED, stack.copy()); }
    private final class GatherGoal extends net.minecraft.world.entity.ai.goal.Goal {
        private net.minecraft.world.entity.item.ItemEntity target;
        private int retry;
        GatherGoal() { setFlags(java.util.EnumSet.of(Flag.MOVE, Flag.LOOK)); }
        @Override public boolean canUse() {
            if (!hasGatherCore() || destinationInventory() == null || home == null) return false;
            if (!carried().isEmpty()) return canInsert(destinationInventory(), carried());
            if (tickCount % 10 != 0) return false;
            var inventory = destinationInventory();
            var area = new net.minecraft.world.phys.AABB(home).inflate(16);
            target = level().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, area, item -> item.isAlive() && !item.hasPickUpDelay() && !item.getItem().isEmpty()
                    && net.minecraftforge.items.ItemHandlerHelper.insertItemStacked(inventory, item.getItem().copyWithCount(1), true).isEmpty())
                    .stream().min(java.util.Comparator.comparingDouble(ModernGolem.this::distanceToSqr)).orElse(null);
            return target != null;
        }
        private boolean canInsert(net.minecraftforge.items.IItemHandler inventory, net.minecraft.world.item.ItemStack item) {
            return inventory != null && net.minecraftforge.items.ItemHandlerHelper.insertItemStacked(inventory, item.copyWithCount(1), true).isEmpty();
        }
        @Override public boolean canContinueToUse() {
            var inventory = destinationInventory();
            return hasGatherCore() && inventory != null
                    && (!carried().isEmpty() ? canInsert(inventory, carried()) : target != null && target.isAlive() && !target.getItem().isEmpty());
        }
        @Override public void stop() { target = null; getNavigation().stop(); }
        @Override public void tick() {
            var inventory = destinationInventory();
            if (inventory == null) return;
            if (!carried().isEmpty()) {
                if (distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(destination)) <= 5.0) {
                    setCarried(net.minecraftforge.items.ItemHandlerHelper.insertItemStacked(inventory, carried().copy(), false));
                    getNavigation().stop();
                } else if (--retry <= 0) { retry = 20; getNavigation().moveTo(destination.getX() + 0.5, destination.getY() + 0.5, destination.getZ() + 0.5, 1.0); }
                return;
            }
            if (target == null || !target.isAlive() || target.hasPickUpDelay()) return;
            if (distanceToSqr(target) <= 2.25) {
                var source = target.getItem(); int count = Math.min(material.carry, source.getCount());
                setCarried(source.copyWithCount(count)); source.shrink(count);
                if (source.isEmpty()) target.discard(); else target.setItem(source);
                target = null; getNavigation().stop(); retry = 0;
            } else if (--retry <= 0) { retry = 20; getNavigation().moveTo(target, 1.0); }
        }
    }
    @Override protected void dropCustomDeathLoot(DamageSource source, int looting, boolean recentlyHit) {
        super.dropCustomDeathLoot(source, looting, recentlyHit);
        if (!carried().isEmpty()) { spawnAtLocation(carried().copy()); setCarried(net.minecraft.world.item.ItemStack.EMPTY); }
    }
    @Override public void tick() {
        super.tick();
        if (attackAnimationTicks > 0) attackAnimationTicks--;
        if (level().isClientSide || !isAlive()) return;
        if (home == null) setHome(blockPosition());
        if (regenerationTimer > 0) regenerationTimer--;
        else {
            regenerationTimer = material.regenerationDelay;
            if (getHealth() < getMaxHealth()) heal(1.0F);
        }
    }
    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (ownerId != null) tag.putUUID("Tc4Owner", ownerId);
        if (home != null) tag.putLong("Tc4Home", home.asLong());
        tag.putInt("Tc4RegenerationTimer", regenerationTimer);
        tag.putInt("Tc4Core", core());
        if (!carried().isEmpty()) tag.put("Tc4Carried", carried().save(new CompoundTag()));
        if (destination != null) { tag.putLong("Tc4Destination", destination.asLong()); tag.putInt("Tc4DestinationFace", destinationFace.get3DDataValue()); }
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        ownerId = tag.hasUUID("Tc4Owner") ? tag.getUUID("Tc4Owner") : null;
        if (tag.contains("Tc4Home", 4)) setHome(BlockPos.of(tag.getLong("Tc4Home")));
        regenerationTimer = Math.max(0, tag.getInt("Tc4RegenerationTimer"));
        int savedCore = tag.contains("Tc4Core", 99) ? tag.getInt("Tc4Core") : tag.getBoolean("Tc4GatherCore") ? GATHER_CORE : NO_CORE;
        entityData.set(CORE, savedCore == GATHER_CORE || savedCore == GUARD_CORE ? savedCore : NO_CORE);
        setCarried(net.minecraft.world.item.ItemStack.of(tag.getCompound("Tc4Carried")));
        if (tag.contains("Tc4Destination", 4)) { destination = BlockPos.of(tag.getLong("Tc4Destination")); destinationFace = net.minecraft.core.Direction.from3DDataValue(tag.getInt("Tc4DestinationFace")); }
    }
    @Override public boolean fireImmune() { return material != null && material.fireResistant || super.fireImmune(); }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.IRON_GOLEM_HURT; }
    @Override protected SoundEvent getDeathSound() { return SoundEvents.IRON_GOLEM_DEATH; }
}
