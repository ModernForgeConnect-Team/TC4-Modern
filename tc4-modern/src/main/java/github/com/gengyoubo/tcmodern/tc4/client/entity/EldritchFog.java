package github.com.gengyoubo.tcmodern.tc4.client.entity;

import github.com.gengyoubo.tcmodern.tc4.Tc4Modern;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The dark fog an eldritch guardian brings outside the Outer Lands (PacketMiscEvent 2): every five seconds a guardian
 * within 16 blocks (24 on hard) closes the fog in for at least ten seconds.
 */
@Mod.EventBusSubscriber(modid = Tc4Modern.MOD_ID, value = Dist.CLIENT)
public final class EldritchFog {
    private static int duration;
    private static float density;
    private EldritchFog() { }
    public static void trigger() { duration = Math.max(duration, 200); }
    public static boolean isActive() { return duration > 0; }

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var minecraft = Minecraft.getInstance();
        var player = minecraft.player;
        if (player == null || minecraft.level == null) { duration = 0; density = 0; return; }
        if (minecraft.isPaused()) return;
        if (duration > 0) {
            if (duration < 100) density = 0.1F * duration / 100.0F;
            else density = Math.min(0.1F, density + 0.001F);
            duration--;
        } else density = 0;
    }

    @SubscribeEvent public static void fog(ViewportEvent.RenderFog event) {
        if (density <= 0.0F) return;
        float far = Math.min(event.getFarPlaneDistance(), 1.6F / density);
        if (far >= event.getFarPlaneDistance()) return;
        event.setNearPlaneDistance(0.0F);
        event.setFarPlaneDistance(far);
        event.setCanceled(true);
    }
}
