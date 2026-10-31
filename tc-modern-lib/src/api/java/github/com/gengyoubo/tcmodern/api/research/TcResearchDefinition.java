package github.com.gengyoubo.tcmodern.api.research;

import github.com.gengyoubo.mfcl.api.LegacyStack;
import java.util.*;

/**
 * What {@link TcThaumonomicon} registers: one research item with its pages, in the original's terms.
 *
 * @param iconItem    the icon stack; exactly one of it and {@code iconTexture} is set
 * @param iconTexture the icon as {@code namespace:path}
 * @param aspects     tags by aspect tag; at least one unless the research is virtual
 * @param warp        0 for none
 */
public record TcResearchDefinition(String key, String category, int column, int row, int complexity,
                                   LegacyStack iconItem, String iconTexture, Map<String, Integer> aspects,
                                   List<String> parents, List<String> hiddenParents, List<String> siblings,
                                   Set<TcResearchEntry.Flag> flags, List<LegacyStack> itemTriggers, List<String> entityTriggers,
                                   List<String> aspectTriggers, int warp, List<Page> pages) {
    /** A page to build. Recipe pages show every registered recipe of their kind whose output matches. */
    public sealed interface Page { }
    public record Text(String text, String requires) implements Page {
        public Text { Objects.requireNonNull(text); requires = Objects.requireNonNullElse(requires, ""); }
    }
    public record Image(String image, String caption) implements Page {
        public Image { Objects.requireNonNull(image); caption = Objects.requireNonNullElse(caption, ""); }
    }
    public record Aspects(Map<String, Integer> aspects) implements Page {
        public Aspects { aspects = Collections.unmodifiableMap(new LinkedHashMap<>(aspects)); }
    }
    public enum RecipeKind { ARCANE, CRUCIBLE, INFUSION, CRAFTING }
    /** Recipes matched by output item and metadata (and NBT when {@code output} has it). */
    public record Recipe(RecipeKind kind, LegacyStack output) implements Page {
        public Recipe { Objects.requireNonNull(kind); Objects.requireNonNull(output); }
    }
    public record Smelting(LegacyStack input) implements Page {
        public Smelting { Objects.requireNonNull(input); }
    }

    /** A category to register: tab icon and map background as {@code namespace:path}. */
    public record Category(String key, String icon, String background) {
        public Category {
            Objects.requireNonNull(key);
            Objects.requireNonNull(icon);
            background = background == null || background.isEmpty() ? "thaumcraft:textures/gui/gui_researchback.png" : background;
        }
    }

    public TcResearchDefinition {
        Objects.requireNonNull(key);
        Objects.requireNonNull(category);
        if (key.isBlank() || category.isBlank()) throw new IllegalArgumentException("Blank research key or category");
        iconTexture = Objects.requireNonNullElse(iconTexture, "");
        boolean virtual = flags.contains(TcResearchEntry.Flag.VIRTUAL);
        if (!virtual && (iconItem == null) == iconTexture.isEmpty())
            throw new IllegalArgumentException(key + ": a research needs exactly one icon, an item or a texture");
        if (!virtual && aspects.isEmpty()) throw new IllegalArgumentException(key + ": a research needs at least one aspect");
        if (complexity < 1 || complexity > 3) throw new IllegalArgumentException(key + ": complexity is 1 to 3");
        if (warp < 0) throw new IllegalArgumentException(key + ": negative warp");
        aspects = Collections.unmodifiableMap(new LinkedHashMap<>(aspects));
        parents = List.copyOf(parents);
        hiddenParents = List.copyOf(hiddenParents);
        siblings = List.copyOf(siblings);
        flags = flags.isEmpty() ? Set.of() : Collections.unmodifiableSet(EnumSet.copyOf(flags));
        itemTriggers = List.copyOf(itemTriggers);
        entityTriggers = List.copyOf(entityTriggers);
        aspectTriggers = List.copyOf(aspectTriggers);
        pages = List.copyOf(pages);
    }
}
