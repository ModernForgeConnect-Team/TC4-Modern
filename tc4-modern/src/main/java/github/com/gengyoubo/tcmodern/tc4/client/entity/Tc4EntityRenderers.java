package github.com.gengyoubo.tcmodern.tc4.client.entity;

import github.com.gengyoubo.tcmodern.tc4.Tc4Modern;
import github.com.gengyoubo.tcmodern.tc4.init.Tc4Entities;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ZombieRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Tc4Modern.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class Tc4EntityRenderers {
    private Tc4EntityRenderers() { }
    @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(Tc4Entities.BRAINY_ZOMBIE.get(), BrainyRenderer::new);
    }
    private static final class BrainyRenderer extends ZombieRenderer {
        private static final ResourceLocation TEXTURE = new ResourceLocation("thaumcraft", "textures/models/bzombie.png");
        BrainyRenderer(EntityRendererProvider.Context context) { super(context); }
        @Override public ResourceLocation getTextureLocation(Zombie entity) { return TEXTURE; }
    }
}
