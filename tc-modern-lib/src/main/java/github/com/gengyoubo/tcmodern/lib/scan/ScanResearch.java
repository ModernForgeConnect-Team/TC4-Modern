package github.com.gengyoubo.tcmodern.lib.scan;

import github.com.gengyoubo.mfcl.api.*;
import github.com.gengyoubo.tcmodern.api.TcModernApi;
import github.com.gengyoubo.tcmodern.api.scan.*;
import java.util.LinkedHashMap;
import java.util.Optional;
import net.minecraft.server.level.ServerPlayer;

/** Read-only original tag/history queries. Completion belongs exclusively to TC4. */
public final class ScanResearch {
    private ScanResearch() { }
    public static boolean scanned(ServerPlayer player, ScanKey key) {
        var api = TcModernApi.runtime().orElseThrow();
        return api.withPlayer(player, old -> {
            var world = api.accessor(ScanAccessors.Entity.class, old).world();
            LegacyObject entity = key.kind() == ScanKey.Kind.ENTITY ? api.accessor(ScanAccessors.EntityFactory.class)
                    .create(api.hostRepresentations().entity(key.id()), world) : null;
            if (key.kind() == ScanKey.Kind.ENTITY && entity == null) throw new IllegalStateException("Missing original entity identity: " + key);
            int id = 0, meta = 0;
            if (entity == null) {
                var source = OriginalScanBridge.stack(key);
                var item = api.accessor(ScanAccessors.Stack.class, api.stack(source)).item();
                id = api.accessor(ScanAccessors.Item.class).id(item); meta = source.metadata();
            }
            var result = api.accessor(ScanAccessors.ResultFactory.class).create((byte)(entity == null ? 1 : 2), id, meta, entity, "");
            return api.accessor(ScanAccessors.Manager.class).scanned(old, result);
        });
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
}