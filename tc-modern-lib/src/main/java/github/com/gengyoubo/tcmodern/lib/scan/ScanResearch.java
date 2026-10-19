package github.com.gengyoubo.tcmodern.lib.scan;

import github.com.gengyoubo.mfcl.api.LegacyRuntime;
import github.com.gengyoubo.mfcl.api.LegacyStack;
import github.com.gengyoubo.tcmodern.api.TcModernApi;
import github.com.gengyoubo.tcmodern.api.scan.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.server.level.ServerPlayer;

/** Original prerequisites, diminishing returns, notifications and persistence remain authoritative. */
public final class ScanResearch {
    public enum Outcome { SUCCESS, ALREADY_SCANNED, MISSING_PREREQUISITES }
    private ScanResearch() { }
    public static boolean scanned(ServerPlayer player, ScanKey key) {
        var api = TcModernApi.runtime().orElseThrow();
        return api.withPlayer(player, old -> api.accessor(ScanAccessors.Manager.class).scanned(old,
                api.accessor(ScanAccessors.Result.class).create((byte)3,0,0,null,key.researchKey())));
    }
    public static Optional<AspectValues> original(LegacyStack source) {
        return TcModernApi.runtime().flatMap(api -> {
            var crafting = api.accessor(ScanAccessors.Crafting.class);
            var stack = api.stack(source);
            var tags = crafting.bonus(stack, crafting.tags(stack));
            if (tags == null) return Optional.empty();
            var list = api.accessor(ScanAccessors.Aspects.class, tags);
            var result = new LinkedHashMap<String, Integer>();
            for (var aspect : list.values()) {
                int amount = list.amount(aspect);
                if (amount > 0) result.put(api.accessor(ScanAccessors.Aspect.class, aspect).tag(), amount);
            }
            return result.isEmpty() ? Optional.empty() : Optional.of(new AspectValues(result));
        });
    }
    public static Outcome complete(ServerPlayer player, ScanKey key, AspectValues values) {
        var api = TcModernApi.runtime().orElseThrow();
        return api.withPlayer(player, oldPlayer -> {
            var manager = api.accessor(ScanAccessors.Manager.class);
            var result = api.accessor(ScanAccessors.Result.class).create((byte) 3, 0, 0, null, key.researchKey());
            if (manager.scanned(oldPlayer, result)) return Outcome.ALREADY_SCANNED;
            var tags = api.accessor(ScanAccessors.Aspects.class).create();
            var aspects = api.accessor(ScanAccessors.Aspects.class, tags);
            var types = api.accessor(ScanAccessors.Aspect.class);
            var resolved = new LinkedHashMap<String, github.com.gengyoubo.mfcl.api.LegacyObject>();
            for (var entry : values.amounts().entrySet()) {
                var type = types.find(entry.getKey());
                if (type == null) throw new IllegalArgumentException("Unknown original TC4 aspect: " + entry.getKey());
                aspects.add(type, entry.getValue()); resolved.put(entry.getKey(), type);
            }
            if (!manager.valid(tags, oldPlayer)) return Outcome.MISSING_PREREQUISITES;
            var proxy = api.accessor(ScanAccessors.Proxy.class, api.accessor(ScanAccessors.Thaumcraft.class).proxy());
            api.accessor(ScanAccessors.Research.class, proxy.research()).mark(oldPlayer, "@" + key.researchKey());
            var earned = api.accessor(ScanAccessors.Aspects.class).create();
            for (var entry : resolved.entrySet()) {
                int count = manager.award(oldPlayer, entry.getValue(), values.amounts().get(entry.getKey()));
                if (count > 0) api.accessor(ScanAccessors.Aspects.class, earned).add(entry.getValue(), count);
            }
            api.accessor(ScanAccessors.Research.class).clue(api.accessor(ScanAccessors.Entity.class, oldPlayer).world(),
                    oldPlayer, api.accessor(ScanAccessors.Result.class, result).source(), earned);
            return Outcome.SUCCESS;
        });
    }
}
