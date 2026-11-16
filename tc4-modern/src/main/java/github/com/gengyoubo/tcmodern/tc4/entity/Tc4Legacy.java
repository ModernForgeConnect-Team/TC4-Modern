package github.com.gengyoubo.tcmodern.tc4.entity;

import com.mojang.logging.LogUtils;
import github.com.gengyoubo.mfcl.api.LegacyFeatures;
import github.com.gengyoubo.mfcl.api.LegacyMappings;
import github.com.gengyoubo.mfcl.api.LegacyObject;
import github.com.gengyoubo.mfcl.api.LegacyRuntime;
import github.com.gengyoubo.mfcl.api.LegacyStack;
import github.com.gengyoubo.mfcl.api.mla.LegacyField;
import github.com.gengyoubo.mfcl.api.mla.LegacyMethod;
import github.com.gengyoubo.mfcl.api.mla.LegacyType;
import github.com.gengyoubo.tcmodern.api.TcModernApi;
import github.com.gengyoubo.tcmodern.api.aspect.TcAspect;
import github.com.gengyoubo.tcmodern.api.aspect.TcAspects;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

/** Small bridges to TC4's own items, sounds, effects and entities used by the modern entity hosts. */
public final class Tc4Legacy {
    private Tc4Legacy() { }
    private static final Map<String, SoundEvent> SOUNDS = new ConcurrentHashMap<>();
    private static final Map<String, ItemStack> STACKS = new ConcurrentHashMap<>();
    private static final Map<String, Optional<MobEffect>> EFFECTS = new ConcurrentHashMap<>();
    private static volatile List<TcAspect> aspects;
    private static volatile long lastAspectAttempt;

    /** Registers {@code factory} as the modern host of an exact TC4 class; a failing factory falls back to MFCL's own host. */
    public static void host(LegacyRuntime runtime, String legacyClass, BiFunction<ServerLevel, LegacyObject, Entity> factory) {
        if (!runtime.supports(LegacyFeatures.ENTITY_HOSTS)) return;
        runtime.entityHosts().register(legacyClass, (level, original) -> {
            try {
                var host = factory.apply((ServerLevel) level, original);
                if (Boolean.getBoolean("tc4modern.entitySmokeTest")) LogUtils.getLogger().info("TC4 host factory {} -> {}", legacyClass, host);
                return host;
            } catch (RuntimeException failure) {
                LogUtils.getLogger().warn("Could not create the modern host of {}; MFCL keeps the original", legacyClass, failure);
                return null;
            }
        });
    }

    /** A sound from TC4's own sounds.json, for example {@code "zap"}. */
    public static SoundEvent sound(String name) {
        return SOUNDS.computeIfAbsent(name, key -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("thaumcraft", key)));
    }

    /** A host copy of an original TC4 stack, or empty while the runtime is unavailable. */
    public static ItemStack stack(String key, int meta, int count) { return stack(key, meta, count, ""); }

    public static ItemStack stack(String key, int meta, int count, String snbt) {
        String cacheKey = key + "@" + meta + "#" + snbt;
        var prototype = STACKS.get(cacheKey);
        if (prototype == null) {
            var runtime = TcModernApi.runtime().orElse(null);
            if (runtime == null) return ItemStack.EMPTY;
            try {
                prototype = runtime.mappings().toHost(LegacyMappings.ITEM_STACK, runtime.stack(new LegacyStack(key, meta, 1, snbt)), ItemStack.class)
                        .orElse(ItemStack.EMPTY);
            } catch (RuntimeException failure) {
                prototype = ItemStack.EMPTY;
            }
            if (prototype.isEmpty()) return ItemStack.EMPTY;
            STACKS.put(cacheKey, prototype);
        }
        var result = prototype.copy();
        result.setCount(Math.max(1, Math.min(count, result.getMaxStackSize())));
        return result;
    }

    /** The original identity of a host stack, or empty for a non-TC4 or unreadable stack. */
    public static Optional<LegacyStack> legacy(ItemStack stack) {
        if (stack.isEmpty()) return Optional.empty();
        var runtime = TcModernApi.runtime().orElse(null);
        if (runtime == null) return Optional.empty();
        try {
            return runtime.mappings().toLegacy(LegacyMappings.ITEM_STACK, stack).map(runtime::snapshot);
        } catch (RuntimeException failure) {
            return Optional.empty();
        }
    }

    public static boolean is(ItemStack stack, String key) { return legacy(stack).map(s -> s.key().equals(key)).orElse(false); }

    public static boolean is(ItemStack stack, String key, int meta) {
        return legacy(stack).map(s -> s.key().equals(key) && s.metadata() == meta).orElse(false);
    }

    public static boolean isNamed(ItemStack stack, String hostPath) {
        var id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id != null && id.getNamespace().equals("thaumcraft") && id.getPath().equals(hostPath);
    }

    /** A TC4 item that carries an aspect list, such as wisp essence or a mana bean. */
    public static ItemStack withAspect(String key, String aspect, int amount) {
        return stack(key, 0, 1, "{Aspects:[{key:\"" + aspect + "\",amount:" + amount + "}]}");
    }

    /** A damage source of any type with both entities, which DamageSources only offers for some types. */
    public static net.minecraft.world.damagesource.DamageSource damage(Level level, net.minecraft.resources.ResourceKey<net.minecraft.world.damagesource.DamageType> type,
            @Nullable Entity direct, @Nullable Entity causing) {
        return new net.minecraft.world.damagesource.DamageSource(level.registryAccess()
                .registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE).getHolderOrThrow(type), direct, causing);
    }

    public static void drop(Entity entity, ItemStack stack, float height) {
        if (!stack.isEmpty()) entity.spawnAtLocation(stack, height);
    }

    /** TC4 potions keep their original translation keys, for example {@code potion.fluxtaint}. */
    @Nullable public static MobEffect effect(String descriptionId) {
        return EFFECTS.computeIfAbsent(descriptionId, id -> ForgeRegistries.MOB_EFFECTS.getValues().stream()
                .filter(effect -> id.equals(effect.getDescriptionId())).findFirst()).orElse(null);
    }

    @Nullable public static BlockState block(String key, int meta) {
        return TcModernApi.runtime().flatMap(runtime -> {
            try { return runtime.hostBlock(key, meta, BlockState.class); }
            catch (RuntimeException failure) { return Optional.empty(); }
        }).orElse(null);
    }

    public static List<TcAspect> aspects() {
        var known = aspects;
        if (known != null && !known.isEmpty()) return known;
        long now = System.currentTimeMillis();
        if (known != null && now - lastAspectAttempt < 5000) return known;
        lastAspectAttempt = now;
        try {
            known = TcModernApi.runtime().map(TcAspects::snapshot).orElse(List.of());
        } catch (RuntimeException failure) {
            known = List.of();
        }
        aspects = known;
        return known;
    }

    @Nullable public static TcAspect aspect(String tag) {
        if (tag == null || tag.isEmpty()) return null;
        for (var aspect : aspects()) if (aspect.tag().equals(tag)) return aspect;
        return null;
    }

    /** The host of an original entity: MFCL gives hosts their original's entity id. */
    @Nullable public static Entity hostOf(LegacyRuntime runtime, Level level, @Nullable LegacyObject original) {
        if (original == null) return null;
        try {
            return level.getEntity(runtime.accessor(OldEntity.class, original).id());
        } catch (RuntimeException failure) {
            return null;
        }
    }

    @Nullable public static Entity thrower(LegacyRuntime runtime, Level level, LegacyObject throwable) {
        try { return hostOf(runtime, level, runtime.accessor(OldThrowable.class, throwable).thrower()); }
        catch (RuntimeException failure) { return null; }
    }

    @Nullable public static Entity shooter(LegacyRuntime runtime, Level level, LegacyObject arrow) {
        try { return hostOf(runtime, level, runtime.accessor(OldArrow.class, arrow).shooter()); }
        catch (RuntimeException failure) { return null; }
    }

    public static ItemStack hostStack(LegacyRuntime runtime, @Nullable LegacyObject stack) {
        if (stack == null) return ItemStack.EMPTY;
        try { return runtime.mappings().toHost(LegacyMappings.ITEM_STACK, stack, ItemStack.class).orElse(ItemStack.EMPTY); }
        catch (RuntimeException failure) { return ItemStack.EMPTY; }
    }

    private static final EquipmentSlot[] OLD_SLOTS = { EquipmentSlot.MAINHAND, EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD };

    /** Copies the original's held item and armour; false when the original carried nothing. */
    public static boolean copyEquipment(LegacyRuntime runtime, LegacyObject original, Mob host) {
        boolean any = false;
        try {
            var old = runtime.accessor(OldLiving.class, original);
            for (int i = 0; i < OLD_SLOTS.length; i++) {
                var stack = hostStack(runtime, old.equipment(i));
                if (stack.isEmpty()) continue;
                host.setItemSlot(OLD_SLOTS[i], stack);
                any = true;
            }
        } catch (RuntimeException failure) {
            return any;
        }
        return any;
    }

    /** The original's custom name, if it has one. */
    public static void copyName(LegacyRuntime runtime, LegacyObject original, Entity host) {
        try {
            var old = runtime.accessor(OldLiving.class, original);
            if (old.hasCustomName()) host.setCustomName(net.minecraft.network.chat.Component.literal(old.customName()));
        } catch (RuntimeException ignored) {
            // Names are cosmetic.
        }
    }

    @LegacyType("net.minecraft.entity.Entity") public interface OldEntity {
        @LegacyMethod(name = "func_145782_y", descriptor = "()I") int id();
        @LegacyMethod(name = "func_70096_w", descriptor = "()Lnet/minecraft/entity/DataWatcher;") LegacyObject watcher();
    }
    @LegacyType("net.minecraft.entity.DataWatcher") public interface OldWatcher {
        @LegacyMethod(name = "func_75683_a", descriptor = "(I)B") byte getByte(int id);
        @LegacyMethod(name = "func_75693_b", descriptor = "(I)S") short getShort(int id);
    }

    /** A byte the original keeps only in its DataWatcher, such as a boss title; 0 when unreadable. */
    public static int watcherByte(LegacyRuntime runtime, LegacyObject original, int id) {
        try { return runtime.accessor(OldWatcher.class, runtime.accessor(OldEntity.class, original).watcher()).getByte(id); }
        catch (RuntimeException failure) { return 0; }
    }
    @LegacyType("net.minecraft.entity.projectile.EntityThrowable") public interface OldThrowable {
        @LegacyMethod(name = "func_85052_h", descriptor = "()Lnet/minecraft/entity/EntityLivingBase;") LegacyObject thrower();
    }
    @LegacyType("net.minecraft.entity.projectile.EntityArrow") public interface OldArrow {
        @LegacyField(name = "field_70250_c", descriptor = "Lnet/minecraft/entity/Entity;") LegacyObject shooter();
        @LegacyMethod(name = "func_70242_d", descriptor = "()D") double damage();
        @LegacyMethod(name = "func_70241_g", descriptor = "()Z") boolean critical();
        @LegacyField(name = "field_70251_a", descriptor = "I") int pickup();
    }
    @LegacyType("net.minecraft.entity.EntityLiving") public interface OldLiving {
        @LegacyMethod(name = "func_71124_b", descriptor = "(I)Lnet/minecraft/item/ItemStack;") LegacyObject equipment(int slot);
        @LegacyMethod(name = "func_94056_bM", descriptor = "()Z") boolean hasCustomName();
        @LegacyMethod(name = "func_94057_bL", descriptor = "()Ljava/lang/String;") String customName();
        @LegacyMethod(name = "func_110172_bL", descriptor = "()Lnet/minecraft/util/ChunkCoordinates;") LegacyObject home();
        @LegacyMethod(name = "func_110174_bM", descriptor = "()F") float homeDistance();
    }
    @LegacyType("net.minecraft.util.ChunkCoordinates") public interface OldCoordinates {
        @LegacyField(name = "field_71574_a", descriptor = "I") int x();
        @LegacyField(name = "field_71572_b", descriptor = "I") int y();
        @LegacyField(name = "field_71573_c", descriptor = "I") int z();
    }
    @LegacyType("net.minecraft.entity.item.EntityItem") public interface OldItem {
        @LegacyMethod(name = "func_92059_d", descriptor = "()Lnet/minecraft/item/ItemStack;") LegacyObject item();
        @LegacyField(name = "field_70292_b", descriptor = "I") int age();
        @LegacyField(name = "field_145804_b", descriptor = "I") int pickupDelay();
    }

    /** TC4's HomeX/Y/Z/D tags; vanilla does not save a mob's home restriction. */
    public static void saveHome(net.minecraft.world.entity.PathfinderMob mob, net.minecraft.nbt.CompoundTag tag) {
        if (!mob.hasRestriction()) return;
        var home = mob.getRestrictCenter();
        tag.putInt("HomeD", (int) mob.getRestrictRadius());
        tag.putInt("HomeX", home.getX()); tag.putInt("HomeY", home.getY()); tag.putInt("HomeZ", home.getZ());
    }

    public static void loadHome(net.minecraft.world.entity.PathfinderMob mob, net.minecraft.nbt.CompoundTag tag) {
        if (tag.contains("HomeD")) mob.restrictTo(new net.minecraft.core.BlockPos(tag.getInt("HomeX"), tag.getInt("HomeY"), tag.getInt("HomeZ")), tag.getInt("HomeD"));
    }

    /** Whether the level is TC4's Outer Lands, where eldritch creatures are at full strength. */
    public static boolean isOuterLands(Level level) { return level.dimension().location().getNamespace().equals("thaumcraft"); }

    /** The original's home restriction (cultists, guardians, bosses), applied to a modern pathfinding mob. */
    public static void copyHome(LegacyRuntime runtime, LegacyObject original, net.minecraft.world.entity.PathfinderMob host) {
        try {
            var old = runtime.accessor(OldLiving.class, original);
            float distance = old.homeDistance();
            var home = old.home();
            if (home == null || distance <= 0) return;
            var c = runtime.accessor(OldCoordinates.class, home);
            host.restrictTo(new net.minecraft.core.BlockPos(c.x(), c.y(), c.z()), (int) distance);
        } catch (RuntimeException ignored) {
            // Unrestricted is the original default.
        }
    }
}
