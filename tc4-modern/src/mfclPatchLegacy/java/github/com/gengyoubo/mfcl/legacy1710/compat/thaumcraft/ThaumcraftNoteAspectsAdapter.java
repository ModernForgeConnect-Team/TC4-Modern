package github.com.gengyoubo.mfcl.legacy1710.compat.thaumcraft;

import github.com.gengyoubo.mfcl.legacy1710.boot.*;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/** Let a research optionally supply note aspects independently of its original point cost. */
public final class ThaumcraftNoteAspectsAdapter implements LegacyBytecodeAdapter {
    private static final String ITEM = "thaumcraft/api/research/ResearchItem";
    private static final String MANAGER = "thaumcraft/common/lib/research/ResearchManager";
    private static final String ASPECTS = "Lthaumcraft/api/aspects/AspectList;";
    public boolean targets(String name) { return name.equals(ITEM.replace('/', '.')) || name.equals(MANAGER.replace('/', '.')); }
    public byte[] adapt(String name, byte[] bytes) {
        ClassNode node = BytecodePatches.read(bytes);
        if (node.name.equals(ITEM)) {
            node.fields.add(new FieldNode(Opcodes.ACC_PUBLIC, "mfcl$noteAspects", ASPECTS, null, null));
            int primaryReads = 0;
            for (MethodNode method : node.methods) if (method.name.equals("getResearchPrimaryTag")) {
                for (AbstractInsnNode insn : method.instructions.toArray()) if (insn instanceof FieldInsnNode) {
                    FieldInsnNode field = (FieldInsnNode) insn;
                    if (field.getOpcode() == Opcodes.GETFIELD && field.owner.equals(ITEM) && field.name.equals("tags") && field.desc.equals(ASPECTS)) {
                        method.instructions.set(field, new MethodInsnNode(Opcodes.INVOKEVIRTUAL, ITEM, "mfcl$getNoteAspects", "()" + ASPECTS, false));
                        primaryReads++;
                    }
                }
            }
            if (primaryReads != 4) throw new IllegalStateException("Cannot locate TC4 research primary aspect: " + primaryReads);
            MethodNode getter = new MethodNode(Opcodes.ACC_PUBLIC, "mfcl$getNoteAspects", "()" + ASPECTS, null, null);
            LabelNode fallback = new LabelNode();
            getter.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
            getter.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, ITEM, "mfcl$noteAspects", ASPECTS));
            getter.instructions.add(new JumpInsnNode(Opcodes.IFNULL, fallback));
            getter.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
            getter.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, ITEM, "mfcl$noteAspects", ASPECTS));
            getter.instructions.add(new InsnNode(Opcodes.ARETURN));
            getter.instructions.add(fallback);
            getter.instructions.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
            getter.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
            getter.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, ITEM, "tags", ASPECTS));
            getter.instructions.add(new InsnNode(Opcodes.ARETURN));
            node.methods.add(getter);
        } else {
            int changed = 0;
            for (MethodNode method : node.methods) {
                for (AbstractInsnNode insn : method.instructions.toArray()) if (insn instanceof FieldInsnNode) {
                    FieldInsnNode field = (FieldInsnNode) insn;
                    if (field.getOpcode() == Opcodes.GETFIELD && field.owner.equals(ITEM) && field.name.equals("tags") && field.desc.equals(ASPECTS)) {
                        method.instructions.set(field, new MethodInsnNode(Opcodes.INVOKEVIRTUAL, ITEM, "mfcl$getNoteAspects", "()" + ASPECTS, false));
                        changed++;
                    }
                }
            }
            if (changed != 7) throw new IllegalStateException("Cannot locate TC4 research note and discovery aspects: " + changed);
        }
        return BytecodePatches.write(node);
    }
}
