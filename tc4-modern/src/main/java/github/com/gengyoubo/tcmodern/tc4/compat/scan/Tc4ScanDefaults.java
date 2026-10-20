package github.com.gengyoubo.tcmodern.tc4.compat.scan;

import github.com.gengyoubo.mfcl.api.*;
import github.com.gengyoubo.tcmodern.api.scan.*;
import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.BlockFamilies;
import net.minecraft.world.item.*;

/** Conservative addon recommendations, not original TC4 assignments for post-1.7 content. */
public final class Tc4ScanDefaults {
    private static final TcScanRegistry REGISTRY = TcScanApi.registry();
    private Tc4ScanDefaults() { }
    public static void install(LegacyRuntime runtime) {
        var nativeItems = new HashMap<String, LegacyStack>();
        for (var item : BuiltInRegistries.ITEM) {
            String id = BuiltInRegistries.ITEM.getKey(item).toString();
            if (!id.startsWith("minecraft:") || item == Items.AIR) continue;
            try {
                runtime.mappings().toLegacy(LegacyMappings.ITEM_STACK, new ItemStack(item)).map(runtime::snapshot)
                        .ifPresent(stack -> nativeItems.put(id, stack));
            } catch (RuntimeException unsupported) { /* New content has no original counterpart. */ }
        }
        for (var block : BuiltInRegistries.BLOCK) {
            String id = BuiltInRegistries.BLOCK.getKey(block).toString();
            if (!id.startsWith("minecraft:") || block.defaultBlockState().isAir()) continue;
            var source = nativeItems.containsKey(id) ? TcScanRegistry.Definition.original(nativeItems.get(id)) : blockValues(id.substring(10));
            if (source != null) REGISTRY.registerDefault(ScanKey.block(id), source);
        }
        // Vanilla's own family table defines the main block of slabs, stairs, walls, doors, etc.
        BlockFamilies.getAllFamilies().forEach(family -> family.getVariants().forEach((variant, block) ->
                parent(BuiltInRegistries.BLOCK.getKey(block).toString(), BuiltInRegistries.BLOCK.getKey(family.getBaseBlock()).toString())));
        for (var block : BuiltInRegistries.BLOCK) {
            String id = BuiltInRegistries.BLOCK.getKey(block).toString();
            if (!id.startsWith("minecraft:")) continue;
            String name = id.substring(10);
            if (name.startsWith("deepslate_") && name.endsWith("_ore")) parent(id, "minecraft:" + name.substring(10));
            // Family bases of post-1.7.10 stone have an obvious original counterpart.
            if (List.of("deepslate","polished_deepslate","deepslate_bricks","deepslate_tiles","tuff","calcite","dripstone_block",
                    "smooth_basalt","blackstone","polished_blackstone","polished_blackstone_bricks").contains(name)) parent(id, "minecraft:stone");
            if (name.equals("cobbled_deepslate")) parent(id, "minecraft:cobblestone");
            var explicit = explicitBlockValues(name);
            // Explicit, so it is installed even where the object already has an original identity (heads map to "skull").
            if (explicit != null) REGISTRY.define(ScanKey.block(id), explicit);
            // Wall heads are separate blocks that drop the standing head.
            if (name.endsWith("_wall_skull") || name.endsWith("_wall_head")) parent(id, "minecraft:" + name.replace("_wall_", "_"));
            if (name.contains("copper") && !name.startsWith("raw_") && !name.equals("copper_ore") && !name.equals("deepslate_copper_ore") && !name.equals("copper_block"))
                parent(id, "minecraft:copper_block");
            for (String wood : List.of("spruce","birch","jungle","acacia","dark_oak","mangrove","cherry","bamboo","crimson","warped")) {
                if (name.equals(wood + "_planks")) parent(id, "minecraft:oak_planks");
                if (name.equals(wood + "_log") || name.equals("stripped_" + wood + "_log") || name.equals(wood + "_wood")
                        || name.equals("stripped_" + wood + "_wood") || name.equals(wood + "_stem") || name.equals(wood + "_hyphae"))
                    parent(id, "minecraft:oak_log");
                if (name.equals(wood + "_leaves")) parent(id, "minecraft:oak_leaves");
            }
        }
        for (var item : BuiltInRegistries.ITEM) {
            String id = BuiltInRegistries.ITEM.getKey(item).toString();
            if (!id.startsWith("minecraft:") || item == Items.AIR) continue;
            var source = item instanceof BlockItem blockItem ? TcScanRegistry.Definition.parent(
                    ScanKey.block(BuiltInRegistries.BLOCK.getKey(blockItem.getBlock()).toString())) : nativeItems.containsKey(id)
                    ? TcScanRegistry.Definition.original(nativeItems.get(id)) : itemValues(item, id.substring(10));
            if (source != null) REGISTRY.registerDefault(ScanKey.item(id), source);
            if (item instanceof BlockItem blockItem && explicitBlockValues(BuiltInRegistries.BLOCK.getKey(blockItem.getBlock()).getPath()) != null)
                REGISTRY.inherit(ScanKey.item(id), ScanKey.block(BuiltInRegistries.BLOCK.getKey(blockItem.getBlock()).toString()));
        }
        for (var type : BuiltInRegistries.ENTITY_TYPE) {
            String id = BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
            if (!id.startsWith("minecraft:")) continue;
            // No catch-all: an entity without a rule must scan as "nothing found", like original TC4.
            var values = entityValues(id.substring(10));
            if (values != null) REGISTRY.registerDefault(ScanKey.entity(id), values);
        }
        String[] ores = {"cinnabar_ore","air_infused_stone","fire_infused_stone","water_infused_stone","earth_infused_stone",
                "order_infused_stone","entropy_infused_stone","amber_bearing_stone"};
        for (int meta = 0; meta < ores.length; meta++) REGISTRY.registerDefault(ScanKey.block("tc4modern:deepslate_" + ores[meta]),
                TcScanRegistry.Definition.original(new LegacyStack("Thaumcraft:blockCustomOre", meta, 1, "")));
    }
    private static void parent(String child, String parent) {
        REGISTRY.replaceDefault(ScanKey.block(child), TcScanRegistry.Definition.parent(ScanKey.block(parent)));
    }
    private static TcScanRegistry.Definition blockValues(String name) {
        if (name.contains("copper")) return values("metallum",4,"permutatio",1);
        if (name.contains("amethyst")) return values("vitreus",4,"praecantatio",2);
        if (name.contains("sculk")) return values("tenebrae",3,"sensus",2,"mortuus",2);
        if (name.equals("ancient_debris")) return values("terra",4,"metallum",4,"ignis",2);
        if (name.contains("netherite")) return values("metallum",32,"ignis",16,"tutamen",16);
        if (name.contains("prismarine") || name.equals("sea_lantern")) return values("aqua",3,"vitreus",2,"lux",2);
        if (name.contains("coral") || name.contains("kelp") || name.contains("seagrass")) return values("herba",2,"aqua",2);
        if (name.contains("ice") || name.equals("powder_snow")) return values("gelum",2,"aqua",1);
        if (name.contains("magma") || name.contains("basalt")) return values("terra",2,"ignis",2);
        if (name.contains("soul")) return values("spiritus",2,"terra",1);
        if (name.contains("honey") || name.equals("bee_nest") || name.equals("beehive")) return values("fames",2,"bestia",1,"arbor",1);
        if (name.contains("mud")) return values("terra",2,"aqua",1);
        if (name.contains("moss") || name.contains("azalea") || name.contains("roots") || name.contains("flower")
                || name.contains("petals") || name.contains("lichen") || name.contains("vine") || name.contains("bamboo")) return values("herba",2);
        if (name.contains("log") || name.contains("wood") || name.contains("stem") || name.contains("planks")
                || name.contains("bookshelf") || name.contains("barrel")) return values("arbor",4);
        if (name.contains("lantern") || name.contains("candle") || name.contains("shroomlight") || name.contains("froglight")) return values("lux",3,"ignis",1);
        if (name.contains("observer") || name.contains("piston") || name.contains("sensor")) return values("machina",3,"potentia",2);
        if (name.contains("glass")) return values("vitreus",2);
        return null;
    }
    /** Requested assignments; these win over the keyword heuristics and over an original counterpart's tags (minecraft namespace only). */
    private static TcScanRegistry.Definition explicitBlockValues(String name) {
        if (name.endsWith("shulker_box")) return values("vacuos",4,"alienis",4,"tutamen",2);
        return switch (name) {
            // Original obsidian is terra 2, ignis 2, tenebrae 1; the crying variant weeps instead of burning.
            case "crying_obsidian" -> values("terra",2,"aqua",2,"tenebrae",1);
            case "bone_block" ->values("mortuus",6,"corpus",3,"tutamen",3);
            case "skeleton_skull" -> values("mortuus",4,"corpus",2);
            case "wither_skeleton_skull" -> values("mortuus",4,"exanimis",2,"ignis",2);
            case "zombie_head" -> values("mortuus",4,"exanimis",2,"humanus",2);
            case "player_head" -> values("humanus",4,"corpus",2);
            case "creeper_head" -> values("mortuus",2,"perditio",2,"ignis",2);
            case "dragon_head" -> values("mortuus",4,"alienis",4,"praecantatio",4);
            case "piglin_head" -> values("mortuus",2,"humanus",2,"lucrum",2);
            case "chain" -> values("metallum",3,"vinculum",2);
            case "lodestone" -> values("terra",4,"metallum",4,"iter",4);
            case "raw_iron_block" -> values("metallum",16,"terra",4);
            case "raw_gold_block" -> values("metallum",12,"lucrum",6,"terra",4);
            case "raw_copper_block" -> values("metallum",12,"permutatio",4,"terra",4);
            default -> null;
        };
    }
    private static TcScanRegistry.Definition itemValues(Item item, String name) {
        switch (name) {
            case "recovery_compass": return values("iter",4,"mortuus",2,"sensus",2);
            case "brush": return values("instrumentum",2,"sensus",2,"cognitio",1);
            case "spyglass": return values("sensus",4,"vitreus",2,"metallum",2);
            case "raw_iron": return values("metallum",2,"terra",1);
            case "raw_gold": return values("metallum",1,"lucrum",1,"terra",1);
            case "raw_copper": return values("metallum",1,"permutatio",1,"terra",1);
            default: break;
        }
        if (name.contains("netherite")) return values("metallum",8,"ignis",4,"tutamen",4);
        if (name.contains("copper")) return values("metallum",4,"permutatio",1);
        if (name.equals("amethyst_shard")) return values("vitreus",2,"praecantatio",1);
        if (name.equals("echo_shard")) return values("sensus",4,"tenebrae",2,"mortuus",2);
        if (name.equals("trident")) return values("telum",6,"aqua",4);
        if (name.equals("totem_of_undying")) return values("victus",8,"spiritus",4,"praecantatio",4);
        if (name.equals("elytra")) return values("volatus",8,"iter",4,"tutamen",2);
        if (name.equals("nautilus_shell") || name.equals("heart_of_the_sea")) return values("aqua",6,"praecantatio",2);
        if (item instanceof ArmorItem) return values("tutamen",4,"metallum",2);
        if (item instanceof TieredItem || item instanceof BowItem || item instanceof CrossbowItem) return values("instrumentum",3,"telum",2);
        if (item instanceof BoatItem) return values("arbor",4,"aqua",2,"iter",2);
        if (item instanceof SpawnEggItem) return values("bestia",2,"spiritus",2);
        if (item instanceof RecordItem) return values("sensus",4,"lucrum",2);
        if (item.isEdible()) return values("victus",Math.max(1,item.getFoodProperties(new ItemStack(item),null).getNutrition()/2),"fames",1);
        if (name.endsWith("_dye")) return values("sensus",1);
        if (name.endsWith("_banner_pattern") || name.contains("pottery_sherd")) return values("sensus",2,"cognitio",1);
        if (name.endsWith("_smithing_template")) return values("fabrico",2,"cognitio",2);
        return null;
    }
    /** Post-1.7.10 vanilla entities only; entities known to the original EntityList keep TC4's own tags. */
    private static TcScanRegistry.Definition entityValues(String name) {
        return switch (name) {
            case "warden" -> values("bestia",16,"tenebrae",8,"sensus",8,"perditio",8);
            case "allay" -> values("volatus",4,"spiritus",4,"vacuos",2);
            case "axolotl" -> values("bestia",2,"aqua",4,"victus",2);
            case "frog" -> values("bestia",2,"aqua",2,"motus",1);
            case "tadpole" -> values("bestia",1,"aqua",2,"victus",1);
            case "goat" -> values("bestia",4,"terra",2,"motus",2);
            case "piglin" -> values("bestia",4,"humanus",4,"lucrum",2);
            case "piglin_brute" -> values("bestia",4,"humanus",4,"telum",4);
            case "hoglin" -> values("bestia",4,"ignis",2,"fames",2);
            case "zoglin" -> values("bestia",4,"exanimis",2,"perditio",2);
            case "strider" -> values("bestia",3,"ignis",4,"iter",2);
            case "glow_squid" -> values("bestia",2,"aqua",4,"lux",4);
            case "phantom" -> values("volatus",4,"exanimis",4,"tenebrae",2);
            case "vex" -> values("volatus",2,"spiritus",4,"telum",2);
            case "shulker" -> values("tutamen",4,"alienis",4,"volatus",2);
            case "shulker_bullet" -> values("volatus",2,"alienis",2);
            case "endermite" -> values("bestia",2,"alienis",2);
            case "sniffer" -> values("bestia",6,"herba",4,"sensus",2);
            case "camel" -> values("bestia",6,"iter",4,"terra",2);
            case "bee" -> values("bestia",2,"volatus",2,"herba",2);
            case "dolphin" -> values("bestia",4,"aqua",4,"motus",2);
            case "turtle" -> values("bestia",4,"aqua",2,"tutamen",2);
            case "cod", "salmon", "tropical_fish" -> values("bestia",1,"aqua",2);
            case "pufferfish" -> values("bestia",1,"aqua",2,"venenum",2);
            case "guardian" -> values("bestia",4,"aqua",4,"sensus",2);
            case "elder_guardian" -> values("bestia",8,"aqua",8,"sensus",4,"tutamen",4);
            case "drowned" -> values("exanimis",4,"aqua",2,"mortuus",2);
            case "husk" -> values("exanimis",4,"terra",2,"mortuus",2);
            case "stray" -> values("exanimis",4,"gelum",2,"mortuus",2);
            case "wither_skeleton" -> values("exanimis",4,"ignis",2,"mortuus",2);
            case "zombie_villager" -> values("exanimis",4,"humanus",2,"mortuus",2);
            case "skeleton_horse" -> values("bestia",4,"exanimis",4,"mortuus",2);
            case "zombie_horse" -> values("bestia",4,"exanimis",4,"mortuus",2);
            case "donkey", "mule" -> values("bestia",4,"terra",2,"iter",2);
            case "llama", "trader_llama" -> values("bestia",4,"terra",2,"pannus",2);
            case "cat" -> values("bestia",3,"sensus",2,"motus",1);
            case "fox" -> values("bestia",3,"motus",2,"sensus",1);
            case "panda" -> values("bestia",6,"herba",2,"fames",2);
            case "parrot" -> values("bestia",2,"volatus",2,"sensus",2);
            case "polar_bear" -> values("bestia",6,"gelum",2,"telum",2);
            case "rabbit" -> values("bestia",1,"motus",2,"terra",1);
            case "pillager", "vindicator" -> values("humanus",4,"telum",2,"perditio",2);
            case "evoker" -> values("humanus",4,"praecantatio",4,"spiritus",2);
            case "illusioner" -> values("humanus",4,"praecantatio",4,"sensus",2);
            case "ravager" -> values("bestia",8,"telum",4,"perditio",4);
            case "wandering_trader" -> values("humanus",4,"lucrum",4,"iter",2);
            case "trident" -> values("telum",4,"aqua",2,"motus",2);
            case "evoker_fangs" -> values("telum",2,"praecantatio",2);
            case "spectral_arrow" -> values("telum",1,"lux",1,"sensus",1);
            default -> null;
        };
    }
    private static TcScanRegistry.Definition values(Object... pairs) {
        var values = new LinkedHashMap<String,Integer>();
        for (int i = 0; i < pairs.length; i += 2) values.put((String)pairs[i], (Integer)pairs[i+1]);
        return TcScanRegistry.Definition.aspects(new AspectValues(values));
    }
}
