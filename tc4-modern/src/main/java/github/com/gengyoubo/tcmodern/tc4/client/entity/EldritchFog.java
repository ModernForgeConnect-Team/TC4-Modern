package github.com.gengyoubo.tcmodern.tc4.client.entity;

import github.com.gengyoubo.tcmodern.tc4.Tc4Modern;
import github.com.gengyoubo.tcmodern.tc4.entity.EldritchGuardian;
import github.com.gengyoubo.tcmodern.tc4.entity.Tc4Legacy;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
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
    private static float strength;
    private EldritchFog() { }

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var minecraft = Minecraft.getInstance();
        var player = minecraft.player;
        if (player == null || minecraft.level == null || minecraft.isPaused()) return;
        if (duration > 0) duration--;
        if (minecraft.level.getGameTime() % 100 == 0 && minecraft.level.getDifficulty() != Difficulty.EASY && !Tc4Legacy.isOuterLands(minecraft.level)) {
            double range = minecraft.level.getDifficulty() == Difficulty.HARD ? 24.0 : 16.0;
            boolean near = !minecraft.level.getEntitiesOfClass(EldritchGuardian.class, player.getBoundingBox().inflate(range), g -> g.distanceTo(player) <= range).isEmpty();
            if (near && duration < 200) duration = 200;
        }
        strength = Mth.approach(strength, duration > 0 ? 1.0F : 0.0F, 0.02F);
    }

    @SubscribeEvent public static void fog(ViewportEvent.RenderFog event) {
        if (strength <= 0.0F) return;
        event.setNearPlaneDistance(Mth.lerp(strength, event.getNearPlaneDistance(), 0.0F));
        event.setFarPlaneDistance(Mth.lerp(strength, event.getFarPlaneDistance(), 24.0F));
        event.setCanceled(true);
    }

    @SubscribeEvent public static void color(ViewportEvent.ComputeFogColor event) {
        if (strength <= 0.0F) return;
        event.setRed(Mth.lerp(strength, event.getRed(), 0.05F));
        event.setGreen(Mth.lerp(strength, event.getGreen(), 0.02F));
        event.setBlue(Mth.lerp(strength, event.getBlue(), 0.08F));
    }
}
