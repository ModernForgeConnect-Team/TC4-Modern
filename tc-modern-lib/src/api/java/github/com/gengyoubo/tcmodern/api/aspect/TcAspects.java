package github.com.gengyoubo.tcmodern.api.aspect;

import github.com.gengyoubo.mfcl.api.LegacyObject;
import github.com.gengyoubo.mfcl.api.LegacyRuntime;
import github.com.gengyoubo.tcmodern.api.TcModernApi;
import java.util.ArrayList;
import java.util.List;
import static github.com.gengyoubo.tcmodern.api.aspect.TcAspectAccessors.*;

/** Reads the original aspect registry exclusively through MFCL's public MLA API. */
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
