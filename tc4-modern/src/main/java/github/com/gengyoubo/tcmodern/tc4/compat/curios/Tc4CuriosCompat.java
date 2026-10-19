package github.com.gengyoubo.tcmodern.tc4.compat.curios;

import com.mojang.logging.LogUtils;
import github.com.gengyoubo.mfcl.api.LegacyMappings;
import github.com.gengyoubo.mfcl.api.LegacyObject;
import github.com.gengyoubo.mfcl.api.LegacyRuntime;
import github.com.gengyoubo.mfcl.api.LegacyStack;
import github.com.gengyoubo.tcmodern.api.TcModernApi;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotResult;

/**
 * Assigns original Thaumcraft baubles to modern Curios slots.
 *
 * <p>Curios takes precedence over the original Baubles slots: this mod only drives the legacy
 * slot system when Curios is absent, in which case none of these classes are loaded. Original
 * TC4 items are registered by MFCL under opaque {@code mfcl:legacy/<hex>} keys, so static
 * {@code curios:} item tags cannot reference them. Slot assignment therefore uses a Curios
 * predicate that inspects the legacy identity through the public MLA API. Only the modern side
 * placement is bridged: the original Baubles inventory and its worn-item effects still run in the
 * isolated legacy runtime and are not synchronized with Curios.</p>
 */
public final class Tc4CuriosCompat {
    private static final ResourceLocation VALIDATOR = ResourceLocation.fromNamespaceAndPath("tc4modern", "thaumcraft_bauble");
    private static final String BAUBLE_PACKAGE = "thaumcraft.common.items.baubles";
    private static final String LEGACY_BAUBLES_MOD_ID = "Baubles";

    private static volatile Map<Item, String> itemToSlot = Map.of();

    private Tc4CuriosCompat() { }

    /** Registers the shared slot validator and scans the legacy runtime once it is ready. */
    public static void install() {
        LogUtils.getLogger().info("TC4 Curios: Curios detected; using modern Curios slots instead of the legacy Baubles slots.");
        CuriosApi.registerCurioPredicate(VALIDATOR, Tc4CuriosCompat::matchesAssignedSlot);
        TcModernApi.whenReady(Tc4CuriosCompat::collect);
    }

    private static void collect(LegacyRuntime runtime) {
        if (!runtime.loadedMods().contains(LEGACY_BAUBLES_MOD_ID)) {
            LogUtils.getLogger().info("TC4 Curios: legacy Baubles is not loaded; no Thaumcraft baubles classified.");
            return;
        }
        Map<Item, String> classified = new HashMap<>();
        int candidates = 0;
        for (String key : runtime.registryKeys(LegacyRuntime.Registry.ITEM)) {
            if (!key.startsWith(TcModernApi.LEGACY_MOD_ID + ":")) continue;
            LegacyObject item = runtime.find(LegacyRuntime.Registry.ITEM, key).orElse(null);
            if (item == null || !item.typeName().startsWith(BAUBLE_PACKAGE)) continue;
            candidates++;
            try {
                var stack = runtime.stack(new LegacyStack(key, 0, 1, ""));
                var type = runtime.accessor(Tc4BaubleAccessors.Bauble.class, item).baubleType(stack);
                if (type == null) continue;
                String slot = switch (runtime.accessor(Tc4BaubleAccessors.BaubleType.class, type).name()) {
                    case "AMULET" -> "amulet";
                    case "RING" -> "ring";
                    case "BELT" -> "belt";
                    default -> null;
                };
                if (slot == null) continue;
                runtime.mappings().toHost(LegacyMappings.ITEM_STACK, stack, ItemStack.class)
                        .map(ItemStack::getItem).ifPresent(modern -> classified.put(modern, slot));
            } catch (RuntimeException unsupported) {
                LogUtils.getLogger().debug("TC4 Curios skipped legacy bauble {}: {}", key, unsupported.toString());
            }
        }
        itemToSlot = Map.copyOf(classified);
        LogUtils.getLogger().info("TC4 Curios: classified {} of {} Thaumcraft bauble items into Curios slots.", classified.size(), candidates);
    }

    private static boolean matchesAssignedSlot(SlotResult result) {
        return result.slotContext().identifier().equals(itemToSlot.get(result.stack().getItem()));
    }
}
