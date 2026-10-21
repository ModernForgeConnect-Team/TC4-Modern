package github.com.gengyoubo.tcmodern.api.aspect;

import github.com.gengyoubo.mfcl.api.LegacyObject;
import github.com.gengyoubo.mfcl.api.mla.LegacyConstructor;
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

    // Factories and static lookups must not share a receiver-less contract with instance members.
    @LegacyType("thaumcraft.api.aspects.Aspect")
    public interface AspectLookup {
        /** Null when no aspect has that tag. */
        @LegacyMethod(name = "getAspect", descriptor = "(Ljava/lang/String;)Lthaumcraft/api/aspects/Aspect;", isStatic = true)
        LegacyObject find(String tag);
    }

    /** The original constructor adds the new aspect to {@link Registry#aspects()} itself. */
    @LegacyType("thaumcraft.api.aspects.Aspect")
    public interface AspectFactory {
        @LegacyConstructor(descriptor = "(Ljava/lang/String;I[Lthaumcraft/api/aspects/Aspect;Lnet/minecraft/util/ResourceLocation;I)V")
        LegacyObject create(String tag, int color, LegacyObject[] components, LegacyObject image, int blend);
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

    @LegacyType("thaumcraft.common.Thaumcraft")
    public interface Thaumcraft {
        @LegacyField(name = "proxy", descriptor = "Lthaumcraft/common/CommonProxy;", isStatic = true) LegacyObject proxy();
    }

    @LegacyType("thaumcraft.common.CommonProxy")
    public interface Proxy {
        @LegacyMethod(name = "getPlayerKnowledge", descriptor = "()Lthaumcraft/common/lib/research/PlayerKnowledge;") LegacyObject knowledge();
    }

    /** Per-player research state, keyed by player name. The aspect pool doubles as the discovered-aspect list. */
    @LegacyType("thaumcraft.common.lib.research.PlayerKnowledge")
    public interface Knowledge {
        @LegacyMethod(name = "getAspectPoolFor", descriptor = "(Ljava/lang/String;Lthaumcraft/api/aspects/Aspect;)S") short pool(String player, LegacyObject aspect);
        @LegacyMethod(name = "addAspectPool", descriptor = "(Ljava/lang/String;Lthaumcraft/api/aspects/Aspect;S)Z") boolean add(String player, LegacyObject aspect, short amount);
    }

    @LegacyType("net.minecraft.entity.player.EntityPlayer")
    public interface Player {
        @LegacyMethod(name = "func_70005_c_", descriptor = "()Ljava/lang/String;") String name();
    }

    @LegacyType("net.minecraft.util.ResourceLocation")
    public interface ResourceFactory {
        @LegacyConstructor(descriptor = "(Ljava/lang/String;Ljava/lang/String;)V") LegacyObject create(String namespace, String path);
    }
}
