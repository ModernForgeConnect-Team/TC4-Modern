package github.com.gengyoubo.tcmodern.api.scan;

import java.util.Map;
import java.util.TreeMap;

/** Immutable per-object TC4 aspect amounts; stack size never multiplies scan rewards. */
public record AspectValues(Map<String, Integer> amounts) {
    public AspectValues {
        var copy = new TreeMap<String, Integer>();
        amounts.forEach((tag, amount) -> {
            if (tag == null || !tag.matches("[a-z][a-z0-9_]*") || amount == null || amount <= 0 || amount > 32767)
                throw new IllegalArgumentException("Invalid TC4 aspect: " + tag + "=" + amount);
            copy.put(tag, amount);
        });
        if (copy.isEmpty() || copy.size() > 64) throw new IllegalArgumentException("Expected 1..64 aspects");
        amounts = java.util.Collections.unmodifiableMap(copy);
    }
    public static AspectValues of(String tag, int amount) { return new AspectValues(Map.of(tag, amount)); }
}
