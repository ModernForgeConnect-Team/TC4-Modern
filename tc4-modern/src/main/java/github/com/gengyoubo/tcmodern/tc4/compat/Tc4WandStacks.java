package github.com.gengyoubo.tcmodern.tc4.compat;

import com.mojang.logging.LogUtils;
import github.com.gengyoubo.mfcl.api.LegacyMappings;
import github.com.gengyoubo.mfcl.api.LegacyObject;
import github.com.gengyoubo.mfcl.api.LegacyRuntime;
import github.com.gengyoubo.mfcl.api.LegacyStack;
import github.com.gengyoubo.mfcl.api.mla.LegacyField;
import github.com.gengyoubo.mfcl.api.mla.LegacyMethod;
import github.com.gengyoubo.mfcl.api.mla.LegacyType;
import github.com.gengyoubo.tcmodern.api.TcModernApi;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.world.item.ItemStack;

/**
 * Every wand, staff and sceptre build the original recipes can craft, fully charged, for the wand creative tab.
 * The builds are the caps and rods registered in this game: TC4 adds copper and silver caps only when some mod
 * provides those ingots, and other addons may register more.
 * With MFCL's item splits each build is its own host item ({@link Tc4ItemSplits}); without them (MFCL 0.2.10)
 * every build is the original wand item, told apart by NBT.
 */
public final class Tc4WandStacks {
    /** TC4 4.2.3.5 {@code Aspect.getPrimalAspects()} tags, the NBT keys {@code ItemWandCasting.storeVis} writes. */
    private static final List<String> PRIMALS = List.of("aer", "terra", "ignis", "aqua", "ordo", "perditio");

    private Tc4WandStacks() { }

    @LegacyType("net.minecraft.item.ItemStack")
    public interface Holder {
        @LegacyMethod(name = "func_77973_b", descriptor = "()Lnet/minecraft/item/Item;") LegacyObject item();
    }

    @LegacyType("thaumcraft.common.items.wands.ItemWandCasting")
    public interface Wand {
        /** In centivis, already including the sceptre bonus. */
        @LegacyMethod(name = "getMaxVis", descriptor = "(Lnet/minecraft/item/ItemStack;)I") int maxVis(LegacyObject stack);
    }

    @LegacyType("thaumcraft.api.wands.WandCap")
    public interface Caps {
        @LegacyField(name = "caps", descriptor = "Ljava/util/LinkedHashMap;", isStatic = true) LegacyObject registered();
    }

    @LegacyType("thaumcraft.api.wands.WandRod")
    public interface Rods {
        @LegacyField(name = "rods", descriptor = "Ljava/util/LinkedHashMap;", isStatic = true) LegacyObject registered();
    }

    /** The starting iron-capped wooden wand, uncharged; empty when the runtime is not ready. */
    public static ItemStack icon() {
        return TcModernApi.runtime().flatMap(runtime -> host(runtime, build("iron", "wood", false, 0))).orElse(ItemStack.EMPTY);
    }

    /** Wands, then staves, then sceptres; staff cores make no sceptre in the original sceptre recipe. */
    public static List<ItemStack> all() {
        var runtime = TcModernApi.runtime().orElse(null);
        if (runtime == null) return List.of();
        var wands = new ArrayList<ItemStack>();
        var staves = new ArrayList<ItemStack>();
        var sceptres = new ArrayList<ItemStack>();
        var caps = caps(runtime);
        for (var rod : rods(runtime).entrySet()) {
            boolean staff = rod.getValue();
            for (String cap : caps) {
                charged(runtime, cap, rod.getKey(), false).ifPresent(staff ? staves::add : wands::add);
                if (!staff) charged(runtime, cap, rod.getKey(), true).ifPresent(sceptres::add);
            }
        }
        var result = new ArrayList<ItemStack>(wands);
        result.addAll(staves);
        result.addAll(sceptres);
        // An empty tab is hidden from the creative screen; say why instead of vanishing silently.
        if (result.isEmpty()) LogUtils.getLogger().warn("TC4 wand tab: no wand build could be created; the tab is hidden. See debug.log.");
        return result;
    }

    /** Registered cap tags: TC4's own first, in {@link Tc4WandBuilds} order, then any other in registration order. */
    static List<String> caps(LegacyRuntime runtime) {
        var registered = registered(runtime, () -> runtime.accessor(Caps.class).registered());
        return registered == null ? Tc4WandBuilds.CAPS : List.copyOf(ordered(registered, Tc4WandBuilds.CAPS).keySet());
    }

    /** Registered rod tags, ordered like {@link #caps}, each with whether it is a staff core. */
    static Map<String, Boolean> rods(LegacyRuntime runtime) {
        var registered = registered(runtime, () -> runtime.accessor(Rods.class).registered());
        var result = new LinkedHashMap<String, Boolean>();
        if (registered == null) {
            for (String rod : Tc4WandBuilds.RODS) result.put(rod, rod.endsWith("_staff"));
            return result;
        }
        for (var rod : ordered(registered, Tc4WandBuilds.RODS).entrySet())
            result.put(rod.getKey(), rod.getValue() instanceof LegacyObject core && runtime.values().isInstance(core, "thaumcraft.api.wands.StaffRod"));
        return result;
    }

    /** A static TC4 registry map by tag, or null when this runtime cannot read it (then TC4's own fixed builds are used). */
    private static Map<Object, Object> registered(LegacyRuntime runtime, java.util.function.Supplier<LegacyObject> field) {
        try {
            var map = field.get();
            return map == null ? null : runtime.values().map(map);
        } catch (RuntimeException unsupported) {
            LogUtils.getLogger().warn("TC4 wand tab: cannot read the registered wand parts, listing TC4's own builds: {}", unsupported.toString());
            return null;
        }
    }

    private static Map<String, Object> ordered(Map<Object, Object> registered, List<String> known) {
        var result = new LinkedHashMap<String, Object>();
        for (String tag : known) if (registered.containsKey(tag)) result.put(tag, registered.get(tag));
        for (var entry : registered.entrySet()) if (entry.getKey() instanceof String tag) result.putIfAbsent(tag, entry.getValue());
        return result;
    }

    private static Optional<ItemStack> charged(LegacyRuntime runtime, String cap, String rod, boolean sceptre) {
        try {
            var empty = runtime.stack(build(cap, rod, sceptre, 0));
            var item = runtime.accessor(Holder.class, empty).item();
            int max = item == null ? 0 : runtime.accessor(Wand.class, item).maxVis(empty);
            return host(runtime, build(cap, rod, sceptre, max));
        } catch (RuntimeException unsupported) {
            LogUtils.getLogger().debug("TC4 wand tab skipped {}/{}{}: {}", cap, rod, sceptre ? "/sceptre" : "", unsupported.toString());
            return Optional.empty();
        }
    }

    private static LegacyStack build(String cap, String rod, boolean sceptre, int vis) {
        var snbt = new StringBuilder("{cap:\"").append(cap).append("\",rod:\"").append(rod).append('"');
        if (sceptre) snbt.append(",sceptre:1b");
        if (vis > 0) for (String primal : PRIMALS) snbt.append(',').append(primal).append(':').append(vis);
        return new LegacyStack(Tc4WandBuilds.WAND, 0, 1, snbt.append('}').toString());
    }

    private static Optional<ItemStack> host(LegacyRuntime runtime, LegacyStack stack) {
        try {
            var host = runtime.mappings().toHost(LegacyMappings.ITEM_STACK, runtime.stack(stack), ItemStack.class)
                    .filter(candidate -> !candidate.isEmpty());
            if (host.isEmpty()) LogUtils.getLogger().debug("TC4 wand tab: no host stack for {}", stack);
            return host;
        } catch (RuntimeException unsupported) {
            LogUtils.getLogger().debug("TC4 wand tab: cannot map {}: {}", stack, unsupported.toString());
            return Optional.empty();
        }
    }
}
