package github.com.gengyoubo.mfcl.legacy1710;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/**
 * Entity mirrors are snapshots of host entities. Every declaration of attackEntityFrom, setFire and addPotionEffect
 * (SRG names are unique to the Entity hierarchy) first asks the bridge whether {@code this} is such a mirror, so
 * the effect reaches the real host entity instead of a discarded copy. Other entities run their original code.
 * EntityItem also reports reads of its stack and liveness, the two ways old code reaches a dropped item it kept.
 */
public final class EntityHostTransformer implements IClassTransformer {
    private static final String HOOKS = "github/com/gengyoubo/mfcl/bridge/LegacyEntityHooks";
    public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null) return null;
        String constants = new String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1);
        if (!constants.contains("func_70097_a") && !constants.contains("func_70015_d") && !constants.contains("func_70690_d") && !constants.contains("func_70691_i") && !constants.contains("func_70618_n") && !constants.contains("func_82170_o") && !constants.contains("func_70674_bp") && !constants.contains("func_71038_i") && !constants.contains("func_96122_a") && !constants.contains("func_70893_e") && !constants.contains("func_92059_d")) return bytes;
        ClassNode node = new ClassNode(); new ClassReader(bytes).accept(node, 0);
        boolean changed = false;
        for (MethodNode m : node.methods) {
            if ((m.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE | Opcodes.ACC_STATIC)) != 0) continue;
            if(m.name.equals("func_71038_i")&&m.desc.equals("()V")) {
                for(AbstractInsnNode instruction:m.instructions.toArray())if(instruction instanceof MethodInsnNode) {
                    MethodInsnNode call=(MethodInsnNode)instruction;
                    if(call.owner.equals("net/minecraft/entity/EntityTracker")&&call.name.equals("func_151247_a")) {
                        m.instructions.set(call,new MethodInsnNode(Opcodes.INVOKESTATIC,HOOKS,"animation","(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V",false));changed=true;
                    }
                }
            }
            if(m.name.equals("func_92059_d")&&m.desc.equals("()Lnet/minecraft/item/ItemStack;")) {
                m.instructions.insert(droppedItemHook());changed=true;continue;
            }
            InsnList code = new InsnList(); LabelNode original = new LabelNode();
            code.add(new VarInsnNode(Opcodes.ALOAD, 0));
            boolean playerRule=m.name.equals("func_96122_a")&&m.desc.equals("(Lnet/minecraft/entity/player/EntityPlayer;)Z");
            code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, playerRule?"hasServerHost":"intercepts", "(Ljava/lang/Object;)Z", false));
            code.add(new JumpInsnNode(Opcodes.IFEQ, original));
            code.add(new VarInsnNode(Opcodes.ALOAD, 0));
            if(playerRule) {
                code.add(new VarInsnNode(Opcodes.ALOAD,1));
                code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,HOOKS,"canAttackPlayer","(Ljava/lang/Object;Ljava/lang/Object;)Z",false));
                code.add(new InsnNode(Opcodes.IRETURN));
            } else if (m.name.equals("func_70097_a") && m.desc.equals("(Lnet/minecraft/util/DamageSource;F)Z")) {
                code.add(new VarInsnNode(Opcodes.ALOAD, 1)); code.add(new VarInsnNode(Opcodes.FLOAD, 2));
                code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, "attack", "(Ljava/lang/Object;Ljava/lang/Object;F)Z", false));
                code.add(new InsnNode(Opcodes.IRETURN));
            } else if (m.name.equals("func_70015_d") && m.desc.equals("(I)V")) {
                code.add(new VarInsnNode(Opcodes.ILOAD, 1));
                code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, "ignite", "(Ljava/lang/Object;I)V", false));
                code.add(new InsnNode(Opcodes.RETURN));
            } else if (m.name.equals("func_70690_d") && m.desc.equals("(Lnet/minecraft/potion/PotionEffect;)V")) {
                code.add(new VarInsnNode(Opcodes.ALOAD, 1));
                code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, "effect", "(Ljava/lang/Object;Ljava/lang/Object;)V", false));
                code.add(new InsnNode(Opcodes.RETURN));
            } else if(m.name.equals("func_70691_i")&&m.desc.equals("(F)V")) {
                code.add(new VarInsnNode(Opcodes.FLOAD,1));code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,HOOKS,"heal","(Ljava/lang/Object;F)V",false));code.add(new InsnNode(Opcodes.RETURN));
            } else if((m.name.equals("func_70618_n")||m.name.equals("func_82170_o"))&&m.desc.equals("(I)V")||m.name.equals("func_70674_bp")&&m.desc.equals("()V")) {
                if(m.desc.equals("()V"))code.add(new InsnNode(Opcodes.ICONST_M1));else code.add(new VarInsnNode(Opcodes.ILOAD,1));
                code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,HOOKS,"removeEffect","(Ljava/lang/Object;I)V",false));code.add(new InsnNode(Opcodes.RETURN));
            } else if(m.name.equals("func_70893_e")&&m.desc.equals("(Z)V")){
                code.add(new VarInsnNode(Opcodes.ILOAD,1));code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,HOOKS,"sheared","(Ljava/lang/Object;Z)V",false));code.add(new InsnNode(Opcodes.RETURN));
            } else continue;
            code.add(original);
            // The jump target has the method's entry state. A frame already describing offset 0 is reused as is.
            if (needsFrame(node, m)) code.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
            m.instructions.insert(code); changed = true;
        }
        if(node.name.equals("net/minecraft/entity/item/EntityItem")) {
            // isEntityAlive is inherited, and is usually the first thing old AI asks of a retained target each tick.
            MethodNode alive=new MethodNode(Opcodes.ACC_PUBLIC,"func_70089_S","()Z",null,null);
            alive.instructions.add(droppedItemHook());alive.instructions.add(new VarInsnNode(Opcodes.ALOAD,0));
            alive.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,node.superName,"func_70089_S","()Z",false));alive.instructions.add(new InsnNode(Opcodes.IRETURN));
            node.methods.add(alive);changed=true;
        }
        if (!changed) return bytes;
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS); node.accept(writer); return writer.toByteArray();
    }
    private static InsnList droppedItemHook() {
        InsnList code=new InsnList();
        code.add(new VarInsnNode(Opcodes.ALOAD,0));code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,HOOKS,"droppedItem","(Ljava/lang/Object;)V",false));
        return code;
    }
    private static boolean needsFrame(ClassNode node, MethodNode m) {
        boolean frames = false;
        for (AbstractInsnNode instruction = m.instructions.getFirst(); instruction != null; instruction = instruction.getNext()) {
            if (instruction instanceof FrameNode) frames = true;
        }
        for (AbstractInsnNode instruction = m.instructions.getFirst(); instruction != null; instruction = instruction.getNext()) {
            if (instruction instanceof FrameNode) return false;
            if (instruction.getOpcode() >= 0) break;
        }
        // Java 6 classes without any frames are verified by type inference; adding a lone frame there is not needed.
        return frames || (node.version & 0xFFFF) >= Opcodes.V1_7;
    }
}
