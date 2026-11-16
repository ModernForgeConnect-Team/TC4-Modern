package github.com.gengyoubo.tcmodern.tc4.client.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import github.com.gengyoubo.tcmodern.tc4.entity.TaintTentacle;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.util.Mth;

/** Original TC4 texture coordinates and tapered, nested tentacle segments. */
final class TaintTentacleModel<T extends TaintTentacle> extends EntityModel<T> {
    private final ModelPart root;
    private final ModelPart[] segments;
    TaintTentacleModel(int length) {
        super(net.minecraft.client.renderer.RenderType::entityTranslucent);
        var mesh = new MeshDefinition();
        var current = mesh.getRoot().addOrReplaceChild("base", CubeListBuilder.create().texOffs(0, 0).addBox(-4, -4, -4, 8, 8, 8), PartPose.offset(0, 20, 0));
        for (int i = 0; i < length - 1; i++) {
            current = current.addOrReplaceChild("segment" + i, CubeListBuilder.create().texOffs(0, 16).addBox(-4, -4, -4, 8, 8, 8), PartPose.offset(0, -8, 0));
        }
        current.addOrReplaceChild("orb", CubeListBuilder.create().texOffs(0, 56).addBox(-2, -2, -2, 4, 4, 4), PartPose.offset(0, -8, 0));
        current.addOrReplaceChild("end", CubeListBuilder.create().texOffs(0, 32).addBox(-6, -6, -6, 12, 12, 12), PartPose.offset(0, -8, 0));
        root = LayerDefinition.create(mesh, 64, 64).bakeRoot();
        segments = new ModelPart[length - 1];
        ModelPart part = root.getChild("base");
        for (int i = 0; i < segments.length; i++) {
            part = part.getChild("segment" + i); part.xScale = part.yScale = part.zScale = 0.88F; segments[i] = part;
        }
        ModelPart orb = part.getChild("orb"); orb.xScale = orb.yScale = orb.zScale = 0.88F;
        ModelPart end = part.getChild("end"); end.xScale = end.yScale = end.zScale = 0.88F;
    }
    @Override public void setupAnim(T entity, float limbSwing, float limbAmount, float age, float yaw, float pitch) {
        for (int i = 0; i < segments.length; i++) {
            segments[i].xRot = 0.15F * Mth.sin(age * 0.1F - i * 0.5F);
            segments[i].zRot = 0.1F * Mth.sin(age * 0.15F - i * 0.5F);
        }
    }
    @Override public void renderToBuffer(PoseStack pose, VertexConsumer vertices, int light, int overlay, float red, float green, float blue, float alpha) { root.render(pose, vertices, light, overlay, red, green, blue, alpha); }
}
