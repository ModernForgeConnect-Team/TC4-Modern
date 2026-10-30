package github.com.gengyoubo.tcmodern.tc4.client.research;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import github.com.gengyoubo.tcmodern.api.aspect.TcAspect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** A screen-owned atlas assembled from installed resources. All aspect/hex glyphs share one ordered upload. */
final class ResearchTableAtlas implements AutoCloseable {
    private record Source(String key,ResourceLocation texture,int column,int row,int columns,int rows,boolean rotated) {}
    private record Region(float u,float v,float u1,float v1) {}
    private final ResourceLocation texture;
    private final Map<String,Region> regions=new HashMap<>();
    ResearchTableAtlas(Collection<TcAspect> aspects) {
        var sources=new ArrayList<Source>();
        sources.add(new Source("@unknown",id("aspects/_unknown.png"),0,0,1,1,false));
        for(var aspect:aspects)sources.add(new Source(aspect.tag(),ResourceLocation.tryParse(aspect.texture()),0,0,1,1,false));
        sources.add(new Source("@hex",id("gui/hex1.png"),0,0,1,1,false));
        sources.add(new Source("@gold",id("gui/hex2.png"),0,0,1,1,false));
        sources.add(new Source("@back",id("aspects/_back.png"),0,0,1,1,false));
        for(int i=0;i<16;i++){
            sources.add(new Source("@rune"+i,id("misc/script.png"),i,0,16,1,false));
            sources.add(new Source("@bonus"+i,id("misc/particles.png"),i,5,16,16,false));
        }
        for(int i=0;i<8;i++)sources.add(new Source("@orb"+i,id("misc/particles.png"),(8+i*2)%16,8,16,16,true));
        int size=64;while((size/36)*(size/36)<sources.size())size*=2;
        var image=new NativeImage(size,size,true);
        var resources=Minecraft.getInstance().getResourceManager();
        for(int i=0;i<sources.size();i++){
            var source=sources.get(i);int x=(i%(size/36))*36+2,y=(i/(size/36))*36+2;
            boolean copied=false;
            if(source.texture()!=null)try(var stream=resources.open(source.texture());var glyph=NativeImage.read(stream)){
                int width=glyph.getWidth()/source.columns(),height=glyph.getHeight()/source.rows();
                for(int gy=0;gy<32;gy++)for(int gx=0;gx<32;gx++){
                    int sx=source.rotated()?gy:gx,sy=source.rotated()?31-gx:gy;
                    image.setPixelRGBA(x+gx,y+gy,glyph.getPixelRGBA(source.column()*width+sx*width/32,source.row()*height+sy*height/32));
                }
                copied=true;
            }catch(java.io.IOException missing){/* An addon may omit its icon; retain a visible tinted placeholder. */}
            if(!copied)for(int gy=0;gy<32;gy++)for(int gx=0;gx<32;gx++)image.setPixelRGBA(x+gx,y+gy,(gx<16)^(gy<16)?0xff000000:0xffff00ff);
            regions.put(source.key(),new Region(x/(float)size,y/(float)size,(x+32)/(float)size,(y+32)/(float)size));
        }
        texture=Minecraft.getInstance().getTextureManager().register("tc4modern/research_table",new DynamicTexture(image));
    }
    private static ResourceLocation id(String path){return ResourceLocation.fromNamespaceAndPath("thaumcraft","textures/"+path);}
    Batch begin(GuiGraphics graphics){return new Batch(graphics);}
    final class Batch implements AutoCloseable {
        private final BufferBuilder buffer=Tesselator.getInstance().getBuilder();
        private final org.joml.Matrix4f matrix;
        private boolean additive;
        Batch(GuiGraphics graphics){
            graphics.flush();matrix=graphics.pose().last().pose();
            RenderSystem.disableDepthTest();RenderSystem.disableCull();RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor(1,1,1,1);RenderSystem.setShader(GameRenderer::getPositionTexColorShader);RenderSystem.setShaderTexture(0,texture);
            buffer.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_TEX_COLOR);
        }
        void glyph(String key,float x,float y,float size,int color){
            var region=regions.getOrDefault(key,regions.get("@unknown"));
            vertex(x,y,region.u(),region.v(),color);vertex(x,y+size,region.u(),region.v1(),color);
            vertex(x+size,y+size,region.u1(),region.v1(),color);vertex(x+size,y,region.u1(),region.v(),color);
        }
        void additive(boolean enabled){
            if(additive==enabled)return;
            BufferUploader.drawWithShader(buffer.end());additive=enabled;
            if(enabled)RenderSystem.blendFunc(770,1);else RenderSystem.defaultBlendFunc();
            buffer.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_TEX_COLOR);
        }
        private void vertex(float x,float y,float u,float v,int color){buffer.vertex(matrix,x,y,0).uv(u,v)
                .color(color>>16&255,color>>8&255,color&255,color>>>24).endVertex();}
        public void close(){
            try{BufferUploader.drawWithShader(buffer.end());}
            finally{RenderSystem.enableDepthTest();RenderSystem.enableCull();RenderSystem.defaultBlendFunc();RenderSystem.disableBlend();}
        }
    }
    @Override public void close(){Minecraft.getInstance().getTextureManager().release(texture);}
}
