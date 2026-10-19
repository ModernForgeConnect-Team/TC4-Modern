package github.com.gengyoubo.tcmodern.lib.scan;

import java.util.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Server-owned scan sessions. No target, aspect amounts or reward claims are trusted from clients. */
public final class ModernScanning {
    public static final int DURATION = 20;
    private record Session(int token, InteractionHand hand, ModernScanTargets.Target target, long start) { }
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private ModernScanning() { }
    public static void install() { ScanNetwork.register(); MinecraftForge.EVENT_BUS.register(ModernScanning.class); }
    public static void request(ServerPlayer player, InteractionHand hand, boolean begin, int token) {
        if (!begin && SESSIONS.containsKey(player.getUUID()) && SESSIONS.get(player.getUUID()).token() != token) return;
        SESSIONS.remove(player.getUUID());
        if (!begin) return;
        if (!player.isAlive() || player.isSpectator() || !ModernScanTargets.scanner(player.getItemInHand(hand))) {
            ScanNetwork.result(player,token,"cancelled"); return;
        }
        var target = ModernScanTargets.find(player);
        if (target.isPresent()) SESSIONS.put(player.getUUID(), new Session(token, hand, target.get(), player.level().getGameTime()));
        else ScanNetwork.result(player,token,"cancelled");
    }
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        var session = SESSIONS.get(player.getUUID());
        if (session == null) return;
        var target = ModernScanTargets.find(player);
        if (!player.isAlive() || player.isSpectator() || !ModernScanTargets.scanner(player.getItemInHand(session.hand()))
                || target.isEmpty() || !target.get().location().equals(session.target().location())
                || !target.get().key().equals(session.target().key())) {
            SESSIONS.remove(player.getUUID()); ScanNetwork.result(player, session.token(), "cancelled"); return;
        }
        if (player.level().getGameTime() - session.start() < DURATION) return;
        SESSIONS.remove(player.getUUID());
        try {
            var outcome = ScanResearch.complete(player, target.get().key(), target.get().aspects());
            String status = switch (outcome) { case SUCCESS -> "success"; case ALREADY_SCANNED -> "already"; case MISSING_PREREQUISITES -> "prerequisites"; };
            ScanNetwork.result(player, session.token(), status);
            player.displayClientMessage(Component.translatable("tcmodern.scan." + status), true);
            if (outcome == ScanResearch.Outcome.SUCCESS) player.level().playSound(null, player.blockPosition(),
                    SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.3F, 1.0F);
        } catch (RuntimeException failure) {
            com.mojang.logging.LogUtils.getLogger().error("TC4 modern scan failed for {}", target.get().key(), failure);
            ScanNetwork.result(player, session.token(), "error");
        }
    }
    @SubscribeEvent public static void sync(OnDatapackSyncEvent event) {
        if (event.getPlayer() != null) ScanNetwork.rules(event.getPlayer());
        else event.getPlayerList().getPlayers().forEach(ScanNetwork::rules);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { SESSIONS.remove(event.getEntity().getUUID()); }
    @SubscribeEvent public static void stopped(net.minecraftforge.event.server.ServerStoppedEvent event) { SESSIONS.clear(); }
}
