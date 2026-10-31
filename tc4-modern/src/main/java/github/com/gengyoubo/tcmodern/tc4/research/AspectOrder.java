package github.com.gengyoubo.tcmodern.tc4.research;

import github.com.gengyoubo.tcmodern.api.aspect.TcAspect;
import github.com.gengyoubo.tcmodern.tc4.practice.PracticeAspects;
import java.util.*;
import java.util.function.Function;

/** The research desk palette: search and the four orderings, over plain tags so it runs without a game. */
public final class AspectOrder {
    public enum Mode {
        /** Original registration order: primals first, then compounds as Thaumcraft and its addons added them. */
        ORIGINAL,
        /** Most points first. */
        AMOUNT,
        /** Primal, then tier 1 to 7 by composition depth. */
        TIER,
        /** Aspect id from a to z. */
        ALPHABETICAL;

        public Mode next() { return values()[(ordinal() + 1) % values().length]; }
        public String key() { return "desk.tc4modern.sort." + name().toLowerCase(Locale.ROOT); }
    }

    private final PracticeAspects graph;
    private final Map<String, Integer> registration = new HashMap<>();

    public AspectOrder(List<TcAspect> registry) {
        graph = new PracticeAspects(registry);
        for (var aspect : registry) registration.putIfAbsent(aspect.tag(), registration.size());
    }

    public PracticeAspects graph() { return graph; }
    public int tier(String tag) { return graph.tier(tag); }

    /**
     * The tags of {@code amounts} that match {@code query}, ordered by {@code mode}. A query matches the tag or the
     * localized name case-insensitively; blank queries match everything. Ties always fall back to registration order.
     */
    public List<String> apply(Map<String, Integer> amounts, Mode mode, String query, Function<String, String> names) {
        String needle = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        Comparator<String> original = Comparator.comparingInt((String tag) -> registration.getOrDefault(tag, Integer.MAX_VALUE))
                .thenComparing(Comparator.naturalOrder());
        Comparator<String> order = switch (mode) {
            case ORIGINAL -> original;
            case AMOUNT -> Comparator.comparingInt((String tag) -> -amounts.getOrDefault(tag, 0)).thenComparing(original);
            case TIER -> Comparator.comparingInt(this::tier).thenComparing(original);
            case ALPHABETICAL -> Comparator.naturalOrder();
        };
        return amounts.keySet().stream()
                .filter(tag -> needle.isEmpty() || tag.toLowerCase(Locale.ROOT).contains(needle)
                        || names.apply(tag).toLowerCase(Locale.ROOT).contains(needle))
                .sorted(order).toList();
    }
}
