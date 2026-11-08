package github.com.gengyoubo.tcmodern.tc4.client.testing;

import github.com.gengyoubo.mfcl.api.LegacyMappings;
import github.com.gengyoubo.tcmodern.api.TcModernApi;
import github.com.gengyoubo.tcmodern.tc4.content.GiantSaplingEntity;
import github.com.gengyoubo.tcmodern.tc4.content.MagicalLeavesBlock;
import github.com.gengyoubo.tcmodern.tc4.content.ReactorAccessors;
import github.com.gengyoubo.tcmodern.tc4.init.Tc4Blocks;
import github.com.gengyoubo.tcmodern.tc4.world.MfrMassiveTree;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
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
import java.util.concurrent.CompletableFuture;

/** Disposable-world smoke test for the synchronous sapling path. */
@Mod.EventBusSubscriber(modid="tc4modern",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
public final class SyncSaplingProbe {
    private int stage; private CompletableFuture<String> result;
    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        if(Boolean.getBoolean("tc4modern.syncSaplingProbe")) event.enqueueWork(()->MinecraftForge.EVENT_BUS.addListener(new SyncSaplingProbe()::tick));
    }
    private void tick(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END||stage==99)return;
        var mc=Minecraft.getInstance();
        try {
            if(stage==0&&mc.screen instanceof TitleScreen&&mc.getOverlay()==null) {
                org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());
                mc.options.pauseOnLostFocus=false;mc.options.renderDistance().set(3);
                var rules=new GameRules();rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,null);rules.getRule(GameRules.RULE_RANDOMTICKING).set(0,null);
                mc.createWorldOpenFlows().createFreshLevel("tc4modern-sync-sapling-"+System.currentTimeMillis(),
                        new LevelSettings("Synchronous sapling",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,rules,WorldDataConfiguration.DEFAULT),
                        new WorldOptions(42,false,false),a->a.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
                stage=1;
            } else if(stage==1&&mc.level!=null&&mc.player!=null&&mc.screen==null) {
                boolean silver=Boolean.getBoolean("tc4modern.syncSaplingSilver");
                result=mc.getSingleplayerServer().submit(()->{
                    var level=mc.getSingleplayerServer().overworld();level.noSave=true;
                    int testHeight=Integer.getInteger("tc4modern.syncProbeHeight",80);
                    if(testHeight<80||testHeight>250)throw new IllegalArgumentException("Test height must be 80..250");
                    var origin=new BlockPos(4,level.getMaxBuildHeight()-1-testHeight,4);
                    var sapling=silver?Tc4Blocks.ENCHANTED_SACRED_SILVERWOOD_SAPLING.get():Tc4Blocks.ENCHANTED_SACRED_GREATWOOD_SAPLING.get();
                    level.setBlock(origin.below(),Blocks.DIRT.defaultBlockState(),3);level.setBlock(origin,sapling.defaultBlockState(),3);
                    level.setBlock(origin.offset(10,60,10),Blocks.CHEST.defaultBlockState(),3);
                    level.setBlock(origin.offset(11,60,10),Blocks.OAK_FENCE.defaultBlockState(),3);
                    level.setBlock(origin.offset(-6,62,-6),Blocks.BEDROCK.defaultBlockState(),3);
                    var be=(GiantSaplingEntity)level.getBlockEntity(origin);level.random.setSeed(9876L);
                    boolean reference=Boolean.getBoolean("tc4modern.syncReferenceWrites");
                    String runId=System.getProperty("tc4modern.syncProbeRunId","");
                    if(!runId.isEmpty()&&!runId.matches("[A-Za-z0-9_-]{1,80}"))throw new IllegalArgumentException("Invalid probe run ID");
                    java.nio.file.Path marker=null;
                    if(!runId.isEmpty()) {
                        marker=mc.gameDirectory.toPath().resolve("logs/tc4modern/"+runId);
                        try {Files.createDirectories(marker.getParent());Files.writeString(marker.resolveSibling(runId+"-start.txt"),
                                "pid="+ProcessHandle.current().pid()+"\nstartedEpochMs="+System.currentTimeMillis()+"\nheight="+testHeight+"\n");}
                        catch(java.io.IOException failure){throw new RuntimeException(failure);}
                    }
                    long started=System.nanoTime();
                    if(reference) {
                        var rt=TcModernApi.runtime().orElseThrow();int species=silver?1:0;var logs=new BlockState[4];
                        for(int axis=0;axis<3;axis++)logs[axis]=rt.hostBlock("Thaumcraft:blockMagicalLog",species|axis*4,BlockState.class).orElseThrow();
                        logs[3]=(silver?Tc4Blocks.SILVERWOOD_WOOD.get():Tc4Blocks.GREATWOOD_WOOD.get()).defaultBlockState();
                        var leaf=rt.hostBlock("Thaumcraft:blockMagicalLeaves",species,BlockState.class).orElseThrow();
                        level.removeBlock(origin,false);
                        var generated=MfrMassiveTree.generate(level,origin,(net.minecraftforge.common.IPlantable)sapling,logs,leaf,MfrMassiveTree.Kind.ENCHANTED_SACRED,0,new java.util.Random(9876L).nextLong(),false);
                        if(!generated.generated())throw new IllegalStateException("reference tree did not grow");
                    } else be.start();
                    long elapsed=System.nanoTime()-started;
                    if(level.getBlockState(origin).is(sapling)||level.getBlockState(origin).isAir())throw new IllegalStateException("synchronous sapling did not grow");
                    if(marker!=null)try {Files.writeString(marker.resolveSibling(runId+"-done.txt"),"generation_complete=true\nelapsed_ms="+elapsed/1_000_000.0+"\n");}
                    catch(java.io.IOException failure){throw new RuntimeException(failure);}
                    int leaves=0;var random=new java.util.Random(9);
                    for(int i=0;i<100000;i++) {var state=level.getBlockState(origin.offset(random.nextInt(225)-112,random.nextInt(testHeight),random.nextInt(225)-112));
                        if(state.getBlock() instanceof MagicalLeavesBlock){if(state.getValue(MagicalLeavesBlock.MASSIVE_STATIC))throw new IllegalStateException("synchronous tree used static batch leaves");leaves++;}}
                    if(leaves<20)throw new IllegalStateException("too few leaves: "+leaves);
                    long shapeHash=0xcbf29ce484222325L,heightHash=0xcbf29ce484222325L;var scan=new BlockPos.MutableBlockPos();
                    if(testHeight==80)for(int y=0;y<80;y++)for(int x=-112;x<=112;x++)for(int z=-112;z<=112;z++){
                        shapeHash=(shapeHash^net.minecraft.world.level.block.Block.getId(level.getBlockState(scan.set(origin.getX()+x,origin.getY()+y,origin.getZ()+z))))*0x100000001b3L;
                    }
                    if(testHeight==80)for(int x=-112;x<=112;x++)for(int z=-112;z<=112;z++){
                        heightHash=(heightHash^level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,origin.getX()+x,origin.getZ()+z))*0x100000001b3L;
                        heightHash=(heightHash^level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,origin.getX()+x,origin.getZ()+z))*0x100000001b3L;
                    }
                    int nodes=0;
                    if(silver) {
                        long treeSeed=new java.util.Random(9876L).nextLong();int base=(int)(testHeight*.45F);int height=base+new java.util.Random(treeSeed).nextInt(testHeight-base);
                        var rt=TcModernApi.runtime().orElseThrow();var world=rt.accessor(ReactorAccessors.World.class,rt.mappings().toLegacy(LegacyMappings.WORLD,level).orElseThrow());
                        for(int i=0;i<GiantSaplingEntity.randomNodeCount(treeSeed,testHeight);i++) {
                            var p=GiantSaplingEntity.randomNodePosition(origin,height,11,true,treeSeed,i);var node=world.tile(p.getX(),p.getY(),p.getZ());
                            if(node!=null&&rt.values().isInstance(node,"thaumcraft.common.tiles.TileNode"))nodes++;
                        }
                        if(nodes==0)throw new IllegalStateException("silverwood generated no TC4 nodes");
                    }
                    return "completed=true\nspecies="+(silver?"silverwood":"greatwood")+"\nreference="+reference+"\nheight="+testHeight+"\nsynchronous_ms="+elapsed/1_000_000.0+"\nsampled_leaves="+leaves+"\ntc4_nodes="+nodes+"\nshape_hash="+(testHeight==80?Long.toUnsignedString(shapeHash):"not_measured")+"\nheight_hash="+(testHeight==80?Long.toUnsignedString(heightHash):"not_measured")+"\n";
                });stage=2;
            } else if(stage==2&&result.isDone()) {
                var path=mc.gameDirectory.toPath().resolve("logs/tc4modern/sync-sapling-result.txt");Files.createDirectories(path.getParent());
                try {Files.writeString(path,result.join());}catch(Throwable failure){Files.writeString(path,"error="+failure+"\n");throw failure;}
                stage=99;mc.stop();
            }
        } catch(Throwable failure) {failure.printStackTrace();stage=99;mc.stop();}
    }
}
