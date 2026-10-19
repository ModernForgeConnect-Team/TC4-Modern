package github.com.gengyoubo.tcmodern.tc4.client.testing;

import com.mojang.logging.LogUtils;
import github.com.gengyoubo.mfcl.api.*;
import github.com.gengyoubo.mfcl.api.mla.*;
import github.com.gengyoubo.tcmodern.api.TcModernApi;
import github.com.gengyoubo.tcmodern.api.scan.*;
import github.com.gengyoubo.tcmodern.lib.scan.*;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
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

/** Real held input -> network -> server research -> save/reload acceptance, without MFCL internals. */
@Mod.EventBusSubscriber(modid="tc4modern",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
public final class ModernScannerSmokeProbe {
    private static final BlockPos TARGET = new BlockPos(0,81,4);
    private static final ScanKey BLOCK = ScanKey.block("minecraft:deepslate");
    private static final ScanKey ITEM = ScanKey.item("minecraft:copper_ingot");
    private static final ScanKey ENTITY = ScanKey.entity("minecraft:allay");
    private final long deadline = System.nanoTime()+TimeUnit.MINUTES.toNanos(6);
    private final Map<String,Object> proof = new LinkedHashMap<>();
    private int stage, frames, firstPool;
    private int loggedStage = -1;
    private String world;
    private CompletableFuture<?> pending;
    @LegacyType("thaumcraft.common.CommonProxy") public interface Proxy {
        @LegacyMethod(name="getPlayerKnowledge",descriptor="()Lthaumcraft/common/lib/research/PlayerKnowledge;") LegacyObject knowledge();
    }
    @LegacyType("thaumcraft.common.lib.research.PlayerKnowledge") public interface Knowledge {
        @LegacyMethod(name="getAspectPoolFor",descriptor="(Ljava/lang/String;Lthaumcraft/api/aspects/Aspect;)S") short pool(String name,LegacyObject aspect);
        @LegacyMethod(name="addDiscoveredAspect",descriptor="(Ljava/lang/String;Lthaumcraft/api/aspects/Aspect;)Z") boolean discover(String name,LegacyObject aspect);
    }
    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        if (Boolean.getBoolean("tc4modern.modernScannerSmokeTest"))
            event.enqueueWork(() -> MinecraftForge.EVENT_BUS.addListener(new ModernScannerSmokeProbe()::tick));
    }
    private void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage==99) return;
        var mc=Minecraft.getInstance();
        try {
            if (stage != loggedStage) {
                loggedStage = stage;
                LogUtils.getLogger().info("TC4_MODERN_SCANNER_SMOKE_STAGE: {}", stage);
            }
            require(!(mc.screen instanceof net.minecraft.client.gui.screens.DisconnectedScreen),
                    "Disconnected at stage " + stage);
            require(System.nanoTime()<deadline,"Timed out at stage "+stage+", screen="
                    +(mc.screen==null?"none":mc.screen.getClass().getSimpleName()));
            if (pending != null && pending.isCompletedExceptionally()) pending.join();
            if (stage==0 && mc.screen instanceof TitleScreen && mc.getOverlay()==null) {
                org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());
                mc.options.pauseOnLostFocus=false;mc.options.renderDistance().set(2);
                world="tc4modern-scanner-smoke-"+System.currentTimeMillis();
                mc.createWorldOpenFlows().createFreshLevel(world,new LevelSettings("TC4 modern scans",GameType.CREATIVE,false,
                        net.minecraft.world.Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),
                        new WorldOptions(42,false,false),access -> access.registryOrThrow(Registries.WORLD_PRESET)
                                .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());stage=1;
            } else if (stage==1 && ready(mc)) {
                proof.put("initialWorldLogin", true);
                pending=server(mc,() -> {initialize(player(mc));return true;});stage=2;
            } else if (stage==2 && pending.isDone() && ready(mc) && mc.player.getY()>79 && target(mc,BLOCK)) {
                pending.join();hold(mc);frames=0;stage=3;
            } else if (stage==3 && ++frames>=40) {
                release(mc);pending=server(mc,() -> {
                    var player=player(mc); require(ScanResearch.scanned(player,BLOCK),"Held block scan was not recorded");
                    firstPool=pool(player,"terra"); require(firstPool>0,"Scan did not award original research points");
                    return true;
                });stage=4;
            } else if (stage==4 && pending.isDone()) {
                pending.join();proof.put("heldBlockScanAndOriginalReward",true);hold(mc);frames=0;stage=5;
            } else if (stage==5 && ++frames>=35) {
                release(mc);pending=server(mc,() -> {
                    require(pool(player(mc),"terra")==firstPool,"Repeat scan awarded duplicate points");
                    prepareItem(player(mc));return true;
                });stage=6;
            } else if (stage==6 && pending.isDone() && target(mc,ITEM)) {
                pending.join();proof.put("repeatScanNoDuplicatePoints",true);hold(mc);frames=0;stage=7;
            } else if (stage==7 && ++frames>=40) {
                release(mc);pending=server(mc,() -> {
                    require(ScanResearch.scanned(player(mc),ITEM),"Modern dropped item was not scanned");
                    prepareEntity(player(mc));return true;
                });stage=8;
            } else if (stage==8 && pending.isDone() && target(mc,ENTITY)) {
                pending.join();proof.put("modernDroppedItemScan",true);hold(mc);frames=0;stage=9;
            } else if (stage==9 && ++frames>=40) {
                release(mc);pending=server(mc,() -> {
                    require(ScanResearch.scanned(player(mc),ENTITY),"Modern entity was not scanned");return true;
                });stage=10;
            } else if (stage==10 && pending.isDone()) {
                pending.join();proof.put("modernEntityScan",true);mc.level.disconnect();mc.clearLevel();stage=11;frames=0;
            } else if (stage==11 && mc.getSingleplayerServer()==null && ++frames>20) {
                mc.createWorldOpenFlows().loadLevel(mc.screen,world);stage=12;
            } else if (stage==12 && ready(mc)) {
                pending=server(mc,() -> {
                    var player=player(mc);
                    require(ScanResearch.scanned(player,BLOCK)&&ScanResearch.scanned(player,ITEM)&&ScanResearch.scanned(player,ENTITY),"Scan history lost after reload");
                    require(pool(player,"terra")==firstPool,"Original research points lost after reload");return true;
                });stage=13;
            } else if (stage==13 && pending.isDone()) {
                pending.join();proof.put("historyAndOriginalPointsPersistAfterReload",true);finish(mc,null);
            }
        } catch(Exception|AssertionError failure) {finish(mc,failure);}
    }
    private void initialize(ServerPlayer player) {
        var api=TcModernApi.runtime().orElseThrow();var rules=ScanRules.server();
        require(rules.get(ScanKey.block("minecraft:deepslate_iron_ore")).equals(rules.get(ScanKey.block("minecraft:iron_ore"))),"Deepslate ore inheritance differs");
        require(rules.get(ScanKey.block("minecraft:cut_copper_stairs")).equals(rules.get(ScanKey.block("minecraft:copper_block"))),"Copper stair inheritance differs");
        proof.put("mainBlockAndOreInheritance",true);
        var blocked=ScanKey.block("minecraft:sculk");
        require(!api.withPlayer(player,old -> api.accessor(ScanAccessors.Manager.class).complete(old,result(api,blocked),"@")),"Original prerequisite bypassed");
        require(!ScanResearch.scanned(player,blocked),"Rejected scan recorded");proof.put("originalPrerequisitesEnforced",true);
        int before=pool(player,"terra");
        try { api.withPlayer(player,old -> {api.accessor(ScanAccessors.Manager.class).complete(old,result(api,BLOCK),"@");throw new IllegalStateException("expected rollback");}); }
        catch(LegacyAccessException expected) { }
        require(pool(player,"terra")==before,"Failed player transaction leaked points");proof.put("failedPlayerTransactionRollsBack",true);
        var borrowed=new AtomicReference<LegacyObject>();api.withPlayer(player,old -> {borrowed.set(old);return true;});
        boolean expired=false;try {api.accessor(ScanAccessors.Entity.class,borrowed.get()).world();}catch(LegacyAccessException expected){expired=true;}
        require(expired,"Player reference escaped callback scope");proof.put("borrowedPlayerExpires",true);
        player.teleportTo(0.5,80,0.5);player.setYRot(0);player.setXRot(0);
        for(BlockPos pos:BlockPos.betweenClosed(-2,80,1,2,84,8))player.serverLevel().setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());
        var scanner=api.mappings().toHost(LegacyMappings.ITEM_STACK,api.stack(new LegacyStack("Thaumcraft:ItemThaumometer",0,1,"")),ItemStack.class).orElseThrow();
        player.setItemInHand(InteractionHand.MAIN_HAND,scanner);
        player.serverLevel().setBlockAndUpdate(TARGET,Blocks.STONE.defaultBlockState());
        require(originalTarget(player,ScanKey.block("minecraft:stone")),"Original TC4 did not recognize stone");
        player.serverLevel().setBlockAndUpdate(TARGET,Blocks.DEEPSLATE.defaultBlockState());
        require(originalTarget(player,BLOCK),"Original TC4 did not recognize modern block");
        player.serverLevel().setBlockAndUpdate(new BlockPos(0,81,2),Blocks.STONE.defaultBlockState());
        require(!originalTarget(player,BLOCK),"Original target search accepted a block behind a wall");
        player.serverLevel().setBlockAndUpdate(new BlockPos(0,81,2),Blocks.AIR.defaultBlockState());proof.put("occlusionAndOriginalPath",true);
    }
    private void prepareItem(ServerPlayer player) {
        var api=TcModernApi.runtime().orElseThrow();
        api.withPlayer(player,old -> {
            var knowledge=knowledge(api);var types=api.accessor(ScanAccessors.AspectLookup.class);
            var tags=new HashSet<String>();ScanRules.server().values().forEach(values -> tags.addAll(values.amounts().keySet()));
            tags.addAll(List.of("vacuos","vitreus","victus","mortuus","spiritus","potentia","motus","tenebrae"));
            for(String tag:tags) {var aspect=types.find(tag);if(aspect!=null)knowledge.discover(player.getGameProfile().getName(),aspect);}return true;
        });
        player.serverLevel().setBlockAndUpdate(TARGET,Blocks.AIR.defaultBlockState());
        var item=new ItemEntity(player.serverLevel(),0.5,81.3,4.5,new ItemStack(Items.COPPER_INGOT,64));
        item.setNoGravity(true);item.setDeltaMovement(0,0,0);item.setPickUpDelay(32767);player.serverLevel().addFreshEntity(item);
    }
    private void prepareEntity(ServerPlayer player) {
        for(var entity:player.serverLevel().getEntitiesOfClass(ItemEntity.class,new net.minecraft.world.phys.AABB(TARGET).inflate(2)))entity.discard();
        var allay=EntityType.ALLAY.create(player.serverLevel());allay.moveTo(0.5,81.3,4.5,0,0);allay.setNoAi(true);allay.setNoGravity(true);player.serverLevel().addFreshEntity(allay);
    }
    private static Knowledge knowledge(LegacyRuntime api) {
        return api.accessor(Knowledge.class,api.accessor(Proxy.class,api.accessor(ScanAccessors.Thaumcraft.class).proxy()).knowledge());
    }
    private static int pool(ServerPlayer player,String tag) {
        var api=TcModernApi.runtime().orElseThrow();return api.withPlayer(player,old -> (int)knowledge(api).pool(player.getGameProfile().getName(),api.accessor(ScanAccessors.AspectLookup.class).find(tag)));
    }
    private static boolean ready(Minecraft mc){return mc.player!=null&&mc.level!=null&&mc.screen==null;}
    private static ServerPlayer player(Minecraft mc){return mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());}
    private static boolean target(Minecraft mc,ScanKey key){
        mc.player.setYRot(0);mc.player.setXRot(0);
        if(key.kind()==ScanKey.Kind.BLOCK)return net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(mc.level.getBlockState(TARGET).getBlock()).toString().equals(key.id());
        var box=new net.minecraft.world.phys.AABB(TARGET).inflate(2);
        if(key.kind()==ScanKey.Kind.ITEM)return mc.level.getEntitiesOfClass(ItemEntity.class,box).stream().anyMatch(item -> net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item.getItem().getItem()).toString().equals(key.id()));
        return mc.level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class,box).stream().anyMatch(entity -> net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString().equals(key.id()));
    }
    private static LegacyObject result(LegacyRuntime api,ScanKey key) {
        var source=OriginalScanBridge.stack(key);var item=api.accessor(ScanAccessors.Stack.class,api.stack(source)).item();
        return api.accessor(ScanAccessors.ResultFactory.class).create((byte)1,api.accessor(ScanAccessors.Item.class).id(item),source.metadata(),null,"");
    }
    private static boolean originalTarget(ServerPlayer player,ScanKey key) {
        var api=TcModernApi.runtime().orElseThrow();
        return api.withPlayer(player,old -> {
            var stack=api.stack(new LegacyStack("Thaumcraft:ItemThaumometer",0,1,""));
            var item=api.accessor(ScanAccessors.Stack.class,stack).item();
            var scan=api.accessor(ScanAccessors.Thaumometer.class,item).target(stack,api.accessor(ScanAccessors.Entity.class,old).world(),old,0);
            return scan!=null&&api.accessor(ScanAccessors.Result.class,scan).id()==api.accessor(ScanAccessors.Result.class,result(api,key)).id();
        });
    }
    private static void hold(Minecraft mc){mc.options.keyUse.setDown(true);}
    private static void release(Minecraft mc){mc.options.keyUse.setDown(false);}
    private static <T>CompletableFuture<T>server(Minecraft mc,Callable<T> action){return mc.getSingleplayerServer().submit(() -> {try{return action.call();}catch(Exception failure){throw new IllegalStateException(failure);}});}
    private static void require(boolean success,String message){if(!success)throw new IllegalStateException(message);}
    private void finish(Minecraft mc,Throwable failure){
        stage=99;release(mc);proof.put("status",failure==null?"PASS":"FAIL");
        if(failure!=null){proof.put("failure",failure.toString());LogUtils.getLogger().error("TC4_MODERN_SCANNER_SMOKE_FAIL",failure);}
        try{var file=mc.gameDirectory.toPath().resolve("logs/tc4modern/modern-scanner-smoke.json");Files.createDirectories(file.getParent());Files.writeString(file,new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(proof));}
        catch(java.io.IOException error){throw new IllegalStateException(error);}
        LogUtils.getLogger().info("TC4_MODERN_SCANNER_SMOKE_RESULT: {}",proof);
        if(mc.level!=null){mc.level.disconnect();mc.clearLevel();}mc.stop();
    }
}
