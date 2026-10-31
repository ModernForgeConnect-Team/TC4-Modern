package github.com.gengyoubo.tcmodern.tc4.client.desk;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import github.com.gengyoubo.tcmodern.tc4.content.ResearchDeskBlock;
import github.com.gengyoubo.tcmodern.tc4.content.ResearchDeskBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.item.ItemDisplayContext;

/** The scribing tools stand at the front corner above the drawers; the working note lies open on the desk mat across the join. */
public final class ResearchDeskRenderer implements BlockEntityRenderer<ResearchDeskBlockEntity> {
    public ResearchDeskRenderer(BlockEntityRendererProvider.Context context) { }

    @Override public void render(ResearchDeskBlockEntity desk, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        var state = desk.getBlockState();
        if (!(state.getBlock() instanceof ResearchDeskBlock)) return;
        var items = Minecraft.getInstance().getItemRenderer();
        var facing = state.getValue(ResearchDeskBlock.FACING);
        pose.pushPose();
        // Into the north-facing model space the block models are drawn in.
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-facing.toYRot() + 180));
        pose.translate(-0.5, 0, -0.5);

        var tools = desk.tools();
        if (!tools.isEmpty()) {
            pose.pushPose();
            pose.translate(13.5 / 16.0, 15 / 16.0 + 0.12, 3.5 / 16.0);
            pose.scale(0.38F, 0.38F, 0.38F);
            items.renderStatic(tools, ItemDisplayContext.FIXED, light, overlay, pose, buffers, desk.getLevel(), (int) desk.getBlockPos().asLong());
            pose.popPose();
        }
        var note = desk.note();
        if (!note.isEmpty()) {
            pose.pushPose();
            pose.translate(0.5 / 16.0, 15.25 / 16.0 + 0.005, 8 / 16.0);
            pose.mulPose(Axis.XP.rotationDegrees(90));
            pose.mulPose(Axis.ZP.rotationDegrees(8));
            pose.scale(0.55F, 0.55F, 0.55F);
            items.renderStatic(note, ItemDisplayContext.FIXED, light, overlay, pose, buffers, desk.getLevel(), (int) desk.getBlockPos().asLong() + 1);
            pose.popPose();
        }
        pose.popPose();
    }
}
