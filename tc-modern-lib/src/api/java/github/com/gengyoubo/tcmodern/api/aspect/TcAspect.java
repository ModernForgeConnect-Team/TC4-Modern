package github.com.gengyoubo.tcmodern.api.aspect;

import java.util.List;
import java.util.Objects;

/**
 * Detached description of one original TC4 aspect.
 *
 * @param texture    the aspect icon as {@code namespace:path}, or empty when the original has none
 * @param components the two aspects it is made of, empty for a primal aspect
 */
public record TcAspect(String tag, int color, String texture, List<String> components) {
    public TcAspect {
        Objects.requireNonNull(tag);
        if (tag.isBlank()) throw new IllegalArgumentException("Blank aspect tag");
        texture = Objects.requireNonNullElse(texture, "");
        components = List.copyOf(components);
    }

    public boolean primal() { return components.isEmpty(); }
}
