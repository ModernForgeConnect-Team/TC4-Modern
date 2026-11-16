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

/** Simple segmented modern approximation of the original animated tentacle. */
final class TaintTentacleModel<T extends TaintTentacle> extends EntityModel<T> {
    private final ModelPart root;
    TaintTentacleModel() {
        var mesh = new MeshDefinition();
        var rootPart = mesh.getRoot();
        var base = rootPart.addOrReplaceChild("base", CubeListBuilder.create().texOffs(0, 0).addBox(-4, -8, -4, 8, 8, 8), PartPose.offset(0, 24, 0));
        var middle = base.addOrReplaceChild("middle", CubeListBuilder.create().texOffs(0, 16).addBox(-3, -12, -3, 6, 12, 6), PartPose.offset(0, -8, 0));
        middle.addOrReplaceChild("tip", CubeListBuilder.create().texOffs(0, 34).addBox(-2, -12, -2, 4, 12, 4), PartPose.offset(0, -12, 0));
        root = LayerDefinition.create(mesh, 64, 64).bakeRoot();
    }
    @Override public void setupAnim(T entity, float limbSwing, float limbAmount, float age, float yaw, float pitch) {
        var base = root.getChild("base"); var middle = base.getChild("middle"); var tip = middle.getChild("tip");
        base.xRot = 0.08F * Mth.sin(age * 0.09F); middle.zRot = 0.18F * Mth.sin(age * 0.12F); tip.xRot = 0.22F * Mth.sin(age * 0.14F + 1);
    }
    @Override public void renderToBuffer(PoseStack pose, VertexConsumer vertices, int light, int overlay, float red, float green, float blue, float alpha) { root.render(pose, vertices, light, overlay, red, green, blue, alpha); }
}
