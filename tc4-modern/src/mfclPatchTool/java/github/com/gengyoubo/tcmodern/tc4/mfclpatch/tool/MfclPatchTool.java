package github.com.gengyoubo.tcmodern.tc4.mfclpatch.tool;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.SimpleRemapper;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InnerClassNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Turns MFCL classes compiled from the backported sources into a load-time patch for the unmodified MFCL 0.2.10 mod.
 *
 * <ul>
 *   <li>A class 0.2.10 already has is <em>replaced</em>: a copy is stored under {@link #REPLACED} with only its own
 *   name changed, so it is an ordinary class of this mod that ForgeGradle reobfuscates; at runtime the Mixin plugin
 *   renames it back and moves its members into the original class.</li>
 *   <li>A class 0.2.10 lacks is <em>added</em>: Forge cannot add classes to another mod's package, so it is relocated
 *   under {@link #ADDED} and every reference to it is rewritten.</li>
 * </ul>
 * Relocation crosses packages and modules, so every patch class and member is made public and the nest and sealed
 * attributes are dropped. The tool fails the build on any patch the original classes could not link against.
 *
 * <p>Arguments: {@code <patch classes dir> <MFCL 0.2.10 jar (same mappings)> <output classes dir> <output resources dir>}
 */
public final class MfclPatchTool {
    static final String MFCL = "github/com/gengyoubo/mfcl/";
    static final String BASE = "github/com/gengyoubo/tcmodern/tc4/mfclpatch/";
    static final String REPLACED = BASE + "replaced/";
    static final String ADDED = BASE + "added/";
    static final String MIXINS = BASE + "mixin/";
    static final String INDEX = "tc4modern/mfclpatch/replaced.txt";

    private MfclPatchTool() {}

    public static void main(String[] args) throws IOException {
        if (args.length != 4) throw new IllegalArgumentException("MfclPatchTool <patch classes> <mfcl jar> <out classes> <out resources>");
        Path patchDir = Path.of(args[0]), mfclJar = Path.of(args[1]), outClasses = Path.of(args[2]), outResources = Path.of(args[3]);

        Map<String, byte[]> patch = readDir(patchDir);
        Map<String, byte[]> original = readJar(mfclJar);
        for (String name : patch.keySet())
            if (!name.startsWith(MFCL)) throw new IllegalStateException("Patch class outside MFCL: " + name);

        Set<String> replaced = new TreeSet<>(), added = new TreeSet<>();
        for (String name : patch.keySet()) (original.containsKey(name) ? replaced : added).add(name);

        Map<String, ClassNode> patched = new LinkedHashMap<>();
        for (var entry : patch.entrySet()) patched.put(entry.getKey(), node(entry.getValue()));
        Map<String, ClassNode> originals = new LinkedHashMap<>();
        for (var entry : original.entrySet()) originals.put(entry.getKey(), node(entry.getValue()));

        List<String> problems = new ArrayList<>();
        // Unpatched 0.2.10 classes keep calling replaced classes: every member they use must survive.
        for (var entry : originals.entrySet()) {
            if (patched.containsKey(entry.getKey())) continue;
            for (String[] ref : references(entry.getValue())) {
                ClassNode target = patched.get(ref[0]);
                if (target == null) continue;
                ClassNode before = originals.get(ref[0]);
                boolean declaredBefore = declares(before, ref[1], ref[2]);
                if (declaredBefore && !declares(target, ref[1], ref[2]))
                    problems.add(entry.getKey() + " uses " + ref[0] + "." + ref[1] + ref[2] + ", which the patch removes");
            }
        }
        // Relocated classes leave MFCL's package: they may only use public members of the classes left unpatched.
        Set<String> widenOnly = new TreeSet<>();
        for (String name : added) for (String[] ref : references(patched.get(name))) {
            if (patched.containsKey(ref[0]) || !originals.containsKey(ref[0])) continue;
            ClassNode owner = originals.get(ref[0]);
            if ((owner.access & Opcodes.ACC_PUBLIC) == 0 || !isPublic(owner, ref[1], ref[2])) widenOnly.add(ref[0]);
        }
        // Reflection by name cannot follow relocation.
        Set<String> addedNames = new HashSet<>();
        for (String name : added) { addedNames.add(name); addedNames.add(name.replace('/', '.')); }
        for (var entry : patched.entrySet()) for (MethodNode method : entry.getValue().methods)
            for (AbstractInsnNode insn : method.instructions)
                if (insn instanceof LdcInsnNode ldc && ldc.cst instanceof String s && addedNames.contains(s))
                    problems.add(entry.getKey() + "." + method.name + " names relocated class " + s + " in a string");
        if (!problems.isEmpty()) throw new IllegalStateException("MFCL patch cannot be applied:\n  " + String.join("\n  ", problems));

        for (String name : widenOnly) { replaced.add(name); patched.put(name, node(original.get(name))); }

        Map<String, String> relocation = new TreeMap<>();
        for (String name : added) relocation.put(name, ADDED + name.substring(MFCL.length()));

        clean(outClasses); clean(outResources);
        List<String> classTargets = new ArrayList<>(), interfaceTargets = new ArrayList<>();
        for (var entry : patched.entrySet()) {
            String name = entry.getKey();
            boolean isAdded = added.contains(name);
            var mapping = new LinkedHashMap<>(relocation);
            if (!isAdded) mapping.put(name, REPLACED + name.substring(MFCL.length()));
            ClassNode remapped = new ClassNode();
            entry.getValue().accept(new ClassRemapper(remapped, new SimpleRemapper(mapping)));
            widen(remapped);
            if (!isAdded) {
                // The original class keeps its own annotations; the copy must not be picked up by Forge's scanner.
                remapped.visibleAnnotations = null; remapped.invisibleAnnotations = null;
                (((entry.getValue().access & Opcodes.ACC_INTERFACE) != 0) ? interfaceTargets : classTargets).add(name.replace('/', '.'));
            }
            write(outClasses.resolve(remapped.name + ".class"), bytes(remapped));
        }
        writeMixin(outClasses, MIXINS + "PatchedClasses", classTargets, false);
        writeMixin(outClasses, MIXINS + "PatchedInterfaces", interfaceTargets, true);
        List<String> index = new ArrayList<>();
        for (String name : replaced) index.add(name.replace('/', '.'));
        index.add(0, "# MFCL 0.2.10 classes replaced at load time; generated by MfclPatchTool");
        write(outResources.resolve(INDEX), (String.join("\n", index) + "\n").getBytes(StandardCharsets.UTF_8));
        System.out.printf("MFCL patch: %d replaced (%d widened only), %d added%n", replaced.size(), widenOnly.size(), added.size());
    }

    /** Owner, name and descriptor of every field and method reference. */
    static List<String[]> references(ClassNode node) {
        List<String[]> result = new ArrayList<>();
        for (MethodNode method : node.methods) for (AbstractInsnNode insn : method.instructions) {
            if (insn instanceof MethodInsnNode m) result.add(new String[]{m.owner, m.name, m.desc});
            else if (insn instanceof FieldInsnNode f) result.add(new String[]{f.owner, f.name, f.desc});
        }
        return result;
    }

    static boolean declares(ClassNode node, String name, String desc) {
        if (desc.startsWith("(")) { for (MethodNode m : node.methods) if (m.name.equals(name) && m.desc.equals(desc)) return true; }
        else for (FieldNode f : node.fields) if (f.name.equals(name) && f.desc.equals(desc)) return true;
        return false;
    }

    static boolean isPublic(ClassNode node, String name, String desc) {
        if (desc.startsWith("(")) { for (MethodNode m : node.methods) if (m.name.equals(name) && m.desc.equals(desc)) return (m.access & Opcodes.ACC_PUBLIC) != 0; }
        else for (FieldNode f : node.fields) if (f.name.equals(name) && f.desc.equals(desc)) return (f.access & Opcodes.ACC_PUBLIC) != 0;
        return true; // inherited: checked against its declaring class, which is outside MFCL or public already
    }

    /** Public everything; relocated nestmates and sealed subtypes would otherwise fail access checks. */
    static void widen(ClassNode node) {
        node.access = publicAccess(node.access);
        node.nestHostClass = null; node.nestMembers = null; node.permittedSubclasses = null;
        for (MethodNode m : node.methods) if (!m.name.equals("<clinit>")) m.access = publicAccess(m.access);
        for (FieldNode f : node.fields) f.access = publicAccess(f.access);
        if (node.innerClasses != null) for (InnerClassNode inner : node.innerClasses) inner.access = publicAccess(inner.access);
    }

    static int publicAccess(int access) {
        return (access & ~(Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED)) | Opcodes.ACC_PUBLIC;
    }

    /** An empty mixin whose only purpose is to make Mixin hand these targets to the plugin's postApply. */
    static void writeMixin(Path outClasses, String name, List<String> targets, boolean isInterface) throws IOException {
        ClassWriter writer = new ClassWriter(0);
        int access = Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT | (isInterface ? Opcodes.ACC_INTERFACE : 0);
        writer.visit(Opcodes.V17, access, name, null, "java/lang/Object", null);
        AnnotationVisitor mixin = writer.visitAnnotation("Lorg/spongepowered/asm/mixin/Mixin;", false);
        AnnotationVisitor array = mixin.visitArray("targets");
        for (String target : targets) array.visit(null, target);
        array.visitEnd();
        mixin.visit("remap", Boolean.FALSE);
        mixin.visitEnd();
        if (!isInterface) {
            var init = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
            init.visitCode(); init.visitVarInsn(Opcodes.ALOAD, 0);
            init.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
            init.visitInsn(Opcodes.RETURN); init.visitMaxs(1, 1); init.visitEnd();
        }
        writer.visitEnd();
        write(outClasses.resolve(name + ".class"), writer.toByteArray());
    }

    static ClassNode node(byte[] bytes) { ClassNode node = new ClassNode(); new ClassReader(bytes).accept(node, 0); return node; }

    static byte[] bytes(ClassNode node) { ClassWriter writer = new ClassWriter(0); node.accept(writer); return writer.toByteArray(); }

    static Map<String, byte[]> readDir(Path dir) throws IOException {
        Map<String, byte[]> result = new TreeMap<>();
        try (Stream<Path> files = Files.walk(dir)) {
            for (Path file : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".class"))::iterator) {
                String name = dir.relativize(file).toString().replace('\\', '/');
                result.put(name.substring(0, name.length() - 6), Files.readAllBytes(file));
            }
        }
        return result;
    }

    static Map<String, byte[]> readJar(Path jar) throws IOException {
        Map<String, byte[]> result = new TreeMap<>();
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            for (ZipEntry entry : java.util.Collections.list(zip.entries())) {
                if (!entry.getName().endsWith(".class") || !entry.getName().startsWith(MFCL)) continue;
                try (InputStream in = zip.getInputStream(entry)) {
                    result.put(entry.getName().substring(0, entry.getName().length() - 6), in.readAllBytes());
                }
            }
        }
        return result;
    }

    static void clean(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        try (Stream<Path> files = Files.walk(dir)) {
            files.sorted((a, b) -> b.getNameCount() - a.getNameCount()).forEach(p -> {
                try { Files.delete(p); } catch (IOException e) { throw new UncheckedIOException(e); }
            });
        }
    }

    static void write(Path file, byte[] bytes) throws IOException { Files.createDirectories(file.getParent()); Files.write(file, bytes); }

}
