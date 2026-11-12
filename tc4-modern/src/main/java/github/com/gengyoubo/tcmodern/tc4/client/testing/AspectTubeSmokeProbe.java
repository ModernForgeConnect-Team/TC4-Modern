package github.com.gengyoubo.tcmodern.tc4.client.testing;

import github.com.gengyoubo.tcmodern.api.TcModernApi;
import github.com.gengyoubo.tcmodern.api.essentia.*;
import github.com.gengyoubo.tcmodern.lib.essentia.AspectCapabilities;
import java.nio.file.Files;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.GameType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.*;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Isolated opt-in acceptance with real old tubes and sided modern storage capabilities. */
@Mod.EventBusSubscriber(modid="tc4modern", value=Dist.CLIENT, bus=Mod.EventBusSubscriber.Bus.MOD)
public final class AspectTubeSmokeProbe {
    private int stage, ticks;
    private BlockPos source, sink;
    private CompletableFuture<?> pending;
    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        if (Boolean.getBoolean("tc4modern.aspectTubeSmokeTest")) event.enqueueWork(() -> {
            MinecraftForge.EVENT_BUS.addGenericListener(BlockEntity.class, AspectTubeSmokeProbe::attach);
            MinecraftForge.EVENT_BUS.addListener(new AspectTubeSmokeProbe()::tick);
        });
    }
    private static void attach(AttachCapabilitiesEvent<BlockEntity> event) {
        if (!(event.getObject() instanceof ChestBlockEntity)) return;
        var provider = new Fixture();
        event.addCapability(ResourceLocation.fromNamespaceAndPath("tc4modern", "tube_smoke"), provider);
        event.addListener(provider.value::invalidate);
    }
    private static final class Fixture implements ICapabilityProvider {
        final Port storage = new Port();
        final LazyOptional<AspectStorage> value = LazyOptional.of(() -> storage);
        @Override public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> capability, @Nullable Direction side) {
            return capability == AspectCapabilities.STORAGE && (side == storage.side || side == null) ? value.cast() : LazyOptional.empty();
        }
    }
    private static final class Port extends SimpleAspectStorage implements AspectTubePort {
        Direction side = Direction.EAST;
        boolean input;
        String filter;
        Port() { super(100); }
        @Override public boolean canReceive() { return input; }
        @Override public boolean canExtract() { return !input; }
        @Override public int suction() { return input ? 128 : 0; }
        @Override public String suctionAspect() { return filter; }
    }
    private static Port port(Level level, BlockPos pos) {
        return (Port) level.getBlockEntity(pos).getCapability(AspectCapabilities.STORAGE, null).orElseThrow(IllegalStateException::new);
    }
    private void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == 99) return;
        var mc = Minecraft.getInstance();
        try {
            if (pending != null && pending.isCompletedExceptionally()) pending.join();
            if (stage == 0 && mc.screen instanceof TitleScreen && mc.getOverlay() == null) {
                org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());
                mc.options.pauseOnLostFocus = false;
                mc.createWorldOpenFlows().createFreshLevel("aspect-tube-" + System.currentTimeMillis(),
                    new LevelSettings("Aspect tube smoke", GameType.CREATIVE, false, Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT),
                    new WorldOptions(42, false, false), a -> a.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
                stage = 1;
            } else if (stage == 1 && mc.player != null && mc.level != null && mc.screen == null) {
                pending = mc.getSingleplayerServer().submit(() -> {
                    var level = mc.getSingleplayerServer().overworld();
                    source = mc.getSingleplayerServer().getPlayerList().getPlayers().get(0).blockPosition().above(6);
                    sink = source.east(3);
                    level.setBlockAndUpdate(source, Blocks.CHEST.defaultBlockState());
                    level.setBlockAndUpdate(sink, Blocks.CHEST.defaultBlockState());
                    var tube = TcModernApi.runtime().orElseThrow().hostBlock("Thaumcraft:blockTube", 0, BlockState.class).orElseThrow();
                    level.setBlockAndUpdate(source.east(), tube); level.setBlockAndUpdate(source.east(2), tube);
                    port(level, source).side = Direction.EAST;
                    port(level, source).load(java.util.Map.of("aer", 5, "ignis", 5));
                    port(level, sink).side = Direction.EAST; port(level, sink).input = true;
                }); stage = 2;
            } else if (stage == 2 && pending.isDone()) { pending.join(); ticks = 0; stage = 3; }
            else if (stage == 3 && ++ticks >= 100) {
                pending = mc.getSingleplayerServer().submit(() -> {
                    var level = mc.getSingleplayerServer().overworld();
                    require(port(level, source).stored("ignis") == 5 && port(level, source).stored("aer") == 5 && port(level, sink).contents().isEmpty(),
                            "Closed side moved essentia");
                    port(level, sink).side = Direction.WEST; port(level, sink).filter = "aqua";
                }); stage = 4;
            } else if (stage == 4 && pending.isDone()) { pending.join(); ticks = 0; stage = 5; }
            else if (stage == 5 && ++ticks >= 100) {
                pending = mc.getSingleplayerServer().submit(() -> {
                    var level = mc.getSingleplayerServer().overworld();
                    require(port(level, sink).stored("ignis") == 0, "Filter accepted the wrong aspect");
                    port(level, sink).filter = "ignis";
                }); stage = 6;
            } else if (stage == 6 && pending.isDone()) { pending.join(); ticks = 0; stage = 7; }
            else if (stage == 7 && ++ticks >= 300) {
                pending = mc.getSingleplayerServer().submit(() -> {
                    var level = mc.getSingleplayerServer().overworld();
                    require(port(level, source).stored("ignis") == 0 && port(level, source).stored("aer") == 5
                            && port(level, sink).stored("ignis") == 5 && port(level, sink).stored("aer") == 0,
                            "Filtered transfer: " + port(level, source).contents() + " -> " + port(level, sink).contents());
                    port(level, sink).filter = null;
                }); stage = 8;
            } else if (stage == 8 && pending.isDone()) { pending.join(); ticks = 0; stage = 9; }
            else if (stage == 9 && ++ticks >= 300) {
                pending = mc.getSingleplayerServer().submit(() -> {
                    var level = mc.getSingleplayerServer().overworld();
                    require(port(level, source).contents().isEmpty() && port(level, sink).stored("ignis") == 5
                            && port(level, sink).stored("aer") == 5,
                            "Forward transfer: " + port(level, source).contents() + " -> " + port(level, sink).contents());
                    port(level, source).input = true; port(level, sink).input = false;
                }); stage = 10;
            } else if (stage == 10 && pending.isDone()) { pending.join(); ticks = 0; stage = 11; }
            else if (stage == 11 && ++ticks >= 500) {
                pending = mc.getSingleplayerServer().submit(() -> {
                    var level = mc.getSingleplayerServer().overworld();
                    require(port(level, source).stored("ignis") == 5 && port(level, source).stored("aer") == 5
                            && port(level, sink).contents().isEmpty(),
                            "Reverse transfer: " + port(level, source).contents() + " <- " + port(level, sink).contents());
                }); stage = 12;
            } else if (stage == 12 && pending.isDone()) { pending.join(); finish(mc, "PASS"); }

        } catch (Throwable failure) {
            var text = new java.io.StringWriter(); failure.printStackTrace(new java.io.PrintWriter(text)); finish(mc, text.toString());
        }
    }
    private void finish(Minecraft mc, String result) {
        stage = 99;
        try { Files.writeString(mc.gameDirectory.toPath().resolve("aspect-tube-smoke.txt"), result); } catch (Exception ignored) { }
        mc.stop();
    }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
