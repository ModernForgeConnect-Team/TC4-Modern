package github.com.gengyoubo.tcmodern.tc4.content;

import com.mojang.logging.LogUtils;
import github.com.gengyoubo.mfcl.api.LegacyMappings;
import github.com.gengyoubo.mfcl.api.LegacyRuntime;
import github.com.gengyoubo.mfcl.api.LegacyStack;
import github.com.gengyoubo.tcmodern.api.TcModernApi;
import github.com.gengyoubo.tcmodern.api.aspect.TcAspect;
import github.com.gengyoubo.tcmodern.api.aspect.TcAspects;
import github.com.gengyoubo.tcmodern.api.research.TcResearch;
import github.com.gengyoubo.tcmodern.tc4.crucible.CrucibleEssentia;
import github.com.gengyoubo.tcmodern.tc4.crucible.CrucibleRules;
import github.com.gengyoubo.tcmodern.tc4.crucible.IntermediateRecipe;
import github.com.gengyoubo.tcmodern.tc4.crucible.IntermediateRecipes;
import github.com.gengyoubo.tcmodern.tc4.init.Tc4BlockEntities;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The intermediate crucible. Water, heat, boil-off and overflow follow TC4's crucible; a wand switches between
 * decomposition (every item thrown in dissolves into its aspects) and synthesis (up to two materials are held and
 * react, after a minute or on an empty-handed right-click, with the dissolved aspects).
 */
public final class IntermediateCrucibleBlockEntity extends BlockEntity {
    public enum Mode { DECOMPOSE, SYNTHESIS }
    /** Items the crucible itself ejected; it never takes them back in. */
    public static final String EJECTED = "tc4modern_crucible_output";
    private static final String FLUX_GOO = "Thaumcraft:blockFluxGoo", FLUX_GAS = "Thaumcraft:blockFluxGas";

    private final CrucibleEssentia essentia = new CrucibleEssentia();
    private final List<ItemStack> materials = new ArrayList<>();
    private final FluidTank tank = new FluidTank(CrucibleRules.WATER_CAPACITY, fluid -> fluid.getFluid().isSame(Fluids.WATER)) {
        @Override protected void onContentsChanged() { changed(); }
    };
    private final LazyOptional<IFluidHandler> fluidCapability = LazyOptional.of(() -> tank);
    private Mode mode = Mode.DECOMPOSE;
    private int heat, synthesisTimer, contactDelay;
    private long counter = CrucibleRules.START_COUNTER;
    @Nullable private UUID alchemist;
    private boolean loggedFailure;

    public IntermediateCrucibleBlockEntity(BlockPos pos, BlockState state) { super(Tc4BlockEntities.INTERMEDIATE_CRUCIBLE.get(), pos, state); }

    public Mode mode() { return mode; }
    public int heat() { return heat; }
    public int water() { return tank.getFluidAmount(); }
    public Map<String, Integer> essentia() { return essentia.snapshot(); }
    public List<ItemStack> materials() { return List.copyOf(materials); }
    public boolean boiling() { return CrucibleRules.boiling(heat, water()); }

    // ---- ticking: heat, overflow and boil-off as TileCrucible.updateEntity ----

    public static void tick(Level level, BlockPos pos, BlockState state, IntermediateCrucibleBlockEntity crucible) {
        if (level.isClientSide) return;
        try {
            crucible.serverTick();
        } catch (RuntimeException failure) {
            if (!crucible.loggedFailure) { crucible.loggedFailure = true; LogUtils.getLogger().error("Intermediate crucible at {} failed", pos, failure); }
        }
    }

    private void serverTick() {
        counter++;
        int previous = heat;
        heat = CrucibleRules.nextHeat(heat, water() > 0, heated(level, worldPosition.below()));
        boolean crossed = (previous > CrucibleRules.BOILING) != (heat > CrucibleRules.BOILING);
        if (heat != previous) setChanged();
        if (crossed) sync();

        if (essentia.total() > CrucibleEssentia.CAPACITY && counter % CrucibleRules.OVERFLOW_INTERVAL == 0) {
            essentia.takeRandom(random());
            spill(false);
            changed();
        }
        if (counter > CrucibleRules.BOIL_OFF_INTERVAL && heat > CrucibleRules.BOILING) {
            counter = 0;
            if (!essentia.isEmpty()) {
                var components = components();
                tank.drain(CrucibleRules.BOIL_OFF_WATER, IFluidHandler.FluidAction.EXECUTE);
                if (essentia.boilOff(random(), tag -> components.getOrDefault(tag, List.of()))) spill(false);
                changed();
            }
        }
        if (mode == Mode.SYNTHESIS && !materials.isEmpty() && boiling() && ++synthesisTimer >= CrucibleRules.SYNTHESIS_TICKS)
            synthesize(owner());
    }

    private java.util.random.RandomGenerator random() { return level.random::nextLong; }

    /** Lava, fire or a nitor directly below, the heat sources of the original crucible. */
    static boolean heated(Level level, BlockPos below) {
        var state = level.getBlockState(below);
        if (level.getFluidState(below).is(FluidTags.LAVA) || state.is(net.minecraft.tags.BlockTags.FIRE)) return true;
        return TcModernApi.runtime().flatMap(runtime -> runtime.hostBlock("Thaumcraft:blockAiry", 1, BlockState.class))
                .map(nitor -> nitor.equals(state)).orElse(false);
    }

    private Map<String, List<String>> components() {
        var result = new HashMap<String, List<String>>();
        TcModernApi.runtime().ifPresent(runtime -> { for (TcAspect aspect : TcAspects.snapshot(runtime)) result.put(aspect.tag(), aspect.components()); });
        return result;
    }

    // ---- items and creatures falling in ----

    /** An item entity inside the crucible. Called only while the crucible is boiling. */
    public void accept(ItemEntity entity) {
        if (entity.getPersistentData().getBoolean(EJECTED) || entity.isRemoved()) return;
        var runtime = TcModernApi.runtime().orElse(null);
        if (runtime == null) return;
        var stack = entity.getItem();
        if (stack.isEmpty()) return;
        if (entity.getOwner() instanceof ServerPlayer player) alchemist = player.getUUID();
        if (mode == Mode.SYNTHESIS) {
            if (materials.size() >= CrucibleRules.MAX_MATERIALS) { bounce(entity); return; }
            materials.add(stack.split(1));
            if (stack.isEmpty()) entity.discard(); else entity.setItem(stack);
            synthesisTimer = 0;
            sound(bubble(), 0.2F, 1.0F + level.random.nextFloat() * 0.4F);
            changed();
            return;
        }
        Map<String, Integer> aspects;
        try {
            aspects = ModernDeconstructionBlockEntity.itemAspects(runtime, stack);
        } catch (RuntimeException unreadable) {
            aspects = Map.of();
        }
        if (aspects.isEmpty()) { bounce(entity); return; }
        // As the original: every item of the stack dissolves, each delaying the next boil-off.
        for (int i = 0; i < stack.getCount(); i++) essentia.add(aspects);
        counter = CrucibleRules.AFTER_DISSOLVE;
        entity.discard();
        sound(bubble(), 0.2F, 1.0F + level.random.nextFloat() * 0.4F);
        changed();
    }

    /** What the original does with an item that has no aspects: it pops back out. */
    private void bounce(ItemEntity entity) {
        entity.setDeltaMovement((level.random.nextFloat() - level.random.nextFloat()) * 0.2F, 0.35F,
                (level.random.nextFloat() - level.random.nextFloat()) * 0.2F);
        level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS,
                0.2F, (level.random.nextFloat() - level.random.nextFloat()) * 0.7F + 1.0F);
    }

    /** Creatures standing in the boiling crucible are scalded, once every ten contacts as in the original. */
    public void scald(net.minecraft.world.entity.LivingEntity entity) {
        if (++contactDelay < 10) return;
        contactDelay = 0;
        entity.hurt(level.damageSources().inFire(), 1.0F);
        sound(SoundEvents.FIRE_EXTINGUISH, 0.4F, 2.0F + level.random.nextFloat() * 0.4F);
    }

    // ---- player interaction ----

    public boolean fill(ItemStack bucket) {
        if (!bucket.is(net.minecraft.world.item.Items.WATER_BUCKET) || water() >= CrucibleRules.WATER_CAPACITY) return false;
        tank.fill(new FluidStack(Fluids.WATER, CrucibleRules.WATER_CAPACITY), IFluidHandler.FluidAction.EXECUTE);
        sound(SoundEvents.BUCKET_EMPTY, 1.0F, 1.0F);
        return true;
    }

    /** A wand switches the mode; leaving synthesis gives the held materials back. */
    public void toggleMode(Player player) {
        if (mode == Mode.SYNTHESIS) ejectMaterials();
        mode = mode == Mode.DECOMPOSE ? Mode.SYNTHESIS : Mode.DECOMPOSE;
        synthesisTimer = 0;
        player.displayClientMessage(Component.translatable("intermediate_crucible.tc4modern.mode." + mode.name().toLowerCase(java.util.Locale.ROOT)), true);
        sound(SoundEvents.UI_BUTTON_CLICK.value(), 0.4F, 1.0F);
        changed();
    }

    /**
     * Reacts the held materials with the dissolved aspects. A recipe the alchemist has researched, whose aspects are all
     * there, gives its result; anything else ruins the materials into flux goo and gas.
     */
    public void synthesize(@Nullable ServerPlayer player) {
        if (materials.isEmpty()) return;
        var runtime = TcModernApi.runtime().orElse(null);
        if (runtime == null) return;
        if (player != null) alchemist = player.getUUID();
        var recipe = match(runtime, player);
        synthesisTimer = 0;
        counter = CrucibleRules.AFTER_SYNTHESIS;
        if (recipe.isPresent() && water() > 0) {
            var result = output(runtime, recipe.get().result());
            if (!result.isEmpty()) {
                essentia.take(recipe.get().aspects());
                tank.drain(CrucibleRules.SYNTHESIS_WATER, IFluidHandler.FluidAction.EXECUTE);
                materials.clear();
                eject(result);
                sound(bubble(), 0.4F, 1.0F);
                changed();
                return;
            }
        }
        int lost = 0;
        for (var material : materials) {
            try {
                for (int amount : ModernDeconstructionBlockEntity.itemAspects(runtime, material).values()) lost += amount;
            } catch (RuntimeException unreadable) { /* counted as nothing */ }
        }
        materials.clear();
        for (int i = 0; i < CrucibleEssentia.fluxSpills(lost); i++) spill(true);
        sound(SoundEvents.FIRE_EXTINGUISH, 0.6F, 0.8F + level.random.nextFloat() * 0.4F);
        if (player != null) player.displayClientMessage(Component.translatable("intermediate_crucible.tc4modern.failed"), true);
        changed();
    }

    private Optional<IntermediateRecipe> match(LegacyRuntime runtime, @Nullable ServerPlayer player) {
        var aspects = essentia.snapshot();
        for (var recipe : IntermediateRecipes.all()) {
            if (!recipe.matches(materials, (ingredient, stack) -> accepts(runtime, ingredient, stack))) continue;
            if (!recipe.aspects().entrySet().stream().allMatch(cost -> aspects.getOrDefault(cost.getKey(), 0) >= cost.getValue())) continue;
            if (player == null || !researched(runtime, player, recipe.research())) continue;
            return Optional.of(recipe);
        }
        return Optional.empty();
    }

    private static boolean researched(LegacyRuntime runtime, ServerPlayer player, String key) {
        try {
            return TcResearch.isComplete(runtime, player, key);
        } catch (RuntimeException unavailable) {
            return false;
        }
    }

    static boolean accepts(LegacyRuntime runtime, IntermediateRecipe.Ingredient ingredient, ItemStack stack) {
        if (ingredient instanceof IntermediateRecipe.Ingredient.Item item)
            return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(item.id());
        if (ingredient instanceof IntermediateRecipe.Ingredient.Tag tag)
            return stack.is(ItemTags.create(new ResourceLocation(tag.id())));
        var legacy = (IntermediateRecipe.Ingredient.Legacy) ingredient;
        try {
            var original = runtime.mappings().toLegacy(LegacyMappings.ITEM_STACK, stack.copyWithCount(1)).orElse(null);
            if (original == null) return false;
            var snapshot = runtime.snapshot(original);
            return snapshot.key().equals(legacy.key())
                    && (legacy.metadata() == IntermediateRecipe.WILDCARD || snapshot.metadata() == legacy.metadata());
        } catch (RuntimeException unmapped) {
            return false;
        }
    }

    private static ItemStack output(LegacyRuntime runtime, IntermediateRecipe.Output result) {
        try {
            if (result.item() != null) {
                var item = BuiltInRegistries.ITEM.getOptional(new ResourceLocation(result.item())).orElse(null);
                if (item == null) return ItemStack.EMPTY;
                var stack = new ItemStack(item, result.count());
                if (!result.nbt().isEmpty()) stack.setTag(TagParser.parseTag(result.nbt()));
                return stack;
            }
            var stack = new LegacyStack(result.legacyItem(), result.metadata(), result.count(), result.nbt());
            return runtime.mappings().toHost(LegacyMappings.ITEM_STACK, runtime.stack(stack), ItemStack.class).orElse(ItemStack.EMPTY);
        } catch (Exception invalid) {
            LogUtils.getLogger().error("Intermediate crucible cannot make {}: {}", result, invalid.toString());
            return ItemStack.EMPTY;
        }
    }

    /** Lifts an item out of the liquid, as TileCrucible.ejectItem does; the crucible will not take it back. */
    private void eject(ItemStack stack) {
        var entity = new ItemEntity(level, worldPosition.getX() + 0.5, worldPosition.getY() + 0.71, worldPosition.getZ() + 0.5, stack);
        entity.getPersistentData().putBoolean(EJECTED, true);
        entity.setDeltaMovement(0, 0.1, 0);
        level.addFreshEntity(entity);
    }

    private void ejectMaterials() {
        for (var material : materials) eject(material);
        materials.clear();
    }

    /** Sneaking with a wand empties the crucible as in the original: the water goes and the essentia spills as flux. */
    public void spillRemnants() {
        if (water() <= 0 && essentia.isEmpty()) return;
        tank.setFluid(FluidStack.EMPTY);
        int spills = essentia.total() / 2;
        for (int i = 0; i < spills; i++) spill(false);
        essentia.clear();
        changed();
    }

    /** Breaking the crucible spills its contents and drops the held materials. */
    public void onBroken() {
        spillRemnants();
        for (var material : materials) Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), material);
        materials.clear();
    }

    @Nullable private ServerPlayer owner() {
        return alchemist == null || level.getServer() == null ? null : level.getServer().getPlayerList().getPlayer(alchemist);
    }

    // ---- flux, as TileCrucible.spill ----

    /**
     * Puts flux goo or gas above the crucible, thickening what is already there, or in a random free block next to it.
     * The original does this only one time in four; a ruined synthesis always does.
     */
    void spill(boolean always) {
        if (!always && level.random.nextInt(4) != 0) return;
        var runtime = TcModernApi.runtime().orElse(null);
        if (runtime == null) return;
        var above = worldPosition.above();
        if (level.isEmptyBlock(above)) { place(runtime, above, level.random.nextBoolean() ? FLUX_GAS : FLUX_GOO, 0); return; }
        var state = level.getBlockState(above);
        for (String flux : List.of(FLUX_GOO, FLUX_GAS)) {
            for (int meta = 0; meta < CrucibleRules.MAX_FLUX_META; meta++) {
                int current = meta;
                if (runtime.hostBlock(flux, current, BlockState.class).map(state::equals).orElse(false)) {
                    place(runtime, above, flux, current + 1);
                    return;
                }
            }
        }
        var target = worldPosition.offset(level.random.nextInt(3) - 1, level.random.nextInt(3) - 1, level.random.nextInt(3) - 1);
        if (level.isEmptyBlock(target)) place(runtime, target, level.random.nextBoolean() ? FLUX_GAS : FLUX_GOO, 0);
    }

    private void place(LegacyRuntime runtime, BlockPos pos, String flux, int meta) {
        runtime.hostBlock(flux, meta, BlockState.class).ifPresent(state -> level.setBlock(pos, state, Block.UPDATE_ALL));
    }

    // ---- sounds ----

    private static SoundEvent bubble() {
        var original = ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("thaumcraft", "bubble"));
        return original != null ? original : SoundEvents.BUBBLE_COLUMN_BUBBLE_POP;
    }

    private void sound(SoundEvent sound, float volume, float pitch) {
        level.playSound(null, worldPosition, sound, SoundSource.BLOCKS, volume, pitch);
    }

    // ---- saving and client sync ----

    private void changed() {
        setChanged();
        sync();
    }

    private void sync() {
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putString("Mode", mode.name());
        tag.putShort("Heat", (short) heat);
        tag.put("Tank", tank.writeToNBT(new CompoundTag()));
        var aspects = new CompoundTag();
        essentia.snapshot().forEach(aspects::putInt);
        tag.put("Aspects", aspects);
        var held = new ListTag();
        for (var material : materials) held.add(material.save(new CompoundTag()));
        tag.put("Materials", held);
        tag.putInt("SynthesisTimer", synthesisTimer);
        tag.putLong("Counter", counter);
        if (alchemist != null) tag.putUUID("Alchemist", alchemist);
    }

    @Override public void load(CompoundTag tag) {
        super.load(tag);
        mode = "SYNTHESIS".equals(tag.getString("Mode")) ? Mode.SYNTHESIS : Mode.DECOMPOSE;
        heat = Math.max(0, Math.min(CrucibleRules.MAX_HEAT, tag.getShort("Heat")));
        tank.readFromNBT(tag.getCompound("Tank"));
        var aspects = new LinkedHashMap<String, Integer>();
        var stored = tag.getCompound("Aspects");
        for (String key : stored.getAllKeys()) aspects.put(key, stored.getInt(key));
        essentia.load(aspects);
        materials.clear();
        var held = tag.getList("Materials", Tag.TAG_COMPOUND);
        for (int i = 0; i < held.size() && materials.size() < CrucibleRules.MAX_MATERIALS; i++) {
            var material = ItemStack.of(held.getCompound(i));
            if (!material.isEmpty()) materials.add(material);
        }
        synthesisTimer = Math.max(0, tag.getInt("SynthesisTimer"));
        counter = tag.contains("Counter") ? tag.getLong("Counter") : CrucibleRules.START_COUNTER;
        alchemist = tag.hasUUID("Alchemist") ? tag.getUUID("Alchemist") : null;
    }

    @Override public CompoundTag getUpdateTag() { return saveWithoutMetadata(); }
    @Override public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }

    @Override public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        return cap == ForgeCapabilities.FLUID_HANDLER ? fluidCapability.cast() : super.getCapability(cap, side);
    }

    @Override public void invalidateCaps() { super.invalidateCaps(); fluidCapability.invalidate(); }
}
