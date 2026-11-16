package github.com.gengyoubo.tcmodern.tc4.client.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import github.com.gengyoubo.tcmodern.tc4.entity.golem.ModernGolem;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.util.Mth;

/** TC4's golem body geometry and 128 by 128 material texture layout. */
final class ModernGolemModel extends EntityModel<ModernGolem> {
    private final ModelPart root;
    ModernGolemModel() {
        var mesh = new MeshDefinition(); var body = mesh.getRoot();
        body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-4, -11, -5.5F, 8, 9, 8), PartPose.offset(0, 30, -2));
        body.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 40).addBox(-8, -2, -6, 16, 12, 11)
                .texOffs(0, 70).addBox(-4.5F, 10, -3, 9, 5, 6, new CubeDeformation(0.5F)), PartPose.offset(0, 30, 0));
        body.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(60, 21).addBox(-12, -2.5F, -3, 4, 25, 6), PartPose.offset(0, 30, 0));
        body.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(60, 21).mirror().addBox(8, -2.5F, -3, 4, 25, 6), PartPose.offset(0, 30, 0));
        body.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(37, 0).addBox(-3.5F, -3, -3, 6, 16, 5), PartPose.offset(-4, 48, 0));
        body.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(37, 0).mirror().addBox(-3.5F, -3, -3, 6, 16, 5), PartPose.offset(5, 48, 0));
        root = LayerDefinition.create(mesh, 128, 128).bakeRoot();
    }
    @Override public void setupAnim(ModernGolem entity, float limbSwing, float limbAmount, float age, float yaw, float pitch) {
        root.getChild("head").yRot = yaw * Mth.DEG_TO_RAD; root.getChild("head").xRot = pitch * Mth.DEG_TO_RAD;
        float walk = Mth.cos(limbSwing * 0.6662F) * limbAmount;
        root.getChild("left_leg").xRot = walk; root.getChild("right_leg").xRot = -walk;
        root.getChild("left_arm").xRot = -walk * 0.7F; root.getChild("right_arm").xRot = walk * 0.7F;
    }
    @Override public void renderToBuffer(PoseStack pose, VertexConsumer vertices, int light, int overlay, float r, float g, float b, float a) {
        pose.pushPose(); pose.scale(0.4F, 0.4F, 0.4F); root.render(pose, vertices, light, overlay, r, g, b, a); pose.popPose();
    }
}
