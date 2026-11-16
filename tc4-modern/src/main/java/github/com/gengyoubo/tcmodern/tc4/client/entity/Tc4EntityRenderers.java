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
        Tc4Entities.GOLEMS.values().forEach(type -> event.registerEntityRenderer(type.get(), ModernGolemRenderer::new));
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
        event.registerEntityRenderer(Tc4Entities.TAINT_TENTACLE.get(), context -> new TaintTentacleRenderer(context, 10, 1.0F));
        event.registerEntityRenderer(Tc4Entities.SMALL_TAINT_TENTACLE.get(), context -> new TaintTentacleRenderer(context, 6, 1.0F / 3.0F));
        event.registerEntityRenderer(Tc4Entities.GIANT_TAINT_TENTACLE.get(), context -> new TaintTentacleRenderer(context, 14, 2.66F));
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
        private final float modelScale;
        TaintTentacleRenderer(EntityRendererProvider.Context context, int length, float modelScale) { super(context, new TaintTentacleModel<>(length), 0.35F); this.modelScale = modelScale; }
        @Override public ResourceLocation getTextureLocation(github.com.gengyoubo.tcmodern.tc4.entity.TaintTentacle entity) { return TEXTURE; }
        @Override protected void scale(github.com.gengyoubo.tcmodern.tc4.entity.TaintTentacle entity, com.mojang.blaze3d.vertex.PoseStack stack, float partialTick) {
            stack.scale(modelScale, modelScale, modelScale);
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
    private static final class ModernGolemRenderer extends net.minecraft.client.renderer.entity.MobRenderer<github.com.gengyoubo.tcmodern.tc4.entity.golem.ModernGolem, ModernGolemModel> {
        private static final ResourceLocation FLOAT_TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/fishing_hook.png");
        ModernGolemRenderer(EntityRendererProvider.Context context) { super(context, new ModernGolemModel(), 0.3F); addLayer(new GolemCoreLayer(this, context.getItemRenderer())); addLayer(new GolemItemLayer(this, context.getItemRenderer())); addLayer(new GolemRodLayer(this, context.getItemRenderer())); }
        @Override public ResourceLocation getTextureLocation(github.com.gengyoubo.tcmodern.tc4.entity.golem.ModernGolem entity) {
            return ResourceLocation.fromNamespaceAndPath(TC, "textures/models/golem_" + entity.material().id() + ".png");
        }
        @Override public void render(github.com.gengyoubo.tcmodern.tc4.entity.golem.ModernGolem golem, float yaw, float partialTick, com.mojang.blaze3d.vertex.PoseStack pose, net.minecraft.client.renderer.MultiBufferSource buffers, int light) {
            super.render(golem, yaw, partialTick, pose, buffers, light);
            var water = golem.fishingPosition().orElse(null);
            if (water == null || golem.isInvisible()) return;
            double x = net.minecraft.util.Mth.lerp(partialTick, golem.xo, golem.getX());
            double y = net.minecraft.util.Mth.lerp(partialTick, golem.yo, golem.getY());
            double z = net.minecraft.util.Mth.lerp(partialTick, golem.zo, golem.getZ());
            float bx = (float)(water.getX() + 0.5 - x), by = (float)(water.getY() + 1.0 - y), bz = (float)(water.getZ() + 0.5 - z);
            pose.pushPose(); pose.translate(bx, by, bz); pose.scale(0.25F, 0.25F, 0.25F);
            pose.mulPose(entityRenderDispatcher.cameraOrientation()); pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180));
            var buffer = buffers.getBuffer(net.minecraft.client.renderer.RenderType.entityCutout(FLOAT_TEXTURE));
            floatVertex(buffer, pose.last(), -0.5F, -0.5F, 0, 1, light);
            floatVertex(buffer, pose.last(), 0.5F, -0.5F, 1, 1, light);
            floatVertex(buffer, pose.last(), 0.5F, 0.5F, 1, 0, light);
            floatVertex(buffer, pose.last(), -0.5F, 0.5F, 0, 0, light);
            pose.popPose();
            float angle = net.minecraft.util.Mth.lerp(partialTick, golem.yBodyRotO, golem.yBodyRot) * net.minecraft.util.Mth.DEG_TO_RAD;
            float sx = -0.28F * net.minecraft.util.Mth.cos(angle) - 0.30F * net.minecraft.util.Mth.sin(angle);
            float sz = -0.28F * net.minecraft.util.Mth.sin(angle) + 0.30F * net.minecraft.util.Mth.cos(angle);
            var line = buffers.getBuffer(net.minecraft.client.renderer.RenderType.lineStrip());
            var matrix = pose.last();
            for (int i = 0; i <= 16; i++) {
                float t = i / 16.0F;
                float lx = net.minecraft.util.Mth.lerp(t, sx, bx), ly = net.minecraft.util.Mth.lerp(t, 0.82F, by) - 0.15F * 4 * t * (1 - t), lz = net.minecraft.util.Mth.lerp(t, sz, bz);
                line.vertex(matrix.pose(), lx, ly, lz).color(25, 25, 25, 255).normal(matrix.normal(), 0, 1, 0).endVertex();
            }
        }
        private static void floatVertex(com.mojang.blaze3d.vertex.VertexConsumer buffer, com.mojang.blaze3d.vertex.PoseStack.Pose pose, float x, float y, float u, float v, int light) {
            buffer.vertex(pose.pose(), x, y, 0).color(255, 255, 255, 255).uv(u, v).overlayCoords(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).uv2(light).normal(pose.normal(), 0, 1, 0).endVertex();
        }
    }
    private static final class GolemRodLayer extends net.minecraft.client.renderer.entity.layers.RenderLayer<github.com.gengyoubo.tcmodern.tc4.entity.golem.ModernGolem, ModernGolemModel> {
        private final net.minecraft.client.renderer.entity.ItemRenderer items;
        private final net.minecraft.world.item.ItemStack rod = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.FISHING_ROD);
        GolemRodLayer(ModernGolemRenderer renderer, net.minecraft.client.renderer.entity.ItemRenderer items) { super(renderer); this.items = items; }
        @Override public void render(com.mojang.blaze3d.vertex.PoseStack pose, net.minecraft.client.renderer.MultiBufferSource buffers, int light, github.com.gengyoubo.tcmodern.tc4.entity.golem.ModernGolem golem, float limbSwing, float limbAmount, float partialTick, float age, float headYaw, float headPitch) {
            if (golem.fishingPosition().isEmpty() || golem.isInvisible()) return;
            pose.pushPose(); pose.translate(-0.27, 0.80, -0.17); pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(180));
            pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-30)); pose.scale(0.4F, 0.4F, 0.4F);
            items.renderStatic(golem, rod, net.minecraft.world.item.ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, false, pose, buffers, golem.level(), light, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, golem.getId());
            pose.popPose();
        }
    }
    private static final class GolemCoreLayer extends net.minecraft.client.renderer.entity.layers.RenderLayer<github.com.gengyoubo.tcmodern.tc4.entity.golem.ModernGolem, ModernGolemModel> {
        private final net.minecraft.client.renderer.entity.ItemRenderer items;
        private final java.util.Map<Integer, net.minecraft.world.item.ItemStack> cores = new java.util.HashMap<>();
        GolemCoreLayer(ModernGolemRenderer renderer, net.minecraft.client.renderer.entity.ItemRenderer items) { super(renderer); this.items = items; }
        @Override public void render(com.mojang.blaze3d.vertex.PoseStack pose, net.minecraft.client.renderer.MultiBufferSource buffers, int light, github.com.gengyoubo.tcmodern.tc4.entity.golem.ModernGolem golem, float limbSwing, float limbAmount, float partialTick, float age, float headYaw, float headPitch) {
            int core = golem.core();
            if (core < 0 || golem.isInvisible()) return;
            var stack = cores.get(core);
            if (stack == null) {
                var runtime = github.com.gengyoubo.tcmodern.api.TcModernApi.runtime().orElse(null);
                if (runtime == null) return;
                stack = runtime.mappings().toHost(github.com.gengyoubo.mfcl.api.LegacyMappings.ITEM_STACK,
                        runtime.stack(new github.com.gengyoubo.mfcl.api.LegacyStack("Thaumcraft:ItemGolemCore", core, 1, "")), net.minecraft.world.item.ItemStack.class)
                        .orElse(net.minecraft.world.item.ItemStack.EMPTY);
                if (stack.isEmpty()) return;
                cores.put(core, stack);
            }
            pose.pushPose();
            pose.translate(0.0875, 0.96, -0.155);
            pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(180));
            pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180));
            pose.scale(0.35F, 0.35F, 0.35F);
            items.renderStatic(golem, stack, net.minecraft.world.item.ItemDisplayContext.FIXED, false, pose, buffers, golem.level(), light, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, golem.getId());
            pose.popPose();
        }
    }
    private static final class GolemItemLayer extends net.minecraft.client.renderer.entity.layers.RenderLayer<github.com.gengyoubo.tcmodern.tc4.entity.golem.ModernGolem, ModernGolemModel> {
        private final net.minecraft.client.renderer.entity.ItemRenderer items;
        GolemItemLayer(ModernGolemRenderer renderer, net.minecraft.client.renderer.entity.ItemRenderer items) { super(renderer); this.items = items; }
        @Override public void render(com.mojang.blaze3d.vertex.PoseStack pose, net.minecraft.client.renderer.MultiBufferSource buffers, int light, github.com.gengyoubo.tcmodern.tc4.entity.golem.ModernGolem golem, float limbSwing, float limbAmount, float partialTick, float age, float headYaw, float headPitch) {
            var carried = golem.carriedForDisplay();
            if (carried.isEmpty() || golem.isInvisible()) return;
            pose.pushPose(); pose.translate(0, 0.65, -0.42); pose.scale(0.55F, 0.55F, 0.55F);
            items.renderStatic(golem, carried, net.minecraft.world.item.ItemDisplayContext.GROUND, false, pose, buffers, golem.level(), light, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, golem.getId());
            pose.popPose();
        }
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
        InhabitedRenderer(EntityRendererProvider.Context context) {
            super(context);
            layers.removeIf(layer -> layer instanceof net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer<?, ?, ?>);
            addLayer(new InhabitedArmorLayer(this, context));
        }
        @Override public ResourceLocation getTextureLocation(Zombie entity) { return TEXTURE; }
    }
    private static final class InhabitedArmorLayer extends net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer<Zombie, net.minecraft.client.model.ZombieModel<Zombie>, net.minecraft.client.model.ZombieModel<Zombie>> {
        private static final ResourceLocation HELMET = ResourceLocation.fromNamespaceAndPath(TC, "item_helmet_cultist_plate");
        private static final ResourceLocation HELMET_TEXTURE = ResourceLocation.fromNamespaceAndPath(TC, "textures/models/zombie_plate_armor.png");
        private final net.minecraft.client.model.HumanoidModel<Zombie> helmet;

        InhabitedArmorLayer(InhabitedRenderer renderer, EntityRendererProvider.Context context) {
            super(renderer, new net.minecraft.client.model.ZombieModel<>(context.bakeLayer(net.minecraft.client.model.geom.ModelLayers.ZOMBIE_INNER_ARMOR)),
                    new net.minecraft.client.model.ZombieModel<>(context.bakeLayer(net.minecraft.client.model.geom.ModelLayers.ZOMBIE_OUTER_ARMOR)), context.getModelManager());
            var mesh = net.minecraft.client.model.HumanoidModel.createMesh(net.minecraft.client.model.geom.builders.CubeDeformation.NONE, 0);
            mesh.getRoot().addOrReplaceChild("head", net.minecraft.client.model.geom.builders.CubeListBuilder.create().texOffs(41, 8)
                    .addBox(-4.5F, -9.0F, -4.5F, 9, 9, 9), net.minecraft.client.model.geom.PartPose.ZERO);
            mesh.getRoot().addOrReplaceChild("hat", net.minecraft.client.model.geom.builders.CubeListBuilder.create(), net.minecraft.client.model.geom.PartPose.ZERO);
            helmet = new net.minecraft.client.model.HumanoidModel<>(net.minecraft.client.model.geom.builders.LayerDefinition.create(mesh, 128, 64).bakeRoot());
        }
        private boolean isCultistHelmet(net.minecraft.world.item.ItemStack stack, net.minecraft.world.entity.EquipmentSlot slot) {
            return slot == net.minecraft.world.entity.EquipmentSlot.HEAD && HELMET.equals(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem()));
        }
        @Override protected net.minecraft.client.model.Model getArmorModelHook(Zombie entity, net.minecraft.world.item.ItemStack stack, net.minecraft.world.entity.EquipmentSlot slot, net.minecraft.client.model.ZombieModel<Zombie> original) {
            if (!isCultistHelmet(stack, slot)) return super.getArmorModelHook(entity, stack, slot, original);
            original.copyPropertiesTo(helmet);
            helmet.setAllVisible(false);
            helmet.head.visible = true;
            return helmet;
        }
        @Override public ResourceLocation getArmorResource(net.minecraft.world.entity.Entity entity, net.minecraft.world.item.ItemStack stack, net.minecraft.world.entity.EquipmentSlot slot, String type) {
            return isCultistHelmet(stack, slot) && type == null ? HELMET_TEXTURE : super.getArmorResource(entity, stack, slot, type);
        }
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
