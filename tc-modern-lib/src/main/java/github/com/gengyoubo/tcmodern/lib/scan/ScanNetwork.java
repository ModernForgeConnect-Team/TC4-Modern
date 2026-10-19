package github.com.gengyoubo.tcmodern.lib.scan;

import github.com.gengyoubo.tcmodern.api.scan.*;
import java.util.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ScanNetwork {
    private record Request(InteractionHand hand, boolean begin, int token) { }
    private record Result(int token, String status) { }
    private record Rules(Map<ScanKey, AspectValues> values) { }
    private static SimpleChannel channel;
    private ScanNetwork() { }
    @SuppressWarnings("removal")
    public static void register() {
        channel = NetworkRegistry.newSimpleChannel(ResourceLocation.fromNamespaceAndPath("tcmodernlib", "scans"), () -> "1", "1"::equals, "1"::equals);
        channel.registerMessage(0, Request.class, (value, out) -> { out.writeEnum(value.hand()); out.writeBoolean(value.begin()); out.writeInt(value.token()); },
                in -> new Request(in.readEnum(InteractionHand.class), in.readBoolean(), in.readInt()), (value, context) -> {
                    context.get().enqueueWork(() -> { var player = context.get().getSender(); if (player != null) ModernScanning.request(player, value.hand(), value.begin(), value.token()); });
                    context.get().setPacketHandled(true);
                }, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        channel.registerMessage(1, Result.class, (value,out) -> { out.writeInt(value.token()); out.writeUtf(value.status(),32); }, in -> new Result(in.readInt(),in.readUtf(32)),
                (value,context) -> { context.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                        () -> () -> github.com.gengyoubo.tcmodern.lib.scan.client.ModernScannerClient.result(value.token(),value.status()))); context.get().setPacketHandled(true); },
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        channel.registerMessage(2, Rules.class, ScanNetwork::writeRules, ScanNetwork::readRules,
                (value,context) -> { context.get().enqueueWork(() -> ScanRules.receive(value.values())); context.get().setPacketHandled(true); },
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }
    public static void request(InteractionHand hand, boolean begin, int token) { channel.sendToServer(new Request(hand, begin, token)); }
    public static void result(ServerPlayer player, int token, String status) { channel.send(PacketDistributor.PLAYER.with(() -> player), new Result(token,status)); }
    public static void rules(ServerPlayer player) { channel.send(PacketDistributor.PLAYER.with(() -> player), new Rules(ScanRules.server())); }
    private static void writeRules(Rules rules, FriendlyByteBuf out) {
        out.writeVarInt(rules.values().size());
        rules.values().forEach((key,values) -> {
            out.writeEnum(key.kind()); out.writeUtf(key.id(),256); out.writeVarInt(values.amounts().size());
            values.amounts().forEach((tag,count) -> { out.writeUtf(tag,64); out.writeVarInt(count); });
        });
    }
    private static Rules readRules(FriendlyByteBuf in) {
        int size = bounded(in.readVarInt(),8192); var result = new LinkedHashMap<ScanKey,AspectValues>();
        for (int i = 0; i < size; i++) {
            var key = new ScanKey(in.readEnum(ScanKey.Kind.class), in.readUtf(256));
            int count = bounded(in.readVarInt(),64); var aspects = new LinkedHashMap<String,Integer>();
            for (int a = 0; a < count; a++) aspects.put(in.readUtf(64), bounded(in.readVarInt(),32767));
            result.put(key,new AspectValues(aspects));
        }
        return new Rules(Map.copyOf(result));
    }
    private static int bounded(int value, int max) { if (value < 0 || value > max) throw new IllegalArgumentException("Invalid scan payload size"); return value; }
}
