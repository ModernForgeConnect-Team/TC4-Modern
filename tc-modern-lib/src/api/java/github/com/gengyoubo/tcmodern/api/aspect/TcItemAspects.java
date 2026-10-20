package github.com.gengyoubo.tcmodern.api.aspect;

import github.com.gengyoubo.mfcl.api.LegacyObject;
import github.com.gengyoubo.mfcl.api.LegacyRuntime;
import github.com.gengyoubo.mfcl.api.LegacyStack;
import github.com.gengyoubo.tcmodern.api.scan.AspectValues;
import java.util.LinkedHashMap;
import java.util.Optional;
import static github.com.gengyoubo.tcmodern.api.aspect.TcAspectAccessors.*;

/**
 * The aspects TC4 assigns to an original item, object tags plus bonus tags: what the alchemical furnace boils it
 * into and what the thaumometer reports. Read exclusively through MFCL's public MLA API.
 */
public final class TcItemAspects {
    private TcItemAspects() { }

    public static Optional<AspectValues> of(LegacyRuntime runtime, LegacyStack stack) {
        return of(runtime, runtime.stack(stack));
    }

    /** Empty when the item has no aspects, which also means the alchemical furnace refuses it. */
    public static Optional<AspectValues> of(LegacyRuntime runtime, LegacyObject stack) {
        if (stack == null) return Optional.empty();
        var crafting = runtime.accessor(Crafting.class);
        var tags = crafting.bonus(stack, crafting.tags(stack));
        if (tags == null) return Optional.empty();
        var list = runtime.accessor(Aspects.class, tags);
        var values = list.values();
        if (values == null) return Optional.empty();
        var result = new LinkedHashMap<String, Integer>();
        for (var aspect : values) {
            if (aspect == null) continue;
            int amount = list.amount(aspect);
            if (amount > 0) result.merge(runtime.accessor(Aspect.class, aspect).tag(), amount, Integer::sum);
        }
        return result.isEmpty() ? Optional.empty() : Optional.of(new AspectValues(result));
    }
}
