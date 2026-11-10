package github.com.gengyoubo.mfcl.legacy1710;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Marks the TC4 research-node pass; category tabs and tooltips are outside the pass. */
public final class ThaumcraftResearchClipTransformer implements IClassTransformer {
    @Override public byte[] transform(String name,String transformedName,byte[] bytes) {
        if(bytes==null||!"thaumcraft.client.gui.GuiResearchBrowser".equals(name))return bytes;
        ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);
        boolean found=false;
        for(MethodNode method:node.methods) {
            if(!method.name.equals("genResearchBackground")||!method.desc.equals("(IIF)V"))continue;
            FieldInsnNode start=null,end=null;
            for(AbstractInsnNode instruction:method.instructions.toArray())if(instruction instanceof FieldInsnNode) {
                FieldInsnNode field=(FieldInsnNode)instruction;
                if(start==null&&field.getOpcode()==Opcodes.PUTFIELD&&field.name.equals("currentHighlight")&&field.desc.equals("Lthaumcraft/api/research/ResearchItem;"))start=field;
                else if(start!=null&&field.getOpcode()==Opcodes.GETSTATIC&&field.owner.equals("thaumcraft/api/research/ResearchCategories")&&field.name.equals("researchCategories")){end=field;break;}
            }
            if(start==null||end==null)throw new IllegalStateException("Unrecognized TC4 research-node drawing pass");
            method.instructions.insert(start,marker(true));method.instructions.insertBefore(end,marker(false));found=true;
        }
        if(!found)throw new IllegalStateException("Missing TC4 research background renderer");
        ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);node.accept(writer);return writer.toByteArray();
    }
    private static InsnList marker(boolean enabled) {
        InsnList code=new InsnList();code.add(new InsnNode(enabled?Opcodes.ICONST_1:Opcodes.ICONST_0));
        code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"github/com/gengyoubo/mfcl/bridge/LegacyGuiGraphics","researchClip","(Z)V",false));return code;
    }
}
