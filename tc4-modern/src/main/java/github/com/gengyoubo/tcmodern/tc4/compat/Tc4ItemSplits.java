package github.com.gengyoubo.tcmodern.tc4.compat;

import github.com.gengyoubo.mfcl.api.spi.LegacyItemProvider;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import net.minecraft.nbt.CompoundTag;

/**
 * One host item per original subtype, so host recipes, tags, recipe viewers and item-keyed mods (EMC, sorting, filters)
 * tell them apart. Old code still sees the single original item; MFCL keeps its metadata and NBT intact.
 */
public final class Tc4ItemSplits implements LegacyItemProvider {
    static final String WAND = "Thaumcraft:WandCasting";
    /** TC4 4.2.3.5 ConfigItems. The original registries are not readable this early, so these are the fixed builds. */
    static final List<String> CAPS = List.of("iron", "gold", "thaumium", "void", "copper", "silver");
    static final List<String> RODS = List.of("wood", "greatwood", "obsidian", "blaze", "ice", "quartz", "bone", "reed", "silverwood",
            "greatwood_staff", "obsidian_staff", "blaze_staff", "ice_staff", "quartz_staff", "bone_staff", "reed_staff",
            "silverwood_staff", "primal_staff");
    private static final Pattern NAME = Pattern.compile("[a-z0-9_.-]+");
    private static final Pattern PART = Pattern.compile("[{,]\\s*(cap|rod|sceptre)\\s*:\\s*\"?([^\",}]*)");

    @Override public ItemSplit split(String legacyKey, ItemDeclaration declaration) {
        if (!legacyKey.startsWith("Thaumcraft:")) return null;
        if (legacyKey.equals(WAND)) return wands(declaration);
        // Damageable items keep durability in metadata; a single subtype has nothing to separate.
        if (declaration.maxDamage() > 0 || declaration.metadata().size() < 2) return null;
        return LegacyItemProvider.byMetadata(declaration);
    }

    /** Wands, staves and sceptres: the damage is the stored craft cost and vis is charge, so only the build is identity. */
    static ItemSplit wands(ItemDeclaration declaration) {
        Set<String> names = new LinkedHashSet<>(builtInVariants());
        // Builds an addon lists in the creative tab; builds no one lists stay on the original host item.
        for (var stack : declaration.catalog()) {
            String name = variant(stack.snbt());
            if (name != null) names.add(name);
        }
        var variants = List.copyOf(names);
        return new ItemSplit() {
            @Override public List<String> variants() { return variants; }
            @Override public String select(Stack stack) {
                String name = stack.hostTag() instanceof CompoundTag tag
                        ? variant(tag.getString("cap"), tag.getString("rod"), tag.getByte("sceptre") == 1) : null;
                return name != null && names.contains(name) ? name : null;
            }
        };
    }

    /** {@code <rod>_<cap>[_sceptre]}, or null when the build has no valid host name. */
    static String variant(String cap, String rod, boolean sceptre) {
        if (cap.isEmpty() || rod.isEmpty()) return null;
        String name = rod + "_" + cap + (sceptre ? "_sceptre" : "");
        return NAME.matcher(name).matches() ? name : null;
    }

    static String variant(String snbt) {
        String cap = "", rod = "";
        boolean sceptre = false;
        var parts = PART.matcher(snbt);
        while (parts.find()) {
            switch (parts.group(1)) {
                case "cap" -> cap = parts.group(2);
                case "rod" -> rod = parts.group(2);
                default -> sceptre = parts.group(2).matches("1[bB]?");
            }
        }
        return variant(cap, rod, sceptre);
    }

    /** Every cap with every rod, as wand and as sceptre: what the original wand and sceptre recipes can craft. */
    static List<String> builtInVariants() {
        var result = new ArrayList<String>();
        for (String rod : RODS) for (String cap : CAPS) for (boolean sceptre : new boolean[]{false, true}) result.add(variant(cap, rod, sceptre));
        return result;
    }
}
