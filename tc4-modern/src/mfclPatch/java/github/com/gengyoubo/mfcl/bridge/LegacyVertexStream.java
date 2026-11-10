package github.com.gengyoubo.mfcl.bridge;

import java.util.List;

/** Pure legacy geometry and matrix math. No host OpenGL type or context is ever entered. */
public final class LegacyVertexStream {
    public record Vertex(double x, double y, double z, float u, float v, float nx, float ny, float nz, int argb) {}
    public record RenderState(boolean blend, int sourceBlend, int destinationBlend, boolean depthWrite, boolean cull, boolean depthTest, float lineWidth) {
        public RenderState(boolean blend,int sourceBlend,int destinationBlend,boolean depthWrite,boolean cull,boolean depthTest){this(blend,sourceBlend,destinationBlend,depthWrite,cull,depthTest,1f);}
        public RenderState(boolean blend,int sourceBlend,int destinationBlend,boolean depthWrite,boolean cull){this(blend,sourceBlend,destinationBlend,depthWrite,cull,true);}
        public static final RenderState OPAQUE = new RenderState(false, 770, 771, true, false);
    }
    public record Primitive(int mode, String texture, List<Vertex> vertices, RenderState state) {
        public Primitive(int mode, String texture, List<Vertex> vertices) { this(mode, texture, vertices, RenderState.OPAQUE); }
        public Primitive { vertices = List.copyOf(vertices); }
    }
    /** Modern consumer of captured geometry: one primitive at a time, already in legacy world space. */
    public interface Sink {
        void begin(int mode, String texture, int vertexCount);
        void vertex(Vertex vertex);
        default void end() { }
    }
    public static final int GL_TRIANGLES = 4, GL_TRIANGLE_STRIP = 5, GL_TRIANGLE_FAN = 6,
            GL_QUADS = 7, GL_QUAD_STRIP = 8, GL_POLYGON = 9;
    private LegacyVertexStream() { }
    /**
     * TC4's inventory helper gives X faces a Z normal and Z faces an X normal, which swaps the side shading under
     * host item lighting. Only that exact mismatch is corrected; deliberately flipped or non-axial normals stay.
     */
    public static List<Primitive> correctSwappedSideNormals(List<Primitive> primitives) {
        var result = new java.util.ArrayList<Primitive>(primitives.size());
        for (var primitive : primitives) {
            if (primitive.mode() != GL_QUADS || primitive.vertices().size() % 4 != 0) { result.add(primitive); continue; }
            var vertices = new java.util.ArrayList<>(primitive.vertices());
            for (int base = 0; base < vertices.size(); base += 4) {
                Vertex a = vertices.get(base), b = vertices.get(base+1), c = vertices.get(base+2);
                double bx = b.x()-a.x(), by = b.y()-a.y(), bz = b.z()-a.z(), cx = c.x()-a.x(), cy = c.y()-a.y(), cz = c.z()-a.z();
                double x = by*cz-bz*cy, y = bz*cx-bx*cz, z = bx*cy-by*cx, length = Math.sqrt(x*x+y*y+z*z);
                if (length < 1e-9) continue;
                x /= length; y /= length; z /= length;
                boolean faceX = isSideAxis(x, y, z), faceZ = isSideAxis(z, y, x);
                if (!faceX && !faceZ) continue;
                boolean swapped = true;
                for (int index = base; index < base+4; index++) {
                    var vertex = vertices.get(index);
                    swapped &= faceX ? isSideAxis(vertex.nz(), vertex.ny(), vertex.nx()) : isSideAxis(vertex.nx(), vertex.ny(), vertex.nz());
                }
                if (!swapped) continue;
                for (int index = base; index < base+4; index++) {
                    var v = vertices.get(index);
                    vertices.set(index, new Vertex(v.x(), v.y(), v.z(), v.u(), v.v(), (float)x, (float)y, (float)z, v.argb()));
                }
            }
            result.add(new Primitive(primitive.mode(), primitive.texture(), vertices, primitive.state()));
        }
        return List.copyOf(result);
    }
    private static boolean isSideAxis(double axis, double y, double other) {
        return Math.abs(Math.abs(axis)-1) < 1e-3 && Math.abs(y) < 1e-3 && Math.abs(other) < 1e-3;
    }
    public static double[] identity() { return new double[]{1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1}; }
    public static double[] copy(double[] matrix) { return matrix.clone(); }
    public static void multiply(double[] left, double[] right) {
        double[] result = new double[16];
        for (int column = 0; column < 4; column++) for (int row = 0; row < 4; row++) {
            double sum = 0;
            for (int k = 0; k < 4; k++) sum += left[k * 4 + row] * right[column * 4 + k];
            result[column * 4 + row] = sum;
        }
        System.arraycopy(result, 0, left, 0, 16);
    }
    public static void translate(double[] matrix, double x, double y, double z) {
        multiply(matrix, new double[]{1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, x, y, z, 1});
    }
    public static void scale(double[] matrix, double x, double y, double z) {
        multiply(matrix, new double[]{x, 0, 0, 0, 0, y, 0, 0, 0, 0, z, 0, 0, 0, 0, 1});
    }
    public static void rotate(double[] matrix, double angleDegrees, double x, double y, double z) {
        double length = Math.sqrt(x * x + y * y + z * z);
        if (length == 0) return;
        x /= length; y /= length; z /= length;
        double radians = Math.toRadians(angleDegrees), c = Math.cos(radians), s = Math.sin(radians), t = 1 - c;
        // Row-major rotation, written column-major for multiply().
        multiply(matrix, new double[]{
                t * x * x + c, t * x * y + s * z, t * x * z - s * y, 0,
                t * x * y - s * z, t * y * y + c, t * y * z + s * x, 0,
                t * x * z + s * y, t * y * z - s * x, t * z * z + c, 0,
                0, 0, 0, 1});
    }
    /** Inverse of an affine column-major matrix, used to store display-list geometry in local space. */
    public static double[] invert(double[] m) {
        double a00 = m[0], a01 = m[4], a02 = m[8];
        double a10 = m[1], a11 = m[5], a12 = m[9];
        double a20 = m[2], a21 = m[6], a22 = m[10];
        double determinant = a00 * (a11 * a22 - a12 * a21) - a01 * (a10 * a22 - a12 * a20) + a02 * (a10 * a21 - a11 * a20);
        if (determinant == 0) return identity();
        double inv = 1.0 / determinant;
        double b00 = (a11 * a22 - a12 * a21) * inv, b01 = (a02 * a21 - a01 * a22) * inv, b02 = (a01 * a12 - a02 * a11) * inv;
        double b10 = (a12 * a20 - a10 * a22) * inv, b11 = (a00 * a22 - a02 * a20) * inv, b12 = (a02 * a10 - a00 * a12) * inv;
        double b20 = (a10 * a21 - a11 * a20) * inv, b21 = (a01 * a20 - a00 * a21) * inv, b22 = (a00 * a11 - a01 * a10) * inv;
        double tx = m[12], ty = m[13], tz = m[14];
        return new double[]{
                b00, b10, b20, 0,
                b01, b11, b21, 0,
                b02, b12, b22, 0,
                -(b00 * tx + b01 * ty + b02 * tz), -(b10 * tx + b11 * ty + b12 * tz), -(b20 * tx + b21 * ty + b22 * tz), 1};
    }
    public static double[] transform(double[] matrix, double x, double y, double z) {
        return new double[]{
                matrix[0] * x + matrix[4] * y + matrix[8] * z + matrix[12],
                matrix[1] * x + matrix[5] * y + matrix[9] * z + matrix[13],
                matrix[2] * x + matrix[6] * y + matrix[10] * z + matrix[14]};
    }
    public static void replay(List<Primitive> primitives, Sink sink) {
        for (Primitive primitive : primitives) {
            sink.begin(primitive.mode(), primitive.texture(), primitive.vertices().size());
            for (Vertex vertex : primitive.vertices()) sink.vertex(vertex);
            sink.end();
        }
    }
}
