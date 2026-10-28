package github.com.gengyoubo.tcmodern.tc4.compat.curios;

import com.mojang.logging.LogUtils;
import github.com.gengyoubo.mfcl.api.LegacyEquipment;
import github.com.gengyoubo.mfcl.api.LegacyEvents;
import github.com.gengyoubo.mfcl.api.LegacyMappings;
import github.com.gengyoubo.mfcl.api.LegacyObject;
import github.com.gengyoubo.mfcl.api.LegacyRuntime;
import java.util.Set;
import java.util.function.BiFunction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

/** Curios owns stacks; MFCL mirrors them into every original TC4 BaublesApi inventory read. */
final class Tc4CuriosBaubles implements LegacyEquipment.Source, ICurioItem {
    private final LegacyRuntime runtime;
    private Tc4CuriosBaubles(LegacyRuntime runtime) { this.runtime = runtime; }

    static void install(LegacyRuntime runtime, Set<Item> items) {
        var bridge = new Tc4CuriosBaubles(runtime);
        runtime.equipment().register(10, bridge);
        items.forEach(item -> CuriosApi.registerCurio(item, bridge));
        // Old Baubles (or our ABI provider) owns worn ticks through this event. MFCL excludes
        // those slots from its fallback ticks when the event is forwarded, avoiding double effects.
        runtime.events().forward(LegacyEvents.Kind.LIVING_UPDATE);
        runtime.events().forward(LegacyEvents.Kind.LIVING_HURT);
        runtime.events().forward(LegacyEvents.Kind.LIVING_FALL);
        runtime.events().forward(LegacyEvents.Kind.BREAK_SPEED);
        LogUtils.getLogger().info("TC4 Curios: bauble inventories, worn ticks and equipment callbacks connected.");
    }

    static String identifier(LegacyEquipment.Slot slot) {
        return switch (slot) {
            case BAUBLE_AMULET -> "amulet";
            case BAUBLE_RING_0, BAUBLE_RING_1 -> "ring";
            case BAUBLE_BELT -> "belt";
            default -> null;
        };
    }
    static int index(LegacyEquipment.Slot slot) { return slot == LegacyEquipment.Slot.BAUBLE_RING_1 ? 1 : 0; }

    @Override public Object worn(Object hostPlayer, LegacyEquipment.Slot slot, boolean client) {
        if (!slot.bauble()) return null;
        var stacks = stacks(hostPlayer, identifier(slot));
        int index = index(slot);
        if (stacks == null || index >= stacks.getSlots()) return ItemStack.EMPTY;
        ItemStack stack = stacks.getStackInSlot(index);
        // Modern-only Curios items stay with their own implementation; expose every old IBauble.
        return identifier(slot).equals(Tc4CuriosCompat.slotOf(stack)) ? stack : ItemStack.EMPTY;
    }

    @Override public void changed(Object hostPlayer, LegacyEquipment.Slot slot, Object newHostStack) {
        if (!slot.bauble()) return;
        var stacks = stacks(hostPlayer, identifier(slot));
        int index = index(slot);
        if (stacks != null && index < stacks.getSlots()) {
            var current = stacks.getStackInSlot(index);
            if (identifier(slot).equals(Tc4CuriosCompat.slotOf(current)))
                stacks.setStackInSlot(index, (ItemStack) newHostStack);
        }
    }

    private static IDynamicStackHandler stacks(Object hostPlayer, String slot) {
        if (!(hostPlayer instanceof Player player)) return null;
        return CuriosApi.getCuriosInventory(player).resolve().flatMap(inventory -> inventory.getStacksHandler(slot))
                .map(handler -> handler.getStacks()).orElse(null);
    }

    @Override public boolean canEquip(SlotContext context, ItemStack stack) {
        if (context.cosmetic() || !context.identifier().equals(Tc4CuriosCompat.slotOf(stack))) return false;
        if (context.index() < 0 || context.index() >= (context.identifier().equals("ring") ? 2 : 1)) return false;
        return invoke(context, stack, (item, args) -> item.canEquip(args.stack(), args.player()), false);
    }
    @Override public boolean canUnequip(SlotContext context, ItemStack stack) {
        if (context.cosmetic()) return true;
        return invoke(context, stack, (item, args) -> item.canUnequip(args.stack(), args.player()), false);
    }
    @Override public void onEquip(SlotContext context, ItemStack previous, ItemStack stack) {
        if (context.cosmetic() || sameItem(previous, stack)) return;
        invoke(context, stack, (item, args) -> { item.equipped(args.stack(), args.player()); return true; }, false);
        runtime.equipment().invalidate(context.entity());
    }
    @Override public void onUnequip(SlotContext context, ItemStack next, ItemStack stack) {
        if (context.cosmetic() || sameItem(stack, next)) return;
        invoke(context, stack, (item, args) -> { item.unequipped(args.stack(), args.player()); return true; }, false);
        runtime.equipment().invalidate(context.entity());
    }
    private boolean sameItem(ItemStack left, ItemStack right) {
        if (left.isEmpty() || right.isEmpty() || !left.is(right.getItem())) return false;
        if (!java.util.Objects.equals(Tc4CuriosCompat.slotOf(left), Tc4CuriosCompat.slotOf(right))) return false;
        // MFCL stores original metadata separately from modern item damage. Distinct old
        // subtypes need their own equip cycle; ordinary charge/NBT updates do not.
        try {
            var a = runtime.mappings().toLegacy(LegacyMappings.ITEM_STACK, left).orElse(null);
            var b = runtime.mappings().toLegacy(LegacyMappings.ITEM_STACK, right).orElse(null);
            return a != null && b != null && runtime.snapshot(a).metadata() == runtime.snapshot(b).metadata();
        } catch (RuntimeException unsupported) { return false; }
    }
    private record Arguments(LegacyObject stack, LegacyObject player) { }
    private <T> T invoke(SlotContext context, ItemStack stack,
            BiFunction<Tc4BaubleAccessors.Bauble, Arguments, T> operation, T fallback) {
        if (!(context.entity() instanceof Player player) || stack.isEmpty()) return fallback;
        // Only MFCL's local client actor is mapped; remote players are server authoritative.
        if (player.level().isClientSide && !Tc4CuriosClient.local(player)) return fallback;
        try {
            var action = (java.util.function.Function<LegacyObject, T>) actor -> {
                var old = runtime.mappings().toLegacy(LegacyMappings.ITEM_STACK, stack).orElse(null);
                if (old == null) return fallback;
                var item = Tc4CuriosCompat.baubleOf(stack.getItem());
                if (item == null) return fallback;
                T result = operation.apply(item, new Arguments(old, actor));
                if (!player.level().isClientSide) {
                    // Equip callbacks may change the supplied stack too; preserve host-owned tags/capabilities.
                    ItemStack updated = runtime.mappings().applyTo(LegacyMappings.ITEM_STACK, stack, old);
                    stack.setTag(updated.getTag());
                    stack.setCount(updated.getCount());
                }
                return result;
            };
            return player.level().isClientSide ? runtime.withClientPlayer(action) : runtime.withPlayer(player, action);
        } catch (RuntimeException failure) {
            LogUtils.getLogger().debug("TC4 Curios callback unavailable: {}", failure.toString());
            return fallback;
        }
    }
}
