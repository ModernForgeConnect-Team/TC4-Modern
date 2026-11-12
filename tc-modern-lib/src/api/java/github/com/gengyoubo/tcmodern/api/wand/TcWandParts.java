package github.com.gengyoubo.tcmodern.api.wand;

import github.com.gengyoubo.mfcl.api.LegacyObject;
import github.com.gengyoubo.mfcl.api.LegacyRuntime;
import github.com.gengyoubo.mfcl.api.LegacyStack;
import github.com.gengyoubo.mfcl.api.mla.LegacyMethod;
import github.com.gengyoubo.mfcl.api.mla.LegacyType;
import github.com.gengyoubo.tcmodern.api.aspect.TcAspectAccessors;
import github.com.gengyoubo.tcmodern.api.recipe.TcRecipeAccessors;
import java.util.ArrayList;
import java.util.List;

/** Snapshot of the original registered wand parts, including legacy addons. */
public final class TcWandParts {
    private TcWandParts() { }
    public record Rod(String tag, int capacity, int craftCost, String research, LegacyStack item, boolean staff) { }
    public record Cap(String tag, float baseModifier, float specialModifier, List<String> specialAspects,
                      int craftCost, String research, LegacyStack item) {
        public Cap { specialAspects = List.copyOf(specialAspects); }
    }
    @LegacyType("thaumcraft.api.wands.WandRod") public interface RodExtra {
        @LegacyMethod(name="getCapacity", descriptor="()I") int capacity();
    }
    @LegacyType("thaumcraft.api.wands.WandCap") public interface CapExtra {
        @LegacyMethod(name="getBaseCostModifier", descriptor="()F") float base();
        @LegacyMethod(name="getSpecialCostModifier", descriptor="()F") float special();
        @LegacyMethod(name="getSpecialCostModifierAspects", descriptor="()Ljava/util/List;") LegacyObject aspects();
    }
    public static List<Rod> rods(LegacyRuntime runtime) {
        var result = new ArrayList<Rod>();
        for (Object value : runtime.values().map(runtime.accessor(TcRecipeAccessors.Rods.class).all()).values()) {
            var original = (LegacyObject) value;
            var rod = runtime.accessor(TcRecipeAccessors.Rod.class, original);
            var item = rod.item();
            result.add(new Rod(rod.tag(), runtime.accessor(RodExtra.class, original).capacity(), rod.craftCost(),
                    rod.research(), item == null ? null : runtime.snapshot(item),
                    runtime.values().isInstance(original, "thaumcraft.api.wands.StaffRod")));
        }
        return List.copyOf(result);
    }
    public static List<Cap> caps(LegacyRuntime runtime) {
        var result = new ArrayList<Cap>();
        for (Object value : runtime.values().map(runtime.accessor(TcRecipeAccessors.Caps.class).all()).values()) {
            var original = (LegacyObject) value;
            var cap = runtime.accessor(TcRecipeAccessors.Cap.class, original);
            var extra = runtime.accessor(CapExtra.class, original);
            var tags = new ArrayList<String>();
            var aspects = extra.aspects();
            if (aspects != null) for (Object aspect : runtime.values().list(aspects))
                tags.add(runtime.accessor(TcAspectAccessors.Aspect.class, (LegacyObject) aspect).tag());
            var item = cap.item();
            result.add(new Cap(cap.tag(), extra.base(), extra.special(), tags, cap.craftCost(), cap.research(),
                    item == null ? null : runtime.snapshot(item)));
        }
        return List.copyOf(result);
    }
}
