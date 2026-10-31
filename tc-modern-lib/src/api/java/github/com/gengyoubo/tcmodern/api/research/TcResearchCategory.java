package github.com.gengyoubo.tcmodern.api.research;

import java.util.List;
import java.util.Objects;

/**
 * Detached description of one Thaumonomicon category, its research in registration order.
 *
 * @param icon       the tab icon as {@code namespace:path}
 * @param background the map background as {@code namespace:path}
 */
public record TcResearchCategory(String key, String icon, String background, List<TcResearchEntry> entries) {
    public TcResearchCategory {
        Objects.requireNonNull(key);
        icon = Objects.requireNonNullElse(icon, "");
        background = Objects.requireNonNullElse(background, "");
        entries = List.copyOf(entries);
    }
}
