package github.com.gengyoubo.mfcl.bridge;

import java.lang.reflect.Field;
import java.util.*;

/** Exposes the host main inventory to old item logic and applies old-code changes back, slot by slot. */
public final class LegacyPlayerInventory {
    /** The host collects and synchronizes this actor's inventory after its callback transaction commits. */
    public static void synchronize(Object ignored,Object actor) throws Exception {
        var loader=actor.getClass().getClassLoader();var entity=loader.loadClass("net.minecraft.entity.Entity");
        if(LegacyWorlds.hostOrNull(entity.getField("field_70170_p").get(actor))!=null)return;
        if(ignored!=null)ignored.getClass().getMethod("func_72385_f",loader.loadClass("net.minecraft.entity.player.EntityPlayerMP")).invoke(ignored,actor);
    }
    private final LegacyServices services;
    private final Field inventoryField, mainField, currentField;
    public LegacyPlayerInventory(LegacyServices services) throws Exception {
        this.services = services;
        ClassLoader loader = services.loader();
        inventoryField = loader.loadClass("net.minecraft.entity.player.EntityPlayer").getField("field_71071_by");
        Class<?> inventoryType = loader.loadClass("net.minecraft.entity.player.InventoryPlayer");
        mainField = inventoryType.getField("field_70462_a");
        currentField = inventoryType.getField("field_70461_c");
    }
    public void write(Object player, List<LegacyClientData.Stack> main) throws Exception {
        services.items().inLegacy(() -> {
            Object[] slots = (Object[]) mainField.get(inventoryField.get(player));
            var stackType = services.loader().loadClass("net.minecraft.item.ItemStack");
            for (int index = 0; index < slots.length; index++) {
                LegacyClientData.Stack value = index < main.size() ? main.get(index) : null;
                Object next = value == null ? null : services.items().materialize(value);
                // Portable containers retain the stack they opened. Replacing an equal stack detaches their storage.
                if (!same(stackType, slots[index], next)) slots[index] = next;
            }
            return null;
        });
    }
    public List<LegacyClientData.Stack> read(Object player) throws Exception {
        return services.items().inLegacy(() -> {
            Object[] slots = (Object[]) mainField.get(inventoryField.get(player));
            var result = new ArrayList<LegacyClientData.Stack>(slots.length);
            for (Object slot : slots) result.add(slot == null || slot.getClass().getField("field_77994_a").getInt(slot)<=0 ? null : services.items().snapshot(slot));
            return result;
        });
    }
    public void select(Object player, int slot) throws Exception {
        services.items().inLegacy(() -> { currentField.setInt(inventoryField.get(player), slot); return null; });
    }
    /** Read-only render context: refresh or clear only the selected stack, preserving other slots and equal objects. */
    public void held(Object player, LegacyClientData.Stack held) throws Exception {
        services.items().inLegacy(() -> {
            Object inventory=inventoryField.get(player);Object[] slots=(Object[])mainField.get(inventory);
            int slot=currentField.getInt(inventory);Object next=held==null?null:services.items().materialize(held);
            if(!same(services.loader().loadClass("net.minecraft.item.ItemStack"),slots[slot],next))slots[slot]=next;
            return null;
        });
    }
    /** What {@link #expose} last put in each slot: the host's view, the old object and a copy to detect in-place changes. */
    private record Exposed(LegacyClientData.Stack[] host, Object[] objects, Object[] copies) {}
    private final Map<Object, Exposed> exposed = new com.google.common.collect.MapMaker().weakKeys().makeMap();
    /**
     * Like {@link #write}, for a persistent actor exposed every tick: slots whose host stack and old object are
     * unchanged since the last exposure are kept without materializing. Otherwise, equal stacks still retain
     * their identity, including when another inventory import has already applied the host's changes.
     */
    public void expose(Object player, List<LegacyClientData.Stack> main) throws Exception {
        services.items().inLegacy(() -> {
            Object[] slots = (Object[]) mainField.get(inventoryField.get(player));
            var prior = exposed.get(player); var stackType = services.loader().loadClass("net.minecraft.item.ItemStack");
            var state = new Exposed(new LegacyClientData.Stack[slots.length], new Object[slots.length], new Object[slots.length]);
            for (int index = 0; index < slots.length; index++) {
                LegacyClientData.Stack value = index < main.size() ? main.get(index) : null;
                boolean kept = prior != null && sameValue(prior.host[index], value) && slots[index] == prior.objects[index] && same(stackType, slots[index], prior.copies[index]);
                if (!kept) {
                    Object next = value == null ? null : services.items().materialize(value);
                    // A menu may still own this object even when the last exposure's tracking is stale.
                    if (!same(stackType, slots[index], next)) slots[index] = next;
                }
                state.host[index] = value; state.objects[index] = slots[index];
                state.copies[index] = slots[index] == null ? null : services.items().copyLegacyStack(slots[index]);
            }
            exposed.put(player, state);
            return null;
        });
    }
    /** Slots old code changed since {@link #expose}, as their new contents (null: emptied). */
    public Map<Integer, LegacyClientData.Stack> changes(Object player) throws Exception {
        return services.items().inLegacy(() -> {
            var state = exposed.get(player); if (state == null) return Map.<Integer, LegacyClientData.Stack>of();
            Object[] slots = (Object[]) mainField.get(inventoryField.get(player)); var stackType = services.loader().loadClass("net.minecraft.item.ItemStack");
            var result = new TreeMap<Integer, LegacyClientData.Stack>();
            for (int index = 0; index < slots.length && index < state.host.length; index++) {
                if (slots[index] == state.objects[index] && same(stackType, slots[index], state.copies[index])) continue;
                Object slot = slots[index];
                var now = slot == null || slot.getClass().getField("field_77994_a").getInt(slot) <= 0 ? null : services.items().snapshot(slot);
                state.objects[index] = slot; state.copies[index] = slot == null ? null : services.items().copyLegacyStack(slot);
                if (sameValue(now, state.host[index])) continue;
                state.host[index] = now; result.put(index, now);
            }
            return result;
        });
    }
    /** Display names are presentation only. */
    private static boolean sameValue(LegacyClientData.Stack left, LegacyClientData.Stack right) {
        if (left == null || right == null) return left == right;
        return left.key().equals(right.key()) && left.metadata() == right.metadata() && left.count() == right.count() && left.tag().equals(right.tag());
    }
    private static boolean same(Class<?> stackType, Object left, Object right) throws Exception {
        if (left == null || right == null) return left == right;
        return (boolean) stackType.getMethod("func_77989_b", stackType, stackType).invoke(null, left, right);
    }
}
