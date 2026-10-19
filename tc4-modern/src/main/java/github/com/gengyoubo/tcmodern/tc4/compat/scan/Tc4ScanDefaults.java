package github.com.gengyoubo.tcmodern.tc4.compat.scan;

import github.com.gengyoubo.mfcl.api.*;
import github.com.gengyoubo.tcmodern.api.scan.*;
import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.BlockFamilies;
import net.minecraft.world.entity.MobCategory;
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
            REGISTRY.registerDefault(ScanKey.block(id), nativeItems.containsKey(id)
                    ? TcScanRegistry.Definition.original(nativeItems.get(id)) : blockValues(id.substring(10)));
        }
        // Vanilla's own family table defines the main block of slabs, stairs, walls, doors, etc.
        BlockFamilies.getAllFamilies().forEach(family -> family.getVariants().forEach((variant, block) ->
                parent(BuiltInRegistries.BLOCK.getKey(block).toString(), BuiltInRegistries.BLOCK.getKey(family.getBaseBlock()).toString())));
        for (var block : BuiltInRegistries.BLOCK) {
            String id = BuiltInRegistries.BLOCK.getKey(block).toString();
            if (!id.startsWith("minecraft:")) continue;
            String name = id.substring(10);
            if (name.startsWith("deepslate_") && name.endsWith("_ore")) parent(id, "minecraft:" + name.substring(10));
            if (name.contains("copper") && !name.equals("copper_ore") && !name.equals("deepslate_copper_ore") && !name.equals("copper_block"))
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
            REGISTRY.registerDefault(ScanKey.item(id), source);
        }
        for (var type : BuiltInRegistries.ENTITY_TYPE) {
            String id = BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
            if (!id.startsWith("minecraft:")) continue;
            var category = type.getCategory();
            var values = switch (id.substring(10)) {
                case "warden" -> values("bestia",16,"tenebrae",8,"sensus",8,"perditio",8);
                case "allay" -> values("volatus",4,"spiritus",4,"vacuos",2);
                case "axolotl" -> values("bestia",2,"aqua",4,"victus",2);
                case "frog", "tadpole" -> values("bestia",2,"aqua",2,"motus",1);
                case "goat" -> values("bestia",4,"terra",2,"motus",2);
                case "piglin", "piglin_brute" -> values("bestia",4,"humanus",4,"lucrum",2);
                case "hoglin", "zoglin" -> values("bestia",4,"ignis",2,"fames",2);
                case "glow_squid" -> values("bestia",2,"aqua",4,"lux",4);
                case "phantom", "vex" -> values("volatus",4,"exanimis",4,"perditio",2);
                case "shulker" -> values("tutamen",4,"alienis",4,"volatus",2);
                case "endermite" -> values("bestia",2,"alienis",2);
                case "sniffer" -> values("bestia",6,"herba",4,"sensus",2);
                case "camel" -> values("bestia",6,"iter",4,"terra",2);
                case "bee" -> values("bestia",2,"volatus",2,"herba",2);
                case "dolphin" -> values("bestia",4,"aqua",4,"motus",2);
                case "turtle" -> values("bestia",4,"aqua",2,"tutamen",2);
                case "trident" -> values("telum",4,"aqua",2,"motus",2);
                default -> category == MobCategory.MONSTER ? values("bestia",4,"perditio",2)
                        : category == MobCategory.WATER_CREATURE || category == MobCategory.WATER_AMBIENT || category == MobCategory.UNDERGROUND_WATER_CREATURE
                        ? values("bestia",2,"aqua",2) : category == MobCategory.MISC ? values("motus",1) : values("bestia",4,"victus",2);
            };
            REGISTRY.registerDefault(ScanKey.entity(id), values);
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
        return values("terra",2);
    }
    private static TcScanRegistry.Definition itemValues(Item item, String name) {
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
        return values("permutatio",1);
    }
    private static TcScanRegistry.Definition values(Object... pairs) {
        var values = new LinkedHashMap<String,Integer>();
        for (int i = 0; i < pairs.length; i += 2) values.put((String)pairs[i], (Integer)pairs[i+1]);
        return TcScanRegistry.Definition.aspects(new AspectValues(values));
    }
}
