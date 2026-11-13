package github.com.gengyoubo.tcmodern.lib.wand;

import github.com.gengyoubo.mfcl.api.*;
import github.com.gengyoubo.mfcl.api.mla.*;
import github.com.gengyoubo.tcmodern.api.TcModernApi;
import github.com.gengyoubo.tcmodern.api.wand.TcWandVis;
import java.util.Map;
import java.util.Optional;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Modern ItemStack convenience for the original arcane workbench's wand check and Vis consumption. */
public final class TcWandCrafting {
    private TcWandCrafting() { }
    @LegacyType("thaumcraft.common.items.wands.ItemWandCasting") public interface Wand {
        @LegacyMethod(name="isStaff", descriptor="(Lnet/minecraft/item/ItemStack;)Z") boolean staff(LegacyObject stack);
        @LegacyMethod(name="isSceptre", descriptor="(Lnet/minecraft/item/ItemStack;)Z") boolean sceptre(LegacyObject stack);
    }
    /** True for a casting wand or sceptre accepted by TC4's workbench, but false for a staff. */
    public static boolean canCraftWith(ItemStack stack) { return kind(stack, false); }
    /** True for a wand that is neither a staff nor a sceptre. */
    public static boolean isNormalWand(ItemStack stack) { return kind(stack, true); }
    private static boolean kind(ItemStack stack, boolean normal) {
        if (stack == null || stack.isEmpty()) return false;
        var runtime = TcModernApi.runtime().orElse(null); if (runtime == null) return false;
        try {
            var old = runtime.mappings().toLegacy(LegacyMappings.ITEM_STACK, stack).orElse(null);
            if (old == null) return false;
            var item = runtime.accessor(TcWandVis.Stack.class, old).item();
            if (item == null || !runtime.values().isInstance(item, "thaumcraft.common.items.wands.ItemWandCasting")) return false;
            var wand = runtime.accessor(Wand.class, item);
            return !wand.staff(old) && (!normal || !wand.sceptre(old));
        } catch (RuntimeException unsupported) { return false; }
    }
    /** Never mutates wand. On success the caller must revalidate and write the returned copy to its slot. */
    public static Optional<ItemStack> pay(ServerPlayer player, ItemStack wand, Map<String,Integer> costs, boolean simulate) {
        if (player == null || wand == null || wand.isEmpty() || costs == null || costs.isEmpty()
                || costs.entrySet().stream().anyMatch(e -> e.getKey() == null || e.getValue() == null || e.getValue() <= 0)) return Optional.empty();
        var runtime = TcModernApi.runtime().orElse(null); if (runtime == null) return Optional.empty();
        var old = runtime.mappings().toLegacy(LegacyMappings.ITEM_STACK, wand.copy()).orElse(null);
        if (old == null) return Optional.empty();
        var item = runtime.accessor(TcWandVis.Stack.class, old).item();
        if (item == null || !runtime.values().isInstance(item, "thaumcraft.common.items.wands.ItemWandCasting")
                || runtime.accessor(Wand.class, item).staff(old)) return Optional.empty();
        TcWandVis.Consumption result;
        try { result = TcWandVis.consume(runtime, player, runtime.snapshot(old), costs, simulate, true); }
        catch (IllegalArgumentException invalid) { return Optional.empty(); }
        if (!result.success()) return Optional.empty();
        return simulate ? Optional.of(wand.copy()) : runtime.mappings().toHost(LegacyMappings.ITEM_STACK,
                runtime.stack(result.stack()), ItemStack.class);
    }
}
