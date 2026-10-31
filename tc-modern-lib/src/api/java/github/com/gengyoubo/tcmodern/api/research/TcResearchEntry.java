package github.com.gengyoubo.tcmodern.api.research;

import github.com.gengyoubo.mfcl.api.LegacyStack;
import java.util.*;

/**
 * Detached description of one original research item.
 *
 * @param column      grid column in its category; one unit is one research slot
 * @param complexity  1..3; sets the research note's size
 * @param iconItem    the icon stack, or null when the icon is a texture
 * @param iconTexture the icon as {@code namespace:path}, or empty
 * @param aspects     tags by aspect: the note's fixed aspects and the price of buying it
 * @param warp        warp the research gives on completion
 */
public record TcResearchEntry(String key, String category, int column, int row, int complexity,
                              LegacyStack iconItem, String iconTexture, Map<String, Integer> aspects,
                              List<String> parents, List<String> hiddenParents, List<String> siblings, Set<Flag> flags,
                              List<LegacyStack> itemTriggers, List<String> entityTriggers, List<String> aspectTriggers,
                              int warp, List<TcResearchPage> pages) {
    /** The original {@code ResearchItem} switches. */
    public enum Flag { SPECIAL, SECONDARY, ROUND, STUB, VIRTUAL, CONCEALED, HIDDEN, LOST, AUTO_UNLOCK }

    public TcResearchEntry {
        Objects.requireNonNull(key);
        Objects.requireNonNull(category);
        iconTexture = Objects.requireNonNullElse(iconTexture, "");
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

    public boolean has(Flag flag) { return flags.contains(flag); }

    /** All requisites, shown and hidden. */
    public List<String> requisites() {
        var all = new ArrayList<>(parents);
        all.addAll(hiddenParents);
        return all;
    }
}
