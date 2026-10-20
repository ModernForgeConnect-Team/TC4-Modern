package github.com.gengyoubo.tcmodern.api.aspect;

import github.com.gengyoubo.mfcl.api.LegacyObject;
import github.com.gengyoubo.mfcl.api.mla.LegacyField;
import github.com.gengyoubo.mfcl.api.mla.LegacyMethod;
import github.com.gengyoubo.mfcl.api.mla.LegacyType;

/** Exact public contracts of TC4 4.2.3.5 and the legacy runtime's Guava API. No old classes are linked. */
public final class TcAspectAccessors {
    private TcAspectAccessors() { }

    @LegacyType("thaumcraft.api.aspects.Aspect")
    public interface Registry {
        /** Every registered aspect by tag, in registration order (primals first). */
        @LegacyField(name = "aspects", descriptor = "Ljava/util/LinkedHashMap;", isStatic = true) LegacyObject aspects();
    }

    // JDK collection classes cannot be MLA targets; snapshot through Guava's public ImmutableMap instead.
    @LegacyType("com.google.common.collect.ImmutableMap")
    public interface MapFactory {
        @LegacyMethod(name = "copyOf", descriptor = "(Ljava/util/Map;)Lcom/google/common/collect/ImmutableMap;", isStatic = true)
        LegacyObject copy(LegacyObject map);
    }

    @LegacyType("com.google.common.collect.ImmutableMap")
    public interface MapView {
        @LegacyMethod(name = "values", descriptor = "()Lcom/google/common/collect/ImmutableCollection;") LegacyObject values();
    }

    @LegacyType("com.google.common.collect.ImmutableCollection")
    public interface CollectionView {
        @LegacyMethod(name = "toArray", descriptor = "()[Ljava/lang/Object;") LegacyObject[] elements();
    }

    @LegacyType("thaumcraft.api.aspects.Aspect")
    public interface Aspect {
        @LegacyMethod(name = "getTag", descriptor = "()Ljava/lang/String;") String tag();
        @LegacyMethod(name = "getColor", descriptor = "()I") int color();
        @LegacyMethod(name = "getImage", descriptor = "()Lnet/minecraft/util/ResourceLocation;") LegacyObject image();
        /** Null for a primal aspect. */
        @LegacyMethod(name = "getComponents", descriptor = "()[Lthaumcraft/api/aspects/Aspect;") LegacyObject[] components();
    }

    /** How the alchemical furnace and the thaumometer read an item: its object tags plus bonus tags. */
    @LegacyType("thaumcraft.common.lib.crafting.ThaumcraftCraftingManager")
    public interface Crafting {
        @LegacyMethod(name = "getObjectTags", descriptor = "(Lnet/minecraft/item/ItemStack;)Lthaumcraft/api/aspects/AspectList;", isStatic = true)
        LegacyObject tags(LegacyObject stack);
        @LegacyMethod(name = "getBonusTags", descriptor = "(Lnet/minecraft/item/ItemStack;Lthaumcraft/api/aspects/AspectList;)Lthaumcraft/api/aspects/AspectList;", isStatic = true)
        LegacyObject bonus(LegacyObject stack, LegacyObject tags);
    }

    @LegacyType("thaumcraft.api.aspects.AspectList")
    public interface Aspects {
        @LegacyMethod(name = "getAspects", descriptor = "()[Lthaumcraft/api/aspects/Aspect;") LegacyObject[] values();
        @LegacyMethod(name = "getAmount", descriptor = "(Lthaumcraft/api/aspects/Aspect;)I") int amount(LegacyObject aspect);
    }

    @LegacyType("net.minecraft.util.ResourceLocation")
    public interface Resource {
        @LegacyMethod(name = "func_110624_b", descriptor = "()Ljava/lang/String;") String namespace();
        @LegacyMethod(name = "func_110623_a", descriptor = "()Ljava/lang/String;") String path();
    }
}
