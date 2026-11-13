package github.com.gengyoubo.tcmodern.tc4.client.testing;

import net.minecraft.client.Screenshot;
import com.mojang.logging.LogUtils;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.IntUnaryOperator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Opt-in acceptance test. Does nothing in normal games and links no optional JEI types. */
@Mod.EventBusSubscriber(modid = "tc4modern", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class JeiSmokeProbe {
    private static volatile IntUnaryOperator showCategory;
    private static boolean createdTagFixture;
    private boolean wandPaymentTested;
    private final long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(5);
    private final Map<String, Object> proof = new LinkedHashMap<>();
    private final String[] categories = {"arcane", "crucible", "infusion", "item_in_fluid", "multiblock", "modern_desk_upgrade"};
    private int stage, category, frames;
    private java.util.concurrent.CompletableFuture<Map<String, Integer>> command;

    public static void ready(IntUnaryOperator callback) { showCategory = callback; }

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        if (Boolean.getBoolean("tc4modern.jeiSmokeTest")) {
            try {
                var file = net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get().resolve("tc4modern/tc4_recipes/tc4jeitest/tag_shapeless.json");
                Files.createDirectories(file.getParent());
                Files.writeString(file, """
                        {"type":"tc4modern:arcane_shapeless","research":"BASICARTIFACE",
                         "ingredients":[{"tag":"forge:ingots/iron"},{"tag":"minecraft:wooden_slabs"}],
                         "aspects":{"terra":42},"result":{"item":"minecraft:amethyst_shard"}}
                        """, java.nio.file.StandardOpenOption.CREATE_NEW);
                createdTagFixture = true;
            } catch (java.io.IOException failure) { throw new IllegalStateException(failure); }
            event.enqueueWork(() -> MinecraftForge.EVENT_BUS.addListener(new JeiSmokeProbe()::tick));
        }
    }

    private void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == 99) return;
        if (Boolean.getBoolean("tc4modern.multiblockSmokeTest") && !MultiblockSmokeProbe.completed) return;
        var mc = Minecraft.getInstance();
        try {
            if (System.nanoTime() > deadline) throw new IllegalStateException("JEI smoke timed out at " + stage);
            if (stage == 0 && mc.screen instanceof TitleScreen && mc.getOverlay() == null) {
                org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());
                mc.options.pauseOnLostFocus = false;
                mc.options.renderDistance().set(2);
                mc.createWorldOpenFlows().createFreshLevel("tc4modern-jei-smoke-" + System.currentTimeMillis(),
                        new LevelSettings("TC4 Modern JEI", GameType.CREATIVE, false, Difficulty.PEACEFUL,
                                true, new GameRules(), WorldDataConfiguration.DEFAULT),
                        new WorldOptions(42, false, false), access -> access.registryOrThrow(Registries.WORLD_PRESET)
                                .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
                stage = 1;
            } else if (stage == 1 && mc.player != null && mc.level != null && mc.screen == null && showCategory != null) {
                if (!wandPaymentTested) {
                    var ok = mc.getSingleplayerServer().submit(() -> {
                        var runtime = github.com.gengyoubo.tcmodern.api.TcModernApi.runtime().orElseThrow();
                        var player = mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);
                        var hand = net.minecraft.world.InteractionHand.MAIN_HAND;
                        var previous = player.getItemInHand(hand);
                        var modern = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STICK);
                        var proxy = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BLAZE_ROD);
                        var syncCount = new java.util.concurrent.atomic.AtomicInteger();
                        var adapter = new github.com.gengyoubo.tcmodern.lib.wand.TcWandInteractions.Adapter() {
                            public net.minecraft.world.item.ItemStack proxy(net.minecraft.world.item.ItemStack stack) { return proxy; }
                            public boolean isProxy(net.minecraft.world.item.ItemStack stack) { return stack == proxy; }
                            public void sync(net.minecraft.world.item.ItemStack source, net.minecraft.world.item.ItemStack target) {
                                if (source != proxy || target != modern) throw new IllegalStateException("Wrong proxy sync");
                                syncCount.incrementAndGet();
                            }
                        };
                        try {
                            player.setItemInHand(hand, modern);
                            try {
                                github.com.gengyoubo.tcmodern.lib.wand.TcWandInteractions.withProxy(player, hand, modern, adapter, stack -> {
                                    if (player.getItemInHand(hand) != proxy) throw new AssertionError("Proxy not held");
                                    throw new IllegalStateException("expected test exception");
                                });
                                return false;
                            } catch (IllegalStateException expected) {
                                if (!"expected test exception".equals(expected.getMessage())) throw expected;
                            }
                            if (player.getItemInHand(hand) != modern || syncCount.get() != 1) return false;
                        } finally { player.setItemInHand(hand, previous); }
                        var original = new github.com.gengyoubo.mfcl.api.LegacyStack("Thaumcraft:WandCasting", 0, 1,
                                "{cap:\"iron\",rod:\"wood\",aer:1000}");
                        var host = runtime.mappings().toHost(github.com.gengyoubo.mfcl.api.LegacyMappings.ITEM_STACK,
                                runtime.stack(original), net.minecraft.world.item.ItemStack.class).orElseThrow();
                        if (!github.com.gengyoubo.tcmodern.lib.wand.TcWandCrafting.canCraftWith(host)
                                || !github.com.gengyoubo.tcmodern.lib.wand.TcWandCrafting.isNormalWand(host)) return false;
                        var staffOld = new github.com.gengyoubo.mfcl.api.LegacyStack("Thaumcraft:WandCasting", 0, 1,
                                "{cap:\"iron\",rod:\"greatwood_staff\",aer:1000}");
                        var staff = runtime.mappings().toHost(github.com.gengyoubo.mfcl.api.LegacyMappings.ITEM_STACK,
                                runtime.stack(staffOld), net.minecraft.world.item.ItemStack.class).orElseThrow();
                        if (github.com.gengyoubo.tcmodern.lib.wand.TcWandCrafting.canCraftWith(staff)
                                || github.com.gengyoubo.tcmodern.lib.wand.TcWandCrafting.pay(player, staff, Map.of("aer", 100), true).isPresent()) return false;
                        var simulated = github.com.gengyoubo.tcmodern.lib.wand.TcWandCrafting.pay(player, host, Map.of("aer", 100), true).orElseThrow();
                        var paid = github.com.gengyoubo.tcmodern.lib.wand.TcWandCrafting.pay(player, host, Map.of("aer", 100), false).orElseThrow();
                        var originalAfter = runtime.snapshot(runtime.mappings().toLegacy(github.com.gengyoubo.mfcl.api.LegacyMappings.ITEM_STACK, host).orElseThrow());
                        var simulation = runtime.snapshot(runtime.mappings().toLegacy(github.com.gengyoubo.mfcl.api.LegacyMappings.ITEM_STACK, simulated).orElseThrow());
                        var charged = runtime.snapshot(runtime.mappings().toLegacy(github.com.gengyoubo.mfcl.api.LegacyMappings.ITEM_STACK, paid).orElseThrow());
                        var before = github.com.gengyoubo.tcmodern.api.wand.TcWandVis.snapshot(runtime, originalAfter).orElseThrow().amounts().get("aer");
                        var dry = github.com.gengyoubo.tcmodern.api.wand.TcWandVis.snapshot(runtime, simulation).orElseThrow().amounts().get("aer");
                        var after = github.com.gengyoubo.tcmodern.api.wand.TcWandVis.snapshot(runtime, charged).orElseThrow().amounts().get("aer");
                        return before == 1000 && dry == 1000 && after > 0 && after < before;
                    }).get(15, TimeUnit.SECONDS);
                    if (!ok) throw new IllegalStateException("TC4 wand simulation or payment failed");
                    proof.put("wandPayment", true); wandPaymentTested = true;
                }
                proof.put(categories[category], showCategory.applyAsInt(category));
                frames = 0;
                stage = 2;
            } else if (stage == 2 && ++frames >= 30) {
                Screenshot.grab(mc.gameDirectory, "tc4modern-jei-" + categories[category] + ".png",
                        mc.getMainRenderTarget(), message -> LogUtils.getLogger().info("{}", message.getString()));
                category++;
                if (category < categories.length) {
                    proof.put(categories[category], showCategory.applyAsInt(category));
                    frames = 0;
                } else { frames = 0; stage = 3; }
            } else if (stage == 3 && ++frames >= 20) {
                for (var name : categories) {
                    if (!Files.isRegularFile(mc.gameDirectory.toPath().resolve("screenshots/tc4modern-jei-" + name + ".png")))
                        throw new IllegalStateException("Missing screenshot for " + name);
                }
                var aspects = new LinkedHashMap<String, Object>();
                for (var aspect : github.com.gengyoubo.tcmodern.api.aspect.TcAspects.snapshot(
                        github.com.gengyoubo.tcmodern.api.TcModernApi.runtime().orElseThrow()))
                    if (List.of("antiquitas", "modernitas", "echo").contains(aspect.tag())) aspects.put(aspect.tag(), aspect.components());
                if (aspects.size() != 3) throw new IllegalStateException("Missing TC4: Modern aspects: " + aspects);
                proof.put("modernAspects", aspects);
                proof.put("allLayoutsCreated", true);
                proof.put("recipeAndUsageLookups", true);
                var server = mc.getSingleplayerServer();
                command = server.submit(() -> {
                    var player = server.getPlayerList().getPlayers().get(0);
                    var source = player.createCommandSourceStack().withPermission(4).withSuppressedOutput();
                    server.getCommands().performPrefixedCommand(source, "tc4modern aspect give @s modernitas 7");
                    server.getCommands().performPrefixedCommand(source, "tc4modern aspect give @s all 2");
                    // A fresh world starts with empty pools: modernitas 7 + 2 + 1, echo 2 + 1.
                    return github.com.gengyoubo.tcmodern.api.aspect.TcPlayerAspects.give(
                            github.com.gengyoubo.tcmodern.api.TcModernApi.runtime().orElseThrow(), player, Map.of("modernitas", 1, "echo", 1));
                });
                stage = 4;
            } else if (stage == 4 && command.isDone()) {
                var totals = command.join();
                proof.put("aspectCommand", totals);
                if (!Map.of("modernitas", 10, "echo", 3).equals(Map.copyOf(totals)))
                    throw new IllegalStateException("Unexpected aspect pool after /tc4modern aspect give: " + totals);
                finish(mc, null);
            }
        } catch (Exception | AssertionError failure) { finish(mc, failure); }
    }

    private void finish(Minecraft mc, Throwable failure) {
        stage = 99;
        proof.put("status", failure == null ? "PASS" : "FAIL");
        if (failure != null) {
            proof.put("failure", failure.toString());
            LogUtils.getLogger().error("TC4_MODERN_JEI_SMOKE_FAIL", failure);
        }
        try {
            if (createdTagFixture) Files.deleteIfExists(net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get().resolve("tc4modern/tc4_recipes/tc4jeitest/tag_shapeless.json"));
            var file = mc.gameDirectory.toPath().resolve("logs/tc4modern/jei-smoke.json");
            Files.createDirectories(file.getParent());
            Files.writeString(file, new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(proof));
        } catch (java.io.IOException error) { throw new IllegalStateException(error); }
        if (mc.level != null) { mc.level.disconnect(); mc.clearLevel(); }
        mc.stop();
    }
}
