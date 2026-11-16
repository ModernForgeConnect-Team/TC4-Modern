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

import static github.com.gengyoubo.tcmodern.tc4.Tc4Modern.TC;

@Mod.EventBusSubscriber(modid = Tc4Modern.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class Tc4EntityRenderers {
    private Tc4EntityRenderers() { }
    @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(Tc4Entities.BRAINY_ZOMBIE.get(), BrainyRenderer::new);
        event.registerEntityRenderer(Tc4Entities.GIANT_BRAINY_ZOMBIE.get(), BrainyRenderer::new);
        event.registerEntityRenderer(Tc4Entities.TAINT_SPIDER.get(), TaintSpiderRenderer::new);
        event.registerEntityRenderer(Tc4Entities.INHABITED_ZOMBIE.get(), InhabitedRenderer::new);
        event.registerEntityRenderer(Tc4Entities.TAINT_COW.get(), TaintCowRenderer::new);
    }
    private static final class TaintSpiderRenderer extends net.minecraft.client.renderer.entity.SpiderRenderer<github.com.gengyoubo.tcmodern.tc4.entity.TaintSpider> {
        private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(TC, "textures/models/taint_spider.png");
        TaintSpiderRenderer(EntityRendererProvider.Context context) { super(context); }
        @Override public ResourceLocation getTextureLocation(github.com.gengyoubo.tcmodern.tc4.entity.TaintSpider entity) { return TEXTURE; }
        @Override protected void scale(github.com.gengyoubo.tcmodern.tc4.entity.TaintSpider entity, com.mojang.blaze3d.vertex.PoseStack stack, float partialTick) {
            stack.scale(0.4F, 0.4F, 0.4F);
        }
    }
    private static final class TaintCowRenderer extends net.minecraft.client.renderer.entity.MobRenderer<github.com.gengyoubo.tcmodern.tc4.entity.TaintCow, net.minecraft.client.model.CowModel<github.com.gengyoubo.tcmodern.tc4.entity.TaintCow>> {
        private static final ResourceLocation TEXTURE = new ResourceLocation("thaumcraft", "textures/models/cow.png");
        TaintCowRenderer(EntityRendererProvider.Context context) {
            super(context, new net.minecraft.client.model.CowModel<>(context.bakeLayer(net.minecraft.client.model.geom.ModelLayers.COW)), 0.7F);
        }
        @Override public ResourceLocation getTextureLocation(github.com.gengyoubo.tcmodern.tc4.entity.TaintCow entity) { return TEXTURE; }
    }
    private static final class InhabitedRenderer extends ZombieRenderer {
        private static final ResourceLocation TEXTURE = new ResourceLocation("thaumcraft", "textures/models/czombie.png");
        InhabitedRenderer(EntityRendererProvider.Context context) { super(context); }
        @Override public ResourceLocation getTextureLocation(Zombie entity) { return TEXTURE; }
    }
    private static final class BrainyRenderer extends ZombieRenderer {
        private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(TC, "textures/models/bzombie.png");
        BrainyRenderer(EntityRendererProvider.Context context) { super(context); }
        @Override protected void scale(Zombie entity, com.mojang.blaze3d.vertex.PoseStack stack, float partialTick) {
            if (entity instanceof github.com.gengyoubo.tcmodern.tc4.entity.GiantBrainyZombie giant) {
                float scale = giant.anger(); stack.scale(scale, scale, scale);
            }
            super.scale(entity, stack, partialTick);
        }
        @Override public ResourceLocation getTextureLocation(Zombie entity) { return TEXTURE; }
    }
}
