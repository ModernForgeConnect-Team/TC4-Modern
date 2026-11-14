package github.com.gengyoubo.tcmodern.api.wand;

import github.com.gengyoubo.mfcl.api.LegacyObject;
import github.com.gengyoubo.mfcl.api.LegacyRuntime;
import github.com.gengyoubo.mfcl.api.LegacyStack;
import github.com.gengyoubo.mfcl.api.mla.LegacyConstructor;
import github.com.gengyoubo.mfcl.api.mla.LegacyMethod;
import github.com.gengyoubo.mfcl.api.mla.LegacyType;
import github.com.gengyoubo.tcmodern.api.TcModernApi;
import github.com.gengyoubo.tcmodern.api.aspect.TcAspectAccessors;
import github.com.gengyoubo.tcmodern.api.essentia.TcContainers;
import java.util.Map;
import java.util.Optional;
import net.minecraft.item.ItemStack;
import thaumcraft.api.ThaumcraftApiHelper;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.common.items.wands.ItemWandCasting;

/** Original casting wand Vis. Units are centivis (100 = 1 displayed Vis), not research points or essentia. */
public final class TcWandVis {
    private TcWandVis() { }
    public record Snapshot(int capacity, Map<String,Integer> amounts) { public Snapshot { amounts = Map.copyOf(amounts); } }
    /** The updated stack must be written back by the caller after a successful non-simulated operation. */
    public record Consumption(boolean success, LegacyStack stack) { }
    @LegacyType(type = ItemStack.class) public interface Stack {
        @LegacyMethod(name="getItem") LegacyObject item();
    }
    @LegacyType(type = ItemWandCasting.class) public interface Wand {
        @LegacyMethod(name="getMaxVis") int max(LegacyObject stack);
        @LegacyMethod(name="getAllVis") LegacyObject all(LegacyObject stack);
    }
    @LegacyType(type = AspectList.class) public interface Factory {
        @LegacyConstructor LegacyObject create();
    }
    @LegacyType(type = AspectList.class) public interface ListBuilder {
        @LegacyMethod(name="add") LegacyObject add(LegacyObject aspect, int amount);
    }
    @LegacyType(type = ThaumcraftApiHelper.class) public interface Helper {
        @LegacyMethod(name="consumeVisFromWand", isStatic=true)
        boolean consume(LegacyObject stack, LegacyObject player, LegacyObject aspects, boolean actual, boolean crafting);
    }
    private static void require(LegacyRuntime runtime) {
        if (!runtime.isOpen() || !runtime.loadedMods().contains(TcModernApi.LEGACY_MOD_ID)) throw new IllegalStateException("Thaumcraft runtime is not available");
    }
    public static Optional<Snapshot> snapshot(LegacyRuntime runtime, LegacyStack stack) {
        require(runtime);
        var old = runtime.stack(stack); var item = runtime.accessor(Stack.class, old).item();
        if (item == null || !runtime.values().isInstance(item, "thaumcraft.common.items.wands.ItemWandCasting")) return Optional.empty();
        var wand = runtime.accessor(Wand.class, item);
        return Optional.of(new Snapshot(wand.max(old), TcContainers.aspects(runtime, wand.all(old))));
    }
    /**
     * Applies TC4's discount and all-or-nothing consumption on a copy of the supplied wand. Use a server player on
     * its game thread. When simulate is true no Vis is spent. Costs are positive centivis per aspect; crafting selects
     * the original crafting modifier. Unknown tags fail before the original is called. The caller owns writeback and
     * synchronization of the returned stack; this method does not locate or update an inventory slot.
     */
    public static Consumption consume(LegacyRuntime runtime, Object hostPlayer, LegacyStack stack,
                                      Map<String,Integer> costs, boolean simulate, boolean crafting) {
        require(runtime);
        if (costs == null || costs.isEmpty()) throw new IllegalArgumentException("No Vis costs");
        var old = runtime.stack(stack);
        var item = runtime.accessor(Stack.class, old).item();
        if (item == null || !runtime.values().isInstance(item, "thaumcraft.common.items.wands.ItemWandCasting")) return new Consumption(false, stack);
        var list = runtime.accessor(Factory.class).create();
        var builder = runtime.accessor(ListBuilder.class, list);
        costs.forEach((tag, amount) -> {
            if (tag == null || amount == null || amount <= 0) throw new IllegalArgumentException("Invalid Vis cost");
            var aspect = runtime.accessor(TcAspectAccessors.AspectLookup.class).find(tag);
            if (aspect == null) throw new IllegalArgumentException("Unknown aspect: " + tag);
            builder.add(aspect, amount);
        });
        boolean success = runtime.withPlayer(hostPlayer, player -> runtime.accessor(Helper.class).consume(old, player, list, !simulate, crafting));
        return new Consumption(success, success && !simulate ? runtime.snapshot(old) : stack);
    }
}
