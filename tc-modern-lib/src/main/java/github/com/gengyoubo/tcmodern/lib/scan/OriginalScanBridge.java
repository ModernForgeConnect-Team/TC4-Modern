package github.com.gengyoubo.tcmodern.lib.scan;

import github.com.gengyoubo.mfcl.api.*;
import github.com.gengyoubo.tcmodern.api.TcModernApi;
import github.com.gengyoubo.tcmodern.api.scan.*;
import java.util.*;

/** Registers data only. TC4 owns targeting, held use, completion, rewards and rendering. */
public final class OriginalScanBridge {
    private static LegacyRuntime runtime;
    private static Map<ScanKey, AspectValues> installed = Map.of();
    private static Set<ScanKey> overrides = Set.of();
    private OriginalScanBridge() { }
    public static synchronized void install(Map<ScanKey, AspectValues> rules, Set<ScanKey> explicit) {
        var api = TcModernApi.runtime().orElseThrow();
        if (api == runtime && installed.equals(rules) && overrides.equals(explicit)) return;
        var representations = api.hostRepresentations();
        var registration = api.accessor(ScanAccessors.Registration.class);
        var types = api.accessor(ScanAccessors.AspectLookup.class);
        var factory = api.accessor(ScanAccessors.AspectsFactory.class);
        var keys = new ArrayList<>(rules.keySet());
        keys.sort(Comparator.comparing((ScanKey key) -> key.kind().name()).thenComparing(ScanKey::id));
        for (var key : keys) {
            LegacyStack stack = null; String entity = null;
            if (key.kind() == ScanKey.Kind.ENTITY) entity = representations.entity(key.id());
            else stack = key.kind() == ScanKey.Kind.BLOCK ? representations.block(key.id()) : representations.item(key.id());
            boolean host = entity != null ? entity.startsWith("mfcl_host:") : stack != null && stack.key().startsWith("mfcl_host:");
            if ((!host && !explicit.contains(key)) || (stack == null && entity == null)) continue;
            if (api == runtime && rules.get(key).equals(installed.get(key)) && explicit.contains(key) == overrides.contains(key)) continue;
            var tags = factory.create(); var aspects = api.accessor(ScanAccessors.Aspects.class, tags);
            for (var entry : rules.get(key).amounts().entrySet()) {
                var aspect = types.find(entry.getKey());
                if (aspect == null) throw new IllegalArgumentException("Unknown TC4 aspect: " + entry.getKey());
                aspects.add(aspect, entry.getValue());
            }
            if (entity != null) registration.entity(entity, tags, new LegacyObject[0]);
            else registration.item(api.stack(stack), tags);
        }
        runtime = api; installed = Map.copyOf(rules); overrides = Set.copyOf(explicit);
    }
    public static Set<ScanKey> overrides() {
        var registry = TcScanApi.registry(); var keys = new HashSet<ScanKey>();
        registry.definitions().keySet().stream().filter(registry::isOverride).forEach(keys::add);
        return Set.copyOf(keys);
    }
    public static LegacyStack stack(ScanKey key) {
        var representations = TcModernApi.runtime().orElseThrow().hostRepresentations();
        return key.kind() == ScanKey.Kind.BLOCK ? representations.block(key.id()) : representations.item(key.id());
    }
}
