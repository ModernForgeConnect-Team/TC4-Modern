package github.com.gengyoubo.tcmodern.tc4.research;

import github.com.gengyoubo.mfcl.api.LegacyMappings;
import github.com.gengyoubo.tcmodern.api.TcModernApi;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Which host items are original research notes or scribing tools. The answer depends only on the original item
 * class, so it is cached per host item; both sides ask while validating slots.
 */
public final class ResearchItems {
    private static final Map<Item, Boolean> NOTES = new ConcurrentHashMap<>(), TOOLS = new ConcurrentHashMap<>();

    private ResearchItems() {}

    public static boolean isNote(ItemStack stack) { return is(stack, NOTES, DeskAccessors.NOTES); }
    public static boolean isScribeTools(ItemStack stack) { return is(stack, TOOLS, DeskAccessors.SCRIBE_TOOLS); }

    private static boolean is(ItemStack stack, Map<Item, Boolean> cache, String type) {
        if (stack.isEmpty()) return false;
        var cached = cache.get(stack.getItem());
        if (cached != null) return cached;
        var available = TcModernApi.runtime();
        // Not cached: the runtime may simply not be up yet.
        if (available.isEmpty()) return false;
        var runtime = available.get();
        boolean result;
        try {
            var legacy = runtime.mappings().toLegacy(LegacyMappings.ITEM_STACK, stack.copyWithCount(1)).orElse(null);
            var item = legacy == null ? null : runtime.accessor(DeskAccessors.StackView.class, legacy).item();
            result = item != null && runtime.values().isInstance(item, type);
        } catch (RuntimeException modernOnly) {
            result = false;
        }
        cache.put(stack.getItem(), result);
        return result;
    }
}
