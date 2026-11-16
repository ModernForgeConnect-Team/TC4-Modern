package github.com.gengyoubo.tcmodern.tc4.entity.golem;

import github.com.gengyoubo.mfcl.api.LegacyMappings;
import github.com.gengyoubo.mfcl.api.LegacyObject;
import github.com.gengyoubo.mfcl.api.LegacyRuntime;
import github.com.gengyoubo.mfcl.api.mla.LegacyMethod;
import github.com.gengyoubo.mfcl.api.mla.LegacyField;
import github.com.gengyoubo.mfcl.api.mla.LegacyType;
import github.com.gengyoubo.tcmodern.api.TcModernApi;
import github.com.gengyoubo.tcmodern.tc4.content.ReactorAccessors;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/** Accesses original TC4 aspect containers through the hosted world's public interfaces. */
final class GolemEssentia {
    private GolemEssentia() { }
    private record Container(LegacyRuntime runtime, ReactorAccessors.World world, LegacyObject tile, BlockPos pos, Level level) {
        Access access() { return runtime.accessor(Access.class, tile); }
        void changed() {
            try {
                var block = level.getBlockEntity(pos);
                if (block != null) block.setChanged();
                world.update(pos.getX(), pos.getY(), pos.getZ());
            } catch (RuntimeException failure) {
                com.mojang.logging.LogUtils.getLogger().warn("Could not synchronize essentia container at {}", pos, failure);
            }
        }
    }
    private static @Nullable Container container(Level level, BlockPos pos) {
        if (pos == null || !level.hasChunkAt(pos)) return null;
        var runtime = TcModernApi.runtime().orElse(null);
        if (runtime == null) return null;
        var oldWorld = runtime.mappings().toLegacy(LegacyMappings.WORLD, level).orElse(null);
        if (oldWorld == null) return null;
        var world = runtime.accessor(ReactorAccessors.World.class, oldWorld);
        var tile = world.tile(pos.getX(), pos.getY(), pos.getZ());
        return tile != null && runtime.values().isInstance(tile, "thaumcraft.api.aspects.IAspectContainer") ? new Container(runtime, world, tile, pos, level) : null;
    }
    static boolean isContainer(Level level, BlockPos pos) {
        try { return container(level, pos) != null; }
        catch (RuntimeException failure) { return false; }
    }
    static @Nullable String available(Level level, BlockPos pos) {
        var container = container(level, pos);
        if (container == null) return null;
        var list = container.access().aspects();
        if (list == null) return null;
        var aspects = container.runtime().accessor(Aspects.class, list);
        for (var aspect : aspects.all()) if (aspect != null && aspects.amount(aspect) > 0) return container.runtime().accessor(Aspect.class, aspect).tag();
        return null;
    }
    static boolean accepts(Level level, BlockPos pos, String tag) {
        var container = container(level, pos);
        if (container == null || tag.isEmpty()) return false;
        var aspect = container.runtime().accessor(AspectLookup.class).get(tag);
        if (aspect == null || !container.access().accepts(aspect)) return false;
        if (container.runtime().values().isInstance(container.tile(), "thaumcraft.common.tiles.TileJarFillable")) {
            var jar = container.runtime().accessor(Jar.class, container.tile());
            if (jar.amount() >= jar.capacity()) return false;
            var current = jar.aspect();
            if (jar.amount() > 0 && (current == null || !tag.equals(container.runtime().accessor(Aspect.class, current).tag()))) return false;
        }
        return true;
    }
    static boolean takeOne(Level level, BlockPos pos, String tag) {
        var container = container(level, pos);
        if (container == null) return false;
        var aspect = container.runtime().accessor(AspectLookup.class).get(tag);
        if (aspect == null || !container.access().take(aspect, 1)) return false;
        container.changed(); return true;
    }
    /** TC4 addToContainer returns the amount left over. */
    static int insert(Level level, BlockPos pos, String tag, int amount) {
        var container = container(level, pos);
        if (container == null || amount <= 0) return amount;
        var aspect = container.runtime().accessor(AspectLookup.class).get(tag);
        if (aspect == null || !container.access().accepts(aspect)) return amount;
        int remaining = container.access().add(aspect, amount);
        if (remaining < amount) container.changed();
        return remaining;
    }
    @LegacyType("thaumcraft.api.aspects.IAspectContainer") public interface Access {
        @LegacyMethod(name="getAspects", descriptor="()Lthaumcraft/api/aspects/AspectList;") LegacyObject aspects();
        @LegacyMethod(name="doesContainerAccept", descriptor="(Lthaumcraft/api/aspects/Aspect;)Z") boolean accepts(LegacyObject aspect);
        @LegacyMethod(name="takeFromContainer", descriptor="(Lthaumcraft/api/aspects/Aspect;I)Z") boolean take(LegacyObject aspect, int amount);
        @LegacyMethod(name="addToContainer", descriptor="(Lthaumcraft/api/aspects/Aspect;I)I") int add(LegacyObject aspect, int amount);
    }
    @LegacyType("thaumcraft.api.aspects.AspectList") public interface Aspects {
        @LegacyMethod(name="getAspects", descriptor="()[Lthaumcraft/api/aspects/Aspect;") LegacyObject[] all();
        @LegacyMethod(name="getAmount", descriptor="(Lthaumcraft/api/aspects/Aspect;)I") int amount(LegacyObject aspect);
    }
    @LegacyType("thaumcraft.common.tiles.TileJarFillable") public interface Jar {
        @LegacyField(name="aspect", descriptor="Lthaumcraft/api/aspects/Aspect;") LegacyObject aspect();
        @LegacyField(name="amount", descriptor="I") int amount();
        @LegacyField(name="maxAmount", descriptor="I") int capacity();
    }
    @LegacyType("thaumcraft.api.aspects.Aspect") public interface Aspect {
        @LegacyMethod(name="getTag", descriptor="()Ljava/lang/String;") String tag();
    }
    @LegacyType("thaumcraft.api.aspects.Aspect") public interface AspectLookup {
        @LegacyMethod(name="getAspect", descriptor="(Ljava/lang/String;)Lthaumcraft/api/aspects/Aspect;", isStatic=true) LegacyObject get(String tag);
    }
}
