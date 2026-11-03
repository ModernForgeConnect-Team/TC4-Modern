package github.com.gengyoubo.tcmodern.tc4.client.testing;

import github.com.gengyoubo.mfcl.api.*;
import github.com.gengyoubo.mfcl.api.mla.*;
import github.com.gengyoubo.tcmodern.api.TcModernApi;
import github.com.gengyoubo.tcmodern.api.aspect.TcAspectAccessors;
import github.com.gengyoubo.tcmodern.tc4.client.FormedAccessors;
import github.com.gengyoubo.tcmodern.tc4.content.NodeReactorBlockEntity;
import github.com.gengyoubo.tcmodern.tc4.init.Tc4Blocks;
import java.nio.file.Files;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Opt-in in-game check of the real TC4 jar, node, and MFCL world bridge. */
@Mod.EventBusSubscriber(modid="tc4modern", value=Dist.CLIENT, bus=Mod.EventBusSubscriber.Bus.MOD)
public final class NodeReactorSmokeProbe {
    private int stage, ticks;
    private BlockPos pos;
    private CompletableFuture<?> pending;
    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        if (Boolean.getBoolean("tc4modern.nodeReactorSmokeTest"))
            event.enqueueWork(() -> MinecraftForge.EVENT_BUS.addListener(new NodeReactorSmokeProbe()::tick));
    }
    private void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == 99) return;
        var mc = Minecraft.getInstance();
        try {
            if (stage == 0 && mc.screen instanceof TitleScreen && mc.getOverlay() == null) {
                org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());
                mc.options.pauseOnLostFocus = false;
                mc.createWorldOpenFlows().createFreshLevel("reactor-smoke-" + System.currentTimeMillis(),
                    new LevelSettings("Reactor smoke", GameType.CREATIVE, false, Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT),
                    new WorldOptions(42, false, false), access -> access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
                stage = 1;
            } else if (stage == 1 && mc.player != null && mc.level != null && mc.screen == null) {
                pending = mc.getSingleplayerServer().submit(() -> {
                    var level = mc.getSingleplayerServer().overworld();
                    pos = mc.getSingleplayerServer().getPlayerList().getPlayers().get(0).blockPosition().above(5);
                    var runtime = TcModernApi.runtime().orElseThrow();
                    level.setBlockAndUpdate(pos.east(2).below(), runtime.hostBlock("Thaumcraft:blockJar", 0, BlockState.class).orElseThrow());
                    level.setBlockAndUpdate(pos, Tc4Blocks.NODE_REACTOR.get().defaultBlockState());
                    level.setBlockAndUpdate(pos.east(), runtime.hostBlock("Thaumcraft:blockTube", 0, BlockState.class).orElseThrow());
                    level.setBlockAndUpdate(pos.east(2), runtime.hostBlock("Thaumcraft:blockTube", 0, BlockState.class).orElseThrow());
                    var world = runtime.mappings().toLegacy(LegacyMappings.WORLD, level).orElseThrow();
                    var access = runtime.accessor(FormedAccessors.World.class, world);
                    var jar = access.tile(pos.getX()+2,pos.getY()-1,pos.getZ());
                    var lookup = runtime.accessor(TcAspectAccessors.AspectLookup.class);
                    require(runtime.accessor(Container.class, jar).add(lookup.find("ignis"), 10) == 0,
                            "Could not fill jar");
                    // Reproduce an already-blocked line: a compound aspect at the reactor end, fire behind it.
                    var tube = access.tile(pos.getX()+1,pos.getY(),pos.getZ());
                    var side = runtime.accessor(github.com.gengyoubo.tcmodern.tc4.content.ReactorAccessors.Directions.class).of(0);
                    require(runtime.accessor(Tube.class, tube).add(lookup.find("auram"),1,side)==1,"Could not seed compound aspect in tube");
                });
                stage = 2;
            } else if (stage == 2 && pending.isDone()) { pending.join(); stage = 3; }
            else if (stage == 3 && ++ticks >= 200) {
                pending = mc.getSingleplayerServer().submit(() -> {
                    var level = mc.getSingleplayerServer().overworld(); var runtime = TcModernApi.runtime().orElseThrow();
                    require(github.com.gengyoubo.tcmodern.tc4.content.NodeReactorBlock.hasTubeConnection(level.getBlockState(pos), Direction.EAST),
                            "Reactor did not display the tube connection");
                    var world = runtime.mappings().toLegacy(LegacyMappings.WORLD, level).orElseThrow();
                    var access = runtime.accessor(FormedAccessors.World.class, world);
                    var tube = access.tile(pos.getX()+1,pos.getY(),pos.getZ());
                    require(tube != null && runtime.accessor(Tube.class, tube).suction(null) > 0, "Tube has no reactor suction");
                    var node = access.tile(pos.getX(),pos.getY()+1,pos.getZ());
                    require(node != null && runtime.values().isInstance(node, "thaumcraft.common.tiles.TileNode"), "Node was not generated");
                    var aspect = runtime.accessor(TcAspectAccessors.AspectLookup.class).find("auram");
                    var list = runtime.accessor(Node.class, node).aspects();
                    var values = runtime.accessor(Aspects.class, list);
                    require(values.amount(aspect) > 0, "Compound essentia was blocked before reaching the node");
                    require(values.amount(runtime.accessor(TcAspectAccessors.AspectLookup.class).find("ignis")) > 0,
                            "Fire did not reach the node after the compound aspect");
                    values.reduce(aspect, values.amount(aspect));
                    level.removeBlock(pos.east(), false);
                    level.removeBlock(pos.east(2), false);
                    level.removeBlock(pos.east(2).below(), false);
                    var reactor = (NodeReactorBlockEntity) level.getBlockEntity(pos);
                    for (int i=0; i<1250; i++) {
                        NodeReactorBlockEntity.tick(level, pos, level.getBlockState(pos), reactor);
                        runtime.accessor(Tile.class, node).tick();
                    }
                    require(values.amount(aspect) == 0, "Node recharged itself");
                    require(access.tile(pos.getX(),pos.getY()+1,pos.getZ()) != null, "Node disappeared after depletion");
                });
                stage = 4;
            } else if (stage == 4 && pending.isDone()) { pending.join(); finish(mc, "PASS"); }
        } catch (Throwable failure) { failure.printStackTrace(); finish(mc, "FAIL: " + failure); }
    }
    private void finish(Minecraft mc, String result) {
        stage = 99;
        try { Files.writeString(mc.gameDirectory.toPath().resolve("node-reactor-smoke.txt"), result); } catch (Exception ignored) { }
        mc.stop();
    }
    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
    @LegacyType("thaumcraft.api.aspects.IAspectContainer") public interface Container {
        @LegacyMethod(name="addToContainer", descriptor="(Lthaumcraft/api/aspects/Aspect;I)I") int add(LegacyObject aspect, int amount);
    }
    @LegacyType("thaumcraft.api.nodes.INode") public interface Node {
        @LegacyMethod(name="getAspects", descriptor="()Lthaumcraft/api/aspects/AspectList;") LegacyObject aspects();
    }
    @LegacyType("thaumcraft.api.aspects.AspectList") public interface Aspects {
        @LegacyMethod(name="getAmount", descriptor="(Lthaumcraft/api/aspects/Aspect;)I") int amount(LegacyObject aspect);
        @LegacyMethod(name="reduce", descriptor="(Lthaumcraft/api/aspects/Aspect;I)Z") boolean reduce(LegacyObject aspect, int amount);
    }
    @LegacyType("net.minecraft.tileentity.TileEntity") public interface Tile {
        @LegacyMethod(name="func_145845_h", descriptor="()V") void tick();
    }
    @LegacyType("thaumcraft.api.aspects.IEssentiaTransport") public interface Tube {
        @LegacyMethod(name="getSuctionAmount", descriptor="(Lnet/minecraftforge/common/util/ForgeDirection;)I") int suction(LegacyObject side);
        @LegacyMethod(name="addEssentia", descriptor="(Lthaumcraft/api/aspects/Aspect;ILnet/minecraftforge/common/util/ForgeDirection;)I") int add(LegacyObject aspect,int amount,LegacyObject side);
    }
}
