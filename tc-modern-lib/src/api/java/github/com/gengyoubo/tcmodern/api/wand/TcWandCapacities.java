package github.com.gengyoubo.tcmodern.api.wand;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Opt-in NBT key prefixes for per-aspect TC4 wand capacities, stored in centivis. */
public final class TcWandCapacities {
    public static final String DEFAULT_PREFIX = "tcmodernlib_capacity_";
    private static final Set<String> PREFIXES = new LinkedHashSet<>(List.of(DEFAULT_PREFIX));
    private TcWandCapacities() { }
    /** Register an addon's existing saved format before its wands are used. Duplicate prefixes are rejected. */
    public static synchronized AutoCloseable registerPrefix(String prefix) {
        if (prefix == null || !prefix.matches("[a-z][a-z0-9_]{0,95}_")) throw new IllegalArgumentException("Invalid capacity prefix");
        if (!PREFIXES.add(prefix)) throw new IllegalArgumentException("Capacity prefix already registered: " + prefix);
        return () -> { synchronized (TcWandCapacities.class) { PREFIXES.remove(prefix); } };
    }
    public static synchronized List<String> prefixes() { return List.copyOf(PREFIXES); }
}
