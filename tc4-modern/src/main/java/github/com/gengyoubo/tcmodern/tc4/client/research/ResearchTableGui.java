package github.com.gengyoubo.tcmodern.tc4.client.research;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import github.com.gengyoubo.mfcl.api.*;
import github.com.gengyoubo.tcmodern.api.aspect.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import java.util.*;
import static github.com.gengyoubo.tcmodern.tc4.client.research.ResearchTableAccessors.*;
import static github.com.gengyoubo.tcmodern.tc4.client.research.ResearchTableLayout.*;

/** Native modern view of the original research menu. Server packets and inventory ownership stay original. */
public final class ResearchTableGui implements LegacyClientScreens.Controller {
    private static final ResourceLocation TABLE=ResourceLocation.fromNamespaceAndPath("thaumcraft","textures/gui/guiresearchtable2.png");
    private static final ResourceLocation PAPER=ResourceLocation.fromNamespaceAndPath("thaumcraft","textures/misc/parchment3.png");
    private static final Map<AbstractContainerScreen<?>,ResearchTableGui> OPEN=new IdentityHashMap<>();
    final LegacyClientScreens.Context context;
    private final AbstractContainerScreen<?> host;
    private final Map<String,TcAspect> aspects=new LinkedHashMap<>();
    private final Map<String,ResearchTableTooltip> tooltips=new HashMap<>();
    private final ResearchTableAtlas atlas;
    private ResearchTableState state=ResearchTableState.empty();
    private ResearchTableNotifications notifications=ResearchTableNotifications.empty();
    private ItemStack lastNote=ItemStack.EMPTY;
    private boolean first=true,closed;
    private boolean primaryDown;
    private int page;
    private String select1,select2,dragged,pressed;
    private long combineUntil,lastRuneUpdate;
    private List<Link> links=List.of();
    private Map<Hex,ResearchTableState.Cell> cells=Map.of();
    private Set<String> highlighted=Set.of();
    private final Map<Hex,Rune> runes=new LinkedHashMap<>();
    private record Link(ResearchTableState.Cell a,ResearchTableState.Cell b) {}
    private record Rune(Hex hex,int glyph,long start,long end) {}
    private enum Sound {BUTTON,ASPECT,COMBINE,WRITE,ERASE,PAGE}
    static AutoCloseable install(LegacyRuntime runtime){return runtime.clientScreens().register("thaumcraft.client.gui.GuiResearchTable",ResearchTableGui::new);}
    ResearchTableGui(LegacyClientScreens.Context context){
        this.context=context;host=context.hostScreen(AbstractContainerScreen.class);
        for(var aspect:TcAspects.snapshot(context.runtime()))aspects.put(aspect.tag(),aspect);
        var runtime=context.runtime();
        for(var value:runtime.values().map(runtime.accessor(TcAspectAccessors.Registry.class).aspects()).values()){
            var reference=(LegacyObject)value;var text=runtime.accessor(AspectText.class,reference);
            tooltips.put(runtime.accessor(TcAspectAccessors.Aspect.class,reference).tag(),new ResearchTableTooltip(text.name(),text.description()));
        }
        atlas=new ResearchTableAtlas(aspects.values());OPEN.put(host,this);
    }
    static ResearchTableGui active(Screen screen){return OPEN.get(screen);}
    ResearchTableState state(){return state;}
    int page(){return page;}
    List<String> selection(){return List.of(Objects.toString(select1,""),Objects.toString(select2,""));}
    @Override public void tick(){
        if(closed)return;
        ItemStack note=host.getMenu().slots.size()>1?host.getMenu().getSlot(1).getItem():ItemStack.EMPTY;
        boolean changed=first||!ItemStack.matches(note,lastNote);
        var previous=state;var next=ResearchTableState.read(context,state.sheet(),changed);if(next==null)return;state=next;
        if(changed){lastNote=note.copy();first=false;}
        page=Math.max(0,Math.min(page,lastPage(state.palette().size())));
        if(!state.available(select1))select1=null;if(!state.available(select2))select2=null;
        if(state.sheet()!=previous.sheet()||!state.pools().keySet().equals(previous.pools().keySet()))buildLinks();
        updateRunes();
        notifications=ResearchTableNotifications.read(context);
    }
    private void buildLinks(){
        if(state.sheet()==null){links=List.of();cells=Map.of();highlighted=Set.of();return;}
        var byPosition=new LinkedHashMap<Hex,ResearchTableState.Cell>();
        for(var cell:state.sheet().cells())byPosition.put(new Hex(cell.q(),cell.r()),cell);
        cells=Map.copyOf(byPosition);var result=new ArrayList<Link>();var visited=new HashSet<String>();var lit=new HashSet<String>();
        for(var cell:state.sheet().cells())if(cell.type()==1&&known(cell.aspect()))visit(cell,visited,lit,result);
        links=List.copyOf(result);highlighted=Set.copyOf(lit);
    }
    private void visit(ResearchTableState.Cell cell,Set<String> visited,Set<String> lit,List<Link> result){
        visited.add(cell.key());
        for(int[] direction:new int[][]{{1,0},{1,-1},{0,-1},{-1,0},{-1,1},{0,1}}){
            var target=cells.get(new Hex(cell.q()+direction[0],cell.r()+direction[1]));
            if(target==null||target.type()<1||visited.contains(target.key())||!known(target.aspect())||!connects(cell.aspect(),target.aspect()))continue;
            result.add(new Link(cell,target));lit.add(target.key());visit(target,visited,lit,result);
        }
    }
    private void updateRunes(){
        if(state.sheet()==null||state.sheet().key()==null||state.sheet().key().isEmpty()){runes.clear();return;}
        long now=System.currentTimeMillis();runes.values().removeIf(rune->rune.end()<now);
        if(now<=lastRuneUpdate)return;lastRuneUpdate=now+250;
        var random=Minecraft.getInstance().level.random;
        var position=hexAt(169+random.nextInt(120)-60,83+random.nextInt(120)-60);
        if(!cells.containsKey(position)&&!runes.containsKey(position)){
            long end=lastRuneUpdate+15000+random.nextInt(10000);runes.put(position,new Rune(position,random.nextInt(16),now,end));
        }
    }
    private boolean known(String tag){return tag!=null&&state.pools().containsKey(tag);}
    private boolean connects(String a,String b){return aspects.containsKey(a)&&aspects.containsKey(b)&&(aspects.get(a).components().contains(b)||aspects.get(b).components().contains(a));}
    private int color(String tag){var aspect=aspects.get(tag);return aspect==null?0xffffff:aspect.color();}
    private int left(){return host.getGuiLeft();}private int top(){return host.getGuiTop();}
    private ResearchTableState.Cell cellAt(double x,double y){
        return cells.get(hexAt(x,y));
    }
    @Override public void renderBackground(Object target,int left,int top,int mouseX,int mouseY,float partial){
        var graphics=(GuiGraphics)target;long now=net.minecraft.Util.getMillis();int ticks=Minecraft.getInstance().player.tickCount;
        RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();graphics.setColor(1,1,1,1);
        graphics.blit(TABLE,left,top,0,0,255,167);graphics.blit(TABLE,left+40,top+167,0,166,184,88);
        if(page>0)graphics.blit(TABLE,left+27,top+121,184,208,24,8);
        if(page<lastPage(state.palette().size()))graphics.blit(TABLE,left+51,top+121,208,208,24,8);
        if(select1!=null&&select2!=null){graphics.blit(TABLE,left+35,top+139,184,184,32,16);
            if(now<combineUntil)graphics.blit(TABLE,left+35,top+139,184,168,32,16);}
        if(state.sheet()!=null){graphics.blit(PAPER,left+94,top+8,0,0,150,150);
            if(state.duplicate()&&state.sheet().complete())graphics.blit(TABLE,left+37,top+5,232,200,24,24);}
        RenderSystem.disableBlend();
        var hover=cellAt(mouseX-left,mouseY-top);
        if(!links.isEmpty())drawLinks(graphics,left,top,now);
        try(var batch=atlas.begin(graphics)){
            if(state.sheet()!=null){
                for(var rune:runes.values()){
                    double progress=(System.currentTimeMillis()-rune.start())/(double)(rune.end()-rune.start());
                    double alpha=progress<.25?progress*2:progress>.5?1-progress:.5;
                    float x=(float)(left+169+rune.hex().q()*13.5-5),y=(float)(top+83+Math.sqrt(3)*9*(rune.hex().r()+rune.hex().q()/2.0)-5);
                    batch.glyph("@rune"+rune.glyph(),x,y,10,(int)(alpha*.66*255)<<24);
                }
                for(var cell:state.sheet().cells()){
                    float x=(float)(left+cell.x()-8),y=(float)(top+cell.y()-8);
                    if(!state.sheet().complete()){
                        if(cell.type()==1)orb(batch,x,y,null,ticks);
                        else {
                            if(cell==hover){batch.additive(true);batch.glyph("@gold",x,y,16,0xffffffff);batch.additive(false);}
                            batch.glyph("@hex",x,y,16,0x3fffffff);
                        }
                    }
                    if(cell.aspect()!=null&&!known(cell.aspect()))batch.glyph("@unknown",x,y,16,0x80000000);
                    else if(cell.aspect()!=null){boolean lit=cell.type()==1||highlighted.contains(cell.key());
                        batch.glyph(cell.aspect(),x,y,16,lit?0xff000000|color(cell.aspect()):0x87191919);}
                }
            }
            for(int i=0;i<25&&page*5+i<state.palette().size();i++){
                var pool=state.palette().get(page*5+i);batch.glyph(pool.tag(),left+10+i/5*16,top+40+i%5*16,16,(pool.available()?0xff000000:0x54000000)|color(pool.tag()));
                if(pool.bonus()>0)batch.glyph("@bonus"+(ticks%16),left+6+i/5*16,top+36+i%5*16,16,0xffffffff);
            }
            if(select1!=null)batch.glyph(select1,left+13,top+139,16,0xff000000|color(select1));
            if(select2!=null)batch.glyph(select2,left+71,top+139,16,0xff000000|color(select2));
            if(select1!=null&&select2!=null&&now>=combineUntil)orb(batch,left+43,top+139,null,ticks);
        }
        // All palette amounts share the font buffer instead of submitting each outline separately.
        for(int i=0;i<25&&page*5+i<state.palette().size();i++){
            var pool=state.palette().get(page*5+i);int x=left+10+i/5*16,y=top+40+i%5*16;
            if(pool.amount()>0)amount(graphics,Integer.toString(pool.amount()),x,y,false);
            if(pool.bonus()>1)amount(graphics,Integer.toString(pool.bonus()),x,y,true);
        }
        graphics.flush();
    }
    private void orb(ResearchTableAtlas.Batch batch,float x,float y,Integer tint,int ticks){
        int rgb=tint!=null?tint:((int)((.85+.15*Math.sin((ticks+x)/10))*255)<<16)|((int)((.85+.15*Math.sin((ticks+x+y)/11))*255)<<8)|(int)((.85+.15*Math.sin((ticks+y)/12))*255);
        if(state.colorBlind())rgb=((int)((rgb>>16&255)/1.8)<<16)|((int)((rgb>>8&255)/1.8)<<8)|(int)((rgb&255)/1.8);
        batch.glyph("@orb"+(ticks%8),x,y,16,0xff000000|rgb);
    }
    private static void amount(GuiGraphics graphics,String label,int x,int y,boolean bonus){
        var font=Minecraft.getInstance().font;float tx=bonus?x+4-font.width(label)*.25f:x+16-font.width(label)*.5f;
        float ty=bonus?y+7.5f-font.lineHeight*.5f:y+16-font.lineHeight*.5f;
        graphics.pose().pushPose();graphics.pose().translate(tx,ty,0);graphics.pose().scale(.5f,.5f,1);
        text(graphics,label,-1,0,0xff000000,false);text(graphics,label,1,0,0xff000000,false);
        text(graphics,label,0,-1,0xff000000,false);text(graphics,label,0,1,0xff000000,false);text(graphics,label,0,0,0xffffffff,false);graphics.pose().popPose();
    }
    private static void text(GuiGraphics graphics,String text,float x,float y,int color,boolean shadow){
        var font=Minecraft.getInstance().font;
        font.drawInBatch(text,x,y,color,shadow,graphics.pose().last().pose(),graphics.bufferSource(),Font.DisplayMode.NORMAL,0,15728880,font.isBidirectional());
    }
    private void drawLinks(GuiGraphics graphics,int left,int top,long now){
        graphics.flush();var buffer=Tesselator.getInstance().getBuilder();var matrix=graphics.pose().last().pose();
        RenderSystem.disableDepthTest();RenderSystem.disableCull();RenderSystem.enableBlend();RenderSystem.blendFunc(770,1);RenderSystem.setShader(GameRenderer::getPositionColorShader);
        buffer.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_COLOR);
        for(var link:links){float x=(float)(left+link.a().x()),y=(float)(top+link.a().y()),x1=(float)(left+link.b().x()),y1=(float)(top+link.b().y());
            float dx=x1-x,dy=y1-y,length=(float)Math.hypot(dx,dy),half=(float)(1.5/Minecraft.getInstance().getWindow().getGuiScale()),ox=-dy/length*half,oy=dx/length*half;
            int alpha=(int)((.6+.3*Math.sin(Minecraft.getInstance().player.tickCount+x))*255);
            buffer.vertex(matrix,x+ox,y+oy,0).color(0,153,204,alpha).endVertex();buffer.vertex(matrix,x1+ox,y1+oy,0).color(0,153,204,alpha).endVertex();
            buffer.vertex(matrix,x1-ox,y1-oy,0).color(0,153,204,alpha).endVertex();buffer.vertex(matrix,x-ox,y-oy,0).color(0,153,204,alpha).endVertex();
        }
        BufferUploader.drawWithShader(buffer.end());RenderSystem.enableDepthTest();RenderSystem.enableCull();RenderSystem.defaultBlendFunc();RenderSystem.disableBlend();
    }
    @Override public void renderForeground(Object target,int mouseX,int mouseY,float partial){
        var graphics=(GuiGraphics)target;
        renderNotifications(graphics);
        int index=paletteIndex(mouseX-left(),mouseY-top(),page,state.palette().size());
        if(primaryDown&&dragged==null&&index>=0&&state.palette().get(index).available()){
            dragged=pressed=state.palette().get(index).tag();sound(Sound.ASPECT);
        }
        if(dragged!=null)try(var batch=atlas.begin(graphics)){orb(batch,mouseX-8,mouseY-8,color(dragged),Minecraft.getInstance().player.tickCount);}
        String tag=index>=0?state.palette().get(index).tag():null;
        if(tag==null&&inside(mouseX-left(),mouseY-top(),13,139,16,16))tag=select1;
        if(tag==null&&inside(mouseX-left(),mouseY-top(),71,139,16,16))tag=select2;
        if(tag!=null){
            var tooltip=tooltips.get(tag);if(tooltip!=null)tooltip(graphics,tooltip.lines(),mouseX,mouseY-8);
            var aspect=aspects.get(tag);
            if(index>=0&&state.researcher()&&aspect!=null&&aspect.components().size()==2){
                try(var batch=atlas.begin(graphics)){
                    batch.glyph("@back",mouseX+6,mouseY+6,20,0xffffffff);batch.glyph("@back",mouseX+24,mouseY+6,20,0xffffffff);
                    String first=aspect.components().get(0),second=aspect.components().get(1);
                    batch.glyph(first,mouseX+8,mouseY+8,16,0xff000000|color(first));batch.glyph(second,mouseX+26,mouseY+8,16,0xff000000|color(second));
                }
            }
        }
        else if(state.duplicate()&&state.sheet()!=null&&state.sheet().complete()&&inside(mouseX-left(),mouseY-top(),37,5,24,24)){
            RenderSystem.enableBlend();graphics.blit(TABLE,left()+100,top()+21,184,224,48,16);RenderSystem.disableBlend();
            int offset=0;
            try(var batch=atlas.begin(graphics)){for(var aspect:state.sheet().copyCost().keySet()){batch.glyph(aspect,left()+148+offset++*16,top()+21,16,0xff000000|color(aspect));}}
            offset=0;for(var cost:state.sheet().copyCost().values())amount(graphics,Integer.toString(cost),left()+148+offset++*16,top()+21,false);
            text(graphics,I18n.get("tc.research.copy"),left()+100,top()+12,0xffffffff,true);graphics.flush();
        }
        if(state.sheet()!=null&&!state.ink()){
            var lines=List.of(I18n.get("tile.researchtable.noink.0"),I18n.get("tile.researchtable.noink.1"));
            int width=lines.stream().mapToInt(Minecraft.getInstance().font::width).max().orElse(0)/2;
            tooltip(graphics,lines,left()+157-width,top()+84);
        }
    }
    private void renderNotifications(GuiGraphics graphics){
        long now=System.nanoTime()/1_000_000L;float shift=-8;var mc=Minecraft.getInstance();
        var notices=notifications.entries();int delay=Math.max(1,notifications.delay());
        for(int i=0;i<Math.min(notifications.limit(),notices.size());i++){
            var notice=notices.get(i);int alpha=255,halfWidth=mc.font.width(notice.text())/2;
            if(i==notices.size()-1&&notice.created()>now)alpha=255-(int)((notice.created()-now)/(float)Math.max(1,delay/4)*240);
            if(notice.expire()<now+delay){alpha=(int)(255-(now+delay-notice.expire())/(float)delay*240);shift=-8*alpha/255f;}
            alpha=Math.max(0,Math.min(255,alpha));float y=host.height-i*8+shift;
            graphics.pose().pushPose();graphics.pose().translate(host.width-halfWidth-10,y,400);graphics.pose().scale(.5f,.5f,1);
            mc.font.drawInBatch(notice.text(),-4,-8,(alpha/2<<24)|0xffffff,true,graphics.pose().last().pose(),graphics.bufferSource(),Font.DisplayMode.SEE_THROUGH,0,15728880,mc.font.isBidirectional());
            graphics.pose().popPose();graphics.flush();
            if(notice.image()!=null){var texture=ResourceLocation.tryParse(notice.image());
                if(texture!=null){int rgb=notice.color();graphics.setColor((rgb>>16&255)/255f,(rgb>>8&255)/255f,(rgb&255)/255f,alpha/511f);
                    RenderSystem.enableBlend();graphics.blit(texture,host.width-9,(int)(y-6),8,8,0,0,256,256,256,256);graphics.setColor(1,1,1,1);RenderSystem.disableBlend();}}
            if(i==notices.size()-1&&notice.created()>now){float s=(notice.created()-now)/(float)Math.max(1,delay/4),f=1-s;
                float x=host.width-5-8*s-f*f*f*halfWidth*3,py=y-2-8*s;int opacity=(int)((.5-(255-s*240)/511)*255);
                try(var batch=atlas.begin(graphics)){batch.glyph("@bonus"+((mc.player.tickCount+i*3)%16),x,py,16*s,Math.max(0,Math.min(255,opacity))<<24|0xffffff);}
            }
        }
    }
    List<String> aspectTooltip(String tag){var tooltip=tooltips.get(tag);return tooltip==null?List.of():tooltip.lines();}
    private static void tooltip(GuiGraphics graphics,List<String> lines,int x,int y){ResearchTableTooltip.draw(graphics,lines,x,y);}
    @Override public boolean mouseClicked(double mouseX,double mouseY,int button){
        if(button==0)primaryDown=true;
        double x=mouseX-left(),y=mouseY-top();
        if(net.minecraft.Util.getMillis()<combineUntil)return false;
        if(inside(x,y,35,139,32,16)&&select1!=null&&select2!=null){combine(select1,select2,true);return true;}
        if(page>0&&inside(x,y,27,121,24,8)){scroll(-1);return true;}
        if(page<lastPage(state.palette().size())&&inside(x,y,51,121,24,8)){scroll(1);return true;}
        if(select1!=null&&inside(x,y,11,137,16,16)){select1=null;sound(Sound.ASPECT);return true;}
        if(select2!=null&&inside(x,y,71,137,16,16)){select2=null;sound(Sound.ASPECT);return true;}
        if(state.duplicate()&&state.sheet()!=null&&state.sheet().complete()&&inside(x,y,37,5,24,24)){context.clickMenuButton(5);sound(Sound.BUTTON);return true;}
        int index=paletteIndex(x,y,page,state.palette().size());
        if(index>=0){var pool=state.palette().get(index);
            if(Screen.hasShiftDown()&&state.expert()){
                var aspect=aspects.get(pool.tag());if(aspect!=null&&aspect.components().size()==2)combine(aspect.components().get(0),aspect.components().get(1),false);
            }
            if(button==0&&pool.available()){dragged=pressed=pool.tag();sound(Sound.ASPECT);}
            return true;
        }
        var cell=cellAt(x,y);if(cell!=null){if(cell.type()==2)place(cell,null);return true;}
        return false;
    }
    @Override public boolean mouseReleased(double mouseX,double mouseY,int button){
        if(button==0)primaryDown=false;
        if(button!=0||dragged==null)return false;
        String tag=dragged;dragged=null;double x=mouseX-left(),y=mouseY-top();
        var cell=cellAt(x,y);
        if(cell!=null&&cell.type()==0&&!state.sheet().complete())place(cell,tag);
        else if(inside(x,y,4,130,32,32)){select1=tag;sound(Sound.ASPECT);}
        else if(inside(x,y,63,130,32,32)){select2=tag;sound(Sound.ASPECT);}
        else {int index=paletteIndex(x,y,page,state.palette().size());if(index>=0&&state.palette().get(index).tag().equals(pressed)){
            if(select1==null)select1=tag;else if(select2==null)select2=tag;
        }}
        pressed=null;return true;
    }
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){return dragged!=null;}
    @Override public boolean mouseScrolled(double x,double y,double delta){return false;}
    private void scroll(int direction){int next=Math.max(0,Math.min(lastPage(state.palette().size()),page+direction));if(page!=next){page=next;sound(Sound.PAGE);}}
    private void combine(String first,String second,boolean button){
        long now=net.minecraft.Util.getMillis();if(first==null||second==null||!state.available(first)||!state.available(second)||now<combineUntil)return;
        context.withScreen(reference->{var runtime=context.runtime();var gui=runtime.accessor(Gui.class,reference);var tile=runtime.accessor(Table.class,gui.tile());
            var lookup=runtime.accessor(TcAspectAccessors.AspectLookup.class);
            var packet=runtime.accessor(Combination.class).create(gui.player(),tile.x(),tile.y(),tile.z(),lookup.find(first),lookup.find(second),false,false,true);
            context.sendToServer(runtime.accessor(Network.class).channel(),packet);return null;
        });
        if(button){combineUntil=now+200;sound(Sound.BUTTON);}sound(Sound.COMBINE);
    }
    private void place(ResearchTableState.Cell cell,String tag){
        context.withScreen(reference->{var runtime=context.runtime();var gui=runtime.accessor(Gui.class,reference);var tile=runtime.accessor(Table.class,gui.tile());
            var aspect=tag==null?null:runtime.accessor(TcAspectAccessors.AspectLookup.class).find(tag);
            var packet=runtime.accessor(Placement.class).create(gui.player(),(byte)cell.q(),(byte)cell.r(),tile.x(),tile.y(),tile.z(),aspect);
            context.sendToServer(runtime.accessor(Network.class).channel(),packet);return null;
        });
        sound(Sound.COMBINE);sound(tag==null?Sound.ERASE:Sound.WRITE);
    }
    private void sound(Sound sound){context.withScreen(reference->{var sounds=context.runtime().accessor(Sounds.class,reference);
        switch(sound){case BUTTON->sounds.button();case ASPECT->sounds.aspect();case COMBINE->sounds.combine();case WRITE->sounds.write();case ERASE->sounds.erase();case PAGE->sounds.page();}return null;});}
    @Override public void close(){if(!closed){closed=true;primaryDown=false;dragged=pressed=null;OPEN.remove(host,this);atlas.close();}}
}
