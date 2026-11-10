package github.com.gengyoubo.mfcl.bridge;

import java.lang.reflect.*;
import java.util.*;

/** Frozen declarations from the real old registries. No Minecraft types cross the runtime boundary. */
public final class LegacyContent {
    public record Layer(String icon, int color) {}
    public record Variant(int metadata, String nameKey, String icon, int color, List<Layer> layers) {
        public Variant { layers = List.copyOf(layers); }
    }
    /**
     * Original ItemArmor: slot (0 helmet .. 3 boots, as in 1.7.10), armor points (ISpecialArmor's displayed value
     * when it has one), enchantability, the worn layer textures as old resource locations (overlay may be null),
     * whether it is dyed like leather, and whether it overrides onArmorTick.
     */
    public record Armor(int slot, int protection, int enchantability, String texture, String overlay, boolean dyeable, boolean ticks) {}
    /** Held-use icon frame: from {@code ticks} of drawing on, the original shows these per-pass icons (bows). */
    public record Pull(int ticks, List<String> icons) {
        public Pull { icons = List.copyOf(icons); }
    }
    /** full3D/rodRotation: original Item.isFull3D and shouldRotateAroundWhenRendering (held upright like a tool or a rod). */
    public record Item(String key, String blockKey, int stackLimit, int maxDamage, boolean creative, List<Variant> variants, Armor armor, List<Pull> pulls,
                       boolean full3D, boolean rodRotation) {
        public Item { variants = List.copyOf(variants); pulls = List.copyOf(pulls); }
    }
    public record Sound(float volume,float pitch,String breaking,String stepping,String placing) {}
    public record Block(String key, float hardness, float resistance, int light, boolean opaque,
                        boolean tileEntity, int renderType, boolean inventory3D, List<List<String>> faces, List<Integer> tileMetas, boolean randomTicks, Sound sound) {
        public Block {
            faces = faces.stream().map(List::copyOf).toList();
            tileMetas = List.copyOf(tileMetas);
        }
        public boolean simple() { return renderType == 0 && !tileEntity; }
        public boolean hasTile(int metadata) { return tileMetas.contains(metadata); }
        /** Whether the original inventory model is a block cube instead of a flat sprite. */
        public boolean blockModel() { return inventory3D; }
    }
    private final LegacyClientData data;
    private final List<Item> items;
    private final List<Block> blocks;
    private final List<LegacyCreativeTabs.Tab> tabs;
    private final Map<String, Object> oldBlocks = new HashMap<>();
    private final Map<String,List<String>> miningTags = new LinkedHashMap<>();
    private final Set<String> toolRequired = new HashSet<>();
    private final Map<String, List<LegacyVertexStream.Primitive>> geometry = new LinkedHashMap<>();
    /** IItemRenderer geometry per {@code ItemRenderType}, so held/entity models can differ from the inventory snapshot. */
    private final Map<String, List<LegacyVertexStream.Primitive>> contextGeometry = new LinkedHashMap<>();
    private final Set<String> customItems = new LinkedHashSet<>();
    private final List<String> captureDiagnostics = new ArrayList<>();
    /** Live captures for NBT no catalog variant had, most recently used last. */
    private final Map<String, List<LegacyVertexStream.Primitive>> liveGeometry = new LinkedHashMap<>(64, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, List<LegacyVertexStream.Primitive>> eldest) { return size() > 256; }
    };
    private final Set<String> liveFailures = new HashSet<>();

    public LegacyContent(LegacyClientData data) throws Exception {
        this.data = data;
        progress("Reading legacy blocks, item variants and creative tabs");
        var declarations = data.inLegacy(() -> read());
        items = declarations.items; blocks = declarations.blocks; tabs = declarations.tabs;
        progress("Capturing inventory block models (" + blocks.size() + " blocks)");
        captureStaticGeometry();
        progress("Capturing custom item renderers");
        captureItemRenderers();
    }
    private static void progress(String message) {
        com.mojang.logging.LogUtils.getLogger().info("MFCL content: {}", message);
        try { net.minecraftforge.fml.loading.progress.StartupNotificationManager.addModMessage("MFCL: " + message); }
        catch (RuntimeException | LinkageError unavailable) { /* CLI probes have no host loading screen. */ }
    }

    /**
     * The original IItemRenderer drawing for a stack whose NBT no catalog variant carried (a wand holding a focus, a
     * crafted rod/cap combination...). Cached per NBT; empty when the stack has no renderer or its renderer failed.
     */
    public synchronized List<LegacyVertexStream.Primitive> captureLive(LegacyClientData.Stack snapshot, String typeName) {
        String key = typeName + "|" + geometryKey(snapshot.key(), snapshot.metadata(), snapshot.tag());
        var cached = liveGeometry.get(key);
        if (cached != null) return cached;
        List<LegacyVertexStream.Primitive> captured = List.of();
        try {
            captured = data.inLegacy(() -> {
                ClassLoader loader = data.loader();
                Class<?> renderType = loader.loadClass("net.minecraftforge.client.IItemRenderer$ItemRenderType");
                Object type = renderType.getField(typeName).get(null), stack = data.materialize(snapshot);
                Object renderer = loader.loadClass("net.minecraftforge.client.MinecraftForgeClient").getMethod("getItemRenderer", loader.loadClass("net.minecraft.item.ItemStack"), renderType).invoke(null, stack, type);
                if (renderer == null) return List.<LegacyVertexStream.Primitive>of();
                try (var frame = LegacyWorldGraphics.begin()) {
                    new LegacyItemRendering(loader).render(type, renderer, stack);
                    return resolveIcons(frame.primitives());
                }
            });
        } catch (Exception | LinkageError failure) {
            // A failed capture stays empty for this NBT instead of failing every frame.
            if (liveFailures.add(snapshot.key() + "|" + typeName)) com.mojang.logging.LogUtils.getLogger().error("MFCL cannot capture original {} renderer of {}", typeName, snapshot, failure);
        }
        liveGeometry.put(key, captured);
        return captured;
    }
    /** Executes the real old Forge IItemRenderer (wands, banners, ...) and keeps its captured geometry. */
    private void captureItemRenderers() {
        try {
            ClassLoader loader = data.loader();
            Class<?> forgeClient = loader.loadClass("net.minecraftforge.client.MinecraftForgeClient");
            Class<?> rendererType = loader.loadClass("net.minecraftforge.client.IItemRenderer");
            Class<?> renderType = loader.loadClass("net.minecraftforge.client.IItemRenderer$ItemRenderType");
            Class<?> stackType = loader.loadClass("net.minecraft.item.ItemStack");
            Method getRenderer = forgeClient.getMethod("getItemRenderer", stackType, renderType);
            var pipeline = new LegacyItemRendering(loader);
            var renderTypes = new ArrayList<Object>();
            for (String name : List.of("INVENTORY", "EQUIPPED", "EQUIPPED_FIRST_PERSON", "ENTITY")) renderTypes.add(renderType.getField(name).get(null));
            Object inventory = renderType.getField("INVENTORY").get(null);
            var stacks = new ArrayList<LegacyClientData.Stack>(data.catalog());
            var seen = new HashSet<RenderStack>();
            for (var stack : stacks) seen.add(RenderStack.of(stack));
            for (var tab : tabs) {
                for (var entry : tab.contents()) if (seen.add(RenderStack.of(entry))) stacks.add(entry);
                if (tab.icon() != null && seen.add(RenderStack.of(tab.icon()))) stacks.add(tab.icon());
            }
            int processed = 0;
            for (var snapshot : stacks) {
                if (processed++ % 2048 == 0) progress("Capturing custom item renderers (" + processed + "/" + stacks.size() + ")");
                if (snapshot.key().startsWith("minecraft:")) continue;
                Object stack = data.materialize(snapshot); // keeps the original NBT, e.g. a wand's rod and cap
                Object inventoryRenderer = getRenderer.invoke(null, stack, inventory);
                if (inventoryRenderer == null) { if (snapshot.key().contains("Wand")) captureDiagnostics.add("no item renderer for " + snapshot.key() + "@" + snapshot.metadata()); continue; }
                for (Object renderTypeValue : renderTypes) {
                    String typeName = ((Enum<?>)renderTypeValue).name();
                    Object renderer = getRenderer.invoke(null, stack, renderTypeValue);
                    if (renderer == null) continue;
                    LegacyWorldGraphics.Frame frame = LegacyWorldGraphics.begin();
                    boolean complete = false;
                    try { data.inLegacy(() -> { pipeline.render(renderTypeValue, renderer, stack); return null; }); complete = true; }
                    catch (Throwable failure) {
                        Throwable cause = failure;
                        while (cause.getCause() != null && cause != cause.getCause()) cause = cause.getCause();
                        captureDiagnostics.add(typeName + " item renderer failed " + snapshot.key() + "@" + snapshot.metadata() + ": " + cause);
                    }
                    List<LegacyVertexStream.Primitive> captured;
                    try { captured = frame.primitives(); }
                    finally {
                        try { frame.close(); }
                        catch (IllegalStateException failure) { complete = false; captureDiagnostics.add("item capture failed to close " + snapshot.key() + "@" + snapshot.metadata() + ": " + failure); }
                    }
                    if (!complete) continue; // An exception may leave only half of a model in the frame.
                    captured = resolveIcons(captured);
                    if (typeName.equals("INVENTORY") && snapshot.key().contains("Wand")) captureDiagnostics.add("wand renderer " + snapshot.key() + "@" + snapshot.metadata() + " -> " + captured.size() + " primitives");
                    if (captured.isEmpty()) continue;
                    String key = geometryKey(snapshot.key(), snapshot.metadata(), snapshot.tag());
                    contextGeometry.put(typeName + "|" + key, captured);
                    if (typeName.equals("INVENTORY")) {
                        geometry.put(key, captured);
                        captureDiagnostics.add("captured item renderer " + snapshot.key() + "@" + snapshot.metadata() + ": " + captured.size() + " primitives, "
                                + captured.stream().mapToInt(p -> p.vertices().size()).sum() + " vertices");
                        customItems.add(snapshot.key());
                    } else captureDiagnostics.add("captured " + typeName + " " + snapshot.key() + "@" + snapshot.metadata() + ": " + captured.size() + " primitives, bounds " + bounds(captured));
                }
            }
        } catch (Throwable unavailable) { captureDiagnostics.add("item renderer bridge unavailable: " + unavailable); }
    }
    private record RenderStack(String key, int metadata, String tag) {
        static RenderStack of(LegacyClientData.Stack stack) { return new RenderStack(stack.key(), stack.metadata(), stack.tag()); }
    }

    private static final int STAIRS_RENDER_TYPE = 10;
    /** Executes the real old inventory renderers once and keeps their geometry; texture stays the icon name. */
    private void captureStaticGeometry() {
        Object renderer;
        Method render;
        try {
            Class<?> type = data.loader().loadClass("github.com.gengyoubo.mfcl.legacy1710.LegacyBlockRenderer");
            renderer = type.getConstructor().newInstance();
            render = type.getMethod("renderInventory", String.class, int.class, int.class);
        } catch (Exception unavailable) { return; }
        for (var block : blocks) {
            if (block.renderType() == 0 || block.renderType() < 0) continue;
            for (var item : items) {
                if (!block.key().equals(item.blockKey())) continue;
                boolean rendered = false;
                for (var variant : item.variants()) {
                    LegacyWorldGraphics.Frame frame = LegacyWorldGraphics.begin();
                    boolean drawn = false;
                    // Vanilla's old GUI frame nets Y 315 for stairs; the host block transform applies Y 225.
                    if (block.renderType() == STAIRS_RENDER_TYPE) LegacyWorldGraphics.rotate(90, 0, 1, 0);
                    try { drawn = Boolean.TRUE.equals(data.inLegacy(() -> render.invoke(renderer, block.key(), variant.metadata(), 0))); }
                    catch (Throwable failure) {
                        Throwable cause = failure;
                        while (cause.getCause() != null && cause != cause.getCause()) cause = cause.getCause();
                        captureDiagnostics.add("capture failed " + block.key() + "@" + variant.metadata() + " renderType=" + block.renderType() + ": " + cause);
                    }
                    List<LegacyVertexStream.Primitive> captured;
                    try { captured = frame.primitives(); }
                    finally {
                        try { frame.close(); }
                        catch (IllegalStateException failure) { drawn = false; captureDiagnostics.add("block capture failed to close " + block.key() + "@" + variant.metadata() + ": " + failure); }
                    }
                    if (!drawn || captured.isEmpty()) continue;
                    var resolved = LegacyVertexStream.correctSwappedSideNormals(resolveIcons(captured));
                    if (resolved.isEmpty()) { captureDiagnostics.add("unresolved icons " + block.key() + "@" + variant.metadata() + " (" + captured.size() + " primitives)"); continue; }
                    geometry.put(item.key() + "@" + variant.metadata(), resolved);
                    captureDiagnostics.add("captured " + item.key() + "@" + variant.metadata() + ": " + resolved.size() + " primitives, "
                            + resolved.stream().mapToInt(p -> p.vertices().size()).sum() + " vertices, bounds " + bounds(resolved));
                    rendered = true;
                }
                if (rendered) customItems.add(item.key());
            }
        }
    }
    private List<LegacyVertexStream.Primitive> resolveIcons(List<LegacyVertexStream.Primitive> primitives) {
        return data.resolveIcons(primitives);
    }
    public Map<String, List<LegacyVertexStream.Primitive>> geometry() { return geometry; }
    public Map<String, List<LegacyVertexStream.Primitive>> contextGeometry() { return contextGeometry; }
    public static String geometryKey(String key, int metadata, String tag) {
        return key + "@" + metadata + (tag == null || tag.isEmpty() ? "" : "#" + tag);
    }
    public Set<String> customItems() { return customItems; }
    public List<String> captureDiagnostics() { return List.copyOf(captureDiagnostics); }
    private static String bounds(List<LegacyVertexStream.Primitive> primitives) {
        double[] box = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
        for (var primitive : primitives) for (var vertex : primitive.vertices()) {
            box[0] = Math.min(box[0], vertex.x()); box[1] = Math.min(box[1], vertex.y()); box[2] = Math.min(box[2], vertex.z());
            box[3] = Math.max(box[3], vertex.x()); box[4] = Math.max(box[4], vertex.y()); box[5] = Math.max(box[5], vertex.z());
        }
        return String.format(java.util.Locale.ROOT, "[%.2f,%.2f,%.2f]-[%.2f,%.2f,%.2f]", box[0], box[1], box[2], box[3], box[4], box[5]);
    }
    private record Declarations(List<Item> items, List<Block> blocks, List<LegacyCreativeTabs.Tab> tabs) {}
    private Declarations read() throws Exception {
        ClassLoader loader = data.loader();
        Class<?> blockType = loader.loadClass("net.minecraft.block.Block"), itemType = loader.loadClass("net.minecraft.item.Item");
        Class<?> stackType = loader.loadClass("net.minecraft.item.ItemStack"), itemBlock = loader.loadClass("net.minecraft.item.ItemBlock");
        Class<?> iconType = loader.loadClass("net.minecraft.util.IIcon"), armorType = loader.loadClass("net.minecraft.item.ItemArmor");
        Object blockRegistry = blockType.getField("field_149771_c").get(null), itemRegistry = itemType.getField("field_150901_e").get(null);
        Method blockKey = blockRegistry.getClass().getMethod("func_148750_c", Object.class), itemKey = itemRegistry.getClass().getMethod("func_148750_c", Object.class);
        Set<String> customItems = new HashSet<>();
        for (Object item : (Iterable<?>) itemRegistry) {
            String key = (String) itemKey.invoke(itemRegistry, item);
            if (key == null || key.startsWith("minecraft:")) continue;
            customItems.add(key);
        }
        var tabValues = LegacyCreativeTabs.read(data, customItems);
        var blockValues = new ArrayList<Block>();
        for (Object block : (Iterable<?>) blockRegistry) {
            String key = (String) blockKey.invoke(blockRegistry, block);
            if (key == null || key.startsWith("minecraft:")) continue;
            var faces = new ArrayList<List<String>>();
            for (int meta = 0; meta < 16; meta++) {
                var sides = new ArrayList<String>();
                // World metadata also contains placement/decay/rotation bits absent from creative item variants.
                // The icon helper tolerates old blocks that reject undeclared array indices.
                for (int side = 0; side < 6; side++) sides.add(icon(blockType, block, side, meta, iconType));
                faces.add(sides);
            }
            var tileMetas = new ArrayList<Integer>();
            for (int meta = 0; meta < 16; meta++) if ((boolean) blockType.getMethod("hasTileEntity", int.class).invoke(block, meta)) tileMetas.add(meta);
            Object sound=blockType.getField("field_149762_H").get(block);var soundType=loader.loadClass("net.minecraft.block.Block$SoundType");
            blockValues.add(new Block(key, number(blockType, block, "field_149782_v"),
                    ((Number) blockType.getMethod("func_149638_a", loader.loadClass("net.minecraft.entity.Entity")).invoke(block, new Object[]{null})).floatValue(),
                    (int) blockType.getMethod("func_149750_m").invoke(block), (boolean) blockType.getMethod("func_149662_c").invoke(block),
                    !tileMetas.isEmpty(), (int) blockType.getMethod("func_149645_b").invoke(block),
                    (boolean)data.loader().loadClass("net.minecraft.client.renderer.RenderBlocks").getMethod("func_147739_a", int.class)
                            .invoke(null, (int)blockType.getMethod("func_149645_b").invoke(block)), faces, tileMetas,
                    (boolean)blockType.getMethod("func_149653_t").invoke(block),new Sound(
                            (float)soundType.getMethod("func_150497_c").invoke(sound),(float)soundType.getMethod("func_150494_d").invoke(sound),
                            (String)soundType.getMethod("func_150495_a").invoke(sound),(String)soundType.getMethod("func_150498_e").invoke(sound),(String)soundType.getMethod("func_150496_b").invoke(sound))));
            oldBlocks.put(key, block);
            readMining(key,blockType,block);
        }
        var itemValues = new ArrayList<Item>();
        var metadataByItem = new HashMap<String, Set<Integer>>();
        for (var stack : data.catalog()) metadataByItem.computeIfAbsent(stack.key(), ignored -> new TreeSet<>()).add(stack.metadata());
        for (var tab : tabValues) for (var stack : tab.contents())
            metadataByItem.computeIfAbsent(stack.key(), ignored -> new TreeSet<>()).add(stack.metadata());
        for (Object item : (Iterable<?>) itemRegistry) {
            String key = (String) itemKey.invoke(itemRegistry, item);
            if (key == null || key.startsWith("minecraft:")) continue;
            String linkedBlock = null;
            if (itemBlock.isInstance(item)) linkedBlock = (String) blockKey.invoke(blockRegistry, itemBlock.getField("field_150939_a").get(item));
            var variants = new ArrayList<Variant>();
            var metas = new TreeSet<>(metadataByItem.getOrDefault(key, Set.of()));
            if (metas.isEmpty()) metas.add(0);
            int limit = (int)itemType.getMethod("func_77639_j").invoke(item), damage = 0;
            for (int meta : metas) {
                Object stack = stackType.getConstructor(itemType, int.class, int.class).newInstance(item, 1, meta);
                String name; String texture; int color;
                try {
                    name = (String) itemType.getMethod("func_77667_c", stackType).invoke(item, stack);
                    // ItemBlock's icon methods ignore metadata, so block items resolve the real block icon for
                    // this metadata; ordinary items keep their stack-specific icon and only fall back to damage.
                    Object block = linkedBlock == null ? null : oldBlocks.get(linkedBlock);
                    Object icon;
                    if (block != null) icon = blockType.getMethod("func_149691_a", int.class, int.class).invoke(block, 2, meta);
                    else {
                        icon = itemType.getMethod("getIcon", stackType, int.class).invoke(item, stack, 0);
                        if (icon == null) icon = itemType.getMethod("func_77617_a", int.class).invoke(item, meta);
                    }
                    texture = icon == null ? "" : (String) iconType.getMethod("func_94215_i").invoke(icon);
                    color = (int) itemType.getMethod("func_82790_a", stackType, int.class).invoke(item, stack, 0);
                    damage = Math.max(damage, (int) itemType.getMethod("getMaxDamage", stackType).invoke(item, stack));
                } catch (ReflectiveOperationException undeclared) {
                    name = (String) itemType.getMethod("func_77658_a").invoke(item);
                    texture = ""; color = 0xFFFFFF;
                }
                var layers = new ArrayList<Layer>();
                try {
                    int passes = linkedBlock == null ? (int)itemType.getMethod("getRenderPasses", int.class).invoke(item, meta) : 1;
                    for (int pass = 0; pass < passes; pass++) {
                        Object icon = linkedBlock == null ? itemType.getMethod("getIcon", stackType, int.class).invoke(item, stack, pass) : null;
                        String layerIcon = icon == null ? texture : (String)iconType.getMethod("func_94215_i").invoke(icon);
                        int layerColor = linkedBlock == null ? (int)itemType.getMethod("func_82790_a", stackType, int.class).invoke(item, stack, pass) : color;
                        layers.add(new Layer(layerIcon, layerColor));
                    }
                } catch (ReflectiveOperationException undeclared) { layers.clear(); layers.add(new Layer(texture, color)); }
                variants.add(new Variant(meta, name + ".name", texture, color, layers));
            }
            boolean creative = Arrays.stream((Object[]) itemType.getMethod("getCreativeTabs").invoke(item)).anyMatch(Objects::nonNull);
            Armor armor = armorType.isInstance(item) ? armor(loader, item, stackType.getConstructor(itemType, int.class, int.class).newInstance(item, 1, 0)) : null;
            List<Pull> pulls = linkedBlock == null ? pulls(loader, item, stackType.getConstructor(itemType, int.class, int.class).newInstance(item, 1, 0)) : List.of();
            boolean full3D = false, rodRotation = false;
            try {
                // Both are client-only in 1.7.10; a dedicated server has no held-item models to choose.
                full3D = (boolean) itemType.getMethod("func_77662_d").invoke(item);
                rodRotation = full3D && (boolean) itemType.getMethod("func_77629_n_").invoke(item);
            } catch (ReflectiveOperationException clientOnly) { }
            itemValues.add(new Item(key, linkedBlock, Math.max(1, Math.min(64, limit)), damage, creative, variants, armor, pulls, full3D, rodRotation));
        }
        blockValues.sort(Comparator.comparing(Block::key)); itemValues.sort(Comparator.comparing(Item::key));
        return new Declarations(List.copyOf(itemValues), List.copyOf(blockValues), tabValues);
    }
    private static final String[] VANILLA_ARMOR = {"leather", "chainmail", "iron", "diamond", "gold"};
    private static Armor armor(ClassLoader loader, Object item, Object stack) throws Exception {
        Class<?> armorType = loader.loadClass("net.minecraft.item.ItemArmor"), itemType = loader.loadClass("net.minecraft.item.Item");
        Class<?> stackType = loader.loadClass("net.minecraft.item.ItemStack"), entityType = loader.loadClass("net.minecraft.entity.Entity");
        int slot = armorType.getField("field_77881_a").getInt(item), points = armorType.getField("field_77879_b").getInt(item);
        Class<?> special = loader.loadClass("net.minecraftforge.common.ISpecialArmor");
        // ISpecialArmor indexes armorInventory (0 boots .. 3 helmet); the displayed points are what its wearer sees.
        if (special.isInstance(item)) try {
            points = (int) special.getMethod("getArmorDisplay", loader.loadClass("net.minecraft.entity.player.EntityPlayer"), stackType, int.class).invoke(item, null, stack, 3 - slot);
        } catch (ReflectiveOperationException | RuntimeException needsWearer) { /* keep the ItemArmor material points */ }
        int enchantability = (int) itemType.getMethod("func_77619_b").invoke(item);
        Object material = armorType.getMethod("func_82812_d").invoke(item);
        boolean dyeable = material == loader.loadClass("net.minecraft.item.ItemArmor$ArmorMaterial").getField("CLOTH").get(null)
                || item.getClass().getMethod("func_82814_b", stackType).getDeclaringClass() != armorType;
        boolean ticks = item.getClass().getMethod("onArmorTick", loader.loadClass("net.minecraft.world.World"), loader.loadClass("net.minecraft.entity.player.EntityPlayer"), stackType).getDeclaringClass() != itemType;
        String prefix = VANILLA_ARMOR[Math.floorMod(armorType.getField("field_77880_c").getInt(item), VANILLA_ARMOR.length)];
        // RenderBiped's default: layer 2 only for leggings.
        String texture = armorTexture(itemType, stackType, entityType, item, stack, slot, null,
                String.format(Locale.ROOT, "minecraft:textures/models/armor/%s_layer_%d.png", prefix, slot == 2 ? 2 : 1));
        String overlay = !dyeable ? null : armorTexture(itemType, stackType, entityType, item, stack, slot, "overlay",
                prefix.equals("leather") ? String.format(Locale.ROOT, "minecraft:textures/models/armor/leather_layer_%d_overlay.png", slot == 2 ? 2 : 1) : null);
        return new Armor(slot, points, enchantability, texture, overlay, dyeable, ticks);
    }
    private static String armorTexture(Class<?> itemType, Class<?> stackType, Class<?> entityType, Object item, Object stack, int slot, String type, String fallback) {
        String texture = null;
        try { texture = (String) itemType.getMethod("getArmorTexture", stackType, entityType, int.class, String.class).invoke(item, stack, null, slot, type); }
        catch (ReflectiveOperationException | RuntimeException needsWearer) { /* fall back to the material layer */ }
        if (texture == null) return fallback;
        return texture.contains(":") ? texture : "minecraft:" + texture;
    }
    /** Samples the original held-use icon while drawing (as EntityPlayer.getItemIcon would), keeping each change. */
    private List<Pull> pulls(ClassLoader loader, Object item, Object stack) {
        try {
            Class<?> itemType = loader.loadClass("net.minecraft.item.Item"), stackType = loader.loadClass("net.minecraft.item.ItemStack");
            Class<?> playerType = loader.loadClass("net.minecraft.entity.player.EntityPlayer"), iconType = loader.loadClass("net.minecraft.util.IIcon");
            Object action = itemType.getMethod("func_77661_b", stackType).invoke(item, stack);
            if (action == null || !((Enum<?>) action).name().equals("bow")) return List.of();
            int duration = (int) itemType.getMethod("func_77626_a", stackType).invoke(item, stack);
            int passes = (int) itemType.getMethod("getRenderPasses", int.class).invoke(item, 0);
            Class<?> mc = loader.loadClass("net.minecraft.client.Minecraft");
            Object client = mc.getMethod("func_71410_x").invoke(null), player = client == null ? null : mc.getField("field_71439_g").get(client);
            Method icon = itemType.getMethod("getIcon", stackType, int.class, playerType, stackType, int.class);
            var result = new ArrayList<Pull>(); List<String> previous = new ArrayList<>();
            for (int pass = 0; pass < passes; pass++) {
                Object idle = itemType.getMethod("getIcon", stackType, int.class).invoke(item, stack, pass);
                previous.add(idle == null ? "" : (String) iconType.getMethod("func_94215_i").invoke(idle));
            }
            for (int ticks = 1; ticks <= Math.min(duration, 72); ticks++) {
                var icons = new ArrayList<String>();
                for (int pass = 0; pass < passes; pass++) {
                    Object value = icon.invoke(item, stack, pass, player, stack, duration - ticks);
                    icons.add(value == null ? "" : (String) iconType.getMethod("func_94215_i").invoke(value));
                }
                if (!icons.equals(previous)) { result.add(new Pull(ticks, icons)); previous = icons; }
            }
            return List.copyOf(result);
        } catch (ReflectiveOperationException | RuntimeException unsupported) {
            captureDiagnostics.add("held-use icons unavailable for " + item + ": " + unsupported);
            return List.of();
        }
    }
    private static String icon(Class<?> blockType, Object block, int side, int meta, Class<?> iconType) throws Exception {
        try {
            Object icon = blockType.getMethod("func_149691_a", int.class, int.class).invoke(block, side, meta);
            return icon == null ? "" : (String) iconType.getMethod("func_94215_i").invoke(icon);
        } catch (ReflectiveOperationException undeclared) {
            return "";
        }
    }
    private static float number(Class<?> type, Object target, String name) throws Exception {
        Field field = type.getDeclaredField(name); field.setAccessible(true); return field.getFloat(target);
    }
    public List<Item> items() { return items; }
    public List<Block> blocks() { return blocks; }
    public Map<String,List<String>> miningTags() { return miningTags; }
    public boolean requiresTool(String key) { return toolRequired.contains(key); }
    private void readMining(String key,Class<?> blockType,Object block) throws Exception {
        Object material=blockType.getMethod("func_149688_o").invoke(block);
        // Tags are block-wide. Only export declarations shared by every old metadata value.
        Set<String> tools=new LinkedHashSet<>();Set<Integer> levels=new HashSet<>();
        for(int meta=0;meta<16;meta++) {
            tools.add((String)blockType.getMethod("getHarvestTool",int.class).invoke(block,meta));
            levels.add((int)blockType.getMethod("getHarvestLevel",int.class).invoke(block,meta));
        }
        if(tools.size()!=1||levels.size()!=1)return;
        if(!(boolean)data.loader().loadClass("net.minecraft.block.material.Material").getMethod("func_76229_l").invoke(material))toolRequired.add(key);
        int level=levels.iterator().next();
        if(level>3)return; // Custom old tiers have no equivalent in vanilla's modern mining tags.
        String tool=tools.iterator().next();
        if(tool==null) {
            // Old pickaxes/axes/shovels also recognize materials, even without an explicit harvest declaration.
            var itemType=data.loader().loadClass("net.minecraft.item.Item");
            for(String candidate:List.of("pickaxe","axe","shovel")) {
                Object stack=data.materialize(new LegacyClientData.Stack("minecraft:diamond_"+candidate,0,1,"",""));
                Object item=data.loader().loadClass("net.minecraft.item.ItemStack").getMethod("func_77973_b").invoke(stack);
                float speed=(float)itemType.getMethod("func_150893_a",data.loader().loadClass("net.minecraft.item.ItemStack"),blockType).invoke(item,stack,block);
                if(speed>1)miningTag("mineable/"+candidate,key);
            }
        } else if(Set.of("pickaxe","axe","shovel","hoe").contains(tool))miningTag("mineable/"+tool,key);
        if(level>=1&&level<=3)miningTag(level==1?"needs_stone_tool":level==2?"needs_iron_tool":"needs_diamond_tool",key);
    }
    private void miningTag(String tag,String key) { miningTags.computeIfAbsent(tag,ignored->new ArrayList<>()).add(key); }
    public List<LegacyCreativeTabs.Tab> tabs() { return tabs; }
    public ClassLoader loader() { return data.loader(); }
    public Set<String> registeredIcons() { return Set.copyOf(data.iconLayout().keySet()); }
    public byte[] iconTexture(String name) { return data.iconTexture(name); }
    public String iconTextureMetadata(String name) { return data.iconTextureMetadata(name); }
    /** Forge's extensible drop callback, including mod loot generators and the real world's random/context. */
    public List<LegacyClientData.Stack> drops(String key,int metadata,Object world,int x,int y,int z,int fortune) throws Exception {
        return data.inLegacy(() -> {
            Object block=Objects.requireNonNull(oldBlocks.get(key));
            var type=data.loader().loadClass("net.minecraft.block.Block");
            var worldType=data.loader().loadClass("net.minecraft.world.World");
            var result=new ArrayList<LegacyClientData.Stack>();
            var custom=type.getMethod("func_149690_a",worldType,int.class,int.class,int.class,int.class,float.class,int.class);
            if(block.getClass().getMethod(custom.getName(),custom.getParameterTypes()).getDeclaringClass()!=type){
                // Some old leaves spawn saplings here and deliberately return no items from getDrops.
                LegacyDeviceContainers.withDrops(entity->{
                    var snapshot=data.snapshot(entity.getClass().getMethod("func_92059_d").invoke(entity));
                    if(snapshot!=null&&snapshot.count()>0)result.add(snapshot);
                },()->{custom.invoke(block,world,x,y,z,metadata,1f,fortune);return null;});
                return List.copyOf(result);
            }
            for(Object stack:(List<?>)type.getMethod("getDrops",worldType,int.class,int.class,int.class,int.class,int.class)
                    .invoke(block,world,x,y,z,metadata,fortune)) {
                var snapshot=data.snapshot(stack);if(snapshot!=null&&snapshot.count()>0)result.add(snapshot);
            }
            return List.copyOf(result);
        });
    }
    /** Execute the original vanilla-style drop methods, preserving custom ore drop identity and metadata. */
    public List<LegacyClientData.Stack> drops(String key, int metadata, long seed) throws Exception {
        return data.inLegacy(() -> {
            Object block = Objects.requireNonNull(oldBlocks.get(key)); Class<?> type = data.loader().loadClass("net.minecraft.block.Block");
            Random random = new Random(seed);
            int count = (int) type.getMethod("func_149679_a", int.class, Random.class).invoke(block, 0, random);
            int damage = (int) type.getMethod("func_149692_a", int.class).invoke(block, metadata);
            Class<?> itemType = data.loader().loadClass("net.minecraft.item.Item"), stackType = data.loader().loadClass("net.minecraft.item.ItemStack");
            var result = new ArrayList<LegacyClientData.Stack>();
            for (int i = 0; i < count; i++) {
                Object item = type.getMethod("func_149650_a", int.class, Random.class, int.class).invoke(block, metadata, random, 0);
                if (item != null) result.add(data.snapshot(stackType.getConstructor(itemType, int.class, int.class).newInstance(item, 1, damage)));
            }
            return List.copyOf(result);
        });
    }
}
