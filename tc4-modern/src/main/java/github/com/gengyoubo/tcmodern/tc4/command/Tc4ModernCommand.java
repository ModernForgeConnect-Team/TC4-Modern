package github.com.gengyoubo.tcmodern.tc4.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import github.com.gengyoubo.mfcl.api.LegacyRuntime;
import github.com.gengyoubo.tcmodern.api.TcModernApi;
import github.com.gengyoubo.tcmodern.api.aspect.TcAspect;
import github.com.gengyoubo.tcmodern.api.aspect.TcAspects;
import github.com.gengyoubo.tcmodern.api.aspect.TcPlayerAspects;
import github.com.gengyoubo.tcmodern.tc4.Tc4Modern;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** {@code /tc4modern aspect give <targets> <aspect|all> [amount]}: adds points to the original research aspect pool. */
@Mod.EventBusSubscriber(modid = Tc4Modern.MOD_ID)
public final class Tc4ModernCommand {
    private static final String ALL = "all";

    private Tc4ModernCommand() { }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) { register(event.getDispatcher()); }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal(Tc4Modern.MOD_ID).requires(source -> source.hasPermission(2))
                .then(Commands.literal("aspect").then(Commands.literal("give")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("aspect", StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(Stream.concat(Stream.of(ALL),
                                                TcModernApi.runtime().map(TcAspects::snapshot).orElse(java.util.List.of()).stream().map(TcAspect::tag)), builder))
                                        .executes(context -> give(context, 1))
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(1, TcPlayerAspects.MAX_POOL))
                                                .executes(context -> give(context, IntegerArgumentType.getInteger(context, "amount")))))))));
    }

    private static int give(CommandContext<CommandSourceStack> context, int amount) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var source = context.getSource();
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(context, "targets");
        String tag = StringArgumentType.getString(context, "aspect");
        var runtime = TcModernApi.runtime();
        if (runtime.isEmpty()) {
            source.sendFailure(Component.translatable("commands.tc4modern.unavailable"));
            return 0;
        }
        var amounts = amounts(runtime.get(), tag, amount);
        if (amounts == null) {
            source.sendFailure(Component.translatable("commands.tc4modern.aspect.unknown", tag));
            return 0;
        }
        int given = 0;
        for (var player : targets) {
            try {
                var totals = TcPlayerAspects.give(runtime.get(), player, amounts);
                given++;
                if (targets.size() == 1) source.sendSuccess(() -> amounts.size() == 1
                        ? Component.translatable("commands.tc4modern.aspect.give.single", amount, name(tag), player.getDisplayName(), totals.get(tag))
                        : Component.translatable("commands.tc4modern.aspect.give.all", amount, amounts.size(), player.getDisplayName()), true);
            } catch (RuntimeException failure) {
                source.sendFailure(Component.translatable("commands.tc4modern.aspect.give.failed", player.getDisplayName(), String.valueOf(failure.getMessage())));
            }
        }
        if (targets.size() > 1 && given > 0) {
            int players = given;
            source.sendSuccess(() -> amounts.size() == 1
                    ? Component.translatable("commands.tc4modern.aspect.give.single.multiple", amount, name(tag), players)
                    : Component.translatable("commands.tc4modern.aspect.give.all.multiple", amount, amounts.size(), players), true);
        }
        return given;
    }

    /** Null when the tag names no registered aspect. */
    private static Map<String, Integer> amounts(LegacyRuntime runtime, String tag, int amount) {
        var result = new LinkedHashMap<String, Integer>();
        for (var aspect : TcAspects.snapshot(runtime)) if (tag.equals(ALL) || aspect.tag().equals(tag)) result.put(aspect.tag(), amount);
        return result.isEmpty() ? null : result;
    }

    /** The original shows an aspect by its capitalised tag; the server has no client translations. */
    private static String name(String tag) { return Character.toUpperCase(tag.charAt(0)) + tag.substring(1); }
}
