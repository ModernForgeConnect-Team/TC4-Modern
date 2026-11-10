package github.com.gengyoubo.mfcl.legacy1710.compat.thaumcraft;

import github.com.gengyoubo.mfcl.legacy1710.boot.BytecodePatches;
import github.com.gengyoubo.mfcl.legacy1710.boot.LegacyBytecodeAdapter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/**
 * TC4's Traveling Trunk keeps its items in an InventoryTrunk. An open container keeps using that inventory after the
 * trunk died (its items were already dropped) or after it was replaced, so the player could take the dropped items
 * again, and the trunk could later refill from the copy. The slot accessors are inert unless the trunk is alive and owns them.
 */
public final class ThaumcraftTrunkInventoryAdapter implements LegacyBytecodeAdapter {
    private static final String OWNER = "thaumcraft.common.entities.golems.InventoryTrunk";
    private static final String GUARD = "github/com/gengyoubo/mfcl/legacy1710/compat/thaumcraft/ThaumcraftTrunkGuard";
    public ThaumcraftTrunkInventoryAdapter() {}
    @Override public boolean targets(String className) { return className.equals(OWNER); }
    @Override public byte[] adapt(String className, byte[] bytes) {
        ClassNode node = BytecodePatches.read(bytes);
        int patched = 0;
        for (MethodNode method : node.methods) {
            if (BytecodePatches.matches(method, "func_70301_a", "(I)Lnet/minecraft/item/ItemStack;")
                    || BytecodePatches.matches(method, "func_70298_a", "(II)Lnet/minecraft/item/ItemStack;")) {
                guard(method, true);
                patched++;
            } else if (BytecodePatches.matches(method, "func_70299_a", "(ILnet/minecraft/item/ItemStack;)V")) {
                guard(method, false);
                patched++;
            }
        }
        if (patched != 3) throw new IllegalStateException("Unsupported trunk inventory layout: " + className + " (" + patched + ")");
        return BytecodePatches.write(node);
    }
    /** Prefix: {@code if (!live(this)) return null;} for getters, {@code return;} for setters. */
    private static void guard(MethodNode method, boolean returnsStack) {
        LabelNode original = new LabelNode();
        InsnList code = new InsnList();
        code.add(new VarInsnNode(Opcodes.ALOAD, 0));
        code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, GUARD, "live", "(Ljava/lang/Object;)Z", false));
        code.add(new JumpInsnNode(Opcodes.IFNE, original));
        if (returnsStack) {
            code.add(new InsnNode(Opcodes.ACONST_NULL));
            code.add(new InsnNode(Opcodes.ARETURN));
        } else {
            code.add(new InsnNode(Opcodes.RETURN));
        }
        code.add(original);
        code.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
        method.instructions.insert(code);
    }
}
