package github.com.gengyoubo.tcmodern.tc4.client;

import github.com.gengyoubo.tcmodern.tc4.Tc4Modern;
import github.com.gengyoubo.tcmodern.tc4.init.Tc4Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Magic bone meal reuses the installed vanilla bone meal texture (layer 0), tinted with vis; layer 1 is its own sparkle. */
@Mod.EventBusSubscriber(modid = Tc4Modern.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class MagicBoneMealClient {
    private static final int TINT = 0xA8F0D8;

    private MagicBoneMealClient() {}

    @SubscribeEvent public static void colors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, layer) -> layer == 0 ? TINT : -1, Tc4Items.MAGIC_BONE_MEAL.get());
    }
}
