package github.com.gengyoubo.tcmodern.tc4.client.testing;

import github.com.gengyoubo.tcmodern.tc4.content.GiantSaplingEntity;
import github.com.gengyoubo.tcmodern.tc4.content.MagicalLeavesBlock;
import github.com.gengyoubo.tcmodern.tc4.init.Tc4Blocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.CompletableFuture;

/** Opt-in, isolated 130-block generation and independent save/reopen sample. */
@Mod.EventBusSubscriber(modid="tc4modern",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
public final class SyncLifecycleProbe {
    private static final int HEIGHT=Integer.getInteger("tc4modern.lifecycleHeight",80), SAMPLES=4096;
    private final BlockPos origin=new BlockPos(4,319-HEIGHT,4);
    private final BlockPos[] positions=new BlockPos[SAMPLES];
    private final int[] before=new int[SAMPLES], beforeBlocks=new int[SAMPLES];
    private int stage, frames; private String worldId; private long exitStart,generationGameTime;
    private CompletableFuture<?> pending;
    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        if(Boolean.getBoolean("tc4modern.syncLifecycleProbe"))event.enqueueWork(()->MinecraftForge.EVENT_BUS.addListener(new SyncLifecycleProbe()::tick));
    }
    private static java.nio.file.Path output() {return Minecraft.getInstance().gameDirectory.toPath().resolve("logs/tc4modern/sync-client-"+HEIGHT+"-result.txt");}
    private static void append(String text) {
        try {Files.createDirectories(output().getParent());Files.writeString(output(),text,StandardOpenOption.CREATE,StandardOpenOption.APPEND);}
        catch(Exception failure){throw new RuntimeException(failure);}
    }
    private void tick(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END||stage==99)return;
        var mc=Minecraft.getInstance();
        try {
            if(stage==0&&mc.screen instanceof TitleScreen&&mc.getOverlay()==null) {
                Files.createDirectories(output().getParent());Files.writeString(output(),"status=started\n");
                Files.writeString(output().resolveSibling("sync-lifecycle-pid.txt"),Long.toString(ProcessHandle.current().pid()));
                org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());mc.options.pauseOnLostFocus=false;mc.options.renderDistance().set(3);
                var rules=new GameRules();rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,null);rules.getRule(GameRules.RULE_RANDOMTICKING).set(0,null);
                worldId="tc4modern-sync-lifecycle-"+System.currentTimeMillis();
                mc.createWorldOpenFlows().createFreshLevel(worldId,new LevelSettings("Synchronous lifecycle",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,rules,WorldDataConfiguration.DEFAULT),
                        new WorldOptions(42,false,false),a->a.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
                stage=1;
            } else if(stage==1&&mc.level!=null&&mc.player!=null&&mc.screen==null) {
                var server=mc.getSingleplayerServer();
                pending=server.submit(()->{
                    ServerLevel level=server.overworld();level.noSave=false;
                    if(HEIGHT!=80&&HEIGHT!=130)throw new IllegalArgumentException("Client lifecycle height must be 80 or 130");
                    for(int i=0;i<2;i++) {
                        var p=new BlockPos(40+i*4,64,4);var large=i==0?Tc4Blocks.ENCHANTED_SACRED_GREATWOOD_SAPLING.get():Tc4Blocks.ENCHANTED_SACRED_SILVERWOOD_SAPLING.get();
                        level.setBlock(p.below(),Blocks.DIRT.defaultBlockState(),3);level.setBlock(p,large.defaultBlockState(),3);
                        ((GiantSaplingEntity)level.getBlockEntity(p)).start();
                        if(!level.getBlockState(p).is(large))throw new IllegalStateException("oversize sapling was not blocked");
                        var fake=net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(level);var bone=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BONE_MEAL,8);
                        fake.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,bone);
                        var hit=new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(p),net.minecraft.core.Direction.UP,p,false);
                        var status=large.use(level.getBlockState(p),level,p,fake,net.minecraft.world.InteractionHand.MAIN_HAND,hit);
                        if(!status.consumesAction()||bone.getCount()!=8)throw new IllegalStateException("blocked tree consumed bone meal");
                        level.removeBlock(p,false);
                    }
                    append("oversize_guard_both=true\n");
                    var player=server.getPlayerList().getPlayers().get(0);player.getAbilities().flying=true;player.onUpdateAbilities();
                    player.teleportTo(origin.getX()+.5,origin.getY()+HEIGHT*.35,origin.getZ()-32.5);player.setYRot(0);player.setXRot(-25);
                    var sapling=Tc4Blocks.ENCHANTED_SACRED_GREATWOOD_SAPLING.get();
                    level.setBlock(origin.below(),Blocks.DIRT.defaultBlockState(),3);level.setBlock(origin,sapling.defaultBlockState(),3);
                    level.setBlock(origin.offset(10,60,10),Blocks.CHEST.defaultBlockState(),3);
                    level.setBlock(origin.offset(11,60,10),Blocks.OAK_FENCE.defaultBlockState(),3);
                    level.setBlock(origin.offset(-6,62,-6),Blocks.BEDROCK.defaultBlockState(),3);
                    var be=(GiantSaplingEntity)level.getBlockEntity(origin);level.random.setSeed(9876L);
                    long start=System.nanoTime();be.start();long elapsed=System.nanoTime()-start;
                    generationGameTime=level.getGameTime();
                    if(level.getBlockState(origin).is(sapling)||level.getBlockState(origin).isAir())throw new IllegalStateException("tree did not grow");
                    append("world="+worldId+"\ngeneration_complete=true\nheight="+HEIGHT+"\ngeneration_main_thread_ms="+elapsed/1e6+
                            "\nheap_used_after_generation_bytes="+(Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory())+
                            "\nheap_max_bytes="+Runtime.getRuntime().maxMemory()+"\nblock_ticks_after_generation="+level.getBlockTicks().count()+
                            "\nlight_work_after_generation="+level.getLightEngine().hasLightWork()+"\n");
                });stage=2;
            } else if(stage==2&&pending.isDone()) {pending.join();frames=0;stage=3;}
            else if(stage==3&&++frames>=40) {
                var server=mc.getSingleplayerServer();pending=server.submit(()->{
                    var level=server.overworld();var random=new java.util.Random(3817);var snapshot=new StringBuilder();
                    long resumedTicks=level.getGameTime()-generationGameTime;
                    if(resumedTicks<=0)throw new IllegalStateException("server did not resume ticking after generation");
                    append("server_ticks_after_generation="+resumedTicks+"\nblock_ticks_before_save="+level.getBlockTicks().count()+
                            "\nlight_work_before_save="+level.getLightEngine().hasLightWork()+"\n");
                    for(int i=0;i<SAMPLES;i++) {
                        var p=origin.offset(random.nextInt(65)-32,random.nextInt(HEIGHT),random.nextInt(65)-32);
                        var s=level.getBlockState(p);positions[i]=p;before[i]=Block.getId(s);beforeBlocks[i]=Block.getId(s.getBlock().defaultBlockState());
                        snapshot.append(p.getX()).append(',').append(p.getY()).append(',').append(p.getZ()).append('\t').append(s).append('\n');
                    }
                    try {Files.writeString(output().resolveSibling("sync-lifecycle-before.tsv"),snapshot);}catch(java.io.IOException failure){throw new RuntimeException(failure);}
                    append("pre_save_samples="+SAMPLES+"\npre_save_sample_radius=32\n");
                    long start=System.nanoTime();level.save(null,true,false);long elapsed=System.nanoTime()-start;
                    append("save_returned=true\nsave_flush_ms="+elapsed/1e6+"\n");
                });stage=4;
            } else if(stage==4&&pending.isDone()) {
                pending.join();int available=0,identityDiff=0,clientLeaves=0;
                for(int i=0;i<SAMPLES;i++)if(mc.level.hasChunk(positions[i].getX()>>4,positions[i].getZ()>>4)){
                    var state=mc.level.getBlockState(positions[i]);available++;
                    if(state.getBlock() instanceof MagicalLeavesBlock)clientLeaves++;
                    if(Block.getId(state.getBlock().defaultBlockState())!=beforeBlocks[i])identityDiff++;
                }
                append("client_available_samples="+available+"\nclient_sampled_leaves="+clientLeaves+"\nclient_identity_differences="+identityDiff+"\n");
                if(available==0||clientLeaves==0)throw new IllegalStateException("client has no sampled tree leaves");
                var screenshot=output().resolveSibling("sync-client-"+HEIGHT+"-view.png");
                try(var image=net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(screenshot);}
                append("client_screenshot="+screenshot+"\n");
                exitStart=System.nanoTime();mc.level.disconnect();mc.clearLevel();mc.setScreen(new TitleScreen());
                append("world_closed=true\nclose_ms="+(System.nanoTime()-exitStart)/1e6+"\n");frames=0;stage=5;
            } else if(stage==5&&++frames>=20) {mc.createWorldOpenFlows().loadLevel(new TitleScreen(),worldId);stage=6;}
            else if(stage==6&&mc.screen instanceof net.minecraft.client.gui.screens.ConfirmScreen confirmation) {
                for(var child:confirmation.children())if(child instanceof net.minecraft.client.gui.components.Button button){button.onPress();break;}
            } else if(stage==6&&mc.level!=null&&mc.player!=null&&mc.screen==null) {
                var server=mc.getSingleplayerServer();pending=server.submit(()->{
                    var level=server.overworld();int stateDiff=0,blockDiff=0,leafDistanceDiff=0,leafLegacyDiff=0,otherDiff=0;var examples=new StringBuilder();var snapshot=new StringBuilder();
                    for(int i=0;i<SAMPLES;i++) {var s=level.getBlockState(positions[i]);if(Block.getId(s)!=before[i]) {
                        stateDiff++;var old=Block.stateById(before[i]);boolean classified=false;
                        if(old.getBlock() instanceof MagicalLeavesBlock&&s.is(old.getBlock())) {
                            if(old.getValue(LeavesBlock.DISTANCE)!=s.getValue(LeavesBlock.DISTANCE)){leafDistanceDiff++;classified=true;}
                            if(old.getValue(MagicalLeavesBlock.LEGACY_META)!=s.getValue(MagicalLeavesBlock.LEGACY_META)){leafLegacyDiff++;classified=true;}
                        }
                        if(!classified)otherDiff++;
                        if(stateDiff<=8)examples.append("difference_").append(stateDiff).append('=').append(positions[i]).append(' ').append(old).append(" -> ").append(s).append('\n');
                    }
                        if(Block.getId(s.getBlock().defaultBlockState())!=beforeBlocks[i])blockDiff++;
                        var p=positions[i];snapshot.append(p.getX()).append(',').append(p.getY()).append(',').append(p.getZ()).append('\t').append(s).append('\n');}
                    try {Files.writeString(output().resolveSibling("sync-lifecycle-after.tsv"),snapshot);}catch(java.io.IOException failure){throw new RuntimeException(failure);}
                    append("reopen_complete=true\nsample_count="+SAMPLES+"\nblock_state_differences="+stateDiff+"\nblock_identity_differences="+blockDiff+
                            "\nleaf_distance_differences="+leafDistanceDiff+"\nleaf_legacy_meta_differences="+leafLegacyDiff+"\nother_state_differences="+otherDiff+"\n"+examples);
                });stage=7;
            } else if(stage==7&&pending.isDone()) {pending.join();stage=99;mc.stop();}
        } catch(Throwable failure) {failure.printStackTrace();try{append("probe_error="+failure+"\n");}catch(Throwable ignored){}stage=99;mc.stop();}
    }
}
