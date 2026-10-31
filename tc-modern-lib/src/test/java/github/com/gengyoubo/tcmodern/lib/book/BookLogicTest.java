package github.com.gengyoubo.tcmodern.lib.book;

import github.com.gengyoubo.tcmodern.api.research.TcResearchCategory;
import github.com.gengyoubo.tcmodern.api.research.TcResearchEntry;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BookLogicTest {
    @Test void revokingWithDependentsFollowsEveryRequisite() {
        var entries = List.of(entry("A", List.of(), List.of()), entry("B", List.of("A"), List.of()),
                entry("C", List.of(), List.of("B")), entry("D", List.of("C"), List.of()), entry("E", List.of(), List.of()));
        var book = List.of(new TcResearchCategory("X", "", "", entries));
        assertEquals(List.of("A", "B", "C", "D"), BookLogic.dependents(book, "A"));
        assertEquals(List.of("C", "D"), BookLogic.dependents(book, "C"));
        assertEquals(List.of("E"), BookLogic.dependents(book, "E"));
    }

    private static TcResearchEntry entry(String key, List<String> parents, List<String> hidden) {
        return new TcResearchEntry(key, "X", 0, 0, 1, null, "", Map.of(), parents, hidden, List.of(), Set.of(),
                List.of(), List.of(), List.of(), 0, List.of());
    }

}
