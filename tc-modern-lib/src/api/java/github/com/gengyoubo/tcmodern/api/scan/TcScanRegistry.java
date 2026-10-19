package github.com.gengyoubo.tcmodern.api.scan;

import github.com.gengyoubo.mfcl.api.LegacyStack;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/** Explicit overrides beat defaults. Parent links copy aspects, not discovery identity or stack count. */
public final class TcScanRegistry {
    public record Definition(AspectValues aspects, ScanKey parent, LegacyStack original) {
        public Definition {
            if ((aspects == null ? 0 : 1) + (parent == null ? 0 : 1) + (original == null ? 0 : 1) != 1)
                throw new IllegalArgumentException("Exactly one aspect source is required");
        }
        public static Definition aspects(AspectValues values) { return new Definition(Objects.requireNonNull(values), null, null); }
        public static Definition parent(ScanKey key) { return new Definition(null, Objects.requireNonNull(key), null); }
        public static Definition original(LegacyStack stack) { return new Definition(null, null, Objects.requireNonNull(stack)); }
    }
    private final Map<ScanKey, Definition> defaults = new LinkedHashMap<>();
    private final Map<ScanKey, Definition> overrides = new LinkedHashMap<>();
    private long revision;
    public synchronized void register(ScanKey key, AspectValues values) { define(key, Definition.aspects(values)); }
    public synchronized void inherit(ScanKey key, ScanKey parent) { define(key, Definition.parent(parent)); }
    public synchronized void inheritOriginal(ScanKey key, LegacyStack original) { define(key, Definition.original(original)); }
    public synchronized void define(ScanKey key, Definition definition) {
        Objects.requireNonNull(key); Objects.requireNonNull(definition);
        overrides.put(key, definition); revision++;
    }
    /** Intended for compatibility defaults; never overwrites an addon's explicit rule. */
    public synchronized void registerDefault(ScanKey key, Definition definition) {
        if (defaults.putIfAbsent(Objects.requireNonNull(key), Objects.requireNonNull(definition)) == null) revision++;
    }
    public synchronized long revision() { return revision; }
    public synchronized boolean isOverride(ScanKey key) { return overrides.containsKey(key); }
    public synchronized void replaceDefault(ScanKey key, Definition definition) {
        defaults.put(Objects.requireNonNull(key), Objects.requireNonNull(definition)); revision++;
    }
    public synchronized Map<ScanKey, Definition> definitions() {
        var result = new LinkedHashMap<>(defaults); result.putAll(overrides); return Map.copyOf(result);
    }
    public Optional<AspectValues> resolve(ScanKey key, Function<LegacyStack, Optional<AspectValues>> originals) {
        return resolve(key, definitions(), originals);
    }
    public static Optional<AspectValues> resolve(ScanKey key, Map<ScanKey, Definition> definitions,
                                                Function<LegacyStack, Optional<AspectValues>> originals) {
        var visited = new HashSet<ScanKey>();
        while (true) {
            if (!visited.add(key)) throw new IllegalStateException("Cyclic scan inheritance: " + key);
            var definition = definitions.get(key);
            if (definition == null) return Optional.empty();
            if (definition.aspects() != null) return Optional.of(definition.aspects());
            if (definition.original() != null) return originals.apply(definition.original());
            key = definition.parent();
        }
    }
}
