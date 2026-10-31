package github.com.gengyoubo.tcmodern.lib.book;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import github.com.gengyoubo.tcmodern.api.TcModernApi;
import github.com.gengyoubo.tcmodern.api.research.TcResearch;
import github.com.gengyoubo.tcmodern.lib.TcModernLib;
import github.com.gengyoubo.tcmodern.lib.json.ResearchLoader;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Research debugging for operators, the command-line side of the Thaumonomicon's debug mode:
 * {@code /tcmodernlib research revoke <targets> <research|all>} takes research back (only the named one), and
 * {@code /tcmodernlib research reload} reloads the JSON research on the server and every client.
 */
@Mod.EventBusSubscriber(modid = TcModernLib.MOD_ID)
public final class BookCommand {
    private static final String ALL = "all";

    private BookCommand() { }

    @SubscribeEvent public static void register(RegisterCommandsEvent event) { register(event.getDispatcher()); }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal(TcModernLib.MOD_ID).requires(source -> source.hasPermission(BookLogic.CHEAT_PERMISSION))
                .then(Commands.literal("research")
                        .then(Commands.literal("revoke").then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("research", StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(Stream.concat(Stream.of(ALL),
                                                TcModernApi.runtime().map(TcResearch::keys).orElse(List.of()).stream()), builder))
                                        .executes(BookCommand::revoke))))
                        .then(Commands.literal("reload").executes(BookCommand::reload))));
    }

    private static int revoke(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var source = context.getSource();
        var targets = EntityArgument.getPlayers(context, "targets");
        String key = StringArgumentType.getString(context, "research");
        var runtime = TcModernApi.runtime();
        if (runtime.isEmpty()) {
            source.sendFailure(Component.translatable("commands.tcmodernlib.unavailable"));
            return 0;
        }
        var keys = key.equals(ALL) ? TcResearch.keys(runtime.get()) : List.of(key);
        for (var player : targets) {
            int removed = TcResearch.revoke(runtime.get(), player, keys);
            source.sendSuccess(() -> Component.translatable("commands.tcmodernlib.research.revoke", removed, player.getDisplayName()), true);
        }
        return targets.size();
    }

    private static int reload(CommandContext<CommandSourceStack> context) {
        var source = context.getSource();
        var runtime = TcModernApi.runtime();
        if (runtime.isEmpty()) {
            source.sendFailure(Component.translatable("commands.tcmodernlib.unavailable"));
            return 0;
        }
        var result = ResearchLoader.reload(runtime.get());
        BookNetwork.reloaded(source.getServer(), result);
        source.sendSuccess(() -> Component.translatable("commands.tcmodernlib.research.reload",
                result.categories(), result.research(), result.errors().size()), true);
        result.errors().stream().limit(8).forEach(error -> source.sendFailure(Component.literal(error)));
        return result.research();
    }
}
