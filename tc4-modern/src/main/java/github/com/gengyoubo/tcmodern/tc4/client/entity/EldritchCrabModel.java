package github.com.gengyoubo.tcmodern.tc4.client.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.util.Mth;

/** The eldritch crab; the shell-covered tail shows while it wears its chestplate. Geometry and texture layout of the original ModelEldritchCrab. */
final class EldritchCrabModel extends EntityModel<github.com.gengyoubo.tcmodern.tc4.entity.EldritchCrab> {
    final ModelPart root;
    final ModelPart TailHelm, TailBare, RClaw1, Head1, RClaw0, RClaw2, RArm, LClaw2, LClaw1, LClaw0, LArm, Torso, Head0, RRLeg1, RFLeg1, LRLeg1, LFLeg1, RRLeg0, RFLeg0, LFLeg0, LRLeg0;

    static LayerDefinition create() {
        var mesh = new MeshDefinition();
        var root = mesh.getRoot();
        var TailHelm = root.addOrReplaceChild("TailHelm", CubeListBuilder.create().texOffs(0, 0).addBox(-4.5F, -4.5F, -0.4F, 9, 9, 9), PartPose.offsetAndRotation(0.0F, 18.0F, 0.0F, 0.1047198F, 0.0F, 0.0F));
        var TailBare = root.addOrReplaceChild("TailBare", CubeListBuilder.create().texOffs(64, 0).addBox(-4.0F, -4.0F, -0.4F, 8, 8, 8), PartPose.offsetAndRotation(0.0F, 18.0F, 0.0F, 0.1047198F, 0.0F, 0.0F));
        var RClaw1 = root.addOrReplaceChild("RClaw1", CubeListBuilder.create().texOffs(0, 47).addBox(-2.0F, -1.0F, -5.066667F, 4, 3, 5), PartPose.offsetAndRotation(-6.0F, 15.5F, -10.0F, 0, 0, 0));
        var Head1 = root.addOrReplaceChild("Head1", CubeListBuilder.create().texOffs(0, 38).addBox(-2.0F, -1.5F, -9.066667F, 4, 4, 1), PartPose.offsetAndRotation(0.0F, 18.0F, 0.0F, 0, 0, 0));
        var RClaw0 = root.addOrReplaceChild("RClaw0", CubeListBuilder.create().texOffs(0, 55).addBox(-2.0F, -2.5F, -3.066667F, 4, 5, 3), PartPose.offsetAndRotation(-6.0F, 17.0F, -7.0F, 0, 0, 0));
        var RClaw2 = root.addOrReplaceChild("RClaw2", CubeListBuilder.create().texOffs(14, 54).addBox(-1.5F, -1.0F, -4.066667F, 3, 2, 5), PartPose.offsetAndRotation(-6.0F, 18.5F, -10.0F, 0.3141593F, 0.0F, 0.0F));
        var RArm = root.addOrReplaceChild("RArm", CubeListBuilder.create().texOffs(44, 4).addBox(-1.0F, -1.0F, -5.066667F, 2, 2, 6), PartPose.offsetAndRotation(-3.0F, 17.0F, -4.0F, 0.0F, 0.7504916F, 0.0F));
        var LClaw2 = root.addOrReplaceChild("LClaw2", CubeListBuilder.create().texOffs(14, 54).addBox(-1.5F, -1.0F, -4.066667F, 3, 2, 5), PartPose.offsetAndRotation(6.0F, 18.5F, -10.0F, 0.3141593F, 0.0F, 0.0F));
        var LClaw1 = root.addOrReplaceChild("LClaw1", CubeListBuilder.create().texOffs(0, 47).mirror().addBox(-2.0F, -1.0F, -5.066667F, 4, 3, 5).mirror(false), PartPose.offsetAndRotation(6.0F, 15.5F, -10.0F, 0, 0, 0));
        var LClaw0 = root.addOrReplaceChild("LClaw0", CubeListBuilder.create().texOffs(0, 55).mirror().addBox(-2.0F, -2.5F, -3.066667F, 4, 5, 3).mirror(false), PartPose.offsetAndRotation(6.0F, 17.0F, -7.0F, 0, 0, 0));
        var LArm = root.addOrReplaceChild("LArm", CubeListBuilder.create().texOffs(44, 4).addBox(-1.0F, -1.0F, -4.066667F, 2, 2, 6), PartPose.offsetAndRotation(4.0F, 17.0F, -5.0F, 0.0F, -0.7504916F, 0.0F));
        var Torso = root.addOrReplaceChild("Torso", CubeListBuilder.create().texOffs(0, 18).addBox(-3.5F, -3.5F, -6.066667F, 7, 7, 6), PartPose.offsetAndRotation(0.0F, 18.0F, 0.0F, 0.0523599F, 0.0F, 0.0F));
        var Head0 = root.addOrReplaceChild("Head0", CubeListBuilder.create().texOffs(0, 31).addBox(-2.5F, -2.0F, -8.066667F, 5, 5, 2), PartPose.offsetAndRotation(0.0F, 18.0F, 0.0F, 0, 0, 0));
        var RRLeg1 = root.addOrReplaceChild("RRLeg1", CubeListBuilder.create().texOffs(36, 4).addBox(-4.5F, 1.0F, -0.9F, 2, 5, 2), PartPose.offsetAndRotation(-4.0F, 20.0F, -1.5F, 0, 0, 0));
        var RFLeg1 = root.addOrReplaceChild("RFLeg1", CubeListBuilder.create().texOffs(36, 4).addBox(-5.0F, 1.0F, -1.066667F, 2, 5, 2), PartPose.offsetAndRotation(-4.0F, 20.0F, -3.5F, 0, 0, 0));
        var LRLeg1 = root.addOrReplaceChild("LRLeg1", CubeListBuilder.create().texOffs(36, 4).addBox(2.5F, 1.0F, -0.9F, 2, 5, 2), PartPose.offsetAndRotation(4.0F, 20.0F, -1.5F, 0, 0, 0));
        var LFLeg1 = root.addOrReplaceChild("LFLeg1", CubeListBuilder.create().texOffs(36, 4).addBox(3.0F, 1.0F, -1.066667F, 2, 5, 2), PartPose.offsetAndRotation(4.0F, 20.0F, -3.5F, 0, 0, 0));
        var RRLeg0 = root.addOrReplaceChild("RRLeg0", CubeListBuilder.create().texOffs(36, 0).addBox(-4.5F, -1.0F, -0.9F, 6, 2, 2), PartPose.offsetAndRotation(-4.0F, 20.0F, -1.5F, 0, 0, 0));
        var RFLeg0 = root.addOrReplaceChild("RFLeg0", CubeListBuilder.create().texOffs(36, 0).addBox(-5.0F, -1.0F, -1.066667F, 6, 2, 2), PartPose.offsetAndRotation(-4.0F, 20.0F, -3.5F, 0, 0, 0));
        var LFLeg0 = root.addOrReplaceChild("LFLeg0", CubeListBuilder.create().texOffs(36, 0).addBox(-1.0F, -1.0F, -1.066667F, 6, 2, 2), PartPose.offsetAndRotation(4.0F, 20.0F, -3.5F, 0, 0, 0));
        var LRLeg0 = root.addOrReplaceChild("LRLeg0", CubeListBuilder.create().texOffs(36, 0).addBox(-1.5F, -1.0F, -0.9F, 6, 2, 2), PartPose.offsetAndRotation(4.0F, 20.0F, -1.5F, 0, 0, 0));
        return LayerDefinition.create(mesh, 128, 64);
    }

    EldritchCrabModel() { this(create().bakeRoot()); }
    EldritchCrabModel(ModelPart root) {
        this.root = root;

        TailHelm = root.getChild("TailHelm");
        TailBare = root.getChild("TailBare");
        RClaw1 = root.getChild("RClaw1");
        Head1 = root.getChild("Head1");
        RClaw0 = root.getChild("RClaw0");
        RClaw2 = root.getChild("RClaw2");
        RArm = root.getChild("RArm");
        LClaw2 = root.getChild("LClaw2");
        LClaw1 = root.getChild("LClaw1");
        LClaw0 = root.getChild("LClaw0");
        LArm = root.getChild("LArm");
        Torso = root.getChild("Torso");
        Head0 = root.getChild("Head0");
        RRLeg1 = root.getChild("RRLeg1");
        RFLeg1 = root.getChild("RFLeg1");
        LRLeg1 = root.getChild("LRLeg1");
        LFLeg1 = root.getChild("LFLeg1");
        RRLeg0 = root.getChild("RRLeg0");
        RFLeg0 = root.getChild("RFLeg0");
        LFLeg0 = root.getChild("LFLeg0");
        LRLeg0 = root.getChild("LRLeg0");
    }

    private static void rot(ModelPart part, float x, float y, float z) { part.xRot = x; part.yRot = y; part.zRot = z; }


    @Override public void setupAnim(github.com.gengyoubo.tcmodern.tc4.entity.EldritchCrab entity, float par1, float par2, float age, float yaw, float pitch) {
        TailHelm.visible = entity.hasHelm(); TailBare.visible = !entity.hasHelm();
        rot(RRLeg1, 0.0F, 0.2094395F, 0.4363323F); rot(RFLeg1, 0.0F, -0.2094395F, 0.4363323F);
        rot(LRLeg1, 0.0F, -0.2094395F, -0.4363323F); rot(LFLeg1, 0.0F, 0.2094395F, -0.4363323F);
        rot(RRLeg0, 0.0F, 0.2094395F, 0.4363323F); rot(RFLeg0, 0.0F, -0.2094395F, 0.4363323F);
        rot(LFLeg0, 0.0F, 0.2094395F, -0.4363323F); rot(LRLeg0, 0.0F, -0.2094395F, -0.4363323F);
        float f9 = -(Mth.cos(par1 * 0.6662F * 2.0F) * 0.4F) * par2, f10 = -(Mth.cos(par1 * 0.6662F * 2.0F + Mth.PI) * 0.4F) * par2;
        RRLeg1.yRot += f9; RRLeg0.yRot += f9; LRLeg1.yRot -= f9; LRLeg0.yRot -= f9;
        RFLeg1.yRot += f10; RFLeg0.yRot += f10; LFLeg1.yRot -= f10; LFLeg0.yRot -= f10;
        RRLeg1.zRot += f9; RRLeg0.zRot += f9; LRLeg1.zRot -= f9; LRLeg0.zRot -= f9;
        RFLeg1.zRot += f10; RFLeg0.zRot += f10; LFLeg1.zRot -= f10; LFLeg0.zRot -= f10;
        TailBare.yRot = TailHelm.yRot = Mth.cos(par1 * 0.6662F) * 2.0F * par2 * 0.125F;
        TailBare.zRot = TailHelm.zRot = Mth.cos(par1 * 0.6662F) * par2 * 0.125F;
        RClaw2.xRot = 0.3141593F - Mth.sin(age / 4.0F) * 0.25F;
        LClaw2.xRot = 0.3141593F + Mth.sin(age / 4.1F) * 0.25F;
        RClaw1.xRot = Mth.sin(age / 4.0F) * 0.125F;
        LClaw1.xRot = -Mth.sin(age / 4.1F) * 0.125F;
    }

    @Override public void renderToBuffer(PoseStack pose, VertexConsumer vertices, int light, int overlay, float r, float g, float b, float a) {
        root.render(pose, vertices, light, overlay, r, g, b, a);
    }
}
