package github.com.gengyoubo.mfcl.legacy1710.boot;

import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraft.launchwrapper.LaunchClassLoader;
import java.lang.reflect.Field;
import java.net.URL;
import java.io.IOException;
import java.util.*;

/** LaunchWrapper semantics inside a private root. Never delegates to the modern game's loader. */
public final class LegacyLaunchLoader extends LaunchClassLoader {
    private final ClassLoader root;
    private final List<String> audit = new ArrayList<String>();
    private final Map<IClassTransformer, IClassTransformer> originals = new IdentityHashMap<IClassTransformer, IClassTransformer>();
    private final Map<String,URL> apiResources=new HashMap<String,URL>();
    public void preferApiClasses(Map<String,URL> sources) throws java.net.MalformedURLException {
        for(Map.Entry<String,URL> entry:sources.entrySet()) {
            String resource=entry.getKey().replace('.','/')+".class";
            apiResources.put(resource,new URL("jar:"+entry.getValue().toExternalForm()+"!/"+resource));
        }
    }
    @Override public URL findResource(String name) {
        URL preferred=apiResources==null?null:apiResources.get(name);
        return preferred==null?super.findResource(name):preferred;
    }
    public LegacyLaunchLoader(URL[] urls, ClassLoader root) throws Exception {
        super(urls); this.root = root;
        Field exceptions = LaunchClassLoader.class.getDeclaredField("classLoaderExceptions");
        exceptions.setAccessible(true);
        @SuppressWarnings("unchecked") Set<String> set = (Set<String>) exceptions.get(this);
        set.clear();
        for (String prefix : Arrays.asList("java.", "jdk.", "sun.", "com.sun.",
                "net.minecraft.launchwrapper.", "cpw.mods.fml.relauncher.", "org.apache.logging.",
                "github.com.gengyoubo.mfcl.bridge.", "github.com.gengyoubo.mfcl.legacy1710.boot.")) set.add(prefix);
        addTransformerExclusion("github.com.gengyoubo.mfcl.legacy1710.");
        for (String prefix : Arrays.asList("cpw.mods.fml.repackage.", "cpw.mods.fml.relauncher.",
                "cpw.mods.fml.common.asm.transformers.", "cpw.mods.fml.common.patcher.",
                "net.minecraftforge.classloading.", "net.minecraftforge.transformers.")) addTransformerExclusion(prefix);
        registerTransformer("github.com.gengyoubo.mfcl.legacy1710.ModernJavaTransformer");
        registerTransformer("github.com.gengyoubo.mfcl.legacy1710.LegacyReflectionTransformer");
        registerTransformer("github.com.gengyoubo.mfcl.legacy1710.RegistryCaptureTransformer");
        registerTransformer(REDIRECTS);
        registerTransformer(HEIGHTS);
        registerBytecodeAdapter("github.com.gengyoubo.mfcl.legacy1710.boot.GuiPlatformAdapter");
        registerBytecodeAdapter("github.com.gengyoubo.mfcl.legacy1710.boot.DisplayPlatformAdapter");
        registerBytecodeAdapter("github.com.gengyoubo.mfcl.legacy1710.boot.ForgeChunkManagerAdapter");
        registerBytecodeAdapter("github.com.gengyoubo.mfcl.legacy1710.boot.FakePlayerAdapter");
    }
    private static final String REDIRECTS = "github.com.gengyoubo.mfcl.legacy1710.LegacyRedirectTransformer";
    private static final String HEIGHTS = "github.com.gengyoubo.mfcl.legacy1710.WorldHeightTransformer";
    private final List<LegacyBytecodeAdapter> bytecodeAdapters = new ArrayList<LegacyBytecodeAdapter>();
    public void registerBytecodeAdapter(String name) throws Exception {
        bytecodeAdapters.add((LegacyBytecodeAdapter) root.loadClass(name).getConstructor().newInstance());
    }
    @Override public Class<?> findClass(String name) throws ClassNotFoundException {
        boolean adapted = false;
        for (LegacyBytecodeAdapter adapter : bytecodeAdapters) if (adapter.targets(name)) adapted = true;
        if (!adapted) return super.findClass(name);
        try {
            byte[] bytes = super.getClassBytes(name);
            if (bytes == null) throw new ClassNotFoundException(name);
            for (LegacyBytecodeAdapter adapter : bytecodeAdapters) if (adapter.targets(name)) bytes = adapter.adapt(name, bytes);
            // Adapted classes skip the transformer chain; developer API call-site redirects still apply to them.
            for (IClassTransformer transformer : getTransformers()) if (transformer.getClass().getName().equals(REDIRECTS)||transformer.getClass().getName().equals(HEIGHTS)) bytes = transformer.transform(name, name, bytes);
            URL resource = findResource(name.replace('.', '/') + ".class");
            if (resource == null) throw new ClassNotFoundException(name);
            java.net.URLConnection connection = resource.openConnection();
            java.security.CodeSource source = null;
            if (connection instanceof java.net.JarURLConnection) {
                java.net.JarURLConnection jar = (java.net.JarURLConnection) connection;
                URL location = jar.getJarFileURL();
                String packageName = name.substring(0, name.lastIndexOf('.'));
                if (ClassLoader.class.getMethod("getDefinedPackage", String.class).invoke(this, packageName) == null) {
                    try { definePackage(packageName, jar.getManifest(), location); }
                    catch (IllegalArgumentException concurrent) {
                        if (ClassLoader.class.getMethod("getDefinedPackage", String.class).invoke(this, packageName) == null) throw concurrent;
                    }
                }
                source = new java.security.CodeSource(location, (java.security.CodeSigner[]) null);
                Field manifests = LaunchClassLoader.class.getDeclaredField("packageManifests"); manifests.setAccessible(true);
                @SuppressWarnings("unchecked") Map<Package, java.util.jar.Manifest> packageManifests = (Map<Package, java.util.jar.Manifest>) manifests.get(this);
                Package defined = (Package) ClassLoader.class.getMethod("getDefinedPackage", String.class).invoke(this, packageName);
                java.util.jar.Manifest manifest=jar.getManifest();
                if(manifest!=null)packageManifests.put(defined, manifest);
            }
            return defineClass(name, bytes, 0, bytes.length, source);
        } catch (IOException error) { throw new ClassNotFoundException(name, error); }
        catch (ReflectiveOperationException error) { throw new ClassNotFoundException(name, error); }
    }
    public Class<?> defineLegacyClass(String name, byte[] bytes, int offset, int length) {
        return defineClass(name, bytes, offset, length);
    }
    @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            Class<?> c = findLoadedClass(name);
            if (c == null) {
                if (name.startsWith("java.") || name.startsWith("javax.") && findResource(name.replace('.', '/') + ".class") == null
                        || name.startsWith("jdk.") || name.startsWith("sun.") || name.startsWith("com.sun.")
                        || name.startsWith("net.minecraft.launchwrapper.") || name.startsWith("cpw.mods.fml.relauncher.")
                        || name.startsWith("org.apache.logging.") || name.startsWith("github.com.gengyoubo.mfcl.bridge.")
                        || name.startsWith("github.com.gengyoubo.mfcl.legacy1710.boot.")) c = root.loadClass(name);
                else {
                    // JDK modules also expose org.w3c.dom, org.xml.sax, etc. The platform
                    // loader cannot resolve the host's Minecraft, Forge or application libraries.
                    try { c = jdkClass(name); }
                    catch (ClassNotFoundException absentPlatformClass) { c = findClass(name); }
                }
            }
            if (resolve) resolveClass(c);
            return c;
        }
    }
    private Class<?> jdkClass(String name) throws ClassNotFoundException {
        Class<?> type = root.getParent().loadClass(name);
        try {
            Object module = Class.class.getMethod("getModule").invoke(type);
            String moduleName = (String) module.getClass().getMethod("getName").invoke(module);
            // ModLauncher's boot module path also exposes ASM. Only JDK modules may
            // bypass private libraries; otherwise ASM 9 can replace legacy ASM 5.
            if (moduleName != null && (moduleName.startsWith("java.") || moduleName.startsWith("jdk."))) return type;
        } catch (ReflectiveOperationException failure) { throw new ClassNotFoundException(name, failure); }
        throw new ClassNotFoundException("Not a JDK module class: " + name);
    }
    @Override public void registerTransformer(String name) {
        int before = getTransformers().size();
        super.registerTransformer(name);
        if (getTransformers().size() != before + 1) throw new IllegalStateException("Legacy transformer registration failed: " + name);
        // LaunchWrapper returns an unmodifiable view; retain its actual pipeline for audited wrappers.
        try {
            Field field = LaunchClassLoader.class.getDeclaredField("transformers"); field.setAccessible(true);
            @SuppressWarnings("unchecked") List<IClassTransformer> pipeline = (List<IClassTransformer>) field.get(this);
            final IClassTransformer original = pipeline.get(before);
            final String transformerName = name;
            IClassTransformer audited = new IClassTransformer() {
                @Override public byte[] transform(String originalName, String transformedName, byte[] bytes) {
                    byte[] result = original.transform(originalName, transformedName, bytes);
                    if (!Arrays.equals(bytes, result)) synchronized (audit) { audit.add(transformerName + " -> " + transformedName); }
                    return result;
                }
            };
            originals.put(audited, original); pipeline.set(before, audited);
        } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
    }
    @Override public List<IClassTransformer> getTransformers() {
        List<IClassTransformer> result = new ArrayList<IClassTransformer>();
        for (IClassTransformer transformer : super.getTransformers()) result.add(originals.containsKey(transformer) ? originals.get(transformer) : transformer);
        return Collections.unmodifiableList(result);
    }
    public List<String> transformationAudit() { synchronized (audit) { return new ArrayList<String>(audit); } }
}
