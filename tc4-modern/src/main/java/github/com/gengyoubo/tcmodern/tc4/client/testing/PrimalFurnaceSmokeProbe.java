package github.com.gengyoubo.tcmodern.tc4.client.testing;

import github.com.gengyoubo.mfcl.api.*;
import github.com.gengyoubo.mfcl.api.mla.*;
import github.com.gengyoubo.tcmodern.api.TcModernApi;
import github.com.gengyoubo.tcmodern.api.aspect.*;
import github.com.gengyoubo.tcmodern.lib.client.formed.FormedAccessors;
import github.com.gengyoubo.tcmodern.tc4.content.*;
import github.com.gengyoubo.tcmodern.tc4.init.Tc4Blocks;
import github.com.gengyoubo.tcmodern.tc4.client.furnace.PrimalFurnaceScreen;
import github.com.gengyoubo.tcmodern.api.research.TcResearch;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Exercises the original jar and tubes through the native furnace to an artificial node. */
@Mod.EventBusSubscriber(modid="tc4modern",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
public final class PrimalFurnaceSmokeProbe {
    private final long deadline=System.nanoTime()+TimeUnit.MINUTES.toNanos(5);
    private int stage,ticks; private BlockPos reactor,furnace,itemFurnace; private CompletableFuture<?> pending;
    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        if (Boolean.getBoolean("tc4modern.primalFurnaceSmokeTest"))
            event.enqueueWork(()->MinecraftForge.EVENT_BUS.addListener(new PrimalFurnaceSmokeProbe()::tick));
    }
    private void tick(TickEvent.ClientTickEvent event) {
        if (event.phase!=TickEvent.Phase.END || stage==99) return;
        var mc=Minecraft.getInstance();
        try {
            require(System.nanoTime()<deadline,"Timed out at stage "+stage);
            if (pending!=null && pending.isCompletedExceptionally()) pending.join();
            if (stage==0 && mc.screen instanceof TitleScreen && mc.getOverlay()==null) {
                org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow()); mc.options.pauseOnLostFocus=false; mc.options.renderDistance().set(4);
                mc.createWorldOpenFlows().createFreshLevel("primal-furnace-smoke-"+System.currentTimeMillis(),
                    new LevelSettings("Primal furnace smoke",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),
                    new WorldOptions(42,false,false),access->access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
                stage=1;
            } else if (stage==1 && mc.player!=null && mc.level!=null && mc.screen==null) {
                pending=mc.getSingleplayerServer().submit(()->{
                    var server=mc.getSingleplayerServer(); var level=server.overworld(); var player=server.getPlayerList().getPlayers().get(0);
                    var api=TcModernApi.runtime().orElseThrow(); reactor=player.blockPosition().above(5); furnace=reactor.east(2);
                    var oldFuel=api.stack(new LegacyStack("Thaumcraft:ItemResource",0,1,""));
                    int legacyBurn=api.accessor(ReactorAccessors.FurnaceFuel.class).burnTime(oldFuel);
                    var hostFuel=api.mappings().toHost(LegacyMappings.ITEM_STACK,oldFuel,ItemStack.class).orElseThrow();
                    if (legacyBurn>0) require(PrimalFurnaceBlockEntity.fuelTime(hostFuel)>0,"Original TC4 fuel was not accepted");
                    var tube=api.hostBlock("Thaumcraft:blockTube",0,BlockState.class).orElseThrow();
                    var jar=api.hostBlock("Thaumcraft:blockJar",0,BlockState.class).orElseThrow();
                    level.setBlockAndUpdate(reactor,Tc4Blocks.NODE_REACTOR.get().defaultBlockState());
                    level.setBlockAndUpdate(reactor.east(),tube);
                    level.setBlockAndUpdate(reactor.east().above(),tube);
                    level.setBlockAndUpdate(furnace,Tc4Blocks.PRIMAL_FURNACE.get().defaultBlockState());
                    level.setBlockAndUpdate(furnace.above(),api.hostBlock("Thaumcraft:blockMetalDevice",1,BlockState.class).orElseThrow());
                    level.setBlockAndUpdate(furnace.below(),tube);
                    level.setBlockAndUpdate(furnace.below(2),jar);
                    var world=api.mappings().toLegacy(LegacyMappings.WORLD,level).orElseThrow();
                    var oldJar=api.accessor(FormedAccessors.World.class,world).tile(furnace.getX(),furnace.getY()-2,furnace.getZ());
                    var auram=api.accessor(TcAspectAccessors.AspectLookup.class).find("auram");
                    require(api.accessor(Container.class,oldJar).add(auram,1)==0,"Cannot fill input jar");
                    itemFurnace=reactor.west(3);
                    level.setBlockAndUpdate(itemFurnace,Tc4Blocks.PRIMAL_FURNACE.get().defaultBlockState());
                    level.setBlockAndUpdate(itemFurnace.east(),tube);
                    level.setBlockAndUpdate(itemFurnace.east().below(),jar);
                    var itemTile=(PrimalFurnaceBlockEntity)level.getBlockEntity(itemFurnace);
                    itemTile.items().setStackInSlot(0,new ItemStack(Items.STONE));
                    player.teleportTo(itemFurnace.getX()+0.5,itemFurnace.getY(),itemFurnace.getZ()+2.5);
                    require(!TcResearch.isComplete(api,player,"primal_furnace"),"Fixture unexpectedly has the research");
                    level.getBlockState(itemFurnace).use(level,player,InteractionHand.MAIN_HAND,
                            new BlockHitResult(Vec3.atCenterOf(itemFurnace),Direction.UP,itemFurnace,false));
                }); stage=2;
            } else if (stage==2 && pending.isDone() && mc.screen instanceof PrimalFurnaceScreen) { pending.join(); ticks=0; stage=3; }
            else if (stage==3 && ++ticks>=40) {
                pending=mc.getSingleplayerServer().submit(()->{
                    var level=mc.getSingleplayerServer().overworld(); var tile=(PrimalFurnaceBlockEntity)level.getBlockEntity(itemFurnace);
                    require(tile.items().getStackInSlot(0).getCount()==1 && tile.contents().isEmpty(),"Smelting occurred without fuel");
                    tile.items().setStackInSlot(1,new ItemStack(Items.COAL));
                }); ticks=0; stage=4;
            } else if (stage==4 && pending.isDone()) { pending.join(); stage=5; }
            else if (stage==5 && ++ticks>=350) {
                require(mc.screen instanceof PrimalFurnaceScreen,"Furnace GUI closed unexpectedly");
                var menu=((PrimalFurnaceScreen)mc.screen).getMenu();
                require(!menu.results().isEmpty() && menu.burnFraction()>0,"GUI did not receive the buffer or fuel state");
                pending=mc.getSingleplayerServer().submit(()->{
                    var level=mc.getSingleplayerServer().overworld(); var api=TcModernApi.runtime().orElseThrow();
                    var world=api.mappings().toLegacy(LegacyMappings.WORLD,level).orElseThrow();
                    var node=api.accessor(FormedAccessors.World.class,world).tile(reactor.getX(),reactor.getY()+1,reactor.getZ());
                    require(node!=null && api.values().isInstance(node,"thaumcraft.common.tiles.TileNode"),"Output did not reach the node");
                    var expected=PrimalDecomposition.expand(Map.of("auram",1),TcAspects.snapshot(api));
                    var aspects=api.accessor(Aspects.class,api.accessor(Node.class,node).aspects());
                    var lookup=api.accessor(TcAspectAccessors.AspectLookup.class);
                    for (var entry:expected.entrySet()) require(aspects.amount(lookup.find(entry.getKey()))==entry.getValue(),"Incorrect output for "+entry.getKey());
                    var buffer=(PrimalFurnaceBlockEntity)level.getBlockEntity(furnace);
                    require(buffer.contents().isEmpty(),"Output remains buffered after transport");
                    var itemTile=(PrimalFurnaceBlockEntity)level.getBlockEntity(itemFurnace);
                    var itemAspects=ModernDeconstructionBlockEntity.itemAspects(api,new ItemStack(Items.STONE));
                    var expectedItem=PrimalDecomposition.expand(itemAspects,TcAspects.snapshot(api));
                    require(itemTile.items().getStackInSlot(0).isEmpty() && itemTile.items().getStackInSlot(1).isEmpty(),"Item or fuel was not consumed");
                    require(itemTile.contents().equals(expectedItem),"Incorrect item decomposition: "+itemTile.contents()+" expected "+expectedItem);
                    var outputJar=api.accessor(FormedAccessors.World.class,world).tile(itemFurnace.getX()+1,itemFurnace.getY()-1,itemFurnace.getZ());
                    require(api.accessor(Jar.class,outputJar).amount()==0,"Furnace sent output directly to a tube without an alembic");
                    var copy=new PrimalFurnaceBlockEntity(itemFurnace,itemTile.getBlockState()); copy.load(itemTile.saveWithoutMetadata());
                    require(copy.contents().equals(expectedItem) && copy.burnFraction()>0,"Buffer or fuel did not persist");
                    require(!TcResearch.isComplete(api,mc.getSingleplayerServer().getPlayerList().getPlayers().get(0),"primal_furnace"),"Using the furnace unlocked research");
                }); stage=6;
            } else if (stage==6 && pending.isDone()) { pending.join(); finish(mc,"PASS"); }
        } catch(Throwable failure) { failure.printStackTrace(); finish(mc,"FAIL: "+failure); }
    }
    private void finish(Minecraft mc,String result) { stage=99; try { Files.writeString(mc.gameDirectory.toPath().resolve("primal-furnace-smoke.txt"),result); } catch(Exception ignored) { } mc.stop(); }
    private static void require(boolean condition,String message) { if (!condition) throw new IllegalStateException(message); }
    @LegacyType("thaumcraft.api.aspects.IAspectContainer") public interface Container {
        @LegacyMethod(name="addToContainer",descriptor="(Lthaumcraft/api/aspects/Aspect;I)I") int add(LegacyObject aspect,int amount);
    }
    @LegacyType("thaumcraft.common.tiles.TileJarFillable") public interface Jar {
        @LegacyField(name="amount",descriptor="I") int amount();
    }
    @LegacyType("thaumcraft.api.nodes.INode") public interface Node {
        @LegacyMethod(name="getAspects",descriptor="()Lthaumcraft/api/aspects/AspectList;") LegacyObject aspects();
    }
    @LegacyType("thaumcraft.api.aspects.AspectList") public interface Aspects {
        @LegacyMethod(name="getAmount",descriptor="(Lthaumcraft/api/aspects/Aspect;)I") int amount(LegacyObject aspect);
    }
}
