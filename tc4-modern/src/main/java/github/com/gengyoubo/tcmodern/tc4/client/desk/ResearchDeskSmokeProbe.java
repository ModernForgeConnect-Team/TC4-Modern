package github.com.gengyoubo.tcmodern.tc4.client.desk;

import com.mojang.logging.LogUtils;
import github.com.gengyoubo.mfcl.api.LegacyMappings;
import github.com.gengyoubo.mfcl.api.LegacyRuntime;
import github.com.gengyoubo.mfcl.api.LegacyStack;
import github.com.gengyoubo.tcmodern.api.TcModernApi;
import github.com.gengyoubo.tcmodern.api.aspect.TcAspects;
import github.com.gengyoubo.tcmodern.api.aspect.TcPlayerAspects;
import github.com.gengyoubo.tcmodern.api.research.TcResearch;
import github.com.gengyoubo.tcmodern.tc4.client.research.NativeResearchSmokeProbe;
import github.com.gengyoubo.tcmodern.tc4.content.ArchiveCabinetBlockEntity;
import github.com.gengyoubo.tcmodern.tc4.content.ResearchDeskBlock;
import github.com.gengyoubo.tcmodern.tc4.content.ResearchDeskBlockEntity;
import github.com.gengyoubo.tcmodern.tc4.init.Tc4Blocks;
import github.com.gengyoubo.tcmodern.tc4.research.AspectOrder;
import github.com.gengyoubo.tcmodern.tc4.research.DeskAccessors;
import github.com.gengyoubo.tcmodern.tc4.research.DeskSnapshot;
import github.com.gengyoubo.tcmodern.tc4.research.ResearchItems;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Opt-in acceptance with {@code -PresearchDeskSmokeTest}: a real world, the original notes and inkwell, the desk opened
 * through its side half, every action driven by screen clicks, and server state checked through MFCL afterwards.
 */
@Mod.EventBusSubscriber(modid = "tc4modern", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ResearchDeskSmokeProbe {
    private static final int WAIT = 12;
    private final long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(6);
    private final Map<String, Object> proof = new LinkedHashMap<>();
    private CompletableFuture<?> pending;
    private int stage, frames, writes, lastStage = -1;
    private BlockPos desk;
    private DeskSnapshot.Cell blank;
    private int airBefore, inkBefore, luxBefore, ignisBefore;

    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        if (Boolean.getBoolean("tc4modern.researchDeskSmokeTest"))
            event.enqueueWork(() -> MinecraftForge.EVENT_BUS.addListener(new ResearchDeskSmokeProbe()::tick));
    }

    private static LegacyRuntime api() { return TcModernApi.runtime().orElseThrow(); }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
    private static ServerPlayer player(Minecraft mc) { return mc.getSingleplayerServer().getPlayerList().getPlayers().get(0); }
    private CompletableFuture<?> server(Minecraft mc, Runnable action) { return mc.getSingleplayerServer().submit(action); }
    private ResearchDeskScreen screen(Minecraft mc) {
        require(mc.screen instanceof ResearchDeskScreen, "Research desk screen is not open: " + mc.screen);
        return (ResearchDeskScreen) mc.screen;
    }

    private void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == 99) return;
        var mc = Minecraft.getInstance();
        try {
            if (stage != lastStage) { lastStage = stage; frames = 0; LogUtils.getLogger().info("TC4_RESEARCH_DESK_STAGE {}", stage); }
            require(System.nanoTime() < deadline, "Research desk smoke timed out at stage " + stage);
            if (pending != null && pending.isCompletedExceptionally()) pending.join();
            if (stage == 0 && mc.screen instanceof TitleScreen && mc.getOverlay() == null) {
                org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());
                mc.options.pauseOnLostFocus = false;
                mc.options.renderDistance().set(2);
                mc.options.languageCode = "zh_cn";
                mc.getLanguageManager().setSelected("zh_cn");
                pending = mc.reloadResourcePacks();
                stage = 1;
            } else if (stage == 1 && pending.isDone() && mc.getOverlay() == null) {
                mc.createWorldOpenFlows().createFreshLevel("tc4modern-research-desk-" + System.currentTimeMillis(),
                        new LevelSettings("Research desk", GameType.SURVIVAL, false, Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT),
                        new WorldOptions(42, false, false), access -> access.registryOrThrow(Registries.WORLD_PRESET)
                                .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
                stage = 2;
            } else if (stage == 2 && mc.level != null && mc.player != null && mc.screen == null) {
                pending = server(mc, () -> seed(mc));
                stage = 3;
            } else if (stage == 3 && pending.isDone() && ++frames > WAIT) {
                // Opened through the side half: the main half's menu must answer.
                pending = server(mc, () -> use(mc, ResearchDeskBlock.partner(desk, mc.getSingleplayerServer().overworld().getBlockState(desk))));
                stage = 4;
            } else if (stage == 4 && pending.isDone() && mc.screen instanceof ResearchDeskScreen screen && screen.snapshot().sheet() != null && ++frames > WAIT) {
                var snapshot = screen.snapshot();
                require(snapshot.cabinets() == 2, "Stacked cabinets were not linked: " + snapshot.cabinets());
                require(snapshot.archive().size() == 1 && snapshot.capacity() == 2 * ArchiveCabinetBlockEntity.SIZE, "Archive listing is wrong");
                require(snapshot.pools().size() >= 40 && snapshot.pool("aer") >= 100, "Pools were not snapshotted");
                require(snapshot.sheet().cells().stream().anyMatch(c -> c.type() == 1), "Note has no fixed hexes");
                proof.put("openedThroughSideHalfWithLinkedCabinets", true);
                proof.put("canvasScale", screen.scale());
                screenshot(mc, "open", screen.getGuiLeft() + ResearchDeskScreen.GRID_X + 28, screen.getGuiTop() + ResearchDeskScreen.GRID_Y + 8);
                exerciseSearchAndSort(mc, screen);
                blank = snapshot.sheet().cells().stream().filter(c -> c.type() == 0).findFirst().orElseThrow();
                pending = server(mc, () -> { airBefore = TcPlayerAspects.pool(api(), player(mc), "aer"); inkBefore = ink(mc); });
                stage = 5;
            } else if (stage == 5 && pending.isDone() && ++frames > WAIT) {
                // Five writes alternate place and erase; the fifth costs no ink.
                var screen = screen(mc);
                var cell = cell(screen, blank);
                float size = screen.hexSize(screen.snapshot().sheet());
                float cx = screen.cellX(cell, size), cy = screen.cellY(cell, size);
                if (writes % 2 == 0) {
                    // Original-table movement: press on the aspect, carry it, release over the blank hex.
                    require(cell.type() == 0, "Cell was not blank before write " + writes);
                    double[] from = palettePoint(screen, "aer");
                    press(screen, from[0], from[1]);
                    drag(screen, cx, cy);
                    if (writes == 0) screenshot(mc, "dragging", Math.round(cx), Math.round(cy));
                    release(screen, cx, cy);
                } else {
                    require(cell.type() == 2 && "aer".equals(cell.aspect()), "Placement did not reach the note before write " + writes);
                    if (writes == 1) screenshot(mc, "written", Math.round(cx), Math.round(cy));
                    // A click on a written hex erases it, as on the original table.
                    click(screen, cx, cy, 0);
                }
                if (++writes == 5) stage = 6;
                else frames = 0;
            } else if (stage == 6 && ++frames > WAIT) {
                require(cell(screen(mc), blank).type() == 2, "Last placement missing");
                pending = server(mc, () -> {
                    int air = TcPlayerAspects.pool(api(), player(mc), "aer"), used = ink(mc) - inkBefore;
                    proof.put("inkUsedForFiveWrites", used);
                    require(used == 4, "Expected 4 ink for 5 writes (20% saving), used " + used);
                    require(air == airBefore - 3, "Expected three aer spent, pool went " + airBefore + " -> " + air);
                });
                stage = 7;
            } else if (stage == 7 && pending.isDone() && ++frames > 2) {
                var screen = screen(mc);
                luxBefore = screen.snapshot().pool("lux");
                ignisBefore = screen.snapshot().pool("ignis");
                // A plain click on an aspect fills the next combine slot.
                clickPalette(screen, "aer", 0);
                clickPalette(screen, "ignis", 0);
                click(screen, screen.getGuiLeft() + ResearchDeskScreen.GRID_X + 10, screen.getGuiTop() + ResearchDeskScreen.COMBINE_Y + 5, 0);
                stage = 8;
            } else if (stage == 8 && ++frames > WAIT) {
                var snapshot = screen(mc).snapshot();
                require(snapshot.pool("lux") == luxBefore + 1 && snapshot.pool("ignis") == ignisBefore - 1 && snapshot.pool("aer") == airBefore - 4, "Combination did not spend aer+ignis into lux");
                proof.put("combineAerIgnisIntoLux", true);
                screenshot(mc, "combined", 0, 0);
                var screen = screen(mc);
                click(screen, screen.getGuiLeft() + ResearchDeskScreen.LIST_X + 10, screen.getGuiTop() + ResearchDeskScreen.LIST_Y + 5, 0);
                stage = 9;
            } else if (stage == 9 && ++frames > WAIT) {
                var snapshot = screen(mc).snapshot();
                require("ALUMENTUM".equals(snapshot.sheet().key()) && snapshot.archive().size() == 1, "Taking a filed note did not swap it onto the desk");
                var screen = screen(mc);
                click(screen, screen.getGuiLeft() + ResearchDeskScreen.FILE_X + 5, screen.getGuiTop() + ResearchDeskScreen.BUTTON_Y + 5, 0);
                stage = 10;
            } else if (stage == 10 && ++frames > WAIT) {
                var snapshot = screen(mc).snapshot();
                require(snapshot.sheet() == null && snapshot.archive().size() == 2 && menu(mc).getSlot(ResearchDeskBlockEntity.NOTE).getItem().isEmpty(),
                        "Filing did not move the note into a cabinet");
                proof.put("takeAndFileThroughArchive", true);
                pending = server(mc, () -> finishNote(mc));
                stage = 11;
            } else if (stage == 11 && pending.isDone() && ++frames > WAIT) {
                var screen = screen(mc);
                require(screen.snapshot().sheet() != null && screen.snapshot().sheet().complete() && screen.snapshot().duplicate(), "Finished note was not shown as complete");
                screenshot(mc, "complete", screen.getGuiLeft() + ResearchDeskScreen.COPY_X + 5, screen.getGuiTop() + ResearchDeskScreen.BUTTON_Y + 5);
                click(screen, screen.getGuiLeft() + ResearchDeskScreen.COPY_X + 5, screen.getGuiTop() + ResearchDeskScreen.BUTTON_Y + 5, 0);
                stage = 12;
            } else if (stage == 12 && ++frames > WAIT) {
                var note = menu(mc).getSlot(ResearchDeskBlockEntity.NOTE).getItem();
                require(note.getCount() == 2 && screen(mc).snapshot().sheet().copies() == 1, "Copy did not add a note");
                require(mc.player.getInventory().countItem(Items.PAPER) == 7 && mc.player.getInventory().countItem(Items.INK_SAC) == 7, "Copy did not use paper and ink sac");
                proof.put("copyFinishedNote", true);
                mc.player.closeContainer();
                // Look at the finished desk in the world: from the front, slightly above, at dusk so the lamp shows.
                pending = server(mc, () -> {
                    var player = player(mc);
                    player.serverLevel().setDayTime(13200);
                    double cx = desk.getX() - 2.3, cy = desk.getY() + 1.1, cz = desk.getZ() + 1.4;
                    player.teleportTo(player.serverLevel(), cx, cy, cz, -98F, 30F);
                });
                stage = 20;
            } else if (stage == 20 && pending.isDone() && ++frames > 80) {
                mc.options.hideGui = true;
                stage = 21;
            } else if (stage == 21 && ++frames > 5) {
                net.minecraft.client.Screenshot.grab(mc.gameDirectory, "research-desk-world.png", mc.getMainRenderTarget(), message -> { });
                mc.options.hideGui = false;
                pending = server(mc, () -> use(mc, desk.east()));
                stage = 13;
            } else if (stage == 13 && pending.isDone() && mc.screen instanceof ArchiveCabinetScreen && ++frames > WAIT) {
                screenshot(mc, "cabinet", 0, 0);
                mc.player.closeContainer();
                pending = server(mc, () -> breakSide(mc));
                stage = 14;
            } else if (stage == 14 && pending.isDone() && ++frames > WAIT) {
                finish(mc, null);
            }
        } catch (Exception | AssertionError failure) { finish(mc, failure); }
    }

    private github.com.gengyoubo.tcmodern.tc4.menu.ResearchDeskMenu menu(Minecraft mc) { return screen(mc).getMenu(); }

    private void seed(Minecraft mc) {
        var player = player(mc);
        var level = player.serverLevel();
        desk = player.blockPosition().east(3);
        var state = Tc4Blocks.RESEARCH_DESK.get().defaultBlockState().setValue(ResearchDeskBlock.FACING, Direction.WEST);
        level.setBlockAndUpdate(desk, state);
        level.setBlockAndUpdate(ResearchDeskBlock.partner(desk, state), state.setValue(ResearchDeskBlock.HALF, ResearchDeskBlock.Half.SIDE));
        var cabinet = Tc4Blocks.ARCHIVE_CABINET.get().defaultBlockState().setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, Direction.WEST);
        level.setBlockAndUpdate(desk.east(), cabinet);
        // Linked only through the first cabinet.
        level.setBlockAndUpdate(desk.east().above(), cabinet);
        var amounts = new LinkedHashMap<String, Integer>();
        for (var aspect : TcAspects.snapshot(api())) amounts.put(aspect.tag(), 100);
        TcPlayerAspects.give(api(), player, amounts);
        var table = (ResearchDeskBlockEntity) level.getBlockEntity(desk);
        var drawer = (ArchiveCabinetBlockEntity) level.getBlockEntity(desk.east());
        api().withPlayer(player, actor -> {
            var world = api().mappings().toLegacy(LegacyMappings.WORLD, level).orElseThrow();
            var notes = api().accessor(NativeResearchSmokeProbe.NoteFactory.class);
            var nitor = notes.create(api().stack(new LegacyStack("Thaumcraft:ItemResearchNotes", 0, 1, "")), "NITOR", world);
            var alumentum = notes.create(api().stack(new LegacyStack("Thaumcraft:ItemResearchNotes", 0, 1, "")), "ALUMENTUM", world);
            var inkwell = api().mappings().toHost(LegacyMappings.ITEM_STACK, api().stack(new LegacyStack("Thaumcraft:ItemInkwell", 0, 1, "")), ItemStack.class).orElseThrow();
            var note = api().mappings().toHost(LegacyMappings.ITEM_STACK, nitor, ItemStack.class).orElseThrow();
            require(ResearchItems.isScribeTools(inkwell) && ResearchItems.isNote(note), "Original inkwell or note not recognised");
            require(!ResearchItems.isNote(inkwell) && !ResearchItems.isNote(new ItemStack(Items.PAPER)), "Note filter accepts other items");
            require(table.items().insertItem(ResearchDeskBlockEntity.TOOLS, inkwell, false).isEmpty(), "Desk refused the inkwell");
            require(table.items().insertItem(ResearchDeskBlockEntity.NOTE, note, false).isEmpty(), "Desk refused the note");
            require(!drawer.items().insertItem(1, new ItemStack(Items.PAPER), false).isEmpty(), "Cabinet accepted paper");
            drawer.items().setStackInSlot(0, api().mappings().toHost(LegacyMappings.ITEM_STACK, alumentum, ItemStack.class).orElseThrow());
            return null;
        });
        player.getInventory().add(new ItemStack(Items.PAPER, 8));
        player.getInventory().add(new ItemStack(Items.INK_SAC, 8));
        proof.put("slotFiltersAcceptOnlyOriginalItems", true);
    }

    private void use(Minecraft mc, BlockPos pos) {
        var player = player(mc);
        var level = player.serverLevel();
        level.getBlockState(pos).use(level, player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
        require(player.containerMenu != player.inventoryMenu, "No menu opened at " + pos);
    }

    private int ink(Minecraft mc) {
        var table = (ResearchDeskBlockEntity) player(mc).serverLevel().getBlockEntity(desk);
        return api().accessor(DeskAccessors.StackView.class, api().mappings().toLegacy(LegacyMappings.ITEM_STACK, table.tools()).orElseThrow()).damage();
    }

    /** Takes the NITOR note back from the archive and finishes it as the original completion would. */
    private void finishNote(Minecraft mc) {
        var player = player(mc);
        TcResearch.complete(api(), player, "RESEARCHDUPE");
        var table = (ResearchDeskBlockEntity) player.serverLevel().getBlockEntity(desk);
        for (var cabinet : table.cabinets()) for (int slot = 0; slot < ArchiveCabinetBlockEntity.SIZE; slot++) {
            var stack = cabinet.items().getStackInSlot(slot);
            if (stack.isEmpty()) continue;
            var legacy = api().mappings().toLegacy(LegacyMappings.ITEM_STACK, stack).orElseThrow();
            var data = api().accessor(DeskAccessors.Notes.class).read(legacy);
            if (!"NITOR".equals(api().accessor(DeskAccessors.Note.class, data).key())) continue;
            api().accessor(NativeResearchSmokeProbe.WritableNote.class, data).complete(true);
            api().accessor(DeskAccessors.Notes.class).write(legacy, data);
            api().accessor(DeskAccessors.StackView.class, legacy).setDamage(DeskAccessors.DISCOVERY);
            cabinet.items().setStackInSlot(slot, ItemStack.EMPTY);
            table.items().setStackInSlot(ResearchDeskBlockEntity.NOTE, api().mappings().toHost(LegacyMappings.ITEM_STACK, legacy, ItemStack.class).orElseThrow());
            ((github.com.gengyoubo.tcmodern.tc4.menu.ResearchDeskMenu) player.containerMenu).invalidate();
            return;
        }
        throw new IllegalStateException("NITOR note was not filed");
    }

    /** Breaking the side half in survival removes both halves, drops one desk and spills the desk's items. */
    private void breakSide(Minecraft mc) {
        var player = player(mc);
        var level = player.serverLevel();
        var side = ResearchDeskBlock.partner(desk, level.getBlockState(desk));
        player.gameMode.destroyBlock(side);
        require(level.getBlockState(desk).isAir() && level.getBlockState(side).isAir(), "A desk half survived");
        var drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(desk).inflate(3));
        long desks = drops.stream().filter(e -> e.getItem().is(Tc4Blocks.RESEARCH_DESK.get().asItem())).mapToInt(e -> e.getItem().getCount()).sum();
        require(desks == 1, "Expected exactly one desk drop, got " + desks);
        require(drops.stream().anyMatch(e -> ResearchItems.isNote(e.getItem())), "Desk note was not dropped");
        proof.put("breakingOneHalfDropsOneDeskAndContents", true);
    }

    private void exerciseSearchAndSort(Minecraft mc, ResearchDeskScreen screen) {
        int left = screen.getGuiLeft(), top = screen.getGuiTop();
        require(ResearchDeskScreen.mode == AspectOrder.Mode.ORIGINAL, "Default sort is not TC4 default");
        var original = screen.palette();
        require(original.subList(0, 6).containsAll(List.of("aer", "terra", "ignis", "aqua", "ordo", "perditio")), "TC4 default does not start with primals: " + original);
        clickSort(screen, 1);
        require(ResearchDeskScreen.mode == AspectOrder.Mode.AMOUNT, "Amount segment did not select");
        var amounts = screen.palette();
        for (int i = 1; i < amounts.size(); i++)
            require(screen.snapshot().pool(amounts.get(i - 1)) >= screen.snapshot().pool(amounts.get(i)), "Amount sort out of order at " + i);
        clickSort(screen, 2);
        var tiers = screen.palette();
        var order = new AspectOrder(TcAspects.snapshot(api()));
        for (int i = 1; i < tiers.size(); i++) require(order.tier(tiers.get(i - 1)) <= order.tier(tiers.get(i)), "Tier sort out of order at " + i);
        require(order.tier("humanus") == 5, "Humanus is not tier 5");
        clickSort(screen, 3);
        var alphabetical = screen.palette();
        var sorted = new ArrayList<>(alphabetical);
        Collections.sort(sorted);
        require(alphabetical.equals(sorted), "Alphabetical sort is not a to z");
        clickSort(screen, 0);
        require(ResearchDeskScreen.mode == AspectOrder.Mode.ORIGINAL, "Sort did not cycle back");
        click(screen, left + ResearchDeskScreen.GRID_X + 20, top + ResearchDeskScreen.SEARCH_Y + 5, 0);
        String name = github.com.gengyoubo.tcmodern.tc4.client.AspectIcons.name("lux");
        for (char c : name.toCharArray()) screen.charTyped(c, 0);
        var found = screen.palette();
        require(found.contains("lux") && found.size() < original.size(), "Search by localized name '" + name + "' failed: " + found);
        try { screenshot(mc, "search", 0, 0); } catch (Exception error) { throw new IllegalStateException(error); }
        click(screen, left + ResearchDeskScreen.GRID_X + 20, top + ResearchDeskScreen.SEARCH_Y + 5, 1);
        require(screen.palette().size() == original.size(), "Right-click did not clear the search");
        click(screen, left + 200, top + 4, 0);
        proof.put("searchAndFourSortModes", true);
        proof.put("luxName", name);
    }

    private static void clickSort(ResearchDeskScreen screen, int segment) {
        click(screen, screen.getGuiLeft() + ResearchDeskScreen.sortSegmentX(segment) + 8, screen.getGuiTop() + ResearchDeskScreen.SORT_Y + 8, 0);
    }

    private static DeskSnapshot.Cell cell(ResearchDeskScreen screen, DeskSnapshot.Cell key) {
        return screen.snapshot().sheet().cells().stream().filter(c -> c.q() == key.q() && c.r() == key.r()).findFirst().orElseThrow();
    }

    private static double[] palettePoint(ResearchDeskScreen screen, String tag) {
        int index = screen.palette().indexOf(tag);
        require(index >= 0 && index < ResearchDeskScreen.COLUMNS * ResearchDeskScreen.ROWS, "Aspect " + tag + " not on the first palette page");
        return new double[]{screen.getGuiLeft() + ResearchDeskScreen.GRID_X + index % ResearchDeskScreen.COLUMNS * ResearchDeskScreen.CELL + 9,
                screen.getGuiTop() + ResearchDeskScreen.GRID_Y + index / ResearchDeskScreen.COLUMNS * ResearchDeskScreen.CELL + 9};
    }

    private static void clickPalette(ResearchDeskScreen screen, String tag, int button) {
        double[] point = palettePoint(screen, tag);
        click(screen, point[0], point[1], button);
    }

    private static void press(ResearchDeskScreen screen, double x, double y) { screen.mouseClicked(x * screen.scale(), y * screen.scale(), 0); }
    private static void drag(ResearchDeskScreen screen, double x, double y) { screen.mouseDragged(x * screen.scale(), y * screen.scale(), 0, 0, 0); }
    private static void release(ResearchDeskScreen screen, double x, double y) { screen.mouseReleased(x * screen.scale(), y * screen.scale(), 0); }

    /** {@code x, y} on the desk canvas; the screen may be drawn scaled down. */
    private static void click(ResearchDeskScreen screen, double x, double y, int button) {
        screen.mouseClicked(x * screen.scale(), y * screen.scale(), button);
        screen.mouseReleased(x * screen.scale(), y * screen.scale(), button);
    }

    /**
     * Renders the open screen offscreen at a fixed 480x270 GUI size (960x540 pixels), so captures do not depend on
     * the hidden test window. {@code mouseX, mouseY} are canvas coordinates of the desk; the screen is re-laid out
     * for the capture and restored afterwards.
     */
    private void screenshot(Minecraft mc, String name, int mouseX, int mouseY) throws Exception {
        int width = 480, height = 270, pixels = 2;
        var screen = mc.screen;
        var target = new com.mojang.blaze3d.pipeline.TextureTarget(width * pixels, height * pixels, true, Minecraft.ON_OSX);
        // Canvas coordinates relative to the desk origin survive the re-layout.
        int dx = 0, dy = 0;
        if (screen instanceof ResearchDeskScreen desk) { dx = mouseX - desk.getGuiLeft(); dy = mouseY - desk.getGuiTop(); }
        screen.resize(mc, width, height);
        float scale = screen instanceof ResearchDeskScreen desk ? desk.scale() : 1;
        int mx = screen instanceof ResearchDeskScreen desk ? Math.round((desk.getGuiLeft() + dx) * scale) : mouseX;
        int my = screen instanceof ResearchDeskScreen desk ? Math.round((desk.getGuiTop() + dy) * scale) : mouseY;
        if (mouseX == 0 && mouseY == 0) { mx = 0; my = 0; }
        target.setClearColor(0.23F, 0.29F, 0.23F, 1);
        target.clear(Minecraft.ON_OSX);
        target.bindWrite(true);
        com.mojang.blaze3d.systems.RenderSystem.backupProjectionMatrix();
        com.mojang.blaze3d.systems.RenderSystem.setProjectionMatrix(new org.joml.Matrix4f().setOrtho(0, width, height, 0, 1000, 3000),
                com.mojang.blaze3d.vertex.VertexSorting.ORTHOGRAPHIC_Z);
        var modelView = com.mojang.blaze3d.systems.RenderSystem.getModelViewStack();
        modelView.pushPose();
        modelView.setIdentity();
        modelView.translate(0, 0, -2000);
        com.mojang.blaze3d.systems.RenderSystem.applyModelViewMatrix();
        try {
            var graphics = new GuiGraphics(mc, mc.renderBuffers().bufferSource());
            graphics.fill(0, 0, width, height, 0xff3a4a3a);
            net.minecraftforge.client.ForgeHooksClient.drawScreen(screen, graphics, mx, my, 0);
            graphics.flush();
            var path = mc.gameDirectory.toPath().resolve("screenshots/research-desk-" + name + ".png");
            Files.createDirectories(path.getParent());
            try (var image = net.minecraft.client.Screenshot.takeScreenshot(target)) { image.writeToFile(path); }
        } finally {
            modelView.popPose();
            com.mojang.blaze3d.systems.RenderSystem.applyModelViewMatrix();
            com.mojang.blaze3d.systems.RenderSystem.restoreProjectionMatrix();
            target.unbindWrite();
            target.destroyBuffers();
            mc.getMainRenderTarget().bindWrite(true);
            screen.resize(mc, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
        }
    }

    private void finish(Minecraft mc, Throwable failure) {
        stage = 99;
        proof.put("status", failure == null ? "PASS" : "FAIL");
        if (failure != null) {
            proof.put("stage", lastStage);
            proof.put("failure", failure.toString());
            LogUtils.getLogger().error("TC4_RESEARCH_DESK_SMOKE_FAIL", failure);
        }
        try {
            var file = mc.gameDirectory.toPath().resolve("logs/tc4modern/research-desk-smoke.json");
            Files.createDirectories(file.getParent());
            Files.writeString(file, new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(proof));
        } catch (java.io.IOException error) { throw new IllegalStateException(error); }
        LogUtils.getLogger().info("TC4_RESEARCH_DESK_SMOKE_RESULT: {}", proof);
        if (mc.level != null) { mc.level.disconnect(); mc.clearLevel(); }
        mc.stop();
    }
}
