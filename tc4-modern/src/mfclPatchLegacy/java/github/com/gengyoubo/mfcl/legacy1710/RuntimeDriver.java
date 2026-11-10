package github.com.gengyoubo.mfcl.legacy1710;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.IFMLSidedHandler;
import cpw.mods.fml.relauncher.*;
import github.com.gengyoubo.mfcl.legacy1710.boot.LegacyLaunchLoader;
import net.minecraft.launchwrapper.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.jar.*;

/** Runs in the legacy domain. No modern Minecraft or Forge type appears in this boundary. */
public final class RuntimeDriver {
    public Map<String, Object> boot(File gameDir, File forgeJar, List<File> mods) {
        return boot(gameDir, forgeJar, mods, new Runnable() { public void run() { } });
    }
    public Map<String, Object> boot(File gameDir, File forgeJar, List<File> mods, Runnable initializeClient) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        List<String> completed = new ArrayList<String>(); result.put("completed", completed);
        String stage = "COREMODS";
        Map<String,Double> timings = new LinkedHashMap<String,Double>(); result.put("timingsMs", timings);
        long phaseStart=System.nanoTime();
        try {
            final LaunchClassLoader loader = Launch.classLoader;
            Class<?> remapper = loader.loadClass("cpw.mods.fml.common.asm.transformers.deobf.FMLDeobfuscatingRemapper");
            Object mapper = remapper.getField("INSTANCE").get(null);
            for (String map : Arrays.asList("rawFieldMaps", "rawMethodMaps", "fieldNameMaps", "methodNameMaps")) {
                Field field = remapper.getDeclaredField(map); field.setAccessible(true); field.set(mapper, new HashMap<Object, Object>());
            }
            // CoreModManager is the actual 1614 implementation, including annotation exclusions, ordering and wrappers.
            set(CoreModManager.class, "mcDir", gameDir);
            set(CoreModManager.class, "deobfuscatedEnvironment", true);
            set(CoreModManager.class, "loadPlugins", new ArrayList<Object>());
            Method load = CoreModManager.class.getDeclaredMethod("loadCoreMod", LaunchClassLoader.class, String.class, File.class);
            load.setAccessible(true);
            for (String plugin : Arrays.asList("cpw.mods.fml.relauncher.FMLCorePlugin", "net.minecraftforge.classloading.FMLForgePlugin")) {
                if (load.invoke(null, loader, plugin, forgeJar) == null) throw new IllegalStateException("Missing root plugin " + plugin);
            }
            Class<?> outerAT = CoreModManager.class.getClassLoader().loadClass("cpw.mods.fml.common.asm.transformers.ModAccessTransformer");
            for (File mod : mods) {
                try (JarFile jar = new JarFile(mod)) {
                    outerAT.getMethod("addJar", JarFile.class).invoke(null, jar);
                    Manifest manifest = jar.getManifest();
                    String plugin = manifest == null ? null : manifest.getMainAttributes().getValue("FMLCorePlugin");
                    if (plugin != null && load.invoke(null, loader, plugin, mod) == null) throw new IllegalStateException("Failed core plugin " + plugin);
                }
            }
            Method sort = CoreModManager.class.getDeclaredMethod("sortCoreMods"); sort.setAccessible(true); sort.invoke(null);
            Field pluginsField = CoreModManager.class.getDeclaredField("loadPlugins"); pluginsField.setAccessible(true);
            @SuppressWarnings("unchecked") List<ITweaker> plugins = (List<ITweaker>) pluginsField.get(null);
            // Skip only the root sanity hook (certificate/launcher checks). Its actual containers and transformers remain.
            FMLInjectionData.containers.add("cpw.mods.fml.common.FMLContainer");
            FMLInjectionData.containers.add("net.minecraftforge.common.ForgeModContainer");
            Class<?> checker = loader.loadClass("cpw.mods.fml.common.asm.FMLSanityChecker"); set(checker, "fmlLocation", forgeJar);
            Class<?> forgePlugin = loader.loadClass("net.minecraftforge.classloading.FMLForgePlugin"); set(forgePlugin, "forgeLocation", forgeJar);
            set(forgePlugin, "RUNTIME_DEOBF", false);
            for (ITweaker wrapper : plugins) {
                Field name = wrapper.getClass().getDeclaredField("name"); name.setAccessible(true);
                String n = (String) name.get(wrapper);
                if (n.equals("FMLCorePlugin") || n.equals("FMLForgePlugin")) continue;
                wrapper.injectIntoClassLoader(loader);
            }
            for (String transformer : Arrays.asList(
                    "cpw.mods.fml.common.asm.transformers.MarkerTransformer",
                    "cpw.mods.fml.common.asm.transformers.SideTransformer",
                    "cpw.mods.fml.common.asm.transformers.EventSubscriptionTransformer",
                    "cpw.mods.fml.common.asm.transformers.AccessTransformer",
                    "net.minecraftforge.transformers.ForgeAccessTransformer",
                    "cpw.mods.fml.common.asm.transformers.ModAccessTransformer",
                    "cpw.mods.fml.common.asm.transformers.ItemStackTransformer")) loader.registerTransformer(transformer);
            loader.registerTransformer("net.minecraftforge.classloading.FluidIdTransformer");
            Field ats = CoreModManager.class.getDeclaredField("accessTransformers"); ats.setAccessible(true);
            for (Object transformer : (List<?>) ats.get(null)) loader.registerTransformer((String) transformer);
            loader.registerTransformer("github.com.gengyoubo.mfcl.legacy1710.GuiCaptureTransformer");
            loader.registerTransformer("github.com.gengyoubo.mfcl.legacy1710.ThaumcraftResearchClipTransformer");
            loader.registerTransformer("github.com.gengyoubo.mfcl.legacy1710.EntityHostTransformer");
            completed.add(stage); timings.put(stage,(System.nanoTime()-phaseStart)/1_000_000.0);phaseStart=System.nanoTime(); stage = "VANILLA_BOOTSTRAP";
            Loader.injectData(FMLInjectionData.data()); Loader.instance();
            Class<?> bootstrap = loader.loadClass("net.minecraft.init.Bootstrap"); bootstrap.getMethod("func_151354_b").invoke(null);
            completed.add(stage); timings.put(stage,(System.nanoTime()-phaseStart)/1_000_000.0);phaseStart=System.nanoTime(); stage = "FORGE_INITIALIZATION";
            IFMLSidedHandler sided = (IFMLSidedHandler) Proxy.newProxyInstance(loader, new Class<?>[]{IFMLSidedHandler.class},
                    new InvocationHandler() {
                        @Override public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                            String n = method.getName();
                            if (n.equals("getSide")) return Side.CLIENT;
                            if (n.equals("getCurrentLanguage")) {
                                // The embedded client has no vanilla language-reload loop. Its settings
                                // are kept in sync with the translation tables by LegacyClientData.
                                Class<?> minecraft = loader.loadClass("net.minecraft.client.Minecraft");
                                Object client = minecraft.getMethod("func_71410_x").invoke(null);
                                Object settings = minecraft.getField("field_71474_y").get(client);
                                return settings.getClass().getField("field_74363_ab").get(settings);
                            }
                            if (n.equals("showGuiScreen")) {
                                loader.loadClass("github.com.gengyoubo.mfcl.bridge.LegacyGuiGraphics").getMethod("showScreen",Object.class).invoke(null,args[0]);return null;
                            }
                            if (n.equals("stripSpecialChars")) return ((String) args[0]).replaceAll("(?i)\u00a7[0-9A-FK-OR]", "");
                            if (n.equals("getAdditionalBrandingInformation")) return Collections.emptyList();
                            if (n.equals("toString")) return "MFCL embedded client sided handler";
                            if (method.getReturnType() == void.class && (n.equals("haltGame") || n.equals("displayMissingMods")))
                                throw new IllegalStateException("Legacy sided handler failure: " + Arrays.toString(args));
                            if (method.getReturnType() == void.class) return null;
                            if (method.getReturnType() == boolean.class) return false;
                            throw new UnsupportedOperationException("Legacy client service is not connected: " + method);
                        }
                    });
            FMLCommonHandler.instance().beginLoading(sided);
            initializeClient.run();
            completed.add(stage); timings.put(stage,(System.nanoTime()-phaseStart)/1_000_000.0);phaseStart=System.nanoTime(); stage = "CONSTRUCTING";
            // The only mod candidates: Loader's own mods directory is an empty private folder (ModernJavaTransformer).
            ModListHelper.additionalMods.clear();
            for (File mod : mods) ModListHelper.additionalMods.put(mod.getAbsolutePath(), mod);
            Loader.instance().loadMods(); completed.add(stage); timings.put(stage,(System.nanoTime()-phaseStart)/1_000_000.0);phaseStart=System.nanoTime(); stage = "PREINITIALIZATION";
            Loader.instance().preinitializeMods(); completed.add(stage); timings.put(stage,(System.nanoTime()-phaseStart)/1_000_000.0);phaseStart=System.nanoTime(); stage = "INITIALIZATION_POSTINITIALIZATION";
            // Test-only: exercise TC4's real postInit expansion even when another coremod already enlarged the array.
            Field potionRegistry = null; Object[] beforeExpansion = null;
            if (Boolean.getBoolean("mfcl.test.potionExpansion")) {
                Class<?> potion = loader.loadClass("net.minecraft.potion.Potion");
                potionRegistry = potion.getField("field_76425_a");
                if (potion.getClassLoader() != loader || Modifier.isFinal(potionRegistry.getModifiers()))
                    throw new IllegalStateException("Legacy potion registry was not made writable");
                Object[] current = (Object[]) potionRegistry.get(null);
                int length = 112;
                for (int i = length; i < current.length; i++) if (current[i] != null)
                    throw new IllegalStateException("Cannot shrink populated potion registry for expansion test");
                beforeExpansion = (Object[]) java.lang.reflect.Array.newInstance(potion, length);
                System.arraycopy(current, 0, beforeExpansion, 0, Math.min(current.length, length));
                potionRegistry.set(null, beforeExpansion);
            }
            Loader.instance().initializeMods(); completed.add(stage); timings.put(stage,(System.nanoTime()-phaseStart)/1_000_000.0);
            if (potionRegistry != null) {
                Object[] expanded = (Object[]) potionRegistry.get(null);
                if (expanded == beforeExpansion || expanded.length != beforeExpansion.length + 8)
                    throw new IllegalStateException("TC4 did not replace and extend the potion registry");
                for (int i = 0; i < beforeExpansion.length; i++) if (beforeExpansion[i] != null && beforeExpansion[i] != expanded[i])
                    throw new IllegalStateException("TC4 expansion lost potion " + i);
                result.put("potionExpansionVerified", true);
            }
            LegacyRegistrationSeal.seal(); result.put("registrationSealed", true);
            List<String> modIds = new ArrayList<String>();
            Map<String, String> modVersions = new LinkedHashMap<String, String>();
            for (cpw.mods.fml.common.ModContainer mod : Loader.instance().getActiveModList()) {
                modIds.add(mod.getModId());
                modVersions.put(mod.getModId(), mod.getVersion());
            }
            result.put("loadedMods", modIds);
            result.put("loadedModVersions", modVersions);
            result.put("legacyFmlStarted", true);
            result.put("stage", "AVAILABLE");
        } catch (Throwable failure) {
            while (failure instanceof InvocationTargetException && failure.getCause() != null) failure = failure.getCause();
            result.put("stage", stage); result.put("legacyFmlStarted", false);
            result.put("failure", failure.toString());
            Throwable rootCause = failure;
            Set<Throwable> causes = Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>());
            while (rootCause.getCause() != null && causes.add(rootCause)) rootCause = rootCause.getCause();
            result.put("rootCause", rootCause.toString());
            StringWriter trace = new StringWriter(); failure.printStackTrace(new PrintWriter(trace)); result.put("stackTrace", trace.toString());
        }
        result.put("legacyClientReady", false);
        result.put("transformations", ((LegacyLaunchLoader) Launch.classLoader).transformationAudit());
        return result;
    }
    private static void set(Class<?> owner, String name, Object value) throws Exception {
        Field field = owner.getDeclaredField(name); field.setAccessible(true); field.set(null, value);
    }
}
