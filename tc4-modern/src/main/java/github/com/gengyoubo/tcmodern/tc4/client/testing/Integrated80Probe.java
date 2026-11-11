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
 private final boolean silver=!Boolean.getBoolean("tc4modern.integrated80Greatwood"); private volatile ServerLevel level; private volatile boolean done; private volatile BlockPos clientSample,nodeSample; private boolean largeSaplingsStarted; private int cs,ticks,postCompleteTicks; private boolean reloaded; private long treeSeed; private int treeHeight;
 private final BlockPos pos=new BlockPos(4,239,4);
 private int largeCandidatesBefore; private long measuredBefore, nanosBefore; private String worldId; private int reopenFrames; private java.util.concurrent.CompletableFuture<Boolean> reopenedSample;
 private void checkSectionGuards(ServerLevel l) throws Exception {
  var rt=github.com.gengyoubo.tcmodern.api.TcModernApi.runtime().orElseThrow();
  var leaf=rt.hostBlock("Thaumcraft:blockMagicalLeaves",0,net.minecraft.world.level.block.state.BlockState.class).orElseThrow()
    .setValue(MagicalLeavesBlock.MASSIVE_STATIC,true).setValue(LeavesBlock.PERSISTENT,true).setValue(LeavesBlock.DISTANCE,7);
  var type=Class.forName("github.com.gengyoubo.tcmodern.tc4.world.MassiveLeafSectionBatch");
  var ctor=type.getDeclaredConstructor(ServerLevel.class,net.minecraft.world.level.block.state.BlockState.class);ctor.setAccessible(true);var writer=ctor.newInstance(l,leaf);
  var place=type.getDeclaredMethod("tryPlace",BlockPos.class);place.setAccessible(true);var flush=type.getDeclaredMethod("flush");flush.setAccessible(true);
  var at=pos.offset(0,16,0);var adjacent=at.east();
  l.setBlock(adjacent,Blocks.OAK_FENCE.defaultBlockState(),3);
  if((boolean)place.invoke(writer,at))throw new IllegalStateException("section path accepted a fence neighbour");
  l.setBlock(adjacent,Blocks.WATER.defaultBlockState(),3);
  if((boolean)place.invoke(writer,at))throw new IllegalStateException("section path accepted a water neighbour");
  l.setBlock(adjacent,Blocks.AIR.defaultBlockState(),3);l.setBlock(at,Blocks.CHEST.defaultBlockState(),3);
  if((boolean)place.invoke(writer,at)||l.getBlockEntity(at)==null)throw new IllegalStateException("section path accepted a block entity");
  l.setBlock(at,Blocks.AIR.defaultBlockState(),3);
  l.captureBlockSnapshots=true;
  try {if((boolean)place.invoke(writer,at))throw new IllegalStateException("section path accepted snapshot capture");} finally {l.captureBlockSnapshots=false;}
  if(!(boolean)place.invoke(writer,at))throw new IllegalStateException("section path rejected isolated leaf");
  flush.invoke(writer);if(l.getBlockState(at)!=leaf)throw new IllegalStateException("section path did not write leaf");
  l.setBlock(at,Blocks.AIR.defaultBlockState(),3);
 }
 @SubscribeEvent public static void setup(FMLClientSetupEvent e){if(Boolean.getBoolean("tc4modern.integrated80")){var probe=new Integrated80Probe();MinecraftForge.EVENT_BUS.addListener(probe::server);MinecraftForge.EVENT_BUS.addListener(probe::client);}}
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
    var current=(GiantSaplingEntity)level.getBlockEntity(p);var saved=current.saveWithoutMetadata();var job=saved.getCompound("TickTree");
    largeCandidatesBefore=job.getInt("CandidateCount");var metrics=job.getCompound("Metrics");
    measuredBefore=metrics.getLong("MeasuredSteps");nanosBefore=metrics.getLong("TotalStepNanos");
    if(measuredBefore==0||nanosBefore==0)throw new IllegalStateException("metrics were not recorded");
    var state=level.getBlockState(p);level.removeBlockEntity(p);
    var replacement=(GiantSaplingEntity)((net.minecraft.world.level.block.EntityBlock)sapling).newBlockEntity(p,state);
    replacement.load(saved);level.setBlockEntity(replacement);
   } else if(ticks==350) {
    var saved=((GiantSaplingEntity)level.getBlockEntity(p)).saveWithoutMetadata().getCompound("TickTree");
    int count=saved.getInt("CandidateCount");if(count<=largeCandidatesBefore)throw new IllegalStateException("250-block candidate selection stalled: "+largeCandidatesBefore+" -> "+count);
    var metrics=saved.getCompound("Metrics");long measured=metrics.getLong("MeasuredSteps"),nanos=metrics.getLong("TotalStepNanos"),repeat=metrics.getLong("RepeatRequests");
    int requests=metrics.getInt("Requests"),unique=metrics.getLongArray("RequestedChunks").length;
    if(measured<=measuredBefore||nanos<=nanosBefore||requests!=unique+repeat)throw new IllegalStateException("metrics failed after task restoration");
    record("250_progress=true\ncandidates_at_100="+largeCandidatesBefore+"\ncandidates_at_350="+count+"\nphase="+saved.getInt("Phase")+"\nmeasuredSteps="+measured+"\ntotalStepNanos="+nanos+"\nrequests="+requests+"\nwaits="+metrics.getInt("Waits")+"\nrepeatRequests="+repeat+"\nuniqueRequestedChunks="+unique+"\nticketEvictions="+metrics.getLong("TicketEvictions")+"\nmetrics_restored=true\n");done=true;
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
    var rt=github.com.gengyoubo.tcmodern.api.TcModernApi.runtime().orElseThrow();
    var hoe=rt.mappings().toHost(LegacyMappings.ITEM_STACK,rt.stack(new github.com.gengyoubo.mfcl.api.LegacyStack("Thaumcraft:ItemHoeElemental",0,1,"")),net.minecraft.world.item.ItemStack.class).orElseThrow();
    player.getAbilities().instabuild=false;player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,hoe);
    var hit=new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(largePos),net.minecraft.core.Direction.UP,largePos,false);
    int required=n==0?20:150,cost=n==0?5:25;
    if(hoe.getMaxDamage()<required)throw new IllegalStateException("Hoe of Growth has insufficient maximum durability");
    hoe.setDamageValue(hoe.getMaxDamage()-required+1);
    largeSapling.use(level.getBlockState(largePos),level,largePos,player,net.minecraft.world.InteractionHand.MAIN_HAND,hit);
    if(largeEntity.isTreeGenerating()||hoe.getDamageValue()!=hoe.getMaxDamage()-required+1)throw new IllegalStateException("worn hoe started a giant tree");
    hoe.setDamageValue(0);
    long start=System.nanoTime();
    largeSapling.use(level.getBlockState(largePos),level,largePos,player,net.minecraft.world.InteractionHand.MAIN_HAND,hit);
    if(System.nanoTime()-start>2_000_000_000L)throw new IllegalStateException("large tree start blocked");
    if(hoe.getDamageValue()!=cost)throw new IllegalStateException("incorrect Hoe of Growth durability: "+hoe.getDamageValue());
    var largeSaved=largeEntity.saveWithoutMetadata();
    if(!level.getBlockState(largePos).is(largeSapling) || !largeSaved.getBoolean("TickTreeActive") || largeSaved.getCompound("TickTree").getInt("HeightLimit")!=250)throw new IllegalStateException("250-block tree job did not start");
    GiantSaplingEntity.tick(level,largePos,level.getBlockState(largePos),largeEntity);
    if(largeEntity.saveWithoutMetadata().getCompound("TickTree").getInt("CandidateCount")<1)throw new IllegalStateException("250-block candidate selection did not progress");
    largeSapling.use(level.getBlockState(largePos),level,largePos,player,net.minecraft.world.InteractionHand.MAIN_HAND,hit);
    if(hoe.getDamageValue()!=cost)throw new IllegalStateException("active tree consumed extra hoe durability");
    player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,bone);int remaining=bone.getCount();
    var status=largeSapling.use(level.getBlockState(largePos),level,largePos,player,net.minecraft.world.InteractionHand.MAIN_HAND,hit);
    if(!status.consumesAction()||bone.getCount()!=remaining)throw new IllegalStateException("active tree status consumed bone meal");
    level.removeBlock(largePos,false);
   }
   largeSaplingsStarted=true;
   if(Boolean.getBoolean("tc4modern.compareLeafBatches"))checkSectionGuards(level);
   level.setBlock(pos.below(),Blocks.DIRT.defaultBlockState(),3);level.setBlock(pos,sapling.defaultBlockState(),3);
   if(Boolean.getBoolean("tc4modern.compareLeafBatches"))level.random.setSeed(9876L);
   var be=(GiantSaplingEntity)level.getBlockEntity(pos);be.start();var saved=be.saveWithoutMetadata();if(!saved.getBoolean("TickTreeActive"))throw new IllegalStateException("job not started");treeSeed=saved.getCompound("TickTree").getLong("Seed");treeHeight=saved.getCompound("TickTree").getInt("Height");
   if(Boolean.getBoolean("tc4modern.testUnbatchedLeaves")){
    saved.getCompound("TickTree").remove("CompactLeafBatches");saved.getCompound("TickTree").remove("SectionLeafBatches");level.removeBlockEntity(pos);
    var replacement=(GiantSaplingEntity)((net.minecraft.world.level.block.EntityBlock)sapling).newBlockEntity(pos,level.getBlockState(pos));replacement.load(saved);level.setBlockEntity(replacement);
   }
  }
  else if(ticks>=100&&!reloaded&&b.is(sapling)){
    var be=(GiantSaplingEntity)level.getBlockEntity(pos);var saved=be.saveWithoutMetadata();
    if(!saved.getBoolean("TickTreeActive"))throw new IllegalStateException("job not active before reload");
    var job=saved.getCompound("TickTree");
    if(job.getInt("Phase")>5)throw new IllegalStateException("missed hybrid plan reload");
    if(job.getInt("Phase")==5&&(job.getInt("PlanCell")>0||job.getInt("PlanSection")>0)){
     if(job.getBoolean("CompactLeafBatches")==Boolean.getBoolean("tc4modern.testUnbatchedLeaves"))throw new IllegalStateException("unexpected leaf batch mode");
     level.removeBlockEntity(pos);var replacement=(GiantSaplingEntity)((net.minecraft.world.level.block.EntityBlock)sapling).newBlockEntity(pos,b);replacement.load(saved);level.setBlockEntity(replacement);reloaded=true;
    }
  }
  else if(ticks>100&&!b.is(sapling)){
    if(Boolean.getBoolean("tc4modern.compareLeafBatches")&&++postCompleteTicks<80)return;
    if(!reloaded)throw new IllegalStateException("reload missing");int leaves=0;var rt=github.com.gengyoubo.tcmodern.api.TcModernApi.runtime().orElseThrow();var leaf=rt.hostBlock("Thaumcraft:blockMagicalLeaves",silver?1:0,net.minecraft.world.level.block.state.BlockState.class).orElseThrow();var rng=new java.util.Random(9);
    BlockPos sample=null;
    for(int i=0;i<100000;i++){
     var p=pos.offset(rng.nextInt(225)-112,rng.nextInt(80),rng.nextInt(225)-112);var state=level.getBlockState(p);
     if(state.getBlock()==leaf.getBlock()){
      if(!state.getValue(MagicalLeavesBlock.MASSIVE_STATIC)||!state.getValue(LeavesBlock.PERSISTENT)||state.getValue(LeavesBlock.DISTANCE)!=7)throw new IllegalStateException("generated leaf is not static: "+state);
      if(level.getBlockTicks().hasScheduledTick(p,state.getBlock()))throw new IllegalStateException("static leaf has a scheduled tick");
      leaves++;sample=p;
      if(Math.abs(p.getX()-pos.getX())<=16&&Math.abs(p.getZ()-pos.getZ())<=16)clientSample=p.immutable();
     }
    }
    if(leaves<20)throw new IllegalStateException("too few sampled leaves "+leaves);
    long shapeHash=0xcbf29ce484222325L,heightHash=0xcbf29ce484222325L,lightHash=0xcbf29ce484222325L;
    if(Boolean.getBoolean("tc4modern.compareLeafBatches")){
     var scan=new BlockPos.MutableBlockPos();
     for(int y=0;y<80;y++)for(int x=-112;x<=112;x++)for(int z=-112;z<=112;z++){
      var state=level.getBlockState(scan.set(pos.getX()+x,pos.getY()+y,pos.getZ()+z));
      shapeHash=(shapeHash ^ net.minecraft.world.level.block.Block.getId(state))*0x100000001b3L;
     }
     for(int x=-112;x<=112;x++)for(int z=-112;z<=112;z++){
      heightHash=(heightHash^level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,pos.getX()+x,pos.getZ()+z))*0x100000001b3L;
      heightHash=(heightHash^level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,pos.getX()+x,pos.getZ()+z))*0x100000001b3L;
     }
     var lightRandom=new java.util.Random(3421);
     for(int i=0;i<10000;i++){
      scan.set(pos.getX()+lightRandom.nextInt(225)-112,pos.getY()+lightRandom.nextInt(80),pos.getZ()+lightRandom.nextInt(225)-112);
      lightHash=(lightHash^level.getBrightness(LightLayer.SKY,scan))*0x100000001b3L;
      lightHash=(lightHash^level.getBrightness(LightLayer.BLOCK,scan))*0x100000001b3L;
     }
    }
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
    var oldLevel=rt.accessor(github.com.gengyoubo.tcmodern.tc4.content.ReactorAccessors.World.class,rt.mappings().toLegacy(LegacyMappings.WORLD,level).orElseThrow());
    for(int i=0;i<selected;i++) {var np=GiantSaplingEntity.randomNodePosition(pos,treeHeight,11,true,treeSeed,i);if(level.getBlockState(np)==knot){
     var node=oldLevel.tile(np.getX(),np.getY(),np.getZ());
     if(node==null||!rt.values().isInstance(node,"thaumcraft.common.tiles.TileNode"))throw new IllegalStateException("silverwood knot missing TC4 node at "+np);
     nodeSample=np.immutable();found++;
    }}
    if(found>selected)throw new IllegalStateException("invalid silverwood knot count");
    record("large_both_species_started="+largeSaplingsStarted+"\nselected_nodes="+selected+"\nfound_knots="+found+"\ncompleted=true\nreloaded_block_entity="+reloaded+"\nstatic_leaf_metadata_and_nbt=true\ntree_seed="+treeSeed+"\nshape_hash="+Long.toUnsignedString(shapeHash)+"\nheight_hash="+Long.toUnsignedString(heightHash)+"\nlight_hash="+Long.toUnsignedString(lightHash)+"\nticks="+ticks+"\nroot="+b+"\nsampled_leaves="+leaves+"\n");done=true;
  }else if(ticks>2000)throw new IllegalStateException("timed out");
 }catch(Throwable x){x.printStackTrace();record("error="+x+"\nticks="+ticks+"\n");done=true;}}
 private void client(TickEvent.ClientTickEvent e){if(e.phase!=TickEvent.Phase.END||cs==99)return;var mc=Minecraft.getInstance();try{
  if(cs==0&&mc.screen instanceof TitleScreen&&mc.getOverlay()==null){org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());var d=mc.gameDirectory.toPath().resolve("logs/tc4modern");Files.createDirectories(d);Files.writeString(d.resolve("integrated80-pid.txt"),Long.toString(ProcessHandle.current().pid()));mc.options.pauseOnLostFocus=false;mc.options.renderDistance().set(3);worldId="tc4modern-integrated80-"+System.currentTimeMillis();var rules=new GameRules();rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,null);rules.getRule(GameRules.RULE_RANDOMTICKING).set(0,null);mc.createWorldOpenFlows().createFreshLevel(worldId,new LevelSettings("Integrated 80",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,rules,WorldDataConfiguration.DEFAULT),new WorldOptions(42,false,false),a->a.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());cs=1;}
  else if(cs==1&&mc.level!=null&&mc.player!=null&&mc.screen==null){var server=mc.getSingleplayerServer();server.submit(()->{var l=server.overworld();l.noSave=!Boolean.getBoolean("tc4modern.reopenAfterBatch");l.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(0,server);l.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server);for(var entity:l.getAllEntities())if(entity instanceof net.minecraft.world.entity.Mob)entity.discard();level=l;});cs=2;}
  else if(cs==2&&done){
   if(Boolean.getBoolean("tc4modern.compareLeafBatches")&&clientSample!=null){
    var s=mc.level.getBlockState(clientSample);
    if(!(s.getBlock() instanceof MagicalLeavesBlock)||!s.getValue(MagicalLeavesBlock.MASSIVE_STATIC))record("error=client leaf not synchronized at "+clientSample+" state="+s+"\n");
    else Files.writeString(mc.gameDirectory.toPath().resolve("logs/tc4modern/integrated80-greatwood-result.txt"),"client_sample_synchronized=true\n",java.nio.file.StandardOpenOption.APPEND);
   }
   if(Boolean.getBoolean("tc4modern.reopenAfterBatch")&&clientSample!=null){mc.level.disconnect();mc.clearLevel();mc.setScreen(new TitleScreen());cs=3;}
   else {cs=99;mc.stop();}
  }
  else if(cs==3&&++reopenFrames>=20){mc.createWorldOpenFlows().loadLevel(new TitleScreen(),worldId);cs=4;}
  else if(cs==4&&mc.screen instanceof net.minecraft.client.gui.screens.ConfirmScreen confirmation){for(var child:confirmation.children())if(child instanceof net.minecraft.client.gui.components.Button button){button.onPress();break;}}
  else if(cs==4&&mc.level!=null&&mc.player!=null&&mc.screen==null){var server=mc.getSingleplayerServer();reopenedSample=server.submit(()->{var l=server.overworld();var s=l.getBlockState(clientSample);if(!(s.getBlock() instanceof MagicalLeavesBlock)||!s.getValue(MagicalLeavesBlock.MASSIVE_STATIC)||!s.getValue(LeavesBlock.PERSISTENT))return false;if(nodeSample!=null){var rt=github.com.gengyoubo.tcmodern.api.TcModernApi.runtime().orElseThrow();var old=rt.accessor(github.com.gengyoubo.tcmodern.tc4.content.ReactorAccessors.World.class,rt.mappings().toLegacy(LegacyMappings.WORLD,l).orElseThrow());var node=old.tile(nodeSample.getX(),nodeSample.getY(),nodeSample.getZ());return node!=null&&rt.values().isInstance(node,"thaumcraft.common.tiles.TileNode");}return true;});cs=5;}
  else if(cs==5&&reopenedSample.isDone()){if(!reopenedSample.join())throw new IllegalStateException("section-written leaf missing after world reopen");Files.writeString(mc.gameDirectory.toPath().resolve("logs/tc4modern/integrated80-greatwood-result.txt"),"saved_world_reopened_sample=true\n",java.nio.file.StandardOpenOption.APPEND);cs=99;mc.stop();}
 }catch(Throwable x){x.printStackTrace();record("client_error="+x+"\n");cs=99;mc.stop();}}
}
