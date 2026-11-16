package github.com.gengyoubo.tcmodern.tc4.client.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import github.com.gengyoubo.tcmodern.tc4.entity.FireBat;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.util.Mth;

/** The original ModelFireBat: a bat with a tail flap below the body, hanging or flapping. */
final class FireBatModel extends EntityModel<FireBat> {
    private final ModelPart root, head, body, rightWing, leftWing, outerRightWing, outerLeftWing;

    static LayerDefinition create() {
        var mesh = new MeshDefinition();
        var root = mesh.getRoot();
        var head = root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-3, -3, -3, 6, 6, 6), PartPose.ZERO);
        head.addOrReplaceChild("right_ear", CubeListBuilder.create().texOffs(24, 0).addBox(-4, -6, -2, 3, 4, 1), PartPose.ZERO);
        head.addOrReplaceChild("left_ear", CubeListBuilder.create().texOffs(24, 0).mirror().addBox(1, -6, -2, 3, 4, 1), PartPose.ZERO);
        var body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 16).addBox(-3, 4, -3, 6, 12, 6).texOffs(0, 34).addBox(-5, 16, 0, 10, 6, 1), PartPose.ZERO);
        var rightWing = body.addOrReplaceChild("right_wing", CubeListBuilder.create().texOffs(42, 0).addBox(-12, 1, 1.5F, 10, 16, 1), PartPose.ZERO);
        rightWing.addOrReplaceChild("outer_right_wing", CubeListBuilder.create().texOffs(24, 16).addBox(-8, 1, 0, 8, 12, 1), PartPose.offset(-12, 1, 1.5F));
        var leftWing = body.addOrReplaceChild("left_wing", CubeListBuilder.create().texOffs(42, 0).mirror().addBox(2, 1, 1.5F, 10, 16, 1), PartPose.ZERO);
        leftWing.addOrReplaceChild("outer_left_wing", CubeListBuilder.create().texOffs(24, 16).mirror().addBox(0, 1, 0, 8, 12, 1), PartPose.offset(12, 1, 1.5F));
        return LayerDefinition.create(mesh, 64, 64);
    }

    FireBatModel() {
        root = create().bakeRoot();
        head = root.getChild("head"); body = root.getChild("body");
        rightWing = body.getChild("right_wing"); leftWing = body.getChild("left_wing");
        outerRightWing = rightWing.getChild("outer_right_wing"); outerLeftWing = leftWing.getChild("outer_left_wing");
    }

    @Override public void setupAnim(FireBat bat, float limbSwing, float limbAmount, float age, float headYaw, float headPitch) {
        if (bat.isHanging()) {
            head.xRot = headPitch * Mth.DEG_TO_RAD; head.yRot = Mth.PI - headYaw * Mth.DEG_TO_RAD; head.zRot = Mth.PI;
            head.setPos(0, -2, 0); rightWing.setPos(-3, 0, 3); leftWing.setPos(3, 0, 3);
            body.xRot = Mth.PI;
            rightWing.xRot = -Mth.PI / 20; rightWing.yRot = -Mth.PI * 2.0F / 5.0F; outerRightWing.yRot = -1.7278761F;
            leftWing.xRot = rightWing.xRot; leftWing.yRot = -rightWing.yRot; outerLeftWing.yRot = -outerRightWing.yRot;
        } else {
            head.xRot = headPitch * Mth.DEG_TO_RAD; head.yRot = headYaw * Mth.DEG_TO_RAD; head.zRot = 0;
            head.setPos(0, 0, 0); rightWing.setPos(0, 0, 0); leftWing.setPos(0, 0, 0);
            body.xRot = Mth.PI / 4 + Mth.cos(age * 0.1F) * 0.15F; body.yRot = 0;
            rightWing.yRot = Mth.cos(age * 1.3F) * Mth.PI * 0.25F; leftWing.yRot = -rightWing.yRot;
            outerRightWing.yRot = rightWing.yRot * 0.5F; outerLeftWing.yRot = -rightWing.yRot * 0.5F;
        }
    }

    @Override public void renderToBuffer(PoseStack pose, VertexConsumer vertices, int light, int overlay, float r, float g, float b, float a) {
        root.render(pose, vertices, light, overlay, r, g, b, a);
    }
}
