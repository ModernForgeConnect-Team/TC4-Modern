package github.com.gengyoubo.tcmodern.tc4.crucible;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.random.RandomGenerator;

/** The aspects dissolved in an intermediate crucible, in insertion order like TC4's AspectList. */
public final class CrucibleEssentia {
    /** Total capacity; above it the crucible spills, as the original does above 100. */
    public static final int CAPACITY = 32767;
    private final LinkedHashMap<String, Integer> amounts = new LinkedHashMap<>();

    public Map<String, Integer> snapshot() { return Collections.unmodifiableMap(new LinkedHashMap<>(amounts)); }
    public boolean isEmpty() { return amounts.isEmpty(); }
    public int total() { long sum = 0; for (int amount : amounts.values()) sum += amount; return (int) Math.min(Integer.MAX_VALUE, sum); }
    public int amount(String tag) { return amounts.getOrDefault(tag, 0); }

    public void add(Map<String, Integer> values) { values.forEach(this::add); }
    public void add(String tag, int amount) {
        if (amount <= 0) return;
        amounts.merge(tag, amount, (a, b) -> (int) Math.min(Integer.MAX_VALUE, (long) a + b));
    }
    public void remove(String tag, int amount) {
        int left = amount(tag) - amount;
        if (left > 0) amounts.put(tag, left); else amounts.remove(tag);
    }
    public void clear() { amounts.clear(); }

    /** Whether every required aspect is present in at least the required amount. */
    public boolean contains(Map<String, Integer> required) {
        for (var entry : required.entrySet()) if (amount(entry.getKey()) < entry.getValue()) return false;
        return true;
    }

    /** Removes a recipe's cost; false (and unchanged) when it is not all there. */
    public boolean take(Map<String, Integer> required) {
        if (!contains(required)) return false;
        required.forEach(this::remove);
        return true;
    }

    /** Removes one random essentia, as the original does every 5 ticks while over capacity; null when empty. */
    public String takeRandom(RandomGenerator random) {
        if (amounts.isEmpty()) return null;
        var tags = new ArrayList<>(amounts.keySet());
        String tag = tags.get(random.nextInt(tags.size()));
        remove(tag, 1);
        return tag;
    }

    /**
     * The original's slow boil-off: picks a random aspect (a primal one gets a second draw), removes one; a compound
     * leaves one of its two components behind, a primal turns into flux. Returns true when it turned into flux.
     *
     * @param components an aspect's two components, or an empty list for a primal aspect
     */
    public boolean boilOff(RandomGenerator random, Function<String, List<String>> components) {
        if (amounts.isEmpty()) return false;
        var tags = new ArrayList<>(amounts.keySet());
        String tag = tags.get(random.nextInt(tags.size()));
        if (components.apply(tag).isEmpty()) tag = tags.get(random.nextInt(tags.size()));
        remove(tag, 1);
        var parts = components.apply(tag);
        if (parts.size() < 2) return true;
        add(parts.get(random.nextBoolean() ? 0 : 1), 1);
        return false;
    }

    /** How many flux spills losing {@code lost} essentia causes: one per two, as the original's remnants, and at least one. */
    public static int fluxSpills(int lost) { return Math.max(1, lost / 2); }

    public void load(Map<String, Integer> values) {
        amounts.clear();
        values.forEach((tag, amount) -> { if (tag.matches("[a-z][a-z0-9_]*") && amount > 0) add(tag, amount); });
    }
}
