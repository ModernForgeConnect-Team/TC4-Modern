package github.com.gengyoubo.tcmodern.tc4.compat.curios;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.client.CuriosRendererRegistry;
import top.theillusivec4.curios.api.client.ICurioRenderer;

/**
 * Draws goggles worn in the Curios head slot like the vanilla armor layer draws them in the helmet
 * slot: the outer armor head model with the item's own armor texture (MFCL forwards the original
 * {@code getArmorTexture}). Skipped while a real helmet is worn, which is also when the goggles
 * are not handed to the original code.
 */
final class Tc4CuriosGogglesRenderer implements ICurioRenderer {
    private final Map<String, ResourceLocation> textures = new ConcurrentHashMap<>();
    private HumanoidModel<LivingEntity> model;

    private Tc4CuriosGogglesRenderer() { }

    /** Client only. Curios instantiates registered renderers when entity layers are (re)built. */
    static void register(Set<Item> items) {
        for (Item item : items) CuriosRendererRegistry.register(item, Tc4CuriosGogglesRenderer::new);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends LivingEntity, M extends EntityModel<T>> void render(ItemStack stack, SlotContext slotContext, PoseStack poseStack,
            RenderLayerParent<T, M> renderLayerParent, MultiBufferSource buffer, int light, float limbSwing, float limbSwingAmount,
            float partialTicks, float ageInTicks, float netHeadYaw, float headPitch) {
        LivingEntity entity = slotContext.entity();
        if (!entity.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) return;
        String path = stack.getItem().getArmorTexture(stack, entity, EquipmentSlot.HEAD, null);
        ResourceLocation texture = path == null ? null : textures.computeIfAbsent(path, ResourceLocation::tryParse);
        if (texture == null) return;
        if (model == null) model = new HumanoidModel<>(Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR));
        if (renderLayerParent.getModel() instanceof HumanoidModel<?> parent) {
            ((HumanoidModel<LivingEntity>) parent).copyPropertiesTo(model);
        } else {
            ICurioRenderer.followHeadRotations(entity, model.head, model.hat);
        }
        model.setAllVisible(false);
        model.head.visible = true;
        model.hat.visible = true;
        var consumer = ItemRenderer.getArmorFoilBuffer(buffer, RenderType.armorCutoutNoCull(texture), false, stack.hasFoil());
        model.renderToBuffer(poseStack, consumer, light, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);
    }
}
