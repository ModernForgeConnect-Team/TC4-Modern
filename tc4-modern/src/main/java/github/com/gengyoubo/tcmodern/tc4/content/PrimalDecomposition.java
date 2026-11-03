package github.com.gengyoubo.tcmodern.tc4.content;

import github.com.gengyoubo.tcmodern.api.aspect.TcAspect;
import java.util.*;

/** Expands every occurrence of a compound into both of its components. */
public final class PrimalDecomposition {
    private PrimalDecomposition() { }

    public static Map<String, Integer> expand(Map<String, Integer> input, Collection<TcAspect> registry) {
        var aspects = new HashMap<String, TcAspect>();
        registry.forEach(aspect -> aspects.put(aspect.tag(), aspect));
        var result = new LinkedHashMap<String, Integer>();
        input.forEach((tag, amount) -> expand(tag, amount, aspects, new HashSet<>(), result));
        return result;
    }

    private static void expand(String tag, int amount, Map<String, TcAspect> aspects, Set<String> path, Map<String, Integer> result) {
        var aspect = aspects.get(tag);
        if (amount <= 0 || aspect == null || !path.add(tag)) throw new IllegalArgumentException("Invalid aspect graph: " + tag);
        if (aspect.primal()) result.merge(tag, amount, Math::addExact);
        else {
            if (aspect.components().size() != 2) throw new IllegalArgumentException("Invalid aspect components: " + tag);
            for (String part : aspect.components()) expand(part, amount, aspects, path, result);
        }
        path.remove(tag);
    }
}
