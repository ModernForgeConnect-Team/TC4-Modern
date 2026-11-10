package github.com.gengyoubo.mfcl.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import github.com.gengyoubo.mfcl.bridge.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Replays common old GUI operations through the host renderer and private legacy texture resources. */
final class LegacyGuiRenderer {
    private final LegacyServices services;
    private record Texture(ResourceLocation id,int width,int height) {}
    private final Map<String,Texture> textures=new HashMap<>();
    private final Set<String> missingIcons=new HashSet<>();
    LegacyGuiRenderer(LegacyServices services){this.services=services;}
    void draw(GuiGraphics graphics,List<LegacyGuiGraphics.Command> commands) throws Exception { draw(graphics,commands,false); }
    void draw(GuiGraphics graphics,List<LegacyGuiGraphics.Command> commands,boolean researchBrowser) throws Exception {
        var mc=Minecraft.getInstance();
        boolean depthTest=true;
        boolean textPending=false;
        boolean researchClip=false;
        graphics.flush();
        try {
            for(var command:commands) {
                // Only consecutive text may share the host font buffer. Never move it past a graphic or item.
                if(textPending&&!(command instanceof LegacyGuiGraphics.Text)) {graphics.flush();textPending=false;}
                if(command instanceof LegacyGuiGraphics.ResearchClip clip) {
                    graphics.flush();textPending=false;
                    if(clip.enabled()&&!researchClip) {
                        var screen=mc.screen;int left=(screen.width-256)/2,top=(screen.height-230)/2;
                        graphics.enableScissor(left+16,top+16,left+240,top+214);researchClip=true;
                    } else if(!clip.enabled()&&researchClip) {graphics.disableScissor();researchClip=false;}
                } else if(command instanceof LegacyGuiGraphics.DepthTest state) {
                    // Flush before changing state so buffered item/text batches cannot cross the tooltip boundary.
                    depthTest=state.enabled();
                } else if(command instanceof LegacyGuiGraphics.Rect r) {
                    int x1=Math.min(r.x1(),r.x2()),x2=Math.max(r.x1(),r.x2()),y1=Math.min(r.y1(),r.y2()),y2=Math.max(r.y1(),r.y2());
                    graphics.fillGradient(depthTest?RenderType.gui():RenderType.guiOverlay(),x1,y1,x2,y2,r.top(),r.bottom(),0);
                } else if(command instanceof LegacyGuiGraphics.Text t) {
                    graphics.pose().pushPose();graphics.pose().translate(t.x(),t.y(),0);graphics.pose().scale((float)t.sx(),(float)t.sy(),1);
                    try {
                        mc.font.drawInBatch(t.text(),0,0,t.color(),t.shadow(),graphics.pose().last().pose(),graphics.bufferSource(),depthTest?Font.DisplayMode.NORMAL:Font.DisplayMode.SEE_THROUGH,0,15728880,mc.font.isBidirectional());
                        textPending=true;
                    } finally {graphics.pose().popPose();}
                } else if(command instanceof LegacyGuiGraphics.Texture t) {
                    graphics.pose().pushPose();graphics.pose().translate(t.x(),t.y(),0);graphics.pose().scale((float)t.sx(),(float)t.sy(),1);
                    try {
                        if(depthTest)RenderSystem.enableDepthTest();else RenderSystem.disableDepthTest();
                        graphics.blit(texture(t.resource()).id(),0,0,t.u(),t.v(),t.width(),t.height(),256,256);
                    } finally {graphics.pose().popPose();RenderSystem.enableDepthTest();}
                } else if(command instanceof LegacyGuiGraphics.Item i) {
                    var stack=ModernItemMapping.renderStack(services.items().snapshot(i.stack()));
                    graphics.pose().pushPose();graphics.pose().translate(i.x(),i.y(),0);graphics.pose().scale((float)i.sx(),(float)i.sy(),1);
                    try {
                        if(stack.isEmpty()) { if(!i.overlay()) legacyIcon(graphics,new LegacyGuiGraphics.Item(i.stack(),0,0,false,i.label(),1,1)); }
                        else if(i.overlay())graphics.renderItemDecorations(mc.font,stack,0,0,i.label());
                        else graphics.renderItem(stack,0,0);
                    } finally {graphics.pose().popPose();}
                } else if(command instanceof LegacyGuiGraphics.Mesh mesh) {
                    mesh(graphics,mesh.primitives(),researchBrowser);
                }
                // Overlay backgrounds and glyphs use different render types; preserve their original draw order.
                if(!depthTest) {graphics.flush();textPending=false;}
            }
        } finally {graphics.flush();if(researchClip)graphics.disableScissor();RenderSystem.enableDepthTest();}
    }
    private void mesh(GuiGraphics graphics,List<LegacyVertexStream.Primitive> primitives,boolean researchBrowser) throws Exception {
        graphics.flush();
        var buffer=com.mojang.blaze3d.vertex.Tesselator.getInstance().getBuilder();
        var matrix=graphics.pose().last().pose();
        boolean clipped=false;
        try {
            com.mojang.blaze3d.systems.RenderSystem.disableDepthTest();com.mojang.blaze3d.systems.RenderSystem.disableCull();
            com.mojang.blaze3d.systems.RenderSystem.enableBlend();
            LegacyVertexStream.Primitive previous=null;
            for(var primitive:primitives) {
                var vertices=primitive.vertices();if(vertices.isEmpty())continue;
                boolean lineClip=researchBrowser&&primitive.mode()>=1&&primitive.mode()<=3;
                if(lineClip!=clipped) {
                    if(previous!=null){com.mojang.blaze3d.vertex.BufferUploader.drawWithShader(buffer.end());previous=null;}
                    if(lineClip) {
                        var screen=Minecraft.getInstance().screen;
                        int left=(screen.width-256)/2,top=(screen.height-230)/2;
                        graphics.enableScissor(left+16,top+16,left+240,top+214);
                    } else graphics.disableScissor();
                    clipped=lineClip;
                }
                boolean textured=primitive.mode()>3&&!primitive.texture().isEmpty();
                if(previous==null||!LegacyGuiGeometry.compatible(previous,primitive)) {
                    if(previous!=null)com.mojang.blaze3d.vertex.BufferUploader.drawWithShader(buffer.end());
                com.mojang.blaze3d.systems.RenderSystem.blendFunc(primitive.state().sourceBlend(),primitive.state().destinationBlend());
                if(textured) {
                    com.mojang.blaze3d.systems.RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionTexColorShader);
                    com.mojang.blaze3d.systems.RenderSystem.setShaderTexture(0,texture(primitive.texture()).id());
                } else com.mojang.blaze3d.systems.RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionColorShader);
                buffer.begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS,textured?com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_TEX_COLOR:com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR);
                }
                previous=primitive;
                if(primitive.mode()<=3) {
                    int edges=primitive.mode()==1?vertices.size()/2:primitive.mode()==2?vertices.size():vertices.size()-1;
                    for(int edge=0;edge<edges;edge++) {
                        var a=vertices.get(primitive.mode()==1?edge*2:edge);var b=vertices.get(primitive.mode()==1?edge*2+1:(edge+1)%vertices.size());
                        double dx=b.x()-a.x(),dy=b.y()-a.y(),length=Math.hypot(dx,dy);if(length==0)continue;
                        // OpenGL line widths are framebuffer pixels, independent of GUI scale and model transforms.
                        double halfWidth=primitive.state().lineWidth()/(2.0*Minecraft.getInstance().getWindow().getGuiScale());
                        double ox=-dy/length*halfWidth,oy=dx/length*halfWidth;
                        vertex(buffer,matrix,a,a.x()+ox,a.y()+oy,false);vertex(buffer,matrix,b,b.x()+ox,b.y()+oy,false);
                        vertex(buffer,matrix,b,b.x()-ox,b.y()-oy,false);vertex(buffer,matrix,a,a.x()-ox,a.y()-oy,false);
                    }
                } else LegacyGuiGeometry.quads(primitive.mode(),vertices.size(),(a,b,c,d)->{
                    vertex(buffer,matrix,vertices.get(a),textured);vertex(buffer,matrix,vertices.get(b),textured);
                    vertex(buffer,matrix,vertices.get(c),textured);vertex(buffer,matrix,vertices.get(d),textured);
                });
            }
            if(previous!=null)com.mojang.blaze3d.vertex.BufferUploader.drawWithShader(buffer.end());
        } finally {
            // A missing texture or invalid primitive must not leave the shared Tessellator building.
            if(buffer.building())buffer.end().release();
            if(clipped)graphics.disableScissor();
            com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();com.mojang.blaze3d.systems.RenderSystem.enableCull();
        }
    }
    private void vertex(com.mojang.blaze3d.vertex.BufferBuilder buffer,org.joml.Matrix4f matrix,LegacyVertexStream.Vertex v,boolean textured) {
        vertex(buffer,matrix,v,v.x(),v.y(),textured);
    }
    private void vertex(com.mojang.blaze3d.vertex.BufferBuilder buffer,org.joml.Matrix4f matrix,LegacyVertexStream.Vertex v,double x,double y,boolean textured) {
        buffer.vertex(matrix,(float)x,(float)y,0);if(textured)buffer.uv(v.u(),v.v());
        buffer.color(v.argb()>>16&255,v.argb()>>8&255,v.argb()&255,v.argb()>>>24).endVertex();
    }
    private void legacyIcon(GuiGraphics graphics,LegacyGuiGraphics.Item item) throws Exception {
        Object icon=item.stack().getClass().getMethod("func_77954_c").invoke(item.stack());
        if(icon!=null) {
            String name=(String)services.loader().loadClass("net.minecraft.util.IIcon").getMethod("func_94215_i").invoke(icon);
            ResourceLocation id=ResourceLocation.tryParse(name);
            if(id!=null)for(String directory:List.of("blocks","items")) {
                String key=id.getNamespace()+":textures/"+directory+"/"+id.getPath()+".png";
                if(services.loader().getResource("assets/"+id.getNamespace()+"/textures/"+directory+"/"+id.getPath()+".png")==null)continue;
                Texture texture=texture(key);int size=Math.min(texture.width(),texture.height());
                graphics.blit(texture.id(),item.x(),item.y(),16,16,0,0,size,size,texture.width(),texture.height());return;
            }
        }
        var snapshot=services.items().snapshot(item.stack());String key=snapshot.key()+"@"+snapshot.metadata();
        if(missingIcons.add(key))com.mojang.logging.LogUtils.getLogger().warn("MFCL: no modern model or original 2D icon for {}",key);
    }
    private Texture texture(String key) throws Exception {
        Texture cached=textures.get(key);if(cached!=null)return cached;
        // Legacy jars first; then the host resource manager so modern mods/resource packs can supply textures (e.g. addon aspects).
        try(var input=openTexture(key)) {
            if(input==null)throw new IllegalArgumentException("Missing original GUI texture: "+key);
            var image=NativeImage.read(input);var location=Minecraft.getInstance().getTextureManager().register("mfcl/legacy_gui",new DynamicTexture(image));
            Texture texture=new Texture(location,image.getWidth(),image.getHeight());textures.put(key,texture);return texture;
        }
    }
    private java.io.InputStream openTexture(String key) throws java.io.IOException {
        var legacy=services.loader().getResourceAsStream(LegacyTexturePaths.legacyResource(key));
        if(legacy!=null)return legacy;
        var id=ResourceLocation.tryParse(LegacyTexturePaths.modernResource(key).toLowerCase(Locale.ROOT));
        if(id==null)return null;
        var modern=Minecraft.getInstance().getResourceManager().getResource(id);
        return modern.isPresent()?modern.get().open():null;
    }
}
