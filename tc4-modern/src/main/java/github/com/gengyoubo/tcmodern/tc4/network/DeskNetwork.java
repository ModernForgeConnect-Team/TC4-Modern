package github.com.gengyoubo.tcmodern.tc4.network;

import github.com.gengyoubo.tcmodern.tc4.Tc4Modern;
import github.com.gengyoubo.tcmodern.tc4.menu.ResearchDeskMenu;
import github.com.gengyoubo.tcmodern.tc4.research.DeskLogic;
import github.com.gengyoubo.tcmodern.tc4.research.DeskSnapshot;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;

/** Research desk actions from the viewer and snapshots back. Every action is re-checked on the server. */
public final class DeskNetwork {
    public enum Kind { PLACE, ERASE, COMBINE, COPY, TAKE, FILE }

    /**
     * {@code PLACE}: {@code q, r, first}; {@code ERASE}: {@code q, r}; {@code COMBINE}: {@code first, second};
     * {@code TAKE}: {@code q} is the cabinet and {@code r} the drawer slot; {@code COPY}, {@code FILE}: nothing.
     */
    public record Action(int menu, Kind kind, int q, int r, String first, String second) {
        void write(FriendlyByteBuf out) {
            out.writeVarInt(menu); out.writeEnum(kind); out.writeVarInt(q); out.writeVarInt(r);
            out.writeUtf(first, 64); out.writeUtf(second, 64);
        }
        static Action read(FriendlyByteBuf in) {
            return new Action(in.readVarInt(), in.readEnum(Kind.class), in.readVarInt(), in.readVarInt(), in.readUtf(64), in.readUtf(64));
        }
    }
    private record Snapshot(int menu, DeskSnapshot value) {}

    private static final String VERSION = "1";
    private static SimpleChannel channel;

    private DeskNetwork() {}

    @SuppressWarnings("removal")
    public static void register() {
        channel = NetworkRegistry.newSimpleChannel(ResourceLocation.fromNamespaceAndPath(Tc4Modern.MOD_ID, "research_desk"),
                () -> VERSION, VERSION::equals, VERSION::equals);
        channel.registerMessage(0, Action.class, Action::write, Action::read, DeskNetwork::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        channel.registerMessage(1, Snapshot.class, (value, out) -> { out.writeVarInt(value.menu()); value.value().write(out); },
                in -> new Snapshot(in.readVarInt(), DeskSnapshot.read(in)), DeskNetwork::receive, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    public static void send(Action action) { channel.sendToServer(action); }

    public static void snapshot(ServerPlayer player, int menu, DeskSnapshot value) {
        channel.send(PacketDistributor.PLAYER.with(() -> player), new Snapshot(menu, value));
    }

    private static void handle(Action action, Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> {
            var player = context.get().getSender();
            if (player != null && player.containerMenu instanceof ResearchDeskMenu menu && menu.containerId == action.menu()
                    && menu.stillValid(player)) DeskLogic.handle(player, menu, action);
        });
        context.get().setPacketHandled(true);
    }

    private static void receive(Snapshot snapshot, Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> Client.apply(snapshot)));
        context.get().setPacketHandled(true);
    }

    private static final class Client {
        static void apply(Snapshot snapshot) {
            var player = Minecraft.getInstance().player;
            if (player != null && player.containerMenu instanceof ResearchDeskMenu menu && menu.containerId == snapshot.menu())
                menu.snapshot(snapshot.value());
        }
    }
}
