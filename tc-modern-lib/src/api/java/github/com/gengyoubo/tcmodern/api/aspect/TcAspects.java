package github.com.gengyoubo.tcmodern.api.aspect;

import github.com.gengyoubo.mfcl.api.LegacyObject;
import github.com.gengyoubo.mfcl.api.LegacyRuntime;
import github.com.gengyoubo.tcmodern.api.TcModernApi;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import static github.com.gengyoubo.tcmodern.api.aspect.TcAspectAccessors.*;

/** Reads and extends the original aspect registry exclusively through MFCL's public MLA API. */
public final class TcAspects {
    private TcAspects() { }

    /**
     * Every registered aspect in the original registration order, primals first. Empty when the runtime is closed
     * or Thaumcraft is absent; an aspect that cannot be read is skipped rather than hiding the rest.
     */
    public static List<TcAspect> snapshot(LegacyRuntime runtime) {
        if (!runtime.isOpen() || !runtime.loadedMods().contains(TcModernApi.LEGACY_MOD_ID)) return List.of();
        var copy = runtime.accessor(MapFactory.class).copy(runtime.accessor(Registry.class).aspects());
        var values = runtime.accessor(CollectionView.class, runtime.accessor(MapView.class, copy).values()).elements();
        var result = new ArrayList<TcAspect>();
        for (LegacyObject value : values) {
            try {
                result.add(read(runtime, value));
            } catch (RuntimeException unreadable) {
                // A malformed addon aspect must not hide the original ones.
            }
        }
        return List.copyOf(result);
    }

    /**
     * Adds a compound aspect made of two registered aspects to the original registry. Register from
     * {@link TcModernApi#whenReady} on every side, before anything snapshots the registry.
     *
     * @param tag     lowercase original tag, for example {@code "modernitas"}; its names come from the
     *                {@code tc.aspect.<tag>} translations
     * @param texture the icon as {@code namespace:path}, a white glyph that is tinted with {@code color}
     * @throws IllegalArgumentException when the tag is taken, a component is missing, or another compound already
     *                                  combines the same pair (the research table could not tell them apart)
     * @throws IllegalStateException    when the runtime is closed or Thaumcraft is absent
     */
    public static TcAspect register(LegacyRuntime runtime, String tag, int color, String first, String second, String texture) {
        if (!runtime.isOpen() || !runtime.loadedMods().contains(TcModernApi.LEGACY_MOD_ID))
            throw new IllegalStateException("Thaumcraft runtime is not available");
        if (!tag.matches("[a-z][a-z0-9_]*")) throw new IllegalArgumentException("Invalid aspect tag: " + tag);
        int colon = texture.indexOf(':');
        if (colon <= 0 || colon == texture.length() - 1 || !texture.equals(texture.toLowerCase(Locale.ROOT)))
            throw new IllegalArgumentException("Expected a lowercase namespace:path texture: " + texture);
        var lookup = runtime.accessor(AspectLookup.class);
        if (lookup.find(tag) != null) throw new IllegalArgumentException("Aspect already registered: " + tag);
        var components = new LegacyObject[]{component(lookup, first), component(lookup, second)};
        var pair = sorted(List.of(first, second));
        for (var existing : snapshot(runtime))
            if (sorted(existing.components()).equals(pair))
                throw new IllegalArgumentException(first + " + " + second + " already make " + existing.tag());
        var image = runtime.accessor(ResourceFactory.class).create(texture.substring(0, colon), texture.substring(colon + 1));
        // Blend 1 is what the original's own aspects use.
        return read(runtime, runtime.accessor(AspectFactory.class).create(tag, color, components, image, 1));
    }

    private static List<String> sorted(List<String> tags) { return tags.stream().sorted().toList(); }

    private static LegacyObject component(AspectLookup lookup, String tag) {
        var aspect = lookup.find(tag);
        if (aspect == null) throw new IllegalArgumentException("Unknown component aspect: " + tag);
        return aspect;
    }

    private static TcAspect read(LegacyRuntime runtime, LegacyObject value) {
        var aspect = runtime.accessor(Aspect.class, value);
        String texture = "";
        var image = aspect.image();
        if (image != null) {
            var resource = runtime.accessor(Resource.class, image);
            texture = resource.namespace() + ":" + resource.path();
        }
        var components = new ArrayList<String>();
        var parts = aspect.components();
        if (parts != null) for (var part : parts) if (part != null) components.add(runtime.accessor(Aspect.class, part).tag());
        return new TcAspect(aspect.tag(), aspect.color(), texture, components);
    }
}
