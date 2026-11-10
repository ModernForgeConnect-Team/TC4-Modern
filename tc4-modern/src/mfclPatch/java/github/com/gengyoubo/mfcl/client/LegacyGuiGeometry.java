package github.com.gengyoubo.mfcl.client;

import github.com.gengyoubo.mfcl.bridge.LegacyVertexStream;

/** Order-preserving GUI mesh batching and allocation-free conversion to the host's quad format. */
final class LegacyGuiGeometry {
    private LegacyGuiGeometry() {}
    static boolean compatible(LegacyVertexStream.Primitive a,LegacyVertexStream.Primitive b) {
        boolean texturedA=a.mode()>3&&!a.texture().isEmpty(),texturedB=b.mode()>3&&!b.texture().isEmpty();
        return texturedA==texturedB&&(!texturedA||a.texture().equals(b.texture()))&&a.state().equals(b.state());
    }
    @FunctionalInterface interface QuadSink {void quad(int a,int b,int c,int d);}
    static void quads(int mode,int count,QuadSink sink) {
        switch(mode) {
            case LegacyVertexStream.GL_QUADS -> {for(int i=0;i+3<count;i+=4)sink.quad(i,i+1,i+2,i+3);}
            case LegacyVertexStream.GL_QUAD_STRIP -> {for(int i=0;i+3<count;i+=2)sink.quad(i,i+1,i+3,i+2);}
            case LegacyVertexStream.GL_TRIANGLES -> {for(int i=0;i+2<count;i+=3)sink.quad(i,i+1,i+2,i+2);}
            case LegacyVertexStream.GL_TRIANGLE_STRIP -> {for(int i=0;i+2<count;i++) {
                if((i&1)==0)sink.quad(i,i+1,i+2,i+2);else sink.quad(i+1,i,i+2,i+2);
            }}
            case LegacyVertexStream.GL_TRIANGLE_FAN,LegacyVertexStream.GL_POLYGON -> {for(int i=1;i+1<count;i++)sink.quad(0,i,i+1,i+1);}
            default -> throw new UnsupportedOperationException("Legacy GUI primitive mode: "+mode);
        }
    }
}
