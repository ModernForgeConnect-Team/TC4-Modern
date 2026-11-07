package github.com.gengyoubo.tcmodern.tc4.client.testing;
import github.com.gengyoubo.tcmodern.tc4.init.Tc4Blocks;
import github.com.gengyoubo.tcmodern.tc4.content.GiantSaplingEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import java.nio.file.Files;
@Mod.EventBusSubscriber(modid="tc4modern",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
public final class Integrated80Probe {
 private final boolean silver=!Boolean.getBoolean("tc4modern.integrated80Greatwood"); private volatile ServerLevel level; private volatile boolean done; private int cs,ticks; private boolean reloaded; private long treeSeed; private int treeHeight;
 private final BlockPos pos=new BlockPos(4,239,4);
 @SubscribeEvent public static void setup(FMLClientSetupEvent e){if(Boolean.getBoolean("tc4modern.integrated80"))e.enqueueWork(()->{var p=new Integrated80Probe();MinecraftForge.EVENT_BUS.addListener(p::client);MinecraftForge.EVENT_BUS.addListener(p::server);});}
 private void record(String text){try{var f=Minecraft.getInstance().gameDirectory.toPath().resolve("logs/tc4modern/integrated80-greatwood-result.txt");Files.createDirectories(f.getParent());Files.writeString(f,text);}catch(Exception ex){throw new RuntimeException(ex);}}
 private void server(TickEvent.ServerTickEvent e){if(e.phase!=TickEvent.Phase.END||level==null||done)return;try{
  ticks++;var b=level.getBlockState(pos);var sapling=silver?Tc4Blocks.ENCHANTED_SACRED_SILVERWOOD_SAPLING.get():Tc4Blocks.ENCHANTED_SACRED_GREATWOOD_SAPLING.get();
  if(ticks==1){level.setBlock(pos.below(),Blocks.DIRT.defaultBlockState(),3);level.setBlock(pos,sapling.defaultBlockState(),3);var be=(GiantSaplingEntity)level.getBlockEntity(pos);be.start();var saved=be.saveWithoutMetadata();if(!saved.getBoolean("TickTreeActive"))throw new IllegalStateException("job not started");treeSeed=saved.getCompound("TickTree").getLong("Seed");treeHeight=saved.getCompound("TickTree").getInt("Height");}
  else if(ticks==100){var be=(GiantSaplingEntity)level.getBlockEntity(pos);var saved=be.saveWithoutMetadata();if(!saved.getBoolean("TickTreeActive"))throw new IllegalStateException("job not active before reload");level.removeBlockEntity(pos);var replacement=(GiantSaplingEntity)((net.minecraft.world.level.block.EntityBlock)sapling).newBlockEntity(pos,b);replacement.load(saved);level.setBlockEntity(replacement);reloaded=true;}
  else if(ticks>100&&!b.is(sapling)){
    if(!reloaded)throw new IllegalStateException("reload missing");int leaves=0;var rt=github.com.gengyoubo.tcmodern.api.TcModernApi.runtime().orElseThrow();var leaf=rt.hostBlock("Thaumcraft:blockMagicalLeaves",silver?1:0,net.minecraft.world.level.block.state.BlockState.class).orElseThrow();var rng=new java.util.Random(9);
    for(int i=0;i<100000;i++){var p=pos.offset(rng.nextInt(225)-112,rng.nextInt(80),rng.nextInt(225)-112);if(level.getBlockState(p).getBlock()==leaf.getBlock())leaves++;}
    if(leaves<20)throw new IllegalStateException("too few sampled leaves "+leaves);
    int selected=silver?GiantSaplingEntity.randomNodeCount(treeSeed,80):0,found=0;
    var knot=rt.hostBlock("Thaumcraft:blockMagicalLog",2,net.minecraft.world.level.block.state.BlockState.class).orElseThrow();
    for(int i=0;i<selected;i++) {var np=GiantSaplingEntity.randomNodePosition(pos,treeHeight,11,true,treeSeed,i);if(level.getBlockState(np)==knot)found++;}
    if(found!=selected)throw new IllegalStateException("silverwood knots: "+found+"/"+selected);
    record("selected_nodes="+selected+"\nfound_knots="+found+"\ncompleted=true\nreloaded_block_entity="+reloaded+"\nticks="+ticks+"\nroot="+b+"\nsampled_leaves="+leaves+"\n");done=true;
  }else if(ticks>2000)throw new IllegalStateException("timed out");
 }catch(Throwable x){x.printStackTrace();record("error="+x+"\nticks="+ticks+"\n");done=true;}}
 private void client(TickEvent.ClientTickEvent e){if(e.phase!=TickEvent.Phase.END||cs==99)return;var mc=Minecraft.getInstance();try{
  if(cs==0&&mc.screen instanceof TitleScreen&&mc.getOverlay()==null){org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());var d=mc.gameDirectory.toPath().resolve("logs/tc4modern");Files.createDirectories(d);Files.writeString(d.resolve("integrated80-pid.txt"),Long.toString(ProcessHandle.current().pid()));mc.options.pauseOnLostFocus=false;mc.options.renderDistance().set(3);mc.createWorldOpenFlows().createFreshLevel("tc4modern-integrated80-"+System.currentTimeMillis(),new LevelSettings("Integrated 80",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),new WorldOptions(42,false,false),a->a.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());cs=1;}
  else if(cs==1&&mc.level!=null&&mc.player!=null&&mc.screen==null){var server=mc.getSingleplayerServer();server.submit(()->{var l=server.overworld();l.noSave=true;l.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(0,server);level=l;});cs=2;}
  else if(cs==2&&done){cs=99;mc.stop();}
 }catch(Throwable x){x.printStackTrace();cs=99;mc.stop();}}
}
