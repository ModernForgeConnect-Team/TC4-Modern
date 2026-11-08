package github.com.gengyoubo.tcmodern.tc4.client.testing;
import github.com.gengyoubo.tcmodern.tc4.init.Tc4Blocks;
import github.com.gengyoubo.tcmodern.tc4.content.GiantSaplingEntity;
import github.com.gengyoubo.tcmodern.tc4.content.MagicalLeavesBlock;
import github.com.gengyoubo.mfcl.api.LegacyMappings;
import net.minecraft.world.level.block.LeavesBlock;
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
 private final boolean silver=!Boolean.getBoolean("tc4modern.integrated80Greatwood"); private volatile ServerLevel level; private volatile boolean done; private boolean largeSaplingsStarted; private int cs,ticks; private boolean reloaded; private long treeSeed; private int treeHeight;
 private final BlockPos pos=new BlockPos(4,239,4);
 private int largeCandidatesBefore;
 @SubscribeEvent public static void setup(FMLClientSetupEvent e){if(Boolean.getBoolean("tc4modern.integrated80"))e.enqueueWork(()->{var p=new Integrated80Probe();MinecraftForge.EVENT_BUS.addListener(p::client);MinecraftForge.EVENT_BUS.addListener(p::server);});}
 private void record(String text){try{var f=Minecraft.getInstance().gameDirectory.toPath().resolve("logs/tc4modern/integrated80-greatwood-result.txt");Files.createDirectories(f.getParent());Files.writeString(f,text);}catch(Exception ex){throw new RuntimeException(ex);}}
 private void server(TickEvent.ServerTickEvent e){if(e.phase!=TickEvent.Phase.END||level==null||done)return;try{
  if(Boolean.getBoolean("tc4modern.integrated250")) {
   ticks++;var p=new BlockPos(4,64,4);var sapling=Tc4Blocks.ENCHANTED_SACRED_GREATWOOD_SAPLING.get();
   if(ticks==1) {
    level.setBlock(p.below(),Blocks.DIRT.defaultBlockState(),3);level.setBlock(p,sapling.defaultBlockState(),3);
    var player=net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(level);var bone=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BONE_MEAL,64);
    for(int n=0;n<64&&!((GiantSaplingEntity)level.getBlockEntity(p)).isTreeGenerating();n++) net.minecraft.world.item.BoneMealItem.applyBonemeal(bone,level,p,player);
    if(!((GiantSaplingEntity)level.getBlockEntity(p)).isTreeGenerating())throw new IllegalStateException("250-block bone meal did not start tree");
   } else if(ticks==100) {
    largeCandidatesBefore=((GiantSaplingEntity)level.getBlockEntity(p)).saveWithoutMetadata().getCompound("TickTree").getInt("CandidateCount");
   } else if(ticks==350) {
    var saved=((GiantSaplingEntity)level.getBlockEntity(p)).saveWithoutMetadata().getCompound("TickTree");
    int count=saved.getInt("CandidateCount");if(count<=largeCandidatesBefore)throw new IllegalStateException("250-block candidate selection stalled: "+largeCandidatesBefore+" -> "+count);
    record("250_progress=true\ncandidates_at_100="+largeCandidatesBefore+"\ncandidates_at_350="+count+"\nphase="+saved.getInt("Phase")+"\n");done=true;
   }
   return;
  }
  ticks++;var b=level.getBlockState(pos);var sapling=silver?Tc4Blocks.ENCHANTED_SACRED_SILVERWOOD_SAPLING.get():Tc4Blocks.ENCHANTED_SACRED_GREATWOOD_SAPLING.get();
  if(ticks==1){
   for(int n=0;n<2;n++) {
    var largePos=new BlockPos(40+n*4,64,4);
    var largeSapling=n==0?Tc4Blocks.ENCHANTED_SACRED_GREATWOOD_SAPLING.get():Tc4Blocks.ENCHANTED_SACRED_SILVERWOOD_SAPLING.get();
    level.setBlock(largePos.below(),Blocks.DIRT.defaultBlockState(),3);
    level.setBlock(largePos,largeSapling.defaultBlockState(),3);
    var largeEntity=(GiantSaplingEntity)level.getBlockEntity(largePos);
    var player=net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(level);
    var bone=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BONE_MEAL,64);
    player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,bone);
    long start=System.nanoTime();
    for(int attempt=0;attempt<64&&!largeEntity.isTreeGenerating();attempt++)
     net.minecraft.world.item.BoneMealItem.applyBonemeal(bone,level,largePos,player);
    if(System.nanoTime()-start>2_000_000_000L)throw new IllegalStateException("large tree start blocked");
    var largeSaved=largeEntity.saveWithoutMetadata();
    if(!level.getBlockState(largePos).is(largeSapling) || !largeSaved.getBoolean("TickTreeActive") || largeSaved.getCompound("TickTree").getInt("HeightLimit")!=250)throw new IllegalStateException("250-block tree job did not start");
    GiantSaplingEntity.tick(level,largePos,level.getBlockState(largePos),largeEntity);
    if(largeEntity.saveWithoutMetadata().getCompound("TickTree").getInt("CandidateCount")<1)throw new IllegalStateException("250-block candidate selection did not progress");
    int remaining=bone.getCount();
    var hit=new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(largePos),net.minecraft.core.Direction.UP,largePos,false);
    var status=largeSapling.use(level.getBlockState(largePos),level,largePos,player,net.minecraft.world.InteractionHand.MAIN_HAND,hit);
    if(!status.consumesAction()||bone.getCount()!=remaining)throw new IllegalStateException("active tree status consumed bone meal");
    level.removeBlock(largePos,false);
   }
   largeSaplingsStarted=true;
   level.setBlock(pos.below(),Blocks.DIRT.defaultBlockState(),3);level.setBlock(pos,sapling.defaultBlockState(),3);var be=(GiantSaplingEntity)level.getBlockEntity(pos);be.start();var saved=be.saveWithoutMetadata();if(!saved.getBoolean("TickTreeActive"))throw new IllegalStateException("job not started");treeSeed=saved.getCompound("TickTree").getLong("Seed");treeHeight=saved.getCompound("TickTree").getInt("Height");}
  else if(ticks==100){var be=(GiantSaplingEntity)level.getBlockEntity(pos);var saved=be.saveWithoutMetadata();if(!saved.getBoolean("TickTreeActive"))throw new IllegalStateException("job not active before reload");level.removeBlockEntity(pos);var replacement=(GiantSaplingEntity)((net.minecraft.world.level.block.EntityBlock)sapling).newBlockEntity(pos,b);replacement.load(saved);level.setBlockEntity(replacement);reloaded=true;}
  else if(ticks>100&&!b.is(sapling)){
    if(!reloaded)throw new IllegalStateException("reload missing");int leaves=0;var rt=github.com.gengyoubo.tcmodern.api.TcModernApi.runtime().orElseThrow();var leaf=rt.hostBlock("Thaumcraft:blockMagicalLeaves",silver?1:0,net.minecraft.world.level.block.state.BlockState.class).orElseThrow();var rng=new java.util.Random(9);
    BlockPos sample=null;
    for(int i=0;i<100000;i++){
     var p=pos.offset(rng.nextInt(225)-112,rng.nextInt(80),rng.nextInt(225)-112);var state=level.getBlockState(p);
     if(state.getBlock()==leaf.getBlock()){
      if(!state.getValue(MagicalLeavesBlock.MASSIVE_STATIC)||!state.getValue(LeavesBlock.PERSISTENT)||state.getValue(LeavesBlock.DISTANCE)!=7)throw new IllegalStateException("generated leaf is not static: "+state);
      if(level.getBlockTicks().hasScheduledTick(p,state.getBlock()))throw new IllegalStateException("static leaf has a scheduled tick");
      leaves++;sample=p;
     }
    }
    if(leaves<20)throw new IllegalStateException("too few sampled leaves "+leaves);
    var world=rt.accessor(NativeLeavesSmokeProbe.OldWorld.class,rt.mappings().toLegacy(LegacyMappings.WORLD,level).orElseThrow());
    int metadata=world.metadata(sample.getX(),sample.getY(),sample.getZ());
    world.metadata(sample.getX(),sample.getY(),sample.getZ(),metadata & ~4,2);
    var preserved=level.getBlockState(sample);
    if(!preserved.getValue(MagicalLeavesBlock.MASSIVE_STATIC)||!preserved.getValue(LeavesBlock.PERSISTENT)||preserved.getValue(LeavesBlock.DISTANCE)!=7)throw new IllegalStateException("legacy metadata erased static leaf");
    var serialized=net.minecraft.nbt.NbtUtils.writeBlockState(preserved);
    var restored=net.minecraft.nbt.NbtUtils.readBlockState(level.holderLookup(Registries.BLOCK),serialized);
    if(restored!=preserved)throw new IllegalStateException("static leaf state did not survive NBT round trip");
    int selected=silver?GiantSaplingEntity.randomNodeCount(treeSeed,80):0,found=0;
    var knot=rt.hostBlock("Thaumcraft:blockMagicalLog",2,net.minecraft.world.level.block.state.BlockState.class).orElseThrow();
    for(int i=0;i<selected;i++) {var np=GiantSaplingEntity.randomNodePosition(pos,treeHeight,11,true,treeSeed,i);if(level.getBlockState(np)==knot)found++;}
    if(found>selected)throw new IllegalStateException("invalid silverwood knot count");
    record("large_both_species_started="+largeSaplingsStarted+"\nselected_nodes="+selected+"\nfound_knots="+found+"\ncompleted=true\nreloaded_block_entity="+reloaded+"\nstatic_leaf_metadata_and_nbt=true\nticks="+ticks+"\nroot="+b+"\nsampled_leaves="+leaves+"\n");done=true;
  }else if(ticks>2000)throw new IllegalStateException("timed out");
 }catch(Throwable x){x.printStackTrace();record("error="+x+"\nticks="+ticks+"\n");done=true;}}
 private void client(TickEvent.ClientTickEvent e){if(e.phase!=TickEvent.Phase.END||cs==99)return;var mc=Minecraft.getInstance();try{
  if(cs==0&&mc.screen instanceof TitleScreen&&mc.getOverlay()==null){org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());var d=mc.gameDirectory.toPath().resolve("logs/tc4modern");Files.createDirectories(d);Files.writeString(d.resolve("integrated80-pid.txt"),Long.toString(ProcessHandle.current().pid()));mc.options.pauseOnLostFocus=false;mc.options.renderDistance().set(3);mc.createWorldOpenFlows().createFreshLevel("tc4modern-integrated80-"+System.currentTimeMillis(),new LevelSettings("Integrated 80",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),new WorldOptions(42,false,false),a->a.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());cs=1;}
  else if(cs==1&&mc.level!=null&&mc.player!=null&&mc.screen==null){var server=mc.getSingleplayerServer();server.submit(()->{var l=server.overworld();l.noSave=true;l.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(0,server);level=l;});cs=2;}
  else if(cs==2&&done){cs=99;mc.stop();}
 }catch(Throwable x){x.printStackTrace();cs=99;mc.stop();}}
}
