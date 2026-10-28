package github.com.gengyoubo.tcmodern.tc4.practice;

import github.com.gengyoubo.tcmodern.api.aspect.TcAspect;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PracticePuzzleTest {
    private static TcAspect aspect(String tag, String... parts) { return new TcAspect(tag, 0xFFFFFF, "", List.of(parts)); }

    private static PracticeAspects registry() {
        var entries = new ArrayList<>(List.of(aspect("aer"), aspect("terra"), aspect("aqua"), aspect("ignis"),
                aspect("ordo"), aspect("perditio"), aspect("lux", "aer", "ignis"),
                aspect("motus", "aer", "ordo"), aspect("vacuos", "aer", "perditio"),
                aspect("tenebrae", "vacuos", "lux")));
        for (int i = 3; i <= 8; i++) entries.add(aspect("tier" + i, i == 3 ? "tenebrae" : "tier" + (i - 1), "terra"));
        return new PracticeAspects(entries);
    }

    @Test void followsDirectBidirectionalCompositionAndDeepestComponentTier() {
        var graph = registry();
        assertEquals(0, graph.tier("aer"));
        assertEquals(1, graph.tier("lux"));
        assertEquals(2, graph.tier("tenebrae"));
        assertEquals(7, graph.tier("tier7"));
        assertTrue(graph.connects("aer", "lux"));
        assertTrue(graph.connects("lux", "aer"));
        assertFalse(graph.connects("aer", "ignis"));
        assertFalse(graph.connects("aer", "tenebrae"));
        assertFalse(graph.connects("lux", "lux"));
        assertFalse(graph.connects("unknown", "aer"));
    }

    @Test void everyLevelAlwaysGeneratesASolvableBoundedUnfinishedBoard() {
        var graph = registry();
        for (int level = 1; level <= 7; level++) {
            for (int seed = 0; seed < 200; seed++) {
                var puzzle = PracticePuzzle.generate(graph, level, new Random(seed));
                assertFalse(puzzle.solved(), "Initial board: level " + level + ", seed " + seed);
                assertTrue(graph.available(level).stream().allMatch(a -> graph.tier(a.tag()) <= puzzle.level()));
                assertTrue(puzzle.solution().values().stream().allMatch(tag -> graph.tier(tag) <= puzzle.level()));
                assertTrue(puzzle.anchors().values().stream().anyMatch(tag -> graph.tier(tag) == puzzle.level()));
                puzzle.solution().forEach(puzzle::place);
                assertTrue(puzzle.solved(), "Solution: level " + level + ", seed " + seed);
                assertEquals(puzzle.cells(), puzzle.connected());
            }
        }
    }

    @Test void fixedCellsHolesUnknownAspectsAndHigherTiersRejectEdits() {
        var puzzle = PracticePuzzle.generate(registry(), 1, new Random(42));
        var anchor = puzzle.anchors().keySet().iterator().next();
        var editable = puzzle.cells().stream().filter(h -> !puzzle.anchors().containsKey(h)).findFirst().orElseThrow();
        assertFalse(puzzle.place(anchor, "aer"));
        assertFalse(puzzle.remove(anchor));
        assertFalse(puzzle.place(new PracticePuzzle.Hex(99, 99), "aer"));
        assertFalse(puzzle.place(editable, "tenebrae"));
        assertFalse(puzzle.place(editable, "unknown"));
        assertFalse(puzzle.place(editable, null));
        assertTrue(puzzle.place(editable, "aer"));
        assertTrue(puzzle.remove(editable));
        assertEquals(puzzle.anchors(), puzzle.placed());
        assertThrows(UnsupportedOperationException.class, () -> puzzle.placed().clear());
    }

    @Test void hintsRepairMistakesAndResetReturnsToTheSameQuestion() {
        for (int level = 1; level <= 7; level++) {
            var puzzle = PracticePuzzle.generate(registry(), level, new Random(level));
            puzzle.cells().forEach(h -> puzzle.place(h, "aer"));
            int hints = 0;
            while (!puzzle.solved() && hints++ <= puzzle.cells().size()) assertTrue(puzzle.hint().isPresent());
            assertTrue(puzzle.solved());
            puzzle.reset();
            assertFalse(puzzle.solved());
            assertEquals(puzzle.anchors(), puzzle.placed());
        }
    }

    @Test void acceptsAlternativeSolutionsWithoutFillingTheBoard() {
        var puzzle = PracticePuzzle.generate(registry(), 1, new Random(42));
        puzzle.solution().forEach(puzzle::place);
        // At least one non-terminal can be removed from this redundant connected board.
        boolean sparseWin = false;
        for (var cell : puzzle.cells()) {
            if (!puzzle.remove(cell)) continue;
            if (puzzle.solved()) { sparseWin = true; break; }
            puzzle.place(cell, puzzle.solution().get(cell));
        }
        assertTrue(sparseWin);
    }

    @Test void malformedMissingOrCyclicAspectsNeverEnterThePalette() {
        var graph = new PracticeAspects(List.of(aspect("aer"), aspect("broken", "missing", "aer"),
                aspect("cycle1", "cycle2", "aer"), aspect("cycle2", "cycle1", "aer"),
                aspect("badarity", "aer"), aspect("child", "broken", "aer")));
        assertEquals(List.of("aer"), graph.available(7).stream().map(TcAspect::tag).toList());
        assertThrows(IllegalArgumentException.class, () -> PracticePuzzle.generate(graph, 1, new Random()));
        assertThrows(IllegalArgumentException.class, () -> graph.available(0));
        assertThrows(IllegalArgumentException.class, () -> graph.available(8));
    }

    @Test void sameSeedProducesSameQuestion() {
        var graph = registry();
        var first = PracticePuzzle.generate(graph, 7, new Random(123));
        var second = PracticePuzzle.generate(graph, 7, new Random(123));
        assertEquals(first.solution(), second.solution());
        assertEquals(first.anchors(), second.anchors());
    }
}
