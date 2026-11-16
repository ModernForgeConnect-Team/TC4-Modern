package github.com.gengyoubo.tcmodern.tc4.client.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import github.com.gengyoubo.tcmodern.tc4.Tc4Modern;
import github.com.gengyoubo.tcmodern.tc4.entity.*;
import github.com.gengyoubo.tcmodern.tc4.init.Tc4Entities;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.SlimeModel;
import net.minecraft.client.model.SpiderModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

/** Renderers for TC4's creatures and bosses, following the original Render classes and their textures. */
@Mod.EventBusSubscriber(modid = Tc4Modern.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class Tc4CreatureRenderers {
    private Tc4CreatureRenderers() { }

    static ResourceLocation tc(String path) { return ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/" + path + ".png"); }

    @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(Tc4Entities.TRAVELING_TRUNK.get(), TrunkRenderer::new);
        event.registerEntityRenderer(Tc4Entities.FIRE_BAT.get(), FireBatRenderer::new);
        event.registerEntityRenderer(Tc4Entities.ELDRITCH_GUARDIAN.get(), context -> new GuardianRenderer<>(context, tc("models/eldritch_guardian"), 1.0F));
        event.registerEntityRenderer(Tc4Entities.ELDRITCH_WARDEN.get(), context -> new GuardianRenderer<>(context, tc("models/eldritch_warden"), 1.5F));
        event.registerEntityRenderer(Tc4Entities.ELDRITCH_CRAB.get(), CrabRenderer::new);
        event.registerEntityRenderer(Tc4Entities.MIND_SPIDER.get(), MindSpiderRenderer::new);
        event.registerEntityRenderer(Tc4Entities.THAUMIC_SLIME.get(), SlimeRenderer::new);
        event.registerEntityRenderer(Tc4Entities.CULTIST_CLERIC.get(), context -> new CultistRenderer<>(context, 1.0F));
        event.registerEntityRenderer(Tc4Entities.CULTIST_KNIGHT.get(), context -> new CultistRenderer<>(context, 1.0F));
        event.registerEntityRenderer(Tc4Entities.CULTIST_LEADER.get(), context -> new CultistRenderer<>(context, 1.25F));
        event.registerEntityRenderer(Tc4Entities.WISP.get(), WispRenderer::new);
        event.registerEntityRenderer(Tc4Entities.PECH.get(), PechRenderer::new);
        event.registerEntityRenderer(Tc4Entities.CULTIST_PORTAL.get(), PortalRenderer::new);
        event.registerEntityRenderer(Tc4Entities.ELDRITCH_GOLEM.get(), EldritchGolemRenderer::new);
    }

    // --- Traveling trunk --------------------------------------------------------------------------------------------

    private static final class TrunkRenderer extends MobRenderer<TravelingTrunk, TrunkModel> {
        private static final ResourceLocation NORMAL = tc("models/trunk"), ANGRY = tc("models/trunkangry");
        TrunkRenderer(EntityRendererProvider.Context context) { super(context, new TrunkModel(), 0.5F); addLayer(new UpgradeLayer(this, context)); }
        @Override public ResourceLocation getTextureLocation(TravelingTrunk trunk) { return trunk.anger() > 0 ? ANGRY : NORMAL; }
        @Override protected void scale(TravelingTrunk trunk, PoseStack pose, float partialTick) {
            float squish = Mth.lerp(partialTick, trunk.previousSquish, trunk.squish) / 2.0F;
            float f2 = 1.0F / (squish + 1.0F) / 1.4F;
            float f3 = trunk.upgrade() == TravelingTrunk.EARTH ? 2.0F / 1.33F : 2.0F / 1.5F;
            pose.scale(f2 * f3, 0.5F / f2 * f3, f2 * f3);
            pose.translate(-0.5F, 0.5F, -0.5F);
        }
    }

    /** The fitted golem upgrade shows on the trunk's latch. */
    private static final class UpgradeLayer extends RenderLayer<TravelingTrunk, TrunkModel> {
        private final net.minecraft.client.renderer.entity.ItemRenderer items;
        private final ItemStack[] icons = new ItemStack[6];
        UpgradeLayer(TrunkRenderer parent, EntityRendererProvider.Context context) { super(parent); items = context.getItemRenderer(); }
        @Override public void render(PoseStack pose, MultiBufferSource buffers, int light, TravelingTrunk trunk, float limbSwing, float limbAmount,
                float partialTick, float age, float headYaw, float headPitch) {
            int upgrade = trunk.upgrade();
            if (upgrade < 0 || upgrade > 5 || trunk.isInvisible()) return;
            if (icons[upgrade] == null || icons[upgrade].isEmpty()) icons[upgrade] = Tc4Legacy.stack("Thaumcraft:ItemGolemUpgrade", upgrade, 1);
            if (icons[upgrade].isEmpty()) return;
            pose.pushPose();
            getParentModel().knob.translateAndRotate(pose);
            pose.translate(0.0F, 0.0F, -0.95F);
            pose.mulPose(Axis.XP.rotationDegrees(180.0F));
            pose.mulPose(Axis.YP.rotationDegrees(180.0F));
            pose.scale(0.15F, 0.15F, 0.15F);
            items.renderStatic(trunk, icons[upgrade], ItemDisplayContext.FIXED, false, pose, buffers, trunk.level(), light, OverlayTexture.NO_OVERLAY, trunk.getId());
            pose.popPose();
        }
    }

    // --- Firebat ----------------------------------------------------------------------------------------------------

    private static final class FireBatRenderer extends MobRenderer<FireBat, FireBatModel> {
        private static final ResourceLocation FIRE = tc("models/firebat"), VAMPIRE = tc("models/vampirebat");
        FireBatRenderer(EntityRendererProvider.Context context) { super(context, new FireBatModel(), 0.25F); }
        @Override public ResourceLocation getTextureLocation(FireBat bat) { return bat.isVampire() ? VAMPIRE : FIRE; }
        @Override protected int getBlockLightLevel(FireBat bat, BlockPos pos) { return 15; }
        @Override protected void scale(FireBat bat, PoseStack pose, float partialTick) {
            float scale = bat.isDevil() || bat.isVampire() ? 0.6F : 0.35F;
            pose.scale(scale, scale, scale);
        }
        @Override protected void setupRotations(FireBat bat, PoseStack pose, float age, float yaw, float partialTick) {
            pose.translate(0.0F, bat.isHanging() ? -0.1F : Mth.cos(age * 0.3F) * 0.1F, 0.0F);
            super.setupRotations(bat, pose, age, yaw, partialTick);
        }
    }

    // --- Eldritch guardian and warden -------------------------------------------------------------------------------

    private static final class GuardianRenderer<T extends Mob> extends MobRenderer<T, EldritchGuardianModel<T>> {
        private final ResourceLocation texture;
        private final float scale;
        GuardianRenderer(EntityRendererProvider.Context context, ResourceLocation texture, float scale) {
            super(context, new EldritchGuardianModel<>(), 0.5F * scale);
            this.texture = texture; this.scale = scale;
            if (scale > 1.0F) addLayer(new WardenEye<>(this));
        }
        @Override public ResourceLocation getTextureLocation(T entity) { return texture; }
        @Override protected void scale(T entity, PoseStack pose, float partialTick) { pose.scale(scale, scale, scale); }
        @Override @Nullable protected RenderType getRenderType(T entity, boolean visible, boolean translucent, boolean glowing) {
            return visible || translucent ? RenderType.entityTranslucent(texture) : super.getRenderType(entity, false, false, glowing);
        }
        /** A guardian outside the Outer Lands fades with distance; a rising warden comes up out of the ground. */
        @Override public void render(T entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
            float alpha = 1.0F;
            if (entity instanceof EldritchWarden warden) {
                pose.pushPose();
                pose.translate(0, -entity.getBbHeight() * (warden.spawnTimer() / 150.0F), 0);
            } else if (!Tc4Legacy.isOuterLands(entity.level())) {
                var camera = Minecraft.getInstance().getCameraEntity();
                if (camera != null) {
                    double near = 256.0, far = entity.level().getDifficulty() == Difficulty.HARD ? 576.0 : 1024.0, distance = entity.distanceToSqr(camera);
                    alpha = distance < near ? 0.6F : (float) (1.0 - Math.min(far - near, distance - near) / (far - near)) * 0.6F;
                }
            }
            model.alpha = alpha;
            super.render(entity, yaw, partialTick, pose, buffers, light);
            model.alpha = 1.0F;
            if (entity instanceof EldritchWarden) pose.popPose();
        }
    }

    /** The warden's hood eye glows, flickering. */
    private static final class WardenEye<T extends Mob> extends RenderLayer<T, EldritchGuardianModel<T>> {
        private static final ResourceLocation TEXTURE = tc("models/eldritch_warden");
        WardenEye(RenderLayerParent<T, EldritchGuardianModel<T>> parent) { super(parent); }
        @Override public void render(PoseStack pose, MultiBufferSource buffers, int light, T entity, float limbSwing, float limbAmount,
                float partialTick, float age, float headYaw, float headPitch) {
            var model = getParentModel();
            int glow = (int) (195.0F + Mth.sin(age / 3.0F) * 15.0F + 15.0F);
            model.eyeOnly = true;
            model.setupAnim(entity, limbSwing, limbAmount, age, headYaw, headPitch);
            pose.pushPose();
            pose.scale(1.01F, 1.01F, 1.01F);
            model.renderToBuffer(pose, buffers.getBuffer(RenderType.eyes(TEXTURE)), glow, OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
            pose.popPose();
            model.eyeOnly = false;
            model.setupAnim(entity, limbSwing, limbAmount, age, headYaw, headPitch);
        }
    }

    // --- Eldritch crab ----------------------------------------------------------------------------------------------

    private static final class CrabRenderer extends MobRenderer<EldritchCrab, EldritchCrabModel> {
        private static final ResourceLocation TEXTURE = tc("models/crab"), OVERLAY = tc("models/craboverlay");
        CrabRenderer(EntityRendererProvider.Context context) {
            super(context, new EldritchCrabModel(), 1.0F);
            addLayer(new RenderLayer<>(this) {
                @Override public void render(PoseStack pose, MultiBufferSource buffers, int light, EldritchCrab crab, float limbSwing, float limbAmount,
                        float partialTick, float age, float headYaw, float headPitch) {
                    if (crab.isInvisible()) return;
                    getParentModel().renderToBuffer(pose, buffers.getBuffer(RenderType.entityTranslucent(OVERLAY)), 200, LivingEntityRenderer.getOverlayCoords(crab, 0.0F), 1, 1, 1, 1);
                }
            });
        }
        @Override public ResourceLocation getTextureLocation(EldritchCrab crab) { return TEXTURE; }
    }

    // --- Mind spider ------------------------------------------------------------------------------------------------

    /** A spider model whose alpha the renderer sets: mind spiders are barely there. */
    private static final class GhostSpiderModel extends SpiderModel<MindSpider> {
        float alpha = 1.0F;
        GhostSpiderModel(net.minecraft.client.model.geom.ModelPart root) { super(root); }
        @Override public void renderToBuffer(PoseStack pose, com.mojang.blaze3d.vertex.VertexConsumer vertices, int light, int overlay, float r, float g, float b, float a) {
            super.renderToBuffer(pose, vertices, light, overlay, r, g, b, a * alpha);
        }
    }

    private static final class MindSpiderRenderer extends MobRenderer<MindSpider, GhostSpiderModel> {
        private static final ResourceLocation TEXTURE = tc("models/taint_spider"), EYES = tc("models/taint_spider_eyes");
        MindSpiderRenderer(EntityRendererProvider.Context context) {
            super(context, new GhostSpiderModel(context.bakeLayer(ModelLayers.SPIDER)), 0.0F);
            addLayer(new EyesLayer<>(this) { @Override public RenderType renderType() { return RenderType.eyes(EYES); } });
        }
        @Override public ResourceLocation getTextureLocation(MindSpider spider) { return TEXTURE; }
        @Override protected float getFlipDegrees(MindSpider spider) { return 180.0F; }
        @Override protected void scale(MindSpider spider, PoseStack pose, float partialTick) { pose.scale(0.3F, 0.3F, 0.3F); }
        @Override @Nullable protected RenderType getRenderType(MindSpider spider, boolean visible, boolean translucent, boolean glowing) {
            return RenderType.entityTranslucent(TEXTURE);
        }
        /** Only the haunted player sees a warp spider. */
        @Override public void render(MindSpider spider, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
            var player = Minecraft.getInstance().player;
            if (!spider.viewer().isEmpty() && (player == null || !spider.viewer().equals(player.getGameProfile().getName()))) return;
            model.alpha = Math.min(0.1F, (spider.tickCount + partialTick) / 100.0F);
            super.render(spider, yaw, partialTick, pose, buffers, light);
            model.alpha = 1.0F;
        }
    }

    // --- Thaumic slime ----------------------------------------------------------------------------------------------

    private static final class SlimeRenderer extends MobRenderer<ThaumicSlime, SlimeModel<ThaumicSlime>> {
        private static final ResourceLocation TEXTURE = tc("models/tslime");
        SlimeRenderer(EntityRendererProvider.Context context) {
            super(context, new SlimeModel<>(context.bakeLayer(ModelLayers.SLIME)), 0.25F);
            var outer = new SlimeModel<ThaumicSlime>(context.bakeLayer(ModelLayers.SLIME_OUTER));
            addLayer(new RenderLayer<>(this) {
                @Override public void render(PoseStack pose, MultiBufferSource buffers, int light, ThaumicSlime slime, float limbSwing, float limbAmount,
                        float partialTick, float age, float headYaw, float headPitch) {
                    if (slime.isInvisible()) return;
                    getParentModel().copyPropertiesTo(outer);
                    outer.prepareMobModel(slime, limbSwing, limbAmount, partialTick);
                    outer.setupAnim(slime, limbSwing, limbAmount, age, headYaw, headPitch);
                    outer.renderToBuffer(pose, buffers.getBuffer(RenderType.entityTranslucent(TEXTURE)), light, LivingEntityRenderer.getOverlayCoords(slime, 0.0F), 1, 1, 1, 1);
                }
            });
        }
        @Override public ResourceLocation getTextureLocation(ThaumicSlime slime) { return TEXTURE; }
        @Override protected void scale(ThaumicSlime slime, PoseStack pose, float partialTick) {
            float f1 = Mth.sqrt(slime.slimeSize());
            float f2 = Mth.lerp(partialTick, slime.previousSquish, slime.squish) / (f1 * 0.25F + 1.0F);
            float f3 = 1.0F / (f2 + 1.0F);
            pose.scale(f3 * f1 + 0.1F, 1.0F / f3 * f1 + 0.1F, f3 * f1 + 0.1F);
        }
    }

    // --- Cultists ---------------------------------------------------------------------------------------------------

    private static final class CultistRenderer<T extends Mob> extends HumanoidMobRenderer<T, HumanoidModel<T>> {
        private static final ResourceLocation SKIN = tc("models/cultist"), RITUAL = tc("misc/wispy");
        private final float scale;
        CultistRenderer(EntityRendererProvider.Context context, float scale) {
            super(context, new HumanoidModel<>(LayerDefinition.create(HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F), 64, 32).bakeRoot()), 0.5F * scale);
            this.scale = scale;
            addLayer(new HumanoidArmorLayer<>(this, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                    new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)), context.getModelManager()));
        }
        @Override public ResourceLocation getTextureLocation(T entity) { return SKIN; }
        @Override protected void scale(T entity, PoseStack pose, float partialTick) { pose.scale(scale, scale, scale); }

        @Override public void render(T entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
            boolean ritual = entity instanceof CultistCleric cleric && cleric.isRitualist() && cleric.altar().isPresent();
            float bob = 0.0F;
            pose.pushPose();
            if (ritual) {
                float c = entity.tickCount + partialTick + new java.util.Random(entity.getId()).nextInt(1000);
                bob = Mth.sin(c / 9.0F) * 0.1F + 0.21F;
                pose.translate(0, bob, 0);
            }
            super.render(entity, yaw, partialTick, pose, buffers, light);
            pose.popPose();
            if (ritual) ritualLine(entity, ((CultistCleric) entity).altar().get(), bob, partialTick, pose, buffers);
        }

        /** The red, wavering line from a chanting cleric's eyes to its altar. */
        private void ritualLine(T entity, BlockPos altar, float bob, float partialTick, PoseStack pose, MultiBufferSource buffers) {
            double x = Mth.lerp(partialTick, entity.xo, entity.getX()), y = Mth.lerp(partialTick, entity.yo, entity.getY()), z = Mth.lerp(partialTick, entity.zo, entity.getZ());
            float sx = 0, sy = entity.getEyeHeight() * 1.2F, sz = 0;
            float ex = (float) (altar.getX() + 0.5 - x), ey = (float) (altar.getY() + 1.5 - bob - y), ez = (float) (altar.getZ() + 0.5 - z);
            float reach = Math.min(entity.tickCount, 10) / 10.0F, time = (System.nanoTime() / 30000000L) % 32767L;
            var buffer = buffers.getBuffer(RenderType.entityTranslucentEmissive(RITUAL));
            var last = pose.last();
            int steps = 24;
            for (int pass = 0; pass < 2; pass++) for (int i = 0; i < steps * reach; i++) {
                float t0 = i / (float) steps, t1 = (i + 1) / (float) steps;
                float[] a = wave(sx, sy, sz, ex, ey, ez, t0, time), b = wave(sx, sy, sz, ex, ey, ez, t1, time);
                float wx = pass == 0 ? 0 : 0.25F, wy = pass == 0 ? 0.25F : 0;
                float u0 = t0 * 4 - time * 0.03F, u1 = t1 * 4 - time * 0.03F;
                quad(buffer, last, a, b, wx, wy, u0, u1);
            }
        }

        private static float[] wave(float sx, float sy, float sz, float ex, float ey, float ez, float t, float time) {
            float swell = 1.0F - Math.abs(t - 0.5F) * 2.0F;
            float x = Mth.lerp(t, sx, ex), y = Mth.lerp(t, sy, ey), z = Mth.lerp(t, sz, ez);
            float phase = t * 8 - time / 5.0F;
            return new float[] { x + Mth.sin(phase / 4.0F) * 0.5F * swell, y + Mth.sin(phase / 3.0F) * 0.5F * swell, z + Mth.sin(phase / 2.0F) * 0.5F * swell };
        }

        private static void quad(com.mojang.blaze3d.vertex.VertexConsumer buffer, PoseStack.Pose pose, float[] a, float[] b, float wx, float wy, float u0, float u1) {
            int r = 0x11, g = 0x00, bl = 0x11;
            vertex(buffer, pose, a[0] - wx, a[1] - wy, a[2], u0, 1, r, g, bl);
            vertex(buffer, pose, a[0] + wx, a[1] + wy, a[2], u0, 0, r, g, bl);
            vertex(buffer, pose, b[0] + wx, b[1] + wy, b[2], u1, 0, r, g, bl);
            vertex(buffer, pose, b[0] - wx, b[1] - wy, b[2], u1, 1, r, g, bl);
        }

        private static void vertex(com.mojang.blaze3d.vertex.VertexConsumer buffer, PoseStack.Pose pose, float x, float y, float z, float u, float v, int r, int g, int b) {
            buffer.vertex(pose.pose(), x, y, z).color(r * 15, g, b * 15, 204).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(Tc4Fx.FULL_BRIGHT).normal(pose.normal(), 0, 1, 0).endVertex();
        }
    }

    // --- Wisp -------------------------------------------------------------------------------------------------------

    private static final class WispRenderer extends EntityRenderer<Wisp> {
        private static final ResourceLocation TEXTURE = tc("misc/wisp");
        WispRenderer(EntityRendererProvider.Context context) { super(context); shadowRadius = 0.0F; }
        @Override public ResourceLocation getTextureLocation(Wisp wisp) { return TEXTURE; }
        @Override public void render(Wisp wisp, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
            if (wisp.getHealth() <= 0.0F) return;
            pose.pushPose();
            pose.translate(0, 0.45, 0);
            int frame = wisp.tickCount % 16, color = wisp.color();
            int argb = wisp.hurtTime > 0 ? 0xFF000000 | 255 << 16 | (int) ((color >> 8 & 255) / 300.0F * 255) << 8 | (int) ((color & 255) / 300.0F * 255)
                    : 0xFF000000 | color;
            float u0 = (frame % 4) / 4.0F, v0 = (frame / 4) / 4.0F;
            Tc4Fx.sprite(pose, buffers, entityRenderDispatcher, TEXTURE, u0, v0, u0 + 0.25F, v0 + 0.25F, 2.0F, argb, Tc4Fx.FULL_BRIGHT);
            float pulse = 0.4F + Mth.sin((wisp.tickCount + partialTick) / 10.0F) * 0.1F;
            Tc4Fx.cell(pose, buffers, entityRenderDispatcher, Tc4Fx.PARTICLES, wisp.tickCount % 16, 5, pulse * 2.0F, 0xFFFFFFFF, Tc4Fx.FULL_BRIGHT);
            pose.popPose();
        }
    }

    // --- Pech -------------------------------------------------------------------------------------------------------

    private static final class PechRenderer extends MobRenderer<Pech, PechModel> {
        private static final ResourceLocation[] SKINS = { tc("models/pech_forage"), tc("models/pech_thaum"), tc("models/pech_stalker") };
        PechRenderer(EntityRendererProvider.Context context) {
            super(context, new PechModel(), 0.5F);
            var items = context.getItemInHandRenderer();
            addLayer(new RenderLayer<>(this) {
                @Override public void render(PoseStack pose, MultiBufferSource buffers, int light, Pech pech, float limbSwing, float limbAmount,
                        float partialTick, float age, float headYaw, float headPitch) {
                    var held = pech.getMainHandItem();
                    if (held.isEmpty()) return;
                    pose.pushPose();
                    getParentModel().RightArm.translateAndRotate(pose);
                    pose.mulPose(Axis.XP.rotationDegrees(-90.0F));
                    pose.mulPose(Axis.YP.rotationDegrees(180.0F));
                    pose.translate(1.0F / 16.0F, 0.125F, -0.3375F);
                    items.renderItem(pech, held, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, false, pose, buffers, light);
                    pose.popPose();
                }
            });
        }
        @Override public ResourceLocation getTextureLocation(Pech pech) { return SKINS[Mth.clamp(pech.pechType(), 0, 2)]; }
    }

    // --- Crimson portal ---------------------------------------------------------------------------------------------

    private static final class PortalRenderer extends EntityRenderer<CultistPortal> {
        private static final ResourceLocation TEXTURE = tc("misc/cultist_portal");
        PortalRenderer(EntityRendererProvider.Context context) { super(context); shadowRadius = 0.1F; shadowStrength = 0.5F; }
        @Override public ResourceLocation getTextureLocation(CultistPortal portal) { return TEXTURE; }
        @Override public void render(CultistPortal portal, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
            float scaleY = 1.5F;
            float grow = Math.min(50.0F, portal.tickCount + partialTick);
            if (portal.hurtTime > 0) { double d = Math.sin(portal.hurtTime * 72 * Math.PI / 180.0); scaleY -= d / 4.0; grow += 6.0 * d; }
            if (portal.pulse > 0) { double d = Math.sin(portal.pulse * 36 * Math.PI / 180.0); scaleY += d / 4.0; grow += 12.0 * d; }
            float scale = grow / 50.0F * 1.3F;
            float m = (1.0F - portal.getHealth() / portal.getMaxHealth()) / 3.0F;
            float bob = Mth.sin(portal.tickCount / (5.0F - 12.0F * m)) * m + m, bob2 = Mth.sin(portal.tickCount / (6.0F - 15.0F * m)) * m + m;
            float alpha = Mth.clamp(1.0F - bob, 0.0F, 1.0F);
            scaleY -= bob / 4.0F;
            scale -= bob2 / 3.0F;
            int frame = 15 - (int) (System.nanoTime() / 50000000L % 16);
            pose.pushPose();
            pose.translate(0, portal.getBbHeight() / 2.0F, 0);
            pose.mulPose(entityRenderDispatcher.cameraOrientation());
            pose.mulPose(Axis.YP.rotationDegrees(180.0F));
            pose.scale(scale * 2.0F, scaleY * 2.0F, 1.0F);
            var buffer = buffers.getBuffer(RenderType.entityTranslucentEmissive(TEXTURE));
            var last = pose.last();
            float u0 = frame / 16.0F, u1 = u0 + 0.0625F;
            int a = (int) (alpha * 255);
            buffer.vertex(last.pose(), -0.5F, -0.5F, 0).color(255, 255, 255, a).uv(u1, 1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(Tc4Fx.FULL_BRIGHT).normal(last.normal(), 0, 0, -1).endVertex();
            buffer.vertex(last.pose(), 0.5F, -0.5F, 0).color(255, 255, 255, a).uv(u0, 1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(Tc4Fx.FULL_BRIGHT).normal(last.normal(), 0, 0, -1).endVertex();
            buffer.vertex(last.pose(), 0.5F, 0.5F, 0).color(255, 255, 255, a).uv(u0, 0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(Tc4Fx.FULL_BRIGHT).normal(last.normal(), 0, 0, -1).endVertex();
            buffer.vertex(last.pose(), -0.5F, 0.5F, 0).color(255, 255, 255, a).uv(u1, 0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(Tc4Fx.FULL_BRIGHT).normal(last.normal(), 0, 0, -1).endVertex();
            pose.popPose();
            super.render(portal, yaw, partialTick, pose, buffers, light);
        }
    }

    // --- Eldritch golem ---------------------------------------------------------------------------------------------

    private static final class EldritchGolemRenderer extends MobRenderer<EldritchGolem, EldritchGolemModel> {
        private static final ResourceLocation TEXTURE = tc("models/eldritch_golem");
        EldritchGolemRenderer(EntityRendererProvider.Context context) { super(context, new EldritchGolemModel(), 1.0F); }
        @Override public ResourceLocation getTextureLocation(EldritchGolem golem) { return TEXTURE; }
        @Override protected void scale(EldritchGolem golem, PoseStack pose, float partialTick) { pose.scale(2.15F, 2.15F, 2.15F); }
        @Override @Nullable protected RenderType getRenderType(EldritchGolem golem, boolean visible, boolean translucent, boolean glowing) {
            return visible ? RenderType.entityTranslucent(TEXTURE) : super.getRenderType(golem, false, translucent, glowing);
        }
    }
}
