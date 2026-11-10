package github.com.gengyoubo.mfcl.content;

import github.com.gengyoubo.mfcl.bridge.LegacyClientData;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import java.util.Map;

/** Explicit flattening cases. Unknown variants stay visible as unmapped instead of silently changing identity. */
public final class HostStackMapping {
    private static final String[] WOODS = {"oak", "spruce", "birch", "jungle", "acacia", "dark_oak"};
    private static final String[] COLORS = {"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"};
    private static final Map<String, String> RENAMES = Map.ofEntries(
            Map.entry("mob_spawner", "spawner"), Map.entry("wooden_door", "oak_door"), Map.entry("reeds", "sugar_cane"),
            Map.entry("grass", "grass_block"), Map.entry("yellow_flower", "dandelion"), Map.entry("waterlily", "lily_pad"),
            Map.entry("nether_brick", "nether_bricks"), Map.entry("netherbrick", "nether_brick"), Map.entry("brick_stairs", "brick_stairs"),
            Map.entry("fireworks", "firework_rocket"), Map.entry("firework_charge", "firework_star"), Map.entry("fireball", "fire_charge"),
            Map.entry("slime", "slime_block"), Map.entry("noteblock", "note_block"), Map.entry("golden_apple", "golden_apple"),
            Map.entry("snow", "snow_block"), Map.entry("melon_block", "melon"), Map.entry("fence", "oak_fence"),
            Map.entry("fence_gate", "oak_fence_gate"), Map.entry("boat", "oak_boat"), Map.entry("sign", "oak_sign"),
            Map.entry("wooden_button", "oak_button"), Map.entry("wooden_pressure_plate", "oak_pressure_plate"), Map.entry("trapdoor", "oak_trapdoor"),
            Map.entry("speckled_melon", "glistering_melon_slice"), Map.entry("melon", "melon_slice"), Map.entry("snow_layer", "snow"),
            Map.entry("brick_block", "bricks"), Map.entry("stonebrick", "stone_bricks"), Map.entry("stone_stairs", "cobblestone_stairs"),
            Map.entry("hardened_clay", "terracotta"), Map.entry("double_plant", "sunflower"), Map.entry("deadbush", "dead_bush"),
            Map.entry("golden_rail", "powered_rail"), Map.entry("slime_ball", "slime_ball"), Map.entry("web", "cobweb"),
            Map.entry("record_13", "music_disc_13"), Map.entry("record_cat", "music_disc_cat"),
            Map.entry("record_blocks", "music_disc_blocks"), Map.entry("record_chirp", "music_disc_chirp"),
            Map.entry("record_far", "music_disc_far"), Map.entry("record_mall", "music_disc_mall"),
            Map.entry("record_mellohi", "music_disc_mellohi"), Map.entry("record_stal", "music_disc_stal"),
            Map.entry("record_strad", "music_disc_strad"), Map.entry("record_ward", "music_disc_ward"),
            Map.entry("record_11", "music_disc_11"), Map.entry("record_wait", "music_disc_wait"),
            Map.entry("lit_pumpkin", "jack_o_lantern"));
    private HostStackMapping() {}
    public static ItemStack renderStack(LegacyClientData.Stack old) {
        String key = old.key(); int meta = old.metadata();
        if (key.startsWith("mfcl_host:")) key = key.substring(10).replaceFirst("/", ":");
        else if (key.startsWith("minecraft:")) {
            String path = key.substring(10);
            if ((path.equals("planks") || path.equals("sapling")) && meta >= 0 && meta < WOODS.length) path = WOODS[meta] + (path.equals("planks") ? "_planks" : "_sapling");
            else if ((path.equals("log") || path.equals("leaves")) && meta >= 0 && meta < 4) path = WOODS[meta] + (path.equals("log") ? "_log" : "_leaves");
            else if ((path.equals("log2") || path.equals("leaves2")) && meta >= 0 && meta < 2) path = WOODS[meta + 4] + (path.equals("log2") ? "_log" : "_leaves");
            else if (path.equals("wool") && meta >= 0 && meta < COLORS.length) path = COLORS[meta] + "_wool";
            else if (path.equals("stained_glass") && meta >= 0 && meta < 16) path = COLORS[meta] + "_stained_glass";
            else if (path.equals("stained_glass_pane") && meta >= 0 && meta < 16) path = COLORS[meta] + "_stained_glass_pane";
            else if (path.equals("stained_hardened_clay") && meta >= 0 && meta < 16) path = COLORS[meta] + "_terracotta";
            else if (path.equals("carpet") && meta >= 0 && meta < 16) path = COLORS[meta] + "_carpet";
            else if (path.equals("golden_apple") && meta == 1) path = "enchanted_golden_apple";
            else if (path.equals("bed") && meta == 0) path = "red_bed";
            else if (path.equals("double_stone_slab") && meta == 8) path = "smooth_stone";
            else if (variants(path) != null && meta >= 0 && meta < variants(path).length) path = variants(path)[meta];
            else if (meta != 0 && !isDamageable(key)) return ItemStack.EMPTY;
            else path = RENAMES.getOrDefault(path, path);
            key = "minecraft:" + path;
        } else if (meta != 0) return ItemStack.EMPTY;
        ResourceLocation id = ResourceLocation.tryParse(key);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) return ItemStack.EMPTY;
        ItemStack result = new ItemStack(BuiltInRegistries.ITEM.get(id), Math.max(1, old.count()));
        // Item.canBeDepleted avoids ItemStack.isDamageableItem, which other mods hook and may call into unfilled registries during mod construction.
        if (result.getItem().canBeDepleted()) result.setDamageValue(meta);
        // Legacy NBT is retained by the bridge; it is not copied blindly into a modern item for display.
        return result;
    }
    private static String[] variants(String key) {
        return switch (key) {
            case "dirt" -> new String[]{"dirt", "coarse_dirt", "podzol"};
            case "sand" -> new String[]{"sand", "red_sand"};
            case "sandstone" -> new String[]{"sandstone", "chiseled_sandstone", "cut_sandstone"};
            case "stonebrick" -> new String[]{"stone_bricks", "mossy_stone_bricks", "cracked_stone_bricks", "chiseled_stone_bricks"};
            case "monster_egg" -> new String[]{"infested_stone", "infested_cobblestone", "infested_stone_bricks", "infested_mossy_stone_bricks", "infested_cracked_stone_bricks", "infested_chiseled_stone_bricks"};
            case "quartz_block" -> new String[]{"quartz_block", "chiseled_quartz_block", "quartz_pillar"};
            case "double_plant" -> new String[]{"sunflower", "lilac", "tall_grass", "large_fern", "rose_bush", "peony"};
            case "tallgrass" -> new String[]{"dead_bush", "grass", "fern"};
            case "red_flower" -> new String[]{"poppy", "blue_orchid", "allium", "azure_bluet", "red_tulip", "orange_tulip", "white_tulip", "pink_tulip", "oxeye_daisy"};
            case "stone_slab" -> new String[]{"smooth_stone_slab", "sandstone_slab", "petrified_oak_slab", "cobblestone_slab", "brick_slab", "stone_brick_slab", "nether_brick_slab", "quartz_slab"};
            case "wooden_slab" -> new String[]{"oak_slab", "spruce_slab", "birch_slab", "jungle_slab", "acacia_slab", "dark_oak_slab"};
            case "coal" -> new String[]{"coal", "charcoal"};
            case "fish" -> new String[]{"cod", "salmon", "tropical_fish", "pufferfish"};
            case "cooked_fished" -> new String[]{"cooked_cod", "cooked_salmon"};
            case "dye" -> new String[]{"ink_sac", "red_dye", "green_dye", "cocoa_beans", "lapis_lazuli", "purple_dye", "cyan_dye", "light_gray_dye", "gray_dye", "pink_dye", "lime_dye", "yellow_dye", "light_blue_dye", "magenta_dye", "orange_dye", "bone_meal"};
            case "skull" -> new String[]{"skeleton_skull", "wither_skeleton_skull", "zombie_head", "player_head", "creeper_head"};
            default -> null;
        };
    }
    /** Non-creative old item variants cannot be found by reversing the old creative catalog. */
    static LegacyClientData.Stack nonCreativeStack(ItemStack stack) {
        if (!stack.is(net.minecraft.world.item.Items.SMOOTH_STONE)) return null;
        return new LegacyClientData.Stack("minecraft:double_stone_slab", 8, stack.getCount(), stack.getHoverName().getString(), "");
    }
    private static boolean isDamageable(String key) {
        ResourceLocation id = ResourceLocation.tryParse(key);
        return id != null && BuiltInRegistries.ITEM.containsKey(id) && BuiltInRegistries.ITEM.get(id).canBeDepleted();
    }
}
