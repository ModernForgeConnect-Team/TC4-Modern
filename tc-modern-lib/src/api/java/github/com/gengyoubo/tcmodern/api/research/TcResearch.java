package github.com.gengyoubo.tcmodern.api.research;

import github.com.gengyoubo.mfcl.api.LegacyObject;
import github.com.gengyoubo.mfcl.api.LegacyRuntime;
import github.com.gengyoubo.tcmodern.api.TcModernApi;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import static github.com.gengyoubo.tcmodern.api.research.TcResearchAccessors.*;

/** Reads the original research registry and unlocks research for a player inside MFCL's scoped player transaction. */
public final class TcResearch {
    private TcResearch() { }

    /** Every registered research key, by category in registration order. Empty when the runtime is closed or Thaumcraft is absent. */
    public static List<String> keys(LegacyRuntime runtime) {
        if (!available(runtime)) return List.of();
        var result = new ArrayList<String>();
        for (var item : items(runtime)) result.add(runtime.accessor(Item.class, item).key());
        return List.copyOf(result);
    }

    /**
     * Completes one research together with its parents, hidden parents and siblings, recursively, as the original
     * {@code /thaumcraft research <player> <key>} does. Call on the owning server thread; MFCL persists the research
     * and resynchronizes it to the client afterwards. Completing research also applies its warp, as in the original.
     *
     * @param hostPlayer the host {@code ServerPlayer}
     * @return how many research entries were newly completed; 0 when all of them were already known
     * @throws IllegalArgumentException when no research has that key; nothing is changed then
     * @throws IllegalStateException    when the runtime is closed or Thaumcraft is absent
     */
    public static int complete(LegacyRuntime runtime, Object hostPlayer, String key) {
        require(runtime);
        var categories = runtime.accessor(Categories.class);
        if (categories.find(key) == null) throw new IllegalArgumentException("Unknown research: " + key);
        return runtime.withPlayer(hostPlayer, player -> {
            var unlock = new Unlock(runtime, player);
            var pending = new ArrayDeque<String>(List.of(key));
            var seen = new HashSet<String>();
            while (!pending.isEmpty()) {
                String next = pending.pop();
                // Requisites naming research that no addon registered are skipped rather than failing the rest.
                var found = categories.find(next);
                if (!seen.add(next) || found == null) continue;
                unlock.complete(next);
                var item = runtime.accessor(Item.class, found);
                for (var requisites : new String[][]{item.parents(), item.parentsHidden(), item.siblings()})
                    if (requisites != null) for (var requisite : requisites) if (requisite != null) pending.push(requisite);
            }
            return unlock.completed;
        });
    }

    /**
     * Completes every registered research, as the original {@code /thaumcraft research <player> all} does.
     *
     * @return how many research entries were newly completed
     * @throws IllegalStateException when the runtime is closed or Thaumcraft is absent
     * @see #complete(LegacyRuntime, Object, String)
     */
    public static int completeAll(LegacyRuntime runtime, Object hostPlayer) {
        require(runtime);
        return runtime.withPlayer(hostPlayer, player -> {
            var unlock = new Unlock(runtime, player);
            for (var item : items(runtime)) unlock.complete(runtime.accessor(Item.class, item).key());
            return unlock.completed;
        });
    }

    private static List<LegacyObject> items(LegacyRuntime runtime) {
        var values = runtime.values();
        var result = new ArrayList<LegacyObject>();
        for (Object category : values.map(runtime.accessor(Categories.class).categories()).values())
            for (Object item : values.map(runtime.accessor(Category.class, (LegacyObject) category).research()).values())
                result.add((LegacyObject) item);
        return result;
    }

    private static boolean available(LegacyRuntime runtime) {
        return runtime.isOpen() && runtime.loadedMods().contains(TcModernApi.LEGACY_MOD_ID);
    }

    private static void require(LegacyRuntime runtime) {
        if (!available(runtime)) throw new IllegalStateException("Thaumcraft runtime is not available");
    }

    /** One player's research progress within a single transaction. */
    private static final class Unlock {
        private final LegacyObject player;
        private final String name;
        private final Progress progress;
        private final Manager manager;
        int completed;

        Unlock(LegacyRuntime runtime, LegacyObject player) {
            this.player = player;
            this.name = runtime.accessor(Player.class, player).name();
            this.progress = runtime.accessor(Progress.class);
            this.manager = runtime.accessor(Manager.class, runtime.accessor(Proxy.class, runtime.accessor(Thaumcraft.class).proxy()).manager());
        }

        void complete(String key) {
            if (progress.complete(name, key)) return;
            manager.complete(player, key);
            completed++;
        }
    }
}
