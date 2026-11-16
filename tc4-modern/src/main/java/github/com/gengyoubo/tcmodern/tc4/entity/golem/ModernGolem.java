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

/** Modern golem with supported TC4 core jobs. */
public final class ModernGolem extends PathfinderMob {
    private static final int NO_CORE = -1, FILL_CORE = 0, EMPTY_CORE = 1, GATHER_CORE = 2, HARVEST_CORE = 3, GUARD_CORE = 4, LIQUID_CORE = 5, ESSENTIA_CORE = 6, LUMBER_CORE = 7, USE_CORE = 8, BUTCHER_CORE = 9, SORT_CORE = 10, FISH_CORE = 11;
    private static final net.minecraft.network.syncher.EntityDataAccessor<Integer> CORE = net.minecraft.network.syncher.SynchedEntityData.defineId(ModernGolem.class, net.minecraft.network.syncher.EntityDataSerializers.INT);
    private static final net.minecraft.network.syncher.EntityDataAccessor<net.minecraft.world.item.ItemStack> CARRIED = net.minecraft.network.syncher.SynchedEntityData.defineId(ModernGolem.class, net.minecraft.network.syncher.EntityDataSerializers.ITEM_STACK);
    private static final net.minecraft.network.syncher.EntityDataAccessor<java.util.Optional<BlockPos>> FISHING_POS = net.minecraft.network.syncher.SynchedEntityData.defineId(ModernGolem.class, net.minecraft.network.syncher.EntityDataSerializers.OPTIONAL_BLOCK_POS);
    private final GolemMaterial material;
    private BlockPos destination;
    private net.minecraft.core.Direction destinationFace;
    private BlockPos markedContainer;
    private net.minecraft.core.Direction markedFace;
    private String markedDimension;
    private record SortMarker(BlockPos pos, net.minecraft.core.Direction face, String dimension) { }
    private final java.util.List<SortMarker> sortMarkers = new java.util.ArrayList<>();
    private UUID ownerId;
    private BlockPos home;
    private int regenerationTimer;
    private int attackAnimationTicks;
    private boolean useReturning;
    private net.minecraftforge.fluids.FluidStack carriedFluid = net.minecraftforge.fluids.FluidStack.EMPTY;
    private String carriedAspect = "";
    private int carriedAspectAmount;
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
    public void setMarkedContainer(BlockPos position, net.minecraft.core.Direction face) {
        markedContainer = position.immutable(); markedFace = face; markedDimension = level().dimension().location().toString();
    }
    public boolean toggleSortMarker(BlockPos pos, net.minecraft.core.Direction face) {
        String dimension = level().dimension().location().toString();
        boolean removed = sortMarkers.removeIf(marker -> marker.pos().equals(pos) && marker.face() == face && marker.dimension().equals(dimension));
        if (!removed) sortMarkers.add(new SortMarker(pos.immutable(), face, dimension));
        return !removed;
    }
    @Override protected void defineSynchedData() {
        super.defineSynchedData(); entityData.define(CORE, NO_CORE); entityData.define(CARRIED, net.minecraft.world.item.ItemStack.EMPTY); entityData.define(FISHING_POS, java.util.Optional.empty());
    }
    public static AttributeSupplier.Builder attributes(GolemMaterial material) {
        return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, material.health)
                .add(Attributes.ARMOR, material.armor).add(Attributes.MOVEMENT_SPEED, material.speed)
                .add(Attributes.ATTACK_DAMAGE, 2 + material.strength);
    }
    @Override protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new net.minecraft.world.entity.ai.goal.MeleeAttackGoal(this, 1.0, true) {
            @Override public boolean canUse() { return isCombatCore() && super.canUse(); }
            @Override public boolean canContinueToUse() { return isCombatCore() && super.canContinueToUse(); }
        });
        targetSelector.addGoal(1, new net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal<>(this, net.minecraft.world.entity.Mob.class, 10, true, false,
                target -> target instanceof net.minecraft.world.entity.monster.Enemy && !(target instanceof net.minecraft.world.entity.monster.Creeper)
                        && isWithinRestriction(target.blockPosition())) {
            @Override public boolean canUse() { return core() == GUARD_CORE && super.canUse(); }
            @Override public boolean canContinueToUse() { return core() == GUARD_CORE && getTarget() != null && isWithinRestriction(getTarget().blockPosition()) && super.canContinueToUse(); }
        });
        targetSelector.addGoal(2, new net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal<>(this, net.minecraft.world.entity.animal.Animal.class, 10, true, false,
                target -> !target.isBaby() && !(target instanceof net.minecraft.world.entity.TamableAnimal tame && tame.isTame())
                        && isWithinRestriction(target.blockPosition())) {
            @Override public boolean canUse() { return core() == BUTCHER_CORE && super.canUse(); }
            @Override public boolean canContinueToUse() { return core() == BUTCHER_CORE && getTarget() != null && !getTarget().isBaby() && isWithinRestriction(getTarget().blockPosition()) && super.canContinueToUse(); }
        });
        goalSelector.addGoal(2, new GatherGoal());
        goalSelector.addGoal(2, new TransferGoal());
        goalSelector.addGoal(2, new LiquidGoal());
        goalSelector.addGoal(2, new EssentiaGoal());
        goalSelector.addGoal(2, new UseGoal());
        goalSelector.addGoal(2, new SortGoal());
        goalSelector.addGoal(2, new HarvestGoal());
        goalSelector.addGoal(2, new LumberGoal());
        goalSelector.addGoal(2, new FishingGoal());
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
                && supportedCore(original.metadata());
        if (supported) {
            if (!level().isClientSide) { entityData.set(CORE, original.metadata()); if (!player.getAbilities().instabuild) stack.shrink(1); }
            return net.minecraft.world.InteractionResult.sidedSuccess(level().isClientSide);
        }
        return super.mobInteract(player, hand);
    }
    private net.minecraftforge.items.IItemHandler inventoryAt(BlockPos position, net.minecraft.core.Direction face) {
        if (position == null || !level().hasChunkAt(position)) return null;
        var blockEntity = level().getBlockEntity(position);
        if (blockEntity == null) return null;
        return blockEntity.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER, face).orElse(null);
    }
    private net.minecraftforge.items.IItemHandler destinationInventory() { return inventoryAt(destination, destinationFace); }
    public net.minecraft.world.item.ItemStack carriedForDisplay() { return entityData.get(CARRIED).copy(); }
    public int core() { return entityData.get(CORE); }
    public java.util.Optional<BlockPos> fishingPosition() { return entityData.get(FISHING_POS); }
    public boolean hasLiquidCore() { return core() == LIQUID_CORE; }
    public boolean hasEssentiaCore() { return core() == ESSENTIA_CORE; }
    public boolean hasUseCore() { return core() == USE_CORE; }
    public boolean hasSortCore() { return core() == SORT_CORE; }
    private static boolean supportedCore(int value) { return value == FILL_CORE || value == EMPTY_CORE || value == GATHER_CORE || value == HARVEST_CORE || value == GUARD_CORE || value == LIQUID_CORE || value == ESSENTIA_CORE || value == LUMBER_CORE || value == USE_CORE || value == BUTCHER_CORE || value == SORT_CORE || value == FISH_CORE; }
    public boolean hasCarriedFluid() { return !carriedFluid.isEmpty(); }
    public boolean hasCarriedEssentia() { return carriedAspectAmount > 0 && !carriedAspect.isEmpty(); }
    private boolean isCombatCore() { return core() == GUARD_CORE || core() == BUTCHER_CORE; }
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
    private final class EssentiaGoal extends net.minecraft.world.entity.ai.goal.Goal {
        private int scanDelay, pathDelay, timeout;
        private long blockedUntil;
        EssentiaGoal() { setFlags(java.util.EnumSet.of(Flag.MOVE, Flag.LOOK)); }
        private boolean usable() {
            if (!hasEssentiaCore() || destination == null || markedContainer == null || destination.equals(markedContainer)
                    || !level().dimension().location().toString().equals(markedDimension) || level().getGameTime() < blockedUntil) return false;
            if (hasCarriedEssentia()) return GolemEssentia.accepts(level(), destination, carriedAspect);
            String aspect = GolemEssentia.available(level(), markedContainer);
            return aspect != null && GolemEssentia.accepts(level(), destination, aspect);
        }
        @Override public boolean canUse() { if (--scanDelay > 0) return false; scanDelay = 40; return usable(); }
        @Override public boolean canContinueToUse() { return timeout < 300 && usable(); }
        @Override public void start() { pathDelay = 0; timeout = 0; }
        @Override public void stop() { getNavigation().stop(); }
        @Override public void tick() {
            BlockPos target = hasCarriedEssentia() ? destination : markedContainer;
            if (distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(target)) > 5.0) {
                timeout++;
                if (--pathDelay <= 0) { pathDelay = 20; getNavigation().moveTo(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5, 1.0); }
                return;
            }
            timeout = 0; getNavigation().stop();
            if (hasCarriedEssentia()) {
                int old = carriedAspectAmount;
                carriedAspectAmount = GolemEssentia.insert(level(), destination, carriedAspect, carriedAspectAmount);
                if (carriedAspectAmount <= 0) { carriedAspectAmount = 0; carriedAspect = ""; }
                else if (old == carriedAspectAmount) blockedUntil = level().getGameTime() + 100;
            } else {
                String aspect = GolemEssentia.available(level(), markedContainer);
                if (aspect != null && GolemEssentia.accepts(level(), destination, aspect) && GolemEssentia.takeOne(level(), markedContainer, aspect)) {
                    carriedAspect = aspect; carriedAspectAmount = 1;
                }
            }
            pathDelay = 0;
        }
    }
    private final class SortGoal extends net.minecraft.world.entity.ai.goal.Goal {
        private int scanDelay, pathDelay, timeout;
        private SortMarker selected;
        SortGoal() { setFlags(java.util.EnumSet.of(Flag.MOVE, Flag.LOOK)); }
        private SortMarker match(net.minecraft.world.item.ItemStack stack) {
            for (var marker : sortMarkers) {
                if (marker.pos().equals(destination) || !marker.dimension().equals(level().dimension().location().toString())) continue;
                var sink = inventoryAt(marker.pos(), marker.face());
                if (sink == null) continue;
                boolean found = false;
                for (int i = 0; i < sink.getSlots(); i++) if (net.minecraft.world.item.ItemStack.isSameItemSameTags(stack, sink.getStackInSlot(i))) { found = true; break; }
                if (found && net.minecraftforge.items.ItemHandlerHelper.insertItemStacked(sink, stack.copyWithCount(1), true).isEmpty()) return marker;
            }
            return null;
        }
        private int matchingSlot(net.minecraftforge.items.IItemHandler source) {
            for (int slot = 0; slot < source.getSlots(); slot++) {
                var item = source.extractItem(slot, material.carry, true);
                if (!item.isEmpty() && match(item) != null) return slot;
            }
            return -1;
        }
        private boolean usable() {
            if (!hasSortCore() || destination == null) return false;
            var source = destinationInventory();
            if (source == null) return false;
            if (carried().isEmpty()) return matchingSlot(source) >= 0;
            return match(carried()) != null || net.minecraftforge.items.ItemHandlerHelper.insertItemStacked(source, carried().copyWithCount(1), true).isEmpty();
        }
        @Override public boolean canUse() { if (--scanDelay > 0) return false; scanDelay = 20; return usable(); }
        @Override public boolean canContinueToUse() { return timeout < 300 && usable(); }
        @Override public void start() { pathDelay = 0; timeout = 0; }
        @Override public void stop() { selected = null; getNavigation().stop(); }
        @Override public void tick() {
            selected = carried().isEmpty() ? null : match(carried());
            BlockPos target = selected == null ? destination : selected.pos();
            if (distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(target)) > 5.0) {
                timeout++;
                if (--pathDelay <= 0) { pathDelay = 20; getNavigation().moveTo(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5, 1.0); }
                return;
            }
            timeout = 0; getNavigation().stop();
            if (carried().isEmpty()) {
                var source = destinationInventory();
                if (source == null) return;
                int slot = matchingSlot(source);
                if (slot >= 0) setCarried(source.extractItem(slot, material.carry, false));
            } else {
                var sink = selected == null ? destinationInventory() : inventoryAt(selected.pos(), selected.face());
                if (sink != null) setCarried(net.minecraftforge.items.ItemHandlerHelper.insertItemStacked(sink, carried().copy(), false));
            }
            pathDelay = 0;
        }
    }
    private final class UseGoal extends net.minecraft.world.entity.ai.goal.Goal {
        private int scanDelay, pathDelay, timeout, workDelay;
        UseGoal() { setFlags(java.util.EnumSet.of(Flag.MOVE, Flag.LOOK)); }
        private boolean usable() {
            if (!hasUseCore() || destination == null || markedContainer == null || markedFace == null
                    || !level().dimension().location().toString().equals(markedDimension) || !level().hasChunkAt(markedContainer)) return false;
            var inventory = destinationInventory();
            if (inventory == null) return false;
            if (!carried().isEmpty()) return !useReturning || net.minecraftforge.items.ItemHandlerHelper.insertItemStacked(inventory, carried().copyWithCount(1), true).isEmpty();
            for (int slot = 0; slot < inventory.getSlots(); slot++) if (!inventory.extractItem(slot, 1, true).isEmpty()) return true;
            return false;
        }
        @Override public boolean canUse() { if (--scanDelay > 0) return false; scanDelay = 40; return usable(); }
        @Override public boolean canContinueToUse() { return timeout < 300 && usable(); }
        @Override public void start() { timeout = 0; pathDelay = 0; }
        @Override public void stop() { getNavigation().stop(); }
        @Override public void tick() {
            if (workDelay > 0) { workDelay--; return; }
            BlockPos target = carried().isEmpty() || useReturning ? destination : markedContainer;
            if (distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(target)) > 5.0) {
                timeout++;
                if (--pathDelay <= 0) { pathDelay = 20; getNavigation().moveTo(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5, 1.0); }
                return;
            }
            timeout = 0; getNavigation().stop();
            if (carried().isEmpty()) {
                var inventory = destinationInventory();
                if (inventory == null) return;
                for (int slot = 0; slot < inventory.getSlots(); slot++) {
                    var extracted = inventory.extractItem(slot, 1, false);
                    if (!extracted.isEmpty()) { setCarried(extracted); useReturning = false; break; }
                }
            } else if (useReturning) {
                var inventory = destinationInventory();
                if (inventory != null) setCarried(net.minecraftforge.items.ItemHandlerHelper.insertItemStacked(inventory, carried().copy(), false));
                if (carried().isEmpty()) useReturning = false;
            } else if (level() instanceof net.minecraft.server.level.ServerLevel server) {
                workDelay = 20;
                var profile = new com.mojang.authlib.GameProfile(getUUID(), "[TC4Golem]");
                var fake = new net.minecraftforge.common.util.FakePlayer(server, profile);
                fake.moveTo(getX(), getY(), getZ(), getYRot(), getXRot());
                fake.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, carried().copy());
                setCarried(net.minecraft.world.item.ItemStack.EMPTY);
                useReturning = true;
                var hitPos = net.minecraft.world.phys.Vec3.atCenterOf(markedContainer).add(markedFace.getStepX() * 0.5, markedFace.getStepY() * 0.5, markedFace.getStepZ() * 0.5);
                var hit = new net.minecraft.world.phys.BlockHitResult(hitPos, markedFace, markedContainer, false);
                try {
                    var result = fake.gameMode.useItemOn(fake, server, fake.getMainHandItem(), net.minecraft.world.InteractionHand.MAIN_HAND, hit);
                    if (result.consumesAction()) server.broadcastEntityEvent(ModernGolem.this, (byte)4);
                } catch (RuntimeException failure) {
                    com.mojang.logging.LogUtils.getLogger().warn("Golem could not use an item at {}", markedContainer, failure);
                } finally {
                    setCarried(fake.getMainHandItem());
                    for (int slot = 1; slot < fake.getInventory().getContainerSize(); slot++) {
                        var extra = fake.getInventory().getItem(slot);
                        if (!extra.isEmpty()) { if (carried().isEmpty()) setCarried(extra); else spawnAtLocation(extra.copy()); }
                    }
                }
            }
            pathDelay = 0;
        }
    }
    private final class FishingGoal extends net.minecraft.world.entity.ai.goal.Goal {
        private BlockPos water, bank;
        private int scanDelay, pathDelay, travelTicks, waitTicks;
        private GolemBobber bobber;
        FishingGoal() { setFlags(java.util.EnumSet.of(Flag.MOVE, Flag.LOOK)); }
        private boolean waterSurface(BlockPos pos) {
            return level().hasChunkAt(pos) && level().getFluidState(pos).is(net.minecraft.tags.FluidTags.WATER)
                    && level().getBlockState(pos.above()).isAir();
        }
        @Override public boolean canUse() {
            if (core() != FISH_CORE || home == null || --scanDelay > 0) return false;
            scanDelay = 80;
            for (BlockPos candidate : BlockPos.betweenClosed(home.offset(-8, -3, -8), home.offset(8, 3, 8))) {
                if (!waterSurface(candidate)) continue;
                for (var side : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                    BlockPos shore = candidate.relative(side).above();
                    if (!level().hasChunkAt(shore) || !isWithinRestriction(shore) || !level().getFluidState(shore).isEmpty()
                            || !level().getBlockState(shore).getCollisionShape(level(), shore).isEmpty()
                            || !level().getBlockState(shore.above()).getCollisionShape(level(), shore.above()).isEmpty()
                            || !level().getBlockState(shore.below()).isFaceSturdy(level(), shore.below(), net.minecraft.core.Direction.UP)) continue;
                    water = candidate.immutable(); bank = shore.immutable(); return true;
                }
            }
            return false;
        }
        @Override public boolean canContinueToUse() { return core() == FISH_CORE && water != null && waterSurface(water) && travelTicks < 200; }
        @Override public void start() { pathDelay = 0; travelTicks = 0; waitTicks = 200 + random.nextInt(301); }
        @Override public void stop() {
            getNavigation().stop(); entityData.set(FISHING_POS, java.util.Optional.empty()); water = null; bank = null;
            if (bobber != null) { bobber.discard(); bobber = null; }
        }
        @Override public void tick() {
            if (distanceToSqr(net.minecraft.world.phys.Vec3.atBottomCenterOf(bank)) > 2.25) {
                if (fishingPosition().isPresent()) entityData.set(FISHING_POS, java.util.Optional.empty());
                travelTicks++;
                if (--pathDelay <= 0) { pathDelay = 20; getNavigation().moveTo(bank.getX() + 0.5, bank.getY(), bank.getZ() + 0.5, 1.0); }
                return;
            }
            getNavigation().stop(); travelTicks = 0;
            if (fishingPosition().isEmpty()) entityData.set(FISHING_POS, java.util.Optional.of(water));
            if (bobber == null || !bobber.isAlive()) { bobber = GolemBobber.cast(ModernGolem.this, water); level().addFreshEntity(bobber); }
            getLookControl().setLookAt(water.getX() + 0.5, water.getY() + 0.5, water.getZ() + 0.5);
            if (--waitTicks > 0) return;
            if (!(level() instanceof net.minecraft.server.level.ServerLevel server)) return;
            float roll = random.nextFloat();
            var table = roll < 0.10F ? net.minecraft.world.level.storage.loot.BuiltInLootTables.FISHING_JUNK
                    : roll < 0.15F ? net.minecraft.world.level.storage.loot.BuiltInLootTables.FISHING_TREASURE
                    : net.minecraft.world.level.storage.loot.BuiltInLootTables.FISHING_FISH;
            var params = new net.minecraft.world.level.storage.loot.LootParams.Builder(server)
                    .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN, net.minecraft.world.phys.Vec3.atCenterOf(water))
                    .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.TOOL, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.FISHING_ROD))
                    .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.THIS_ENTITY, ModernGolem.this)
                    .create(net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.FISHING);
            for (var item : server.getServer().getLootData().getLootTable(table).getRandomItems(params)) if (!item.isEmpty()) spawnAtLocation(item);
            if (bobber != null && bobber.isAlive()) bobber.bite();
            else server.sendParticles(net.minecraft.core.particles.ParticleTypes.SPLASH, water.getX() + 0.5, water.getY() + 1.0, water.getZ() + 0.5, 8, 0.2, 0.05, 0.2, 0.05);
            server.broadcastEntityEvent(ModernGolem.this, (byte) 4);
            waitTicks = 200 + random.nextInt(301);
        }
    }
    private net.minecraftforge.fluids.capability.IFluidHandler fluidAt(BlockPos pos, net.minecraft.core.Direction face) {
        if (pos == null || !level().hasChunkAt(pos)) return null;
        var block = level().getBlockEntity(pos);
        return block == null ? null : block.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.FLUID_HANDLER, face).orElse(null);
    }
    private final class LiquidGoal extends net.minecraft.world.entity.ai.goal.Goal {
        private int scanDelay, pathDelay;
        LiquidGoal() { setFlags(java.util.EnumSet.of(Flag.MOVE, Flag.LOOK)); }
        private net.minecraftforge.fluids.FluidStack sourceBlockSample() {
            if (markedContainer == null || !level().hasChunkAt(markedContainer)
                    || !net.minecraftforge.event.ForgeEventFactory.getMobGriefingEvent(level(), ModernGolem.this)) return net.minecraftforge.fluids.FluidStack.EMPTY;
            var state = level().getBlockState(markedContainer);
            var fluid = state.getFluidState();
            if (!(state.getBlock() instanceof net.minecraft.world.level.block.BucketPickup) || !fluid.isSource()) return net.minecraftforge.fluids.FluidStack.EMPTY;
            var bucket = new net.minecraft.world.item.ItemStack(fluid.getType().getBucket());
            var sample = net.minecraftforge.fluids.FluidUtil.getFluidContained(bucket).orElse(net.minecraftforge.fluids.FluidStack.EMPTY);
            return !sample.isEmpty() && sample.getAmount() == 1000 && sample.getFluid() == fluid.getType() ? sample : net.minecraftforge.fluids.FluidStack.EMPTY;
        }
        private boolean usable() {
            if (core() != LIQUID_CORE || destination == null || markedContainer == null || destination.equals(markedContainer)
                    || !level().dimension().location().toString().equals(markedDimension)) return false;
            var sink = fluidAt(destination, destinationFace);
            if (sink == null) return false;
            if (!carriedFluid.isEmpty()) return sink.fill(carriedFluid, net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE) > 0;
            var source = fluidAt(markedContainer, markedFace);
            if (source == null) {
                var sample = sourceBlockSample();
                return !sample.isEmpty() && sink.fill(sample, net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE) >= sample.getAmount();
            }
            var sample = source.drain((int)Math.sqrt(material.carry) * 1000, net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE);
            return !sample.isEmpty() && sink.fill(sample, net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE) > 0;
        }
        @Override public boolean canUse() { if (--scanDelay > 0) return false; scanDelay = 20; return usable(); }
        @Override public boolean canContinueToUse() { return usable(); }
        @Override public void stop() { getNavigation().stop(); }
        @Override public void tick() {
            BlockPos target = carriedFluid.isEmpty() ? markedContainer : destination;
            if (distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(target)) > 5.0) {
                if (--pathDelay <= 0) { pathDelay = 20; getNavigation().moveTo(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5, 1.0); }
                return;
            }
            getNavigation().stop();
            var sink = fluidAt(destination, destinationFace);
            if (sink == null) return;
            if (!carriedFluid.isEmpty()) {
                int accepted = sink.fill(carriedFluid.copy(), net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
                carriedFluid.shrink(accepted);
                if (carriedFluid.isEmpty()) carriedFluid = net.minecraftforge.fluids.FluidStack.EMPTY;
            } else {
                var source = fluidAt(markedContainer, markedFace);
                if (source == null) {
                    var sample = sourceBlockSample();
                    var state = level().getBlockState(markedContainer);
                    if (!sample.isEmpty() && sink.fill(sample, net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE) >= sample.getAmount()
                            && state.getBlock() instanceof net.minecraft.world.level.block.BucketPickup pickup
                            && net.minecraftforge.event.ForgeEventFactory.onEntityDestroyBlock(ModernGolem.this, markedContainer, state)) {
                        var bucket = pickup.pickupBlock(level(), markedContainer, state);
                        carriedFluid = net.minecraftforge.fluids.FluidUtil.getFluidContained(bucket).orElse(net.minecraftforge.fluids.FluidStack.EMPTY);
                    }
                    pathDelay = 0;
                    return;
                }
                var sample = source.drain((int)Math.sqrt(material.carry) * 1000, net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE);
                int accepted = sample.isEmpty() ? 0 : sink.fill(sample, net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE);
                if (accepted > 0) { sample.setAmount(Math.min(sample.getAmount(), accepted)); carriedFluid = source.drain(sample, net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE); }
            }
            pathDelay = 0;
        }
    }
    private final class TransferGoal extends net.minecraft.world.entity.ai.goal.Goal {
        private int scanDelay, pathDelay;
        TransferGoal() { setFlags(java.util.EnumSet.of(Flag.MOVE, Flag.LOOK)); }
        private BlockPos sourcePos() { return core() == FILL_CORE ? markedContainer : destination; }
        private BlockPos sinkPos() { return core() == FILL_CORE ? destination : markedContainer; }
        private net.minecraftforge.items.IItemHandler source() { return core() == FILL_CORE ? inventoryAt(markedContainer, markedFace) : destinationInventory(); }
        private net.minecraftforge.items.IItemHandler sink() { return core() == FILL_CORE ? destinationInventory() : inventoryAt(markedContainer, markedFace); }
        private int transferableSlot(net.minecraftforge.items.IItemHandler source, net.minecraftforge.items.IItemHandler sink) {
            for (int slot = 0; slot < source.getSlots(); slot++) {
                var sample = source.extractItem(slot, material.carry, true);
                if (!sample.isEmpty() && net.minecraftforge.items.ItemHandlerHelper.insertItemStacked(sink, sample.copyWithCount(1), true).isEmpty()) return slot;
            }
            return -1;
        }
        @Override public boolean canUse() {
            if ((core() != FILL_CORE && core() != EMPTY_CORE) || destination == null || markedContainer == null || destination.equals(markedContainer)
                    || !level().dimension().location().toString().equals(markedDimension)) return false;
            if (--scanDelay > 0) return false;
            scanDelay = 20;
            var sink = sink();
            if (sink == null) return false;
            if (!carried().isEmpty()) return net.minecraftforge.items.ItemHandlerHelper.insertItemStacked(sink, carried().copyWithCount(1), true).isEmpty();
            var source = source();
            return source != null && transferableSlot(source, sink) >= 0;
        }
        @Override public boolean canContinueToUse() {
            if ((core() != FILL_CORE && core() != EMPTY_CORE) || destination == null || markedContainer == null || destination.equals(markedContainer)
                    || !level().dimension().location().toString().equals(markedDimension)) return false;
            var sink = sink();
            if (sink == null) return false;
            if (!carried().isEmpty()) return net.minecraftforge.items.ItemHandlerHelper.insertItemStacked(sink, carried().copyWithCount(1), true).isEmpty();
            var source = source();
            return source != null && transferableSlot(source, sink) >= 0;
        }
        @Override public void stop() { getNavigation().stop(); }
        @Override public void tick() {
            BlockPos target = carried().isEmpty() ? sourcePos() : sinkPos();
            if (distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(target)) > 5.0) {
                if (--pathDelay <= 0) { pathDelay = 20; getNavigation().moveTo(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5, 1.0); }
                return;
            }
            getNavigation().stop();
            var sink = sink();
            if (sink == null) return;
            if (!carried().isEmpty()) {
                setCarried(net.minecraftforge.items.ItemHandlerHelper.insertItemStacked(sink, carried().copy(), false));
            } else {
                var source = source();
                if (source == null) return;
                int slot = transferableSlot(source, sink);
                if (slot >= 0) setCarried(source.extractItem(slot, material.carry, false));
            }
            pathDelay = 0;
        }
    }
    private final class LumberGoal extends net.minecraft.world.entity.ai.goal.Goal {
        private BlockPos trunk;
        private int scanDelay, pathDelay, workDelay, timeout;
        LumberGoal() { setFlags(java.util.EnumSet.of(Flag.MOVE, Flag.LOOK)); }
        private boolean isLog(BlockPos pos) { return level().hasChunkAt(pos) && level().getBlockState(pos).is(net.minecraft.tags.BlockTags.LOGS); }
        private boolean treeAt(BlockPos pos) {
            if (!isLog(pos) || isLog(pos.below()) || !isLog(pos.above())) return false;
            for (int y = 2; y <= 8 && isLog(pos.above(y - 1)); y++) {
                BlockPos center = pos.above(y);
                for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
                    BlockPos nearby = center.offset(dx, 0, dz);
                    if (level().hasChunkAt(nearby) && level().getBlockState(nearby).is(net.minecraft.tags.BlockTags.LEAVES)) return true;
                }
            }
            return false;
        }
        @Override public boolean canUse() {
            if (core() != LUMBER_CORE || home == null || !net.minecraftforge.event.ForgeEventFactory.getMobGriefingEvent(level(), ModernGolem.this)) return false;
            if (--scanDelay > 0) return false;
            scanDelay = 60;
            BlockPos origin = blockPosition();
            for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-8, -3, -8), origin.offset(8, 3, 8))) {
                if (isWithinRestriction(pos) && treeAt(pos)) { trunk = pos.immutable(); return true; }
            }
            return false;
        }
        @Override public boolean canContinueToUse() {
            return core() == LUMBER_CORE && trunk != null && isLog(trunk) && timeout < 200
                    && net.minecraftforge.event.ForgeEventFactory.getMobGriefingEvent(level(), ModernGolem.this);
        }
        @Override public void start() { timeout = 0; pathDelay = 0; workDelay = 0; }
        @Override public void stop() { trunk = null; getNavigation().stop(); }
        @Override public void tick() {
            timeout++;
            if (distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(trunk)) > 5.0) {
                if (--pathDelay <= 0) { pathDelay = 20; getNavigation().moveTo(trunk.getX() + 0.5, trunk.getY(), trunk.getZ() + 0.5, 1.0); }
                return;
            }
            getNavigation().stop();
            if (--workDelay > 0) return;
            workDelay = 20;
            BlockPos log = trunk;
            for (int y = 1; y <= 8 && isLog(trunk.above(y)); y++) log = trunk.above(y);
            var state = level().getBlockState(log);
            if (net.minecraftforge.event.ForgeEventFactory.onEntityDestroyBlock(ModernGolem.this, log, state)) {
                level().broadcastEntityEvent(ModernGolem.this, (byte) 4);
                level().destroyBlock(log, true, ModernGolem.this);
                timeout = 0;
            }
        }
    }
    private final class HarvestGoal extends net.minecraft.world.entity.ai.goal.MoveToBlockGoal {
        HarvestGoal() { super(ModernGolem.this, 1.0, 8, 3); }
        @Override public boolean canUse() {
            return core() == HARVEST_CORE && net.minecraftforge.event.ForgeEventFactory.getMobGriefingEvent(level(), ModernGolem.this) && super.canUse();
        }
        @Override public boolean canContinueToUse() {
            return core() == HARVEST_CORE && net.minecraftforge.event.ForgeEventFactory.getMobGriefingEvent(level(), ModernGolem.this) && super.canContinueToUse();
        }
        @Override protected boolean isValidTarget(net.minecraft.world.level.LevelReader level, BlockPos pos) {
            if (!isWithinRestriction(pos)) return false;
            var state = level.getBlockState(pos);
            return state.getBlock() instanceof net.minecraft.world.level.block.CropBlock crop && crop.isMaxAge(state);
        }
        @Override public void tick() {
            super.tick();
            if (isReachedTarget() && isValidTarget(level(), blockPos)) {
                var state = level().getBlockState(blockPos);
                if (net.minecraftforge.event.ForgeEventFactory.onEntityDestroyBlock(ModernGolem.this, blockPos, state)) {
                    level().broadcastEntityEvent(ModernGolem.this, (byte) 4);
                    level().destroyBlock(blockPos, true, ModernGolem.this);
                }
                nextStartTick = 40;
            }
        }
    }
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
        tag.putBoolean("Tc4UseReturning", useReturning);
        if (!carried().isEmpty()) tag.put("Tc4Carried", carried().save(new CompoundTag()));
        if (!carriedFluid.isEmpty()) tag.put("Tc4CarriedFluid", carriedFluid.writeToNBT(new CompoundTag()));
        if (hasCarriedEssentia()) { tag.putString("Tc4CarriedAspect", carriedAspect); tag.putInt("Tc4CarriedAspectAmount", carriedAspectAmount); }
        if (destination != null) { tag.putLong("Tc4Destination", destination.asLong()); tag.putInt("Tc4DestinationFace", destinationFace.get3DDataValue()); }
        if (markedContainer != null) { tag.putLong("Tc4MarkedContainer", markedContainer.asLong()); tag.putInt("Tc4MarkedFace", markedFace.get3DDataValue()); tag.putString("Tc4MarkedDimension", markedDimension); }
        var markers = new net.minecraft.nbt.ListTag();
        for (var marker : sortMarkers) { var entry = new CompoundTag(); entry.putLong("Pos", marker.pos().asLong()); entry.putInt("Face", marker.face().get3DDataValue()); entry.putString("Dimension", marker.dimension()); markers.add(entry); }
        tag.put("Tc4SortMarkers", markers);
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        ownerId = tag.hasUUID("Tc4Owner") ? tag.getUUID("Tc4Owner") : null;
        if (tag.contains("Tc4Home", 4)) setHome(BlockPos.of(tag.getLong("Tc4Home")));
        regenerationTimer = Math.max(0, tag.getInt("Tc4RegenerationTimer"));
        int savedCore = tag.contains("Tc4Core", 99) ? tag.getInt("Tc4Core") : tag.getBoolean("Tc4GatherCore") ? GATHER_CORE : NO_CORE;
        entityData.set(CORE, supportedCore(savedCore) ? savedCore : NO_CORE);
        useReturning = tag.getBoolean("Tc4UseReturning");
        setCarried(net.minecraft.world.item.ItemStack.of(tag.getCompound("Tc4Carried")));
        carriedFluid = net.minecraftforge.fluids.FluidStack.loadFluidStackFromNBT(tag.getCompound("Tc4CarriedFluid"));
        carriedAspect = tag.getString("Tc4CarriedAspect");
        carriedAspectAmount = carriedAspect.isEmpty() ? 0 : Math.max(0, tag.getInt("Tc4CarriedAspectAmount"));
        if (tag.contains("Tc4Destination", 4)) { destination = BlockPos.of(tag.getLong("Tc4Destination")); destinationFace = net.minecraft.core.Direction.from3DDataValue(tag.getInt("Tc4DestinationFace")); }
        markedContainer = tag.contains("Tc4MarkedContainer", 4) ? BlockPos.of(tag.getLong("Tc4MarkedContainer")) : null;
        markedFace = markedContainer == null ? null : net.minecraft.core.Direction.from3DDataValue(tag.getInt("Tc4MarkedFace"));
        markedDimension = markedContainer == null ? null : tag.getString("Tc4MarkedDimension");
        sortMarkers.clear();
        var markers = tag.getList("Tc4SortMarkers", net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int i = 0; i < markers.size(); i++) { var entry = markers.getCompound(i); if (entry.contains("Pos", net.minecraft.nbt.Tag.TAG_LONG) && entry.contains("Dimension", net.minecraft.nbt.Tag.TAG_STRING)) sortMarkers.add(new SortMarker(BlockPos.of(entry.getLong("Pos")), net.minecraft.core.Direction.from3DDataValue(entry.getInt("Face")), entry.getString("Dimension"))); }
    }
    @Override public boolean fireImmune() { return material != null && material.fireResistant || super.fireImmune(); }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.IRON_GOLEM_HURT; }
    @Override protected SoundEvent getDeathSound() { return SoundEvents.IRON_GOLEM_DEATH; }
}
