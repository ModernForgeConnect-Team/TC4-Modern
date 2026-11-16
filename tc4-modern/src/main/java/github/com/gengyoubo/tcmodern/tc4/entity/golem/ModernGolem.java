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

/** Modern blank golem body. Core jobs and TC4's old placer do not yet use this entity. */
public final class ModernGolem extends PathfinderMob {
    private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> GATHER_CORE = net.minecraft.network.syncher.SynchedEntityData.defineId(ModernGolem.class, net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);
    private static final net.minecraft.network.syncher.EntityDataAccessor<net.minecraft.world.item.ItemStack> CARRIED = net.minecraft.network.syncher.SynchedEntityData.defineId(ModernGolem.class, net.minecraft.network.syncher.EntityDataSerializers.ITEM_STACK);
    private final GolemMaterial material;
    private BlockPos destination;
    private net.minecraft.core.Direction destinationFace;
    private UUID ownerId;
    private BlockPos home;
    private int regenerationTimer;
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
        super.defineSynchedData(); entityData.define(GATHER_CORE, false); entityData.define(CARRIED, net.minecraft.world.item.ItemStack.EMPTY);
    }
    public static AttributeSupplier.Builder attributes(GolemMaterial material) {
        return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, material.health)
                .add(Attributes.ARMOR, material.armor).add(Attributes.MOVEMENT_SPEED, material.speed)
                .add(Attributes.ATTACK_DAMAGE, 2 + material.strength);
    }
    @Override protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
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
        var id = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem());
        boolean gatherCore = id != null && id.toString().equals("thaumcraft:item_golem_core/meta_2");
        if (!player.isShiftKeyDown() && !entityData.get(GATHER_CORE) && gatherCore && (ownerId == null || ownerId.equals(player.getUUID()))) {
            if (!level().isClientSide) { entityData.set(GATHER_CORE, true); if (!player.getAbilities().instabuild) stack.shrink(1); }
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
    public boolean hasGatherCore() { return entityData.get(GATHER_CORE); }
    private net.minecraft.world.item.ItemStack carried() { return entityData.get(CARRIED); }
    private void setCarried(net.minecraft.world.item.ItemStack stack) { entityData.set(CARRIED, stack.copy()); }
    private final class GatherGoal extends net.minecraft.world.entity.ai.goal.Goal {
        private net.minecraft.world.entity.item.ItemEntity target;
        private int retry;
        GatherGoal() { setFlags(java.util.EnumSet.of(Flag.MOVE, Flag.LOOK)); }
        @Override public boolean canUse() {
            if (!entityData.get(GATHER_CORE) || destinationInventory() == null || home == null) return false;
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
            return entityData.get(GATHER_CORE) && inventory != null
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
        tag.putBoolean("Tc4GatherCore", entityData.get(GATHER_CORE));
        if (!carried().isEmpty()) tag.put("Tc4Carried", carried().save(new CompoundTag()));
        if (destination != null) { tag.putLong("Tc4Destination", destination.asLong()); tag.putInt("Tc4DestinationFace", destinationFace.get3DDataValue()); }
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        ownerId = tag.hasUUID("Tc4Owner") ? tag.getUUID("Tc4Owner") : null;
        if (tag.contains("Tc4Home", 4)) setHome(BlockPos.of(tag.getLong("Tc4Home")));
        regenerationTimer = Math.max(0, tag.getInt("Tc4RegenerationTimer"));
        entityData.set(GATHER_CORE, tag.getBoolean("Tc4GatherCore"));
        setCarried(net.minecraft.world.item.ItemStack.of(tag.getCompound("Tc4Carried")));
        if (tag.contains("Tc4Destination", 4)) { destination = BlockPos.of(tag.getLong("Tc4Destination")); destinationFace = net.minecraft.core.Direction.from3DDataValue(tag.getInt("Tc4DestinationFace")); }
    }
    @Override public boolean fireImmune() { return material != null && material.fireResistant || super.fireImmune(); }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.IRON_GOLEM_HURT; }
    @Override protected SoundEvent getDeathSound() { return SoundEvents.IRON_GOLEM_DEATH; }
}
