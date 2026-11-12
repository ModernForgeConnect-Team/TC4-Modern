package github.com.gengyoubo.tcmodern.tc4.client.testing;

import github.com.gengyoubo.mfcl.api.*;
import github.com.gengyoubo.mfcl.api.mla.*;
import github.com.gengyoubo.tcmodern.api.TcModernApi;
import github.com.gengyoubo.tcmodern.api.essentia.TcContainers;
import github.com.gengyoubo.tcmodern.api.node.TcNodes;
import github.com.gengyoubo.tcmodern.api.wand.*;
import github.com.gengyoubo.tcmodern.api.registration.TcRegistrations;
import java.nio.file.Files;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Opt-in real-TC4 smoke test of read-only APIs and isolated registration. */
@Mod.EventBusSubscriber(modid="tc4modern", value=Dist.CLIENT, bus=Mod.EventBusSubscriber.Bus.MOD)
public final class LibApiSmokeProbe {
    @LegacyType("thaumcraft.common.tiles.TileNode") public interface NodeFactory {
        @LegacyConstructor(descriptor="()V") LegacyObject create();
    }
    @LegacyType("thaumcraft.common.tiles.TileNode") public interface NodeId {
        @LegacyField(name="id", descriptor="Ljava/lang/String;") void set(String id);
    }
    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        if (Boolean.getBoolean("tc4modern.libApiSmokeTest")) event.enqueueWork(() -> MinecraftForge.EVENT_BUS.addListener(LibApiSmokeProbe::tick));
    }
    private static boolean done;
    private static void tick(TickEvent.ClientTickEvent event) {
        var mc = Minecraft.getInstance();
        if (done || event.phase != TickEvent.Phase.END || !(mc.screen instanceof TitleScreen) || mc.getOverlay() != null || TcModernApi.runtime().isEmpty()) return;
        done = true;
        try {
            var runtime = TcModernApi.runtime().orElseThrow();
            var rods = TcWandParts.rods(runtime); var caps = TcWandParts.caps(runtime);
            if (rods.stream().noneMatch(r -> r.tag().equals("wood")) || caps.stream().noneMatch(c -> c.tag().equals("iron"))) throw new AssertionError("Missing TC4 wand parts");
            var wand = new LegacyStack("Thaumcraft:WandCasting", 0, 1, "{cap:\"iron\",rod:\"wood\",aer:100}");
            var vis = TcWandVis.snapshot(runtime, wand).orElseThrow();
            if (vis.amounts().getOrDefault("aer", 0) != 100 || vis.capacity() <= 0) throw new AssertionError("Incorrect Vis snapshot: " + vis);
            var node = runtime.accessor(NodeFactory.class).create();
            runtime.accessor(NodeId.class, node).set("smoke-test");
            if (TcNodes.snapshot(runtime, node).isEmpty() || TcContainers.block(runtime, node).isEmpty()) throw new AssertionError("Node not recognized");
            if (TcContainers.item(runtime, runtime.stack(wand)).isPresent()) throw new AssertionError("Wand is not an essentia item container");
            TcRegistrations.loot(runtime, new LegacyStack("Thaumcraft:ItemResource", 2, 1, ""), 1, TcRegistrations.LootBag.COMMON);
            var path = mc.gameDirectory.toPath().resolve("logs/tc4modern/lib-api-smoke.txt"); Files.createDirectories(path.getParent());
            Files.writeString(path, "PASS rods=" + rods.size() + " caps=" + caps.size() + " vis=" + vis + "\n");
        } catch (Throwable failure) {
            try {
                var path = mc.gameDirectory.toPath().resolve("logs/tc4modern/lib-api-smoke.txt"); Files.createDirectories(path.getParent());
                var out = new java.io.StringWriter(); failure.printStackTrace(new java.io.PrintWriter(out)); Files.writeString(path, out.toString());
            } catch (Exception ignored) { }
        }
        mc.stop();
    }
}
