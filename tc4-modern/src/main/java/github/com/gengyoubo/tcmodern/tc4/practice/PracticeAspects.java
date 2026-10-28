package github.com.gengyoubo.tcmodern.tc4.practice;

import github.com.gengyoubo.tcmodern.api.aspect.TcAspect;
import java.util.*;

/** Detached aspect graph. Primals are tier zero; compounds are one above their deepest component. */
public final class PracticeAspects {
    private final Map<String, TcAspect> aspects = new LinkedHashMap<>();
    private final Map<String, Integer> tiers = new HashMap<>();

    public PracticeAspects(Collection<TcAspect> registry) {
        registry.forEach(aspect -> aspects.put(aspect.tag(), aspect));
        aspects.keySet().forEach(tag -> tier(tag, new HashSet<>()));
    }

    public TcAspect aspect(String tag) { return aspects.get(tag); }
    public int tier(String tag) { return tiers.getOrDefault(tag, Integer.MAX_VALUE); }

    private int tier(String tag, Set<String> visiting) {
        if (tiers.containsKey(tag)) return tiers.get(tag);
        var aspect = aspects.get(tag);
        if (aspect == null || !visiting.add(tag)) return Integer.MAX_VALUE;
        int result = 0;
        if (!aspect.primal()) {
            result = Integer.MAX_VALUE;
            if (aspect.components().size() == 2) {
                int deepest = 0;
                for (String component : aspect.components()) deepest = Math.max(deepest, tier(component, visiting));
                if (deepest != Integer.MAX_VALUE) result = deepest + 1;
            }
        }
        visiting.remove(tag);
        tiers.put(tag, result);
        return result;
    }

    /** Only direct composition counts, in either direction; identical aspects do not connect. */
    public boolean connects(String first, String second) {
        if (first == null || second == null || first.equals(second)) return false;
        var a = aspects.get(first);
        var b = aspects.get(second);
        return a != null && b != null && (a.components().contains(second) || b.components().contains(first));
    }

    public List<TcAspect> available(int level) {
        checkLevel(level);
        return aspects.values().stream().filter(a -> tier(a.tag()) <= level)
                .sorted(Comparator.comparingInt((TcAspect a) -> tier(a.tag())).thenComparing(TcAspect::tag)).toList();
    }

    public static void checkLevel(int level) {
        if (level < 1 || level > 7) throw new IllegalArgumentException("Practice level must be 1 to 7");
    }
}
