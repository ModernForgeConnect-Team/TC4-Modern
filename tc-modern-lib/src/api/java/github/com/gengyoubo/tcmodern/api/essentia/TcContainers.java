package github.com.gengyoubo.tcmodern.api.essentia;

import github.com.gengyoubo.mfcl.api.LegacyObject;
import github.com.gengyoubo.mfcl.api.LegacyRuntime;
import github.com.gengyoubo.mfcl.api.mla.LegacyMethod;
import github.com.gengyoubo.mfcl.api.mla.LegacyType;
import github.com.gengyoubo.tcmodern.api.aspect.TcAspectAccessors;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.item.ItemStack;
import thaumcraft.api.aspects.IAspectContainer;
import thaumcraft.api.aspects.IEssentiaContainerItem;

/** Read original TC4 aspect containers by their actual public interfaces, including addon implementations. */
public final class TcContainers {
    private TcContainers() { }
    @LegacyType(type = IAspectContainer.class) public interface Container {
        @LegacyMethod(name="getAspects") LegacyObject aspects();
    }
    @LegacyType(type = IEssentiaContainerItem.class) public interface ItemContainer {
        @LegacyMethod(name="getAspects") LegacyObject aspects(LegacyObject stack);
    }
    @LegacyType(type = ItemStack.class) public interface Stack {
        @LegacyMethod(name="getItem") LegacyObject item();
    }
    /** Empty optional means unsupported; a present empty map means a supported but empty container. */
    public static Optional<Map<String, Integer>> block(LegacyRuntime runtime, LegacyObject tile) {
        if (tile == null || !runtime.values().isInstance(tile, "thaumcraft.api.aspects.IAspectContainer")) return Optional.empty();
        return Optional.of(aspects(runtime, runtime.accessor(Container.class, tile).aspects()));
    }
    public static Optional<Map<String, Integer>> item(LegacyRuntime runtime, LegacyObject stack) {
        if (stack == null) return Optional.empty();
        var item = runtime.accessor(Stack.class, stack).item();
        if (item == null || !runtime.values().isInstance(item, "thaumcraft.api.aspects.IEssentiaContainerItem")) return Optional.empty();
        return Optional.of(aspects(runtime, runtime.accessor(ItemContainer.class, item).aspects(stack)));
    }
    /** Copies an original AspectList; null is an empty list. */
    public static Map<String, Integer> aspects(LegacyRuntime runtime, LegacyObject list) {
        var result = new LinkedHashMap<String, Integer>();
        if (list != null) {
            var values = runtime.accessor(TcAspectAccessors.Aspects.class, list);
            var aspects = values.values();
            if (aspects != null) for (var aspect : aspects) {
                if (aspect == null) continue;
                int amount = values.amount(aspect);
                if (amount > 0) result.put(runtime.accessor(TcAspectAccessors.Aspect.class, aspect).tag(), amount);
            }
        }
        return Collections.unmodifiableMap(result);
    }
}
