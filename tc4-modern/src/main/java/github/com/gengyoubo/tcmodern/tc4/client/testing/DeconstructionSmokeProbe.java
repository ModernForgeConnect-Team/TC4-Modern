package github.com.gengyoubo.tcmodern.tc4.client.testing;

import github.com.gengyoubo.tcmodern.api.TcModernApi;
import github.com.gengyoubo.tcmodern.api.aspect.TcPlayerAspects;
import github.com.gengyoubo.tcmodern.api.research.TcResearch;
import github.com.gengyoubo.tcmodern.tc4.client.deconstruction.ModernDeconstructionScreen;
import github.com.gengyoubo.tcmodern.tc4.content.ModernDeconstructionBlockEntity;
import github.com.gengyoubo.tcmodern.tc4.init.Tc4Blocks;
import github.com.gengyoubo.tcmodern.tc4.menu.ModernDeconstructionMenu;
import java.nio.file.Files;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Opt-in integrated-server check for the native deconstruction menu. */
@Mod.EventBusSubscriber(modid="tc4modern", value=Dist.CLIENT, bus=Mod.EventBusSubscriber.Bus.MOD)
public final class DeconstructionSmokeProbe {
    private int stage;
    private final long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.MINUTES.toNanos(4);
    private BlockPos pos;
    private CompletableFuture<?> pending;
    private int terraBefore;
    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        if (Boolean.getBoolean("tc4modern.deconstructionSmokeTest"))
            event.enqueueWork(() -> MinecraftForge.EVENT_BUS.addListener(new DeconstructionSmokeProbe()::tick));
    }
    private void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == 99) return;
        var mc=Minecraft.getInstance();
        try {
            require(System.nanoTime()<deadline,"Timed out at stage "+stage);
            if (pending != null && pending.isCompletedExceptionally()) pending.join();
            if (stage == 0 && mc.screen instanceof TitleScreen && mc.getOverlay() == null) {
                org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow()); mc.options.pauseOnLostFocus=false; mc.options.renderDistance().set(4);
                mc.createWorldOpenFlows().createFreshLevel("deconstruction-smoke-"+System.currentTimeMillis(),
                        new LevelSettings("Deconstruction smoke",GameType.SURVIVAL,false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),
                        new WorldOptions(42,false,false), access -> access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
                stage=1;
            } else if (stage == 1 && mc.player != null && mc.level != null && mc.screen == null) {
                pending=mc.getSingleplayerServer().submit(() -> {
                    var server=mc.getSingleplayerServer(); var level=server.overworld(); var player=server.getPlayerList().getPlayers().get(0);
                    pos=player.blockPosition().above(2); player.teleportTo(pos.getX()+0.5,pos.getY(),pos.getZ()+2.5);
                    level.setBlockAndUpdate(pos,Tc4Blocks.DECONSTRUCTION_Y.get().defaultBlockState());
                    var table=(ModernDeconstructionBlockEntity)level.getBlockEntity(pos);
                    net.minecraftforge.network.NetworkHooks.openScreen(player,table,pos);
                }); stage=2;
            } else if (stage == 2 && pending.isDone() && mc.screen instanceof ModernDeconstructionScreen) {
                pending.join(); pending=mc.getSingleplayerServer().submit(() -> {
                    var server=mc.getSingleplayerServer(); var player=server.getPlayerList().getPlayers().get(0); var runtime=TcModernApi.runtime().orElseThrow();
                    var table=(ModernDeconstructionBlockEntity)server.overworld().getBlockEntity(pos);
                    require(player.containerMenu instanceof ModernDeconstructionMenu,"Wrong server menu");
                    var menu=(ModernDeconstructionMenu)player.containerMenu;
                    require(!TcResearch.isComplete(runtime,player,"deconstruction_y"),"Fixture unexpectedly has the research");
                    terraBefore=TcPlayerAspects.pool(runtime,player,"terra");
                    table.items().setStackInSlot(0,new ItemStack(Items.STONE,2));
                    require(table.items().getStackInSlot(0).getCount()==2 && table.pendingCount()==0,"Input was consumed without processing time");
                    ModernDeconstructionBlockEntity.tick(server.overworld(),pos,table.getBlockState(),table);
                    require(table.progress()==1 && table.items().getStackInSlot(0).getCount()==2,"Progress did not start before consumption");
                    var unfinished=new ModernDeconstructionBlockEntity(pos,table.getBlockState()); unfinished.load(table.saveWithoutMetadata());
                    require(unfinished.progress()==1 && unfinished.items().getStackInSlot(0).getCount()==2,"Unfinished work did not persist");
                    table.items().setStackInSlot(0,ItemStack.EMPTY);
                    require(table.progress()==0,"Removing input did not reset progress");
                    table.items().setStackInSlot(0,new ItemStack(Items.STONE,2));
                }); stage=3;
            } else if (stage == 3 && pending.isDone() && mc.screen instanceof ModernDeconstructionScreen screen && screen.getMenu().hasPending()) {
                pending.join(); require(!screen.getMenu().results().isEmpty(),"Pending aspects were not shown to the client");
                pending=mc.getSingleplayerServer().submit(() -> {
                    var server=mc.getSingleplayerServer(); var player=server.getPlayerList().getPlayers().get(0); var runtime=TcModernApi.runtime().orElseThrow();
                    var table=(ModernDeconstructionBlockEntity)server.overworld().getBlockEntity(pos);
                    require(table.items().getStackInSlot(0).getCount()==1 && table.pendingCount()>0,"Unresearched player could not use the table");
                    var copy=new ModernDeconstructionBlockEntity(pos,table.getBlockState()); copy.load(table.saveWithoutMetadata());
                    require(copy.items().getStackInSlot(0).getCount()==1 && copy.pendingCount()==table.pendingCount(),"Input or result did not persist");
                    ((ModernDeconstructionMenu)player.containerMenu).clickMenuButton(player,1);
                    require(table.pendingCount()==0 && TcPlayerAspects.pool(runtime,player,"terra")>terraBefore,"Claim did not credit research");
                    require(!TcResearch.isComplete(runtime,player,"deconstruction_y"),"Using the table unlocked its research");
                }); stage=4;
            } else if (stage == 4 && pending.isDone()) { pending.join(); finish(mc,"PASS"); }
        } catch(Throwable failure) { failure.printStackTrace(); finish(mc,"FAIL: "+failure); }
    }
    private static void require(boolean value,String message) { if (!value) throw new IllegalStateException(message); }
    private void finish(Minecraft mc,String result) {
        stage=99; try { Files.writeString(mc.gameDirectory.toPath().resolve("deconstruction-smoke.txt"),result); } catch(Exception ignored) { }
        mc.stop();
    }
}
