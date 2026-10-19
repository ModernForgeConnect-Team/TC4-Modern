package github.com.gengyoubo.tcmodern.lib.scan;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Synchronizes registrations; scans run through the original TC4/MFCL path. */
public final class ModernScanning {
    private ModernScanning() { }
    public static void install() { ScanNetwork.register(); MinecraftForge.EVENT_BUS.register(ModernScanning.class); }
    @SubscribeEvent public static void sync(OnDatapackSyncEvent event) {
        OriginalScanBridge.install(ScanRules.server(), OriginalScanBridge.overrides());
        if (event.getPlayer() != null) ScanNetwork.rules(event.getPlayer());
        else event.getPlayerList().getPlayers().forEach(ScanNetwork::rules);
    }
}