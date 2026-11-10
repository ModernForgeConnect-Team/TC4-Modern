package github.com.gengyoubo.mfcl.bridge;

import java.nio.ByteBuffer;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Thread-confined capture of private legacy world rendering. The real old Tessellator, immediate-mode and
 * vertex-array calls are recorded as object-space geometry; the matrix and colour state are applied when the
 * geometry is submitted (EXECUTE) or stored raw inside a display list (COMPILE).
 */
public final class LegacyWorldGraphics {
    public record Text(String text,int x,int y,int color,boolean shadow,double[] matrix) {public Text {matrix=matrix.clone();}}
    private static final ThreadLocal<Frame> CURRENT = new ThreadLocal<>();
    // Installed only while LegacyClientData.inLegacy holds the runtime's shared monitor.
    private static final ThreadLocal<ClassLoader> LEGACY_DOMAIN = new ThreadLocal<>();
    private LegacyWorldGraphics() { }

    public static Frame begin() {
        if (CURRENT.get() != null) throw new IllegalStateException("Nested legacy world capture");
        ClassLoader loader = LEGACY_DOMAIN.get();
        if (loader != null) idleTessellator(loader);
        Frame frame = new Frame(); CURRENT.set(frame); return frame;
    }
    static ClassLoader enterLegacy(ClassLoader loader) {
        ClassLoader previous = LEGACY_DOMAIN.get();
        LEGACY_DOMAIN.set(loader);
        // GUI captures can begin before acquiring the runtime monitor. Recover only after acquiring it,
        // and never on nested calls from a renderer that is still building its current batch.
        if (previous == null && active()) idleTessellator(loader);
        return previous;
    }
    static void leaveLegacy(ClassLoader previous) {
        if (previous == null) LEGACY_DOMAIN.remove();
        else LEGACY_DOMAIN.set(previous);
    }
    /**
     * An original renderer that threw between startDrawing and draw leaves the shared old Tessellator "drawing", and
     * every later capture would fail with "Already tesselating!". The caller holds the runtime monitor and has not
     * started the next original renderer, so these vertices are stale. A GUI capture outside that monitor must never
     * reset the singleton: it may belong to a chunk renderer running on another thread.
     */
    private static void idleTessellator(ClassLoader loader) {
        Class<?> type = oldTessellator(loader);
        if (type == null) return; // not inside the old client domain
        try {
            Object instance = type.getField("field_78398_a").get(null);
            var drawing = type.getDeclaredField("field_78415_z"); drawing.setAccessible(true);
            if (!drawing.getBoolean(instance)) return;
            var reset = type.getDeclaredMethod("func_78379_d"); reset.setAccessible(true); reset.invoke(instance);
            drawing.setBoolean(instance, false);
            LegacyDiagnostics.legacyFailure("tessellator", new IllegalStateException("Recovered an old Tessellator left drawing by a failed renderer"));
        } catch (ReflectiveOperationException | LinkageError | RuntimeException unavailable) { /* not inside the old client domain */ }
    }
    private static Class<?> oldTessellator(ClassLoader loader) {
        if (loader == null) return null;
        try {
            Class<?> type = Class.forName("net.minecraft.client.renderer.Tessellator", false, loader);
            return type.getClassLoader() == LegacyWorldGraphics.class.getClassLoader() ? null : type;
        } catch (ClassNotFoundException | LinkageError unavailable) { return null; }
    }
    public static boolean active() { return CURRENT.get() != null; }
    public static Frame current() { return CURRENT.get(); }

    public static final class Frame implements AutoCloseable {
        private final Thread owner = Thread.currentThread();
        private final Deque<double[]> stack = new ArrayDeque<>();
        private double[] matrix = LegacyVertexStream.identity();
        private double[] textureMatrix = LegacyVertexStream.identity();
        private final Deque<double[]> textureStack = new ArrayDeque<>();
        private double[] projectionMatrix = LegacyVertexStream.identity();
        private final Deque<double[]> projectionStack = new ArrayDeque<>();
        private int matrixMode = 5888;
        private final boolean[] generated = new boolean[4];
        private final double[][] planes = {{1,0,0,0},{0,1,0,0},{0,0,1,0},{0,0,0,1}};
        private String texture = "";
        private final List<LegacyVertexStream.Primitive> primitives = new ArrayList<>();
        private final List<Text> texts=new ArrayList<>();
        private final List<LegacyVertexStream.Vertex> pending = new ArrayList<>();
        private int mode = -1;
        private float u, v, nx, ny = 0, nz = 1;
        private int argb = 0xFFFFFFFF;
        private Object vertexBuffer; private int vertexSize = 3, vertexStride; private int vertexPosition;
        private Object texBuffer; private int texSize = 2, texStride; private int texPosition;
        private Object colorBuffer; private int colorSize = 4, colorStride; private int colorPosition; private boolean colorNormalized;
        private Object normalBuffer; private int normalStride; private int normalPosition;
        private boolean vertexEnabled, texEnabled, colorEnabled, normalEnabled;
        private int clientTextureUnit = 33984;
        private LegacyVertexStream.RenderState state = LegacyVertexStream.RenderState.OPAQUE;
        private final Deque<LegacyVertexStream.RenderState> attributes = new ArrayDeque<>();

        public List<LegacyVertexStream.Primitive> primitives() { check(); return List.copyOf(primitives); }
        public List<Text> texts() {check();return List.copyOf(texts);}
        public List<LegacyVertexStream.Primitive> takePrimitives() { check();var result=List.copyOf(primitives);primitives.clear();return result; }
        private void check() { if (Thread.currentThread() != owner || CURRENT.get() != this) throw new IllegalStateException("Expired or foreign legacy world capture"); }
        @Override public void close() {
            check();
            try { if (mode >= 0) throw new IllegalStateException("Unbalanced legacy world primitive"); }
            finally { CURRENT.remove(); }
        }

        private void emit() {
            if (pending.isEmpty()) return;
            var primitive = new LegacyVertexStream.Primitive(mode, texture, List.copyOf(pending), state);
            if (LegacyDisplayLists.compiling()) LegacyDisplayLists.geometry(primitive, true,
                    !(colorEnabled && colorBuffer != null), !(normalEnabled && normalBuffer != null));
            else addTransformed(primitive);
        }
        private void addTransformed(LegacyVertexStream.Primitive primitive) {
            var vertices = new ArrayList<LegacyVertexStream.Vertex>(primitive.vertices().size());
            double[] inverse = LegacyVertexStream.invert(matrix);
            for (var vertex : primitive.vertices()) {
                double[] world = LegacyVertexStream.transform(matrix, vertex.x(), vertex.y(), vertex.z());
                // Normals use the inverse transpose: translations have no effect, nonuniform scales do.
                double x = inverse[0] * vertex.nx() + inverse[1] * vertex.ny() + inverse[2] * vertex.nz();
                double y = inverse[4] * vertex.nx() + inverse[5] * vertex.ny() + inverse[6] * vertex.nz();
                double z = inverse[8] * vertex.nx() + inverse[9] * vertex.ny() + inverse[10] * vertex.nz();
                double length = Math.sqrt(x*x + y*y + z*z);
                if (length > 0) { x /= length; y /= length; z /= length; }
                double[] uv = {vertex.u(),vertex.v(),0,1};
                for (int coordinate=0;coordinate<4;coordinate++) if (generated[coordinate]) {
                    double[] plane = planes[coordinate]; uv[coordinate] = plane[0]*vertex.x()+plane[1]*vertex.y()+plane[2]*vertex.z()+plane[3];
                }
                double[] mapped = new double[4];
                for (int row=0;row<4;row++) for (int column=0;column<4;column++) mapped[row] += textureMatrix[column*4+row]*uv[column];
                double q = Math.abs(mapped[3]) < 1e-8 ? 1 : mapped[3];
                vertices.add(new LegacyVertexStream.Vertex(world[0], world[1], world[2], (float)(mapped[0]/q), (float)(mapped[1]/q), (float)x, (float)y, (float)z, vertex.argb()));
            }
            primitives.add(new LegacyVertexStream.Primitive(primitive.mode(), primitive.texture(), vertices, primitive.state()));
        }
    }

    private static Frame frame() { return CURRENT.get(); }
    public static void text(String text,int x,int y,int color,boolean shadow) {
        Frame frame=java.util.Objects.requireNonNull(frame(),"Text outside a legacy render frame");frame.check();
        if(text!=null)frame.texts.add(new Text(text,x,y,color,shadow,frame.matrix));
    }

    public static int integer(int value) {
        Frame f = frame();
        if (value == 2979) return f == null ? 0 : f.stack.size() + 1; // GL_MODELVIEW_STACK_DEPTH
        if (value == 3415) return 0; // No legacy stencil buffer exists.
        throw new UnsupportedOperationException("Unsupported legacy world GL integer query: " + value);
    }

    public static void glMatrixMode(int mode) { Frame f = frame(); if (f != null) { f.check(); if (mode != 5888 && mode != 5890 && !(mode==5889&&LegacyGuiGraphics.capturing())) throw new UnsupportedOperationException("Legacy matrix mode " + mode); f.matrixMode = mode; } }
    public static boolean modelView(){Frame f=frame();return f==null||f.matrixMode==5888;}
    private static double[] activeMatrix(Frame f) { return f.matrixMode == 5890 ? f.textureMatrix : f.matrixMode==5889?f.projectionMatrix:f.matrix; }
    public static void push() { Frame f = frame(); if (f != null) { f.check(); if (f.matrixMode == 5890) f.textureStack.push(f.textureMatrix.clone()); else if(f.matrixMode==5889)f.projectionStack.push(f.projectionMatrix.clone());else f.stack.push(f.matrix.clone()); } }
    public static void pop() { Frame f = frame(); if (f != null) { f.check(); if (f.matrixMode == 5890) f.textureMatrix = f.textureStack.pop(); else if(f.matrixMode==5889)f.projectionMatrix=f.projectionStack.pop();else f.matrix = f.stack.pop(); } }
    public static void loadIdentity() { Frame f = frame(); if (f != null) { f.check(); if (f.matrixMode == 5890) f.textureMatrix = LegacyVertexStream.identity(); else if(f.matrixMode==5889)f.projectionMatrix=LegacyVertexStream.identity();else f.matrix = LegacyVertexStream.identity(); } }
    public static void translate(double x, double y, double z) { Frame f = frame(); if (f != null) { f.check(); LegacyVertexStream.translate(activeMatrix(f), x, y, z); } }
    public static void scale(double x, double y, double z) { Frame f = frame(); if (f != null) { f.check(); LegacyVertexStream.scale(activeMatrix(f), x, y, z); } }
    public static void rotate(double angle, double x, double y, double z) { Frame f = frame(); if (f != null) { f.check(); LegacyVertexStream.rotate(activeMatrix(f), angle, x, y, z); } }
    public static void glTexGeni(int coordinate, int parameter, int mode) {
        if (coordinate < 8192 || coordinate > 8195 || parameter != 9472 || mode != 9216 && mode != 9217) throw new UnsupportedOperationException("Legacy texture generation mode");
    }
    public static void glTexGen(int coordinate, int parameter, FloatBuffer values) {
        Frame f = frame(); if (f == null) return; f.check();
        if (coordinate < 8192 || coordinate > 8195 || parameter != 9473 && parameter != 9474) throw new UnsupportedOperationException("Legacy texture generation plane");
        double[] plane = new double[4]; for (int i=0;i<4;i++) plane[i] = values.get(values.position()+i);
        if (parameter == 9474) {
            double[] inverse = LegacyVertexStream.invert(f.matrix), transformed = new double[4];
            for (int i=0;i<4;i++) for (int j=0;j<4;j++) transformed[i] += inverse[i*4+j]*plane[j];
            plane = transformed;
        }
        f.planes[coordinate-8192] = plane;
    }

    public static void bindTexture(int handle) { Frame f = frame(); if (f != null) { f.check(); f.texture = "legacy:" + handle; } }
    public static void texture(String resource) { Frame f = frame(); if (f != null) { f.check(); f.texture = resource; } }
    public static void glEnable(int cap) { state(cap, true); }
    public static void glDisable(int cap) { state(cap, false); }
    /** Queries the captured state, never the host OpenGL context. */
    public static boolean glIsEnabled(int cap) {
        Frame f = frame();
        if (f == null) throw new IllegalStateException("Legacy GL capability query outside a capture");
        f.check();
        if (cap == 3042) return f.state.blend();
        if (cap == 2884) return f.state.cull();
        if (cap == 2929) return f.state.depthTest();
        if (cap >= 3168 && cap <= 3171) return f.generated[cap-3168];
        throw new UnsupportedOperationException("Unsupported legacy GL capability query: " + cap);
    }
    private static void state(int cap, boolean enabled) {
        if (cap == 2929) LegacyGuiGraphics.depthTest(enabled);
        Frame f = frame(); if (f == null) return; f.check(); var s = f.state;
        if (cap >= 3168 && cap <= 3171) f.generated[cap-3168] = enabled;
        f.state = new LegacyVertexStream.RenderState(cap == 3042 ? enabled : s.blend(), s.sourceBlend(), s.destinationBlend(), s.depthWrite(), cap == 2884 ? enabled : s.cull(),cap == 2929 ? enabled : s.depthTest(),s.lineWidth());
    }
    public static void glLineWidth(float width) {
        Frame f=frame(); if(f==null || width<=0 || !Float.isFinite(width))return; f.check(); var s=f.state;
        f.state=new LegacyVertexStream.RenderState(s.blend(),s.sourceBlend(),s.destinationBlend(),s.depthWrite(),s.cull(),s.depthTest(),width);
    }
    public static void glBlendFunc(int source, int destination) {
        Frame f = frame(); if (f == null) return; f.check(); var s = f.state;
        f.state = new LegacyVertexStream.RenderState(s.blend(), source, destination, s.depthWrite(), s.cull(),s.depthTest(),s.lineWidth());
    }
    public static void glDepthMask(boolean enabled) {
        Frame f = frame(); if (f == null) return; f.check(); var s = f.state;
        f.state = new LegacyVertexStream.RenderState(s.blend(), s.sourceBlend(), s.destinationBlend(), enabled, s.cull(),s.depthTest(),s.lineWidth());
    }
    public static void glPushAttrib(int mask) { LegacyGuiGraphics.pushDepthAttributes(mask);Frame f = frame(); if (f != null) { f.check(); f.attributes.push(f.state); } }
    public static void glPopAttrib() { LegacyGuiGraphics.popDepthAttributes();Frame f = frame(); if (f != null) { f.check(); f.state = f.attributes.pop(); } }

    public static void clientActiveTexture(int unit) { Frame f = frame(); if (f != null) { f.check(); f.clientTextureUnit = unit; } }
    public static void glEnableClientState(int cap) { Frame f = frame(); if (f == null) return; f.check(); if (cap == 32884) f.vertexEnabled = true; else if (cap == 32888 && f.clientTextureUnit == 33984) f.texEnabled = true; else if (cap == 32886) f.colorEnabled = true; else if (cap == 32885) f.normalEnabled = true; }
    public static void glDisableClientState(int cap) { Frame f = frame(); if (f == null) return; f.check(); if (cap == 32884) f.vertexEnabled = false; else if (cap == 32888 && f.clientTextureUnit == 33984) f.texEnabled = false; else if (cap == 32886) f.colorEnabled = false; else if (cap == 32885) f.normalEnabled = false; }

    public static void glVertexPointer(int size, int stride, FloatBuffer buffer) { Frame f = frame(); if (f == null) return; f.check(); f.vertexBuffer = buffer; f.vertexSize = size; f.vertexStride = stride; f.vertexPosition = buffer == null ? 0 : buffer.position(); }
    public static void glVertexPointer(int size, int stride, DoubleBuffer buffer) { Frame f = frame(); if (f == null) return; f.check(); f.vertexBuffer = buffer; f.vertexSize = size; f.vertexStride = stride; f.vertexPosition = buffer == null ? 0 : buffer.position(); }
    public static void glTexCoordPointer(int size, int stride, FloatBuffer buffer) { Frame f = frame(); if (f == null || f.clientTextureUnit != 33984) return; f.check(); f.texBuffer = buffer; f.texSize = size; f.texStride = stride; f.texPosition = buffer == null ? 0 : buffer.position(); }
    public static void glTexCoordPointer(int size, int stride, ShortBuffer buffer) { Frame f = frame(); if (f == null || f.clientTextureUnit != 33984) return; f.check(); f.texBuffer = buffer; f.texSize = size; f.texStride = stride; f.texPosition = buffer == null ? 0 : buffer.position(); }
    public static void glColorPointer(int size, boolean normalized, int stride, ByteBuffer buffer) { Frame f = frame(); if (f == null) return; f.check(); f.colorBuffer = buffer; f.colorSize = size; f.colorStride = stride; f.colorNormalized = normalized; f.colorPosition = buffer == null ? 0 : buffer.position(); }
    public static void glColorPointer(int size, int stride, FloatBuffer buffer) { Frame f = frame(); if (f == null) return; f.check(); f.colorBuffer = buffer; f.colorSize = size; f.colorStride = stride; f.colorNormalized = false; f.colorPosition = buffer == null ? 0 : buffer.position(); }
    public static void glNormalPointer(int stride, ByteBuffer buffer) { Frame f = frame(); if (f == null) return; f.check(); f.normalBuffer = buffer; f.normalStride = stride; f.normalPosition = buffer == null ? 0 : buffer.position(); }

    public static void glBegin(int mode) {
        Frame f = frame(); if (f == null) return; f.check();
        if (f.mode >= 0) throw new IllegalStateException("Nested legacy world primitive");
        f.mode = mode; f.pending.clear();
    }
    public static void glEnd() {
        Frame f = frame(); if (f == null) return; f.check();
        if (f.mode < 0) throw new IllegalStateException("Legacy world primitive was not started");
        f.emit(); f.pending.clear(); f.mode = -1;
    }
    public static void glVertex2f(float x, float y) { vertex(x, y, 0); }
    public static void glVertex2d(double x, double y) { vertex(x, y, 0); }
    public static void glVertex3f(float x, float y, float z) { vertex(x, y, z); }
    public static void glVertex3d(double x, double y, double z) { vertex(x, y, z); }
    public static void glTexCoord2f(float u, float v) { Frame f = frame(); if (f != null) { f.check(); f.u = u; f.v = v; } }
    public static void glTexCoord2d(double u, double v) { Frame f = frame(); if (f != null) { f.check(); f.u = (float) u; f.v = (float) v; } }
    public static void glNormal3f(float x, float y, float z) { Frame f = frame(); if (f != null) { f.check(); f.nx = x; f.ny = y; f.nz = z; } }
    public static void glNormal3d(double x, double y, double z) { Frame f = frame(); if (f != null) { f.check(); f.nx = (float) x; f.ny = (float) y; f.nz = (float) z; } }
    public static void glColor3f(float r, float g, float b) { glColor4f(r, g, b, 1); }
    public static void glColor4f(float r, float g, float b, float a) { setColor(byte255(r), byte255(g), byte255(b), byte255(a)); }
    public static void glColor3d(double r, double g, double b) { glColor4f((float) r, (float) g, (float) b, 1); }
    public static void glColor4d(double r, double g, double b, double a) { glColor4f((float) r, (float) g, (float) b, (float) a); }
    public static void glColor3ub(byte r, byte g, byte b) { setColor(r & 0xFF, g & 0xFF, b & 0xFF, 255); }
    public static void glColor4ub(byte r, byte g, byte b, byte a) { setColor(r & 0xFF, g & 0xFF, b & 0xFF, a & 0xFF); }
    private static int byte255(float value) { int v = Math.round(value * 255f); return v < 0 ? 0 : Math.min(v, 255); }
    private static void setColor(int r, int g, int b, int a) { Frame f = frame(); if (f != null) { f.check(); f.argb = (a << 24) | (r << 16) | (g << 8) | b; } }
    private static void vertex(double x, double y, double z) {
        Frame f = frame(); if (f == null) return; f.check();
        if (f.mode < 0) throw new IllegalStateException("Legacy world vertex outside a primitive");
        f.pending.add(new LegacyVertexStream.Vertex(x, y, z, f.u, f.v, f.nx, f.ny, f.nz, f.argb));
    }

    public static void glDrawArrays(int mode, int first, int count) {
        Frame f = frame(); if (f == null) return; f.check();
        if (f.mode >= 0) throw new IllegalStateException("glDrawArrays inside glBegin/glEnd");
        if (f.vertexBuffer == null) throw new IllegalStateException("Legacy world array draw without a vertex pointer");
        f.mode = mode; f.pending.clear();
        try {
            for (int i = 0; i < count; i++) {
                int vertexIndex = first + i;
                double x = component(f.vertexBuffer, f.vertexStride, f.vertexPosition, f.vertexSize, vertexIndex, 0);
                double y = component(f.vertexBuffer, f.vertexStride, f.vertexPosition, f.vertexSize, vertexIndex, 1);
                double z = component(f.vertexBuffer, f.vertexStride, f.vertexPosition, f.vertexSize, vertexIndex, 2);
                float u = 0, v = 0;
                if (f.texEnabled && f.texBuffer != null) {
                    u = (float) component(f.texBuffer, f.texStride, f.texPosition, f.texSize, vertexIndex, 0);
                    v = (float) component(f.texBuffer, f.texStride, f.texPosition, f.texSize, vertexIndex, 1);
                    if (f.texBuffer instanceof ShortBuffer) { u /= 32768f; v /= 32768f; }
                }
                // With GL_COLOR_ARRAY disabled, fixed-function GL uses the current glColor value.
                // Ore renderers tint their inner crystal layer this way instead of using vertex colors.
                int argb = f.colorEnabled && f.colorBuffer != null ? color(f, vertexIndex) : f.argb;
                float nx = f.nx, ny = f.ny, nz = f.nz;
                if (f.normalEnabled && f.normalBuffer instanceof ByteBuffer normalBuffer) {
                    int base = f.normalPosition + vertexIndex * (f.normalStride == 0 ? 3 : f.normalStride);
                    nx = normalBuffer.get(base) / 127f; ny = normalBuffer.get(base + 1) / 127f; nz = normalBuffer.get(base + 2) / 127f;
                }
                f.pending.add(new LegacyVertexStream.Vertex(x, y, z, u, v, nx, ny, nz, argb));
            }
            f.emit();
        } finally { f.pending.clear(); f.mode = -1; }
    }
    private static int color(Frame f, int vertexIndex) {
        int size = Math.max(3, Math.min(4, f.colorSize));
        int stride = f.colorStride == 0 ? size : f.colorStride / elementSize(f.colorBuffer);
        int base = f.colorPosition + vertexIndex * stride;
        int r, g, b, a;
        if (f.colorBuffer instanceof ByteBuffer buffer) {
            r = buffer.get(base) & 0xFF; g = buffer.get(base + 1) & 0xFF; b = buffer.get(base + 2) & 0xFF;
            a = size == 4 ? buffer.get(base + 3) & 0xFF : 255;
        } else if (f.colorBuffer instanceof FloatBuffer buffer) {
            r = byte255(buffer.get(base)); g = byte255(buffer.get(base + 1)); b = byte255(buffer.get(base + 2));
            a = size == 4 ? byte255(buffer.get(base + 3)) : 255;
        } else throw new IllegalStateException("Unsupported legacy color pointer buffer");
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
    private static double component(Object buffer, int byteStride, int positionElements, int size, int vertexIndex, int component) {
        int elementSize = elementSize(buffer);
        int strideElements = byteStride == 0 ? size : byteStride / elementSize;
        int index = positionElements + vertexIndex * strideElements + component;
        if (buffer instanceof FloatBuffer b) return b.get(index);
        if (buffer instanceof DoubleBuffer b) return b.get(index);
        if (buffer instanceof ByteBuffer b) return b.get(index) & 0xFF;
        if (buffer instanceof ShortBuffer b) return b.get(index);
        if (buffer instanceof IntBuffer b) return b.get(index);
        throw new IllegalStateException("Unsupported legacy array buffer " + buffer.getClass().getName());
    }
    private static int elementSize(Object buffer) {
        if (buffer instanceof ByteBuffer) return 1;
        if (buffer instanceof ShortBuffer) return 2;
        if (buffer instanceof IntBuffer || buffer instanceof FloatBuffer) return 4;
        if (buffer instanceof DoubleBuffer) return 8;
        throw new IllegalStateException("Unsupported legacy array buffer " + buffer.getClass().getName());
    }

    /** Replays a compiled list in order: commands change state, geometry is transformed by the current matrix. */
    public static void replayEntries(Class<?> owner, List<LegacyDisplayLists.Entry> entries) {
        Frame f = frame(); if (f == null) return;
        for (LegacyDisplayLists.Entry entry : entries) {
            if (entry instanceof LegacyDisplayLists.CommandEntry command) applyCommand(owner, command.operation(), command.arguments());
            else if (entry instanceof LegacyDisplayLists.GeometryEntry geometry) {
                var primitive = geometry.primitive();
                var vertices = new ArrayList<LegacyVertexStream.Vertex>(primitive.vertices().size());
                for (var vertex : primitive.vertices()) vertices.add(new LegacyVertexStream.Vertex(
                        vertex.x(), vertex.y(), vertex.z(), vertex.u(), vertex.v(),
                        geometry.inheritNormal() ? f.nx : vertex.nx(), geometry.inheritNormal() ? f.ny : vertex.ny(),
                        geometry.inheritNormal() ? f.nz : vertex.nz(), geometry.inheritColor() ? f.argb : vertex.argb()));
                f.addTransformed(new LegacyVertexStream.Primitive(primitive.mode(), geometry.inheritTexture() ? f.texture : primitive.texture(), vertices, f.state));
            }
        }
    }
    private static void applyCommand(Class<?> owner, String operation, List<Double> a) {
        double d0 = a.size() > 0 ? a.get(0) : 0, d1 = a.size() > 1 ? a.get(1) : 0, d2 = a.size() > 2 ? a.get(2) : 0, d3 = a.size() > 3 ? a.get(3) : 0;
        if (operation.equals("glLineWidth(F)V")) glLineWidth((float)d0);
        else if (operation.equals("glBegin(I)V")) glBegin((int) d0);
        else if (operation.equals("glMatrixMode(I)V")) glMatrixMode((int)d0);
        else if (operation.equals("glEnable(I)V")) glEnable((int)d0);
        else if (operation.equals("glDisable(I)V")) glDisable((int)d0);
        else if (operation.equals("glBlendFunc(II)V")) glBlendFunc((int)d0, (int)d1);
        else if (operation.equals("glDepthMask(Z)V")) glDepthMask(d0 != 0);
        else if (operation.equals("glPushAttrib(I)V")) glPushAttrib((int)d0);
        else if (operation.equals("glPopAttrib()V")) glPopAttrib();
        else if (operation.equals("glEnd()V")) glEnd();
        else if (operation.startsWith("glVertex2")) glVertex2d(d0, d1);
        else if (operation.startsWith("glVertex3")) glVertex3d(d0, d1, d2);
        else if (operation.startsWith("glTexCoord2")) glTexCoord2d(d0, d1);
        else if (operation.startsWith("glNormal3")) glNormal3d(d0, d1, d2);
        else if (operation.startsWith("glColor3")) setColor(byte255((float) d0), byte255((float) d1), byte255((float) d2), 255);
        else if (operation.startsWith("glColor4")) setColor(byte255((float) d0), byte255((float) d1), byte255((float) d2), byte255((float) d3));
        else if (operation.equals("glPushMatrix()V")) push();
        else if (operation.equals("glPopMatrix()V")) pop();
        else if (operation.equals("glLoadIdentity()V")) loadIdentity();
        else if (operation.startsWith("glTranslate")) translate(d0, d1, d2);
        else if (operation.startsWith("glScale")) scale(d0, d1, d2);
        else if (operation.startsWith("glRotate")) rotate(d0, d1, d2, d3);
        else if (operation.equals("glCallList(I)V")) replayEntries(owner, LegacyDisplayLists.entries(owner, (int) d0));
        // Other immediate state changes (enable/disable/blend/etc.) do not affect captured geometry.
    }
}
