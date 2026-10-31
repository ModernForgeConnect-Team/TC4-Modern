package github.com.gengyoubo.tcmodern.lib.book;

import com.mojang.logging.LogUtils;
import github.com.gengyoubo.mfcl.api.LegacyMappings;
import github.com.gengyoubo.mfcl.api.LegacyRuntime;
import github.com.gengyoubo.tcmodern.api.TcModernApi;
import github.com.gengyoubo.tcmodern.api.aspect.TcAspectAccessors;
import github.com.gengyoubo.tcmodern.api.research.*;
import github.com.gengyoubo.tcmodern.lib.json.ResearchLoader;
import java.util.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/**
 * Server side of the modern Thaumonomicon. Buying research and writing research notes follow the original
 * {@code PacketPlayerCompleteToServer}; the editing mode's actions are for operators (permission level 2) only.
 */
public final class BookLogic {
    /** Permission level of the editing mode, as FTB Quests' own. */
    public static final int CHEAT_PERMISSION = 2;

    private BookLogic() {}

    public static boolean mayCheat(ServerPlayer player) { return player.hasPermissions(CHEAT_PERMISSION); }

    public static void handle(ServerPlayer player, BookNetwork.Action action) {
        var runtime = TcModernApi.runtime().orElse(null);
        if (runtime == null) return;
        try {
            switch (action.kind()) {
                case BUY -> buy(runtime, player, action.key());
                case NOTE -> note(runtime, player, action.key());
                default -> {
                    if (!mayCheat(player)) {
                        player.displayClientMessage(Component.translatable("book.tcmodernlib.cheat.denied"), true);
                        return;
                    }
                    cheat(runtime, player, action);
                }
            }
        } catch (RuntimeException failure) {
            LogUtils.getLogger().warn("Thaumonomicon action {} {} failed: {}", action.kind(), action.key(), failure.toString());
        }
    }

    /** Buys a research with aspect points where the difficulty allows it, then completes it and its siblings. */
    private static void buy(LegacyRuntime runtime, ServerPlayer player, String key) {
        var entry = TcThaumonomicon.find(runtime, key).orElse(null);
        if (entry == null || entry.aspects().isEmpty() || !buyable(runtime, entry)) return;
        var unlocked = runtime.withPlayer(player, actor -> {
            String name = runtime.accessor(TcAspectAccessors.Player.class, actor).name();
            var progress = runtime.accessor(TcBookAccessors.Progress.class);
            if (TcThaumonomicon.completed(runtime, name).contains(key) || !progress.requisites(name, key)) return List.<String>of();
            var knowledge = runtime.accessor(TcAspectAccessors.Knowledge.class, runtime.accessor(TcAspectAccessors.Proxy.class,
                    runtime.accessor(TcAspectAccessors.Thaumcraft.class).proxy()).knowledge());
            var lookup = runtime.accessor(TcAspectAccessors.AspectLookup.class);
            for (var cost : entry.aspects().entrySet()) {
                var aspect = lookup.find(cost.getKey());
                if (aspect == null || knowledge.pool(name, aspect) < cost.getValue()) return List.<String>of();
            }
            for (var cost : entry.aspects().entrySet()) knowledge.add(name, lookup.find(cost.getKey()), (short) -cost.getValue());
            var keys = new ArrayList<String>(List.of(key));
            for (String sibling : entry.siblings())
                if (!TcThaumonomicon.completed(runtime, name).contains(sibling) && progress.requisites(name, sibling)) keys.add(sibling);
            return keys;
        });
        if (unlocked.isEmpty()) return;
        TcResearch.completeOnly(runtime, player, unlocked);
        learn(player);
    }

    /** As the original book: a research note for research that must be studied, using ink and one paper. */
    private static void note(LegacyRuntime runtime, ServerPlayer player, String key) {
        var world = runtime.mappings().toLegacy(LegacyMappings.WORLD, player.serverLevel()).orElse(null);
        if (world == null) return;
        boolean written = runtime.withPlayer(player, actor -> {
            String name = runtime.accessor(TcAspectAccessors.Player.class, actor).name();
            var progress = runtime.accessor(TcBookAccessors.Progress.class);
            if (TcThaumonomicon.completed(runtime, name).contains(key) || !progress.requisites(name, key)) return false;
            return progress.note(world, actor, key) != null;
        });
        if (written) learn(player);
    }

    /** True when the original book sells this research for points at the current difficulty. */
    public static boolean buyable(LegacyRuntime runtime, TcResearchEntry entry) {
        int difficulty = TcThaumonomicon.difficulty(runtime);
        return difficulty == -1 || difficulty == 0 && entry.has(TcResearchEntry.Flag.SECONDARY);
    }

    private static void cheat(LegacyRuntime runtime, ServerPlayer player, BookNetwork.Action action) {
        if (action.kind() == BookNetwork.Kind.SAVE || action.kind() == BookNetwork.Kind.DELETE || action.kind() == BookNetwork.Kind.MOVE) {
            BookEditing.handle(runtime, player, action);
            return;
        }
        var book = TcThaumonomicon.snapshot(runtime);
        int changed = switch (action.kind()) {
            case COMPLETE -> action.cascade() ? TcResearch.complete(runtime, player, action.key())
                    : TcResearch.completeOnly(runtime, player, List.of(action.key()));
            case REVOKE -> TcResearch.revoke(runtime, player, action.cascade() ? dependents(book, action.key()) : List.of(action.key()));
            case CATEGORY_COMPLETE -> TcResearch.completeOnly(runtime, player, category(book, action.key()));
            case CATEGORY_RESET -> TcResearch.revoke(runtime, player, category(book, action.key()));
            case ALL_COMPLETE -> TcResearch.completeAll(runtime, player);
            case ALL_RESET -> TcResearch.revoke(runtime, player, book.stream().flatMap(c -> c.entries().stream()).map(TcResearchEntry::key).toList());
            case RELOAD -> {
                var result = ResearchLoader.reload(runtime);
                BookNetwork.reloaded(player.server, result);
                yield result.research() + result.categories();
            }
            default -> 0;
        };
        player.displayClientMessage(Component.translatable("book.tcmodernlib.cheat.done." + action.kind().name().toLowerCase(Locale.ROOT), changed), true);
        if (action.kind() == BookNetwork.Kind.COMPLETE || action.kind() == BookNetwork.Kind.CATEGORY_COMPLETE) learn(player);
    }

    /** The research and everything that needs it, directly or through other research. */
    static List<String> dependents(List<TcResearchCategory> book, String key) {
        var result = new LinkedHashSet<String>(List.of(key));
        boolean grew = true;
        while (grew) {
            grew = false;
            for (var category : book) for (var entry : category.entries())
                if (!result.contains(entry.key()) && entry.requisites().stream().anyMatch(result::contains)) grew |= result.add(entry.key());
        }
        return List.copyOf(result);
    }

    private static List<String> category(List<TcResearchCategory> book, String key) {
        return book.stream().filter(c -> c.key().equals(key)).flatMap(c -> c.entries().stream()).map(TcResearchEntry::key).toList();
    }

    private static void learn(ServerPlayer player) {
        player.serverLevel().playSound(null, player.blockPosition(),
                SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("thaumcraft", "learn")), SoundSource.PLAYERS, 0.75F, 1.0F);
    }
}
