package github.com.gengyoubo.mfcl.bridge;

import java.lang.reflect.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

/** Forge 1.7.10 Item/ItemStack semantics. Owns identity, copies, variant/NBT snapshots and host recipe publication. */
public final class LegacyClientData {
    /** Plain values only: the private runtime owns its own Session and authlib types. */
    public record ClientSession(String name, String uuid, String accessToken, String type) {
        public static ClientSession offline() {
            String name = "MFCL";
            return new ClientSession(name, UUID.nameUUIDFromBytes(("OfflinePlayer:" + name)
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString(), "0", "legacy");
        }
        @Override public String toString() { return "ClientSession[credentials redacted]"; }
    }
    public record Stack(String key, int metadata, int count, String name, String tag) {}
    public record Ingredient(int x, int y, List<Stack> alternatives) { public Ingredient { alternatives = List.copyOf(alternatives); } }
    public record Recipe(String handler, String name, List<Ingredient> ingredients, Stack result, List<Ingredient> other) {
        public Recipe { ingredients = List.copyOf(ingredients); other = List.copyOf(other); }
    }
    public record HostRecipe(String id, String kind, int width, int height, List<List<Stack>> ingredients, Stack result, float experience) {
        public HostRecipe { ingredients = ingredients.stream().map(List::copyOf).toList(); }
        public HostRecipe(String id, String kind, int width, int height, List<List<Stack>> ingredients, Stack result) {
            this(id, kind, width, height, ingredients, result, 0f);
        }
    }
    public record HostItem(String key, String name, int stackLimit, int maxDamage) {}
    private final ClassLoader loader;
    private final Class<?> stackType, itemType;
    private final Object registry;
    private final Method itemOfStack, nameOfItem, itemByName, copyStack;
    private List<Object> catalog = List.of();
    private List<Stack> catalogSnapshots;
    private Map<String, List<Stack>> catalogByKey;
    private final Map<Stack, Object> originals = new HashMap<>();
    /** The first original of each variant. Callers often build stacks with another count or an empty name. */
    private final Map<Variant, Object> originalsByVariant = new HashMap<>();
    private record Variant(String key, int metadata, String tag) {
        static Variant of(Stack stack) { return new Variant(stack.key(), stack.metadata(), stack.tag()); }
    }
    /** Development only: "scan" restores the linear lookup for A/B runs; "verify" checks the index against a full scan. */
    private final String indexMode = System.getProperty("mfcl.debug.materializeIndex", "");
    private final MaterializeIndexCheck indexCheck = new MaterializeIndexCheck();
    private final List<String> diagnostics = new ArrayList<>();
    private boolean ready;
    private List<Object> ownCrafting;
    private List<FurnaceRecipe> ownFurnace;
    private record FurnaceRecipe(Object input, Object output, float experience) {}
    private final Map<Object, HostItem> hostItems = new IdentityHashMap<>();
    private final Map<String, Object> hostItemsByKey = new HashMap<>();
    private Class<?> facadeType;
    private Map<String, float[]> iconLayout = Map.of();
    private LegacyIconResolver iconResolver = new LegacyIconResolver(Map.of());
    private Map<String, LegacySpriteTextures.Texture> iconTextures = Map.of();
    private boolean contextInitialized;
    private LegacyClientResources.Context resourceContext;
    private final List<Path> resourcePacks;

    private final List<Consumer<List<Object>>> catalogListeners = new ArrayList<>();
    public LegacyClientData(ClassLoader loader) throws Exception { this(loader, List.of()); }
    public LegacyClientData(ClassLoader loader, List<Path> resourcePacks) throws Exception {
        this.loader = loader; this.resourcePacks = List.copyOf(resourcePacks);
        stackType = loader.loadClass("net.minecraft.item.ItemStack"); itemType = loader.loadClass("net.minecraft.item.Item");
        registry = itemType.getField("field_150901_e").get(null);
        itemOfStack = stackType.getMethod("func_77973_b"); copyStack = stackType.getMethod("func_77946_l");
        nameOfItem = registry.getClass().getMethod("func_148750_c", Object.class);
        itemByName = registry.getClass().getMethod("func_82594_a", String.class);
    }

    public ClassLoader loader() { return loader; }
    public Object legacyItem(String key) throws Exception {
        Object represented = LegacyHostRegistry.item(loader, key);
        return represented == null ? itemByName.invoke(registry, key) : represented;
    }
    public Map<String, float[]> iconLayout() { return iconLayout; }
    public List<LegacyVertexStream.Primitive> resolveIcons(List<LegacyVertexStream.Primitive> primitives) { return iconResolver.resolve(primitives); }
    public byte[] iconTexture(String name) { var value=iconTextures.get(name);return value==null?null:value.png().clone(); }
    public String iconTextureMetadata(String name) { var value=iconTextures.get(name);return value==null?null:value.metadata(); }
    /** The original icon whose synthetic atlas cell contains the given legacy UV, or null. */
    public String iconAt(float u, float v) {
        for (var entry : iconLayout.entrySet()) {
            float[] cell = entry.getValue();
            if (u >= cell[0] - 1.0e-4f && u <= cell[2] + 1.0e-4f && v >= cell[1] - 1.0e-4f && v <= cell[3] + 1.0e-4f) return entry.getKey();
        }
        return null;
    }
    public synchronized void initializeContext(Path gameDir, List<String> languageResources) throws Exception {
        initializeContext(gameDir, languageResources, ClientSession.offline());
    }
    public synchronized void initializeContext(Path gameDir, List<String> languageResources, ClientSession session) throws Exception {
        inLegacy(() -> {
            if (contextInitialized) { addLanguages(languageResources); return null; }
            // A typed old client context supplies settings and data; it never starts an old window or game loop.
            Class<?> mcType = loader.loadClass("net.minecraft.client.Minecraft");
            Field unsafeField = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe"); unsafeField.setAccessible(true);
            Object unsafe = unsafeField.get(null);
            Object client = unsafe.getClass().getMethod("allocateInstance", Class.class).invoke(unsafe, mcType);
            Object oldSession = loader.loadClass("net.minecraft.util.Session")
                    .getConstructor(String.class, String.class, String.class, String.class)
                    .newInstance(session.name(), session.uuid(), session.accessToken(), session.type());
            setField(mcType, client, "field_71449_j", oldSession);
            setField(mcType, client, "field_71432_P", client);
            setField(mcType, client, "field_71412_D", gameDir.toFile());
            resourceContext = LegacyClientResources.create(loader, resourcePacks);
            setField(mcType, client, "field_110451_am", resourceContext.manager());
            Class<?> fmlClient = loader.loadClass("cpw.mods.fml.client.FMLClientHandler");
            setField(fmlClient, fmlClient.getMethod("instance").invoke(null), "client", client);
            setField(mcType, client, "field_71474_y", loader.loadClass("net.minecraft.client.settings.GameSettings").getConstructor().newInstance());
            setField(mcType, client, "field_71428_T", loader.loadClass("net.minecraft.util.Timer").getConstructor(float.class).newInstance(20f));
            setField(mcType, client, "field_71424_I", loader.loadClass("net.minecraft.profiler.Profiler").getConstructor().newInstance());
            setField(mcType, client, "field_71443_c", 854); setField(mcType, client, "field_71440_d", 480);
            Class<?> fontType = loader.loadClass("net.minecraft.client.gui.FontRenderer");
            Object font = unsafe.getClass().getMethod("allocateInstance", Class.class).invoke(unsafe, fontType);
            int[] widths = new int[256];
            try (var in = loader.getResourceAsStream("assets/minecraft/textures/font/ascii.png")) {
                if (in == null) throw new IllegalStateException("Legacy font texture missing");
                var image = javax.imageio.ImageIO.read(in); int cell = image.getWidth() / 16;
                for (int character = 0; character < 256; character++) {
                    int right = cell - 1;
                    scan: for (; right >= 0; right--) for (int y = 0; y < cell; y++)
                        if ((image.getRGB(character % 16 * cell + right, character / 16 * cell + y) >>> 24) > 16) break scan;
                    widths[character] = (right + 1) * 8 / cell + 1;
                }
                widths[32] = 4;
            }
            setField(fontType, font, "field_78286_d", widths); setField(fontType, font, "field_78288_b", 9);
            try (var in = loader.getResourceAsStream("assets/minecraft/font/glyph_sizes.bin")) {
                setField(fontType, font, "field_78287_e", in == null ? new byte[65536] : in.readAllBytes());
            }
            setField(mcType, client, "field_71466_p", font); setField(mcType, client, "field_71464_q", font);
            setField(mcType,client,"field_71456_v",loader.loadClass("net.minecraft.client.gui.GuiIngame").getConstructor(mcType).newInstance(client));
            Object textureManager = loader.loadClass("net.minecraft.client.renderer.texture.TextureManager")
                    .getConstructor(loader.loadClass("net.minecraft.client.resources.IResourceManager"))
                    .newInstance(mcType.getMethod("func_110442_L").invoke(client));
            setField(mcType, client, "field_71446_o", textureManager);
            Class<?> renderManagerType = loader.loadClass("net.minecraft.client.renderer.entity.RenderManager");
            Object renderManager = renderManagerType.getField("field_78727_a").get(null);
            Object heldRenderer = loader.loadClass("net.minecraft.client.renderer.ItemRenderer").getConstructor(mcType).newInstance(client);
            Class<?> gameRendererType = loader.loadClass("net.minecraft.client.renderer.EntityRenderer");
            Object gameRenderer = unsafe.getClass().getMethod("allocateInstance",Class.class).invoke(unsafe,gameRendererType);
            setField(gameRendererType,gameRenderer,"field_78516_c",heldRenderer);
            setField(mcType,client,"field_71460_t",gameRenderer);
            if (renderManager != null) {
                setField(renderManagerType,renderManager,"field_78721_f",heldRenderer);
                setField(renderManagerType, renderManager, "field_78724_e", textureManager);
                setField(renderManagerType, renderManager, "field_78736_p", font);
                setField(renderManagerType, renderManager, "field_78733_k", mcType.getField("field_71474_y").get(client));
            }
            Class<?> dispatcherType = loader.loadClass("net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher");
            Object dispatcher = dispatcherType.getField("field_147556_a").get(null);
            setField(dispatcherType, dispatcher, "field_147553_e", textureManager);
            setField(dispatcherType, dispatcher, "field_147557_n", font);
            Class<?> soundHandlerType=loader.loadClass("net.minecraft.client.audio.SoundHandler");
            Object soundHandler=unsafe.getClass().getMethod("allocateInstance",Class.class).invoke(unsafe,soundHandlerType);
            // Initializes bookkeeping only; never reload/start the old audio engine.
            Object soundManager=loader.loadClass("net.minecraft.client.audio.SoundManager")
                    .getConstructor(soundHandlerType,loader.loadClass("net.minecraft.client.settings.GameSettings"))
                    .newInstance(soundHandler,mcType.getField("field_71474_y").get(client));
            setField(soundHandlerType,soundHandler,"field_147694_f",soundManager);
            setField(mcType, client, "field_147127_av", soundHandler);
            setField(mcType, client, "field_135017_as", loader.loadClass("net.minecraft.client.resources.LanguageManager")
                    .getConstructor(loader.loadClass("net.minecraft.client.resources.data.IMetadataSerializer"), String.class).newInstance(null, "en_US"));
            Class<?> localeType = loader.loadClass("net.minecraft.client.resources.Locale"); Object locale = localeType.getConstructor().newInstance();
            Method loadLanguage = localeType.getDeclaredMethod("func_135021_a", java.io.InputStream.class); loadLanguage.setAccessible(true);
            for (String resource : languageResources) {
                try (var in = loader.getResourceAsStream(resource)) { if (in != null) loadLanguage.invoke(locale, in); }
                try (var in = loader.getResourceAsStream(resource)) { if (in != null) loader.loadClass("net.minecraft.util.StringTranslate").getMethod("inject", java.io.InputStream.class).invoke(null, in); }
            }
            setField(loader.loadClass("net.minecraft.client.resources.I18n"), null, "field_135054_a", locale);
            prepareIconMetadata();
            contextInitialized = true;
            return null;
        });
    }
    private void addLanguages(List<String> resources) throws Exception {
        Class<?> i18n = loader.loadClass("net.minecraft.client.resources.I18n");
        Field field = i18n.getDeclaredField("field_135054_a"); field.setAccessible(true); Object locale = field.get(null);
        Method method = locale.getClass().getDeclaredMethod("func_135021_a", java.io.InputStream.class); method.setAccessible(true);
        for (String resource : resources) {
            try (var in = loader.getResourceAsStream(resource)) { if (in != null) method.invoke(locale, in); }
            try (var in = loader.getResourceAsStream(resource)) { if (in != null) loader.loadClass("net.minecraft.util.StringTranslate").getMethod("inject", java.io.InputStream.class).invoke(null, in); }
        }
    }
    public synchronized void loadRegistryCatalog() throws Exception {
        inLegacy(() -> {
            var values = new ArrayList<Object>();
            Class<?> tab = loader.loadClass("net.minecraft.creativetab.CreativeTabs");
            Method variants = itemType.getMethod("func_150895_a", itemType, tab, List.class);
            for (Object item : (Iterable<?>) registry) {
                var entries = new ArrayList<Object>(); variants.invoke(item, item, null, entries);
                if (entries.isEmpty()) entries.add(stackType.getConstructor(itemType, int.class, int.class).newInstance(item, 1, 0));
                values.addAll(entries);
            }
            replaceCatalog(values); return null;
        });
    }
    public synchronized void replaceCatalog(List<?> values) {
        for (Object value : values) if (!stackType.isInstance(value)) throw new IllegalArgumentException("Stack belongs to a different runtime");
        catalog = new ArrayList<>(values); catalogSnapshots = null; catalogByKey = null; ready = true;
    }
    public synchronized void onCatalogChanged(Consumer<List<Object>> listener) { catalogListeners.add(Objects.requireNonNull(listener)); }
    public synchronized List<Stack> catalog() throws Exception {
        requireReady();
        return inLegacy(() -> {
            if (catalogSnapshots == null) {
                var values = new ArrayList<Stack>(); for (Object value : catalog) values.add(snapshot(value));
                catalogSnapshots = List.copyOf(values);
            }
            return catalogSnapshots;
        });
    }
    public synchronized boolean ready() { return ready; }
    public synchronized List<Stack> catalog(String key) throws Exception {
        if (catalogByKey == null) {
            var grouped = new HashMap<String, List<Stack>>();
            for (var stack : catalog()) grouped.computeIfAbsent(stack.key(), ignored -> new ArrayList<>()).add(stack);
            grouped.replaceAll((name, values) -> List.copyOf(values));
            catalogByKey = grouped;
        }
        return catalogByKey.getOrDefault(key, List.of());
    }
    public Map<String, Object> registrySummary() throws Exception {
        return inLegacy(() -> {
            Object blocks = loader.loadClass("net.minecraft.block.Block").getField("field_149771_c").get(null);
            return Map.of("itemsByNamespace", namespaces(registry), "blocksByNamespace", namespaces(blocks), "catalogStacks", catalog.size());
        });
    }
    private Map<String, Integer> namespaces(Object registry) throws Exception {
        var names = new TreeMap<String, Integer>();
        var keyOf = registry.getClass().getMethod("func_148750_c", Object.class);
        for (Object value : (Iterable<?>) registry) {
            String key = (String) keyOf.invoke(registry, value);
            String namespace = key != null && key.contains(":") ? key.substring(0, key.indexOf(':')) : "minecraft";
            names.merge(namespace, 1, Integer::sum);
        }
        return Map.copyOf(names);
    }
    public void close() throws java.io.IOException { LegacyHostTools.close(loader); LegacyHostRegistry.close(loader); if (resourceContext != null) resourceContext.close(); }
    public Object copyLegacyStack(Object value) throws Exception { return copyStack.invoke(value); }
    public String displayName(Stack value) throws Exception {
        return inLegacy(()->(String)stackType.getMethod("func_82833_r").invoke(materialize(value)));
    }
    public int stackLimit(Stack value) throws Exception {
        return inLegacy(()->(int)stackType.getMethod("func_77976_d").invoke(materialize(value)));
    }
    public List<String> tooltip(Stack value,Object player,boolean advanced) throws Exception {
        return inLegacy(()->{
            @SuppressWarnings("unchecked") var lines=(List<String>)stackType.getMethod("func_82840_a",loader.loadClass("net.minecraft.entity.player.EntityPlayer"),boolean.class).invoke(materialize(value),player,advanced);
            return List.copyOf(lines);
        });
    }
    /** Evaluate the original item's per-pass color on the full stack, including its opaque old NBT. */
    public int itemColor(Stack value, int pass) throws Exception {
        return inLegacy(() -> {
            Object stack = materialize(value);
            return (int)itemType.getMethod("func_82790_a", stackType, int.class).invoke(itemOfStack.invoke(stack), stack, pass);
        });
    }
    private String currentLanguage;
    private long hostLanguageRevision = -1;
    private final Map<String, Map<String, String>> supplements = new HashMap<>(), overrides = new HashMap<>();
    private long supplementRevision, appliedSupplementRevision = -1;
    /** Fallback translations; they only fill keys the original files of that language leave undefined. */
    public synchronized void supplement(String language, Map<String, String> entries) { addTranslations(supplements, language, entries); }
    /** Corrections; they replace whatever the original files define. */
    public synchronized void override(String language, Map<String, String> entries) { addTranslations(overrides, language, entries); }
    private void addTranslations(Map<String, Map<String, String>> target, String language, Map<String, String> entries) {
        var copy = Map.copyOf(entries);
        if (copy.isEmpty()) return;
        target.computeIfAbsent(legacyCode(language), ignored -> new HashMap<>()).putAll(copy);
        supplementRevision++;
    }
    /** Keys of the original en_US files that the original files of the language do not define. */
    public synchronized Set<String> missingKeys(String language) throws Exception {
        return inLegacy(() -> {
            var result = new TreeSet<>(originalLanguage("en_US").keySet());
            result.removeAll(originalLanguage(legacyCode(language)).keySet());
            return Collections.unmodifiableSet(result);
        });
    }
    private static String legacyCode(String code) {
        int split = code.indexOf('_');
        return split < 0 ? code : code.substring(0, split).toLowerCase(java.util.Locale.ROOT) + "_" + code.substring(split + 1).toUpperCase(java.util.Locale.ROOT);
    }
    @SuppressWarnings("unchecked")
    private Map<String, String> originalLanguage(String oldCode) throws Exception {
        Class<?> localeType = loader.loadClass("net.minecraft.client.resources.Locale");
        Object locale = localeType.getConstructor().newInstance();
        localeType.getMethod("func_135022_a", loader.loadClass("net.minecraft.client.resources.IResourceManager"), List.class)
                .invoke(locale, resourceContext.manager(), List.of(oldCode));
        Field translations = localeType.getDeclaredField("field_135032_a"); translations.setAccessible(true);
        return (Map<String, String>) translations.get(locale);
    }
    public synchronized void language(String code) throws Exception {
        long revision = LegacyHostRegistry.revision(loader);
        if (code.equals(currentLanguage) && revision == hostLanguageRevision && supplementRevision == appliedSupplementRevision) return;
        inLegacy(() -> {
            String oldCode = legacyCode(code);
            Class<?> localeType = loader.loadClass("net.minecraft.client.resources.Locale");
            Object locale = localeType.getConstructor().newInstance();
            localeType.getMethod("func_135022_a", loader.loadClass("net.minecraft.client.resources.IResourceManager"), List.class)
                    .invoke(locale, resourceContext.manager(), oldCode.equals("en_US") ? List.of("en_US") : List.of("en_US", oldCode));
            setField(loader.loadClass("net.minecraft.client.resources.I18n"), null, "field_135054_a", locale);
            Field translations = localeType.getDeclaredField("field_135032_a"); translations.setAccessible(true);
            var table = (Map<String, String>) translations.get(locale);
            // Same order as Forge's LanguageManager reload: resource packs, then names registered through FML.
            Class<?> fmlLanguages = loader.loadClass("cpw.mods.fml.common.registry.LanguageRegistry");
            Object fml = fmlLanguages.getMethod("instance").invoke(null);
            fmlLanguages.getMethod("mergeLanguageTable", Map.class, String.class).invoke(fml, table, oldCode);
            var own = supplements.get(oldCode);
            if (own != null) {
                // Keys this language really defines (its files or FML names) win; en_US fallback text does not.
                var defined = new HashSet<>(oldCode.equals("en_US") ? table.keySet() : originalLanguage(oldCode).keySet());
                Field registered = fmlLanguages.getDeclaredField("modLanguageData"); registered.setAccessible(true);
                var fmlNames = (Map<?, ?>) ((Map<?, ?>) registered.get(fml)).get(oldCode);
                if (fmlNames != null) fmlNames.keySet().forEach(key -> defined.add(key.toString()));
                own.forEach((key, value) -> { if (!defined.contains(key)) table.put(key, value); });
            }
            table.putAll(overrides.getOrDefault(oldCode, Map.of()));
            table.putAll(LegacyHostRegistry.translations(loader));
            loader.loadClass("net.minecraft.util.StringTranslate").getMethod("func_135063_a", Map.class).invoke(null, table);
            Class<?> minecraft = loader.loadClass("net.minecraft.client.Minecraft");
            Object client = minecraft.getMethod("func_71410_x").invoke(null);
            Object settings = minecraft.getField("field_71474_y").get(client);
            setField(settings.getClass(), settings, "field_74363_ab", oldCode);
            currentLanguage = code;
            hostLanguageRevision = revision;
            appliedSupplementRevision = supplementRevision;
            return null;
        });
    }
    private void prepareIconMetadata() throws Exception {
        Class<?> register = loader.loadClass("net.minecraft.client.renderer.texture.IIconRegister");
        Class<?> atlas = loader.loadClass("net.minecraft.client.renderer.texture.TextureMap");
        Class<?> sprite = loader.loadClass("net.minecraft.client.renderer.texture.TextureAtlasSprite");
        Object blocks = metadataAtlas(atlas, 0, "textures/blocks"), items = metadataAtlas(atlas, 1, "textures/items");
        Class<?> minecraft = loader.loadClass("net.minecraft.client.Minecraft");
        Object client = minecraft.getMethod("func_71410_x").invoke(null);
        setField(minecraft, client, "field_147128_au", blocks);
        Object manager = minecraft.getField("field_71446_o").get(client);
        Field textures = manager.getClass().getDeclaredField("field_110585_a"); textures.setAccessible(true);
        @SuppressWarnings("unchecked") var textureObjects = (Map<Object, Object>) textures.get(manager);
        textureObjects.put(atlas.getField("field_110575_b").get(null), blocks);
        textureObjects.put(atlas.getField("field_110576_c").get(null), items);
        Class<?> block = loader.loadClass("net.minecraft.block.Block");
        for (Object b : (Iterable<?>) block.getField("field_149771_c").get(null)) block.getMethod("func_149651_a", register).invoke(b, blocks);
        Method atlasOfItem = itemType.getMethod("func_94901_k");
        for (Object i : (Iterable<?>) registry) itemType.getMethod("func_94581_a", register)
                .invoke(i, (int) atlasOfItem.invoke(i) == 0 ? blocks : items);
        for(Object map:List.of(blocks,items))LegacySpriteTextures.event(loader,map,"Pre");

        // Keep real TextureAtlasSprite instances, including entries installed by custom mod loaders.
        Field entries = atlas.getDeclaredField("field_110574_e"); entries.setAccessible(true);
        Field missing = atlas.getDeclaredField("field_94249_f"); missing.setAccessible(true);
        Field uploaded = atlas.getDeclaredField("field_94252_e"); uploaded.setAccessible(true);
        Map<String, List<Object>> icons = new TreeMap<>();
        Map<String,LegacySpriteTextures.Texture> images = new LinkedHashMap<>();
        for (Object map : List.of(blocks, items)) {
            images.putAll(LegacySpriteTextures.load(loader,resourceContext.manager(),(Map<?,?>)entries.get(map)));
            for (var entry : ((Map<?, ?>) entries.get(map)).entrySet())
                icons.computeIfAbsent((String) entry.getKey(), ignored -> new ArrayList<>()).add(entry.getValue());
            icons.computeIfAbsent("missingno", ignored -> new ArrayList<>()).add(missing.get(map));
            // Atlas lookups must return the same sprites as registration, without uploading a legacy GL texture.
            @SuppressWarnings("unchecked") var lookup = (Map<Object, Object>) uploaded.get(map);
            lookup.putAll((Map<?, ?>) entries.get(map));
        }
        Map<String, float[]> layout = new LinkedHashMap<>();
        Method width = sprite.getMethod("func_110966_b", int.class), height = sprite.getMethod("func_110969_c", int.class);
        Method initialize = sprite.getMethod("func_110971_a", int.class, int.class, int.class, int.class, boolean.class);
        int cell = 0;
        for (var entry : icons.entrySet()) {
            // A synthetic atlas cell identifies the source texture of captured geometry without a GL stitch.
            int x = cell % 16 * 16, y = cell / 16 * 16; cell++;
            for (Object value : entry.getValue()) {
                int w=(int)sprite.getMethod("func_94211_a").invoke(value),h=(int)sprite.getMethod("func_94216_b").invoke(value);
                if(w<=0)w=16;if(h<=0)h=16;
                width.invoke(value, w); height.invoke(value, h);
                initialize.invoke(value, w*16, h*16, x/16*w, y/16*h, false);
            }
        }
        for(Object map:List.of(blocks,items))LegacySpriteTextures.event(loader,map,"Post");
        for(var entry:icons.entrySet()) {
            Object value = entry.getValue().get(0);
            layout.put(entry.getKey(), new float[]{(float) sprite.getMethod("func_94209_e").invoke(value),
                    (float) sprite.getMethod("func_94206_g").invoke(value), (float) sprite.getMethod("func_94212_f").invoke(value),
                    (float) sprite.getMethod("func_94210_h").invoke(value)});
        }
        iconLayout = Map.copyOf(layout);
        iconResolver = new LegacyIconResolver(iconLayout);
        iconTextures = Map.copyOf(images);
    }
    private Object metadataAtlas(Class<?> atlas, int type, String path) throws Exception {
        // -1 skips the constructor's automatic registry/renderer scan before the client is fully connected.
        Object value = atlas.getConstructor(int.class, String.class).newInstance(-1, path);
        setField(atlas, value, "field_94255_a", type);
        return value;
    }
    public void refreshIconMetadata() throws Exception { inLegacy(() -> { prepareIconMetadata(); return null; }); }

    private void setField(Class<?> owner, Object target, String name, Object value) throws Exception {
        loader.loadClass("github.com.gengyoubo.mfcl.legacy1710.ModernEnumSupport")
                .getMethod("setFailsafeFieldValue", Field.class, Object.class, Object.class).invoke(null, owner.getDeclaredField(name), target, value);
    }
    public synchronized int importHostItems(List<HostItem> items) throws Exception {
        requireReady();
        return inLegacy(() -> {
            if (facadeType == null) facadeType = LegacyItemFacades.define(loader);
            var next = new ArrayList<>(catalog); int imported = 0;
            for (HostItem item : items) {
                String key = "mfcl_host:" + item.key().replace(':', '/');
                if (hostItemsByKey.containsKey(key)) continue;
                Object facade = facadeType.getConstructor(String.class, int.class, int.class).newInstance(item.name(), item.stackLimit(), item.maxDamage());
                hostItems.put(facade, new HostItem(key, item.name(), item.stackLimit(), item.maxDamage())); hostItemsByKey.put(key, facade);
                Object stack = stackType.getConstructor(itemType, int.class, int.class).newInstance(facade, 1, 0);
                next.add(stack); imported++;
            }
            catalog = List.copyOf(next);
            catalogSnapshots = null;
            catalogByKey = null;
            for (var listener : catalogListeners) listener.accept(List.copyOf(catalog));
            return imported;
        });
    }
    public synchronized void preserveCrafting(List<Object> recipes) {ownCrafting=List.copyOf(recipes);}
    public boolean tagsEqual(Stack first,Stack second) throws Exception {return inLegacy(()->(boolean)stackType.getMethod("func_77970_a",stackType,stackType).invoke(null,materialize(first),materialize(second)));}
    /** Build the replacement entirely before publishing it; reloads replace rather than append. */
    @SuppressWarnings("unchecked")
    public synchronized int projectHostRecipes(List<HostRecipe> recipes) throws Exception {
        requireReady();
        return inLegacy(() -> {
            Class<?> craftingType = loader.loadClass("net.minecraft.item.crafting.CraftingManager");
            Object crafting = craftingType.getMethod("func_77594_a").invoke(null);
            List<Object> target = (List<Object>) craftingType.getMethod("func_77592_b").invoke(crafting);
            if (ownCrafting == null) {
                ownCrafting = new ArrayList<>();
                for (Object recipe : target) {
                    Object output = loader.loadClass("net.minecraft.item.crafting.IRecipe").getMethod("func_77571_b").invoke(recipe);
                    if (output != null && !snapshot(output).key().startsWith("minecraft:")) ownCrafting.add(recipe);
                }
            }
            var replacement = new ArrayList<>(ownCrafting);
            var furnace = new ArrayList<FurnaceRecipe>();
            for (HostRecipe recipe : recipes) {
                Object output = materialize(recipe.result());
                if (recipe.kind().equals("smelting")) {
                    if (recipe.ingredients().isEmpty() || recipe.ingredients().get(0).isEmpty()) continue;
                    for (Stack ingredient : recipe.ingredients().get(0)) furnace.add(new FurnaceRecipe(materialize(ingredient), copyStack.invoke(output), recipe.experience()));
                } else if (recipe.kind().equals("shaped")) {
                    if (recipe.width() < 1 || recipe.width() > 3 || recipe.height() < 1 || recipe.height() > 3 || recipe.ingredients().size() != recipe.width() * recipe.height())
                        throw new IllegalArgumentException("Invalid crafting grid: " + recipe.id());
                    Class<?> type = loader.loadClass("net.minecraftforge.oredict.ShapedOreRecipe");
                    Object created = type.getConstructor(stackType, Object[].class).newInstance(output, new Object[]{"a", 'a', output});
                    Object[] input = new Object[recipe.ingredients().size()];
                    for (int i = 0; i < input.length; i++) input[i] = alternatives(recipe.ingredients().get(i));
                    setField(type, created, "input", input); setField(type, created, "width", recipe.width()); setField(type, created, "height", recipe.height());
                    replacement.add(created);
                } else if (recipe.kind().equals("shapeless")) {
                    Class<?> type = loader.loadClass("net.minecraftforge.oredict.ShapelessOreRecipe");
                    Object created = type.getConstructor(stackType, Object[].class).newInstance(output, new Object[]{output});
                    var input = new ArrayList<Object>();
                    for (var ingredient : recipe.ingredients()) { Object converted = alternatives(ingredient); if (converted == null) throw new IllegalArgumentException("Empty shapeless ingredient"); input.add(converted); }
                    setField(type, created, "input", input); replacement.add(created);
                } else throw new IllegalArgumentException("Unsupported host recipe: " + recipe.kind());
            }
            Class<?> furnaceType = loader.loadClass("net.minecraft.item.crafting.FurnaceRecipes");
            Object furnaceOwner = furnaceType.getMethod("func_77602_a").invoke(null);
            Map<Object, Object> furnaceTarget = (Map<Object, Object>) furnaceType.getMethod("func_77599_b").invoke(furnaceOwner);
            if (ownFurnace == null) {
                var captured = new ArrayList<FurnaceRecipe>();
                var experience = furnaceType.getMethod("func_151398_b", stackType);
                for (var entry : furnaceTarget.entrySet()) {
                    if (entry.getKey() == null || entry.getValue() == null) continue;
                    captured.add(new FurnaceRecipe(copyStack.invoke(entry.getKey()), copyStack.invoke(entry.getValue()), (float) experience.invoke(furnaceOwner, entry.getKey())));
                }
                ownFurnace = captured;
            }
            Field experienceField = furnaceType.getDeclaredField("field_77605_c"); experienceField.setAccessible(true);
            Map<Object, Object> experienceTarget = (Map<Object, Object>) experienceField.get(furnaceOwner);
            var addSmelting = furnaceType.getMethod("func_151394_a", stackType, stackType, float.class);
            target.clear(); target.addAll(replacement);
            furnaceTarget.clear(); experienceTarget.clear();
            for (var stored : ownFurnace) addSmelting.invoke(furnaceOwner, copyStack.invoke(stored.input()), copyStack.invoke(stored.output()), stored.experience());
            for (var projected : furnace) addSmelting.invoke(furnaceOwner, projected.input(), projected.output(), projected.experience());
            return recipes.size();
        });
    }
    private void requireReady() { if (!ready) throw new IllegalStateException("Legacy item catalog is not ready"); }
    private Object alternatives(List<Stack> values) throws Exception {
        if (values.isEmpty()) return null;
        if (values.size() == 1) return materialize(values.get(0));
        var result = new ArrayList<Object>(); for (Stack value : values) result.add(materialize(value)); return result;
    }
    public synchronized Object materialize(Stack value) throws Exception {
        Object existing = originals.get(value);
        if (existing == null) existing = variantOriginal(value);
        else if (indexMode.equals("verify")) { indexCheck.exactHits++; reportIndexCheck(); }
        Object result;
        if (existing != null) result = copyStack.invoke(existing);
        else {
            Object item = hostItemsByKey.get(value.key());
            if (item != null && !value.tag().isEmpty()) throw new UnsupportedOperationException("Host item NBT needs a host-specific mapping: " + value.key());
            if (item == null) item = legacyItem(value.key());
            if (item == null) throw new IllegalArgumentException("Unknown legacy item " + value.key());
            result = stackType.getConstructor(itemType, int.class, int.class).newInstance(item, value.count(), value.metadata());
            if (!value.tag().isEmpty()) {
                Object nbt = loader.loadClass("net.minecraft.nbt.JsonToNBT").getMethod("func_150315_a", String.class).invoke(null, value.tag());
                stackType.getField("field_77990_d").set(result, nbt);
            }
        }
        stackType.getField("field_77994_a").setInt(result, value.count()); return result;
    }
    private Object variantOriginal(Stack value) {
        if (indexMode.equals("scan")) return originals.entrySet().stream().filter(e -> e.getKey().key().equals(value.key())
                && e.getKey().metadata() == value.metadata() && e.getKey().tag().equals(value.tag())).map(Map.Entry::getValue).findFirst().orElse(null);
        Object indexed = originalsByVariant.get(Variant.of(value));
        if (indexMode.equals("verify")) verifyIndex(value, indexed);
        return indexed;
    }
    /** Counters from "verify" mode. A mismatch means the index and a full scan could disagree for that variant. */
    public record MaterializeIndexStats(long exactHits, long variantHits, long misses, long entriesVisited,
                                        long ambiguousVariants, long mismatches, long failures, int originals, int variants) {}
    private static final class MaterializeIndexCheck {
        private static final int MAX_LOGGED = 20;
        private static final long REPORT_NANOS = 10_000_000_000L;
        long exactHits, variantHits, misses, entriesVisited, ambiguousVariants, mismatches, failures;
        long lastReport = System.nanoTime();
    }
    public synchronized MaterializeIndexStats materializeIndexStats() {
        var check = indexCheck;
        return new MaterializeIndexStats(check.exactHits, check.variantHits, check.misses, check.entriesVisited,
                check.ambiguousVariants, check.mismatches, check.failures, originals.size(), originalsByVariant.size());
    }
    /** Never changes the lookup's result or throws: it only records what a full scan of the same variant would see. */
    private void verifyIndex(Stack value, Object indexed) {
        var check = indexCheck;
        try {
            var variant = Variant.of(value);
            int candidates = 0;
            boolean differs = false;
            for (var entry : originals.entrySet()) {
                check.entriesVisited++;
                if (!variant.equals(Variant.of(entry.getKey()))) continue;
                candidates++;
                if (indexed != null && !sameOriginal(indexed, entry.getValue())) differs = true;
            }
            if (indexed == null) check.misses++; else check.variantHits++;
            if (candidates > 1) check.ambiguousVariants++;
            if (indexed == null ? candidates > 0 : candidates == 0 || differs) {
                if (check.mismatches++ < MaterializeIndexCheck.MAX_LOGGED) com.mojang.logging.LogUtils.getLogger().warn(
                        "MFCL materialize index mismatch: key={}, metadata={}, tagLength={}, indexed={}, candidates={}, contentDiffers={}",
                        value.key(), value.metadata(), value.tag() == null ? -1 : value.tag().length(), indexed != null, candidates, differs);
            }
        } catch (ReflectiveOperationException | RuntimeException failure) {
            if (check.failures++ < MaterializeIndexCheck.MAX_LOGGED) com.mojang.logging.LogUtils.getLogger().warn("MFCL materialize index check failed for {}", value.key(), failure);
        }
        reportIndexCheck();
    }
    /** Item, damage and full NBT, ignoring count. The tag string alone can hide differences in old NBT. */
    private boolean sameOriginal(Object first, Object second) throws ReflectiveOperationException {
        var damage = stackType.getMethod("func_77960_j");
        return itemOfStack.invoke(first) == itemOfStack.invoke(second) && damage.invoke(first).equals(damage.invoke(second))
                && (boolean) stackType.getMethod("func_77970_a", stackType, stackType).invoke(null, first, second);
    }
    private void reportIndexCheck() {
        long now = System.nanoTime();
        if (now - indexCheck.lastReport < MaterializeIndexCheck.REPORT_NANOS) return;
        indexCheck.lastReport = now;
        com.mojang.logging.LogUtils.getLogger().info("MFCL materialize index check: {}", materializeIndexStats());
    }
    /** Adds host presentation tags to a copy using the old NBT implementation, preserving old indexed lists. */
    public Stack renderTags(Stack source,Integer repairCost,Integer color,String name) throws Exception {
        return inLegacy(()->{
            Object stack=materialize(source);
            var compound=loader.loadClass("net.minecraft.nbt.NBTTagCompound");
            var field=stackType.getField("field_77990_d");Object tag=field.get(stack);
            if(tag==null){tag=compound.getConstructor().newInstance();field.set(stack,tag);}
            if(repairCost!=null)compound.getMethod("func_74768_a",String.class,int.class).invoke(tag,"RepairCost",repairCost);
            if(color!=null||name!=null) {
                Object display=compound.getMethod("func_74775_l",String.class).invoke(tag,"display");
                if(color!=null)compound.getMethod("func_74768_a",String.class,int.class).invoke(display,"color",color);
                if(name!=null)compound.getMethod("func_74778_a",String.class,String.class).invoke(display,"Name",name);
                compound.getMethod("func_74782_a",String.class,loader.loadClass("net.minecraft.nbt.NBTBase")).invoke(tag,"display",display);
            }
            return snapshot(stack);
        });
    }
    /** Replaces a stack's tag using the shared binary NBT format (old indexed SNBT is not modern SNBT). */
    public Stack withTag(Stack source,byte[] compressed) throws Exception {
        return inLegacy(()->{
            Object stack=materialize(source);
            Object tag=loader.loadClass("net.minecraft.nbt.CompressedStreamTools").getMethod("func_74796_a",java.io.InputStream.class)
                    .invoke(null,new java.io.ByteArrayInputStream(compressed));
            stackType.getField("field_77990_d").set(stack,tag);return snapshot(stack);
        });
    }
    public synchronized Stack snapshot(Object value) throws Exception {
        Object item = itemOfStack.invoke(value);
        Object nbt = stackType.getField("field_77990_d").get(value);
        HostItem host = hostItems.get(item);
        String represented = LegacyHostRegistry.name(loader, item);
        Stack stack = new Stack(represented != null ? represented : host == null ? (String) nameOfItem.invoke(registry, item) : host.key(),
                (Integer) stackType.getMethod("func_77960_j").invoke(value), stackType.getField("field_77994_a").getInt(value),
                host == null ? (String) stackType.getMethod("func_82833_r").invoke(value) : host.name(), nbt == null ? "" : nbt.toString());
        if (!originals.containsKey(stack)) {
            Object copy = copyStack.invoke(value);
            originals.put(stack, copy);
            originalsByVariant.putIfAbsent(Variant.of(stack), copy);
        }
        return stack;
    }
    public synchronized <T> T inLegacy(Callable<T> work) throws Exception {
        Thread thread = Thread.currentThread(); ClassLoader before = thread.getContextClassLoader();
        ClassLoader previousDomain = LegacyWorldGraphics.enterLegacy(loader);
        try { thread.setContextClassLoader(loader); return work.call(); }
        finally { LegacyWorldGraphics.leaveLegacy(previousDomain); thread.setContextClassLoader(before); }
    }
}
