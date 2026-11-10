package github.com.gengyoubo.mfcl.legacy1710;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Changes only bytes defined by the private legacy LaunchClassLoader. */
public final class ModernJavaTransformer implements IClassTransformer {
    @Override public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null) return null;
        if (!name.equals("cpw.mods.fml.common.Loader") && !name.equals("net.minecraftforge.common.util.EnumHelper")
                && !name.equals("cpw.mods.fml.common.registry.ObjectHolderRef") && !name.equals("cpw.mods.fml.common.discovery.ModDiscoverer")
                && !name.equals("net.minecraftforge.common.ForgeVersion") && !name.equals("cpw.mods.fml.common.registry.GameData")
                && !name.equals("net.minecraft.item.Item") && !name.equals("net.minecraft.block.Block")
                && !name.equals("net.minecraft.potion.Potion")) return bytes;
        ClassNode node = new ClassNode(); new ClassReader(bytes).accept(node, 0);
        if (name.equals("net.minecraft.potion.Potion")) {
            boolean found = false;
            for (FieldNode field : node.fields) {
                if (field.name.equals("field_76425_a") && field.desc.equals("[Lnet/minecraft/potion/Potion;")
                        && (field.access & Opcodes.ACC_STATIC) != 0) {
                    field.access &= ~Opcodes.ACC_FINAL;
                    found = true;
                    break;
                }
            }
            if (!found) throw new IllegalStateException("Missing legacy Potion.field_76425_a registry array");
        }
        java.util.List<MethodNode> wrappers = new java.util.ArrayList<MethodNode>();
        for (MethodNode method : node.methods) {
            String bridge = null;
            if (name.equals("net.minecraft.item.Item")) {
                if (method.name.equals("func_150891_b")) bridge = "itemId";
                if (method.name.equals("func_150899_d")) bridge = "itemById";
                if (method.name.equals("func_150898_a")) bridge = "itemFromBlock";
            } else if (name.equals("net.minecraft.block.Block")) {
                if (method.name.equals("func_149682_b")) bridge = "blockId";
                if (method.name.equals("func_149729_e")) bridge = "blockById";
            } else if (name.equals("cpw.mods.fml.common.registry.GameData") && method.name.equals("getUniqueName")) {
                bridge = method.desc.startsWith("(Lnet/minecraft/item/Item;") ? "uniqueItemName" : "uniqueBlockName";
            }
            if (bridge != null) {
                MethodNode wrapper = new MethodNode(method.access, method.name, method.desc, method.signature, null);
                Type argument = Type.getArgumentTypes(method.desc)[0], result = Type.getReturnType(method.desc);
                wrapper.instructions.add(new LdcInsnNode(Type.getObjectType(node.name)));
                wrapper.instructions.add(new VarInsnNode(argument.getOpcode(Opcodes.ILOAD), 0));
                wrapper.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "github/com/gengyoubo/mfcl/bridge/LegacyHostRegistry", bridge,
                        "(Ljava/lang/Class;" + (argument.getSort() == Type.INT ? "I" : "Ljava/lang/Object;") + ")" + (result.getSort() == Type.INT ? "I" : "Ljava/lang/Object;"), false));
                if (result.getSort() == Type.OBJECT) wrapper.instructions.add(new TypeInsnNode(Opcodes.CHECKCAST, result.getInternalName()));
                wrapper.instructions.add(new InsnNode(result.getOpcode(Opcodes.IRETURN)));
                method.name = "mfcl$original$" + method.name; wrappers.add(wrapper);
            }
            if (name.equals("cpw.mods.fml.common.registry.GameData") && (method.name.equals("registerItem") || method.name.equals("registerBlock"))) {
                method.instructions.insert(new MethodInsnNode(Opcodes.INVOKESTATIC, "github/com/gengyoubo/mfcl/legacy1710/LegacyRegistrationSeal", "check", "()V", false));
            }
            if (name.equals("net.minecraftforge.common.ForgeVersion") && method.name.equals("startVersionCheck")) {
                method.instructions.clear(); method.tryCatchBlocks.clear(); if (method.localVariables != null) method.localVariables.clear();
                method.instructions.add(new InsnNode(Opcodes.RETURN));
            }
            if (name.equals("cpw.mods.fml.common.discovery.ModDiscoverer") && method.name.equals("findClasspathMods")) {
                // Runtime libraries are not mod candidates. The validated direct version directory is authoritative.
                method.instructions.clear(); method.tryCatchBlocks.clear(); if (method.localVariables != null) method.localVariables.clear();
                method.instructions.add(new InsnNode(Opcodes.RETURN));
            }
            if (name.equals("cpw.mods.fml.common.Loader") && method.name.equals("identifyMods")) {
                for (AbstractInsnNode insn : method.instructions.toArray()) if (insn instanceof MethodInsnNode) {
                    MethodInsnNode call = (MethodInsnNode) insn;
                    if (call.owner.equals("java/io/File") && call.name.equals("isDirectory")) {
                        method.instructions.insertBefore(call, new InsnNode(Opcodes.POP));
                        method.instructions.set(call, new InsnNode(Opcodes.ICONST_0));
                    }
                }
            }
            if (name.equals("cpw.mods.fml.common.registry.ObjectHolderRef")) {
                if (method.name.equals("makeWritable")) {
                    method.instructions.clear(); method.tryCatchBlocks.clear(); if (method.localVariables != null) method.localVariables.clear();
                    method.instructions.add(new InsnNode(Opcodes.RETURN));
                } else if (method.name.equals("apply")) {
                    boolean tail = false;
                    for (AbstractInsnNode insn : method.instructions.toArray()) {
                        if (insn instanceof FieldInsnNode && ((FieldInsnNode) insn).name.equals("newFieldAccessor")) tail = true;
                        if (tail) method.instructions.remove(insn);
                    }
                    if (!tail) throw new IllegalStateException("Unrecognized ObjectHolderRef layout");
                    method.tryCatchBlocks.clear(); if (method.localVariables != null) method.localVariables.clear();
                    method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    method.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, node.name, "field", "Ljava/lang/reflect/Field;"));
                    method.instructions.add(new InsnNode(Opcodes.ACONST_NULL)); method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
                    method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "github/com/gengyoubo/mfcl/legacy1710/ModernEnumSupport", "setFailsafeFieldValue", "(Ljava/lang/reflect/Field;Ljava/lang/Object;Ljava/lang/Object;)V", false));
                    method.instructions.add(new InsnNode(Opcodes.RETURN));
                }
            }
            if (name.equals("cpw.mods.fml.common.Loader") && method.name.equals("initializeLoader")) {
                // Legacy FML must not list the modern mods/ folder. Point it at an empty private folder; RuntimeDriver passes
                // MFCL's validated JARs (from mods/1.7.10/ and mods/) explicitly through ModListHelper.additionalMods.
                for (AbstractInsnNode insn : method.instructions.toArray()) if (insn instanceof LdcInsnNode && "mods".equals(((LdcInsnNode) insn).cst))
                    ((LdcInsnNode) insn).cst = "mfcl/legacy-fml-mods";
            }
            if (!name.equals("net.minecraftforge.common.util.EnumHelper")) continue;
            if (method.name.equals("setup")) {
                method.instructions.clear(); method.tryCatchBlocks.clear(); if (method.localVariables != null) method.localVariables.clear();
                method.instructions.add(new InsnNode(Opcodes.ICONST_1));
                method.instructions.add(new FieldInsnNode(Opcodes.PUTSTATIC, node.name, "isSetup", "Z"));
                method.instructions.add(new InsnNode(Opcodes.RETURN));
            } else if (method.name.equals("makeEnum") || method.name.equals("setFailsafeFieldValue") || method.name.equals("cleanEnumCache")) {
                method.instructions.clear(); method.tryCatchBlocks.clear(); if (method.localVariables != null) method.localVariables.clear();
                Type[] args = Type.getArgumentTypes(method.desc); int slot = 0;
                for (Type arg : args) { method.instructions.add(new VarInsnNode(arg.getOpcode(Opcodes.ILOAD), slot)); slot += arg.getSize(); }
                method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "github/com/gengyoubo/mfcl/legacy1710/ModernEnumSupport", method.name, method.desc, false));
                method.instructions.add(new InsnNode(Type.getReturnType(method.desc).getOpcode(Opcodes.IRETURN)));
            }
        }
        node.methods.addAll(wrappers);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS); node.accept(writer); return writer.toByteArray();
    }
}
