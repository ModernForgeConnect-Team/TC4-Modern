package github.com.gengyoubo.tcmodern.lib.scan;

import github.com.gengyoubo.tcmodern.api.scan.*;
import java.util.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;

/** Registration sync only. Original TC4 packets carry scan completions through MFCL. */
public final class ScanNetwork {
    private record Rules(Map<ScanKey, AspectValues> values, Set<ScanKey> overrides) { }
    private static SimpleChannel channel;
    private ScanNetwork() { }
    @SuppressWarnings("removal")
    public static void register() {
        channel = NetworkRegistry.newSimpleChannel(ResourceLocation.fromNamespaceAndPath("tcmodernlib", "scans"), () -> "2", "2"::equals, "2"::equals);
        channel.registerMessage(0, Rules.class, ScanNetwork::writeRules, ScanNetwork::readRules,
                (value, context) -> {
                    context.get().enqueueWork(() -> {
                        OriginalScanBridge.install(value.values(), value.overrides());
                        ScanRules.receive(value.values());
                    });
                    context.get().setPacketHandled(true);
                }, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }
    public static void rules(ServerPlayer player) {
        channel.send(PacketDistributor.PLAYER.with(() -> player), new Rules(ScanRules.server(), OriginalScanBridge.overrides()));
    }
    private static void writeRules(Rules rules, FriendlyByteBuf out) {
        out.writeVarInt(rules.values().size());
        rules.values().forEach((key, values) -> {
            out.writeEnum(key.kind()); out.writeUtf(key.id(), 256); out.writeBoolean(rules.overrides().contains(key));
            out.writeVarInt(values.amounts().size());
            values.amounts().forEach((tag, count) -> { out.writeUtf(tag, 64); out.writeVarInt(count); });
        });
    }
    private static Rules readRules(FriendlyByteBuf in) {
        int size = bounded(in.readVarInt(), 8192);
        var result = new LinkedHashMap<ScanKey, AspectValues>(); var overrides = new HashSet<ScanKey>();
        for (int i = 0; i < size; i++) {
            var key = new ScanKey(in.readEnum(ScanKey.Kind.class), in.readUtf(256));
            if (in.readBoolean()) overrides.add(key);
            int count = bounded(in.readVarInt(), 64); var aspects = new LinkedHashMap<String, Integer>();
            for (int a = 0; a < count; a++) aspects.put(in.readUtf(64), bounded(in.readVarInt(), 32767));
            result.put(key, new AspectValues(aspects));
        }
        return new Rules(Map.copyOf(result), Set.copyOf(overrides));
    }
    private static int bounded(int value, int max) { if (value < 0 || value > max) throw new IllegalArgumentException("Invalid scan payload size"); return value; }
}