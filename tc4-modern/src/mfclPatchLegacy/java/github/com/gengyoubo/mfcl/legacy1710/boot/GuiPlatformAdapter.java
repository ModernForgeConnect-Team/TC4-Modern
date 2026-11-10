package github.com.gengyoubo.mfcl.legacy1710.boot;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Replaces only the private LWJGL 2 platform classes. Unsupported native operations fail explicitly. */
public final class GuiPlatformAdapter implements LegacyBytecodeAdapter {
    private static final String API = "github/com/gengyoubo/mfcl/bridge/LegacyGuiGraphics";
    private static final String LISTS = "github/com/gengyoubo/mfcl/bridge/LegacyDisplayLists";
    private static final String DISPATCH = "github/com/gengyoubo/mfcl/bridge/LegacyRenderBridge";
    private static final String WORLD = "github/com/gengyoubo/mfcl/bridge/LegacyWorldGraphics";
    private static final java.util.Set<String> WORLD_METHODS = new java.util.HashSet<>(java.util.Arrays.asList(
            "glBegin(I)V", "glEnd()V", "glLineWidth(F)V", "glEnable(I)V", "glDisable(I)V", "glIsEnabled(I)Z", "glBlendFunc(II)V", "glDepthMask(Z)V", "glPushAttrib(I)V", "glPopAttrib()V",
            "glMatrixMode(I)V", "glTexGeni(III)V", "glTexGen(IILjava/nio/FloatBuffer;)V",
            "glVertex2f(FF)V", "glVertex2d(DD)V", "glVertex3f(FFF)V", "glVertex3d(DDD)V",
            "glTexCoord2f(FF)V", "glTexCoord2d(DD)V", "glNormal3f(FFF)V", "glNormal3d(DDD)V",
            "glColor3f(FFF)V", "glColor4f(FFFF)V", "glColor3d(DDD)V", "glColor4d(DDDD)V", "glColor3ub(BBB)V", "glColor4ub(BBBB)V",
            "glEnableClientState(I)V", "glDisableClientState(I)V",
            "glVertexPointer(IILjava/nio/FloatBuffer;)V", "glVertexPointer(IILjava/nio/DoubleBuffer;)V",
            "glTexCoordPointer(IILjava/nio/FloatBuffer;)V", "glTexCoordPointer(IILjava/nio/ShortBuffer;)V",
            "glColorPointer(IZILjava/nio/ByteBuffer;)V", "glColorPointer(IILjava/nio/FloatBuffer;)V",
            "glNormalPointer(ILjava/nio/ByteBuffer;)V", "glDrawArrays(III)V"));
    public boolean targets(String name) { return name.equals("org.lwjgl.opengl.GL11") || name.equals("org.lwjgl.opengl.GL20") || name.equals("org.lwjgl.opengl.GL30")
            || name.equals("org.lwjgl.Sys") || name.equals("org.lwjgl.input.Keyboard") || name.equals("org.lwjgl.input.Mouse"); }
    public byte[] adapt(String name, byte[] bytes) {
        ClassNode node = new ClassNode(); new ClassReader(bytes).accept(node, 0);
        for (MethodNode m : node.methods) {
            if (m.name.equals("<init>")) continue;
            m.access &= ~(Opcodes.ACC_NATIVE | Opcodes.ACC_ABSTRACT);
            m.instructions.clear(); m.tryCatchBlocks.clear(); if (m.localVariables != null) m.localVariables.clear();
            String method = m.name, desc = m.desc;
            if (method.equals("<clinit>")) { m.instructions.add(new InsnNode(Opcodes.RETURN)); continue; }
            if (name.equals("org.lwjgl.Sys")) {
                if (method.equals("getTime") && desc.equals("()J")) {
                    m.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/System", "nanoTime", "()J", false));
                    m.instructions.add(new LdcInsnNode(1000L)); m.instructions.add(new InsnNode(Opcodes.LDIV));
                    m.instructions.add(new InsnNode(Opcodes.LRETURN));
                } else if (method.equals("getTimerResolution") && desc.equals("()J")) {
                    m.instructions.add(new LdcInsnNode(1_000_000L)); m.instructions.add(new InsnNode(Opcodes.LRETURN));
                } else if (method.equals("initialize") && desc.equals("()V")) m.instructions.add(new InsnNode(Opcodes.RETURN));
                else unsupported(m, name + "." + method + desc);
                continue;
            }
            if (nativeGlMethod(name, m)) continue;
            if (name.endsWith("GL11")) {
                if (displayListMethod(m)) continue;
                if (method.matches("gl(Begin|End|Vertex[234][fd]|TexCoord[234][fd]|Color[34][fd]|Normal3[fd]|Enable|Disable|BlendFunc|DepthMask|DepthFunc|ColorMask|ShadeModel|AlphaFunc|LineWidth|PushAttrib|PopAttrib|PushMatrix|PopMatrix|Translate[fd]|Scale[fd]|Rotate[fd])")) recordDisplayList(m);
                if (method.equals("glPushMatrix")) call(m,DISPATCH,"push","()V");
                else if (method.equals("glPopMatrix")) call(m,DISPATCH,"pop","()V");
                else if (method.equals("glTranslatef") || method.equals("glScalef")) {
                    for (int i=0;i<3;i++) { m.instructions.add(new VarInsnNode(Opcodes.FLOAD,i)); m.instructions.add(new InsnNode(Opcodes.F2D)); }
                    call(m,DISPATCH,method.equals("glTranslatef") ? "translate" : "scale","(DDD)V");
                } else if (method.equals("glTranslated") || method.equals("glScaled")) {
                    for (int i=0;i<3;i++) m.instructions.add(new VarInsnNode(Opcodes.DLOAD,i*2));
                    call(m,DISPATCH,method.equals("glTranslated") ? "translate" : "scale","(DDD)V");
                } else if (method.equals("glRotatef")) {
                    for (int i=0;i<4;i++) { m.instructions.add(new VarInsnNode(Opcodes.FLOAD,i)); m.instructions.add(new InsnNode(Opcodes.F2D)); }
                    call(m,DISPATCH,"rotate","(DDDD)V");
                } else if (method.equals("glRotated")) {
                    for (int i=0;i<4;i++) m.instructions.add(new VarInsnNode(Opcodes.DLOAD,i*2));
                    call(m,DISPATCH,"rotate","(DDDD)V");
                } else if (method.equals("glLoadIdentity")) call(m,DISPATCH,"loadIdentity","()V");
                else if(method.equals("glClear")&&desc.equals("(I)V")){m.instructions.add(new VarInsnNode(Opcodes.ILOAD,0));call(m,DISPATCH,"clear","(I)V");}
                else if(method.equals("glOrtho")&&desc.equals("(DDDDDD)V")){for(int i=0;i<6;i++)m.instructions.add(new VarInsnNode(Opcodes.DLOAD,i*2));call(m,DISPATCH,"ortho","(DDDDDD)V");}
                else if (method.equals("glGetInteger") && desc.equals("(I)I")) { m.instructions.add(new VarInsnNode(Opcodes.ILOAD,0)); call(m,DISPATCH,"integer","(I)I"); }
                else if (WORLD_METHODS.contains(method+desc)) routeWorld(m);
                else if (method.matches("gl(Enable|Disable|BlendFunc|DepthMask|DepthFunc|ColorMask|ShadeModel|AlphaFunc|LineWidth|PushAttrib|PopAttrib|MatrixMode|ColorMaterial|DepthRange|Hint)")) { }
                else { unsupported(m,name+"."+method+desc); continue; }
            } else if (name.endsWith("Keyboard") && method.equals("isKeyDown")) { m.instructions.add(new VarInsnNode(Opcodes.ILOAD,0)); call(m,"key","(I)Z"); }
            else if (name.endsWith("Mouse") && method.equals("isButtonDown")) { m.instructions.add(new VarInsnNode(Opcodes.ILOAD,0));call(m,"button","(I)Z"); }
            else if (name.endsWith("Mouse") && method.equals("getX")) call(m,"mouseX","()I");
            else if (name.endsWith("Mouse") && method.equals("getY")) call(m,"mouseY","()I");
            // handleMouseInput sees one synthetic wheel event: host clicks/releases arrive through their own handlers.
            else if (name.endsWith("Mouse") && method.equals("getEventDWheel")) call(m,"eventWheel","()I");
            else if (name.endsWith("Mouse") && method.equals("getEventX")) call(m,"mouseX","()I");
            else if (name.endsWith("Mouse") && method.equals("getEventY")) call(m,"mouseY","()I");
            else if (name.endsWith("Mouse") && method.equals("getEventButton")) m.instructions.add(new InsnNode(Opcodes.ICONST_M1));
            else if (name.endsWith("Mouse") && method.equals("getEventButtonState")) m.instructions.add(new InsnNode(Opcodes.ICONST_0));
            else if (method.equals("isCreated")) m.instructions.add(new InsnNode(Opcodes.ICONST_1));
            else if (name.endsWith("Keyboard") && (method.equals("getKeyName") || method.equals("getKeyIndex"))) { keyNames(node,m);continue; }
            else if (method.equals("getDWheel") || method.equals("next") || method.equals("isButtonDown")) m.instructions.add(new InsnNode(Opcodes.ICONST_0));
            else if (method.equals("enableRepeatEvents") || method.equals("setGrabbed")) { }
            else { unsupported(m,name+"."+method+desc); continue; }
            m.instructions.add(new InsnNode(Type.getReturnType(desc).getOpcode(Opcodes.IRETURN)));
        }
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS); node.accept(writer); return writer.toByteArray();
    }
    private static boolean nativeGlMethod(String owner, MethodNode method) {
        String key = method.name + method.desc;
        boolean supported = owner.endsWith("GL11") && java.util.Arrays.asList(
                "glGetString(I)Ljava/lang/String;", "glGetInteger(I)I", "glGenTextures()I", "glBindTexture(II)V",
                "glDeleteTextures(I)V", "glIsTexture(I)Z",                 "glTexParameteri(III)V", "glTexParameterf(IIF)V",
                "glTexEnvi(III)V", "glPixelStorei(II)V", "glGetTexLevelParameteri(III)I",
                "glGetTexParameteri(II)I", "glGetTexParameterf(II)F",
                "glTexImage2D(IIIIIIIILjava/nio/ByteBuffer;)V", "glTexImage2D(IIIIIIIILjava/nio/IntBuffer;)V",
                "glTexSubImage2D(IIIIIIIILjava/nio/ByteBuffer;)V", "glTexSubImage2D(IIIIIIIILjava/nio/IntBuffer;)V",
                "glGetTexImage(IIIILjava/nio/ByteBuffer;)V", "glGetTexImage(IIIILjava/nio/IntBuffer;)V").contains(key);
        supported |= owner.endsWith("GL20") && java.util.Arrays.asList(
                "glCreateProgram()I", "glCreateShader(I)I", "glShaderSource(ILjava/lang/CharSequence;)V", "glCompileShader(I)V",
                "glAttachShader(II)V", "glDetachShader(II)V", "glLinkProgram(I)V", "glUseProgram(I)V",
                "glGetShaderi(II)I", "glGetProgrami(II)I", "glGetShaderInfoLog(II)Ljava/lang/String;", "glGetProgramInfoLog(II)Ljava/lang/String;",
                "glGetUniformLocation(ILjava/lang/CharSequence;)I", "glUniform1i(II)V", "glUniform1f(IF)V", "glUniform2f(IFF)V",
                "glUniform3f(IFFF)V", "glUniform4f(IFFFF)V", "glDeleteShader(I)V", "glDeleteProgram(I)V").contains(key);
        supported |= owner.endsWith("GL30") && key.equals("glGenerateMipmap(I)V");
        if (!supported) return false;
        InsnList code = method.instructions;
        code.add(new LdcInsnNode(Type.getObjectType(owner.replace('.', '/')))); code.add(new LdcInsnNode(key));
        Type[] args = Type.getArgumentTypes(method.desc);
        code.add(new LdcInsnNode(args.length)); code.add(new TypeInsnNode(Opcodes.ANEWARRAY, "java/lang/Object"));
        int local = 0;
        for (int i = 0; i < args.length; i++) {
            Type type = args[i]; code.add(new InsnNode(Opcodes.DUP)); code.add(new LdcInsnNode(i));
            code.add(new VarInsnNode(type.getOpcode(Opcodes.ILOAD), local)); local += type.getSize();
            if (type.getSort() == Type.INT) code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false));
            else if (type.getSort() == Type.FLOAT) code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Float", "valueOf", "(F)Ljava/lang/Float;", false));
            code.add(new InsnNode(Opcodes.AASTORE));
        }
        code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "github/com/gengyoubo/mfcl/bridge/LegacyOpenGl", "invoke", "(Ljava/lang/Class;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/Object;", false));
        Type result = Type.getReturnType(method.desc);
        if (result.getSort() == Type.VOID) code.add(new InsnNode(Opcodes.POP));
        else if (result.getSort() == Type.INT || result.getSort() == Type.BOOLEAN) {
            String boxed = result.getSort() == Type.INT ? "java/lang/Integer" : "java/lang/Boolean";
            code.add(new TypeInsnNode(Opcodes.CHECKCAST, boxed));
            code.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, boxed, result.getSort() == Type.INT ? "intValue" : "booleanValue", "()" + result.getDescriptor(), false));
        } else if (result.getSort() == Type.FLOAT || result.getSort() == Type.DOUBLE) {
            boolean single = result.getSort() == Type.FLOAT;
            String boxed = single ? "java/lang/Float" : "java/lang/Double";
            code.add(new TypeInsnNode(Opcodes.CHECKCAST, boxed));
            code.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, boxed, single ? "floatValue" : "doubleValue", "()" + result.getDescriptor(), false));
        } else code.add(new TypeInsnNode(Opcodes.CHECKCAST, result.getInternalName()));
        code.add(new InsnNode(result.getOpcode(Opcodes.IRETURN))); return true;
    }
    private static boolean displayListMethod(MethodNode method) {
        String target = null;
        if (method.name.equals("glGenLists") && method.desc.equals("(I)I")) target = "generate";
        else if (method.name.equals("glNewList") && method.desc.equals("(II)V")) target = "begin";
        else if (method.name.equals("glEndList") && method.desc.equals("()V")) target = "end";
        else if (method.name.equals("glCallList") && method.desc.equals("(I)V")) target = "call";
        else if (method.name.equals("glIsList") && method.desc.equals("(I)Z")) target = "exists";
        else if (method.name.equals("glDeleteLists") && method.desc.equals("(II)V")) target = "delete";
        if (target == null) return false;
        method.instructions.add(new LdcInsnNode(Type.getObjectType("org/lwjgl/opengl/GL11")));
        Type[] args = Type.getArgumentTypes(method.desc);
        for (int i = 0; i < args.length; i++) method.instructions.add(new VarInsnNode(Opcodes.ILOAD, i));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, LISTS, target,
                "(Ljava/lang/Class;" + method.desc.substring(1), false));
        method.instructions.add(new InsnNode(Type.getReturnType(method.desc).getOpcode(Opcodes.IRETURN)));
        return true;
    }
    private static void recordDisplayList(MethodNode method) {
        Type[] arguments = Type.getArgumentTypes(method.desc);
        for (Type argument : arguments) if (argument.getSort() < Type.BOOLEAN || argument.getSort() > Type.DOUBLE) return;
        if (!Type.getReturnType(method.desc).equals(Type.VOID_TYPE)) return;
        InsnList code = method.instructions;
        code.add(new LdcInsnNode(Type.getObjectType("org/lwjgl/opengl/GL11")));
        code.add(new LdcInsnNode(method.name + method.desc));
        code.add(new LdcInsnNode(arguments.length)); code.add(new IntInsnNode(Opcodes.NEWARRAY, Opcodes.T_DOUBLE));
        int local = 0;
        for (int i = 0; i < arguments.length; i++) {
            Type type = arguments[i];
            code.add(new InsnNode(Opcodes.DUP)); code.add(new LdcInsnNode(i));
            code.add(new VarInsnNode(type.getOpcode(Opcodes.ILOAD), local));
            if (type.getSort() == Type.FLOAT) code.add(new InsnNode(Opcodes.F2D));
            else if (type.getSort() == Type.LONG) code.add(new InsnNode(Opcodes.L2D));
            else if (type.getSort() != Type.DOUBLE) code.add(new InsnNode(Opcodes.I2D));
            code.add(new InsnNode(Opcodes.DASTORE)); local += type.getSize();
        }
        code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, LISTS, "record", "(Ljava/lang/Class;Ljava/lang/String;[D)Z", false));
        LabelNode immediate = new LabelNode(); code.add(new JumpInsnNode(Opcodes.IFEQ, immediate));
        code.add(new InsnNode(Opcodes.RETURN)); code.add(immediate);
    }
    private static void keyNames(ClassNode node,MethodNode method) {
        for(FieldNode field:node.fields)if(field.name.startsWith("KEY_") && field.value instanceof Integer) {
            LabelNode next=new LabelNode();
            if(method.name.equals("getKeyName")) {
                method.instructions.add(new VarInsnNode(Opcodes.ILOAD,0));method.instructions.add(new LdcInsnNode(field.value));
                method.instructions.add(new JumpInsnNode(Opcodes.IF_ICMPNE,next));method.instructions.add(new LdcInsnNode(field.name.substring(4)));method.instructions.add(new InsnNode(Opcodes.ARETURN));
            } else {
                method.instructions.add(new LdcInsnNode(field.name.substring(4)));method.instructions.add(new VarInsnNode(Opcodes.ALOAD,0));
                method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,"java/lang/String","equals","(Ljava/lang/Object;)Z",false));
                method.instructions.add(new JumpInsnNode(Opcodes.IFEQ,next));method.instructions.add(new LdcInsnNode(field.value));method.instructions.add(new InsnNode(Opcodes.IRETURN));
            }
            method.instructions.add(next);
        }
        method.instructions.add(new InsnNode(method.name.equals("getKeyName")?Opcodes.ACONST_NULL:Opcodes.ICONST_0));
        method.instructions.add(new InsnNode(method.name.equals("getKeyName")?Opcodes.ARETURN:Opcodes.IRETURN));
    }
    private static void call(MethodNode m,String name,String desc) { call(m,API,name,desc); }
    private static void call(MethodNode m,String owner,String name,String desc) { m.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,owner,name,desc,false)); }
    private static void routeWorld(MethodNode m) {
        Type[] arguments = Type.getArgumentTypes(m.desc);
        InsnList code = m.instructions;
        int local = 0;
        for (Type argument : arguments) { code.add(new VarInsnNode(argument.getOpcode(Opcodes.ILOAD), local)); local += argument.getSize(); }
        code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, WORLD, m.name, m.desc, false));
        code.add(new InsnNode(Type.getReturnType(m.desc).getOpcode(Opcodes.IRETURN)));
    }
    private static void unsupported(MethodNode m,String name) {
        m.instructions.add(new LdcInsnNode(name)); call(m,"unsupported","(Ljava/lang/String;)V");
        m.instructions.add(new InsnNode(Opcodes.ACONST_NULL)); m.instructions.add(new InsnNode(Opcodes.ATHROW));
    }
}
