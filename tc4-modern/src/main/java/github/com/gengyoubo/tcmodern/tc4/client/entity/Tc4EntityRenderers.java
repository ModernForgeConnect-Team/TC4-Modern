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
        event.registerEntityRenderer(Tc4Entities.TAINT_PIG.get(), TaintPigRenderer::new);
        event.registerEntityRenderer(Tc4Entities.TAINT_CHICKEN.get(), TaintChickenRenderer::new);
        event.registerEntityRenderer(Tc4Entities.TAINT_SHEEP.get(), TaintSheepRenderer::new);
        event.registerEntityRenderer(Tc4Entities.TAINT_VILLAGER.get(), TaintVillagerRenderer::new);
        event.registerEntityRenderer(Tc4Entities.TAINT_CREEPER.get(), TaintCreeperRenderer::new);
        event.registerEntityRenderer(Tc4Entities.TAINT_SPORE.get(), TaintSporeRenderer::new);
        event.registerEntityRenderer(Tc4Entities.TAINT_SPORE_NEST.get(), TaintSporeNestRenderer::new);
        event.registerEntityRenderer(Tc4Entities.TAINT_SWARM.get(), net.minecraft.client.renderer.entity.NoopRenderer::new);
        event.registerEntityRenderer(Tc4Entities.TAINT_TENTACLE.get(), TaintTentacleRenderer::new);
        event.registerEntityRenderer(Tc4Entities.SMALL_TAINT_TENTACLE.get(), TaintTentacleRenderer::new);
        event.registerEntityRenderer(Tc4Entities.GIANT_TAINT_TENTACLE.get(), TaintTentacleRenderer::new);
        event.registerEntityRenderer(Tc4Entities.FALLING_TAINT.get(), FallingTaintRenderer::new);
    }
    private static final class TaintSpiderRenderer extends net.minecraft.client.renderer.entity.SpiderRenderer<github.com.gengyoubo.tcmodern.tc4.entity.TaintSpider> {
        private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(TC, "textures/models/taint_spider.png");
        TaintSpiderRenderer(EntityRendererProvider.Context context) { super(context); }
        @Override public ResourceLocation getTextureLocation(github.com.gengyoubo.tcmodern.tc4.entity.TaintSpider entity) { return TEXTURE; }
        @Override protected void scale(github.com.gengyoubo.tcmodern.tc4.entity.TaintSpider entity, com.mojang.blaze3d.vertex.PoseStack stack, float partialTick) {
            stack.scale(0.4F, 0.4F, 0.4F);
        }
    }
    private static final class TaintPigRenderer extends net.minecraft.client.renderer.entity.MobRenderer<github.com.gengyoubo.tcmodern.tc4.entity.TaintPig, net.minecraft.client.model.PigModel<github.com.gengyoubo.tcmodern.tc4.entity.TaintPig>> {
        private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(TC, "textures/models/pig.png");
        TaintPigRenderer(EntityRendererProvider.Context context) { super(context, new net.minecraft.client.model.PigModel<>(context.bakeLayer(net.minecraft.client.model.geom.ModelLayers.PIG)), 0.7F); }
        @Override public ResourceLocation getTextureLocation(github.com.gengyoubo.tcmodern.tc4.entity.TaintPig entity) { return TEXTURE; }
    }
    private static final class TaintChickenRenderer extends net.minecraft.client.renderer.entity.MobRenderer<github.com.gengyoubo.tcmodern.tc4.entity.TaintChicken, net.minecraft.client.model.ChickenModel<github.com.gengyoubo.tcmodern.tc4.entity.TaintChicken>> {
        private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(TC, "textures/models/chicken.png");
        TaintChickenRenderer(EntityRendererProvider.Context context) { super(context, new net.minecraft.client.model.ChickenModel<>(context.bakeLayer(net.minecraft.client.model.geom.ModelLayers.CHICKEN)), 0.3F); }
        @Override public ResourceLocation getTextureLocation(github.com.gengyoubo.tcmodern.tc4.entity.TaintChicken entity) { return TEXTURE; }
        @Override protected float getBob(github.com.gengyoubo.tcmodern.tc4.entity.TaintChicken entity, float partialTick) {
            float flap = net.minecraft.util.Mth.lerp(partialTick, entity.previousFlap, entity.flap);
            float amount = net.minecraft.util.Mth.lerp(partialTick, entity.previousFlapAmount, entity.flapAmount);
            return (net.minecraft.util.Mth.sin(flap) + 1.0F) * amount;
        }
    }
    private static final class TaintSheepRenderer extends net.minecraft.client.renderer.entity.MobRenderer<github.com.gengyoubo.tcmodern.tc4.entity.TaintSheep, net.minecraft.client.model.CowModel<github.com.gengyoubo.tcmodern.tc4.entity.TaintSheep>> {
        private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(TC, "textures/models/sheep.png");
        TaintSheepRenderer(EntityRendererProvider.Context context) {
            super(context, new net.minecraft.client.model.CowModel<>(context.bakeLayer(net.minecraft.client.model.geom.ModelLayers.SHEEP)), 0.7F);
            addLayer(new TaintSheepFur(this, context));
        }
        @Override public ResourceLocation getTextureLocation(github.com.gengyoubo.tcmodern.tc4.entity.TaintSheep entity) { return TEXTURE; }
    }
    private static final class TaintSheepFur extends net.minecraft.client.renderer.entity.layers.RenderLayer<github.com.gengyoubo.tcmodern.tc4.entity.TaintSheep, net.minecraft.client.model.CowModel<github.com.gengyoubo.tcmodern.tc4.entity.TaintSheep>> {
        private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(TC, "textures/models/sheep_fur.png");
        private final net.minecraft.client.model.CowModel<github.com.gengyoubo.tcmodern.tc4.entity.TaintSheep> model;
        TaintSheepFur(TaintSheepRenderer renderer, EntityRendererProvider.Context context) { super(renderer); model = new net.minecraft.client.model.CowModel<>(context.bakeLayer(net.minecraft.client.model.geom.ModelLayers.SHEEP_FUR)); }
        @Override public void render(com.mojang.blaze3d.vertex.PoseStack stack, net.minecraft.client.renderer.MultiBufferSource buffers, int light, github.com.gengyoubo.tcmodern.tc4.entity.TaintSheep sheep, float limbSwing, float limbAmount, float partialTick, float age, float headYaw, float headPitch) {
            if (sheep.isSheared() || sheep.isInvisible()) return;
            coloredCutoutModelCopyLayerRender(getParentModel(), model, TEXTURE, stack, buffers, light, sheep, limbSwing, limbAmount, age, headYaw, headPitch, partialTick, 1.0F, 1.0F, 1.0F);
        }
    }
    private static final class TaintVillagerRenderer extends net.minecraft.client.renderer.entity.MobRenderer<github.com.gengyoubo.tcmodern.tc4.entity.TaintVillager, net.minecraft.client.model.VillagerModel<github.com.gengyoubo.tcmodern.tc4.entity.TaintVillager>> {
        private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(TC, "textures/models/villager.png");
        TaintVillagerRenderer(EntityRendererProvider.Context context) { super(context, new net.minecraft.client.model.VillagerModel<>(context.bakeLayer(net.minecraft.client.model.geom.ModelLayers.VILLAGER)), 0.5F); }
        @Override public ResourceLocation getTextureLocation(github.com.gengyoubo.tcmodern.tc4.entity.TaintVillager entity) { return TEXTURE; }
    }
    private static final class TaintCreeperRenderer extends net.minecraft.client.renderer.entity.MobRenderer<github.com.gengyoubo.tcmodern.tc4.entity.TaintCreeper, net.minecraft.client.model.CreeperModel<github.com.gengyoubo.tcmodern.tc4.entity.TaintCreeper>> {
        private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(TC, "textures/models/creeper.png");
        TaintCreeperRenderer(EntityRendererProvider.Context context) { super(context, new net.minecraft.client.model.CreeperModel<>(context.bakeLayer(net.minecraft.client.model.geom.ModelLayers.CREEPER)), 0.5F); }
        @Override public ResourceLocation getTextureLocation(github.com.gengyoubo.tcmodern.tc4.entity.TaintCreeper entity) { return TEXTURE; }
        @Override protected void scale(github.com.gengyoubo.tcmodern.tc4.entity.TaintCreeper entity, com.mojang.blaze3d.vertex.PoseStack stack, float partialTick) {
            float f = entity.swelling(partialTick); float pulse = 1.0F + net.minecraft.util.Mth.sin(f * 100.0F) * f * 0.01F;
            f = net.minecraft.util.Mth.clamp(f, 0, 1); f *= f; f *= f;
            float horizontal = (1.0F + f * 0.4F) * pulse; float vertical = (1.0F + f * 0.1F) / pulse;
            stack.scale(horizontal, vertical, horizontal);
        }
    }
    private static final class TaintSporeRenderer extends net.minecraft.client.renderer.entity.MobRenderer<github.com.gengyoubo.tcmodern.tc4.entity.TaintSpore, net.minecraft.client.model.SlimeModel<github.com.gengyoubo.tcmodern.tc4.entity.TaintSpore>> {
        private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(TC, "textures/models/taint_spore.png");
        TaintSporeRenderer(EntityRendererProvider.Context context) { super(context, new net.minecraft.client.model.SlimeModel<>(context.bakeLayer(net.minecraft.client.model.geom.ModelLayers.SLIME_OUTER)), 0.25F); }
        @Override public ResourceLocation getTextureLocation(github.com.gengyoubo.tcmodern.tc4.entity.TaintSpore entity) { return TEXTURE; }
        @Override protected void scale(github.com.gengyoubo.tcmodern.tc4.entity.TaintSpore entity, com.mojang.blaze3d.vertex.PoseStack stack, float partialTick) { float scale = 0.24F * entity.sporeSize(); stack.scale(scale, scale, scale); }
    }
    private static final class TaintSporeNestRenderer extends net.minecraft.client.renderer.entity.MobRenderer<github.com.gengyoubo.tcmodern.tc4.entity.TaintSporeNest, net.minecraft.client.model.SlimeModel<github.com.gengyoubo.tcmodern.tc4.entity.TaintSporeNest>> {
        private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(TC, "textures/models/taint_spore.png");
        TaintSporeNestRenderer(EntityRendererProvider.Context context) { super(context, new net.minecraft.client.model.SlimeModel<>(context.bakeLayer(net.minecraft.client.model.geom.ModelLayers.SLIME_OUTER)), 0.25F); }
        @Override public ResourceLocation getTextureLocation(github.com.gengyoubo.tcmodern.tc4.entity.TaintSporeNest entity) { return TEXTURE; }
        @Override protected void scale(github.com.gengyoubo.tcmodern.tc4.entity.TaintSporeNest entity, com.mojang.blaze3d.vertex.PoseStack stack, float partialTick) { stack.scale(1.6F, 1.6F, 1.6F); }
    }
    private static final class TaintTentacleRenderer extends net.minecraft.client.renderer.entity.MobRenderer<github.com.gengyoubo.tcmodern.tc4.entity.TaintTentacle, TaintTentacleModel<github.com.gengyoubo.tcmodern.tc4.entity.TaintTentacle>> {
        private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(TC, "textures/models/taintacle.png");
        TaintTentacleRenderer(EntityRendererProvider.Context context) { super(context, new TaintTentacleModel<>(), 0.35F); }
        @Override public ResourceLocation getTextureLocation(github.com.gengyoubo.tcmodern.tc4.entity.TaintTentacle entity) { return TEXTURE; }
        @Override protected void scale(github.com.gengyoubo.tcmodern.tc4.entity.TaintTentacle entity, com.mojang.blaze3d.vertex.PoseStack stack, float partialTick) {
            float scale = entity instanceof github.com.gengyoubo.tcmodern.tc4.entity.SmallTaintTentacle ? 0.5F : entity instanceof github.com.gengyoubo.tcmodern.tc4.entity.GiantTaintTentacle ? 3.0F : 1.5F;
            stack.scale(scale, scale, scale);
        }
    }
    private static final class FallingTaintRenderer extends net.minecraft.client.renderer.entity.EntityRenderer<github.com.gengyoubo.tcmodern.tc4.entity.FallingTaint> {
        private final net.minecraft.client.renderer.block.BlockRenderDispatcher blocks;
        FallingTaintRenderer(EntityRendererProvider.Context context) { super(context); blocks = context.getBlockRenderDispatcher(); shadowRadius = 0.5F; }
        @Override public void render(github.com.gengyoubo.tcmodern.tc4.entity.FallingTaint entity, float yaw, float partialTick, com.mojang.blaze3d.vertex.PoseStack pose, net.minecraft.client.renderer.MultiBufferSource buffers, int light) {
            if (!entity.blockState().isAir()) {
                pose.pushPose(); pose.translate(-0.5, 0, -0.5);
                blocks.renderSingleBlock(entity.blockState(), pose, buffers, light, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
                pose.popPose();
            }
            super.render(entity, yaw, partialTick, pose, buffers, light);
        }
        @Override public ResourceLocation getTextureLocation(github.com.gengyoubo.tcmodern.tc4.entity.FallingTaint entity) { return net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS; }
    }
    private static final class TaintCowRenderer extends net.minecraft.client.renderer.entity.MobRenderer<github.com.gengyoubo.tcmodern.tc4.entity.TaintCow, net.minecraft.client.model.CowModel<github.com.gengyoubo.tcmodern.tc4.entity.TaintCow>> {
        private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/models/cow.png");
        TaintCowRenderer(EntityRendererProvider.Context context) {
            super(context, new net.minecraft.client.model.CowModel<>(context.bakeLayer(net.minecraft.client.model.geom.ModelLayers.COW)), 0.7F);
        }
        @Override public ResourceLocation getTextureLocation(github.com.gengyoubo.tcmodern.tc4.entity.TaintCow entity) { return TEXTURE; }
    }
    private static final class InhabitedRenderer extends ZombieRenderer {
        private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/models/czombie.png");
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
