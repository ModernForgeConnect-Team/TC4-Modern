package github.com.gengyoubo.tcmodern.lib.creative;

import com.mojang.logging.LogUtils;
import github.com.gengyoubo.mfcl.api.LegacyFeatures;
import github.com.gengyoubo.mfcl.api.LegacyMappings;
import github.com.gengyoubo.mfcl.api.LegacyObject;
import github.com.gengyoubo.mfcl.api.LegacyRuntime;
import github.com.gengyoubo.mfcl.api.mla.LegacyMethod;
import github.com.gengyoubo.mfcl.api.mla.LegacyType;
import github.com.gengyoubo.tcmodern.api.TcModernApi;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.loading.FMLEnvironment;

/**
 * MFCL freezes each original creative tab when it captures the original declarations, before {@code whenReady}.
 * Variants the original {@code getSubItems} only produces later, such as the phials, mana beans and other
 * per-aspect items of aspects added through {@code TcAspects.register}, are therefore missing. This asks the
 * original again once the runtime is complete and adds what is new right after the item's existing entries, for
 * every addon's aspects alike; addons register nothing themselves.
 * Their color needs nothing here: MFCL tints every original stack through the original
 * {@code getColorFromItemStack}, which reads the aspect from the stack's own NBT.
 */
public final class LateCreativeVariants {
    private static volatile List<ItemStack> variants;

    private LateCreativeVariants() { }

    @LegacyType("net.minecraft.item.Item")
    interface OriginalItem {
        /** Null when the original lists the item in no creative tab. */
        @LegacyMethod(name = "func_77640_w", descriptor = "()Lnet/minecraft/creativetab/CreativeTabs;") LegacyObject tab();
        /** {@code getSubItems}: appends the item's creative variants for {@code tab} to {@code list}. */
        @LegacyMethod(name = "func_150895_a", descriptor = "(Lnet/minecraft/item/Item;Lnet/minecraft/creativetab/CreativeTabs;Ljava/util/List;)V")
        void subItems(LegacyObject item, LegacyObject tab, LegacyObject list);
    }

    /** Creative tabs are only built on a client; a dedicated server registers nothing. */
    public static void install(IEventBus modBus) {
        if (FMLEnvironment.dist.isClient()) modBus.addListener(LateCreativeVariants::build);
    }

    private static void build(BuildCreativeModeTabContentsEvent event) {
        var extra = variants();
        if (extra.isEmpty()) return;
        var entries = event.getEntries();
        // The last entry of each item this tab already lists; new variants follow it, in the original's order.
        var last = new HashMap<Item, ItemStack>();
        for (var entry : entries) last.put(entry.getKey().getItem(), entry.getKey());
        for (var variant : extra) {
            var after = last.get(variant.getItem());
            if (after == null || entries.contains(variant)) continue;
            var stack = variant.copy();
            entries.putAfter(after, stack, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
            last.put(stack.getItem(), stack);
        }
    }

    /** Every current creative variant of the original Thaumcraft items, computed once the runtime is complete. */
    private static List<ItemStack> variants() {
        var known = variants;
        if (known != null) return known;
        var runtime = TcModernApi.runtime().orElse(null);
        if (runtime == null || !runtime.supports(LegacyFeatures.MLA_COLLECTIONS_MUTABLE)) return List.of();
        var result = collect(runtime);
        variants = result;
        return result;
    }

    private static List<ItemStack> collect(LegacyRuntime runtime) {
        var result = new ArrayList<ItemStack>();
        for (String key : runtime.registryKeys(LegacyRuntime.Registry.ITEM)) {
            if (!key.startsWith(TcModernApi.LEGACY_MOD_ID + ":")) continue;
            var item = runtime.find(LegacyRuntime.Registry.ITEM, key).orElse(null);
            if (item == null) continue;
            try {
                var original = runtime.accessor(OriginalItem.class, item);
                var tab = original.tab();
                if (tab == null) continue;
                var list = runtime.values().newList(List.of());
                original.subItems(item, tab, list);
                for (Object stack : runtime.values().list(list)) {
                    if (!(stack instanceof LegacyObject legacy)) continue;
                    runtime.mappings().toHost(LegacyMappings.ITEM_STACK, legacy, ItemStack.class)
                            .filter(host -> !host.isEmpty()).ifPresent(host -> { host.setCount(1); result.add(host); });
                }
            } catch (RuntimeException unsupported) {
                LogUtils.getLogger().debug("TC4 creative variants skipped {}: {}", key, unsupported.toString());
            }
        }
        return List.copyOf(result);
    }
}
