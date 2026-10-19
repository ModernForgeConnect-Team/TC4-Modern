package github.com.gengyoubo.tcmodern.lib.scan.client;

import github.com.gengyoubo.tcmodern.api.scan.ScanKey;
import github.com.gengyoubo.tcmodern.lib.scan.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Modern targets use a server session, while original targets retain MFCL's original held-use path. */
@Mod.EventBusSubscriber(modid="tcmodernlib", value=Dist.CLIENT)
public final class ModernScannerClient {
    private static ModernScanTargets.Target target;
    private static InteractionHand hand;
    private static boolean active, attempted;
    private static int start, token;
    private static String status = "ready";
    private static final Set<ScanKey> SCANNED = new HashSet<>();
    private ModernScannerClient() { }
    @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
    public static void click(InputEvent.InteractionKeyMappingTriggered event) {
        var mc = Minecraft.getInstance();
        if (event.isUseItem() && mc.player != null && ModernScanTargets.scanner(mc.player.getItemInHand(event.getHand()))
                && (active || ModernScanTargets.find(mc.player).isPresent())) {
            event.setCanceled(true); event.setSwingHand(false);
        }
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.screen != null) { cancel(); target = null; return; }
        InteractionHand nextHand = ModernScanTargets.scanner(mc.player.getMainHandItem()) ? InteractionHand.MAIN_HAND
                : ModernScanTargets.scanner(mc.player.getOffhandItem()) ? InteractionHand.OFF_HAND : null;
        var next = nextHand == null ? Optional.<ModernScanTargets.Target>empty() : ModernScanTargets.find(mc.player);
        if (next.isEmpty()) { cancel(); target = null; return; }
        if (target == null || !target.location().equals(next.get().location()) || nextHand != hand) {
            cancel(); target = next.get(); hand = nextHand;
        } else target = next.get();
        if (!mc.options.keyUse.isDown()) { cancel(); return; }
        if (!attempted) {
            if (mc.player.isUsingItem() && mc.gameMode != null) mc.gameMode.releaseUsingItem(mc.player);
            attempted = true; active = true; status = "scanning"; start = mc.player.tickCount; token++;
            ScanNetwork.request(hand,true,token);
        }
    }
    private static void cancel() {
        var mc = Minecraft.getInstance();
        if (active && mc.getConnection() != null) ScanNetwork.request(hand,false,token);
        active = false; attempted = false; status = "ready";
    }
    public static void result(int request, String value) {
        if (request != token || !active) return;
        active = false; status = value;
        if (target != null && (value.equals("success") || value.equals("already"))) SCANNED.add(target.key());
    }
    @SubscribeEvent public static void disconnect(ClientPlayerNetworkEvent.LoggingOut event) {
        active = false; attempted = false; target = null; SCANNED.clear(); ScanRules.disconnect();
    }
    @Mod.EventBusSubscriber(modid="tcmodernlib", value=Dist.CLIENT, bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class Overlay {
        @SubscribeEvent public static void register(RegisterGuiOverlaysEvent event) {
            event.registerAboveAll("modern_scan", (gui, graphics, partialTick, width, height) -> {
                var mc = Minecraft.getInstance();
                if (target == null || mc.player == null || mc.options.hideGui || !mc.options.getCameraType().isFirstPerson()) return;
                int x = width / 2, y = height / 2 + 34;
                graphics.drawCenteredString(mc.font,target.label(),x,y,0xFFFFFF);
                graphics.drawCenteredString(mc.font,Component.translatable("tcmodern.scan." + status),x,y+12,0xD9CBA3);
                if (active) {
                    int progress = Math.min(ModernScanning.DURATION, mc.player.tickCount - start);
                    graphics.fill(x-40,y+25,x+40,y+28,0xAA202020);
                    graphics.fill(x-40,y+25,x-40+80*progress/ModernScanning.DURATION,y+28,0xFF88CFFF);
                }
                if (!SCANNED.contains(target.key())) return;
                var entries = new ArrayList<>(target.aspects().amounts().entrySet());
                for (int i = 0; i < entries.size(); i++) {
                    var aspect = entries.get(i); int col=i%8,row=i/8, columns=Math.min(8,entries.size()-row*8);
                    int ax=x-columns*22/2+col*22, ay=y+34+row*27;
                    graphics.blit(ResourceLocation.fromNamespaceAndPath("thaumcraft","textures/aspects/"+aspect.getKey()+".png"),ax,ay,0,0,16,16,16,16);
                    graphics.drawString(mc.font,Integer.toString(aspect.getValue()),ax+10,ay+12,0xFFFFFF,true);
                }
            });
        }
    }
}
