package github.com.gengyoubo.tcmodern.tc4.client.crucible;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import github.com.gengyoubo.tcmodern.api.TcModernApi;
import github.com.gengyoubo.tcmodern.api.aspect.TcAspects;
import github.com.gengyoubo.tcmodern.tc4.content.IntermediateCrucibleBlockEntity;
import github.com.gengyoubo.tcmodern.tc4.crucible.CrucibleRules;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;

/** The liquid of the intermediate crucible, tinted by its essentia as the original, and the materials held on it. */
public final class IntermediateCrucibleRenderer implements BlockEntityRenderer<IntermediateCrucibleBlockEntity> {
    private static final ResourceLocation WATER = new ResourceLocation("block/water_still");
    private static final float MIN = 2 / 16F, MAX = 14 / 16F;
    private static Map<String, Integer> colors;

    public IntermediateCrucibleRenderer(BlockEntityRendererProvider.Context context) { }

    @Override public void render(IntermediateCrucibleBlockEntity crucible, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        var essentia = crucible.essentia();
        int total = 0;
        for (int amount : essentia.values()) total += amount;
        if (crucible.water() <= 0 && total <= 0) return;
        float height = CrucibleRules.liquidHeight(crucible.water(), total);
        int color = blend(crucible.getLevel() == null ? 0x3F76E4 : BiomeColors.getAverageWaterColor(crucible.getLevel(), crucible.getBlockPos()),
                essentia, total);
        var sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(WATER);
        var consumer = buffers.getBuffer(Sheets.translucentCullBlockSheet());
        var matrix = pose.last().pose();
        var normal = pose.last().normal();
        int r = color >> 16 & 0xFF, g = color >> 8 & 0xFF, b = color & 0xFF;
        float u0 = sprite.getU(2), u1 = sprite.getU(14), v0 = sprite.getV(2), v1 = sprite.getV(14);
        consumer.vertex(matrix, MIN, height, MIN).color(r, g, b, 220).uv(u0, v0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(normal, 0, 1, 0).endVertex();
        consumer.vertex(matrix, MIN, height, MAX).color(r, g, b, 220).uv(u0, v1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(normal, 0, 1, 0).endVertex();
        consumer.vertex(matrix, MAX, height, MAX).color(r, g, b, 220).uv(u1, v1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(normal, 0, 1, 0).endVertex();
        consumer.vertex(matrix, MAX, height, MIN).color(r, g, b, 220).uv(u1, v0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(normal, 0, 1, 0).endVertex();

        var materials = crucible.materials();
        long time = crucible.getLevel() == null ? 0 : crucible.getLevel().getGameTime();
        for (int i = 0; i < materials.size(); i++) {
            pose.pushPose();
            float offset = materials.size() == 1 ? 0 : (i == 0 ? -0.17F : 0.17F);
            pose.translate(0.5 + offset, height + 0.08 + Math.sin((time + partialTick) / 10.0 + i * 3) * 0.02, 0.5);
            pose.mulPose(Axis.YP.rotationDegrees((time + partialTick) * 2 + i * 90));
            pose.scale(0.35F, 0.35F, 0.35F);
            Minecraft.getInstance().getItemRenderer().renderStatic(materials.get(i), ItemDisplayContext.FIXED, light, overlay, pose, buffers,
                    crucible.getLevel(), (int) crucible.getBlockPos().asLong() + i);
            pose.popPose();
        }
    }

    /** Water colour mixed toward the essentia's average colour, fully once 100 essentia are dissolved. */
    private static int blend(int water, Map<String, Integer> essentia, int total) {
        if (total <= 0) return water;
        var known = colors();
        double r = 0, g = 0, b = 0;
        int counted = 0;
        for (var entry : essentia.entrySet()) {
            Integer color = known.get(entry.getKey());
            if (color == null) continue;
            r += (color >> 16 & 0xFF) * entry.getValue();
            g += (color >> 8 & 0xFF) * entry.getValue();
            b += (color & 0xFF) * entry.getValue();
            counted += entry.getValue();
        }
        if (counted == 0) return water;
        float mix = Math.min(1F, total / 100F);
        int er = (int) (r / counted), eg = (int) (g / counted), eb = (int) (b / counted);
        int wr = water >> 16 & 0xFF, wg = water >> 8 & 0xFF, wb = water & 0xFF;
        return (int) (wr + (er - wr) * mix) << 16 | (int) (wg + (eg - wg) * mix) << 8 | (int) (wb + (eb - wb) * mix);
    }

    private static Map<String, Integer> colors() {
        if (colors == null || colors.isEmpty()) {
            var result = new HashMap<String, Integer>();
            TcModernApi.runtime().ifPresent(runtime -> TcAspects.snapshot(runtime).forEach(aspect -> result.put(aspect.tag(), aspect.color())));
            colors = result;
        }
        return colors;
    }
}
