package github.com.gengyoubo.tcmodern.tc4.client.research;

import github.com.gengyoubo.mfcl.api.*;
import github.com.gengyoubo.mfcl.api.mla.*;
import github.com.gengyoubo.tcmodern.api.TcModernApi;
import github.com.gengyoubo.tcmodern.api.aspect.*;
import github.com.gengyoubo.tcmodern.api.research.TcResearch;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import java.util.*;
import java.util.concurrent.*;
import static github.com.gengyoubo.tcmodern.tc4.client.research.ResearchTableAccessors.*;

/** Disposable native-GUI acceptance. Uses host input, real original packets and public MFCL accessors only. */
public final class NativeResearchSmokeProbe {
    @LegacyType("net.minecraft.world.World") public interface World {
        @LegacyMethod(name="func_147438_o",descriptor="(III)Lnet/minecraft/tileentity/TileEntity;") LegacyObject tile(int x,int y,int z);
    }
    @LegacyType("thaumcraft.common.tiles.TileResearchTable") public interface Inventory {
        @LegacyMethod(name="func_70299_a",descriptor="(ILnet/minecraft/item/ItemStack;)V") void set(int slot,LegacyObject stack);
        @LegacyMethod(name="func_70296_d",descriptor="()V") void changed();
    }
    @LegacyType("thaumcraft.common.lib.research.ResearchManager") public interface NoteFactory {
        @LegacyMethod(name="createNote",descriptor="(Lnet/minecraft/item/ItemStack;Ljava/lang/String;Lnet/minecraft/world/World;)Lnet/minecraft/item/ItemStack;",isStatic=true) LegacyObject create(LegacyObject stack,String key,LegacyObject world);
        @LegacyMethod(name="updateData",descriptor="(Lnet/minecraft/item/ItemStack;Lthaumcraft/common/lib/research/ResearchNoteData;)V",isStatic=true) void update(LegacyObject stack,LegacyObject data);
    }
    @LegacyType("thaumcraft.common.lib.research.ResearchNoteData") public interface WritableNote {
        @LegacyField(name="complete",descriptor="Z") void complete(boolean complete);
    }
    @LegacyType("net.minecraft.item.ItemStack") public interface Stack {
        @LegacyMethod(name="func_77964_b",descriptor="(I)V") void damage(int damage);
    }
    @LegacyType("thaumcraft.client.gui.GuiResearchTable") public interface DrawStamp {
        @LegacyField(name="lastRuneCheck",descriptor="J") long value();
    }
    private final Map<String,Object> proof=new LinkedHashMap<>();
    private final long deadline=System.nanoTime()+TimeUnit.MINUTES.toNanos(7);
    private CompletableFuture<?> pending;
    private String worldName;
    private BlockPos table;
    private int stage,frames,lastStage=-1;
    private int air,fire,light;
    private final Map<String,Integer> expectedAfterCopy=new LinkedHashMap<>();
    private ResearchTableState.Cell blank,anchor;
    private ResearchTableGui expired;
    private AbstractContainerScreen<?> closingHost;
    private LegacyObject borrowed;
    private AutoCloseable registration;
    private NativeResearchSmokeProbe(AutoCloseable registration){this.registration=registration;}
    static void install(AutoCloseable registration){MinecraftForge.EVENT_BUS.addListener(new NativeResearchSmokeProbe(registration)::tick);}
    private static void require(boolean ok,String message){if(!ok)throw new IllegalStateException(message);}
    private LegacyRuntime api(){return TcModernApi.runtime().orElseThrow();}
    private AbstractContainerScreen<?> host(Minecraft mc){return (AbstractContainerScreen<?>)mc.screen;}
    private ResearchTableGui gui(Minecraft mc){return Objects.requireNonNull(ResearchTableGui.active(mc.screen),"Native research view was not installed");}
    private void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END||stage==99)return;var mc=Minecraft.getInstance();
        try{
            if(stage!=lastStage){lastStage=stage;frames=0;com.mojang.logging.LogUtils.getLogger().info("TC4_NATIVE_RESEARCH_STAGE {}",stage);}
            if(pending!=null&&pending.isCompletedExceptionally())pending.join();
            require(System.nanoTime()<deadline,"Native research acceptance timed out at stage "+stage);
            if(stage==0&&mc.screen instanceof TitleScreen&&mc.getOverlay()==null){
                org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());mc.options.pauseOnLostFocus=false;mc.options.renderDistance().set(2);
                worldName="tc4modern-native-research-"+System.currentTimeMillis();
                mc.createWorldOpenFlows().createFreshLevel(worldName,new LevelSettings("Native research GUI",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),new WorldOptions(42,false,false),
                        access->access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());stage=1;
            }else if(stage==1&&mc.level!=null&&mc.player!=null&&mc.screen==null){pending=server(mc,()->seed(mc));stage=18;}
            else if(stage==18&&pending.isDone()&&++frames>16){pending=server(mc,()->open(mc));stage=2;}
            else if(stage==2&&pending.isDone()&&ResearchTableGui.active(mc.screen)!=null&&++frames>12){
                require(api().supports(LegacyFeatures.CLIENT_SCREENS),"Native GUI feature missing");
                require(gui(mc).state().palette().size()>25,"Fixture did not discover enough aspects to test paging");
                clickSlot(mc,29);clickSlot(mc,0);clickSlot(mc,30);clickSlot(mc,1);stage=3;
            }else if(stage==3&&++frames>14){
                var view=gui(mc);require(view.state().sheet()!=null,"Native slot insertion did not update the sheet");require(host(mc).getMenu().getCarried().isEmpty(),"Native slots lost their carried stack");
                proof.put("nativeMenuSlotsAndNoteSnapshot",true);
                blank=view.state().sheet().cells().stream().filter(c->c.type()==0).findFirst().orElseThrow();
                anchor=view.state().sheet().cells().stream().filter(c->c.type()==1).findFirst().orElseThrow();
                host(mc).mouseScrolled(host(mc).getGuiLeft()+20,host(mc).getGuiTop()+60,-1);require(view.page()==0,"Mouse wheel added non-original palette paging");
                click(mc,63,125,0);require(view.page()==1,"Original next-page button did not advance one column");
                click(mc,39,125,0);require(view.page()==0,"Original previous-page button did not return");
                proof.put("originalPaletteButtonsAndNoAddedWheelPaging",true);screenshot(mc,"note");benchmark(mc);
                var originalText=api().accessor(AspectText.class,api().accessor(TcAspectAccessors.AspectLookup.class).find("aer"));
                require(view.aspectTooltip("aer").equals(List.of(originalText.name(),originalText.description())),"Native tooltip differs from the original name and description");
                require(view.aspectTooltip("aer").size()==2,"Native tooltip added amounts, bonuses or shortcut instructions");
                proof.put("aspectTooltipExactlyOriginalTwoLines",true);
                int[] airPoint=palettePoint(mc,"aer");screenshotAt(mc,"hover-aer",airPoint[0],airPoint[1]);
                int[] lightPoint=palettePoint(mc,"lux");screenshotAt(mc,"hover-lux",lightPoint[0],lightPoint[1]);
                require(view.context.withScreen(ref->api().accessor(DrawStamp.class,ref).value())==0,"Native view executed the old draw method");proof.put("noLegacyDrawCaptureOrReplay",true);
                view.context.withScreen(ref->{borrowed=api().accessor(Gui.class,ref).tile();return null;});
                boolean rejected=false;try{api().accessor(Table.class,borrowed).x();}catch(RuntimeException expected){rejected=true;}require(rejected,"Borrowed GUI object escaped its scope");proof.put("borrowedReferencesExpire",true);
                referenceScreenshots(mc);view=gui(mc);
                clickAspect(mc,"aer");clickAspect(mc,"ignis");proof.put("combinationSelection",view.selection());
                require(view.selection().equals(List.of("aer","ignis")),"Native palette clicks selected the wrong aspects: "+view.selection());
                proof.put("combinationBefore",List.of(view.state().pools().get("aer").amount(),view.state().pools().get("ignis").amount(),view.state().pools().get("lux").amount()));
                air=view.state().pools().get("aer").amount();fire=view.state().pools().get("ignis").amount();light=view.state().pools().get("lux").amount();
                click(mc,51,147,0);click(mc,51,147,0);stage=4;
            }else if(stage==4&&++frames>14){
                proof.put("combinationAfter",List.of(gui(mc).state().pools().get("aer").amount(),gui(mc).state().pools().get("ignis").amount(),gui(mc).state().pools().get("lux").amount()));
                require(gui(mc).state().pools().get("aer").amount()==air-1&&gui(mc).state().pools().get("ignis").amount()==fire-1&&gui(mc).state().pools().get("lux").amount()==light+1,"Original combination did not synchronize: "+proof.get("combinationAfter"));
                air--;fire--;light++;pending=server(mc,()->verifyPools(mc,air,fire,light));proof.put("nativeCombinationAndClientSync",true);proof.put("rapidCombineClicksApplyExactlyOnce",true);stage=5;
            }else if(stage==5&&pending.isDone()){
                int[] point=palettePoint(mc,"aer");var screen=host(mc);screen.mouseClicked(point[0],point[1],0);
                screen.mouseDragged(screen.getGuiLeft()+blank.x(),screen.getGuiTop()+blank.y(),0,40,0);
                screen.mouseReleased(screen.getGuiLeft()+blank.x(),screen.getGuiTop()+blank.y(),0);stage=6;
            }else if(stage==6&&++frames>14){
                require(cell(mc,blank).type()==2,"Native aspect drop did not update the cell");
                int after=gui(mc).state().pools().get("aer").amount();require(after==air-1||after==air,"Placement changed more than the original 0..1 points");
                proof.put("originalPlacementCostIncludingResearcherChance",air-after);air=after;
                pending=server(mc,()->{verifyPools(mc,air,fire,light);verifyCell(mc,blank,2,1);});stage=7;
            }else if(stage==7&&pending.isDone()){click(mc,blank.x(),blank.y(),1);stage=8;}
            else if(stage==8&&++frames>14){
                require(cell(mc,blank).type()==0,"Native erase did not synchronize");
                int after=gui(mc).state().pools().get("aer").amount();require(after==air||after==air+1,"Erase changed more than the original 0..1 point refund");
                proof.put("originalEraseRefundIncludingResearcherChance",after-air);air=after;
                pending=server(mc,()->{verifyPools(mc,air,fire,light);verifyCell(mc,blank,0,2);});proof.put("nativeDragPlaceEraseInkAndServerAuthority",true);stage=9;
            }else if(stage==9&&pending.isDone()){
                int[] point=palettePoint(mc,"aer");host(mc).mouseClicked(point[0],point[1],0);host(mc).mouseReleased(-20,-20,0);click(mc,anchor.x(),anchor.y(),1);stage=10;
            }else if(stage==10&&++frames>12){
                pending=server(mc,()->{verifyPools(mc,air,fire,light);verifyCell(mc,blank,0,2);completeSheet(mc);});proof.put("cancelledDragAndFixedAnchorConsumeNothing",true);stage=11;
            }else if(stage==11&&pending.isDone()&&++frames>14){
                require(gui(mc).state().sheet().complete()&&gui(mc).state().duplicate(),"Complete note or original duplication unlock was not reflected");
                var snapshot=gui(mc).state();snapshot.sheet().copyCost().forEach((tag,cost)->expectedAfterCopy.put(tag,snapshot.pools().get(tag).amount()-cost));
                click(mc,49,17,0);stage=12;
            }else if(stage==12&&++frames>16){
                require(host(mc).getMenu().getSlot(1).getItem().getCount()==2,"Native copy button did not duplicate the note");
                pending=server(mc,()->verifyDuplicate(mc));proof.put("nativeCopyButtonUsesOriginalCosts",true);screenshot(mc,"copied");
                expired=gui(mc);host(mc).resize(mc,host(mc).width,host(mc).height);require(gui(mc)!=expired,"Resizing did not recreate the native view");
                boolean rejected=false;try{expired.context.withScreen(ref->null);}catch(RuntimeException expected){rejected=true;}require(rejected,"Old view context survived a resize");proof.put("resizeReleasesViewAndExpiresContext",true);stage=13;
            }else if(stage==13&&pending.isDone()&&++frames>12){
                int[] point=palettePoint(mc,"aer");closingHost=host(mc);closingHost.mouseClicked(point[0],point[1],0);mc.screen.onClose();stage=14;
            }else if(stage==14&&++frames>12){
                require(ResearchTableGui.active(closingHost)==null,"Closed view was retained");
                // Server menu close and its authoritative slot export have completed before saving.
                mc.level.disconnect();mc.clearLevel();mc.createWorldOpenFlows().loadLevel(new TitleScreen(),worldName);stage=15;
            }else if(stage==15&&mc.level!=null&&mc.player!=null&&mc.screen==null){pending=server(mc,()->{verifyDuplicate(mc);open(mc);});stage=16;}
            else if(stage==16&&pending.isDone()&&ResearchTableGui.active(mc.screen)!=null&&++frames>14){
                require(gui(mc).state().sheet()!=null&&gui(mc).state().sheet().complete()&&host(mc).getMenu().getSlot(1).getItem().getCount()==2,"Native view did not restore the saved original sheet");
                proof.put("closeDuringDragAndSaveReloadPreserveNoteAndCosts",true);screenshot(mc,"reloaded");finish(mc,null);
            }
        }catch(Throwable failure){finish(mc,failure);}
    }
    private LegacyObject world(net.minecraft.server.level.ServerLevel level){return api().mappings().toLegacy(LegacyMappings.WORLD,level).orElseThrow();}
    private LegacyObject tile(net.minecraft.server.level.ServerLevel level){return api().accessor(World.class,world(level)).tile(table.getX(),table.getY(),table.getZ());}
    private void seed(Minecraft mc){
        var player=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);var level=player.serverLevel();table=player.blockPosition().east(2);
        if(mc.getSingleplayerServer().getWorldData() instanceof net.minecraft.world.level.storage.PrimaryLevelData data)data.withConfirmedWarning(true);
        level.setBlockAndUpdate(table,api().hostBlock("Thaumcraft:blockTable",5,BlockState.class).orElseThrow());
        level.setBlockAndUpdate(table.east(),api().hostBlock("Thaumcraft:blockTable",8,BlockState.class).orElseThrow());
        require(api().values().isInstance(tile(level),"thaumcraft.common.tiles.TileResearchTable"),"Fixture has the wrong original tile subtype");
        var amounts=new LinkedHashMap<String,Integer>();for(var aspect:TcAspects.snapshot(api()))amounts.put(aspect.tag(),100);TcPlayerAspects.give(api(),player,amounts);
        TcResearch.complete(api(),player,"RESEARCHER2");TcResearch.complete(api(),player,"RESEARCHDUPE");
        player.getInventory().clearContent();player.getInventory().selected=8;
        api().withPlayer(player,actor->{
            var note=api().accessor(NoteFactory.class).create(api().stack(new LegacyStack("Thaumcraft:ItemResearchNotes",0,1,"")),"NITOR",world(level));
            player.getInventory().setItem(0,api().mappings().toHost(LegacyMappings.ITEM_STACK,api().stack(new LegacyStack("Thaumcraft:ItemInkwell",0,1,"")),ItemStack.class).orElseThrow());
            player.getInventory().setItem(1,api().mappings().toHost(LegacyMappings.ITEM_STACK,note,ItemStack.class).orElseThrow());return null;
        });
        player.getInventory().setItem(2,new ItemStack(Items.PAPER,8));player.getInventory().setItem(3,new ItemStack(Items.INK_SAC,8));
    }
    private void open(Minecraft mc){
        var player=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);var level=player.serverLevel();
        level.getBlockState(table).use(level,player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(table),Direction.UP,table,false));
        require(player.containerMenu!=player.inventoryMenu,"Original research menu did not open");
    }
    private void verifyPools(Minecraft mc,int air,int fire,int light){
        var player=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);
        api().withPlayer(player,actor->{
            var knowledge=api().accessor(TcAspectAccessors.Knowledge.class,api().accessor(TcAspectAccessors.Proxy.class,api().accessor(TcAspectAccessors.Thaumcraft.class).proxy()).knowledge());
            var lookup=api().accessor(TcAspectAccessors.AspectLookup.class);String name=player.getName().getString();
            require(knowledge.pool(name,lookup.find("aer"))==air&&knowledge.pool(name,lookup.find("ignis"))==fire&&knowledge.pool(name,lookup.find("lux"))==light,"Server aspect consumption differs from client");return null;
        });
    }
    private void verifyCell(Minecraft mc,ResearchTableState.Cell cell,int type,int ink){
        var player=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);
        api().withPlayer(player,actor->{var inventory=api().accessor(Table.class,tile(player.serverLevel()));
            var data=api().accessor(Note.class,api().accessor(Research.class).note(inventory.stack(1)));
            var entry=(LegacyObject)api().values().map(data.entries()).get(cell.key());
            require(api().accessor(Entry.class,entry).type()==type&&api().snapshot(inventory.stack(0)).metadata()==ink,"Server research cell or ink differs");return null;
        });
    }
    private void completeSheet(Minecraft mc){
        var player=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);
        api().withPlayer(player,actor->{var value=tile(player.serverLevel());var table=api().accessor(Table.class,value);var note=table.stack(1);var data=api().accessor(Research.class).note(note);
            api().accessor(WritableNote.class,data).complete(true);api().accessor(NoteFactory.class).update(note,data);api().accessor(Stack.class,note).damage(64);api().accessor(Inventory.class,value).changed();return null;
        });
    }
    private void verifyDuplicate(Minecraft mc){
        var player=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);
        api().withPlayer(player,actor->{var inventory=api().accessor(Table.class,tile(player.serverLevel()));var note=inventory.stack(1);var data=api().accessor(Note.class,api().accessor(Research.class).note(note));
            require(api().snapshot(note).count()==2&&data.copies()==1,"Original note copies were not preserved");
            require(player.getInventory().countItem(Items.PAPER)==7&&player.getInventory().countItem(Items.INK_SAC)==7,"Original copy did not consume paper/ink exactly once");
            var knowledge=api().accessor(TcAspectAccessors.Knowledge.class,api().accessor(TcAspectAccessors.Proxy.class,api().accessor(TcAspectAccessors.Thaumcraft.class).proxy()).knowledge());
            var lookup=api().accessor(TcAspectAccessors.AspectLookup.class);
            expectedAfterCopy.forEach((tag,total)->require(knowledge.pool(player.getName().getString(),lookup.find(tag))==total,"Original copy aspect cost differs for "+tag));return null;
        });
    }
    private ResearchTableState.Cell cell(Minecraft mc,ResearchTableState.Cell key){return gui(mc).state().sheet().cells().stream().filter(c->c.q()==key.q()&&c.r()==key.r()).findFirst().orElseThrow();}
    private void clickSlot(Minecraft mc,int index){var slot=host(mc).getMenu().getSlot(index);click(mc,slot.x+8,slot.y+8,0);}
    private void click(Minecraft mc,double x,double y,int button){var screen=host(mc);screen.mouseClicked(screen.getGuiLeft()+x,screen.getGuiTop()+y,button);screen.mouseReleased(screen.getGuiLeft()+x,screen.getGuiTop()+y,button);}
    private int[] palettePoint(Minecraft mc,String tag){
        var view=gui(mc);int index=0;while(index<view.state().palette().size()&&!view.state().palette().get(index).tag().equals(tag))index++;
        require(index<view.state().palette().size(),"No known aspect "+tag);
        while(index<view.page()*5)click(mc,39,125,0);
        while(index>=view.page()*5+25)click(mc,63,125,0);
        int offset=index-view.page()*5;return new int[]{host(mc).getGuiLeft()+18+offset/5*16,host(mc).getGuiTop()+48+offset%5*16};
    }
    private void clickAspect(Minecraft mc,String tag){int[] point=palettePoint(mc,tag);host(mc).mouseClicked(point[0],point[1],0);host(mc).mouseReleased(point[0],point[1],0);}
    private void benchmark(Minecraft mc)throws Exception{
        var view=gui(mc);var graphics=new GuiGraphics(mc,mc.renderBuffers().bufferSource());long[] samples=new long[100];
        try(var target=guiTarget(mc)){
        for(int i=-30;i<samples.length;i++){
            graphics.flush();com.mojang.blaze3d.systems.RenderSystem.clear(256,Minecraft.ON_OSX);graphics.fill(0,0,host(mc).width,host(mc).height,0xff404040);graphics.flush();
            long start=System.nanoTime();view.renderBackground(graphics,host(mc).getGuiLeft(),host(mc).getGuiTop(),0,0,0);graphics.flush();if(i>=0)samples[i]=System.nanoTime()-start;
        }
        }
        Arrays.sort(samples);proof.put("nativeRenderCpuMedianMs",samples[50]/1_000_000.0);proof.put("nativeRenderCpuP95Ms",samples[95]/1_000_000.0);
        proof.put("visibleAspects",Math.min(25,view.state().palette().size()));proof.put("researchCells",view.state().sheet().cells().size());
    }
    private void screenshot(Minecraft mc,String name)throws Exception{
        screenshotAt(mc,name,0,0);
    }
    private void referenceScreenshots(Minecraft mc)throws Exception{
        int[] air=palettePoint(mc,"aer"),light=palettePoint(mc,"lux");var screen=host(mc);
        registration.close();screen.resize(mc,screen.width,screen.height);
        require(ResearchTableGui.active(screen)==null,"Reference rendering retained the native view");
        screenshotAt(mc,"original-hover-aer",air[0],air[1]);screenshotAt(mc,"original-hover-lux",light[0],light[1]);
        registration=ResearchTableGui.install(api());screen.resize(mc,screen.width,screen.height);
        require(ResearchTableGui.active(screen)!=null,"Native view was not restored after reference capture");
        proof.put("originalReferenceScreenshotsCaptured",true);
    }
    private void screenshotAt(Minecraft mc,String name,int mouseX,int mouseY)throws Exception{
        try(var target=guiTarget(mc)){
        var graphics=new GuiGraphics(mc,mc.renderBuffers().bufferSource());net.minecraftforge.client.ForgeHooksClient.drawScreen(mc.screen,graphics,mouseX,mouseY,0);graphics.flush();
        var path=mc.gameDirectory.toPath().resolve("screenshots/native-research-"+name+".png");java.nio.file.Files.createDirectories(path.getParent());
        try(var image=net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(path);}
        }
    }
    private static AutoCloseable guiTarget(Minecraft mc){
        mc.getMainRenderTarget().bindWrite(true);
        com.mojang.blaze3d.systems.RenderSystem.backupProjectionMatrix();
        com.mojang.blaze3d.systems.RenderSystem.setProjectionMatrix(new org.joml.Matrix4f().setOrtho(0,mc.getWindow().getGuiScaledWidth(),mc.getWindow().getGuiScaledHeight(),0,1000,3000),com.mojang.blaze3d.vertex.VertexSorting.ORTHOGRAPHIC_Z);
        var modelView=com.mojang.blaze3d.systems.RenderSystem.getModelViewStack();modelView.pushPose();modelView.setIdentity();modelView.translate(0,0,-2000);com.mojang.blaze3d.systems.RenderSystem.applyModelViewMatrix();
        com.mojang.blaze3d.systems.RenderSystem.clear(256,Minecraft.ON_OSX);
        var background=new GuiGraphics(mc,mc.renderBuffers().bufferSource());background.fill(0,0,mc.getWindow().getGuiScaledWidth(),mc.getWindow().getGuiScaledHeight(),0xff303030);background.flush();
        return ()->{modelView.popPose();com.mojang.blaze3d.systems.RenderSystem.applyModelViewMatrix();com.mojang.blaze3d.systems.RenderSystem.restoreProjectionMatrix();mc.getMainRenderTarget().unbindWrite();};
    }
    private CompletableFuture<?> server(Minecraft mc,Runnable action){return mc.getSingleplayerServer().submit(action);}
    private void finish(Minecraft mc,Throwable failure){
        if(failure!=null){proof.put("status","FAIL");proof.put("stage",stage);proof.put("failure",failure.toString());com.mojang.logging.LogUtils.getLogger().error("TC4_NATIVE_RESEARCH_FAIL",failure);}else proof.put("status","PASS");stage=99;
        try{var report=mc.gameDirectory.toPath().resolve("logs/tc4modern/native-research-smoke.json");java.nio.file.Files.createDirectories(report.getParent());java.nio.file.Files.writeString(report,new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(proof));}catch(Exception error){throw new IllegalStateException(error);}
        com.mojang.logging.LogUtils.getLogger().info("TC4_NATIVE_RESEARCH_RESULT {}",proof);if(mc.level!=null){mc.level.disconnect();mc.clearLevel();}mc.stop();
    }
}
