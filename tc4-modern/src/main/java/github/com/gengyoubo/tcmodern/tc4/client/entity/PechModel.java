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

/** The pech, with its pack and mumbling jowls. Geometry and texture layout of the original ModelPech. */
final class PechModel extends EntityModel<github.com.gengyoubo.tcmodern.tc4.entity.Pech> {
    final ModelPart root;
    final ModelPart Body, RightLeg, LeftLeg, Head, Jowls, LowerPack, UpperPack, RightArm, LeftArm;

    static LayerDefinition create() {
        var mesh = new MeshDefinition();
        var root = mesh.getRoot();
        var Body = root.addOrReplaceChild("Body", CubeListBuilder.create().texOffs(34, 12).addBox(-3.0F, 0.0F, 0.0F, 6, 10, 6), PartPose.offsetAndRotation(0.0F, 9.0F, -3.0F, 0.3129957F, 0.0F, 0.0F));
        var RightLeg = root.addOrReplaceChild("RightLeg", CubeListBuilder.create().texOffs(35, 1).mirror().addBox(-2.9F, 0.0F, 0.0F, 3, 6, 3).mirror(false), PartPose.offsetAndRotation(0.0F, 18.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        var LeftLeg = root.addOrReplaceChild("LeftLeg", CubeListBuilder.create().texOffs(35, 1).addBox(-0.1F, 0.0F, 0.0F, 3, 6, 3), PartPose.offsetAndRotation(0.0F, 18.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        var Head = root.addOrReplaceChild("Head", CubeListBuilder.create().texOffs(2, 11).addBox(-3.5F, -5.0F, -5.0F, 7, 5, 5), PartPose.offsetAndRotation(0.0F, 8.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        var Jowls = root.addOrReplaceChild("Jowls", CubeListBuilder.create().texOffs(1, 21).addBox(-4.0F, -1.0F, -6.0F, 8, 3, 5), PartPose.offsetAndRotation(0.0F, 8.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        var LowerPack = root.addOrReplaceChild("LowerPack", CubeListBuilder.create().texOffs(0, 0).addBox(-5.0F, 0.0F, 0.0F, 10, 5, 5), PartPose.offsetAndRotation(0.0F, 10.0F, 3.5F, 0.3013602F, 0.0F, 0.0F));
        var UpperPack = root.addOrReplaceChild("UpperPack", CubeListBuilder.create().texOffs(64, 1).addBox(-7.5F, -14.0F, 0.0F, 15, 14, 11), PartPose.offsetAndRotation(0.0F, 10.0F, 3.0F, 0.4537856F, 0.0F, 0.0F));
        var RightArm = root.addOrReplaceChild("RightArm", CubeListBuilder.create().texOffs(52, 2).mirror().addBox(-2.0F, 0.0F, -1.0F, 2, 6, 2).mirror(false), PartPose.offsetAndRotation(-3.0F, 10.0F, -1.0F, 0.0F, 0.0F, 0.0F));
        var LeftArm = root.addOrReplaceChild("LeftArm", CubeListBuilder.create().texOffs(52, 2).addBox(0.0F, 0.0F, -1.0F, 2, 6, 2), PartPose.offsetAndRotation(3.0F, 10.0F, -1.0F, 0.0F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 128, 64);
    }

    PechModel() { this(create().bakeRoot()); }
    PechModel(ModelPart root) {
        this.root = root;

        Body = root.getChild("Body");
        RightLeg = root.getChild("RightLeg");
        LeftLeg = root.getChild("LeftLeg");
        Head = root.getChild("Head");
        Jowls = root.getChild("Jowls");
        LowerPack = root.getChild("LowerPack");
        UpperPack = root.getChild("UpperPack");
        RightArm = root.getChild("RightArm");
        LeftArm = root.getChild("LeftArm");
    }

    private static void rot(ModelPart part, float x, float y, float z) { part.xRot = x; part.yRot = y; part.zRot = z; }


    @Override public void setupAnim(github.com.gengyoubo.tcmodern.tc4.entity.Pech entity, float par1, float par2, float par3, float par4, float par5) {
        Head.yRot = par4 * Mth.DEG_TO_RAD; Head.xRot = par5 * Mth.DEG_TO_RAD;
        float mumble = entity.mumble;
        Jowls.yRot = Head.yRot;
        Jowls.xRot = Head.xRot + Mth.PI / 12 + Mth.cos(par1 * 0.6662F) * par2 * 0.25F + 0.34906587F * Math.abs(Mth.sin(mumble));
        RightArm.xRot = Mth.cos(par1 * 0.6662F + Mth.PI) * 2.0F * par2 * 0.5F;
        LeftArm.xRot = Mth.cos(par1 * 0.6662F) * 2.0F * par2 * 0.5F;
        RightArm.zRot = 0.0F; LeftArm.zRot = 0.0F;
        RightLeg.xRot = Mth.cos(par1 * 0.6662F) * 1.4F * par2;
        LeftLeg.xRot = Mth.cos(par1 * 0.6662F + Mth.PI) * 1.4F * par2;
        RightLeg.yRot = 0.0F; LeftLeg.yRot = 0.0F;
        LowerPack.yRot = Mth.cos(par1 * 0.6662F) * 2.0F * par2 * 0.125F;
        LowerPack.zRot = Mth.cos(par1 * 0.6662F) * 2.0F * par2 * 0.125F;
        if (riding) {
            RightArm.xRot += -Mth.PI / 5; LeftArm.xRot += -Mth.PI / 5;
            RightLeg.xRot = -Mth.PI * 2.0F / 5.0F; LeftLeg.xRot = -Mth.PI * 2.0F / 5.0F;
            RightLeg.yRot = Mth.PI / 10; LeftLeg.yRot = -Mth.PI / 10;
        }
        RightArm.yRot = 0.0F; LeftArm.yRot = 0.0F;
        if (attackTime > 0.0F) {
            RightArm.yRot += Body.yRot; LeftArm.yRot += Body.yRot; LeftArm.xRot += Body.yRot;
            float f6 = 1.0F - attackTime; f6 *= f6; f6 *= f6; f6 = 1.0F - f6;
            float f7 = Mth.sin(f6 * Mth.PI), f8 = Mth.sin(attackTime * Mth.PI) * -(Head.xRot - 0.7F) * 0.75F;
            RightArm.xRot = (float) (RightArm.xRot - (f7 * 1.2 + f8));
            RightArm.yRot += Body.yRot * 2.0F;
            RightArm.zRot = Mth.sin(attackTime * Mth.PI) * -0.4F;
        }
        if (entity.isCrouching()) { RightArm.xRot += 0.4F; LeftArm.xRot += 0.4F; }
        RightArm.zRot += Mth.cos(par3 * 0.09F) * 0.05F + 0.05F;
        LeftArm.zRot -= Mth.cos(par3 * 0.09F) * 0.05F + 0.05F;
        RightArm.xRot += Mth.sin(par3 * 0.067F) * 0.05F;
        LeftArm.xRot -= Mth.sin(par3 * 0.067F) * 0.05F;
    }

    @Override public void renderToBuffer(PoseStack pose, VertexConsumer vertices, int light, int overlay, float r, float g, float b, float a) {
        root.render(pose, vertices, light, overlay, r, g, b, a);
    }
}
