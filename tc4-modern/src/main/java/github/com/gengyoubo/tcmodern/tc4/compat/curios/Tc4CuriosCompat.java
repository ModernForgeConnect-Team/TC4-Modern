package github.com.gengyoubo.tcmodern.tc4.compat.curios;

import com.mojang.logging.LogUtils;
import github.com.gengyoubo.mfcl.api.LegacyFeatures;
import github.com.gengyoubo.mfcl.api.LegacyMappings;
import github.com.gengyoubo.mfcl.api.LegacyObject;
import github.com.gengyoubo.mfcl.api.LegacyRuntime;
import github.com.gengyoubo.mfcl.api.LegacyStack;
import github.com.gengyoubo.tcmodern.api.TcModernApi;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotResult;

/**
 * Assigns original Thaumcraft baubles and goggles to modern Curios slots.
 *
 * <p>Curios takes precedence over the original Baubles slots: this mod only drives the legacy
 * slot system when Curios is absent, in which case none of these classes are loaded. Original
 * TC4 items are registered by MFCL under opaque {@code mfcl:legacy/<hex>} keys, so static
 * {@code curios:} item tags cannot reference them. Slot assignment therefore uses a Curios
 * predicate that inspects the legacy identity through the public MLA API. Goggles worn in the
 * Curios head slot are handed to the original code as the worn helmet by {@link Tc4CuriosGoggles};
 * baubles are supplied through {@link Tc4CuriosBaubles} to the old inventory and worn-item logic.</p>
 */
public final class Tc4CuriosCompat {
    private static final ResourceLocation VALIDATOR = ResourceLocation.fromNamespaceAndPath("tc4modern", "thaumcraft_bauble");
    // Only the Goggles of Revealing: other IGoggles/IRevealer items are real helmets (fortress,
    // void robe) whose armor and set bonuses must not be worn from a Curios slot.
    private static final String GOGGLES = "thaumcraft.common.items.armor.ItemGoggles";
    private static final String LEGACY_BAUBLES_MOD_ID = "Baubles";
    static final String HEAD = "head";

    private static volatile Map<Item, String> itemToSlot = Map.of();

    private Tc4CuriosCompat() { }

    /** Registers the shared slot validator and scans the legacy runtime once it is ready. */
    public static void install() {
        LogUtils.getLogger().info("TC4 Curios: Curios detected; using modern Curios slots instead of the legacy Baubles slots.");
        CuriosApi.registerCurioPredicate(VALIDATOR, Tc4CuriosCompat::matchesAssignedSlot);
        TcModernApi.whenReady(Tc4CuriosCompat::collect);
    }

    /** The Curios slot identifier an original item is assigned to, or null. */
    static String slotOf(Item item) {
        return itemToSlot.get(item);
    }

    private static void collect(LegacyRuntime runtime) {
        boolean baubles = runtime.loadedMods().contains(LEGACY_BAUBLES_MOD_ID);
        boolean goggles = runtime.supports(LegacyFeatures.EQUIPMENT);
        if (!baubles || !runtime.supports(LegacyFeatures.EQUIPMENT_BAUBLES))
            throw new IllegalStateException("TC4 Curios requires MFCL equipment.baubles and the original or embedded Baubles API provider");
        if (!goggles) LogUtils.getLogger().info("TC4 Curios: this MFCL build has no equipment sources; goggles stay helmet-only.");
        Map<Item, String> classified = new HashMap<>();
        int candidates = 0;
        for (String key : runtime.registryKeys(LegacyRuntime.Registry.ITEM)) {
            if (!key.startsWith(TcModernApi.LEGACY_MOD_ID + ":")) continue;
            LegacyObject item = runtime.find(LegacyRuntime.Registry.ITEM, key).orElse(null);
            if (item == null) continue;
            try {
                String slot;
                if (goggles && runtime.values().isInstance(item, GOGGLES)) slot = HEAD;
                // Every original IBauble, wherever its class lives (the focus pouch is a wand item).
                else if (baubles && runtime.values().isInstance(item, Tc4BaubleAccessors.Bauble.class)) slot = baubleSlot(runtime, key, item);
                else continue;
                candidates++;
                if (slot == null) continue;
                var stack = runtime.stack(new LegacyStack(key, 0, 1, ""));
                runtime.mappings().toHost(LegacyMappings.ITEM_STACK, stack, ItemStack.class)
                        .map(ItemStack::getItem).ifPresent(modern -> classified.put(modern, slot));
            } catch (RuntimeException unsupported) {
                LogUtils.getLogger().debug("TC4 Curios skipped legacy item {}: {}", key, unsupported.toString());
            }
        }
        itemToSlot = Map.copyOf(classified);
        Tc4CuriosBaubles.install(runtime, classified);
        LogUtils.getLogger().info("TC4 Curios: classified {} of {} Thaumcraft items into Curios slots.", classified.size(), candidates);
        var head = classified.entrySet().stream().filter(entry -> HEAD.equals(entry.getValue())).map(Map.Entry::getKey).collect(Collectors.toUnmodifiableSet());
        if (!head.isEmpty()) Tc4CuriosGoggles.install(runtime, head);
    }

    private static String baubleSlot(LegacyRuntime runtime, String key, LegacyObject item) {
        var stack = runtime.stack(new LegacyStack(key, 0, 1, ""));
        var type = runtime.accessor(Tc4BaubleAccessors.Bauble.class, item).baubleType(stack);
        if (type == null) return null;
        return switch (runtime.accessor(Tc4BaubleAccessors.BaubleType.class, type).name()) {
            case "AMULET" -> "amulet";
            case "RING" -> "ring";
            case "BELT" -> "belt";
            default -> null;
        };
    }

    private static boolean matchesAssignedSlot(SlotResult result) {
        return result.slotContext().identifier().equals(itemToSlot.get(result.stack().getItem()));
    }
}
