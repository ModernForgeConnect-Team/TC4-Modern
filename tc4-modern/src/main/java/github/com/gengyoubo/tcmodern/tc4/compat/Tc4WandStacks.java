package github.com.gengyoubo.tcmodern.tc4.compat;

import com.mojang.logging.LogUtils;
import github.com.gengyoubo.mfcl.api.LegacyMappings;
import github.com.gengyoubo.mfcl.api.LegacyObject;
import github.com.gengyoubo.mfcl.api.LegacyRuntime;
import github.com.gengyoubo.mfcl.api.LegacyStack;
import github.com.gengyoubo.mfcl.api.mla.LegacyMethod;
import github.com.gengyoubo.mfcl.api.mla.LegacyType;
import github.com.gengyoubo.tcmodern.api.TcModernApi;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.world.item.ItemStack;

/**
 * Every wand, staff and sceptre build the original recipes can craft, fully charged, for the wand creative tab.
 * Each build maps to its own split host item ({@link Tc4ItemSplits}).
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
        for (String rod : Tc4ItemSplits.RODS) {
            boolean staff = rod.endsWith("_staff");
            for (String cap : Tc4ItemSplits.CAPS) {
                charged(runtime, cap, rod, false).ifPresent(staff ? staves::add : wands::add);
                if (!staff) charged(runtime, cap, rod, true).ifPresent(sceptres::add);
            }
        }
        var result = new ArrayList<ItemStack>(wands);
        result.addAll(staves);
        result.addAll(sceptres);
        // An empty tab is hidden from the creative screen; say why instead of vanishing silently.
        if (result.isEmpty()) LogUtils.getLogger().warn("TC4 wand tab: no wand build could be created; the tab is hidden. See debug.log.");
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
        return new LegacyStack(Tc4ItemSplits.WAND, 0, 1, snbt.append('}').toString());
    }

    private static Optional<ItemStack> host(LegacyRuntime runtime, LegacyStack stack) {
        try {
            return runtime.mappings().toHost(LegacyMappings.ITEM_STACK, runtime.stack(stack), ItemStack.class)
                    .filter(host -> !host.isEmpty());
        } catch (RuntimeException unsupported) {
            return Optional.empty();
        }
    }
}
