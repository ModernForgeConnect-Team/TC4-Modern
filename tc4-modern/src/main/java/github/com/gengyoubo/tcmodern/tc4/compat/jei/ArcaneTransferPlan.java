package github.com.gengyoubo.tcmodern.tc4.compat.jei;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Ordinary slot clicks that fill the original arcane worktable menu, planned on a copy of its contents with the
 * 1.7.10 Container click and merge rules. Menu slots: 0 output, 1 wand, 2-10 grid, 11-37 inventory, 38-46 hotbar.
 */
final class ArcaneTransferPlan {
    static final int WAND = 1, GRID = 2, INVENTORY = 11, SLOTS = 47, MAX_CRAFTS = 64;

    /** {@code id} groups stacks the original slots merge: same item, damage and NBT. */
    record Stack(String id, int count, int max) {
        Stack withCount(int value) { return new Stack(id, value, max); }
    }

    record Click(int slot, int button, boolean quickMove) {}

    /** {@code missing}: grid positions no inventory stack can fill; {@code full}: the old grid cannot be returned. */
    record Result(List<Click> clicks, List<Integer> missing, boolean full) {
        boolean ok() { return missing.isEmpty() && !full; }
    }

    private ArcaneTransferPlan() {}

    /**
     * @param menu  the 47 menu stacks, null for empty
     * @param grid  nine grid positions, each null or accepting a stack id
     * @param wand  inventory slot of a wand to swap into the wand slot first, or -1 to leave the wand slot alone
     * @param max   fill as many crafts as the inventory allows (up to a stack) instead of one
     */
    static Result plan(List<Stack> menu, List<Predicate<String>> grid, int wand, boolean max) {
        if (menu.size() != SLOTS || grid.size() != 9) throw new IllegalArgumentException("Not an arcane worktable menu");
        var best = attempt(menu, grid, wand, 1);
        if (!max || !best.ok()) return best;
        for (int crafts = 2; crafts <= MAX_CRAFTS; crafts++) {
            var next = attempt(menu, grid, wand, crafts);
            if (!next.ok()) break;
            best = next;
        }
        return best;
    }

    private static Result attempt(List<Stack> menu, List<Predicate<String>> grid, int wand, int crafts) {
        var sim = new Sim(menu);
        if (wand >= INVENTORY) {
            sim.pickup(wand);
            sim.swap(WAND);
            if (sim.carried != null) sim.put(wand);
        }
        for (int slot = GRID; slot < GRID + 9; slot++)
            if (sim.slots[slot] != null && !sim.quickMove(slot)) return new Result(List.of(), List.of(), true);
        var missing = new ArrayList<Integer>();
        for (int position = 0; position < 9; position++) {
            var accepts = grid.get(position);
            if (accepts == null) continue;
            int target = GRID + position, need = crafts;
            while (need > 0) {
                int source = sim.find(accepts, target);
                if (source < 0) {
                    missing.add(position);
                    break;
                }
                need -= sim.move(source, target, need);
            }
        }
        return new Result(missing.isEmpty() ? List.copyOf(sim.clicks) : List.of(), List.copyOf(missing), false);
    }

    private static final class Sim {
        final Stack[] slots;
        final List<Click> clicks = new ArrayList<>();
        Stack carried;

        Sim(List<Stack> menu) { slots = menu.toArray(Stack[]::new); }

        /** Left click with an empty cursor: takes the whole stack. */
        void pickup(int slot) {
            clicks.add(new Click(slot, 0, false));
            carried = slots[slot];
            slots[slot] = null;
        }

        /** Left click on an occupied slot with a different stack: the slot and cursor trade places. */
        void swap(int slot) {
            clicks.add(new Click(slot, 0, false));
            var previous = slots[slot];
            slots[slot] = carried;
            carried = previous;
        }

        /** Left click on an empty slot: puts the whole cursor down. */
        void put(int slot) {
            clicks.add(new Click(slot, 0, false));
            slots[slot] = carried;
            carried = null;
        }

        /** Shift click from the grid: Container.mergeItemStack into slots 11-46, topping up stacks before empty slots. */
        boolean quickMove(int slot) {
            clicks.add(new Click(slot, 0, true));
            var moving = slots[slot];
            int left = moving.count();
            if (moving.max() > 1) for (int i = INVENTORY; i < SLOTS && left > 0; i++) {
                var there = slots[i];
                if (there == null || !there.id().equals(moving.id()) || there.count() >= moving.max()) continue;
                int moved = Math.min(left, moving.max() - there.count());
                slots[i] = there.withCount(there.count() + moved);
                left -= moved;
            }
            if (left > 0) for (int i = INVENTORY; i < SLOTS; i++) if (slots[i] == null) {
                slots[i] = moving.withCount(left);
                left = 0;
                break;
            }
            slots[slot] = left > 0 ? moving.withCount(left) : null;
            return left == 0;
        }

        int find(Predicate<String> accepts, int target) {
            var placed = slots[target];
            if (placed != null && placed.count() >= placed.max()) return -1;
            for (int i = INVENTORY; i < SLOTS; i++) {
                var stack = slots[i];
                if (stack != null && (placed == null ? accepts.test(stack.id()) : placed.id().equals(stack.id()))) return i;
            }
            return -1;
        }

        /** Places up to {@code need} from {@code source} on {@code target}; returns how many were placed. */
        int move(int source, int target, int need) {
            pickup(source);
            var placed = slots[target];
            int room = placed == null ? carried.max() : placed.max() - placed.count();
            int count = Math.min(need, Math.min(carried.count(), room));
            int total = (placed == null ? 0 : placed.count()) + count;
            if (count == carried.count()) {
                clicks.add(new Click(target, 0, false));
                slots[target] = carried.withCount(total);
                carried = null;
            } else {
                for (int i = 0; i < count; i++) clicks.add(new Click(target, 1, false));
                slots[target] = carried.withCount(total);
                carried = carried.withCount(carried.count() - count);
                put(source);
            }
            return count;
        }
    }
}
