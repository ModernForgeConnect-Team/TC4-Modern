package github.com.gengyoubo.tcmodern.tc4.client.testing;

import com.mojang.logging.LogUtils;
import dev.ftb.mods.ftbquests.quest.*;
import dev.ftb.mods.ftbquests.quest.task.Task;
import dev.ftb.mods.ftbquests.quest.task.TaskType;
import dev.ftb.mods.ftbquests.quest.reward.RewardType;
import dev.ftb.mods.ftblibrary.config.ConfigGroup;
import dev.ftb.mods.ftblibrary.config.EnumConfig;
import dev.ftb.mods.ftblibrary.ui.input.MouseButton;
import dev.ftb.mods.ftblibrary.util.TooltipList;
import github.com.gengyoubo.tcmodern.api.TcModernApi;
import github.com.gengyoubo.tcmodern.api.aspect.TcPlayerAspects;
import github.com.gengyoubo.tcmodern.api.research.TcResearch;
import github.com.gengyoubo.tcmodern.tc4.compat.ftbquests.*;
import io.netty.buffer.Unpooled;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

/** Opt-in acceptance in a disposable world, with real FTB progress and original TC4 player transactions. */
public final class FtbQuestsSmokeProbe {
    private final Map<String, Object> proof = new LinkedHashMap<>();
    private final long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(5);
    private int stage;
    private CompletableFuture<?> pending;
    private ResearchTask researchTask;
    private AspectTask aspectTask;
    private int frames;

    public static void install() {
        MinecraftForge.EVENT_BUS.addListener(new FtbQuestsSmokeProbe()::tick);
    }

    private void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == 99) return;
        var mc = Minecraft.getInstance();
        try {
            require(System.nanoTime() < deadline, "FTB acceptance timed out at " + stage);
            if (stage == 0 && mc.screen instanceof TitleScreen && mc.getOverlay() == null) {
                org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());
                mc.options.pauseOnLostFocus = false;
                mc.options.renderDistance().set(2);
                mc.createWorldOpenFlows().createFreshLevel("tc4modern-ftb-smoke-" + System.currentTimeMillis(),
                        new LevelSettings("FTB compatibility", GameType.CREATIVE, false, Difficulty.PEACEFUL,
                                true, new GameRules(), WorldDataConfiguration.DEFAULT),
                        new WorldOptions(42, false, false), access -> access.registryOrThrow(Registries.WORLD_PRESET)
                                .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
                stage = 1;
            } else if (stage == 1 && mc.level != null && mc.player != null && mc.screen == null) {
                var server = mc.getSingleplayerServer();
                var playerId = mc.player.getUUID();
                pending = server.submit(() -> validate(server.getPlayerList().getPlayer(playerId)));
                stage = 2;
            } else if (stage == 2 && pending.isDone()) {
                pending.join();
                var fresh = new ConfigGroup("fresh");
                new ResearchTask(0, researchTask.getQuest()).fillConfigGroup(fresh);
                var unset = selector(fresh, "research");
                unset.addInfo(new TooltipList());
                require(unset.nameMap.defaultValue != null && unset.getDefaultValue() != null,
                        "Unconfigured research has a null FTB default");
                unset.setCurrentValue(unset.getDefaultValue()); unset.applyValue();
                proof.put("unconfiguredResearchDefaultTooltipDoesNotCrash", true);
                new dev.ftb.mods.ftblibrary.config.ui.EditConfigScreen(fresh).openGui();
                stage = 5; frames = 0;
            } else if (stage == 5 && ++frames > 4) {
                var screen = (dev.ftb.mods.ftblibrary.ui.ScreenWrapper) mc.screen;
                var entries = new ArrayList<dev.ftb.mods.ftblibrary.ui.Widget>();
                configEntries(screen.getGui(), entries);
                require(!entries.isEmpty(), "FTB editor did not create configuration buttons");
                for (int i = 0; i < entries.size(); i++) {
                    var entry = entries.get(i);
                    screenshot(mc, "new-research-hover-" + i, entry.getX() + entry.getWidth() / 2,
                            entry.getY() + entry.getHeight() / 2);
                }
                proof.put("ftbEditorHoverRendersForNewResearch", true);
                var config = new ConfigGroup("smoke"); researchTask.fillConfigGroup(config);
                var select = selector(config, "research");
                select.addInfo(new TooltipList());
                require(select.nameMap.getName("NITOR").equals("NITOR"), "Research selector changed case-sensitive keys");
                require(!QuestChoices.research("NITOR").icon().isEmpty(), "Research icon missing");
                select.onClicked(null, MouseButton.LEFT, accepted -> { });
                stage = 3; frames = 0;
            } else if (stage == 3 && ++frames > 4) {
                screenshot(mc, "research-selection");
                var config = new ConfigGroup("smoke"); aspectTask.fillConfigGroup(config);
                var select = selector(config, "aspect");
                select.addInfo(new TooltipList());
                require(select.nameMap.size() > 16 && !select.nameMap.getIcon("ignis").isEmpty(), "Aspect selector icons missing");
                require(!select.nameMap.getDisplayName("ignis").getString().equals("tc.aspect.ignis"), "Aspect names are untranslated");
                select.setCurrentValue("ignis"); select.applyValue();
                var saved = new CompoundTag(); aspectTask.writeData(saved);
                require(saved.getString("aspect").equals("ignis"), "Choice did not save the aspect tag");
                select.onClicked(null, MouseButton.LEFT, accepted -> { });
                proof.put("searchableRegistryChoicesWithLocalizedNamesAndIcons", true);
                stage = 4; frames = 0;
            } else if (stage == 4 && ++frames > 4) {
                screenshot(mc, "aspect-selection"); finish(mc, null);
            }
        } catch (Exception | AssertionError failure) { finish(mc, failure); }
    }

    private void validate(ServerPlayer player) {
        var api = TcModernApi.runtime().orElseThrow();
        var file = Objects.requireNonNull(ServerQuestFile.INSTANCE);
        var data = file.getOrCreateTeamData(player);
        var chapter = new Chapter(file.newID(), file, file.getDefaultChapterGroup());
        chapter.onCreated();
        var quest = new Quest(file.newID(), chapter);
        quest.onCreated();

        var research = (ResearchTask) TaskType.createTask(file.newID(), quest, "tc4modern:research");
        var config = new CompoundTag(); config.putString("research", "NITOR"); research.readData(config); research.onCreated();
        var hold = aspect(file, quest, "aer", 3, false);
        researchTask = research; aspectTask = hold;
        var consume = aspect(file, quest, "aer", 6, true);
        var reward = (AspectReward) RewardType.createReward(file.newID(), quest, "tc4modern:aspect");
        var grant = new CompoundTag(); grant.putString("aspect", "aer"); grant.putInt("amount", 9);
        reward.readData(grant); reward.onCreated(); file.refreshIDMap();
        proof.put("registeredTaskAndRewardTypes", true);

        file.withPlayerContext(player, () -> {
            research.submitTask(data, player);
            require(data.getProgress(research) == 0, "Locked research counted as unlocked");
            TcResearch.complete(api, player, "NITOR");
            research.submitTask(data, player);
            require(data.isCompleted(research), "Unlocked research did not complete task");
            proof.put("requiresOriginalUnlockedResearch", true);

            int start = TcPlayerAspects.pool(api, player, "aer");
            TcPlayerAspects.give(api, player, Map.of("aer", 10));
            int funded = TcPlayerAspects.pool(api, player, "aer");
            require(funded == start + 10, "Initial pool grant differs");
            hold.submitTask(data, player);
            require(data.isCompleted(hold) && TcPlayerAspects.pool(api, player, "aer") == funded,
                    "Non-consuming task changed pool");
            require(hold.autoSubmitOnPlayerTick() == 20 && consume.autoSubmitOnPlayerTick() == 0,
                    "Consumption must require manual submission");
            data.setLocked(true); consume.submitTask(data, player); data.setLocked(false);
            require(TcPlayerAspects.pool(api, player, "aer") == funded, "Locked team was charged");
            consume.submitTask(data, player); consume.submitTask(data, player);
            require(data.isCompleted(consume) && TcPlayerAspects.pool(api, player, "aer") == funded - 6,
                    "Repeated submission charged more than once");
            proof.put("nonConsumingAndExactlyOnceConsumingTasks", true);

            var tooMuch = aspect(file, quest, "aer", 32767, true); file.refreshIDMap();
            tooMuch.submitTask(data, player);
            require(data.getProgress(tooMuch) == 0 && TcPlayerAspects.pool(api, player, "aer") == funded - 6,
                    "Insufficient pool was charged");
            var unknown = aspect(file, quest, "not_registered", 1, true); file.refreshIDMap();
            unknown.submitTask(data, player); require(data.getProgress(unknown) == 0, "Unknown aspect passed");
            proof.put("insufficientAndUnknownAspectsDoNotConsume", true);

            data.claimReward(player, reward, false); data.claimReward(player, reward, false);
            require(TcPlayerAspects.pool(api, player, "aer") == funded + 3, "Reward was missing or granted twice");
            proof.put("ftbClaimGrantsOriginalPoolExactlyOnce", true);

            roundTrip(consume, new AspectTask(file.newID(), quest));
            roundTrip(research, new ResearchTask(file.newID(), quest));
            var buf = new FriendlyByteBuf(Unpooled.buffer());
            try {
                reward.writeNetData(buf); var copy = new AspectReward(file.newID(), quest); copy.readNetData(buf);
                var saved = new CompoundTag(); copy.writeData(saved);
                require(saved.getString("aspect").equals("aer") && saved.getInt("amount") == 9, "Reward network round trip differs");
            } finally { buf.release(); }
            proof.put("questConfigurationNetworkRoundTrips", true);
        });
    }

    private static AspectTask aspect(BaseQuestFile file, Quest quest, String aspect, int amount, boolean consume) {
        var task = (AspectTask) TaskType.createTask(file.newID(), quest, "tc4modern:aspect");
        var nbt = new CompoundTag(); nbt.putString("aspect", aspect); nbt.putInt("amount", amount); nbt.putBoolean("consume", consume);
        task.readData(nbt); task.onCreated(); return task;
    }
    private static void roundTrip(Task source, Task target) {
        var buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            source.writeNetData(buf); target.readNetData(buf);
            var expected = new CompoundTag(); source.writeData(expected);
            var actual = new CompoundTag(); target.writeData(actual);
            require(expected.equals(actual), "Task network round trip differs");
        } finally { buf.release(); }
    }
    @SuppressWarnings("unchecked")
    private static EnumConfig<String> selector(ConfigGroup group, String field) {
        return (EnumConfig<String>) group.getValues().stream().filter(v -> v.id.equals(field)).findFirst().orElseThrow();
    }
    private static void configEntries(dev.ftb.mods.ftblibrary.ui.Panel panel, List<dev.ftb.mods.ftblibrary.ui.Widget> entries) {
        for (var widget : panel.getWidgets()) {
            if (widget.getClass().getSimpleName().equals("ConfigEntryButton")) entries.add(widget);
            if (widget instanceof dev.ftb.mods.ftblibrary.ui.Panel child) configEntries(child, entries);
        }
    }
    private static void screenshot(Minecraft mc, String name) throws Exception {
        screenshot(mc, name, 0, 0);
    }
    private static void screenshot(Minecraft mc, String name, int mouseX, int mouseY) throws Exception {
        mc.getMainRenderTarget().bindWrite(true);
        com.mojang.blaze3d.systems.RenderSystem.backupProjectionMatrix();
        com.mojang.blaze3d.systems.RenderSystem.setProjectionMatrix(new org.joml.Matrix4f().setOrtho(0,
                mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(), 0, 1000, 3000),
                com.mojang.blaze3d.vertex.VertexSorting.ORTHOGRAPHIC_Z);
        var model = com.mojang.blaze3d.systems.RenderSystem.getModelViewStack();
        model.pushPose(); model.setIdentity(); model.translate(0, 0, -2000);
        com.mojang.blaze3d.systems.RenderSystem.applyModelViewMatrix();
        try {
            com.mojang.blaze3d.systems.RenderSystem.clear(256, Minecraft.ON_OSX);
            var graphics = new net.minecraft.client.gui.GuiGraphics(mc, mc.renderBuffers().bufferSource());
            graphics.fill(0, 0, mc.screen.width, mc.screen.height, 0xff303030); graphics.flush();
            net.minecraftforge.client.ForgeHooksClient.drawScreen(mc.screen, graphics, mouseX, mouseY, 0); graphics.flush();
            var path = mc.gameDirectory.toPath().resolve("screenshots/ftb-" + name + ".png");
            Files.createDirectories(path.getParent());
            try (var image = net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())) { image.writeToFile(path); }
        } finally {
            model.popPose(); com.mojang.blaze3d.systems.RenderSystem.applyModelViewMatrix();
            com.mojang.blaze3d.systems.RenderSystem.restoreProjectionMatrix(); mc.getMainRenderTarget().unbindWrite();
        }
    }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
    private void finish(Minecraft mc, Throwable failure) {
        stage = 99; proof.put("status", failure == null ? "PASS" : "FAIL");
        if (failure != null) { proof.put("failure", failure.toString()); LogUtils.getLogger().error("TC4_FTB_SMOKE_FAIL", failure); }
        try {
            var path = mc.gameDirectory.toPath().resolve("logs/tc4modern/ftb-quests-smoke.json");
            Files.createDirectories(path.getParent());
            Files.writeString(path, new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(proof));
        } catch (java.io.IOException error) { throw new IllegalStateException(error); }
        LogUtils.getLogger().info("TC4_FTB_SMOKE_RESULT: {}", proof);
        if (mc.level != null) { mc.level.disconnect(); mc.clearLevel(); }
        mc.stop();
    }
}
