package github.com.gengyoubo.tcmodern.tc4.client;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import github.com.gengyoubo.mfcl.api.LegacyFeatures;
import github.com.gengyoubo.mfcl.api.LegacyObject;
import github.com.gengyoubo.mfcl.api.LegacyRuntime;
import github.com.gengyoubo.mfcl.api.LegacyScene;
import github.com.gengyoubo.tcmodern.api.TcModernApi;
import github.com.gengyoubo.tcmodern.api.aspect.TcAspectAccessors;
import github.com.gengyoubo.tcmodern.tc4.Tc4Modern;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import static github.com.gengyoubo.tcmodern.tc4.client.FormedAccessors.*;

/**
 * TC4 multiblocks as they look once formed, built in MFCL preview scenes: the original WandManager forms the
 * infernal furnace, the infusion altar and the node jar itself; the other three get the blocks its formation code
 * places. Shared by the JEI multiblock page and the Jade icon. Ids match the JEI recipe ids. Single original blocks
 * get a scene of their own, so tile-rendered ones show in the unformed preview too. Client thread only.
 */
@Mod.EventBusSubscriber(modid = Tc4Modern.MOD_ID, value = Dist.CLIENT)
public final class FormedStructures {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String TC = "Thaumcraft:";
    private static final long RETRY_NANOS = 10_000_000_000L;
    /** The original blocks that only exist in a formed structure, by structure id. */
    private static final Map<String, Map<String, List<Integer>>> PARTS = new LinkedHashMap<>();
    static {
        PARTS.put("research_table", Map.of(TC + "blockTable", List.of(2, 3, 4, 5, 6, 7, 8, 9)));
        PARTS.put("infusion_altar", Map.of(TC + "blockStoneDevice", List.of(3, 4)));
        PARTS.put("infernal_furnace", Map.of(TC + "blockArcaneFurnace", List.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15)));
        PARTS.put("advanced_alchemy_furnace", Map.of(TC + "blockAlchemyFurnace", List.of(0, 1, 2, 3, 4)));
        PARTS.put("thaumatorium", Map.of(TC + "blockMetalDevice", List.of(10, 11)));
        PARTS.put("node_jar", Map.of(TC + "blockJar", List.of(2)));
    }

    private record Entry(LegacyRuntime runtime, LegacyScene scene, long retryAt) { }
    private static final Map<String, Entry> SCENES = new HashMap<>();
    private static Map<BlockState, String> partStates;
    private static LegacyRuntime partRuntime;

    private FormedStructures() { }

    /** The formed structure a host block belongs to, for blocks that exist only in one. */
    public static Optional<String> partOf(BlockState state) {
        var runtime = TcModernApi.runtime().filter(api -> api.supports(LegacyFeatures.HOST_BLOCKS));
        if (runtime.isEmpty()) return Optional.empty();
        if (partStates == null || partRuntime != runtime.get()) {
            var result = new HashMap<BlockState, String>();
            PARTS.forEach((id, blocks) -> blocks.forEach((key, metas) -> {
                for (int meta : metas) {
                    try { runtime.get().hostBlock(key, meta, BlockState.class).ifPresent(host -> result.putIfAbsent(host, id)); }
                    catch (RuntimeException unmapped) { /* A metadata the original never places has no host state. */ }
                }
            }));
            partStates = Map.copyOf(result);
            partRuntime = runtime.get();
        }
        return Optional.ofNullable(partStates.get(state));
    }

    /** The formed structure, built on first use; empty without preview scenes or when the original refuses to form it. */
    public static Optional<LegacyScene> scene(String id) {
        if (!PARTS.containsKey(id)) return Optional.empty();
        return cached(id, (runtime, scene) -> build(runtime, scene, id));
    }

    /**
     * Draws one original block in the unit cell at the current pose, with its block and tile renderers.
     *
     * @return false when preview scenes are unavailable or the block cannot be placed
     */
    public static boolean renderBlock(String legacyKey, int metadata, PoseStack pose, MultiBufferSource buffers, float yaw, float pitch) {
        var scene = cached(legacyKey + "@" + metadata, (runtime, created) -> created.setBlock(0, 0, 0, legacyKey, metadata));
        if (scene.isEmpty()) return false;
        try {
            scene.get().render(pose, buffers, Minecraft.getInstance().getFrameTime(), yaw, pitch);
            return true;
        } catch (RuntimeException failure) {
            return false;
        }
    }

    private static Optional<LegacyScene> cached(String id, java.util.function.BiConsumer<LegacyRuntime, LegacyScene> builder) {
        var runtime = TcModernApi.runtime().filter(api -> api.supports(LegacyFeatures.SCENES)).orElse(null);
        if (runtime == null) return Optional.empty();
        var entry = SCENES.get(id);
        if (entry != null && entry.runtime() == runtime) {
            if (entry.scene() != null) return Optional.of(entry.scene());
            if (System.nanoTime() < entry.retryAt()) return Optional.empty();
        }
        if (entry != null && entry.scene() != null) {
            try { entry.scene().close(); }
            catch (RuntimeException closedRuntime) { /* Its runtime closed with it. */ }
        }
        LegacyScene scene = null;
        try {
            scene = runtime.scenes().create();
            builder.accept(runtime, scene);
            SCENES.put(id, new Entry(runtime, scene, 0));
            return Optional.of(scene);
        } catch (RuntimeException failure) {
            if (scene != null) scene.close();
            if (entry == null) LOGGER.warn("Cannot build the {} preview", id, failure);
            SCENES.put(id, new Entry(runtime, null, System.nanoTime() + RETRY_NANOS));
            return Optional.empty();
        }
    }

    /**
     * Draws the formed structure centred on {@code (x, y)}, scaled to fit {@code size} pixels (at most
     * {@code maxScale} per block) and turned by {@code yaw}/{@code pitch} degrees as the multiblock preview turns it.
     *
     * @return false when the structure is unavailable
     */
    public static boolean render(GuiGraphics graphics, String id, float x, float y, float size, float maxScale, float yaw, float pitch) {
        var scene = scene(id);
        var bounds = scene.flatMap(LegacyScene::bounds);
        if (bounds.isEmpty()) return false;
        var box = bounds.get();
        float diagonal = Mth.sqrt(box.sizeX() * box.sizeX() + box.sizeY() * box.sizeY() + box.sizeZ() * box.sizeZ());
        float scale = Math.min(maxScale, size / diagonal);
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(x, y, 200);
        pose.scale(scale, -scale, scale);
        pose.mulPose(Axis.XP.rotationDegrees(pitch));
        pose.mulPose(Axis.YP.rotationDegrees(yaw));
        pose.translate(-(box.minX() + box.sizeX() / 2F), -(box.minY() + box.sizeY() / 2F), -(box.minZ() + box.sizeZ() / 2F));
        Lighting.setupForEntityInInventory();
        RenderSystem.enableDepthTest();
        try {
            // The preview looks along -Z after turning the model; a host camera looking that way has yaw + 180.
            scene.get().render(pose, graphics.bufferSource(), Minecraft.getInstance().getFrameTime(), yaw + 180, pitch);
            graphics.flush();
            return true;
        } catch (RuntimeException failure) {
            return false;
        } finally {
            Lighting.setupFor3DItems();
            pose.popPose();
        }
    }

    @SubscribeEvent
    public static void loggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
        SCENES.values().forEach(entry -> { if (entry.scene() != null) entry.scene().close(); });
        SCENES.clear();
        partStates = null;
    }

    private static void build(LegacyRuntime runtime, LegacyScene scene, String id) {
        switch (id) {
            case "research_table" -> {
                // BlockTable: the clicked half takes the neighbour's direction (5, east), the other half its opposite + 4.
                scene.setBlock(0, 0, 0, TC + "blockTable", 5);
                scene.setBlock(1, 0, 0, TC + "blockTable", 8);
            }
            case "infusion_altar" -> {
                place(scene, Map.of('b', new Cell(TC + "blockCosmeticSolid", 7), 'p', new Cell(TC + "blockStoneDevice", 1),
                                's', new Cell(TC + "blockCosmeticSolid", 6), 'm', new Cell(TC + "blockStoneDevice", 2)),
                        new String[]{"b b", " p ", "b b"}, new String[]{"s s", "   ", "s s"}, new String[]{"   ", " m ", "   "});
                form(runtime, scene, (wand, world, x, y, z) -> {
                    if (!wand.fitsInfusionAltar(world, x, y, z)) return false;
                    wand.formInfusionAltar(world, x, y, z);
                    return true;
                });
            }
            case "infernal_furnace" -> {
                place(scene, Map.of('N', new Cell("minecraft:nether_brick", 0), 'O', new Cell("minecraft:obsidian", 0),
                                'L', new Cell("minecraft:lava", 0), 'B', new Cell("minecraft:iron_bars", 0)),
                        new String[]{"NON", "OOO", "NON"}, new String[]{"NON", "OLO", "NBN"}, new String[]{"NON", "O O", "NON"});
                form(runtime, scene, (wand, world, x, y, z) -> {
                    if (!wand.fitsInfernalFurnace(world, x, y, z)) return false;
                    // Its result is not whether it formed: it reports false even after replacing every block.
                    wand.formInfernalFurnace(world, x, y, z);
                    return true;
                });
            }
            case "advanced_alchemy_furnace" -> {
                // WandManager.createAdvancedAlchemicalFurnace: centre 0, lower edges 1, lower corners 4, upper edges 3, upper corners 2.
                for (int x = 0; x < 3; x++) for (int z = 0; z < 3; z++) {
                    boolean corner = x != 1 && z != 1, centre = x == 1 && z == 1;
                    scene.setBlock(x, 0, z, TC + "blockAlchemyFurnace", centre ? 0 : corner ? 4 : 1);
                    if (!centre) scene.setBlock(x, 1, z, TC + "blockAlchemyFurnace", corner ? 2 : 3);
                }
            }
            case "thaumatorium" -> {
                scene.setBlock(0, 0, 0, TC + "blockMetalDevice", 0);
                scene.setBlock(0, 1, 0, TC + "blockMetalDevice", 10);
                scene.setBlock(0, 2, 0, TC + "blockMetalDevice", 11);
                // createThaumatorium faces it towards the clicked side; south shows its front to the default view.
                scene.withWorld(world -> {
                    var tile = runtime.accessor(World.class, world).tile(0, 1, 0);
                    if (tile == null) throw new IllegalStateException("The thaumatorium has no original tile");
                    runtime.accessor(Thaumatorium.class, tile).facing(runtime.accessor(Directions.class).of(3));
                    return null;
                });
            }
            case "node_jar" -> {
                place(scene, Map.of('G', new Cell("minecraft:glass", 0), 'X', new Cell(TC + "blockAiry", 0), 'S', new Cell("minecraft:wooden_slab", 0)),
                        new String[]{"GGG", "GGG", "GGG"}, new String[]{"GGG", "GXG", "GGG"}, new String[]{"GGG", "GGG", "GGG"}, new String[]{"SSS", "SSS", "SSS"});
                scene.withWorld(world -> {
                    var node = runtime.accessor(World.class, world).tile(1, 1, 1);
                    if (node == null) throw new IllegalStateException("The aura node has no original tile");
                    var aspects = runtime.accessor(AspectsFactory.class).create();
                    var list = runtime.accessor(Aspects.class, aspects);
                    var lookup = runtime.accessor(TcAspectAccessors.AspectLookup.class);
                    for (var tag : List.of("aer", "ignis", "aqua", "terra", "ordo", "perditio")) list.add(lookup.find(tag), 20);
                    runtime.accessor(Node.class, node).aspects(aspects);
                    return null;
                });
                form(runtime, scene, (wand, world, x, y, z) -> {
                    if (!wand.fitsNodeJar(world, x, y, z)) return false;
                    wand.formNodeJar(world, x, y, z);
                    return true;
                });
            }
            default -> throw new IllegalArgumentException("Unknown multiblock " + id);
        }
    }

    private record Cell(String key, int metadata) { }

    /** Layers go from the bottom up; in each, rows run north to south and characters west to east. */
    private static void place(LegacyScene scene, Map<Character, Cell> legend, String[]... layers) {
        for (int y = 0; y < layers.length; y++) {
            var rows = layers[y];
            for (int z = 0; z < rows.length; z++) for (int x = 0; x < rows[z].length(); x++) {
                var cell = legend.get(rows[z].charAt(x));
                if (cell != null) scene.setBlock(x, y, z, cell.key(), cell.metadata());
            }
        }
    }

    @FunctionalInterface
    private interface Former { boolean tryAt(Wand wand, LegacyObject world, int x, int y, int z); }

    /** The original searches origins around the clicked block; every origin near the structure covers that. */
    private static void form(LegacyRuntime runtime, LegacyScene scene, Former former) {
        boolean formed = scene.withWorld(world -> {
            var wand = runtime.accessor(Wand.class);
            for (int x = -3; x <= 3; x++) for (int y = -3; y <= 3; y++) for (int z = -3; z <= 3; z++)
                if (former.tryAt(wand, world, x, y, z)) return true;
            return false;
        });
        if (!formed) throw new IllegalStateException("The original check rejected the structure");
    }
}
