package github.com.gengyoubo.mfcl.bridge;

import java.util.*;

/** Thread-confined GUI command capture. No legacy OpenGL context or native input library is entered. */
public final class LegacyGuiGraphics {
    public sealed interface Command permits Rect, Text, Texture, Item, Mesh, DepthTest, ResearchClip {}
    public record DepthTest(boolean enabled) implements Command {}
    public record ResearchClip(boolean enabled) implements Command {}
    public record Rect(int x1, int y1, int x2, int y2, int top, int bottom) implements Command {}
    public record Text(String text, int x, int y, int color, boolean shadow, double sx, double sy) implements Command {}
    public record Texture(String resource, int x, int y, int u, int v, int width, int height, double sx, double sy) implements Command {}
    public record Item(Object stack, int x, int y, boolean overlay, String label, double sx, double sy) implements Command {}
    public record Mesh(List<LegacyVertexStream.Primitive> primitives) implements Command {}
    private record Matrix(double x, double y, double sx, double sy) {}
    private record DepthAttribute(boolean enabled, boolean restore) {}
    private static final ThreadLocal<Frame> CURRENT = new ThreadLocal<>();
    private LegacyGuiGraphics() {}
    public static Frame begin(int width, int height, int mouseX, int mouseY, Set<Integer> keys) {
        if (CURRENT.get() != null) throw new IllegalStateException("Nested legacy GUI capture");
        Frame frame = new Frame(width, height, mouseX, mouseY, keys); CURRENT.set(frame); return frame;
    }
    public static final class Frame implements AutoCloseable {
        private final Thread owner = Thread.currentThread();
        private final int width, height, mouseX, mouseY;
        private final Set<Integer> keys;
        private final List<Command> commands = new ArrayList<>();
        private final Deque<Matrix> matrices = new ArrayDeque<>();
        private final Deque<DepthAttribute> depthAttributes = new ArrayDeque<>();
        private boolean depthTest = true;
        private Matrix matrix = new Matrix(0, 0, 1, 1);
        private String texture;
        private final LegacyWorldGraphics.Frame geometry=LegacyWorldGraphics.active()?null:LegacyWorldGraphics.begin();
        private Object requestedScreen;
        private boolean screenRequested, clickSound, tooltip;
        private int wheel;
        private Frame(int width, int height, int x, int y, Set<Integer> keys) {
            this.width = width; this.height = height; mouseX = x; mouseY = y; this.keys = Set.copyOf(keys);
        }
        private void flushGeometry() { if(geometry!=null) {var mesh=geometry.takePrimitives();if(!mesh.isEmpty())commands.add(new Mesh(mesh));} }
        public List<Command> commands() { check();flushGeometry();return List.copyOf(commands); }
        public Object requestedScreen() { check(); return requestedScreen; }
        public boolean hasScreenRequest() { check(); return screenRequested; }
        public boolean hasClickSound() { check(); return clickSound; }
        public boolean hasTooltip() { check(); return tooltip; }
        private void check() { if (Thread.currentThread() != owner || CURRENT.get() != this) throw new IllegalStateException("Expired or foreign GUI capture"); }
        @Override public void close() { check();CURRENT.remove();try {if(geometry!=null)geometry.close();} finally {if (!matrices.isEmpty()) throw new IllegalStateException("Unbalanced legacy GUI matrix stack");} }
        private int x(double value) { return (int)Math.round(matrix.x + matrix.sx * value); }
        private int y(double value) { return (int)Math.round(matrix.y + matrix.sy * value); }
    }
    private static Frame frame() { return Objects.requireNonNull(CURRENT.get(), "Legacy GUI operation outside a host frame"); }
    public static boolean capturing() { return CURRENT.get() != null; }
    public static void researchClip(boolean enabled) { Frame f=CURRENT.get(); if(f!=null){f.flushGeometry();f.commands.add(new ResearchClip(enabled));} }
    public static void depthTest(boolean enabled) {
        Frame f = CURRENT.get();
        if (f == null || f.depthTest == enabled) return;
        f.flushGeometry();f.commands.add(new DepthTest(enabled));f.depthTest = enabled;
    }
    public static void pushDepthAttributes(int mask) { Frame f = CURRENT.get();if(f!=null)f.depthAttributes.push(new DepthAttribute(f.depthTest,(mask&0x2100)!=0)); }
    public static void popDepthAttributes() { Frame f = CURRENT.get();if(f!=null){var saved=f.depthAttributes.pop();if(saved.restore())depthTest(saved.enabled());} }
    public static void tooltip(List<?> lines) { if (!lines.isEmpty()) frame().tooltip = true; }
    public static void rect(int x1, int y1, int x2, int y2, int color) { gradient(x1, y1, x2, y2, color, color); }
    public static void gradient(int x1, int y1, int x2, int y2, int a, int b) {
        Frame f = frame();f.flushGeometry();f.commands.add(new Rect(f.x(x1), f.y(y1), f.x(x2), f.y(y2), a, b));
    }
    public static void bind(Object resource) {
        if (resource == null) return; // RenderItem may defer its actual atlas binding until the item pass.
        Frame f = CURRENT.get();
        if (f != null) f.texture = resource.toString();
        LegacyWorldGraphics.texture(resource.toString());
    }
    public static void texture(int x, int y, int u, int v, int width, int height) {
        Frame f = frame();f.flushGeometry();f.commands.add(new Texture(Objects.requireNonNull(f.texture, "Unbound legacy texture"), f.x(x), f.y(y), u, v, width, height,f.matrix.sx,f.matrix.sy));
    }
    public static int text(String text, int x, int y, int color, boolean shadow) {
        Frame f = frame();f.flushGeometry();if (text != null) f.commands.add(new Text(text, f.x(x), f.y(y), color, shadow,f.matrix.sx,f.matrix.sy)); return x + (text == null ? 0 : text.length() * 6);
    }
    public static int fontText(Object font,String text,int x,int y,int color,boolean shadow) throws Exception {
        if(capturing())text(text,x,y,color,shadow);else LegacyWorldGraphics.text(text,x,y,color,shadow);
        return x+(text==null?0:(int)font.getClass().getMethod("func_78256_a",String.class).invoke(font,text));
    }
    public static void item(Object stack, int x, int y, boolean overlay, String label) {
        Frame f = frame();f.flushGeometry();if (stack != null) f.commands.add(new Item(stack, f.x(x), f.y(y), overlay, label,f.matrix.sx,f.matrix.sy));
    }
    public static void push() { Frame f = frame(); f.matrices.push(f.matrix); }
    public static void loadIdentity() { Frame f=frame();f.flushGeometry();f.matrix=new Matrix(0,0,1,1); }
    public static void pixelProjection(double left,double right,double bottom,double top) {
        Frame f=frame();
        if(left!=0||top!=0||right!=f.width||bottom!=f.height)throw new UnsupportedOperationException("Legacy GUI projection is not in host pixel coordinates");
    }
    public static void pop() { Frame f = frame(); f.matrix = f.matrices.pop(); }
    public static void translate(double x, double y, double z) { Frame f = frame(); var m = f.matrix; f.matrix = new Matrix(m.x + x * m.sx, m.y + y * m.sy, m.sx, m.sy); }
    public static void scale(double x, double y, double z) { Frame f = frame(); var m = f.matrix; f.matrix = new Matrix(m.x, m.y, m.sx * x, m.sy * y); }
    public static int integer(int value) {
        if(value==2979)return frame().matrices.size()+1;
        if(value==3415)return 0; // No legacy stencil buffer exists.
        throw new UnsupportedOperationException("Unsupported legacy GL integer query: "+value);
    }
    public static boolean key(int key) { return CURRENT.get() != null && frame().keys.contains(key); }
    public static boolean button(int button) { return CURRENT.get()!=null&&frame().keys.contains(1000+button); }
    public static void showScreen(Object screen) { Frame f=frame();f.requestedScreen=screen;f.screenRequested=true; }
    public static void clickSound() { frame().clickSound=true; }
    public static void sound(Object sound) {
        try {
            String id=sound.getClass().getMethod("func_147650_b").invoke(sound).toString();
            if(id.equals("minecraft:gui.button.press")){clickSound();return;}
            LegacyAudio.play(sound);
        } catch(Exception failure){throw new IllegalArgumentException("Cannot play legacy sound",failure);}
    }
    public static int mouseX() { return frame().mouseX; }
    /** Host wheel movement delivered to the old GUI's handleMouseInput; LWJGL's event wheel reads it once. */
    public static void wheel(int delta) { frame().wheel = delta; }
    public static int eventWheel() { Frame f = CURRENT.get(); if (f == null) return 0; int delta = f.wheel; f.wheel = 0; return delta; }
    public static int mouseY() { Frame f = frame(); return f.height - f.mouseY - 1; }
    public static void unsupported(String name) { throw new UnsupportedOperationException("Legacy rendering backend does not support " + name); }
}
