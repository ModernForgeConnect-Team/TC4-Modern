package github.com.gengyoubo.mfcl.content;

import com.google.gson.GsonBuilder;
import github.com.gengyoubo.mfcl.bridge.LegacyContent;
import github.com.gengyoubo.mfcl.bridge.LegacyVertexStream;
import github.com.gengyoubo.mfcl.bridge.LegacyBlockRendering;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.*;
import net.minecraft.server.packs.repository.*;
import net.minecraftforge.event.AddPackFindersEvent;
import java.io.*;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.jar.JarFile;

/** Derived resources only: original mod archives are read, never edited or installed as modern mods. */
public final class LegacyContentPack {
    private final Path directory;
    private final LegacyContent content;
    private final LegacyBlockRendering worldRendering;
    private final Map<String, String> textures = new HashMap<>();
    private final Set<String> missingIcons = new TreeSet<>();
    private final Map<String, List<LegacyVertexStream.Primitive>> geometry = new LinkedHashMap<>();
    private final Map<String, List<LegacyVertexStream.Primitive>> contextGeometry = new LinkedHashMap<>();
    public LegacyContentPack(Path gameDirectory, LegacyContent content, github.com.gengyoubo.mfcl.bridge.LegacyBlockRendering worldRendering) throws Exception {
        this.content = content;this.worldRendering=worldRendering;
        Path parent = gameDirectory.resolve("mfcl/generated-packs"); Files.createDirectories(parent);
        directory = Files.createTempDirectory(parent, "content-1.7.10-");
        progress("Copying legacy textures and sounds");
        int copied = 0;
        // TESRs select additional skins and animation textures at runtime, beyond creative inventory snapshots.
        if (content.loader() instanceof URLClassLoader urls) for (var url : urls.getURLs()) {
            if (!url.getProtocol().equals("file")) continue;
            Path archive = Path.of(url.toURI()); if (!Files.isRegularFile(archive)) continue;
            try (var jar = new JarFile(archive.toFile())) {
                for (var entries = jar.entries(); entries.hasMoreElements();) {
                    var entry = entries.nextElement(); String path = entry.getName();
                    boolean definition=path.matches("assets/(?!minecraft/)[^/]+/sounds\\.json");
                    boolean audio=path.matches("assets/(?!minecraft/)[^/]+/sounds/.*\\.ogg");
                    if (entry.isDirectory() || !definition&&!audio&&!path.matches("assets/(?!minecraft/)[^/]+/textures/.*\\.(png|mcmeta)")) continue;
                    Path target = directory.resolve(path.toLowerCase(Locale.ROOT)).normalize();
                    if (!target.startsWith(directory)) throw new IllegalArgumentException(path);
                    Files.createDirectories(target.getParent()); try (var input = jar.getInputStream(entry)) {
                        if(definition) {
                            var converted=github.com.gengyoubo.mfcl.bridge.LegacySounds.definitions(path.split("/")[1],new String(input.readAllBytes(),StandardCharsets.UTF_8));
                            if(Files.exists(target)) {
                                var prior=com.google.gson.JsonParser.parseString(Files.readString(target)).getAsJsonObject();converted.entrySet().forEach(value->prior.add(value.getKey(),value.getValue()));converted=prior;
                            }
                            Files.writeString(target,converted.toString(),StandardCharsets.UTF_8);
                        }else Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
                    }
                    if (++copied % 2048 == 0) progress("Copied " + copied + " legacy textures and sounds");
                }
            }
        }
        for (var primitive : worldRendering.tilePreviews()) if (primitive.texture().contains("textures/")) texturePath(primitive.texture());
        // Worn armor layers; old namespaces/paths may be upper-case, which host resource locations reject.
        for (var item : content.items()) if (item.armor() != null) for (String layer : java.util.Arrays.asList(item.armor().texture(), item.armor().overlay()))
            if (layer != null && !layer.startsWith("minecraft:")) texturePath(layer);
        // Dyed armor always draws an overlay pass; originals without one get an empty layer instead of a missing texture.
        var transparent = directory.resolve("assets/mfcl/textures/models/armor/transparent.png"); Files.createDirectories(transparent.getParent());
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(64, 32, java.awt.image.BufferedImage.TYPE_INT_ARGB), "png", transparent.toFile());
        json("pack.mcmeta", Map.of("pack", Map.of("pack_format", 15, "description", "MFCL original 1.7.10 content resources")));
        var transparentFluid = directory.resolve("assets/mfcl/textures/block/legacy/fluid_transparent.png");Files.createDirectories(transparentFluid.getParent());
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(16,16,java.awt.image.BufferedImage.TYPE_INT_ARGB),"png",transparentFluid.toFile());
        // builtin/entity item models reference no texture, so their sprites must be stitched explicitly.
        json("assets/mfcl/atlases/blocks.json", Map.of("sources", List.of(Map.of("type", "minecraft:directory", "source", "block/legacy"))));
        // Contextual world icons (e.g. connected glass) may never appear in an inventory capture.
        progress("Exporting legacy atlas textures");
        int exported = 0;
        for (String icon : content.registeredIcons()) {
            if (!icon.isBlank() && !icon.startsWith("MISSING_ICON_")) texture(icon, "blocks");
            if (++exported % 2048 == 0) progress("Exported " + exported + " legacy atlas textures");
        }
        progress("Writing legacy block and item models");
        for (var block : content.blocks()) {
            var id = ModernContentBridge.id(block.key()); var variants = new LinkedHashMap<String, Object>();
            for (int meta = 0; meta < 16; meta++) {
                String model = id.getNamespace() + ":block/" + id.getPath() + "_" + meta;
                json("assets/" + id.getNamespace() + "/models/block/" + id.getPath() + "_" + meta + ".json",cuboidModel(block,meta,worldRendering.cuboid(block.key(),meta)));
                variants.put("legacy_meta=" + meta, Map.of("model", model));
            }
            json("assets/" + id.getNamespace() + "/blockstates/" + id.getPath() + ".json", Map.of("variants", variants));
        }
        for(var tag:content.miningTags().entrySet())
            json("data/minecraft/tags/blocks/"+tag.getKey()+".json",Map.of("replace",false,"values",tag.getValue().stream().map(ModernContentBridge::id).map(Object::toString).toList()));
        for (var item : content.items()) {
            var id = ModernContentBridge.id(item.key());
            if (content.customItems().contains(item.key())) {
                // The real old ISBRH geometry is replayed by a modern custom item renderer.
                json("assets/" + id.getNamespace() + "/models/item/" + id.getPath() + ".json", Map.of("parent", "builtin/entity"));
                continue;
            }
            var overrides = new ArrayList<Object>();
            LegacyContent.Variant base = item.variants().stream().filter(v -> v.metadata() == 0).findFirst().orElse(item.variants().get(0));
            Map<String, Object> model = itemModel(item, base);
            for (var variant : item.variants()) if (variant.metadata() != 0) {
                String path = "item/" + id.getPath() + "_meta_" + variant.metadata();
                json("assets/" + id.getNamespace() + "/models/" + path + ".json", itemModel(item, variant));
                overrides.add(Map.of("predicate", Map.of("mfcl:metadata", variant.metadata()), "model", id.getNamespace() + ":" + path));
            }
            if (!item.pulls().isEmpty() && "minecraft:item/generated".equals(model.get("parent"))) {
                // Original held-use frames (bow pulling), chosen by drawn ticks; listed last so they win over metadata.
                model.put("parent", "minecraft:item/bow");
                for (int frame = 0; frame < item.pulls().size(); frame++) {
                    var pull = item.pulls().get(frame); var layers = new LinkedHashMap<String, String>();
                    for (int pass = 0; pass < pull.icons().size(); pass++) layers.put("layer" + pass, texture(pull.icons().get(pass), "items"));
                    String path = "item/" + id.getPath() + "_pull_" + frame;
                    json("assets/" + id.getNamespace() + "/models/" + path + ".json", Map.of("parent", "minecraft:item/bow", "textures", layers));
                    overrides.add(Map.of("predicate", Map.of("mfcl:pull", pull.ticks()), "model", id.getNamespace() + ":" + path));
                }
            }
            model.put("overrides", overrides);
            json("assets/" + id.getNamespace() + "/models/item/" + id.getPath() + ".json", model);
        }
        resolveGeometry(content.geometry(), geometry);
        resolveGeometry(content.contextGeometry(), contextGeometry);
        languages();
        json("mfcl-content-manifest.json", Map.of("blocks", content.blocks(), "items", content.items(), "creativeTabs", content.tabs(), "missingIcons", missingIcons,
                "customItems", List.copyOf(content.customItems()),
                "registryIds", Map.of("blocks",content.blocks().stream().collect(java.util.stream.Collectors.toMap(LegacyContent.Block::key,entry->ModernContentBridge.id(entry.key()).toString())),
                        "items",content.items().stream().collect(java.util.stream.Collectors.toMap(LegacyContent.Item::key,entry->ModernContentBridge.id(entry.key()).toString()))),
                "simpleBlocks", content.blocks().stream().filter(LegacyContent.Block::simple).map(LegacyContent.Block::key).toList()));
        progress("Legacy resource pack ready");
    }
    private static void progress(String message) {
        com.mojang.logging.LogUtils.getLogger().info("MFCL content: {}", message);
        try { net.minecraftforge.fml.loading.progress.StartupNotificationManager.addModMessage("MFCL: " + message); }
        catch (RuntimeException | LinkageError unavailable) { /* CLI probes have no host loading screen. */ }
    }
    private void resolveGeometry(Map<String, List<LegacyVertexStream.Primitive>> source, Map<String, List<LegacyVertexStream.Primitive>> target) throws Exception {
        for (var entry : source.entrySet()) {
            var resolved = new ArrayList<LegacyVertexStream.Primitive>();
            for (var primitive : entry.getValue()) {
                String texture = primitive.texture();
                // IItemRenderer geometry keeps a full old texture path; ISBRH geometry keeps an icon name.
                String id = texture.equals(github.com.gengyoubo.mfcl.bridge.LegacyItemRendering.PLAYER_SKIN) ? texture
                        : texture.contains("textures/") ? texturePath(texture) : texture(texture, "blocks");
                if (id.isEmpty()) continue;
                resolved.add(new LegacyVertexStream.Primitive(primitive.mode(), id, primitive.vertices(), primitive.state()));
            }
            target.put(entry.getKey(), List.copyOf(resolved));
        }
    }
    private Map<String, Object> itemModel(LegacyContent.Item item, LegacyContent.Variant variant) throws Exception {
        var block = item.blockKey() == null ? null : content.blocks().stream()
                .filter(b -> b.key().equals(item.blockKey())).findFirst().orElse(null);
        if (block != null && block.blockModel()) {
            if(block.renderType()==0)return new LinkedHashMap<>(cuboidModel(block,variant.metadata()&15,worldRendering.inventoryCuboid(block.key(),variant.metadata()&15)));
            var id = ModernContentBridge.id(block.key());
            return new LinkedHashMap<>(Map.of("parent", id.getNamespace() + ":block/" + id.getPath() + "_" + (variant.metadata() & 15)));
        }
        var layers = new LinkedHashMap<String, String>();
        for (int pass = 0; pass < variant.layers().size(); pass++) layers.put("layer" + pass, texture(variant.layers().get(pass).icon(), "items"));
        // Original full-3D items (swords, tools, wands) are held upright, as host handheld items are.
        String parent = !item.full3D() ? "minecraft:item/generated" : item.rodRotation() ? "minecraft:item/handheld_rod" : "minecraft:item/handheld";
        return new LinkedHashMap<>(Map.of("parent", parent, "textures", layers));
    }
    private Map<String,Object> cuboidModel(LegacyContent.Block block,int meta,LegacyBlockRendering.Cuboid bounds) throws Exception {
        String[] names={"down","up","north","south","west","east"};
        var textures=new LinkedHashMap<String,String>();
        for(int side=0;side<6;side++)textures.put(names[side],texture(block.faces().get(meta).get(side),"blocks"));
        textures.put("particle",textures.get("north"));
        boolean magicalLeaves=block.key().equals("Thaumcraft:blockMagicalLeaves");
        if(bounds.full()&&!magicalLeaves)return Map.of("parent","minecraft:block/cube","textures",textures);
        var faces=new LinkedHashMap<String,Object>();
        boolean[] boundary={bounds.minY()==0,bounds.maxY()==1,bounds.minZ()==0,bounds.maxZ()==1,bounds.minX()==0,bounds.maxX()==1};
        for(int side=0;side<6;side++){
            var face=new LinkedHashMap<String,Object>();face.put("texture","#"+names[side]);
            if(magicalLeaves)face.put("tintindex",0);
            // Inset sides remain visible beside neighboring blocks. Only boundary faces can be culled.
            if(boundary[side])face.put("cullface",names[side]);
            faces.put(names[side],face);
        }
        // Omitted face UVs use the bounds-derived vanilla crop, matching the original RenderBlocks cuboid.
        return Map.of("parent","minecraft:block/block","textures",textures,"elements",List.of(Map.of(
                "from",List.of(bounds.minX()*16,bounds.minY()*16,bounds.minZ()*16),
                "to",List.of(bounds.maxX()*16,bounds.maxY()*16,bounds.maxZ()*16),"faces",faces)));
    }
    /** Copies a whole old texture resource (for IItemRenderer models) and returns its standalone modern path. */
    private String texturePath(String resource) throws Exception {
        String cacheKey = "path:" + resource;
        if (textures.containsKey(cacheKey)) return textures.get(cacheKey);
        int colon = resource.indexOf(':');
        String namespace = colon < 0 ? "minecraft" : resource.substring(0, colon);
        String path = colon < 0 ? resource : resource.substring(colon + 1);
        try (var input = content.loader().getResourceAsStream("assets/" + namespace + "/" + path)) {
            if (input == null) { missingIcons.add(cacheKey); textures.put(cacheKey, ""); return ""; }
            byte[] bytes = input.readAllBytes();
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(path.getBytes(StandardCharsets.UTF_8)));
            Path target = directory.resolve("assets/mfcl/textures/block/legacy/" + hash + ".png");
            Files.createDirectories(target.getParent()); Files.write(target, bytes);
            try (var meta = content.loader().getResourceAsStream("assets/" + namespace + "/" + path + ".mcmeta")) {
                if (meta != null) Files.write(target.resolveSibling(target.getFileName() + ".mcmeta"), meta.readAllBytes());
            }
            String id = "mfcl:textures/block/legacy/" + hash + ".png";
            textures.put(cacheKey, id); return id;
        }
    }
    private String texture(String icon, String preferred) throws Exception {
        String cacheKey = preferred + ":" + icon;
        if (textures.containsKey(cacheKey)) return textures.get(cacheKey);
        byte[] generated=content.iconTexture(icon);
        if(generated!=null) {
            String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(icon.getBytes(StandardCharsets.UTF_8)));
            Path path=directory.resolve("assets/mfcl/textures/block/legacy/"+hash+".png");Files.createDirectories(path.getParent());Files.write(path,generated);
            String metadata=content.iconTextureMetadata(icon);
            if(metadata!=null)Files.writeString(path.resolveSibling(path.getFileName()+".mcmeta"),metadata,StandardCharsets.UTF_8);
            String id="mfcl:block/legacy/"+hash;textures.put(cacheKey,id);return id;
        }
        String namespace = icon.contains(":") ? icon.substring(0, icon.indexOf(':')) : "minecraft";
        String name = icon.contains(":") ? icon.substring(icon.indexOf(':') + 1) : icon;
        for (String folder : List.of(preferred, preferred.equals("items") ? "blocks" : "items")) {
            String resource = "assets/" + namespace + "/textures/" + folder + "/" + name + ".png";
            try (var input = content.loader().getResourceAsStream(resource)) {
                if (input == null) continue;
                byte[] bytes = input.readAllBytes();
                String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(resource.getBytes(StandardCharsets.UTF_8)));
                // Vanilla 1.20.1 atlas sources include the block/ and item/ directories, not arbitrary texture folders.
                String id = "mfcl:block/legacy/" + hash;
                Path path = directory.resolve("assets/mfcl/textures/block/legacy/" + hash + ".png"); Files.createDirectories(path.getParent()); Files.write(path, bytes);
                try (var meta = content.loader().getResourceAsStream(resource + ".mcmeta")) {
                    if (meta != null) Files.write(path.resolveSibling(path.getFileName() + ".mcmeta"), meta.readAllBytes());
                }
                textures.put(cacheKey, id); return id;
            }
        }
        missingIcons.add(cacheKey); textures.put(cacheKey, "minecraft:item/barrier"); return "minecraft:item/barrier";
    }
    private void languages() throws Exception {
        Map<String, Map<String, String>> languages = new TreeMap<>();
        if (content.loader() instanceof URLClassLoader urls) for (var url : urls.getURLs()) {
            if (!url.getProtocol().equals("file")) continue;
            Path file = Path.of(url.toURI()); if (!Files.isRegularFile(file)) continue;
            try (var jar = new JarFile(file.toFile())) {
                for (var entries = jar.entries(); entries.hasMoreElements();) {
                    var entry = entries.nextElement(); String name = entry.getName();
                    if (!name.matches("assets/[^/]+/lang/[^/]+\\.lang") || name.startsWith("assets/minecraft/")) continue;
                    String language = name.substring(name.lastIndexOf('/') + 1, name.length() - 5).toLowerCase(Locale.ROOT);
                    var values = languages.computeIfAbsent(language, ignored -> new TreeMap<>());
                    try (var reader = new BufferedReader(new InputStreamReader(jar.getInputStream(entry), StandardCharsets.UTF_8))) {
                        // Old language files split on the first '=', without Java Properties escape processing.
                        for (String line; (line = reader.readLine()) != null;) {
                            int split = line.indexOf('='); if (line.isBlank() || line.startsWith("#") || split < 0) continue;
                            values.put(line.substring(0, split), line.substring(split + 1).replaceAll("%(\\d+\\$)?[\\d.]*[df]", "%$1s"));
                        }
                    }
                }
            }
        }
        languages.computeIfAbsent("en_us", ignored -> new TreeMap<>()).putAll(Map.of("itemGroup.mfcl.unclassified_1_7_10", "Other 1.7.10 items",
                "mfcl.content.special_block_pending", "This block needs special rendering or placement support, which is not available yet.",
                "mfcl.content.machine_logic_pending", "The original logic of this block failed in this session and was paused. Details are in the game log.",
                "mfcl.content.placement_failed", "This block could not be initialized. Details are in the game log."));
        languages.computeIfAbsent("zh_cn", ignored -> new TreeMap<>()).putAll(Map.of("itemGroup.mfcl.unclassified_1_7_10", "其他 1.7.10 物品",
                "mfcl.content.special_block_pending", "此方块需要特殊渲染或放置支持，暂不能放置。",
                "mfcl.content.machine_logic_pending", "此方块的原版逻辑本次运行时出错，已暂停；详细信息见游戏日志。",
                "mfcl.content.placement_failed", "无法初始化此方块，详细信息见游戏日志。"));
        for (var entry : languages.entrySet()) json("assets/mfcl/lang/" + entry.getKey() + ".json", entry.getValue());
    }
    /** Adds original items to a host item tag without replacing what vanilla or other packs put there. */
    public void itemTag(String tag, List<String> items) throws IOException {
        var id = net.minecraft.resources.ResourceLocation.parse(tag);
        json("data/" + id.getNamespace() + "/tags/items/" + id.getPath() + ".json", Map.of("replace", false, "values", items));
    }
    void json(String resource, Object value) throws IOException {
        Path file = directory.resolve(resource).normalize();
        if (!file.startsWith(directory)) throw new IllegalArgumentException("Generated resource escapes pack: " + resource);
        Files.createDirectories(file.getParent()); Files.writeString(file, new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(value), StandardCharsets.UTF_8);
    }
    public Map<String, List<LegacyVertexStream.Primitive>> geometry() { return geometry; }
    public Map<String, List<LegacyVertexStream.Primitive>> contextGeometry() { return contextGeometry; }
    /** Both frozen host texture IDs and live captures using original icon names enter the item replay path. */
    public String renderTexture(String texture) {
        return texture.contains("textures/") ? worldResource(texture) : textures.getOrDefault("blocks:"+texture,texture);
    }
    public String worldTexture(String icon) {
        String value = textures.get("blocks:" + icon);
        if (value == null) throw new IllegalArgumentException("World renderer referenced an unregistered icon: " + icon);
        return value;
    }
    public String worldResource(String resource) { return textures.getOrDefault("path:" + resource, github.com.gengyoubo.mfcl.bridge.LegacyTexturePaths.modernResource(resource).toLowerCase(Locale.ROOT)); }
    public static final String TRANSPARENT_ARMOR = "mfcl:textures/models/armor/transparent.png";
    /** Host texture for an original worn armor layer, or null when the old resource was missing. */
    public String armorTexture(String resource) {
        if (resource == null) return null;
        if (resource.startsWith("minecraft:")) return resource;
        String value = textures.get("path:" + resource); return value == null || value.isEmpty() ? null : value;
    }
    public Path directory() { return directory; }
    public void craftingRecipe(String namespace,String hash) throws IOException {json("data/"+namespace+"/recipes/legacy_crafting/"+hash+".json",Map.of("type","mfcl:legacy_crafting","original",hash));}
    public void smeltingRecipe(String namespace,String hash) throws IOException {json("data/"+namespace+"/recipes/legacy_smelting/"+hash+".json",Map.of("type","mfcl:legacy_smelting","original",hash));}
    public Set<String> missingIcons() { return Collections.unmodifiableSet(missingIcons); }
    public void findPacks(AddPackFindersEvent event) {
        event.addRepositorySource(consumer -> {
            Pack pack = Pack.readMetaAndCreate("mfcl_legacy_1_7_10", Component.literal("MFCL 1.7.10"), true,
                    name -> new PathPackResources(name, directory, false), event.getPackType(), Pack.Position.TOP, PackSource.BUILT_IN);
            consumer.accept(Objects.requireNonNull(pack, "Generated legacy pack metadata"));
        });
    }
}
