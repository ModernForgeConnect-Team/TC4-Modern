package github.com.gengyoubo.tcmodern.tc4.client.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import github.com.gengyoubo.tcmodern.tc4.Tc4Modern;
import github.com.gengyoubo.tcmodern.tc4.entity.Tc4Legacy;
import github.com.gengyoubo.tcmodern.tc4.entity.golem.GolemBobber;
import github.com.gengyoubo.tcmodern.tc4.entity.item.AspectOrb;
import github.com.gengyoubo.tcmodern.tc4.entity.item.SpecialItem;
import github.com.gengyoubo.tcmodern.tc4.entity.projectile.*;
import github.com.gengyoubo.tcmodern.tc4.init.Tc4Entities;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ArrowRenderer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** TC4's projectiles, orbs and special item drops: mostly sprites from its particle sheets and ray bursts. */
@Mod.EventBusSubscriber(modid = Tc4Modern.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class Tc4ProjectileRenderers {
    private Tc4ProjectileRenderers() { }
    private static final ResourceLocation ARROW = ResourceLocation.withDefaultNamespace("textures/entity/projectiles/arrow.png");
    /** BlockCustomOreItem.colors: the primal colours TC4 tints its rays with. */
    private static final int[] PRIMAL_COLORS = { 0xFFFFFF, 0xFFFF7E, 0xFF3C01, 0x0090FF, 0x00A000, 0xEECCFF, 0x555577 };

    @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(Tc4Entities.DART.get(), context -> new ArrowRenderer<>(context) {
            @Override public ResourceLocation getTextureLocation(Dart dart) { return ARROW; }
        });
        event.registerEntityRenderer(Tc4Entities.PRIMAL_ARROW.get(), PrimalArrowRenderer::new);
        event.registerEntityRenderer(Tc4Entities.FROST_SHARD.get(), FrostShardRenderer::new);
        event.registerEntityRenderer(Tc4Entities.EXPLOSIVE_ORB.get(), context -> new SpriteRenderer<ExplosiveOrb>(context) {
            @Override void draw(ExplosiveOrb orb, float partialTick, PoseStack pose, MultiBufferSource buffers) {
                Tc4Fx.cell(pose, buffers, entityRenderDispatcher, Tc4Fx.PARTICLES2, orb.tickCount % 4, 13, 0.7F, 0xCCFFFFFF, 0xDC);
            }
        });
        event.registerEntityRenderer(Tc4Entities.SHOCK_ORB.get(), context -> new ElectricOrbRenderer<>(context, false));
        event.registerEntityRenderer(Tc4Entities.GOLEM_ORB.get(), context -> new ElectricOrbRenderer<GolemOrb>(context, false) {
            @Override boolean red(GolemOrb orb) { return orb.red(); }
        });
        event.registerEntityRenderer(Tc4Entities.ELDRITCH_ORB.get(), context -> new SpriteRenderer<EldritchOrb>(context) {
            @Override void draw(EldritchOrb orb, float partialTick, PoseStack pose, MultiBufferSource buffers) {
                Tc4Fx.rays(pose, buffers, orb.getId(), 12, orb.tickCount + partialTick, (orb.tickCount + partialTick) / 80.0F, 1.0F, i -> PRIMAL_COLORS[5]);
                Tc4Fx.cell(pose, buffers, entityRenderDispatcher, Tc4Fx.PARTICLES, orb.tickCount % 13, 3, 1.0F, 0xFFFFFFFF, Tc4Fx.FULL_BRIGHT);
            }
        });
        event.registerEntityRenderer(Tc4Entities.PRIMAL_ORB.get(), context -> new SpriteRenderer<PrimalOrb>(context) {
            @Override void draw(PrimalOrb orb, float partialTick, PoseStack pose, MultiBufferSource buffers) {
                Tc4Fx.rays(pose, buffers, orb.getId(), 12, orb.tickCount + partialTick, (orb.tickCount + partialTick) / 80.0F, 1.0F, i -> PRIMAL_COLORS[i / 2 + 1]);
                Tc4Fx.cell(pose, buffers, entityRenderDispatcher, Tc4Fx.PARTICLES, orb.tickCount % 13, 2, 0.5F, 0xCCFFFFFF, Tc4Fx.FULL_BRIGHT);
            }
        });
        event.registerEntityRenderer(Tc4Entities.EMBER.get(), context -> new SpriteRenderer<Ember>(context) {
            @Override void draw(Ember ember, float partialTick, PoseStack pose, MultiBufferSource buffers) {
                float progress = ember.tickCount / (float) Math.max(1, ember.duration());
                Tc4Fx.cell(pose, buffers, entityRenderDispatcher, Tc4Fx.PARTICLES, Math.min(15, 7 + (int) (8.0F * progress)), 9, 0.25F + progress, 0xE6FFFFFF, 0xDC);
            }
        });
        event.registerEntityRenderer(Tc4Entities.BOTTLE_TAINT.get(), ThrownItemRenderer::new);
        event.registerEntityRenderer(Tc4Entities.PECH_BLAST.get(), NoopRenderer::new);
        event.registerEntityRenderer(Tc4Entities.ALUMENTUM.get(), NoopRenderer::new);
        event.registerEntityRenderer(Tc4Entities.ASPECT_ORB.get(), AspectOrbRenderer::new);
        event.registerEntityRenderer(Tc4Entities.SPECIAL_ITEM.get(), SpecialItemRenderer::new);
        event.registerEntityRenderer(Tc4Entities.PERMANENT_ITEM.get(), SpecialItemRenderer::new);
        event.registerEntityRenderer(Tc4Entities.FOLLOWING_ITEM.get(), PlainItemRenderer::new);
        event.registerEntityRenderer(Tc4Entities.ITEM_GRATE.get(), PlainItemRenderer::new);
        event.registerEntityRenderer(Tc4Entities.GOLEM_BOBBER.get(), BobberRenderer::new);
    }

    /** Base for projectiles drawn only as effects around their position. */
    private abstract static class SpriteRenderer<T extends Entity> extends EntityRenderer<T> {
        SpriteRenderer(EntityRendererProvider.Context context) { super(context); shadowRadius = 0.0F; }
        @Override public ResourceLocation getTextureLocation(T entity) { return Tc4Fx.PARTICLES; }
        abstract void draw(T entity, float partialTick, PoseStack pose, MultiBufferSource buffers);
        @Override public void render(T entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
            pose.pushPose();
            draw(entity, partialTick, pose, buffers);
            pose.popPose();
            super.render(entity, yaw, partialTick, pose, buffers, light);
        }
    }

    /** RenderElectricOrb: the shock focus orb and the golem orbs, a crackling sprite that swells and shrinks. */
    private static class ElectricOrbRenderer<T extends Entity> extends SpriteRenderer<T> {
        ElectricOrbRenderer(EntityRendererProvider.Context context, boolean ignored) { super(context); }
        boolean red(T entity) { return false; }
        @Override void draw(T orb, float partialTick, PoseStack pose, MultiBufferSource buffers) {
            float u0 = (1 + orb.tickCount % 6) / 8.0F, v0 = red(orb) ? 0.75F : 0.875F;
            float bob = Mth.sin(orb.tickCount / 5.0F) * 0.2F + 0.2F;
            Tc4Fx.sprite(pose, buffers, entityRenderDispatcher, Tc4Fx.PARTICLES, u0, v0, u0 + 0.125F, v0 + 0.125F, 1.0F + bob, 0xCCFFFFFF, 0xDC);
        }
    }

    private static final class PrimalArrowRenderer extends ArrowRenderer<PrimalArrow> {
        private static final ResourceLocation WISP = Tc4CreatureRenderers.tc("misc/wisp");
        PrimalArrowRenderer(EntityRendererProvider.Context context) { super(context); }
        @Override public ResourceLocation getTextureLocation(PrimalArrow arrow) { return ARROW; }
        @Override public void render(PrimalArrow arrow, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
            super.render(arrow, yaw, partialTick, pose, buffers, light);
            int frame = arrow.tickCount % 16;
            float u0 = (frame % 4) / 4.0F, v0 = (frame / 4) / 4.0F;
            Tc4Fx.sprite(pose, buffers, entityRenderDispatcher, WISP, u0, v0, u0 + 0.25F, v0 + 0.25F, 0.5F, 0xC0000000 | PRIMAL_COLORS[arrow.arrowType() + 1], Tc4Fx.FULL_BRIGHT);
        }
    }

    /** RenderFrostShard: a few tumbling chips of frost, as big as the shard's damage. */
    private static final class FrostShardRenderer extends EntityRenderer<FrostShard> {
        private static final ResourceLocation TEXTURE = Tc4CreatureRenderers.tc("blocks/frostshard");
        private final ModelPart chip;
        FrostShardRenderer(EntityRendererProvider.Context context) {
            super(context); shadowRadius = 0.0F;
            var mesh = new MeshDefinition();
            mesh.getRoot().addOrReplaceChild("chip", CubeListBuilder.create().texOffs(0, 0).addBox(-2, -2, -2, 4, 4, 4), PartPose.ZERO);
            chip = LayerDefinition.create(mesh, 16, 8).bakeRoot().getChild("chip");
        }
        @Override public ResourceLocation getTextureLocation(FrostShard shard) { return TEXTURE; }
        @Override public void render(FrostShard shard, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
            var random = RandomSource.create(shard.getId());
            var buffer = buffers.getBuffer(RenderType.entityTranslucent(TEXTURE));
            float size = 0.1F + shard.damage() * 0.1F;
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(Mth.lerp(partialTick, shard.yRotO, shard.getYRot()) - 90.0F));
            pose.mulPose(Axis.ZP.rotationDegrees(Mth.lerp(partialTick, shard.xRotO, shard.getXRot())));
            for (int i = 0; i < 3; i++) {
                pose.pushPose();
                pose.translate((random.nextFloat() - 0.5F) * size, (random.nextFloat() - 0.5F) * size, (random.nextFloat() - 0.5F) * size);
                pose.mulPose(Axis.XP.rotationDegrees(random.nextFloat() * 360.0F));
                pose.mulPose(Axis.YP.rotationDegrees(random.nextFloat() * 360.0F));
                float s = size * (0.6F + random.nextFloat() * 0.4F) * 4.0F;
                pose.scale(s, s, s);
                chip.render(pose, buffer, light, OverlayTexture.NO_OVERLAY, 1, 1, 1, 0.9F);
                pose.popPose();
            }
            pose.popPose();
            super.render(shard, yaw, partialTick, pose, buffers, light);
        }
    }

    private static final class AspectOrbRenderer extends EntityRenderer<AspectOrb> {
        AspectOrbRenderer(EntityRendererProvider.Context context) { super(context); shadowRadius = 0.1F; shadowStrength = 0.5F; }
        @Override public ResourceLocation getTextureLocation(AspectOrb orb) { return Tc4Fx.PARTICLES; }
        @Override public void render(AspectOrb orb, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
            var aspect = Tc4Legacy.aspect(orb.aspect());
            int color = aspect == null ? 0xFFFFFF : aspect.color();
            int frame = (int) (System.nanoTime() / 25000000L % 16L);
            float size = 0.1F + 0.3F * ((AspectOrb.MAX_AGE - orb.age()) / (float) AspectOrb.MAX_AGE);
            pose.pushPose();
            pose.translate(0, 0.25F * size, 0);
            Tc4Fx.cell(pose, buffers, entityRenderDispatcher, Tc4Fx.PARTICLES, frame, 8, size, 0x80000000 | color, Tc4Fx.FULL_BRIGHT);
            pose.popPose();
            super.render(orb, yaw, partialTick, pose, buffers, light);
        }
    }

    /** The ordinary item renderer for TC4's item entity subclasses. */
    private static class PlainItemRenderer<T extends ItemEntity> extends EntityRenderer<T> {
        private final ItemEntityRenderer items;
        PlainItemRenderer(EntityRendererProvider.Context context) { super(context); items = new ItemEntityRenderer(context); shadowRadius = 0.15F; shadowStrength = 0.75F; }
        @Override public ResourceLocation getTextureLocation(T item) { return items.getTextureLocation(item); }
        @Override public void render(T item, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
            items.render(item, yaw, partialTick, pose, buffers, light);
        }
    }

    /** RenderSpecialItem: magenta light rays turn slowly behind the floating item. */
    private static final class SpecialItemRenderer<T extends SpecialItem> extends PlainItemRenderer<T> {
        SpecialItemRenderer(EntityRendererProvider.Context context) { super(context); }
        @Override public void render(T item, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
            float age = item.getAge() + partialTick;
            pose.pushPose();
            pose.translate(0, Mth.sin(age / 10.0F + item.bobOffs) * 0.1F + 0.25F, 0);
            Tc4Fx.rays(pose, buffers, 245L, 10, age, age / 500.0F, 1.0F, i -> 0xFF00FF);
            pose.popPose();
            super.render(item, yaw, partialTick, pose, buffers, light);
        }
    }

    /** The modern fishing golem's float, with its line back to the golem's rod. */
    private static final class BobberRenderer extends EntityRenderer<GolemBobber> {
        private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/fishing_hook.png");
        BobberRenderer(EntityRendererProvider.Context context) { super(context); }
        @Override public ResourceLocation getTextureLocation(GolemBobber bobber) { return TEXTURE; }
        @Override public void render(GolemBobber bobber, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
            pose.pushPose();
            pose.translate(0, 0.125, 0);
            Tc4Fx.sprite(pose, buffers, entityRenderDispatcher, TEXTURE, 0, 0, 1, 1, 0.25F, 0xFFFFFFFF, light);
            pose.popPose();
            var golem = bobber.fisher();
            if (golem != null) {
                float body = Mth.lerp(partialTick, golem.yBodyRotO, golem.yBodyRot) * Mth.DEG_TO_RAD;
                double hx = Mth.lerp(partialTick, golem.xo, golem.getX()) - 0.28 * Mth.cos(body) - 0.30 * Mth.sin(body);
                double hy = Mth.lerp(partialTick, golem.yo, golem.getY()) + 0.82;
                double hz = Mth.lerp(partialTick, golem.zo, golem.getZ()) - 0.28 * Mth.sin(body) + 0.30 * Mth.cos(body);
                double bx = Mth.lerp(partialTick, bobber.xo, bobber.getX()), by = Mth.lerp(partialTick, bobber.yo, bobber.getY()) + 0.25, bz = Mth.lerp(partialTick, bobber.zo, bobber.getZ());
                float dx = (float) (hx - bx), dy = (float) (hy - by), dz = (float) (hz - bz);
                var line = buffers.getBuffer(RenderType.lineStrip());
                var matrix = pose.last();
                for (int i = 0; i <= 16; i++) {
                    float t = i / 16.0F;
                    line.vertex(matrix.pose(), dx * t, dy * (t * t + t) * 0.5F + 0.25F - 0.25F, dz * t).color(25, 25, 25, 255).normal(matrix.normal(), 0, 1, 0).endVertex();
                }
            }
            super.render(bobber, yaw, partialTick, pose, buffers, light);
        }
    }
}
