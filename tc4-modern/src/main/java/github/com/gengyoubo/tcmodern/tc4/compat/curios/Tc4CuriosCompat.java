package github.com.gengyoubo.tcmodern.tc4.compat.curios;

import com.mojang.logging.LogUtils;
import github.com.gengyoubo.mfcl.api.LegacyFeatures;
import github.com.gengyoubo.mfcl.api.LegacyMappings;
import github.com.gengyoubo.mfcl.api.LegacyObject;
import github.com.gengyoubo.mfcl.api.LegacyRuntime;
import github.com.gengyoubo.mfcl.api.LegacyStack;
import github.com.gengyoubo.tcmodern.api.TcModernApi;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotResult;

/**
 * Assigns every legacy IBauble (including addons) and TC4 goggles to modern Curios slots.
 *
 * <p>Curios takes precedence over the original Baubles slots: this mod only drives the legacy
 * slot system when Curios is absent, in which case none of these classes are loaded. Original
 * items are registered by MFCL, so slot assignment uses a Curios predicate that calls the
 * original IBauble with the actual stack through the public MLA API. Goggles worn in the
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

    private record Classification(LegacyRuntime runtime, Map<Item, Tc4BaubleAccessors.Bauble> baubles, Set<Item> goggles) { }
    private static volatile Classification classification = new Classification(null, Map.of(), Set.of());

    private Tc4CuriosCompat() { }

    /** Registers the shared slot validator and scans the legacy runtime once it is ready. */
    public static void install() {
        LogUtils.getLogger().info("TC4 Curios: Curios detected; using modern Curios slots instead of the legacy Baubles slots.");
        CuriosApi.registerCurioPredicate(VALIDATOR, Tc4CuriosCompat::matchesAssignedSlot);
        TcModernApi.whenReady(Tc4CuriosCompat::collect);
    }

    /** Slot type can depend on original metadata/NBT, so never cache it by Item alone. */
    static String slotOf(ItemStack stack) {
        if (stack.isEmpty()) return null;
        var known = classification;
        if (known.goggles().contains(stack.getItem())) return HEAD;
        var bauble = known.baubles().get(stack.getItem());
        if (bauble == null) return null;
        try {
            var runtime = known.runtime();
            var old = runtime.mappings().toLegacy(LegacyMappings.ITEM_STACK, stack).orElse(null);
            if (old == null) return null;
            var type = bauble.baubleType(old);
            if (type == null) return null;
            return switch (runtime.accessor(Tc4BaubleAccessors.BaubleType.class, type).name()) {
                case "AMULET" -> "amulet";
                case "RING" -> "ring";
                case "BELT" -> "belt";
                default -> null;
            };
        } catch (RuntimeException unsupported) {
            LogUtils.getLogger().debug("TC4 Curios cannot determine legacy bauble type: {}", unsupported.toString());
            return null;
        }
    }

    static Tc4BaubleAccessors.Bauble baubleOf(Item item) { return classification.baubles().get(item); }

    private static void collect(LegacyRuntime runtime) {
        boolean baubles = runtime.loadedMods().contains(LEGACY_BAUBLES_MOD_ID);
        boolean goggles = runtime.supports(LegacyFeatures.EQUIPMENT);
        if (!baubles || !runtime.supports(LegacyFeatures.EQUIPMENT_BAUBLES))
            throw new IllegalStateException("TC4 Curios requires MFCL equipment.baubles and the original or embedded Baubles API provider");
        if (!goggles) LogUtils.getLogger().info("TC4 Curios: this MFCL build has no equipment sources; goggles stay helmet-only.");
        Map<Item, Tc4BaubleAccessors.Bauble> classified = new HashMap<>();
        Set<Item> head = new HashSet<>();
        int candidates = 0;
        for (String key : runtime.registryKeys(LegacyRuntime.Registry.ITEM)) {
            LegacyObject item = runtime.find(LegacyRuntime.Registry.ITEM, key).orElse(null);
            if (item == null) continue;
            try {
                boolean isGoggles = goggles && runtime.values().isInstance(item, GOGGLES);
                boolean isBauble = runtime.values().isInstance(item, Tc4BaubleAccessors.Bauble.class);
                if (!isGoggles && !isBauble) continue;
                candidates++;
                var stack = runtime.stack(new LegacyStack(key, 0, 1, ""));
                runtime.mappings().toHost(LegacyMappings.ITEM_STACK, stack, ItemStack.class)
                        .filter(modern -> !modern.isEmpty()).map(ItemStack::getItem).ifPresent(modern -> {
                            if (isGoggles) head.add(modern);
                            else classified.put(modern, runtime.accessor(Tc4BaubleAccessors.Bauble.class, item));
                        });
            } catch (RuntimeException unsupported) {
                LogUtils.getLogger().debug("TC4 Curios skipped legacy item {}: {}", key, unsupported.toString());
            }
        }
        classification = new Classification(runtime, Map.copyOf(classified), Set.copyOf(head));
        Tc4CuriosBaubles.install(runtime, classified.keySet());
        LogUtils.getLogger().info("TC4 Curios: connected {} legacy IBauble items (all mod namespaces) and {} goggles from {} candidates.",
                classified.size(), head.size(), candidates);
        if (!head.isEmpty()) Tc4CuriosGoggles.install(runtime, head);
    }

    private static boolean matchesAssignedSlot(SlotResult result) {
        return result.slotContext().identifier().equals(slotOf(result.stack()));
    }
}
