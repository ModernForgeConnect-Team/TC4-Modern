package github.com.gengyoubo.tcmodern.lib.scan;

import github.com.gengyoubo.tcmodern.api.TcModernApi;
import github.com.gengyoubo.tcmodern.api.scan.*;
import java.util.*;

/** Immutable resolved rules can be synchronized without exposing old objects to clients. */
public final class ScanRules {
    private static long revision = -1;
    private static Object runtime;
    private static Map<ScanKey, AspectValues> server = Map.of();
    private static volatile Map<ScanKey, AspectValues> client;
    private ScanRules() { }
    public static synchronized Map<ScanKey, AspectValues> server() {
        var api = TcModernApi.runtime().orElse(null);
        var registry = TcScanApi.registry();
        if (api != runtime || revision != registry.revision()) {
            var definitions = registry.definitions();
            var resolved = new LinkedHashMap<ScanKey, AspectValues>();
            var originals = new HashMap<github.com.gengyoubo.mfcl.api.LegacyStack, Optional<AspectValues>>();
            if (api != null) for (var key : definitions.keySet()) {
                try {
                    TcScanRegistry.resolve(key, definitions, source -> originals.computeIfAbsent(source, ScanResearch::original))
                            .ifPresent(values -> resolved.put(key, values));
                } catch (RuntimeException failure) {
                    com.mojang.logging.LogUtils.getLogger().warn("TC4 scan rule {} omitted", key, failure);
                }
            }
            server = Map.copyOf(resolved); runtime = api; revision = registry.revision();
        }
        return server;
    }
    public static Map<ScanKey, AspectValues> get(boolean clientSide) {
        return clientSide && client != null ? client : server();
    }
    public static void receive(Map<ScanKey, AspectValues> values) { client = Map.copyOf(values); }
    public static void disconnect() { client = null; }
}
