package github.com.gengyoubo.tcmodern.tc4.compat.curios;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.onixary.shapeShifterCurseForge.form.FormManager;

/** Loaded only when SSC is present. */
final class Tc4SscGoggles {
    private Tc4SscGoggles() { }
    static void align(net.minecraft.client.model.HumanoidModel<?> model, Player player) {
        boolean sneaking = net.onixary.shapeShifterCurseForge.power.MovementPowerService.isSneakingOrForced(player);
        float down = sneaking ? 1.0F : 2.0F;
        float forward = sneaking ? -0.5F : -2.0F;
        move(model.head, down, forward);
        move(model.hat, down, forward);
    }
    private static void move(net.minecraft.client.model.geom.ModelPart part, float down, float forward) {
        var offset = new org.joml.Vector3f(0.0F, down, forward);
        new org.joml.Quaternionf().rotationZYX(part.zRot, part.yRot, part.xRot).transform(offset);
        part.x += offset.x; part.y += offset.y; part.z += offset.z;
    }
    static boolean isTransformed(LivingEntity entity) {
        if (!(entity instanceof Player player)) return false;
        var form = FormManager.current(player);
        return form.stage() == 4;
    }
}
