package github.com.gengyoubo.tcmodern.api.research;

import github.com.gengyoubo.mfcl.api.LegacyStack;
import github.com.gengyoubo.tcmodern.api.recipe.TcRecipe;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** One detached Thaumonomicon page, as the original {@code ResearchPage} describes it. */
public sealed interface TcResearchPage {
    /**
     * Text by translation key (or literal text when the key has no translation). With {@code requires} non-empty the
     * page shows only once that research is complete. The text keeps the original markup: {@code <BR>}, {@code <LINE>}
     * and {@code <IMG>namespace:path:u:v:width:height:scale</IMG>}.
     */
    record Text(String text, String requires) implements TcResearchPage {
        public Text { text = Objects.requireNonNullElse(text, ""); requires = Objects.requireNonNullElse(requires, ""); }
    }

    /** A picture ({@code namespace:path}) with a caption key. */
    record Image(String image, String caption) implements TcResearchPage {
        public Image { image = Objects.requireNonNullElse(image, ""); caption = Objects.requireNonNullElse(caption, ""); }
    }

    /** The aspects the research teaches, by tag and amount. */
    record Aspects(Map<String, Integer> aspects) implements TcResearchPage {
        public Aspects { aspects = Map.copyOf(aspects); }
    }

    /** Arcane, crucible or infusion recipes; several cycle on one page. */
    record Recipes(List<TcRecipe> recipes) implements TcResearchPage {
        public Recipes { recipes = List.copyOf(recipes); }
    }

    /** Vanilla crafting grids; several cycle on one page. */
    record Crafting(List<Grid> grids) implements TcResearchPage {
        public Crafting { grids = List.copyOf(grids); }
    }

    /**
     * One crafting grid of {@code width * height} slots, each slot a list of accepted stacks (empty for a blank slot,
     * several for an ore name); {@code shapeless} grids list their ingredients in a row.
     */
    record Grid(int width, int height, List<List<LegacyStack>> inputs, LegacyStack output, boolean shapeless) {
        public Grid { inputs = inputs.stream().map(List::copyOf).toList(); }
    }

    record Smelting(LegacyStack input, LegacyStack output) implements TcResearchPage { }

    /** A page kind this library does not describe, such as a multiblock structure; {@code type} names it. */
    record Unsupported(String type) implements TcResearchPage { }
}
