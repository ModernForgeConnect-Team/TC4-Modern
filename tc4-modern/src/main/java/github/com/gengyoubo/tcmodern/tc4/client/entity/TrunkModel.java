package github.com.gengyoubo.tcmodern.tc4.client.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import github.com.gengyoubo.tcmodern.tc4.entity.TravelingTrunk;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.util.Mth;

/** The original ModelTrunk: a chest whose lid chatters as it hops and gapes while open. */
final class TrunkModel extends EntityModel<TravelingTrunk> {
    private final ModelPart root;
    final ModelPart lid, knob;

    static LayerDefinition create() {
        var mesh = new MeshDefinition();
        var root = mesh.getRoot();
        root.addOrReplaceChild("lid", CubeListBuilder.create().texOffs(0, 0).addBox(0, -5, -14, 14, 5, 14), PartPose.offset(1, 7, 15));
        root.addOrReplaceChild("knob", CubeListBuilder.create().texOffs(0, 0).addBox(-1, -2, -15, 2, 4, 1), PartPose.offset(8, 7, 15));
        root.addOrReplaceChild("base", CubeListBuilder.create().texOffs(0, 19).addBox(0, 0, 0, 14, 10, 14), PartPose.offset(1, 6, 1));
        return LayerDefinition.create(mesh, 64, 64);
    }

    TrunkModel() { root = create().bakeRoot(); lid = root.getChild("lid"); knob = root.getChild("knob"); }

    @Override public void setupAnim(TravelingTrunk trunk, float limbSwing, float limbAmount, float age, float headYaw, float headPitch) {
        float partial = age - trunk.tickCount;
        float open = 1.0F - trunk.lidrot;
        open = 1.0F - open * open * open;
        lid.xRot = -(open * Mth.PI / 2.0F);
        knob.xRot = lid.xRot;
    }

    @Override public void renderToBuffer(PoseStack pose, VertexConsumer vertices, int light, int overlay, float r, float g, float b, float a) {
        root.render(pose, vertices, light, overlay, r, g, b, a);
    }
}
