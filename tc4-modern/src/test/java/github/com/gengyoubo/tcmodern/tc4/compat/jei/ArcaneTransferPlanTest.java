package github.com.gengyoubo.tcmodern.tc4.compat.jei;

import static org.junit.jupiter.api.Assertions.*;

import github.com.gengyoubo.tcmodern.tc4.compat.jei.ArcaneTransferPlan.Click;
import github.com.gengyoubo.tcmodern.tc4.compat.jei.ArcaneTransferPlan.Stack;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

class ArcaneTransferPlanTest {
    private static List<Stack> menu() { return new ArrayList<>(Collections.nCopies(ArcaneTransferPlan.SLOTS, null)); }
    private static List<Predicate<String>> grid() { return new ArrayList<>(Collections.nCopies(9, null)); }
    private static Click left(int slot) { return new Click(slot, 0, false); }
    private static Click right(int slot) { return new Click(slot, 1, false); }

    @Test
    void placesOneOfEachAndPutsTheRestBack() {
        var menu = menu();
        menu.set(11, new Stack("salis", 5, 64));
        var grid = grid();
        grid.set(0, "salis"::equals);
        grid.set(4, "salis"::equals);
        var plan = ArcaneTransferPlan.plan(menu, grid, -1, false);
        assertTrue(plan.ok());
        assertEquals(List.of(left(11), right(2), left(11), left(11), right(6), left(11)), plan.clicks());
    }

    @Test
    void wholeStackIsPlacedWithOneClick() {
        var menu = menu();
        menu.set(20, new Stack("shard", 1, 64));
        var grid = grid();
        grid.set(8, "shard"::equals);
        assertEquals(List.of(left(20), left(10)), ArcaneTransferPlan.plan(menu, grid, -1, false).clicks());
    }

    @Test
    void reportsEveryMissingPositionAndSendsNothing() {
        var menu = menu();
        menu.set(11, new Stack("salis", 1, 64));
        var grid = grid();
        grid.set(0, "salis"::equals);
        grid.set(1, "salis"::equals);
        grid.set(2, "quartz"::equals);
        var plan = ArcaneTransferPlan.plan(menu, grid, -1, false);
        assertEquals(List.of(1, 2), plan.missing());
        assertTrue(plan.clicks().isEmpty());
    }

    @Test
    void clearsTheGridIntoTheInventoryFirst() {
        var menu = menu();
        menu.set(3, new Stack("salis", 2, 64));
        menu.set(11, new Stack("salis", 62, 64));
        var grid = grid();
        grid.set(0, "salis"::equals);
        var plan = ArcaneTransferPlan.plan(menu, grid, -1, false);
        assertTrue(plan.ok());
        assertEquals(new Click(3, 0, true), plan.clicks().get(0));
        // The returned items topped up slot 11, which then supplies the grid.
        assertEquals(List.of(left(11), right(2), left(11)), plan.clicks().subList(1, 4));
    }

    @Test
    void fullInventoryCannotTakeTheOldGrid() {
        var menu = menu();
        menu.set(2, new Stack("stone", 1, 64));
        for (int i = ArcaneTransferPlan.INVENTORY; i < ArcaneTransferPlan.SLOTS; i++) menu.set(i, new Stack("dirt", 64, 64));
        var grid = grid();
        grid.set(0, "dirt"::equals);
        assertTrue(ArcaneTransferPlan.plan(menu, grid, -1, false).full());
    }

    @Test
    void swapsInTheChosenWandAndReturnsTheOldOne() {
        var menu = menu();
        menu.set(1, new Stack("empty_wand", 1, 1));
        menu.set(40, new Stack("charged_wand", 1, 1));
        menu.set(11, new Stack("salis", 1, 64));
        var grid = grid();
        grid.set(4, "salis"::equals);
        var plan = ArcaneTransferPlan.plan(menu, grid, 40, false);
        assertEquals(List.of(left(40), left(1), left(40), left(11), left(6)), plan.clicks());
    }

    @Test
    void maxTransferStopsAtTheScarcestIngredient() {
        var menu = menu();
        menu.set(11, new Stack("salis", 10, 64));
        menu.set(12, new Stack("quartz", 3, 64));
        var grid = grid();
        grid.set(0, "salis"::equals);
        grid.set(1, "quartz"::equals);
        var clicks = ArcaneTransferPlan.plan(menu, grid, -1, true).clicks();
        assertEquals(3, clicks.stream().filter(click -> click.slot() == 2 && click.button() == 1).count());
        assertEquals(List.of(left(12), left(3)), clicks.subList(clicks.size() - 2, clicks.size()));
    }

    @Test
    void maxTransferRespectsTheItemStackLimit() {
        var menu = menu();
        menu.set(11, new Stack("pearl", 16, 16));
        menu.set(12, new Stack("pearl", 16, 16));
        var grid = grid();
        grid.set(0, "pearl"::equals);
        var clicks = ArcaneTransferPlan.plan(menu, grid, -1, true).clicks();
        assertEquals(List.of(left(11), left(2)), clicks);
    }
}
