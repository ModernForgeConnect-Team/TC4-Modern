package github.com.gengyoubo.tcmodern.lib.book;

import github.com.gengyoubo.tcmodern.lib.TcModernLib;
import github.com.gengyoubo.tcmodern.lib.json.ResearchLoader;
import github.com.gengyoubo.tcmodern.api.TcModernApi;
import github.com.gengyoubo.tcmodern.api.research.TcResearch;
import github.com.gengyoubo.tcmodern.api.research.TcResearchEntry;
import github.com.gengyoubo.tcmodern.api.research.TcThaumonomicon;
import java.util.*;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * Modern Thaumonomicon requests and the server's JSON research. The server checks every request again, and its
 * JSON research is the one every client shows: sent on login and after each reload.
 */
@Mod.EventBusSubscriber(modid = TcModernLib.MOD_ID)
public final class BookNetwork {
    /**
     * {@code BUY}/{@code NOTE}: everyone, as the original book. The rest are the operators' editing mode:
     * {@code COMPLETE}/{@code REVOKE} one research ({@code cascade}: with its parents / with what depends on it),
     * {@code CATEGORY_*} the category named by {@code key}, {@code ALL_*} every research, {@code RELOAD} the JSON
     * files, {@code SAVE} a file ({@code key}: its id or empty for a new one, {@code data}: the JSON),
     * {@code DELETE} a file by id, {@code MOVE} a research ({@code key}) to {@code column, row}.
     */
    public enum Kind { BUY, NOTE, COMPLETE, REVOKE, CATEGORY_COMPLETE, CATEGORY_RESET, ALL_COMPLETE, ALL_RESET, RELOAD, SAVE, DELETE, MOVE }

    public record Action(Kind kind, String key, boolean cascade, String data, int column, int row) {
        public Action(Kind kind, String key, boolean cascade) { this(kind, key, cascade, "", 0, 0); }
        void write(FriendlyByteBuf out) {
            out.writeEnum(kind); out.writeUtf(key, 256); out.writeBoolean(cascade); out.writeUtf(data, 262144);
            out.writeVarInt(column); out.writeVarInt(row);
        }
        static Action read(FriendlyByteBuf in) {
            return new Action(in.readEnum(Kind.class), in.readUtf(256), in.readBoolean(), in.readUtf(262144), in.readVarInt(), in.readVarInt());
        }
    }

    /**
     * The server's JSON research, file text by id. {@code announce} reports the counts to the player, after a reload;
     * a login sync is silent.
     */
    public record Reloaded(int categories, int research, List<String> errors, Map<String, String> contents, boolean announce) {
        void write(FriendlyByteBuf out) {
            out.writeVarInt(categories);
            out.writeVarInt(research);
            out.writeVarInt(Math.min(errors.size(), 64));
            errors.stream().limit(64).forEach(error -> out.writeUtf(error.length() > 512 ? error.substring(0, 512) : error, 512));
            out.writeVarInt(contents.size());
            contents.forEach((id, json) -> { out.writeUtf(id, 256); out.writeUtf(json, 262144); });
            out.writeBoolean(announce);
        }
        static Reloaded read(FriendlyByteBuf in) {
            int categories = in.readVarInt(), research = in.readVarInt(), count = in.readVarInt();
            if (count < 0 || count > 64) throw new IllegalArgumentException("Invalid reload payload");
            var errors = new ArrayList<String>();
            for (int i = 0; i < count; i++) errors.add(in.readUtf(512));
            int files = in.readVarInt();
            if (files < 0 || files > 8192) throw new IllegalArgumentException("Invalid research payload");
            var contents = new TreeMap<String, String>();
            for (int i = 0; i < files; i++) contents.put(in.readUtf(256), in.readUtf(262144));
            return new Reloaded(categories, research, List.copyOf(errors), Collections.unmodifiableMap(contents), in.readBoolean());
        }
    }

    /** Full server-authoritative research list: TC4's own sync packet only adds to client knowledge. */
    public record ResearchSync(List<String> completed) {
        void write(FriendlyByteBuf out) {
            out.writeVarInt(completed.size());
            completed.forEach(key -> out.writeUtf(key, 32767));
        }
        static ResearchSync read(FriendlyByteBuf in) {
            int count = in.readVarInt();
            if (count < 0 || count > 65536) throw new IllegalArgumentException("Invalid research progress count");
            var completed = new ArrayList<String>(count);
            for (int i = 0; i < count; i++) completed.add(in.readUtf(32767));
            return new ResearchSync(List.copyOf(completed));
        }
    }

    private static final String VERSION = "3";
    private static SimpleChannel channel;

    private BookNetwork() {}

    @SuppressWarnings("removal")
    public static void register() {
        channel = NetworkRegistry.newSimpleChannel(ResourceLocation.fromNamespaceAndPath(TcModernLib.MOD_ID, "thaumonomicon"),
                () -> VERSION, VERSION::equals, VERSION::equals);
        channel.registerMessage(0, Action.class, Action::write, Action::read, BookNetwork::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        channel.registerMessage(1, Reloaded.class, Reloaded::write, Reloaded::read, BookNetwork::receive, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        channel.registerMessage(2, ResearchSync.class, ResearchSync::write, ResearchSync::read, BookNetwork::receiveResearch, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    public static void send(Action action) { channel.sendToServer(action); }

    public static void research(ServerPlayer player, List<String> completed) {
        channel.send(PacketDistributor.PLAYER.with(() -> player), new ResearchSync(List.copyOf(completed)));
    }

    /** Tells every client about a reload and hands them the server's research. */
    public static void reloaded(MinecraftServer server, ResearchLoader.Result result) {
        channel.send(PacketDistributor.ALL.noArg(),
                new Reloaded(result.categories(), result.research(), result.errors(), ResearchLoader.contents(), true));
        // A reload can introduce default-visible research after players have already joined. Trigger the original
        // player initialization after clients have received the new registry; MFCL persists and syncs the result.
        TcModernApi.runtime().ifPresent(runtime -> {
            var automatic = TcThaumonomicon.snapshot(runtime).stream().flatMap(category -> category.entries().stream())
                    .filter(entry -> ResearchLoader.source(entry.key()) != null && entry.has(TcResearchEntry.Flag.AUTO_UNLOCK))
                    .map(TcResearchEntry::key).toList();
            if (!automatic.isEmpty()) for (var player : server.getPlayerList().getPlayers())
                TcResearch.completeOnly(runtime, player, automatic);
        });
    }

    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            var last = ResearchLoader.last();
            channel.send(PacketDistributor.PLAYER.with(() -> player),
                    new Reloaded(last.categories(), last.research(), List.of(), ResearchLoader.contents(), false));
        }
    }

    private static void handle(Action action, Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> {
            var player = context.get().getSender();
            if (player != null) BookLogic.handle(player, action);
        });
        context.get().setPacketHandled(true);
    }

    private static void receive(Reloaded reloaded, Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> github.com.gengyoubo.tcmodern.lib.client.book.BookClient.reloaded(reloaded)));
        context.get().setPacketHandled(true);
    }

    private static void receiveResearch(ResearchSync progress, Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> github.com.gengyoubo.tcmodern.lib.client.book.BookClient.research(progress.completed())));
        context.get().setPacketHandled(true);
    }
}
