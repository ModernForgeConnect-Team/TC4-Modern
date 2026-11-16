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

/** The eldritch golem, with or without its head. Geometry and texture layout of the original ModelEldritchGolem. */
final class EldritchGolemModel extends EntityModel<github.com.gengyoubo.tcmodern.tc4.entity.EldritchGolem> {
    final ModelPart root;
    final ModelPart Cloak1, Cloak3, Cloak2, CloakCL, CloakCR, Head, Head2, CollarL, CollarR, CollarB, CollarF, CollarBlack, Frontcloth0, Frontcloth1, Frontcloth2, Torso, ArmR, ShoulderR, ShoulderR0, ShoulderR1, ShoulderR2, ArmL, ShoulderL, ShoulderL0, ShoulderL1, ShoulderL2, BackpanelR1, WaistR1, WaistR2, WaistR3, LegR, WaistL1, WaistL2, WaistL3, BackpanelL1, LegL;

    static LayerDefinition create() {
        var mesh = new MeshDefinition();
        var root = mesh.getRoot();
        var Cloak1 = root.addOrReplaceChild("Cloak1", CubeListBuilder.create().texOffs(0, 47).addBox(-5.0F, 1.5F, 4.0F, 10, 12, 1), PartPose.offsetAndRotation(0.0F, 0.0F, -2.5F, 0.1396263F, 0.0F, 0.0F));
        var Cloak3 = root.addOrReplaceChild("Cloak3", CubeListBuilder.create().texOffs(0, 37).addBox(-5.0F, 17.5F, -0.8F, 10, 4, 1), PartPose.offsetAndRotation(0.0F, 0.0F, -2.5F, 0.4465716F, 0.0F, 0.0F));
        var Cloak2 = root.addOrReplaceChild("Cloak2", CubeListBuilder.create().texOffs(0, 59).addBox(-5.0F, 13.5F, 1.7F, 10, 4, 1), PartPose.offsetAndRotation(0.0F, 0.0F, -2.5F, 0.3069452F, 0.0F, 0.0F));
        var CloakCL = root.addOrReplaceChild("CloakCL", CubeListBuilder.create().texOffs(0, 43).addBox(3.0F, 0.5F, 2.0F, 2, 1, 3), PartPose.offsetAndRotation(0.0F, 0.0F, -2.5F, 0.1396263F, 0.0F, 0.0F));
        var CloakCR = root.addOrReplaceChild("CloakCR", CubeListBuilder.create().texOffs(0, 43).addBox(-5.0F, 0.5F, 2.0F, 2, 1, 3), PartPose.offsetAndRotation(0.0F, 0.0F, -2.5F, 0.1396263F, 0.0F, 0.0F));
        var Head = root.addOrReplaceChild("Head", CubeListBuilder.create().texOffs(47, 12).addBox(-3.5F, -6.0F, -2.5F, 7, 7, 5), PartPose.offsetAndRotation(0.0F, 4.5F, -3.8F, -0.1047198F, 0.0F, 0.0F));
        var Head2 = root.addOrReplaceChild("Head2", CubeListBuilder.create().texOffs(26, 16).addBox(-2.0F, -2.0F, -2.0F, 4, 4, 4), PartPose.offsetAndRotation(0.0F, 0.0F, -5.0F, -0.1047198F, 0.0F, 0.0F));
        var CollarL = root.addOrReplaceChild("CollarL", CubeListBuilder.create().texOffs(75, 50).addBox(3.5F, -0.5F, -7.0F, 1, 4, 10), PartPose.offsetAndRotation(0.0F, 0.0F, -2.5F, 0.837758F, 0.0F, 0.0F));
        var CollarR = root.addOrReplaceChild("CollarR", CubeListBuilder.create().texOffs(67, 50).addBox(-4.5F, -0.5F, -7.0F, 1, 4, 10), PartPose.offsetAndRotation(0.0F, 0.0F, -2.5F, 0.837758F, 0.0F, 0.0F));
        var CollarB = root.addOrReplaceChild("CollarB", CubeListBuilder.create().texOffs(77, 59).addBox(-3.5F, -0.5F, 2.0F, 7, 4, 1), PartPose.offsetAndRotation(0.0F, 0.0F, -2.5F, 0.837758F, 0.0F, 0.0F));
        var CollarF = root.addOrReplaceChild("CollarF", CubeListBuilder.create().texOffs(77, 59).addBox(-3.5F, -0.5F, -7.0F, 7, 4, 1), PartPose.offsetAndRotation(0.0F, 0.0F, -2.5F, 0.837758F, 0.0F, 0.0F));
        var CollarBlack = root.addOrReplaceChild("CollarBlack", CubeListBuilder.create().texOffs(22, 0).addBox(-3.5F, 0.0F, -6.0F, 7, 1, 8), PartPose.offsetAndRotation(0.0F, 0.0F, -2.5F, 0.837758F, 0.0F, 0.0F));
        var Torso = root.addOrReplaceChild("Torso", CubeListBuilder.create().texOffs(34, 45).addBox(-5.0F, 2.5F, -3.0F, 10, 10, 6), PartPose.offsetAndRotation(0.0F, 0.0F, -2.5F, 0.1745329F, 0.0F, 0.0F));
        var ArmR = root.addOrReplaceChild("ArmR", CubeListBuilder.create().texOffs(78, 32).addBox(-3.5F, 1.5F, -2.0F, 4, 13, 5), PartPose.offsetAndRotation(-5.0F, 3.0F, -2.0F, 0.0F, 0.0F, 0.1047198F));
        var ShoulderR = ArmR.addOrReplaceChild("ShoulderR", CubeListBuilder.create().texOffs(0, 0).addBox(-4.3F, -1.0F, -3.0F, 4, 5, 7), PartPose.offsetAndRotation(0, 0, 0, 0.0F, 0.0F, 1.186824F));
        var ShoulderR0 = ArmR.addOrReplaceChild("ShoulderR0", CubeListBuilder.create().texOffs(56, 31).addBox(-4.5F, -1.5F, -2.5F, 5, 6, 6), PartPose.offsetAndRotation(0, 0, 0, 0.0F, 0.0F, 0.0F));
        var ShoulderR1 = ArmR.addOrReplaceChild("ShoulderR1", CubeListBuilder.create().texOffs(0, 23).addBox(-3.3F, 4.0F, -2.5F, 1, 2, 6), PartPose.offsetAndRotation(0, 0, 0, 0.0F, 0.0F, 1.186824F));
        var ShoulderR2 = ArmR.addOrReplaceChild("ShoulderR2", CubeListBuilder.create().texOffs(0, 12).addBox(-2.3F, 4.0F, -3.0F, 2, 3, 7), PartPose.offsetAndRotation(0, 0, 0, 0.0F, 0.0F, 1.186824F));
        var ArmL = root.addOrReplaceChild("ArmL", CubeListBuilder.create().texOffs(78, 32).mirror().addBox(-0.5F, 1.5F, -2.0F, 4, 13, 5).mirror(false), PartPose.offsetAndRotation(5.0F, 3.0F, -2.0F, 0.0F, 0.0F, -0.1047198F));
        var ShoulderL = ArmL.addOrReplaceChild("ShoulderL", CubeListBuilder.create().texOffs(0, 0).mirror().addBox(0.3F, -1.0F, -3.0F, 4, 5, 7).mirror(false), PartPose.offsetAndRotation(0, 0, 0, 0.0F, 0.0F, -1.186824F));
        var ShoulderL0 = ArmL.addOrReplaceChild("ShoulderL0", CubeListBuilder.create().texOffs(56, 31).mirror().addBox(-0.5F, -1.5F, -2.5F, 5, 6, 6).mirror(false), PartPose.offsetAndRotation(0, 0, 0, 0.0F, 0.0F, 0.0F));
        var ShoulderL1 = ArmL.addOrReplaceChild("ShoulderL1", CubeListBuilder.create().texOffs(0, 23).mirror().addBox(2.3F, 4.0F, -2.5F, 1, 2, 6).mirror(false), PartPose.offsetAndRotation(0, 0, 0, 0.0F, 0.0F, -1.186824F));
        var ShoulderL2 = ArmL.addOrReplaceChild("ShoulderL2", CubeListBuilder.create().texOffs(0, 12).mirror().addBox(0.3F, 4.0F, -3.0F, 2, 3, 7).mirror(false), PartPose.offsetAndRotation(0, 0, 0, 0.0F, 0.0F, -1.186824F));
        var BackpanelR1 = root.addOrReplaceChild("BackpanelR1", CubeListBuilder.create().texOffs(96, 7).addBox(0.0F, 2.5F, -2.5F, 2, 2, 5), PartPose.offsetAndRotation(-2.0F, 12.0F, 0.0F, 0.0F, 0.0F, 0.1396263F));
        var WaistR1 = root.addOrReplaceChild("WaistR1", CubeListBuilder.create().texOffs(96, 14).addBox(-3.0F, -0.5F, -2.5F, 5, 3, 5), PartPose.offsetAndRotation(-2.0F, 12.0F, 0.0F, 0.0F, 0.0F, 0.1396263F));
        var WaistR2 = root.addOrReplaceChild("WaistR2", CubeListBuilder.create().texOffs(116, 13).addBox(-3.0F, 2.5F, -2.5F, 1, 4, 5), PartPose.offsetAndRotation(-2.0F, 12.0F, 0.0F, 0.0F, 0.0F, 0.1396263F));
        var WaistR3 = root.addOrReplaceChild("WaistR3", CubeListBuilder.create().texOffs(114, 5).mirror().addBox(-2.0F, 2.5F, -2.5F, 2, 3, 5).mirror(false), PartPose.offsetAndRotation(-2.0F, 12.0F, 0.0F, 0.0F, 0.0F, 0.1396263F));
        var LegR = root.addOrReplaceChild("LegR", CubeListBuilder.create().texOffs(79, 19).addBox(-2.5F, 2.5F, -2.0F, 4, 9, 4), PartPose.offsetAndRotation(-2.0F, 12.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        var WaistL1 = root.addOrReplaceChild("WaistL1", CubeListBuilder.create().texOffs(96, 14).addBox(-2.0F, -0.5F, -2.5F, 5, 3, 5), PartPose.offsetAndRotation(2.0F, 12.0F, 0.0F, 0.0F, 0.0F, -0.1396263F));
        var WaistL2 = root.addOrReplaceChild("WaistL2", CubeListBuilder.create().texOffs(116, 13).addBox(2.0F, 2.5F, -2.5F, 1, 4, 5), PartPose.offsetAndRotation(2.0F, 12.0F, 0.0F, 0.0F, 0.0F, -0.1396263F));
        var WaistL3 = root.addOrReplaceChild("WaistL3", CubeListBuilder.create().texOffs(114, 5).addBox(0.0F, 2.5F, -2.5F, 2, 3, 5), PartPose.offsetAndRotation(2.0F, 12.0F, 0.0F, 0.0F, 0.0F, -0.1396263F));
        var BackpanelL1 = root.addOrReplaceChild("BackpanelL1", CubeListBuilder.create().texOffs(96, 7).addBox(-2.0F, 2.5F, -2.5F, 2, 2, 5), PartPose.offsetAndRotation(2.0F, 12.0F, 0.0F, 0.0F, 0.0F, -0.1396263F));
        var LegL = root.addOrReplaceChild("LegL", CubeListBuilder.create().texOffs(79, 19).addBox(-1.5F, 2.5F, -2.0F, 4, 9, 4), PartPose.offsetAndRotation(2.0F, 12.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 128, 64);
    }

    static LayerDefinition cloth() {
        var mesh = new MeshDefinition();
        var root = mesh.getRoot();
        var Frontcloth0 = root.addOrReplaceChild("Frontcloth0", CubeListBuilder.create().texOffs(114, 52).addBox(-3.0F, 3.2F, -3.5F, 6, 10, 1), PartPose.offsetAndRotation(0.0F, 0.0F, -2.5F, 0.1745329F, 0.0F, 0.0F));
        var Frontcloth1 = root.addOrReplaceChild("Frontcloth1", CubeListBuilder.create().texOffs(114, 39).addBox(-1.0F, 1.5F, -3.5F, 6, 6, 1), PartPose.offsetAndRotation(-2.0F, 12.0F, 0.0F, -0.1047198F, 0.0F, 0.0F));
        var Frontcloth2 = root.addOrReplaceChild("Frontcloth2", CubeListBuilder.create().texOffs(114, 47).addBox(-1.0F, 8.5F, -1.5F, 6, 3, 1), PartPose.offsetAndRotation(-2.0F, 11.0F, 0.0F, -0.3316126F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 114, 64);
    }

    EldritchGolemModel() { this(create().bakeRoot(), cloth().bakeRoot()); }
    EldritchGolemModel(ModelPart main, ModelPart clothRoot) {
        var children = new java.util.HashMap<String, ModelPart>();
        for (var name : new String[] { "Cloak1", "Cloak3", "Cloak2", "CloakCL", "CloakCR", "Head", "Head2", "CollarL", "CollarR", "CollarB", "CollarF", "CollarBlack", "Frontcloth0", "Frontcloth1", "Frontcloth2", "Torso", "ArmR", "ArmL", "BackpanelR1", "WaistR1", "WaistR2", "WaistR3", "LegR", "WaistL1", "WaistL2", "WaistL3", "BackpanelL1", "LegL" })
            children.put(name, (name.startsWith("Frontcloth") ? clothRoot : main).getChild(name));
        this.root = new ModelPart(java.util.List.of(), children);
        var root = this.root;

        Cloak1 = root.getChild("Cloak1");
        Cloak3 = root.getChild("Cloak3");
        Cloak2 = root.getChild("Cloak2");
        CloakCL = root.getChild("CloakCL");
        CloakCR = root.getChild("CloakCR");
        Head = root.getChild("Head");
        Head2 = root.getChild("Head2");
        CollarL = root.getChild("CollarL");
        CollarR = root.getChild("CollarR");
        CollarB = root.getChild("CollarB");
        CollarF = root.getChild("CollarF");
        CollarBlack = root.getChild("CollarBlack");
        Frontcloth0 = root.getChild("Frontcloth0");
        Frontcloth1 = root.getChild("Frontcloth1");
        Frontcloth2 = root.getChild("Frontcloth2");
        Torso = root.getChild("Torso");
        ArmR = root.getChild("ArmR");
        ShoulderR = ArmR.getChild("ShoulderR");
        ShoulderR0 = ArmR.getChild("ShoulderR0");
        ShoulderR1 = ArmR.getChild("ShoulderR1");
        ShoulderR2 = ArmR.getChild("ShoulderR2");
        ArmL = root.getChild("ArmL");
        ShoulderL = ArmL.getChild("ShoulderL");
        ShoulderL0 = ArmL.getChild("ShoulderL0");
        ShoulderL1 = ArmL.getChild("ShoulderL1");
        ShoulderL2 = ArmL.getChild("ShoulderL2");
        BackpanelR1 = root.getChild("BackpanelR1");
        WaistR1 = root.getChild("WaistR1");
        WaistR2 = root.getChild("WaistR2");
        WaistR3 = root.getChild("WaistR3");
        LegR = root.getChild("LegR");
        WaistL1 = root.getChild("WaistL1");
        WaistL2 = root.getChild("WaistL2");
        WaistL3 = root.getChild("WaistL3");
        BackpanelL1 = root.getChild("BackpanelL1");
        LegL = root.getChild("LegL");
    }

    private static float triangleWave(float value, float period) { return (Math.abs(value % period - period * 0.5F) - period * 0.25F) / (period * 0.25F); }

    @Override public void prepareMobModel(github.com.gengyoubo.tcmodern.tc4.entity.EldritchGolem golem, float par1, float par2, float partialTick) {
        int i = golem.attackTimer();
        if (i > 0) {
            ArmR.xRot = -2.0F + 1.5F * triangleWave(i - partialTick, 10.0F);
            ArmL.xRot = -2.0F + 1.5F * triangleWave(i - partialTick, 10.0F);
        } else {
            ArmR.xRot = Mth.cos(par1 * 0.4F + Mth.PI) * 2.0F * par2 * 0.5F;
            ArmL.xRot = Mth.cos(par1 * 0.4F) * 2.0F * par2 * 0.5F;
        }
    }

    @Override public void setupAnim(github.com.gengyoubo.tcmodern.tc4.entity.EldritchGolem entity, float par1, float par2, float age, float par4, float par5) {
        Head.visible = !entity.isHeadless(); Head2.visible = entity.isHeadless();
        if (entity.spawnTimer() > 0) {
            Head.yRot = 0.0F;
            Head.xRot = (entity.spawnTimer() / 2) * Mth.DEG_TO_RAD;
        } else {
            Head.yRot = par4 / 4.0F * Mth.DEG_TO_RAD; Head.xRot = par5 / 2.0F * Mth.DEG_TO_RAD;
            Head2.yRot = par4 * Mth.DEG_TO_RAD; Head2.xRot = par5 * Mth.DEG_TO_RAD;
        }
        LegR.xRot = Mth.cos(par1 * 0.4662F) * 1.4F * par2;
        LegL.xRot = Mth.cos(par1 * 0.4662F + Mth.PI) * 1.4F * par2;
        float a = Mth.cos(par1 * 0.44F) * 1.4F * par2, b = Mth.cos(par1 * 0.44F + Mth.PI) * 1.4F * par2, c = Math.min(a, b);
        Frontcloth1.xRot = c - 0.1047198F;
        Frontcloth2.xRot = c - 0.3316126F;
        Cloak1.xRot = -c / 3.0F + 0.1396263F;
        Cloak2.xRot = -c / 3.0F + 0.3069452F;
        Cloak3.xRot = -c / 3.0F + 0.4465716F;
    }

    @Override public void renderToBuffer(PoseStack pose, VertexConsumer vertices, int light, int overlay, float r, float g, float b, float a) {
        root.render(pose, vertices, light, overlay, r, g, b, a);
    }
}
