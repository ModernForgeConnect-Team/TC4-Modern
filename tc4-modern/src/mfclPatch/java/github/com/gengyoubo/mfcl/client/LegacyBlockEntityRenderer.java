package github.com.gengyoubo.mfcl.client;

import com.mojang.blaze3d.vertex.PoseStack;
import github.com.gengyoubo.mfcl.bridge.LegacyVertexStream;
import github.com.gengyoubo.mfcl.content.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import java.util.*;

/** Dynamic models use each placed tile's real NBT/facing, independent from inventory captures. */
public final class LegacyBlockEntityRenderer implements BlockEntityRenderer<LegacyBlockEntity> {
    private final ModernContentBridge bridge;
    private final Map<LegacyBlockEntity, Long> retryAfter = new WeakHashMap<>();
    public LegacyBlockEntityRenderer(ModernContentBridge bridge) { this.bridge = bridge; }
    @Override public void render(LegacyBlockEntity tile, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if(tile.isRemoved()||tile.getLevel()==null||tile.getLevel()!=Minecraft.getInstance().level)return;
        var state = tile.getBlockState(); var cell = bridge.legacyBlock(state);
        if (!bridge.blockRendering().hasTileRenderer(cell.legacyKey(), cell.metadata())) return;
        if (System.nanoTime() < retryAfter.getOrDefault(tile, 0L)) return;
        try {
            var mc = Minecraft.getInstance(); var camera = mc.gameRenderer.getMainCamera(); var eye = camera.getPosition();
            bridge.blockRendering().tiles().view(bridge.services().worlds().bound(tile.getLevel()),eye.x,eye.y,eye.z,camera.getYRot(),camera.getXRot(),mc.player == null ? 0 : mc.player.tickCount,
                    mc.player==null?null:bridge.renderSnapshot(mc.player.getMainHandItem()));
            var pos = tile.getBlockPos();
            var primitives = bridge.blockRendering().tiles().capture(tile.legacyOrNull(), partial,pos.getX()-eye.x,pos.getY()-eye.y,pos.getZ()-eye.z);
            render(primitives, pose, buffers, light, overlay);
            retryAfter.remove(tile);
        } catch (Exception | LinkageError failure) {
            // A single incompatible model must not make a saved world impossible to open or repair.
            // The acceptance probe invokes capture directly so failures still fail verification.
            retryAfter.put(tile, System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5));
            com.mojang.logging.LogUtils.getLogger().error("Cannot render placed legacy tile {} at {}; retrying in 5 seconds",cell,tile.getBlockPos(),failure);
        }
    }
    public void render(List<LegacyVertexStream.Primitive> primitives, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        var atlas = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS);
        int first = 0;
        while (first < primitives.size()) {
            var primitive = primitives.get(first); int end = first + 1;
            while (end < primitives.size() && primitives.get(end).texture().equals(primitive.texture()) && primitives.get(end).state().equals(primitive.state())) end++;
            var group = primitives.subList(first, end);
            boolean standalone = primitive.texture().contains("textures/");
            var texture = standalone ? ResourceLocation.parse(bridge.pack().worldResource(primitive.texture())) : InventoryMenu.BLOCK_ATLAS;
            var type = LegacyRenderTypes.get(texture, primitive.state());
            int brightness = primitive.state().blend() && primitive.state().destinationBlend() == 1 ? 0xF000F0 : light;
            if (standalone) LegacyWorldRenderer.replayStandalone(pose, buffers.getBuffer(type), group, brightness, overlay);
            else LegacyWorldRenderer.replay(pose, buffers.getBuffer(type), atlas.apply(ResourceLocation.parse(bridge.pack().worldTexture(primitive.texture()))), group, brightness, overlay);
            // State changes may include a depth-write barrier; preserve the source batch order.
            if (buffers instanceof MultiBufferSource.BufferSource immediate) immediate.endBatch(type);
            first = end;
        }
    }
    @Override public boolean shouldRenderOffScreen(LegacyBlockEntity tile) { return true; }
}
