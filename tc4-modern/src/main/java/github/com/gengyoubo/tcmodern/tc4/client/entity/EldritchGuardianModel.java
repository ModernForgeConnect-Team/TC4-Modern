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

/** The hooded eldritch guardian, also worn by the warden. Geometry and texture layout of the original ModelEldritchGuardian. */
final class EldritchGuardianModel<T extends net.minecraft.world.entity.Mob> extends EntityModel<T> {
    final ModelPart root;
    final ModelPart BeltR, Mbelt, MbeltL, MbeltR, BeltL, Chestplate, HoodEye, Hood1, Hood2, Hood3, Hood4, Backplate, ShoulderplateTopR, ShoulderplateR1, ShoulderplateR2, ShoulderplateR3, ShoulderR, ArmL1, ArmL2, ArmL3, ArmR1, ArmR2, ArmR3, ShoulderL, ShoulderplateLtop, ShoulderplateL1, ShoulderplateL2, ShoulderplateL3, LegpanelR4, LegpanelR5, LegpanelR6, BackpanelR1, BackpanelR2, BackpanelR3, BackpanelL3, LegpanelL4, LegpanelL5, LegpanelL6, BackpanelL1, BackpanelL2, SidepanelL1, SidepanelR1, SidepanelR2, SidepanelR3, SidepanelR4, SidepanelL2, SidepanelL3, SidepanelL4, LegpanelC1, LegpanelC2, LegpanelC3, Cloak1, Cloak2, Cloak3;

    static LayerDefinition create() {
        var mesh = new MeshDefinition();
        var root = mesh.getRoot();
        var BeltR = root.addOrReplaceChild("BeltR", CubeListBuilder.create().texOffs(76, 44).addBox(-5.0F, 4.0F, -3.0F, 1, 3, 6), PartPose.offsetAndRotation(0.0F, -6.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        var Mbelt = root.addOrReplaceChild("Mbelt", CubeListBuilder.create().texOffs(56, 55).addBox(-4.0F, 8.0F, -3.0F, 8, 4, 1), PartPose.offsetAndRotation(0.0F, -6.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        var MbeltL = root.addOrReplaceChild("MbeltL", CubeListBuilder.create().texOffs(76, 44).addBox(4.0F, 8.0F, -3.0F, 1, 3, 6), PartPose.offsetAndRotation(0.0F, -6.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        var MbeltR = root.addOrReplaceChild("MbeltR", CubeListBuilder.create().texOffs(76, 44).addBox(-5.0F, 8.0F, -3.0F, 1, 3, 6), PartPose.offsetAndRotation(0.0F, -6.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        var BeltL = root.addOrReplaceChild("BeltL", CubeListBuilder.create().texOffs(76, 44).addBox(4.0F, 4.0F, -3.0F, 1, 3, 6), PartPose.offsetAndRotation(0.0F, -6.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        var Chestplate = root.addOrReplaceChild("Chestplate", CubeListBuilder.create().texOffs(56, 45).addBox(-4.0F, 1.0F, -4.0F, 8, 7, 2), PartPose.offsetAndRotation(0.0F, -6.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        var HoodEye = root.addOrReplaceChild("HoodEye", CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -8.0F, -4.0F, 8, 8, 8), PartPose.offsetAndRotation(0.0F, -6.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        var Hood1 = root.addOrReplaceChild("Hood1", CubeListBuilder.create().texOffs(40, 12).addBox(-4.0F, -8.0F, -4.0F, 8, 8, 8), PartPose.offsetAndRotation(0.0F, -6.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        var Hood2 = Hood1.addOrReplaceChild("Hood2", CubeListBuilder.create().texOffs(36, 28).addBox(-3.5F, -8.7F, 2.0F, 7, 7, 3), PartPose.offsetAndRotation(0, 0, 0, -0.2268928F, 0.0F, 0.0F));
        var Hood3 = Hood1.addOrReplaceChild("Hood3", CubeListBuilder.create().texOffs(22, 19).addBox(-3.0F, -9.0F, 2.5F, 6, 6, 3), PartPose.offsetAndRotation(0, 0, 0, -0.3490659F, 0.0F, 0.0F));
        var Hood4 = Hood1.addOrReplaceChild("Hood4", CubeListBuilder.create().texOffs(40, 4).addBox(-2.5F, -9.7F, 3.5F, 5, 5, 3), PartPose.offsetAndRotation(0, 0, 0, -0.5759587F, 0.0F, 0.0F));
        var Backplate = root.addOrReplaceChild("Backplate", CubeListBuilder.create().texOffs(36, 45).addBox(-4.0F, 1.0F, 2.0F, 8, 11, 2), PartPose.offsetAndRotation(0.0F, -6.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        var ShoulderplateTopR = root.addOrReplaceChild("ShoulderplateTopR", CubeListBuilder.create().texOffs(110, 37).addBox(-5.5F, -2.5F, -3.5F, 2, 1, 7), PartPose.offsetAndRotation(-5.0F, -4.0F, 0.0F, -0.3665191F, 0.3141593F, 0.4363323F));
        var ShoulderplateR1 = root.addOrReplaceChild("ShoulderplateR1", CubeListBuilder.create().texOffs(110, 45).addBox(3.5F, -1.5F, -3.5F, 1, 4, 7), PartPose.offsetAndRotation(5.0F, -4.0F, 0.0F, -0.3665191F, -0.3141593F, -0.4363323F));
        var ShoulderplateR2 = root.addOrReplaceChild("ShoulderplateR2", CubeListBuilder.create().texOffs(94, 45).addBox(-3.5F, 1.5F, -3.5F, 1, 3, 7), PartPose.offsetAndRotation(-5.0F, -4.0F, 0.0F, -0.3665191F, 0.3141593F, 0.4363323F));
        var ShoulderplateR3 = root.addOrReplaceChild("ShoulderplateR3", CubeListBuilder.create().texOffs(94, 45).addBox(-2.5F, 3.5F, -3.5F, 1, 3, 7), PartPose.offsetAndRotation(-5.0F, -4.0F, 0.0F, -0.3665191F, 0.3141593F, 0.4363323F));
        var ShoulderR = root.addOrReplaceChild("ShoulderR", CubeListBuilder.create().texOffs(56, 35).addBox(-3.5F, -2.5F, -2.5F, 5, 5, 5), PartPose.offsetAndRotation(-5.0F, -4.0F, 0.0F, -0.3665191F, 0.122173F, 0.0349066F));
        var ArmL1 = root.addOrReplaceChild("ArmL1", CubeListBuilder.create().texOffs(72, 8).addBox(-1.0F, 2.5F, -1.5F, 4, 10, 5), PartPose.offsetAndRotation(5.0F, -4.0F, 0.0F, -0.9599311F, -0.1047198F, -0.1919862F));
        var ArmL2 = ArmL1.addOrReplaceChild("ArmL2", CubeListBuilder.create().texOffs(76, 28).addBox(-1.0F, 9.5F, 3.5F, 4, 3, 3), PartPose.offsetAndRotation(0, 0, 0, 0, 0, 0));
        var ArmL3 = ArmL1.addOrReplaceChild("ArmL3", CubeListBuilder.create().texOffs(76, 23).addBox(-1.0F, 6.5F, 3.5F, 4, 3, 2), PartPose.offsetAndRotation(0, 0, 0, 0, 0, 0));
        var ArmR1 = root.addOrReplaceChild("ArmR1", CubeListBuilder.create().texOffs(72, 8).addBox(-3.0F, 2.5F, -1.5F, 4, 10, 5), PartPose.offsetAndRotation(-5.0F, -4.0F, 0.0F, -0.9599311F, 0.1047198F, 0.1919862F));
        var ArmR2 = ArmR1.addOrReplaceChild("ArmR2", CubeListBuilder.create().texOffs(76, 28).addBox(-3.0F, 9.5F, 3.5F, 4, 3, 3), PartPose.offsetAndRotation(0, 0, 0, 0, 0, 0));
        var ArmR3 = ArmR1.addOrReplaceChild("ArmR3", CubeListBuilder.create().texOffs(76, 23).addBox(-3.0F, 6.5F, 3.5F, 4, 3, 2), PartPose.offsetAndRotation(0, 0, 0, 0, 0, 0));
        var ShoulderL = root.addOrReplaceChild("ShoulderL", CubeListBuilder.create().texOffs(56, 35).mirror().addBox(-1.5F, -2.5F, -2.5F, 5, 5, 5).mirror(false), PartPose.offsetAndRotation(5.0F, -4.0F, 0.0F, -0.3665191F, -0.122173F, -0.0349066F));
        var ShoulderplateLtop = root.addOrReplaceChild("ShoulderplateLtop", CubeListBuilder.create().texOffs(110, 37).addBox(3.5F, -2.5F, -3.5F, 2, 1, 7), PartPose.offsetAndRotation(5.0F, -4.0F, 0.0F, -0.3665191F, -0.3141593F, -0.4363323F));
        var ShoulderplateL1 = root.addOrReplaceChild("ShoulderplateL1", CubeListBuilder.create().texOffs(110, 45).addBox(-4.5F, -1.5F, -3.5F, 1, 4, 7), PartPose.offsetAndRotation(-5.0F, -4.0F, 0.0F, -0.3665191F, 0.3141593F, 0.4363323F));
        var ShoulderplateL2 = root.addOrReplaceChild("ShoulderplateL2", CubeListBuilder.create().texOffs(94, 45).addBox(2.5F, 1.5F, -3.5F, 1, 3, 7), PartPose.offsetAndRotation(5.0F, -4.0F, 0.0F, -0.3665191F, -0.3141593F, -0.4363323F));
        var ShoulderplateL3 = root.addOrReplaceChild("ShoulderplateL3", CubeListBuilder.create().texOffs(94, 45).addBox(1.5F, 3.5F, -3.5F, 1, 3, 7), PartPose.offsetAndRotation(5.0F, -4.0F, 0.0F, -0.3665191F, -0.3141593F, -0.4363323F));
        var LegpanelR4 = root.addOrReplaceChild("LegpanelR4", CubeListBuilder.create().texOffs(0, 43).addBox(-3.0F, 0.5F, -3.5F, 2, 3, 1), PartPose.offsetAndRotation(-2.0F, 6.0F, 0.0F, -0.4363323F, 0.0F, 0.0F));
        var LegpanelR5 = root.addOrReplaceChild("LegpanelR5", CubeListBuilder.create().texOffs(0, 47).addBox(-3.0F, 2.5F, -2.5F, 2, 3, 1), PartPose.offsetAndRotation(-2.0F, 6.0F, 0.0F, -0.4363323F, 0.0F, 0.0F));
        var LegpanelR6 = root.addOrReplaceChild("LegpanelR6", CubeListBuilder.create().texOffs(6, 43).addBox(-3.0F, 4.5F, -1.5F, 2, 3, 1), PartPose.offsetAndRotation(-2.0F, 6.0F, 0.0F, -0.4363323F, 0.0F, 0.0F));
        var BackpanelR1 = root.addOrReplaceChild("BackpanelR1", CubeListBuilder.create().texOffs(0, 18).addBox(-3.0F, 0.5F, 2.5F, 5, 3, 1), PartPose.offsetAndRotation(-2.0F, 6.0F, 0.0F, 0.4363323F, 0.0F, 0.0F));
        var BackpanelR2 = root.addOrReplaceChild("BackpanelR2", CubeListBuilder.create().texOffs(0, 18).addBox(-3.0F, 2.5F, 1.5F, 5, 3, 1), PartPose.offsetAndRotation(-2.0F, 6.0F, 0.0F, 0.4363323F, 0.0F, 0.0F));
        var BackpanelR3 = root.addOrReplaceChild("BackpanelR3", CubeListBuilder.create().texOffs(0, 18).addBox(-3.0F, 4.5F, 0.5F, 5, 3, 1), PartPose.offsetAndRotation(-2.0F, 6.0F, 0.0F, 0.4363323F, 0.0F, 0.0F));
        var BackpanelL3 = root.addOrReplaceChild("BackpanelL3", CubeListBuilder.create().texOffs(0, 18).addBox(-2.0F, 4.5F, 0.5F, 5, 3, 1), PartPose.offsetAndRotation(2.0F, 6.0F, 0.0F, 0.4363323F, 0.0F, 0.0F));
        var LegpanelL4 = root.addOrReplaceChild("LegpanelL4", CubeListBuilder.create().texOffs(0, 43).addBox(1.0F, 0.5F, -3.5F, 2, 3, 1), PartPose.offsetAndRotation(2.0F, 6.0F, 0.0F, -0.4363323F, 0.0F, 0.0F));
        var LegpanelL5 = root.addOrReplaceChild("LegpanelL5", CubeListBuilder.create().texOffs(0, 47).addBox(1.0F, 2.5F, -2.5F, 2, 3, 1), PartPose.offsetAndRotation(2.0F, 6.0F, 0.0F, -0.4363323F, 0.0F, 0.0F));
        var LegpanelL6 = root.addOrReplaceChild("LegpanelL6", CubeListBuilder.create().texOffs(6, 43).addBox(1.0F, 4.5F, -1.5F, 2, 3, 1), PartPose.offsetAndRotation(2.0F, 6.0F, 0.0F, -0.4363323F, 0.0F, 0.0F));
        var BackpanelL1 = root.addOrReplaceChild("BackpanelL1", CubeListBuilder.create().texOffs(0, 18).addBox(-2.0F, 0.5F, 2.5F, 5, 3, 1), PartPose.offsetAndRotation(2.0F, 6.0F, 0.0F, 0.4363323F, 0.0F, 0.0F));
        var BackpanelL2 = root.addOrReplaceChild("BackpanelL2", CubeListBuilder.create().texOffs(0, 18).addBox(-2.0F, 2.5F, 1.5F, 5, 3, 1), PartPose.offsetAndRotation(2.0F, 6.0F, 0.0F, 0.4363323F, 0.0F, 0.0F));
        var SidepanelL1 = root.addOrReplaceChild("SidepanelL1", CubeListBuilder.create().texOffs(0, 22).addBox(1.5F, 0.5F, -2.5F, 1, 4, 5), PartPose.offsetAndRotation(2.0F, 6.0F, 0.0F, 0.0F, 0.0F, -0.4363323F));
        var SidepanelR1 = root.addOrReplaceChild("SidepanelR1", CubeListBuilder.create().texOffs(0, 22).addBox(-2.5F, 0.5F, -2.5F, 1, 4, 5), PartPose.offsetAndRotation(-2.0F, 6.0F, 0.0F, 0.0F, 0.0F, 0.4363323F));
        var SidepanelR2 = root.addOrReplaceChild("SidepanelR2", CubeListBuilder.create().texOffs(0, 54).addBox(0.0F, 0.0F, -0.5F, 1, 5, 5), PartPose.offsetAndRotation(-4.5F, 9.5F, -2.0F, 0.0F, 0.0F, 0.122173F));
        var SidepanelR3 = SidepanelR2.addOrReplaceChild("SidepanelR3", CubeListBuilder.create().texOffs(0, 35).addBox(0.0F, 0.0F, -0.5F, 1, 3, 5), PartPose.offsetAndRotation(0.0F, 5.0F, 0.0F, 0.0F, 0.0F, 0.296706F));
        var SidepanelR4 = SidepanelR3.addOrReplaceChild("SidepanelR4", CubeListBuilder.create().texOffs(24, 35).addBox(0.0F, 0.0F, -0.5F, 1, 3, 5), PartPose.offsetAndRotation(0.0F, 3.0F, 0.0F, 0.0F, 0.0F, (float) (Math.PI / 6)));
        var SidepanelL2 = root.addOrReplaceChild("SidepanelL2", CubeListBuilder.create().texOffs(0, 54).addBox(0.0F, 0.0F, -0.5F, 1, 5, 5), PartPose.offsetAndRotation(4.5F, 9.5F, -2.0F, 0.0F, 0.0F, -0.122173F));
        var SidepanelL3 = SidepanelL2.addOrReplaceChild("SidepanelL3", CubeListBuilder.create().texOffs(0, 35).addBox(0.0F, 0.0F, -0.5F, 1, 3, 5), PartPose.offsetAndRotation(0.0F, 5.0F, 0.0F, 0.0F, 0.0F, -0.296706F));
        var SidepanelL4 = SidepanelL3.addOrReplaceChild("SidepanelL4", CubeListBuilder.create().texOffs(24, 35).addBox(0.0F, 0.0F, -0.5F, 1, 3, 5), PartPose.offsetAndRotation(0.0F, 3.0F, 0.0F, 0.0F, 0.0F, (float) (-Math.PI / 6)));
        var LegpanelC1 = root.addOrReplaceChild("LegpanelC1", CubeListBuilder.create().texOffs(16, 45).addBox(-3.0F, 0.0F, -0.5F, 6, 8, 1), PartPose.offsetAndRotation(0.0F, 5.5F, -3.0F, 0.0F, 0.0F, 0.0F));
        var LegpanelC2 = LegpanelC1.addOrReplaceChild("LegpanelC2", CubeListBuilder.create().texOffs(16, 54).addBox(-3.0F, 0.0F, -0.5F, 6, 4, 1), PartPose.offsetAndRotation(0.0F, 8.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        var LegpanelC3 = LegpanelC2.addOrReplaceChild("LegpanelC3", CubeListBuilder.create().texOffs(32, 59).addBox(-3.0F, 0.0F, -0.5F, 6, 4, 1), PartPose.offsetAndRotation(0.0F, 4.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        var Cloak1 = root.addOrReplaceChild("Cloak1", CubeListBuilder.create().texOffs(106, 0).addBox(0.0F, 0.0F, -0.5F, 10, 18, 1), PartPose.offsetAndRotation(-5.0F, -6.0F, 4.0F, 0.0F, 0.0F, 0.0F));
        var Cloak2 = Cloak1.addOrReplaceChild("Cloak2", CubeListBuilder.create().texOffs(106, 19).addBox(0.0F, 0.0F, -0.5F, 10, 4, 1), PartPose.offsetAndRotation(0.0F, 18.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        var Cloak3 = Cloak2.addOrReplaceChild("Cloak3", CubeListBuilder.create().texOffs(106, 24).addBox(0.0F, 0.0F, -0.5F, 10, 4, 1), PartPose.offsetAndRotation(0.0F, 4.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 128, 64);
    }

    EldritchGuardianModel() { this(create().bakeRoot()); }
    EldritchGuardianModel(ModelPart root) {
        this.root = root;

        BeltR = root.getChild("BeltR");
        Mbelt = root.getChild("Mbelt");
        MbeltL = root.getChild("MbeltL");
        MbeltR = root.getChild("MbeltR");
        BeltL = root.getChild("BeltL");
        Chestplate = root.getChild("Chestplate");
        HoodEye = root.getChild("HoodEye");
        Hood1 = root.getChild("Hood1");
        Hood2 = Hood1.getChild("Hood2");
        Hood3 = Hood1.getChild("Hood3");
        Hood4 = Hood1.getChild("Hood4");
        Backplate = root.getChild("Backplate");
        ShoulderplateTopR = root.getChild("ShoulderplateTopR");
        ShoulderplateR1 = root.getChild("ShoulderplateR1");
        ShoulderplateR2 = root.getChild("ShoulderplateR2");
        ShoulderplateR3 = root.getChild("ShoulderplateR3");
        ShoulderR = root.getChild("ShoulderR");
        ArmL1 = root.getChild("ArmL1");
        ArmL2 = ArmL1.getChild("ArmL2");
        ArmL3 = ArmL1.getChild("ArmL3");
        ArmR1 = root.getChild("ArmR1");
        ArmR2 = ArmR1.getChild("ArmR2");
        ArmR3 = ArmR1.getChild("ArmR3");
        ShoulderL = root.getChild("ShoulderL");
        ShoulderplateLtop = root.getChild("ShoulderplateLtop");
        ShoulderplateL1 = root.getChild("ShoulderplateL1");
        ShoulderplateL2 = root.getChild("ShoulderplateL2");
        ShoulderplateL3 = root.getChild("ShoulderplateL3");
        LegpanelR4 = root.getChild("LegpanelR4");
        LegpanelR5 = root.getChild("LegpanelR5");
        LegpanelR6 = root.getChild("LegpanelR6");
        BackpanelR1 = root.getChild("BackpanelR1");
        BackpanelR2 = root.getChild("BackpanelR2");
        BackpanelR3 = root.getChild("BackpanelR3");
        BackpanelL3 = root.getChild("BackpanelL3");
        LegpanelL4 = root.getChild("LegpanelL4");
        LegpanelL5 = root.getChild("LegpanelL5");
        LegpanelL6 = root.getChild("LegpanelL6");
        BackpanelL1 = root.getChild("BackpanelL1");
        BackpanelL2 = root.getChild("BackpanelL2");
        SidepanelL1 = root.getChild("SidepanelL1");
        SidepanelR1 = root.getChild("SidepanelR1");
        SidepanelR2 = root.getChild("SidepanelR2");
        SidepanelR3 = SidepanelR2.getChild("SidepanelR3");
        SidepanelR4 = SidepanelR3.getChild("SidepanelR4");
        SidepanelL2 = root.getChild("SidepanelL2");
        SidepanelL3 = SidepanelL2.getChild("SidepanelL3");
        SidepanelL4 = SidepanelL3.getChild("SidepanelL4");
        LegpanelC1 = root.getChild("LegpanelC1");
        LegpanelC2 = LegpanelC1.getChild("LegpanelC2");
        LegpanelC3 = LegpanelC2.getChild("LegpanelC3");
        Cloak1 = root.getChild("Cloak1");
        Cloak2 = Cloak1.getChild("Cloak2");
        Cloak3 = Cloak2.getChild("Cloak3");
    }

    private static void rot(ModelPart part, float x, float y, float z) { part.xRot = x; part.yRot = y; part.zRot = z; }

    float partialTicks;
    /** The warden's glowing eye pass draws only the hood eye. */
    boolean eyeOnly;
    /** Guardians outside the Outer Lands are drawn half transparent. */
    float alpha = 1.0F;
    private ModelPart[] others() {
        return new ModelPart[] { BeltR, Mbelt, MbeltL, MbeltR, BeltL, Chestplate, Hood1, Backplate, ShoulderplateTopR, ShoulderplateR1, ShoulderplateR2,
                ShoulderplateR3, ShoulderR, ArmL1, ArmR1, ShoulderL, ShoulderplateLtop, ShoulderplateL1, ShoulderplateL2, ShoulderplateL3, LegpanelR4,
                LegpanelR5, LegpanelR6, BackpanelR1, BackpanelR2, BackpanelR3, BackpanelL3, LegpanelL4, LegpanelL5, LegpanelL6, BackpanelL1, BackpanelL2,
                Cloak1, SidepanelR1, SidepanelL1, SidepanelL2, SidepanelR2, LegpanelC1 };
    }


    @Override public void prepareMobModel(T entity, float limbSwing, float limbAmount, float partialTick) { this.partialTicks = partialTick; }
    @Override public void setupAnim(T entity, float par1, float par2, float age, float par4, float par5) {
        HoodEye.visible = eyeOnly; for (var part : others()) part.visible = !eyeOnly;
        Hood1.yRot = par4 * Mth.DEG_TO_RAD; Hood1.xRot = par5 * Mth.DEG_TO_RAD;
        HoodEye.xRot = Hood1.xRot; HoodEye.yRot = Hood1.yRot;
        float alr = 0.0F, all = 0.0F;
        if (entity instanceof github.com.gengyoubo.tcmodern.tc4.entity.EldritchGuardian guardian) { alr = guardian.armLiftR; all = guardian.armLiftL; }
        if (entity instanceof github.com.gengyoubo.tcmodern.tc4.entity.EldritchWarden warden) { alr = warden.armLiftR; all = warden.armLiftL; }
        float t = entity.tickCount + partialTicks;
        ArmL1.xRot = -1.0F - all + Mth.sin((t + 20) / 10.0F) * 0.08F;
        ArmR1.xRot = -1.0F - alr + Mth.sin(t / 10.0F) * 0.08F;
        LegpanelC1.xRot = -0.15F + Mth.sin(t / 8.0F) * 0.12F;
        LegpanelC2.xRot = Mth.sin((t - 5.0F) / 8.0F) * 0.13F;
        LegpanelC3.xRot = Mth.sin((t - 10.0F) / 8.0F) * 0.14F;
        Cloak1.xRot = 0.2F + Mth.sin(t / 7.0F) * 0.08F;
        Cloak2.xRot = Mth.sin((t - 5.0F) / 7.0F) * 0.1F;
        Cloak3.xRot = Mth.sin((t - 10.0F) / 7.0F) * 0.12F;
        SidepanelL2.zRot = -0.2F + Mth.sin((t + 10) / 8.0F) * 0.12F;
        SidepanelL3.zRot = Mth.sin((t + 5.0F) / 8.0F) * 0.13F;
        SidepanelL4.zRot = Mth.sin(t / 8.0F) * 0.14F;
        SidepanelR2.zRot = 0.2F + Mth.sin((t - 5) / 8.0F) * 0.12F;
        SidepanelR3.zRot = Mth.sin((t - 10.0F) / 8.0F) * 0.13F;
        SidepanelR4.zRot = Mth.sin((t - 15.0F) / 8.0F) * 0.14F;
    }

    @Override public void renderToBuffer(PoseStack pose, VertexConsumer vertices, int light, int overlay, float r, float g, float b, float a) {
        root.render(pose, vertices, light, overlay, r, g, b, a * alpha);
    }
}
