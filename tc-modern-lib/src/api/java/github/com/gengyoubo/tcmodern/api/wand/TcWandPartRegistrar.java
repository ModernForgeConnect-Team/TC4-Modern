package github.com.gengyoubo.tcmodern.api.wand;

import github.com.gengyoubo.mfcl.api.*;
import github.com.gengyoubo.mfcl.api.mla.*;
import github.com.gengyoubo.tcmodern.api.TcModernApi;
import github.com.gengyoubo.tcmodern.api.aspect.TcAspectAccessors;
import github.com.gengyoubo.tcmodern.api.recipe.TcRecipeAccessors;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Registers original TC4 wand parts without linking old classes. Call on the owning thread after TC4 is ready. */
public final class TcWandPartRegistrar {
    private TcWandPartRegistrar() { }
    @LegacyType("thaumcraft.api.wands.WandRod") public interface RodFactory {
        @LegacyConstructor(descriptor="(Ljava/lang/String;ILnet/minecraft/item/ItemStack;I)V") LegacyObject create(String tag, int capacity, LegacyObject item, int cost);
    }
    @LegacyType("thaumcraft.api.wands.StaffRod") public interface StaffFactory {
        @LegacyConstructor(descriptor="(Ljava/lang/String;ILnet/minecraft/item/ItemStack;I)V") LegacyObject create(String tag, int capacity, LegacyObject item, int cost);
    }
    @LegacyType("thaumcraft.api.wands.WandCap") public interface CapFactory {
        @LegacyConstructor(descriptor="(Ljava/lang/String;FLjava/util/List;FLnet/minecraft/item/ItemStack;I)V") LegacyObject create(String tag, float base, LegacyObject aspects, float special, LegacyObject item, int cost);
    }
    @LegacyType("thaumcraft.api.wands.WandRod") public interface RodTexture {
        @LegacyMethod(name="setTexture", descriptor="(Lnet/minecraft/util/ResourceLocation;)V") void set(LegacyObject texture);
    }
    @LegacyType("thaumcraft.api.wands.WandCap") public interface CapTexture {
        @LegacyMethod(name="setTexture", descriptor="(Lnet/minecraft/util/ResourceLocation;)V") void set(LegacyObject texture);
    }
    private static void require(LegacyRuntime runtime, String tag, int cost) {
        if (!runtime.isOpen() || !runtime.loadedMods().contains(TcModernApi.LEGACY_MOD_ID)) throw new IllegalStateException("Thaumcraft runtime is unavailable");
        if (tag == null || !tag.matches("[a-z][a-z0-9_]*") || cost < 0) throw new IllegalArgumentException("Invalid part tag or craft cost");
    }
    private static LegacyObject texture(LegacyRuntime runtime, String texture) {
        if (texture == null) return null;
        String[] parts = texture.split(":", -1);
        if (parts.length != 2 || !parts[0].matches("[a-z0-9_.-]+") || !parts[1].matches("[a-z0-9_./-]+")) throw new IllegalArgumentException("Invalid texture: " + texture);
        return runtime.accessor(TcAspectAccessors.ResourceFactory.class).create(parts[0], parts[1]);
    }
    /** Returns the registry tag; staff constructors append _staff to baseTag. Never overwrites an existing part. */
    public static String rod(LegacyRuntime runtime, String baseTag, int capacity, LegacyStack item, int craftCost, boolean staff, String texture) {
        require(runtime, baseTag, craftCost);
        if (capacity < 0) throw new IllegalArgumentException("Negative rod capacity");
        String tag = staff ? baseTag + "_staff" : baseTag;
        if (runtime.values().map(runtime.accessor(TcRecipeAccessors.Rods.class).all()).containsKey(tag)) throw new IllegalArgumentException("Rod already registered: " + tag);
        var image = texture(runtime, texture);
        var material = item == null ? null : runtime.stack(item);
        var created = staff ? runtime.accessor(StaffFactory.class).create(baseTag, capacity, material, craftCost)
                : runtime.accessor(RodFactory.class).create(baseTag, capacity, material, craftCost);
        if (image != null) runtime.accessor(RodTexture.class, created).set(image);
        return tag;
    }
    /** TC4 supports a base multiplier and one special multiplier shared by the specified aspect tags. */
    public static String cap(LegacyRuntime runtime, String tag, float base, List<String> specialAspects, float special,
                             LegacyStack item, int craftCost, String texture) {
        require(runtime, tag, craftCost);
        if (!Float.isFinite(base) || !Float.isFinite(special) || base < 0 || special < 0) throw new IllegalArgumentException("Invalid cost multiplier");
        if (runtime.values().map(runtime.accessor(TcRecipeAccessors.Caps.class).all()).containsKey(tag)) throw new IllegalArgumentException("Cap already registered: " + tag);
        var aspects = new ArrayList<LegacyObject>();
        for (var name : List.copyOf(Objects.requireNonNull(specialAspects))) {
            var aspect = runtime.accessor(TcAspectAccessors.AspectLookup.class).find(name);
            if (aspect == null) throw new IllegalArgumentException("Unknown aspect: " + name);
            aspects.add(aspect);
        }
        var image = texture(runtime, texture);
        var material = item == null ? null : runtime.stack(item);
        var created = runtime.accessor(CapFactory.class).create(tag, base, runtime.values().newList(aspects), special, material, craftCost);
        if (image != null) runtime.accessor(CapTexture.class, created).set(image);
        return tag;
    }
}
