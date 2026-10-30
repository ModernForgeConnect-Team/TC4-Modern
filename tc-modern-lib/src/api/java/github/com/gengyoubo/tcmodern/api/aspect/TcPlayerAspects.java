package github.com.gengyoubo.tcmodern.api.aspect;

import github.com.gengyoubo.mfcl.api.LegacyObject;
import github.com.gengyoubo.mfcl.api.LegacyRuntime;
import github.com.gengyoubo.tcmodern.api.TcModernApi;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import static github.com.gengyoubo.tcmodern.api.aspect.TcAspectAccessors.*;

/** A player's original research aspect pool, changed only inside MFCL's scoped player transaction. */
public final class TcPlayerAspects {
    /** The original pool stores amounts as shorts. */
    public static final int MAX_POOL = Short.MAX_VALUE;

    private TcPlayerAspects() { }

    /** Reads one research aspect pool on the owning server thread. Unknown tags have no points. */
    public static int pool(LegacyRuntime runtime, Object hostPlayer, String tag) {
        require(runtime);
        var aspect = runtime.accessor(AspectLookup.class).find(tag);
        if (aspect == null) return 0;
        return runtime.withPlayer(hostPlayer, player -> (int) knowledge(runtime).pool(
                runtime.accessor(Player.class, player).name(), aspect));
    }

    /**
     * Atomically checks and consumes the full amount on the owning server thread. Returns false without changes
     * for unknown aspects or insufficient points. An amount must be 1..32767. MFCL persists and synchronizes it.
     */
    public static boolean consume(LegacyRuntime runtime, Object hostPlayer, String tag, int amount) {
        require(runtime);
        if (amount < 1 || amount > MAX_POOL) throw new IllegalArgumentException("Invalid aspect amount: " + amount);
        var aspect = runtime.accessor(AspectLookup.class).find(tag);
        if (aspect == null) return false;
        return runtime.withPlayer(hostPlayer, player -> {
            String name = runtime.accessor(Player.class, player).name();
            var knowledge = knowledge(runtime);
            if (knowledge.pool(name, aspect) < amount) return false;
            return knowledge.add(name, aspect, (short) -amount);
        });
    }

    private static Knowledge knowledge(LegacyRuntime runtime) {
        return runtime.accessor(Knowledge.class, runtime.accessor(Proxy.class,
                runtime.accessor(Thaumcraft.class).proxy()).knowledge());
    }

    private static void require(LegacyRuntime runtime) {
        if (!runtime.isOpen() || !runtime.loadedMods().contains(TcModernApi.LEGACY_MOD_ID))
            throw new IllegalStateException("Thaumcraft runtime is not available");
    }

    /**
     * Adds aspect points to a player's pool, discovering aspects they did not know yet. Call on the owning server
     * thread; MFCL persists the pool and resynchronizes it to the client afterwards.
     *
     * @param hostPlayer the host {@code ServerPlayer}
     * @param amounts    points to add by tag, each 1..{@value #MAX_POOL}; a total beyond {@value #MAX_POOL} is capped
     * @return each aspect's new pool total, in the order given
     * @throws IllegalArgumentException when a tag is unknown or an amount is out of range; nothing is changed then
     * @throws IllegalStateException    when the runtime is closed or Thaumcraft is absent
     */
    public static Map<String, Integer> give(LegacyRuntime runtime, Object hostPlayer, Map<String, Integer> amounts) {
        require(runtime);
        if (amounts.isEmpty()) throw new IllegalArgumentException("No aspects to give");
        var lookup = runtime.accessor(AspectLookup.class);
        var aspects = new LinkedHashMap<String, LegacyObject>();
        amounts.forEach((tag, amount) -> {
            if (amount == null || amount < 1 || amount > MAX_POOL) throw new IllegalArgumentException("Invalid amount for " + tag + ": " + amount);
            var aspect = lookup.find(tag);
            if (aspect == null) throw new IllegalArgumentException("Unknown aspect: " + tag);
            aspects.put(tag, aspect);
        });
        return runtime.withPlayer(hostPlayer, player -> {
            String name = runtime.accessor(Player.class, player).name();
            var knowledge = knowledge(runtime);
            var totals = new LinkedHashMap<String, Integer>();
            aspects.forEach((tag, aspect) -> {
                int added = Math.min(amounts.get(tag), MAX_POOL - knowledge.pool(name, aspect));
                if (added > 0) knowledge.add(name, aspect, (short) added);
                totals.put(tag, (int) knowledge.pool(name, aspect));
            });
            return Collections.unmodifiableMap(totals);
        });
    }
}
