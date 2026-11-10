package github.com.gengyoubo.mfcl.bridge;

import java.lang.reflect.*;
import java.util.*;

/** Captures original block and tile renderers against a read-only view of the host world. */
public final class LegacyBlockRendering {
    @FunctionalInterface public interface View {
        HostWorldAccess.Block block(int x, int y, int z);
        default byte[] tileData(int x, int y, int z) { return null; }
        default long time() { return 0; }
        default String biome(int x,int z) { return "minecraft:plains"; }
        /** False when the host position has no original tile, so old neighbour lookups see what the live world shows them. */
        default boolean legacyTile(int x, int y, int z) { return true; }
        /** FE connections of a host tile without an original counterpart; null when it has none. */
        default HostEnergyAccess energy(int x, int y, int z) { return null; }
        /** A partial world-generation view must not populate or reuse the shared shape cache. */
        default boolean cacheable() { return true; }
    }
    public record Layer(int pass, List<LegacyVertexStream.Primitive> geometry) {
        public Layer { geometry = List.copyOf(geometry); }
    }
    /** Render type 0 draws the block's bounds, which need not fill a voxel (e.g. XU's Magnum Torch). */
    public record Cuboid(double minX,double minY,double minZ,double maxX,double maxY,double maxZ) {
        public static final Cuboid FULL=new Cuboid(0,0,0,1,1,1);
        public boolean full(){return equals(FULL);}
    }
    private final Map<String,Cuboid> cuboids=new HashMap<>(),inventoryCuboids=new HashMap<>();
    private final LegacyClientData data;
    private final LegacyServices services;
    private final LegacyTileRendering tiles;
    private final Map<String, Set<Integer>> tileSupported = new LinkedHashMap<>();
    private final List<LegacyVertexStream.Primitive> tilePreviews = new ArrayList<>();
    private final Class<?> blockType, accessType, rendererType;
    private final Object registry, rendering, tessellator;
    private final Method lookup, render, start, draw, canRender, getPass, setPass;
    private final Map<String, Set<Integer>> supported = new LinkedHashMap<>();
    private final Map<String, List<Layer>> isolated = new HashMap<>();
    private final Map<String, Object> blockCache = new HashMap<>();
    private final List<String> diagnostics = new ArrayList<>();

    public LegacyBlockRendering(LegacyServices services, LegacyContent content) throws Exception {
        this.services = services; this.data = services.items();
        this.tiles = new LegacyTileRendering(services);
        ClassLoader loader = data.loader();
        blockType = loader.loadClass("net.minecraft.block.Block"); accessType = loader.loadClass("net.minecraft.world.IBlockAccess");
        rendererType = loader.loadClass("net.minecraft.client.renderer.RenderBlocks");
        registry = blockType.getField("field_149771_c").get(null);
        lookup = registry.getClass().getMethod("func_82594_a", String.class);
        Class<?> renderingType = loader.loadClass("cpw.mods.fml.client.registry.RenderingRegistry");
        rendering = renderingType.getMethod("instance").invoke(null);
        render = rendererType.getMethod("func_147805_b", blockType, int.class, int.class, int.class);
        Class<?> tessType = loader.loadClass("net.minecraft.client.renderer.Tessellator");
        tessellator = tessType.getField("field_78398_a").get(null);
        start = tessType.getMethod("func_78382_b"); draw = tessType.getMethod("func_78381_a");
        canRender = blockType.getMethod("canRenderInPass", int.class);
        getPass = loader.loadClass("net.minecraftforge.client.MinecraftForgeClient").getMethod("getRenderPass");
        setPass = loader.loadClass("net.minecraftforge.client.ForgeHooksClient").getMethod("setRenderPass", int.class);
        Class<?> dispatcherType = loader.loadClass("net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher");
        Object dispatcher = dispatcherType.getField("field_147556_a").get(null);
        Method special = dispatcherType.getMethod("func_147547_b", loader.loadClass("net.minecraft.tileentity.TileEntity"));
        for (var declaration : content.blocks()) {
            if (declaration.renderType() == 0) {
                captureCuboids(declaration.key());continue;
            }
            Set<Integer> metas = new TreeSet<>();
            for (var item : content.items()) if (declaration.key().equals(item.blockKey()))
                for (var variant : item.variants()) metas.add(variant.metadata());
            Set<Integer> catalog = Set.copyOf(metas);
            // Placement callbacks encode facing, upper halves and axes in metadata absent from the item catalog.
            if (!metas.isEmpty()) for (int meta = 0; meta < 16; meta++) metas.add(meta);
            for (int meta : metas) {
                if (meta < 0 || meta > 15) continue;
                try {
                    data.inLegacy(() -> {
                        Object block = block(declaration.key());
                        View view = previewView(declaration.key(), meta, declaration.renderType());
                        // TESRs and non-cube collision/placement need their own bridge. Do not publish a partial model.
                        boolean hasTile = false;
                        try (var scene = new LegacyBlockScene(services, view)) {
                            Object tile = loader.loadClass("net.minecraft.world.World").getMethod("func_147438_o", int.class,int.class,int.class).invoke(scene.world(),0,0,0);
                            if (tiles.hasRenderer(tile)) {
                                var preview = tiles.capture(tile, 0);
                                tilePreviews.addAll(preview); hasTile = true;
                            }
                        }
                        List<Layer> layers;
                        try { layers = capture(declaration.key(), meta, view); }
                        catch (Exception failure) { if (!hasTile) throw failure; layers = List.of(); }
                        boolean invisibleTechnical=declaration.renderType()==-1&&content.items().stream().filter(item->declaration.key().equals(item.blockKey())).noneMatch(LegacyContent.Item::creative);
                        // Only original code places metadata no item carries. One that renders nothing is a structure part
                        // drawn by a neighbour's TESR (TC4 infusion pillar and thaumatorium tops), not a missing model.
                        boolean invisiblePart=!catalog.contains(meta);
                        if (layers.isEmpty() && !hasTile && !invisibleTechnical && !invisiblePart) return null;
                        for (var layer : layers) for (var primitive : layer.geometry()) {
                            if (primitive.mode() != LegacyVertexStream.GL_QUADS || primitive.vertices().size() % 4 != 0) return null;
                            if (!data.iconLayout().containsKey(primitive.texture()) || primitive.texture().startsWith("MISSING_ICON_")) return null;
                        }
                        supported.computeIfAbsent(declaration.key(), ignored -> new TreeSet<>()).add(meta);
                        if (hasTile) tileSupported.computeIfAbsent(declaration.key(), ignored -> new TreeSet<>()).add(meta);
                        isolated.put(declaration.key() + "@" + meta, layers);
                        return null;
                    });
                } catch (Exception failure) {
                    Throwable cause = failure; while (cause.getCause() != null) cause = cause.getCause();
                    diagnostics.add(declaration.key() + "@" + meta + ": " + cause);
                }
            }
        }
    }
    public boolean supports(String key, int metadata) { return supported.getOrDefault(key, Set.of()).contains(metadata); }
    /** Any placed form renders; the item's own metadata need not be one (vines: meta 0 has no attachment side). */
    public boolean supportsAny(String key) { return !supported.getOrDefault(key, Set.of()).isEmpty(); }
    public Cuboid cuboid(String key,int metadata){return cuboids.getOrDefault(key+"@"+metadata,Cuboid.FULL);}
    public Cuboid inventoryCuboid(String key,int metadata){return inventoryCuboids.getOrDefault(key+"@"+metadata,Cuboid.FULL);}
    private void captureCuboids(String key) {
        for(int metadata=0;metadata<16;metadata++) {
            int meta=metadata;
            try {
                data.inLegacy(()->{
                    Object block=block(key);
                    String[] names={"field_149759_B","field_149760_C","field_149754_D","field_149755_E","field_149756_F","field_149757_G"};
                    Field[] fields=new Field[6];double[] saved=new double[6];
                    for(int i=0;i<6;i++){fields[i]=blockType.getDeclaredField(names[i]);fields[i].setAccessible(true);saved[i]=fields[i].getDouble(block);}
                    try(var scene=new LegacyBlockScene(services,isolatedView(key,meta))) {
                        blockType.getMethod("func_149683_g").invoke(block);
                        inventoryCuboids.put(key+"@"+meta,readCuboid(block,fields));
                        for(int i=0;i<6;i++)fields[i].setDouble(block,saved[i]);
                        blockType.getMethod("func_149719_a",accessType,int.class,int.class,int.class).invoke(block,scene.world(),0,0,0);
                        cuboids.put(key+"@"+meta,readCuboid(block,fields));
                    }finally{for(int i=0;i<6;i++)fields[i].setDouble(block,saved[i]);}
                    return null;
                });
            }catch(Exception failure){
                Throwable cause=failure;while(cause.getCause()!=null)cause=cause.getCause();
                diagnostics.add(key+"@"+meta+" cuboid bounds: "+cause);
            }
        }
    }
    private static Cuboid readCuboid(Object block,Field[] fields) throws IllegalAccessException {
        double[] value=new double[6];
        for(int i=0;i<6;i++){
            value[i]=fields[i].getDouble(block);
            if(!Double.isFinite(value[i])||value[i]<-1||value[i]>2)throw new UnsupportedOperationException("Original cuboid exceeds host model bounds");
        }
        if(value[0]>value[3]||value[1]>value[4]||value[2]>value[5])throw new UnsupportedOperationException("Invalid original cuboid bounds");
        return new Cuboid(value[0],value[1],value[2],value[3],value[4],value[5]);
    }
    public boolean hasTileRenderer(String key, int metadata) { return tileSupported.getOrDefault(key, Set.of()).contains(metadata); }
    public LegacyTileRendering tiles() { return tiles; }
    public List<LegacyVertexStream.Primitive> tilePreviews() { return List.copyOf(tilePreviews); }
    public Map<String, Set<Integer>> supported() { return Collections.unmodifiableMap(supported); }
    public List<String> diagnostics() { return List.copyOf(diagnostics); }
    public List<Layer> isolated(String key, int metadata) { return isolated.getOrDefault(key + "@" + metadata, List.of()); }
    public static View isolatedView(String key, int metadata) {
        return (x,y,z) -> new HostWorldAccess.Block(x == 0 && y == 0 && z == 0 ? key : "minecraft:air", x == 0 && y == 0 && z == 0 ? metadata : 0);
    }
    private static View previewView(String key, int metadata, int renderType) {
        var isolated = isolatedView(key, metadata);
        // The vanilla door renderer deliberately skips orphan halves.
        return (x,y,z) -> renderType == 7 && x == 0 && z == 0 && y == (metadata < 8 ? 1 : -1)
                ? new HostWorldAccess.Block(key, metadata < 8 ? 8 : 0) : isolated.block(x,y,z);
    }
    public List<Layer> capture(String key, int metadata, View view) throws Exception {
        return data.inLegacy(() -> {
            Object block = block(key);
            try (var scene = new LegacyBlockScene(services, view)) {
            Object access = scene.world();
            Object renderer = rendererType.getConstructor(accessType).newInstance(access);
            int model = (int)blockType.getMethod("func_149645_b").invoke(block);
            int previousPass = (int)getPass.invoke(null);
            var result = new ArrayList<Layer>();
            try {
                for (int pass = 0; pass <= 1; pass++) {
                    if (!(boolean)canRender.invoke(block, pass)) continue;
                    setPass.invoke(null, pass);
                    try (var frame = LegacyWorldGraphics.begin()) {
                        LegacyWorldGraphics.texture("minecraft:textures/atlas/blocks.png");
                        start.invoke(tessellator);
                        try { render.invoke(renderer, block, 0, 0, 0); }
                        finally { draw.invoke(tessellator); }
                        var geometry = data.resolveIcons(frame.primitives());
                        if (!geometry.isEmpty()) result.add(new Layer(pass, geometry));
                    }
                }
            } finally {
                try { canRender.invoke(block, previousPass < 0 ? 0 : previousPass); }
                finally { setPass.invoke(null, previousPass); }
            }
            return List.copyOf(result);
            }
        });
    }
    private Object block(String key) throws Exception {
        Object value = blockCache.get(key);
        if (value == null) { value = lookup.invoke(registry, key); blockCache.put(key, value); }
        return value;
    }
    private Object access(View view) {
        return Proxy.newProxyInstance(data.loader(), new Class<?>[]{accessType}, (proxy, method, args) -> {
            String name = method.getName();
            if (name.equals("toString")) return "MFCL read-only render region";
            if (name.equals("hashCode")) return System.identityHashCode(proxy);
            if (name.equals("equals")) return proxy == args[0];
            if (name.equals("func_147439_a")) return block(view.block((int)args[0], (int)args[1], (int)args[2]).legacyKey());
            if (name.equals("func_72805_g")) return view.block((int)args[0], (int)args[1], (int)args[2]).metadata();
            if (name.equals("func_147438_o")) return null; // Supported renderers have no TESR or mutable tile dependency.
            if (name.equals("func_72802_i")) return 0x00F000F0; // Host chunk lighting is applied to the baked quads.
            if (name.equals("func_72800_K")) return 256;
            if (name.equals("func_72806_s")) return view.block((int)args[0], (int)args[1], (int)args[2]).legacyKey().equals("minecraft:air");
            if (name.equals("func_72807_a")) return services.biomes().resolve(view.biome((int)args[0],(int)args[1]));
            if (name.equals("func_72879_k")) return false;
            if (name.equals("isSideSolid")) {
                Object neighbor = block(view.block((int)args[0], (int)args[1], (int)args[2]).legacyKey());
                return blockType.getMethod("func_149662_c").invoke(neighbor);
            }
            if (name.equals("func_72849_a")) return 0;
            throw new UnsupportedOperationException("Unmapped read-only block access: " + method);
        });
    }
}
