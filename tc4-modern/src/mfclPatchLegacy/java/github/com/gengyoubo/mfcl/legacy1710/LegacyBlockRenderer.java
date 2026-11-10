package github.com.gengyoubo.mfcl.legacy1710;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Runs the real old inventory block renderer inside the private legacy domain. The host wraps the call in a
 * {@code LegacyWorldGraphics} capture, so the resulting geometry never touches the host OpenGL context.
 * Reflection keeps this source set free of Minecraft compile-time types.
 */
public final class LegacyBlockRenderer {
    private static final int STAIRS = 10;
    public boolean renderInventory(String blockKey, int metadata, int modelId) throws Exception {
        ClassLoader loader = getClass().getClassLoader();
        Class<?> blockType = loader.loadClass("net.minecraft.block.Block");
        Object registry = blockType.getField("field_149771_c").get(null);
        Object block = registry.getClass().getMethod("func_82594_a", String.class).invoke(registry, blockKey);
        if (block == null) return false;
        int renderType = (Integer) blockType.getMethod("func_149645_b").invoke(block);
        Class<?> renderingType = loader.loadClass("cpw.mods.fml.client.registry.RenderingRegistry");
        Object rendering = renderingType.getMethod("instance").invoke(null);
        if (rendering == null) return false;
        Class<?> renderBlocksType = loader.loadClass("net.minecraft.client.renderer.RenderBlocks");
        Class<?> accessType = loader.loadClass("net.minecraft.world.IBlockAccess");
        Object air = loader.loadClass("net.minecraft.init.Blocks").getField("field_150350_a").get(null);
        Object access = Proxy.newProxyInstance(loader, new Class<?>[]{accessType}, (proxy, method, args) -> {
            if (method.getName().equals("func_147439_a")) return air;
            Class<?> result = method.getReturnType();
            if (result == boolean.class) return false;
            if (result == int.class) return 0;
            if (result == float.class) return 0f;
            if (result == double.class) return 0d;
            if (result == long.class) return 0L;
            return null;
        });
        Object renderBlocks = renderBlocksType.getConstructor(accessType).newInstance(access);
        if (renderType == STAIRS) {
            // The FML registry only knows mod renderers; vanilla draws the two stair cuboids in RenderBlocks itself.
            renderBlocksType.getMethod("func_147800_a", blockType, int.class, float.class).invoke(renderBlocks, block, metadata, 1f);
            return true;
        }
        Method render = renderingType.getMethod("renderInventoryBlock", renderBlocksType, blockType, int.class, int.class);
        render.invoke(rendering, renderBlocks, block, metadata, renderType);
        return true;
    }
}
