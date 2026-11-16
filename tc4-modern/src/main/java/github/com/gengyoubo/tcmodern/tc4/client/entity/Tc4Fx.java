package github.com.gengyoubo.tcmodern.tc4.client.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import org.joml.Matrix4f;

/** The two effects TC4 draws by hand: camera-facing sprites from its particle sheets, and bursts of light rays. */
final class Tc4Fx {
    static final ResourceLocation PARTICLES = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/misc/particles.png");
    static final ResourceLocation PARTICLES2 = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/misc/particles2.png");
    static final int FULL_BRIGHT = LightTexture.FULL_BRIGHT;
    private Tc4Fx() { }

    /** A unit quad centred on the origin facing the camera, scaled by {@code size}, with a cell of {@code texture}. */
    static void sprite(PoseStack pose, MultiBufferSource buffers, EntityRenderDispatcher dispatcher, ResourceLocation texture,
            float u0, float v0, float u1, float v1, float size, int argb, int light) {
        pose.pushPose();
        pose.mulPose(dispatcher.cameraOrientation());
        pose.mulPose(Axis.YP.rotationDegrees(180.0F));
        pose.scale(size, size, size);
        var buffer = buffers.getBuffer(RenderType.entityTranslucentEmissive(texture));
        var last = pose.last();
        int a = argb >>> 24, r = argb >> 16 & 255, g = argb >> 8 & 255, b = argb & 255;
        vertex(buffer, last, -0.5F, -0.5F, u0, v1, r, g, b, a, light);
        vertex(buffer, last, 0.5F, -0.5F, u1, v1, r, g, b, a, light);
        vertex(buffer, last, 0.5F, 0.5F, u1, v0, r, g, b, a, light);
        vertex(buffer, last, -0.5F, 0.5F, u0, v0, r, g, b, a, light);
        pose.popPose();
    }

    /** One cell of a 16 by 16 particle sheet. */
    static void cell(PoseStack pose, MultiBufferSource buffers, EntityRenderDispatcher dispatcher, ResourceLocation sheet, int column, int row, float size, int argb, int light) {
        sprite(pose, buffers, dispatcher, sheet, column / 16.0F, row / 16.0F, (column + 1) / 16.0F, (row + 1) / 16.0F, size, argb, light);
    }

    private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, float x, float y, float u, float v, int r, int g, int b, int a, int light) {
        buffer.vertex(pose.pose(), x, y, 0).color(r, g, b, a).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(pose.normal(), 0, 1, 0).endVertex();
    }

    /**
     * The radiating rays TC4 puts behind eldritch orbs, primal orbs and special items: {@code count} random spikes that
     * grow over the first ten ticks and turn with {@code spin}.
     */
    static void rays(PoseStack pose, MultiBufferSource buffers, long seed, int count, float age, float spin, float scale, java.util.function.IntUnaryOperator edgeColor) {
        var random = RandomSource.create(seed);
        float grow = Math.min(age, 10.0F) / 10.0F;
        var buffer = buffers.getBuffer(RenderType.lightning());
        pose.pushPose();
        pose.scale(scale, scale, scale);
        for (int i = 0; i < count; i++) {
            pose.mulPose(Axis.XP.rotationDegrees(random.nextFloat() * 360.0F));
            pose.mulPose(Axis.YP.rotationDegrees(random.nextFloat() * 360.0F));
            pose.mulPose(Axis.ZP.rotationDegrees(random.nextFloat() * 360.0F));
            pose.mulPose(Axis.XP.rotationDegrees(random.nextFloat() * 360.0F));
            pose.mulPose(Axis.YP.rotationDegrees(random.nextFloat() * 360.0F));
            pose.mulPose(Axis.ZP.rotationDegrees(random.nextFloat() * 360.0F + spin * 360.0F));
            float length = (random.nextFloat() * 20.0F + 5.0F) / 30.0F * grow, width = (random.nextFloat() * 2.0F + 1.0F) / 30.0F * grow;
            int edge = edgeColor.applyAsInt(i);
            Matrix4f m = pose.last().pose();
            // A triangle fan as quads: centre, two rim points, rim point repeated.
            triangle(buffer, m, edge, -0.866F * width, length, -0.5F * width, 0.866F * width, length, -0.5F * width);
            triangle(buffer, m, edge, 0.866F * width, length, -0.5F * width, 0.0F, length, width);
            triangle(buffer, m, edge, 0.0F, length, width, -0.866F * width, length, -0.5F * width);
        }
        pose.popPose();
    }

    private static void triangle(VertexConsumer buffer, Matrix4f m, int edge, float x1, float y1, float z1, float x2, float y2, float z2) {
        int r = edge >> 16 & 255, g = edge >> 8 & 255, b = edge & 255;
        buffer.vertex(m, 0, 0, 0).color(255, 255, 255, 255).endVertex();
        buffer.vertex(m, x1, y1, z1).color(r, g, b, 0).endVertex();
        buffer.vertex(m, x2, y2, z2).color(r, g, b, 0).endVertex();
        buffer.vertex(m, x2, y2, z2).color(r, g, b, 0).endVertex();
    }
}
