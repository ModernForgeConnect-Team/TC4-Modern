package github.com.gengyoubo.tcmodern.tc4.compat.jei;

import github.com.gengyoubo.mfcl.api.LegacyRuntime;
import github.com.gengyoubo.mfcl.api.LegacyStack;
import github.com.gengyoubo.tcmodern.tc4.compat.jei.MultiblockCategory.Material;
import github.com.gengyoubo.tcmodern.tc4.compat.jei.MultiblockCategory.Part;
import github.com.gengyoubo.tcmodern.tc4.compat.jei.MultiblockCategory.Placed;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The structures the original WandManager and BlockTable turn into TC4 devices, with the vis WandManager charges
 * (in the wand's units, not centivis). Layers go from the bottom up; in each, rows run north to south and
 * characters west to east. A space is a position the check leaves alone or wants empty.
 */
final class Tc4Multiblocks {
    private static final String TC = "Thaumcraft:";
    private final LegacyRuntime runtime;
    private final JeiRecipeConverter converter;

    private Tc4Multiblocks(LegacyRuntime runtime, JeiRecipeConverter converter) {
        this.runtime = runtime;
        this.converter = converter;
    }

    static List<MultiblockCategory.Recipe> all(LegacyRuntime runtime, JeiRecipeConverter converter, List<ItemStack> wands) {
        return new Tc4Multiblocks(runtime, converter).build(wands);
    }

    private List<MultiblockCategory.Recipe> build(List<ItemStack> wands) {
        var result = new ArrayList<MultiblockCategory.Recipe>();
        var ink = converter.map(new LegacyStack(TC + "ItemInkwell", 0, 1, "")).map(List::of).orElse(List.of());
        result.add(recipe("research_table", "", ink, Map.of(),
                Map.of('T', legacy("blockTable", 0).asTrigger()),
                new String[]{"TT"}));

        var primals = Map.of("aer", 25, "ignis", 25, "aqua", 25, "terra", 25, "ordo", 25, "perditio", 25);
        result.add(recipe("infusion_altar", "INFUSION", wands, primals,
                Map.of('b', legacy("blockCosmeticSolid", 7), 's', legacy("blockCosmeticSolid", 6),
                        'p', legacy("blockStoneDevice", 1), 'm', legacy("blockStoneDevice", 2).asTrigger()),
                new String[]{"b b", " p ", "b b"},
                new String[]{"s s", "   ", "s s"},
                new String[]{"   ", " m ", "   "}));

        result.add(recipe("infernal_furnace", "INFERNALFURNACE", wands, Map.of("ignis", 50, "terra", 50),
                Map.of('N', vanilla(Blocks.NETHER_BRICKS).asTrigger(), 'O', vanilla(Blocks.OBSIDIAN).asTrigger(),
                        'L', new Part(Blocks.LAVA.defaultBlockState(), List.of(new ItemStack(Items.LAVA_BUCKET)), false),
                        'B', vanilla(Blocks.IRON_BARS).asTrigger()),
                new String[]{"NON", "OOO", "NON"},
                new String[]{"NON", "OLO", "NBN"},
                new String[]{"NON", "O O", "NON"}));

        result.add(recipe("advanced_alchemy_furnace", "ADVALCHEMYFURNACE", wands, Map.of("ignis", 50, "aqua", 50, "ordo", 50),
                Map.of('A', legacy("blockMetalDevice", 3).asTrigger(), 'F', legacy("blockStoneDevice", 0),
                        'C', legacy("blockMetalDevice", 9).asTrigger(), 'D', legacy("blockMetalDevice", 1)),
                new String[]{"AAA", "AFA", "AAA"},
                new String[]{"DCD", "C C", "DCD"}));

        result.add(recipe("thaumatorium", "THAUMATORIUM", wands, Map.of("ignis", 15, "ordo", 30, "aqua", 30),
                Map.of('R', legacy("blockMetalDevice", 0), 'C', legacy("blockMetalDevice", 9).asTrigger()),
                new String[]{"R"},
                new String[]{"C"},
                new String[]{"C"}));

        var slabs = BuiltInRegistries.ITEM.getTag(ItemTags.WOODEN_SLABS).map(tag -> tag.stream().map(ItemStack::new).toList())
                .filter(list -> !list.isEmpty()).orElse(List.of(new ItemStack(Items.OAK_SLAB)));
        var allPrimals = Map.of("aer", 70, "ignis", 70, "aqua", 70, "terra", 70, "ordo", 70, "perditio", 70);
        result.add(recipe("node_jar", "NODEJAR", wands, allPrimals,
                Map.of('G', vanilla(Blocks.GLASS).asTrigger(), 'X', legacy("blockAiry", 0),
                        'S', new Part(Blocks.OAK_SLAB.defaultBlockState(), slabs, false)),
                new String[]{"GGG", "GGG", "GGG"},
                new String[]{"GGG", "GXG", "GGG"},
                new String[]{"GGG", "GGG", "GGG"},
                new String[]{"SSS", "SSS", "SSS"}));
        return result;
    }

    private MultiblockCategory.Recipe recipe(String id, String research, List<ItemStack> tools, Map<String, Integer> cost,
                                             Map<Character, Part> legend, String[]... layers) {
        var blocks = new ArrayList<Placed>();
        var counts = new LinkedHashMap<Character, Integer>();
        int sizeX = 0, sizeZ = layers[0].length;
        for (int y = 0; y < layers.length; y++) {
            for (int z = 0; z < layers[y].length; z++) {
                var row = layers[y][z];
                sizeX = Math.max(sizeX, row.length());
                for (int x = 0; x < row.length(); x++) {
                    char symbol = row.charAt(x);
                    var part = legend.get(symbol);
                    if (part == null) continue;
                    blocks.add(new Placed(x, y, z, part));
                    counts.merge(symbol, 1, Integer::sum);
                }
            }
        }
        var materials = counts.entrySet().stream().map(entry -> new Material(legend.get(entry.getKey()), entry.getValue())).toList();
        // Primals in their original order, so every structure lists them alike.
        var aspects = List.of("aer", "ignis", "aqua", "terra", "ordo", "perditio").stream().filter(cost::containsKey)
                .map(tag -> JeiAspect.of(tag).withAmount(cost.get(tag))).toList();
        return new MultiblockCategory.Recipe(id, research, blocks, sizeX, layers.length, sizeZ, materials, tools, aspects);
    }

    private ItemStack item(String name, int meta) { return converter.map(new LegacyStack(TC + name, meta, 1, "")).orElse(ItemStack.EMPTY); }

    private Part legacy(String name, int meta) {
        BlockState state;
        try {
            state = runtime.hostBlock(TC + name, meta, BlockState.class).orElse(null);
        } catch (RuntimeException unsupported) {
            state = null;
        }
        return new Part(state, Optional.of(item(name, meta)).filter(stack -> !stack.isEmpty()).map(List::of).orElse(List.of()), false);
    }

    private static Part vanilla(Block block) { return new Part(block.defaultBlockState(), List.of(new ItemStack(block)), false); }
}
