package github.com.gengyoubo.tcmodern.tc4.mfclpatch;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraftforge.fml.loading.FMLLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.SimpleRemapper;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Temporary backport of MFCL 1.0.8 fixes onto the CurseForge MFCL 0.2.10 file (see {@code tc4-modern/src/mfclPatch}).
 *
 * <p>Only active when the installed MFCL is exactly that unpatched release. Each replaced class gets the members of its
 * patched copy, which the build stored under {@code mfclpatch/replaced} and reobfuscated together with this mod.
 * Remove this package, the mixin config and the {@code mfclPatch*} source sets once a fixed MFCL is on CurseForge.
 */
public final class MfclPatchPlugin implements IMixinConfigPlugin {
    static final Logger LOGGER = LogManager.getLogger("TC4 MFCL patch");
    static final String VERSION = "0.2.10";
    /** A class the patch adds; an MFCL that already contains it is newer than 0.2.10 or already patched. */
    private static final String ADDED_MARKER = "github/com/gengyoubo/mfcl/content/LegacyFluidRegistry.class";
    private static final String MFCL = "github/com/gengyoubo/mfcl/";
    private static final String REPLACED = "github/com/gengyoubo/tcmodern/tc4/mfclpatch/replaced/";
    private static final String GENERATED = "github.com.gengyoubo.tcmodern.tc4.mfclpatch.mixin.Patched";
    private static volatile boolean active;
    private Set<String> replaced = Set.of();

    /** Whether the backport is applied in this game instance. */
    public static boolean active() { return active; }

    @Override public void onLoad(String mixinPackage) {
        String reason = inactiveReason();
        if (reason != null) { LOGGER.info("MFCL backport disabled: {}", reason); return; }
        try (InputStream in = resource("/tc4modern/mfclpatch/replaced.txt")) {
            var names = new java.util.TreeSet<String>();
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                line = line.trim();
                if (!line.isEmpty() && !line.startsWith("#")) names.add(line);
            }
            replaced = Set.copyOf(names);
        } catch (IOException failure) {
            throw new IllegalStateException("TC4 MFCL backport index is unreadable", failure);
        }
        active = true;
        LOGGER.info("MFCL {} detected: applying TC4's backport of MFCL fixes to {} classes", VERSION, replaced.size());
    }

    private static String inactiveReason() {
        var file = FMLLoader.getLoadingModList().getModFileById("mfcl");
        if (file == null) return "MFCL is not installed";
        String version = file.getMods().get(0).getVersion().toString();
        if (!version.equals(VERSION)) return "MFCL " + version + " is not " + VERSION;
        if (Files.exists(file.getFile().findResource(ADDED_MARKER))) return "MFCL " + version + " already contains the fixes";
        return null;
    }

    @Override public String getRefMapperConfig() { return null; }

    @Override public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return active && (!mixinClassName.startsWith(GENERATED) || replaced.contains(targetClassName));
    }

    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    /** The generated mixins exist only when the build found classes of that kind to replace. */
    @Override public List<String> getMixins() {
        if (!active) return null;
        List<String> mixins = new ArrayList<>();
        for (String name : new String[]{"PatchedClasses", "PatchedInterfaces"})
            if (MfclPatchPlugin.class.getResource("/github/com/gengyoubo/tcmodern/tc4/mfclpatch/mixin/" + name + ".class") != null) mixins.add(name);
        return mixins;
    }

    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    @Override public void postApply(String targetClassName, ClassNode target, String mixinClassName, IMixinInfo mixinInfo) {
        if (!mixinClassName.startsWith(GENERATED)) return;
        String name = targetClassName.replace('.', '/');
        String copyName = REPLACED + name.substring(MFCL.length());
        ClassNode patched = new ClassNode();
        try (InputStream in = resource("/" + copyName + ".class")) {
            new ClassReader(in.readAllBytes()).accept(new ClassRemapper(patched, new SimpleRemapper(Map.of(copyName, name))), 0);
        } catch (IOException failure) {
            throw new IllegalStateException("Missing TC4 MFCL backport class " + copyName, failure);
        }
        if (!patched.name.equals(target.name)) throw new IllegalStateException("Backport class mismatch: " + patched.name);
        if (!java.util.Objects.equals(patched.superName, target.superName))
            throw new IllegalStateException("Backport changes the superclass of " + targetClassName);
        // Annotations stay the original's: the copy's were removed so Forge never scans it as a mod class.
        target.version = patched.version;
        target.access = patched.access;
        target.signature = patched.signature;
        target.interfaces = patched.interfaces;
        target.sourceFile = patched.sourceFile;
        target.outerClass = patched.outerClass;
        target.outerMethod = patched.outerMethod;
        target.outerMethodDesc = patched.outerMethodDesc;
        target.innerClasses = patched.innerClasses;
        target.nestHostClass = null;
        target.nestMembers = null;
        target.permittedSubclasses = null;
        target.recordComponents = patched.recordComponents;
        target.fields = patched.fields;
        target.methods = patched.methods;
    }

    static InputStream resource(String path) throws IOException {
        InputStream in = MfclPatchPlugin.class.getResourceAsStream(path);
        if (in == null) throw new IOException("Resource not found: " + path);
        return in;
    }
}
