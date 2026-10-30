package github.com.gengyoubo.tcmodern.tc4.client.testing;

import com.mojang.logging.LogUtils;
import github.com.gengyoubo.mfcl.api.*;
import github.com.gengyoubo.mfcl.api.mla.*;
import github.com.gengyoubo.tcmodern.api.TcModernApi;
import github.com.gengyoubo.tcmodern.api.aspect.TcAspectAccessors;
import github.com.gengyoubo.tcmodern.api.research.TcResearch;
import github.com.gengyoubo.tcmodern.api.recipe.TcRecipeAccessors;
import github.com.gengyoubo.tcmodern.tc4.client.FormedAccessors;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
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

/** Opt-in acceptance against real host blocks and original tile logic, using only public MFCL APIs. */
@Mod.EventBusSubscriber(modid = "tc4modern", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class MultiblockSmokeProbe {
    static volatile boolean completed;
    private static final String TC = "Thaumcraft:";
    private static final String WAND = "{rod:\"silverwood\",cap:\"thaumium\",aer:10000,ignis:10000,aqua:10000,terra:10000,ordo:10000,perditio:10000}";
    private static final List<String> IDS = List.of("research_table", "infusion_altar", "infernal_furnace",
            "advanced_alchemy_furnace", "thaumatorium", "node_jar");
    private final Map<String, Object> proof = new LinkedHashMap<>();
    private final long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(5);
    private CompletableFuture<?> pending;
    private int stage;

    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        if (Boolean.getBoolean("tc4modern.multiblockSmokeTest"))
            event.enqueueWork(() -> MinecraftForge.EVENT_BUS.addListener(new MultiblockSmokeProbe()::tick));
    }

    private void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == 99) return;
        var mc = Minecraft.getInstance();
        try {
            require(System.nanoTime() < deadline, "Multiblock acceptance timed out at " + stage);
            if (stage == 0 && mc.screen instanceof TitleScreen && mc.getOverlay() == null) {
                org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());
                mc.options.pauseOnLostFocus = false;
                mc.options.renderDistance().set(4);
                mc.createWorldOpenFlows().createFreshLevel("tc4modern-multiblock-smoke-" + System.currentTimeMillis(),
                        new LevelSettings("TC4 multiblock acceptance", GameType.SURVIVAL, false, Difficulty.PEACEFUL,
                                true, new GameRules(), WorldDataConfiguration.DEFAULT),
                        new WorldOptions(42, false, false), access -> access.registryOrThrow(Registries.WORLD_PRESET)
                                .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
                stage = 1;
            } else if (stage == 1 && mc.player != null && mc.level != null && mc.screen == null) {
                pending = mc.getSingleplayerServer().submit(() -> {
                    var server = mc.getSingleplayerServer();
                    var player = server.getPlayerList().getPlayers().get(0);
                    var runtime = TcModernApi.runtime().orElseThrow();
                    TcResearch.completeAll(runtime, player);
                    var origin = player.blockPosition().above(4);
                    for (int i = 0; i < IDS.size(); i++) check(runtime, server.overworld(), player, origin.east(i * 8), IDS.get(i));
                });
                stage = 2;
            } else if (stage == 2 && pending.isDone()) {
                pending.join();
                boolean passed = proof.values().stream().allMatch(value -> value instanceof Map<?, ?> result && "PASS".equals(result.get("status")));
                require(passed, "Some multiblocks failed; see individual results");
                finish(mc, null);
            }
        } catch (Exception | AssertionError failure) { finish(mc, failure); }
    }

    private void check(LegacyRuntime api, ServerLevel level, ServerPlayer player, BlockPos at, String id) {
        var result = new LinkedHashMap<String, Object>();
        proof.put(id, result);
        try {
            BlockPos trigger = build(api, level, at, id);
            if (id.equals("node_jar")) world(api, level, old -> {
                var node = tile(api, old, at.offset(1, 1, 1));
                api.accessor(FormedAccessors.Node.class, node).aspects(aspects(api, Map.of("aer", 20, "ignis", 15)));
            });
            var tool = id.equals("research_table") ? stack(api, "ItemInkwell", 0, "") : stack(api, "WandCasting", 0, WAND);
            player.teleportTo(trigger.getX() + 0.5, trigger.getY() + 1, trigger.getZ() - 2.5);
            player.getInventory().selected = 0;
            player.setItemInHand(InteractionHand.MAIN_HAND, tool);
            var use = player.gameMode.useItemOn(player, level, tool, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(trigger), Direction.NORTH, trigger, false));
            result.put("useResult", use.toString());
            switch (id) {
                case "research_table" -> {
                    state(api, level, at, "blockTable", 5);
                    state(api, level, at.east(), "blockTable", 8);
                    result.put("formed", true);
                    world(api, level, old -> {
                        var inventory = api.accessor(Inventory.class, tile(api, old, at));
                        require(api.snapshot(inventory.get(0)).key().equals(TC + "ItemInkwell"), "Scribing tools were not stored");
                    });
                    // This must open the bridged container through the actual host interaction.
                    player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                    player.gameMode.useItemOn(player, level, ItemStack.EMPTY, InteractionHand.MAIN_HAND,
                            new BlockHitResult(Vec3.atCenterOf(at), Direction.UP, at, false));
                    require(player.containerMenu != player.inventoryMenu, "Research table container did not open");
                    result.put("scribingToolsAndContainer", true);
                    player.closeContainer();
                }
                case "infusion_altar" -> {
                    for (int x : new int[]{0, 2}) for (int z : new int[]{0, 2}) {
                        state(api, level, at.offset(x, 0, z), "blockStoneDevice", 3);
                        state(api, level, at.offset(x, 1, z), "blockStoneDevice", 4);
                    }
                    result.put("formed", true);
                    world(api, level, old -> {
                        var matrix = api.accessor(Matrix.class, tile(api, old, at.offset(1, 2, 1)));
                        require(matrix.valid(), "Matrix rejected its formed pillars");
                        var pedestal = api.accessor(Inventory.class, tile(api, old, at.offset(1, 0, 1)));
                        pedestal.set(0, api.stack(new LegacyStack("minecraft:diamond", 0, 1, "")));
                        require(api.snapshot(pedestal.get(0)).key().equals("minecraft:diamond"), "Pedestal inventory failed");
                        pedestal.set(0, null);
                    });
                    result.put("validMatrixAndPedestalInventory", true);
                    infusion(api, level, player, at.offset(1, 0, 1), result);
                }
                case "infernal_furnace" -> {
                    var center = at.offset(1, 1, 1);
                    state(api, level, center, "blockArcaneFurnace", 0);
                    result.put("formed", true);
                    world(api, level, old -> {
                        var furnace = tile(api, old, center);
                        require(api.accessor(Infernal.class, furnace).add(api.stack(new LegacyStack("minecraft:beef", 0, 1, ""))), "Furnace rejected beef");
                    });
                    for (int i = 0; i < 500; i++) tickHost(level, center);
                    require(level.getEntitiesOfClass(ItemEntity.class, new AABB(center).inflate(5)).stream()
                            .anyMatch(entity -> entity.getItem().is(Items.COOKED_BEEF)), "Furnace did not eject cooked beef");
                    result.put("smeltedAndEjectedBeef", true);
                }
                case "advanced_alchemy_furnace" -> {
                    var center = at.offset(1, 0, 1);
                    state(api, level, center, "blockAlchemyFurnace", 0);
                    state(api, level, at, "blockAlchemyFurnace", 4);
                    result.put("formed", true);
                    world(api, level, old -> {
                        var furnace = api.accessor(Advanced.class, tile(api, old, center));
                        furnace.heat(1000); furnace.power1(1000); furnace.power2(1000);
                        require(furnace.process(api.stack(new LegacyStack("minecraft:log", 0, 1, ""))), "Powered furnace rejected a log");
                        require(furnace.vis() > 0 && furnace.heat() < 1000 && furnace.power1() < 1000 && furnace.power2() < 1000,
                                "Furnace did not produce essentia and consume power");
                        result.put("essentiaProduced", furnace.vis());
                    });
                    result.put("scope", "Processing with supplied internal power; energized-node charging and pipe extraction are not covered");
                }
                case "thaumatorium" -> {
                    state(api, level, at, "blockMetalDevice", 0);
                    state(api, level, at.above(), "blockMetalDevice", 10);
                    state(api, level, at.above(2), "blockMetalDevice", 11);
                    result.put("formed", true);
                    level.setBlockAndUpdate(at.below(), Blocks.LAVA.defaultBlockState());
                    world(api, level, old -> {
                        var tower = tile(api, old, at.above());
                        var inventory = api.accessor(Inventory.class, tower);
                        inventory.set(0, api.stack(new LegacyStack("minecraft:iron_ingot", 0, 1, "")));
                        require(api.snapshot(inventory.get(0)).key().equals("minecraft:iron_ingot"), "Tower inventory failed");
                    });
                    for (int i = 0; i < 45; i++) tickHost(level, at.above());
                    player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                    player.gameMode.useItemOn(player, level, ItemStack.EMPTY, InteractionHand.MAIN_HAND,
                            new BlockHitResult(Vec3.atCenterOf(at.above()), Direction.NORTH, at.above(), false));
                    require(player.containerMenu != player.inventoryMenu, "Thaumatorium container did not open");
                    result.put("inventoryTicksAndContainer", true);
                    player.closeContainer();
                    world(api, level, old -> {
                        // Select an actual registered iron-catalyst recipe, with its original hash/cost.
                        var iron = api.stack(new LegacyStack("minecraft:iron_ingot", 0, 1, ""));
                        var recipe = api.values().list(api.accessor(TcRecipeAccessors.Registry.class).recipes()).stream()
                                .filter(value -> value instanceof LegacyObject ref && api.values().isInstance(ref, Crucible.class))
                                .map(LegacyObject.class::cast).filter(ref -> api.accessor(Crucible.class, ref).matches(iron)).findFirst().orElseThrow();
                        var original = api.accessor(TcRecipeAccessors.Crucible.class, recipe);
                        var tower = tile(api, old, at.above());
                        var settings = api.accessor(Tower.class, tower);
                        api.values().add(settings.hashes(), api.accessor(Crucible.class, recipe).hash());
                        api.values().add(settings.costs(), original.aspects());
                        api.values().add(settings.players(), player.getName().getString());
                        settings.essentia(api.accessor(AspectAmounts.class, original.aspects()).copy());
                        api.accessor(Inventory.class, tower).set(0, iron);
                        for (int i = 0; i < 50; i++) tickHost(level, at.above());
                        require(api.accessor(Inventory.class, tower).get(0) == null, "Tower did not consume its catalyst");
                        var expected = api.mappings().toHost(LegacyMappings.ITEM_STACK, original.output(), ItemStack.class).orElseThrow();
                        require(level.getEntitiesOfClass(ItemEntity.class, new AABB(at.above()).inflate(4)).stream()
                                .anyMatch(entity -> ItemStack.isSameItemSameTags(entity.getItem(), expected)), "Tower did not eject recipe output");
                        result.put("craftedOutput", api.snapshot(original.output()).toString());
                    });
                    result.put("scope", "Actual registered crucible recipe with supplied internal essentia; external pipe supply is not covered");
                }
                case "node_jar" -> {
                    var center = at.offset(1, 1, 1);
                    state(api, level, center, "blockJar", 2);
                    result.put("formed", true);
                    world(api, level, old -> {
                        var node = api.accessor(Node.class, tile(api, old, center));
                        var amounts = api.accessor(AspectAmounts.class, node.aspects());
                        var lookup = api.accessor(TcAspectAccessors.AspectLookup.class);
                        require(amounts.amount(lookup.find("aer")) == 20 && amounts.amount(lookup.find("ignis")) == 15, "Jar lost node aspects");
                    });
                    result.put("nodeAspectsPreserved", true);
                }
                default -> throw new IllegalArgumentException(id);
            }
            result.put("status", "PASS");
        } catch (Exception | AssertionError failure) {
            result.put("status", "FAIL"); result.put("failure", failure.toString());
            LogUtils.getLogger().error("TC4 multiblock check failed: {}", id, failure);
        } finally { player.closeContainer(); }
    }

    private static void infusion(LegacyRuntime api, ServerLevel level, ServerPlayer player, BlockPos center, Map<String, Object> result) {
        var offsets = List.of(new BlockPos(-3, 0, -3), new BlockPos(3, 0, 3), new BlockPos(-3, 0, 3), new BlockPos(3, 0, -3),
                new BlockPos(-3, 0, 0), new BlockPos(3, 0, 0), new BlockPos(0, 0, -3), new BlockPos(0, 0, 3));
        for (var offset : offsets) level.setBlockAndUpdate(center.offset(offset), api.hostBlock(TC + "blockStoneDevice", 1,
                net.minecraft.world.level.block.state.BlockState.class).orElseThrow());
        // Symmetric stabilizers make the low-instability recipe deterministic.
        for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) if (Math.abs(x) == 3 || Math.abs(z) == 3)
            level.setBlockAndUpdate(center.offset(x, -1, z), Blocks.SKELETON_SKULL.defaultBlockState());
        var expected = api.withPlayer(player, actor -> {
            var old = api.mappings().toLegacy(LegacyMappings.WORLD, level).orElseThrow();
            var selected = api.values().list(api.accessor(TcRecipeAccessors.Registry.class).recipes()).stream()
                    .filter(value -> value instanceof LegacyObject ref && ref.typeName().equals("thaumcraft.api.crafting.InfusionRecipe"))
                    .map(LegacyObject.class::cast).map(ref -> api.accessor(TcRecipeAccessors.Infusion.class, ref))
                    .filter(recipe -> recipe.instability() <= 2 && recipe.components().length > 0 && recipe.components().length <= offsets.size()
                            && api.values().isInstance(recipe.output(), "net.minecraft.item.ItemStack"))
                    .min(Comparator.comparingInt(TcRecipeAccessors.Infusion::instability)).orElseThrow();
            var output = api.snapshot(selected.output());
            api.accessor(Inventory.class, tile(api, old, center)).set(0, api.stack(api.snapshot(selected.central())));
            var components = selected.components();
            for (int i = 0; i < components.length; i++)
                api.accessor(Inventory.class, tile(api, old, center.offset(offsets.get(i)))).set(0, api.stack(api.snapshot(components[i])));
            var costs = api.accessor(TcRecipeAccessors.Aspects.class, selected.aspects());
            int index = 0;
            for (var aspect : costs.values()) {
                var jarPos = center.offset(-4 + index++, 0, -4);
                level.setBlockAndUpdate(jarPos, api.hostBlock(TC + "blockJar", 0, net.minecraft.world.level.block.state.BlockState.class).orElseThrow());
                var amount = api.accessor(FormedAccessors.AspectsFactory.class).create();
                api.accessor(FormedAccessors.Aspects.class, amount).add(aspect, costs.amount(aspect));
                api.accessor(Container.class, tile(api, old, jarPos)).aspects(amount);
            }
            var ref = tile(api, old, center.above(2));
            var matrix = api.accessor(Matrix.class, ref);
            matrix.start(actor);
            require(matrix.crafting(), "Matrix did not start a registered infusion recipe");
            return output;
        });
        world(api, level, old -> {
            for (int i = 0; i < 8000 && api.accessor(Matrix.class, tile(api, old, center.above(2))).crafting(); i++)
                tickHost(level, center.above(2));
            var actual = api.snapshot(api.accessor(Inventory.class, tile(api, old, center)).get(0));
            require(!api.accessor(Matrix.class, tile(api, old, center.above(2))).crafting() && actual.key().equals(expected.key())
                    && actual.metadata() == expected.metadata() && actual.snbt().equals(expected.snbt()),
                    "Infusion did not finish with the expected output: " + actual);
            result.put("infusionOutput", actual.toString());
            result.put("scope", "Full registered infusion with stocked source jars and stabilizers");
        });
    }

    private static BlockPos build(LegacyRuntime api, ServerLevel level, BlockPos at, String id) {
        switch (id) {
            case "research_table" -> { place(api, level, at, Map.of('T', "blockTable@0"), new String[]{"TT"}); return at; }
            case "infusion_altar" -> {
                place(api, level, at, Map.of('b', "blockCosmeticSolid@7", 's', "blockCosmeticSolid@6", 'p', "blockStoneDevice@1", 'm', "blockStoneDevice@2"),
                        new String[]{"b b", " p ", "b b"}, new String[]{"s s", "   ", "s s"}, new String[]{"   ", " m ", "   "});
                return at.offset(1, 2, 1);
            }
            case "infernal_furnace" -> {
                place(api, level, at, Map.of('N', "minecraft:nether_brick@0", 'O', "minecraft:obsidian@0", 'L', "minecraft:lava@0", 'B', "minecraft:iron_bars@0"),
                        new String[]{"NON", "OOO", "NON"}, new String[]{"NON", "OLO", "NBN"}, new String[]{"NON", "O O", "NON"});
                return at.above();
            }
            case "advanced_alchemy_furnace" -> {
                place(api, level, at, Map.of('A', "blockMetalDevice@3", 'F', "blockStoneDevice@0", 'C', "blockMetalDevice@9", 'D', "blockMetalDevice@1"),
                        new String[]{"AAA", "AFA", "AAA"}, new String[]{"DCD", "C C", "DCD"});
                return at.east();
            }
            case "thaumatorium" -> {
                place(api, level, at, Map.of('R', "blockMetalDevice@0", 'C', "blockMetalDevice@9"), new String[]{"R"}, new String[]{"C"}, new String[]{"C"});
                return at.above();
            }
            case "node_jar" -> {
                place(api, level, at, Map.of('G', "minecraft:glass@0", 'X', "blockAiry@0", 'S', "minecraft:wooden_slab@0"),
                        new String[]{"GGG", "GGG", "GGG"}, new String[]{"GGG", "GXG", "GGG"}, new String[]{"GGG", "GGG", "GGG"}, new String[]{"SSS", "SSS", "SSS"});
                return at.offset(1, 2, 0);
            }
            default -> throw new IllegalArgumentException(id);
        }
    }

    private static void place(LegacyRuntime api, ServerLevel level, BlockPos at, Map<Character, String> legend, String[]... layers) {
        for (int y = 0; y < layers.length; y++) for (int z = 0; z < layers[y].length; z++) for (int x = 0; x < layers[y][z].length(); x++) {
            var cell = legend.get(layers[y][z].charAt(x));
            if (cell == null) { level.setBlock(at.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2); continue; }
            int split = cell.indexOf('@'); String key = cell.substring(0, split);
            if (!key.contains(":")) key = TC + key;
            level.setBlock(at.offset(x, y, z), api.hostBlock(key, Integer.parseInt(cell.substring(split + 1)), net.minecraft.world.level.block.state.BlockState.class).orElseThrow(), 2);
        }
    }

    private static ItemStack stack(LegacyRuntime api, String key, int metadata, String nbt) {
        return api.mappings().toHost(LegacyMappings.ITEM_STACK, api.stack(new LegacyStack(TC + key, metadata, 1, nbt)), ItemStack.class).orElseThrow();
    }
    private static void state(LegacyRuntime api, ServerLevel level, BlockPos at, String key, int metadata) {
        require(level.getBlockState(at).equals(api.hostBlock(TC + key, metadata, net.minecraft.world.level.block.state.BlockState.class).orElseThrow()),
                "Expected " + key + "@" + metadata + " at " + at + "; got " + level.getBlockState(at));
    }
    private static void world(LegacyRuntime api, ServerLevel level, java.util.function.Consumer<LegacyObject> check) {
        check.accept(api.mappings().toLegacy(LegacyMappings.WORLD, level).orElseThrow());
    }
    private static LegacyObject tile(LegacyRuntime api, LegacyObject world, BlockPos at) {
        return Objects.requireNonNull(api.accessor(FormedAccessors.World.class, world).tile(at.getX(), at.getY(), at.getZ()), "Missing original tile at " + at);
    }
    private static LegacyObject aspects(LegacyRuntime api, Map<String, Integer> amounts) {
        var list = api.accessor(FormedAccessors.AspectsFactory.class).create();
        amounts.forEach((tag, amount) -> api.accessor(FormedAccessors.Aspects.class, list).add(api.accessor(TcAspectAccessors.AspectLookup.class).find(tag), amount));
        return list;
    }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }

    /** Use the block's host ticker so MFCL captures original drops, packets and tile changes as in normal play. */
    @SuppressWarnings("unchecked")
    private static void tickHost(ServerLevel level, BlockPos pos) {
        var tile = Objects.requireNonNull(level.getBlockEntity(pos));
        var type = (net.minecraft.world.level.block.entity.BlockEntityType<net.minecraft.world.level.block.entity.BlockEntity>) tile.getType();
        var state = level.getBlockState(pos);
        var ticker = Objects.requireNonNull(state.getTicker(level, type), "Missing host ticker at " + pos);
        ticker.tick(level, pos, state, tile);
    }

    private void finish(Minecraft mc, Throwable failure) {
        stage = 99;
        proof.put("status", failure == null ? "PASS" : "FAIL");
        if (failure != null) { proof.put("failure", failure.toString()); LogUtils.getLogger().error("TC4_MULTIBLOCK_SMOKE_FAIL", failure); }
        try {
            var file = mc.gameDirectory.toPath().resolve("logs/tc4modern/multiblock-smoke.json");
            Files.createDirectories(file.getParent());
            Files.writeString(file, new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(proof));
        } catch (java.io.IOException error) { throw new IllegalStateException(error); }
        if (mc.level != null) { mc.level.disconnect(); mc.clearLevel(); }
        if (Boolean.getBoolean("tc4modern.jeiSmokeTest")) {
            mc.setScreen(new TitleScreen());
            completed = true;
        } else mc.stop();
    }

    @LegacyType("net.minecraft.inventory.IInventory") public interface Inventory {
        @LegacyMethod(name = "func_70301_a", descriptor = "(I)Lnet/minecraft/item/ItemStack;") LegacyObject get(int slot);
        @LegacyMethod(name = "func_70299_a", descriptor = "(ILnet/minecraft/item/ItemStack;)V") void set(int slot, LegacyObject stack);
    }
    @LegacyType("net.minecraft.tileentity.TileEntity") public interface Tile {
        @LegacyMethod(name = "func_145845_h", descriptor = "()V") void tick();
    }
    @LegacyType("thaumcraft.common.tiles.TileInfusionMatrix") public interface Matrix {
        @LegacyMethod(name = "validLocation", descriptor = "()Z") boolean valid();
        @LegacyMethod(name = "craftingStart", descriptor = "(Lnet/minecraft/entity/player/EntityPlayer;)V") void start(LegacyObject player);
        @LegacyField(name = "crafting", descriptor = "Z") boolean crafting();
    }
    @LegacyType("thaumcraft.api.aspects.IAspectContainer") public interface Container {
        @LegacyMethod(name = "setAspects", descriptor = "(Lthaumcraft/api/aspects/AspectList;)V") void aspects(LegacyObject list);
    }
    @LegacyType("thaumcraft.common.tiles.TileArcaneFurnace") public interface Infernal {
        @LegacyMethod(name = "addItemsToInventory", descriptor = "(Lnet/minecraft/item/ItemStack;)Z") boolean add(LegacyObject stack);
    }
    @LegacyType("thaumcraft.common.tiles.TileAlchemyFurnaceAdvanced") public interface Advanced {
        @LegacyField(name = "heat", descriptor = "I") void heat(int value);
        @LegacyField(name = "heat", descriptor = "I") int heat();
        @LegacyField(name = "power1", descriptor = "I") void power1(int value);
        @LegacyField(name = "power1", descriptor = "I") int power1();
        @LegacyField(name = "power2", descriptor = "I") void power2(int value);
        @LegacyField(name = "power2", descriptor = "I") int power2();
        @LegacyField(name = "vis", descriptor = "I") int vis();
        @LegacyMethod(name = "process", descriptor = "(Lnet/minecraft/item/ItemStack;)Z") boolean process(LegacyObject stack);
    }
    @LegacyType("thaumcraft.api.nodes.INode") public interface Node {
        @LegacyMethod(name = "getAspects", descriptor = "()Lthaumcraft/api/aspects/AspectList;") LegacyObject aspects();
    }
    @LegacyType("thaumcraft.api.aspects.AspectList") public interface AspectAmounts {
        @LegacyMethod(name = "getAmount", descriptor = "(Lthaumcraft/api/aspects/Aspect;)I") int amount(LegacyObject aspect);
        @LegacyMethod(name = "copy", descriptor = "()Lthaumcraft/api/aspects/AspectList;") LegacyObject copy();
    }
    @LegacyType("thaumcraft.api.crafting.CrucibleRecipe") public interface Crucible {
        @LegacyField(name = "hash", descriptor = "I") int hash();
        @LegacyMethod(name = "catalystMatches", descriptor = "(Lnet/minecraft/item/ItemStack;)Z") boolean matches(LegacyObject stack);
    }
    @LegacyType("thaumcraft.common.tiles.TileThaumatorium") public interface Tower {
        @LegacyField(name = "recipeHash", descriptor = "Ljava/util/ArrayList;") LegacyObject hashes();
        @LegacyField(name = "recipeEssentia", descriptor = "Ljava/util/ArrayList;") LegacyObject costs();
        @LegacyField(name = "recipePlayer", descriptor = "Ljava/util/ArrayList;") LegacyObject players();
        @LegacyField(name = "essentia", descriptor = "Lthaumcraft/api/aspects/AspectList;") void essentia(LegacyObject list);
    }
}
