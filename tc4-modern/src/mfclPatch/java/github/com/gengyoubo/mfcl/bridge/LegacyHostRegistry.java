package github.com.gengyoubo.mfcl.bridge;

import java.util.*;

/** Read-only host identities. Does not register content with FML or reopen its registration seal. */
public final class LegacyHostRegistry {
    private static final Map<ClassLoader, Domain> DOMAINS = new WeakHashMap<>();
    private static final class Domain {
        final Map<Object, Integer> ids = new IdentityHashMap<>();
        final Map<Integer, Object> items = new HashMap<>(), blocks = new HashMap<>();
        final Map<String, Object> itemNames = new HashMap<>(), blockNames = new HashMap<>();
        final Map<Object, String> names = new IdentityHashMap<>();
        final Map<Object, Object> blockItems = new IdentityHashMap<>();
        final Map<String, java.util.function.Supplier<String>> labels = new HashMap<>();
        long revision;
        final Set<Integer> occupied = new HashSet<>();
        Domain(ClassLoader loader) throws Exception {
            for (var entry : List.of(new String[]{"net.minecraft.item.Item", "field_150901_e"}, new String[]{"net.minecraft.block.Block", "field_149771_c"})) {
                Object registry = loader.loadClass(entry[0]).getField(entry[1]).get(null);
                var id = registry.getClass().getMethod("func_148757_b", Object.class);
                for (Object value : (Iterable<?>) registry) occupied.add((Integer)id.invoke(registry, value));
            }
        }
        int reserve(int maximum) {
            for (int id = maximum; id > 0; id--) if (occupied.add(id)) return id;
            throw new IllegalStateException("No free read-only host identity below " + maximum);
        }
    }
    private LegacyHostRegistry() { }
    private static Domain create(ClassLoader loader) throws Exception {
        var domain = DOMAINS.get(loader);
        if (domain == null) { domain = new Domain(loader); DOMAINS.put(loader, domain); }
        return domain;
    }
    public static synchronized void registerItem(ClassLoader loader, String key, Object item) throws Exception {
        if (!key.startsWith("mfcl_host:")) throw new IllegalArgumentException("Host identity namespace required");
        var domain = create(loader);
        if (domain.itemNames.containsKey(key)) throw new IllegalArgumentException("Duplicate host identity: " + key);
        // ItemStack's crafting callback indexes StatList.objectCraftStats by this ID.
        // In 1.7.10 that array has 32000 entries, despite the wider registry ID range.
        int id = domain.reserve(31999);
        domain.ids.put(item, id); domain.items.put(id, item); domain.itemNames.put(key, item); domain.names.put(item, key);
    }
    public static synchronized void registerBlock(ClassLoader loader, String key, Object block, Object item) throws Exception {
        if (!key.startsWith("mfcl_host:")) throw new IllegalArgumentException("Host identity namespace required");
        var domain = create(loader);
        if (domain.itemNames.containsKey(key) || domain.blockNames.containsKey(key)) throw new IllegalArgumentException("Duplicate host identity: " + key);
        int id = domain.reserve(4095);
        domain.ids.put(block, id); domain.ids.put(item, id); domain.items.put(id, item); domain.blocks.put(id, block);
        domain.itemNames.put(key, item); domain.blockNames.put(key, block); domain.names.put(item, key); domain.names.put(block, key);
        domain.blockItems.put(block, item);
    }
    public static synchronized Object item(ClassLoader loader, String key) { var d = DOMAINS.get(loader); return d == null ? null : d.itemNames.get(key); }
    public static synchronized Object block(ClassLoader loader, String key) { var d = DOMAINS.get(loader); return d == null ? null : d.blockNames.get(key); }
    public static synchronized String name(ClassLoader loader, Object value) { var d = DOMAINS.get(loader); return d == null ? null : d.names.get(value); }
    public static synchronized void label(ClassLoader loader, String translationKey, java.util.function.Supplier<String> value) throws Exception {
        var d = create(loader); d.labels.put(translationKey, value); d.revision++;
    }
    public static synchronized long revision(ClassLoader loader) { var d = DOMAINS.get(loader); return d == null ? 0 : d.revision; }
    public static synchronized Map<String, String> translations(ClassLoader loader) {
        var d = DOMAINS.get(loader); var result = new HashMap<String, String>();
        if (d != null) d.labels.forEach((key, value) -> result.put(key, value.get()));
        return result;
    }
    public static synchronized void close(ClassLoader loader) { DOMAINS.remove(loader); }
    private static Object original(Class<?> owner, String method, Class<?> parameter, Object argument) {
        try {
            var target = owner.getDeclaredMethod("mfcl$original$" + method, parameter); target.setAccessible(true);
            return target.invoke(null, argument);
        }
        catch (ReflectiveOperationException failure) { throw new IllegalStateException("Original registry lookup failed: " + method, failure); }
    }
    public static synchronized int itemId(Class<?> owner, Object value) {
        var d = DOMAINS.get(owner.getClassLoader());
        return d != null && d.ids.containsKey(value) ? d.ids.get(value) : (Integer)original(owner, "func_150891_b", owner, value);
    }
    public static synchronized Object itemById(Class<?> owner, int id) {
        var d = DOMAINS.get(owner.getClassLoader());
        return d != null && d.items.containsKey(id) ? d.items.get(id) : original(owner, "func_150899_d", int.class, id);
    }
    public static synchronized int blockId(Class<?> owner, Object value) {
        var d = DOMAINS.get(owner.getClassLoader());
        return d != null && d.ids.containsKey(value) ? d.ids.get(value) : (Integer)original(owner, "func_149682_b", owner, value);
    }
    public static synchronized Object blockById(Class<?> owner, int id) {
        var d = DOMAINS.get(owner.getClassLoader());
        return d != null && d.blocks.containsKey(id) ? d.blocks.get(id) : original(owner, "func_149729_e", int.class, id);
    }
    public static synchronized Object itemFromBlock(Class<?> owner, Object value) {
        var d = DOMAINS.get(owner.getClassLoader());
        if (d != null && d.blockItems.containsKey(value)) return d.blockItems.get(value);
        try { return original(owner, "func_150898_a", owner.getClassLoader().loadClass("net.minecraft.block.Block"), value); }
        catch (ClassNotFoundException failure) { throw new IllegalStateException(failure); }
    }
    private static Object uniqueName(Class<?> owner, Object value, String parameter) {
        String key = name(owner.getClassLoader(), value);
        try {
            if (key != null) return owner.getClassLoader().loadClass("cpw.mods.fml.common.registry.GameRegistry$UniqueIdentifier")
                    .getConstructor(String.class).newInstance(key);
            return original(owner, "getUniqueName", owner.getClassLoader().loadClass(parameter), value);
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
    }
    public static Object uniqueItemName(Class<?> owner, Object value) { return uniqueName(owner, value, "net.minecraft.item.Item"); }
    public static Object uniqueBlockName(Class<?> owner, Object value) { return uniqueName(owner, value, "net.minecraft.block.Block"); }
}
