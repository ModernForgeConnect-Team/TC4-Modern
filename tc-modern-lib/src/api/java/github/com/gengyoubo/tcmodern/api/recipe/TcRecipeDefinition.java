package github.com.gengyoubo.tcmodern.api.recipe;

import github.com.gengyoubo.mfcl.api.LegacyStack;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A new original TC4 recipe, described as detached data and added by {@link TcRecipeRegistrar}. Items are original
 * stacks (host-only items first go through MFCL's host representations); aspects are original tags with amounts.
 */
public sealed interface TcRecipeDefinition {
    /** The research that unlocks the recipe, as the original checks it. */
    String research();
    /** Aspect costs by original tag, each at least 1. */
    Map<String, Integer> aspects();

    /** One input slot: a concrete original stack or every stack of an ore dictionary name. */
    sealed interface Ingredient {
        record Item(LegacyStack stack) implements Ingredient {
            public Item { Objects.requireNonNull(stack); }
        }
        record Ore(String name) implements Ingredient {
            public Ore { if (Objects.requireNonNull(name).isBlank()) throw new IllegalArgumentException("Blank ore dictionary name"); }
        }
    }

    /** Arcane workbench, shaped: 1-3 rows of equal width up to 3; a space is an empty slot. */
    record ArcaneShaped(String research, List<String> pattern, Map<Character, Ingredient> key, Map<String, Integer> aspects,
                        LegacyStack result, boolean mirrored) implements TcRecipeDefinition {
        public ArcaneShaped {
            research = requireResearch(research);
            pattern = List.copyOf(pattern);
            key = Map.copyOf(key);
            aspects = requireAspects(aspects);
            Objects.requireNonNull(result);
            if (pattern.isEmpty() || pattern.size() > 3) throw new IllegalArgumentException("Pattern needs 1 to 3 rows");
            int width = pattern.get(0).length();
            if (width < 1 || width > 3 || pattern.stream().anyMatch(row -> row.length() != width))
                throw new IllegalArgumentException("Pattern rows must share a width of 1 to 3");
            if (key.containsKey(' ')) throw new IllegalArgumentException("Space is reserved for empty slots");
            var used = new java.util.HashSet<Character>();
            for (String row : pattern) for (char symbol : row.toCharArray()) if (symbol != ' ') {
                if (!key.containsKey(symbol)) throw new IllegalArgumentException("Pattern symbol '" + symbol + "' has no key");
                used.add(symbol);
            }
            if (used.isEmpty()) throw new IllegalArgumentException("Pattern has no inputs");
            if (!used.containsAll(key.keySet())) throw new IllegalArgumentException("Key defines symbols the pattern does not use");
        }
    }

    /** Arcane workbench, shapeless: 1-9 inputs. */
    record ArcaneShapeless(String research, List<Ingredient> ingredients, Map<String, Integer> aspects, LegacyStack result)
            implements TcRecipeDefinition {
        public ArcaneShapeless {
            research = requireResearch(research);
            ingredients = List.copyOf(ingredients);
            aspects = requireAspects(aspects);
            Objects.requireNonNull(result);
            if (ingredients.isEmpty() || ingredients.size() > 9) throw new IllegalArgumentException("Shapeless recipes take 1 to 9 inputs");
        }
    }

    /** Crucible: the catalyst dropped into a crucible holding the aspects. */
    record Crucible(String research, Ingredient catalyst, Map<String, Integer> aspects, LegacyStack result) implements TcRecipeDefinition {
        public Crucible {
            research = requireResearch(research);
            Objects.requireNonNull(catalyst);
            aspects = requireAspects(aspects);
            Objects.requireNonNull(result);
            if (aspects.isEmpty()) throw new IllegalArgumentException("Crucible recipes need aspects");
        }
    }

    /** Infusion altar: the central item on the matrix pedestal and the components around it (no ore names, as in the original). */
    record Infusion(String research, LegacyStack input, List<LegacyStack> components, Map<String, Integer> aspects,
                    LegacyStack result, int instability) implements TcRecipeDefinition {
        public Infusion {
            research = requireResearch(research);
            Objects.requireNonNull(input);
            components = List.copyOf(components);
            aspects = requireAspects(aspects);
            Objects.requireNonNull(result);
            if (components.isEmpty()) throw new IllegalArgumentException("Infusion needs at least one component");
            if (instability < 0) throw new IllegalArgumentException("Negative instability");
        }
    }

    private static String requireResearch(String research) {
        if (Objects.requireNonNull(research, "research").isBlank()) throw new IllegalArgumentException("Blank research key");
        return research;
    }

    private static Map<String, Integer> requireAspects(Map<String, Integer> aspects) {
        var copy = new LinkedHashMap<String, Integer>();
        aspects.forEach((tag, amount) -> {
            if (tag == null || tag.isBlank() || amount == null || amount < 1) throw new IllegalArgumentException("Invalid aspect cost " + tag + "=" + amount);
            copy.put(tag, amount);
        });
        return java.util.Collections.unmodifiableMap(copy);
    }
}
