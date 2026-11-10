package github.com.gengyoubo.mfcl.legacy1710;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;
import java.util.*;

/** Host bounds for proven world-coordinate comparisons, without moving coordinates or changing storage layouts. */
public final class WorldHeightTransformer implements IClassTransformer {
    private static final String BRIDGE="github/com/gengyoubo/mfcl/bridge/LegacyWorlds";
    private static final String PREFIX="(Lnet/minecraft/world/World;Ljava/util/Random;III";
    @Override public byte[] transform(String name,String transformedName,byte[] bytes) {
        if(bytes==null||name.startsWith("github.com.gengyoubo.mfcl."))return bytes;
        String text=new String(bytes,java.nio.charset.StandardCharsets.ISO_8859_1);
        if(!text.contains("Lnet/minecraft/world/World;")&&!text.contains("Lnet/minecraft/world/IBlockAccess;"))return bytes;
        ClassNode type=new ClassNode(Opcodes.ASM5);new ClassReader(bytes).accept(type,0);boolean changed=false;
        for(MethodNode method:type.methods){Scope scope=scope(method);if(scope!=null)changed|=rewrite(type.name,method,scope);}
        if(!changed)return bytes;
        ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);type.accept(writer);return writer.toByteArray();
    }
    private static final class Scope {
        final int world,y;final FieldInsnNode field;
        Scope(int world,int y,FieldInsnNode field){this.world=world;this.y=y;this.field=field;}
    }
    private static Scope scope(MethodNode method){
        Type[] args=Type.getArgumentTypes(method.desc);int slot=(method.access&Opcodes.ACC_STATIC)==0?1:0,world=-1,y=-1;int[] slots=new int[args.length];
        for(int i=0;i<args.length;i++){slots[i]=slot;slot+=args[i].getSize();}
        for(int i=0;i<args.length;i++)if(args[i].getDescriptor().equals("Lnet/minecraft/world/World;")||args[i].getDescriptor().equals("Lnet/minecraft/world/IBlockAccess;")){
            world=slots[i];for(int j=i+1;j+2<args.length;j++)if(args[j].getSort()==Type.INT&&args[j+1].getSort()==Type.INT&&args[j+2].getSort()==Type.INT){y=slots[j+1];break;}break;
        }
        if(world>=0)return new Scope(world,y,null);
        if((method.access&Opcodes.ACC_STATIC)==0)for(AbstractInsnNode insn:method.instructions.toArray())if(insn instanceof FieldInsnNode){
            FieldInsnNode field=(FieldInsnNode)insn;AbstractInsnNode previous=insn.getPrevious();while(previous!=null&&previous.getOpcode()<0)previous=previous.getPrevious();
            if(field.getOpcode()==Opcodes.GETFIELD&&field.name.equals("field_145850_b")&&field.desc.equals("Lnet/minecraft/world/World;")&&previous instanceof VarInsnNode&&previous.getOpcode()==Opcodes.ALOAD&&((VarInsnNode)previous).var==0)return new Scope(-1,-1,field);
        }
        return null;
    }
    private boolean rewrite(String owner,MethodNode method,Scope scope) {
        Frame<BasicValue>[] frames;
        try{frames=new Analyzer<BasicValue>(new Coordinates(scope.y)).analyze(owner,method);}
        catch(AnalyzerException unsupported){return false;}
        AbstractInsnNode[] instructions=method.instructions.toArray();Map<AbstractInsnNode,Integer> limits=new IdentityHashMap<AbstractInsnNode,Integer>();boolean changed=false;
        boolean coordinateContract=method.desc.startsWith(PREFIX)||scope.y>=0&&Arrays.asList(
                "func_149742_c","func_77648_a","onItemUseFirst","func_149674_a","func_149727_a",
                "func_149695_a","func_149689_a","func_149749_a","func_149726_b").contains(method.name);
        for(int i=0;i<instructions.length&&!coordinateContract;i++)if(instructions[i] instanceof MethodInsnNode&&frames[i]!=null){
            MethodInsnNode call=(MethodInsnNode)instructions[i];Type[] args=Type.getArgumentTypes(call.desc);
            if(!call.owner.startsWith("net/minecraft/world/")||args.length<3||args[0].getSort()!=Type.INT||args[1].getSort()!=Type.INT||args[2].getSort()!=Type.INT)continue;
            int at=frames[i].getStackSize()-args.length;
            if(at>=0&&value(frames[i].getStack(at)).y==0&&value(frames[i].getStack(at+1)).y==1&&value(frames[i].getStack(at+2)).y==0)coordinateContract=true;
        }
        if(!coordinateContract)return false;
        for(int i=0;i<instructions.length;i++) {
            AbstractInsnNode instruction=instructions[i];Frame<BasicValue> frame=frames[i];
            if(frame==null||!(instruction instanceof JumpInsnNode))continue;
            int op=instruction.getOpcode();int stack=frame.getStackSize();
            if(op>=Opcodes.IFLT&&op<=Opcodes.IFLE&&stack>0&&value(frame.getStack(stack-1)).y==1){
                method.instructions.insertBefore(instruction,bound(scope,false,0));
                ((JumpInsnNode)instruction).setOpcode(op+(Opcodes.IF_ICMPLT-Opcodes.IFLT));changed=true;
            }else if(op>=Opcodes.IF_ICMPEQ&&op<=Opcodes.IF_ICMPLE&&stack>1){
                Tracked a=value(frame.getStack(stack-2)),b=value(frame.getStack(stack-1));
                Tracked bound=a.y==1&&b.y==0?b:b.y==1&&a.y==0?a:null;
                if(bound==null)continue;
                // Only literal boundary expressions opposite a Y coordinate; size constants/array lengths stay intact.
                for(AbstractInsnNode limit:bound.upper)limits.put(limit,integer(limit));
                if(op>=Opcodes.IF_ICMPLT&&bound.literal!=null&&(bound.literal==0||bound.literal==1)&&bound.origin!=null)limits.put(bound.origin,bound.literal);
            }
        }
        // A shared local may also size a buffer or encode a field. Never expand those fixed layouts.
        Set<AbstractInsnNode> unsafe=new HashSet<AbstractInsnNode>();
        for(int i=0;i<instructions.length;i++)if(frames[i]!=null){
            AbstractInsnNode instruction=instructions[i];int op=instruction.getOpcode(),count=0;
            if(op==Opcodes.NEWARRAY||op==Opcodes.ANEWARRAY||op==Opcodes.PUTSTATIC||op==Opcodes.PUTFIELD)count=1;
            else if(instruction instanceof MultiANewArrayInsnNode)count=((MultiANewArrayInsnNode)instruction).dims;
            else if(instruction instanceof MethodInsnNode)count=Type.getArgumentTypes(((MethodInsnNode)instruction).desc).length;
            else if(op>=Opcodes.IMUL&&op<=Opcodes.LXOR)count=2;
            for(int n=0;n<count&&n<frames[i].getStackSize();n++){
                Tracked consumed=value(frames[i].getStack(frames[i].getStackSize()-1-n));unsafe.addAll(consumed.upper);if(consumed.origin!=null)unsafe.add(consumed.origin);
            }
        }
        for(AbstractInsnNode instruction:unsafe)limits.remove(instruction);
        for(Map.Entry<AbstractInsnNode,Integer> entry:limits.entrySet()) {
            int literal=entry.getValue();boolean upper=literal>=255;
            method.instructions.insertBefore(entry.getKey(),bound(scope,upper,upper?literal-256:literal));
            method.instructions.remove(entry.getKey());changed=true;
        }
        return changed;
    }
    private static InsnList bound(Scope scope,boolean upper,int offset){
        InsnList code=new InsnList();code.add(new VarInsnNode(Opcodes.ALOAD,scope.world>=0?scope.world:0));
        if(scope.field!=null)code.add(new FieldInsnNode(Opcodes.GETFIELD,scope.field.owner,scope.field.name,scope.field.desc));
        code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,upper?"maxBuildHeight":"minBuildHeight","(Ljava/lang/Object;)I",false));
        if(offset!=0){code.add(new LdcInsnNode(offset));code.add(new InsnNode(Opcodes.IADD));}return code;
    }
    private static Integer integer(AbstractInsnNode node){
        int op=node.getOpcode();if(op>=Opcodes.ICONST_M1&&op<=Opcodes.ICONST_5)return op-Opcodes.ICONST_0;
        if(node instanceof IntInsnNode&&(op==Opcodes.BIPUSH||op==Opcodes.SIPUSH))return ((IntInsnNode)node).operand;
        if(node instanceof LdcInsnNode&&((LdcInsnNode)node).cst instanceof Integer)return (Integer)((LdcInsnNode)node).cst;
        return null;
    }
    private static final class Tracked extends BasicValue {
        final int y;final Integer literal;final AbstractInsnNode origin;final Set<AbstractInsnNode> upper;
        Tracked(Type type,int y,Integer literal,AbstractInsnNode origin,Set<AbstractInsnNode> upper){super(type);this.y=y;this.literal=literal;this.origin=origin;this.upper=upper;}
        @Override public boolean equals(Object other){if(!(other instanceof Tracked))return false;Tracked t=(Tracked)other;return super.equals(t)&&y==t.y&&Objects.equals(literal,t.literal)&&origin==t.origin&&upper.equals(t.upper);}
        @Override public int hashCode(){return super.hashCode()+31*y+upper.hashCode();}
    }
    private static Tracked value(BasicValue value){return value instanceof Tracked?(Tracked)value:new Tracked(value.getType(),0,null,null,Collections.<AbstractInsnNode>emptySet());}
    private static BasicValue tracked(BasicValue base,int y,Set<AbstractInsnNode> upper){return base==null?null:new Tracked(base.getType(),y,null,null,upper);}
    private static final class Coordinates extends BasicInterpreter {
        final int ySlot;
        Coordinates(int ySlot){super(Opcodes.ASM5);this.ySlot=ySlot;}
        @Override public BasicValue newOperation(AbstractInsnNode instruction)throws AnalyzerException{
            BasicValue base=super.newOperation(instruction);Integer literal=integer(instruction);
            return base==null?null:new Tracked(base.getType(),0,literal,instruction,literal!=null&&(literal==255||literal==256)?Collections.singleton(instruction):Collections.<AbstractInsnNode>emptySet());
        }
        @Override public BasicValue copyOperation(AbstractInsnNode instruction,BasicValue source)throws AnalyzerException{
            if(instruction.getOpcode()==Opcodes.ILOAD&&((VarInsnNode)instruction).var==ySlot)return tracked(source,1,Collections.<AbstractInsnNode>emptySet());
            return source;
        }
        @Override public BasicValue unaryOperation(AbstractInsnNode instruction,BasicValue source)throws AnalyzerException{
            BasicValue base=super.unaryOperation(instruction,source);Tracked t=value(source);
            if(instruction instanceof FieldInsnNode&&instruction.getOpcode()==Opcodes.GETFIELD&&((FieldInsnNode)instruction).name.equals("field_145848_d"))return tracked(base,1,Collections.<AbstractInsnNode>emptySet());
            return instruction.getOpcode()==Opcodes.IINC?tracked(base,t.y,t.upper):tracked(base,0,Collections.<AbstractInsnNode>emptySet());
        }
        @Override public BasicValue binaryOperation(AbstractInsnNode instruction,BasicValue left,BasicValue right)throws AnalyzerException{
            BasicValue base=super.binaryOperation(instruction,left,right);Tracked a=value(left),b=value(right);int op=instruction.getOpcode();
            if(op!=Opcodes.IADD&&op!=Opcodes.ISUB)return tracked(base,0,Collections.<AbstractInsnNode>emptySet());
            Set<AbstractInsnNode> upper=new HashSet<AbstractInsnNode>(a.upper);upper.addAll(b.upper);
            return tracked(base,op==Opcodes.IADD?a.y+b.y:a.y-b.y,upper);
        }
        @Override public BasicValue merge(BasicValue left,BasicValue right){
            if(left.equals(right))return left;
            BasicValue base=super.merge(new BasicValue(left.getType()),new BasicValue(right.getType()));Tracked a=value(left),b=value(right);
            Set<AbstractInsnNode> upper=new HashSet<AbstractInsnNode>(a.upper);upper.addAll(b.upper);
            return tracked(base,a.y==b.y?a.y:0,upper);
        }
    }
}
