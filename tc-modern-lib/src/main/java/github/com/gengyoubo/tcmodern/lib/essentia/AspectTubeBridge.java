package github.com.gengyoubo.tcmodern.lib.essentia;

import com.mojang.logging.LogUtils;
import github.com.gengyoubo.mfcl.api.*;
import github.com.gengyoubo.mfcl.api.mla.*;
import github.com.gengyoubo.mfcl.api.spi.LegacyRedirectProvider;
import github.com.gengyoubo.tcmodern.api.TcModernApi;
import github.com.gengyoubo.tcmodern.api.aspect.TcAspectAccessors;
import github.com.gengyoubo.tcmodern.api.essentia.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Bridges TC4 tube call sites to a sided modern capability without retaining legacy handles. */
public final class AspectTubeBridge implements LegacyRedirectProvider {
    private static final String D = "Lnet/minecraftforge/common/util/ForgeDirection;";
    private static final String A = "Lthaumcraft/api/aspects/Aspect;";
    private static final String T = "thaumcraft.api.aspects.IEssentiaTransport";
    private static final String TUBE = "thaumcraft.common.tiles.TileTube";
    private static final int MARKER = Integer.MIN_VALUE;

    @Override public void register(LegacyRedirects redirects) {
        for (String world : new String[]{"net/minecraft/world/World", "net/minecraft/world/IBlockAccess"})
            redirects.redirect("thaumcraft.", "thaumcraft.api.ThaumcraftApiHelper", "getConnectableTile",
                    "(L" + world + ";III" + D + ")Lnet/minecraft/tileentity/TileEntity;", AspectTubeBridge::connect);
        redirects.redirect("thaumcraft.client.renderers.block.BlockTubeRenderer", "thaumcraft.client.renderers.block.BlockTubeRenderer", "getConnectableTile",
                "(Lnet/minecraft/world/IBlockAccess;III" + D + ")Lnet/minecraft/tileentity/TileEntity;", AspectTubeBridge::connect);
        redirects.redirect("thaumcraft.", T, "takeEssentia", "(" + A + "I" + D + ")I", call -> transfer(call, false));
        redirects.redirect("thaumcraft.", T, "addEssentia", "(" + A + "I" + D + ")I", call -> transfer(call, true));
        redirects.redirect("thaumcraft.", T, "getMinimumSuction", "()I", AspectTubeBridge::minimum);
    }
    private static Object connect(LegacyRedirects.Call call) throws Throwable {
        var runtime = TcModernApi.runtime().orElse(null); var args = call.arguments();
        if (runtime == null || !(args[0] instanceof LegacyObject world) || !(args[4] instanceof LegacyObject direction)) return call.proceed();
        try {
            if (!runtime.values().isInstance(world, "net.minecraft.world.World")) return call.proceed();
            var level = hostLevel(runtime, world);
            var delta = runtime.accessor(OldDirection.class, direction);
            var outward = direction(delta);
            if (level == null || outward == null) return call.proceed();
            var origin = new BlockPos((int) args[1], (int) args[2], (int) args[3]);
            var target = origin.relative(outward);
            var storage = storage(level, target, outward.getOpposite());
            if (storage == null || !storage.canReceive() && !storage.canExtract()) return call.proceed();
            int suction = suction(storage);
            String filter = filter(storage);
            var oldFilter = filter == null ? null : runtime.accessor(TcAspectAccessors.AspectLookup.class).find(filter);
            // An invalid filter must never turn into an unrestricted suction.
            if (filter != null && oldFilter == null) suction = 0;
            if (!level.isClientSide && call.caller().equals(TUBE) && suction > 0)
                pull(runtime, world, level, origin, target, outward, storage, suction, filter);
            var proxy = runtime.accessor(TubeFactory.class).create();
            var tile = runtime.accessor(ProxyTile.class, proxy);
            tile.world(world); tile.x(target.getX()); tile.y(target.getY()); tile.z(target.getZ());
            tile.marker(MARKER); tile.face(outward.getOpposite().get3DDataValue());
            tile.suction(oldFilter, suction);
            String requested = requested(runtime, world, origin);
            if (storage.canExtract()) for (String tag : new java.util.TreeSet<>(storage.contents().keySet())) {
                if (requested != null && !requested.equals(tag) || storage.extract(tag, 1, true) <= 0) continue;
                var aspect = runtime.accessor(TcAspectAccessors.AspectLookup.class).find(tag);
                if (aspect != null) { tile.type(aspect); tile.amount(1); break; }
            }
            return proxy;
        } catch (RuntimeException failure) {
            LogUtils.getLogger().warn("TC4 aspect tube connection failed: {}", failure.toString());
            return call.proceed();
        }
    }
    private static Level hostLevel(LegacyRuntime runtime, LegacyObject world) {
        try { return runtime.mappings().toHost(LegacyMappings.WORLD, world, Level.class).orElse(null); }
        catch (RuntimeException unavailable) { return null; }
    }
    private static Direction direction(OldDirection old) {
        for (var direction : Direction.values())
            if (direction.getStepX() == old.x() && direction.getStepY() == old.y() && direction.getStepZ() == old.z()) return direction;
        return null;
    }
    private static AspectStorage storage(Level level, BlockPos pos, Direction side) {
        if (!level.isLoaded(pos)) return null;
        var entity = level.getBlockEntity(pos);
        return entity == null ? null : entity.getCapability(AspectCapabilities.STORAGE, side).orElse(null);
    }
    private static int suction(AspectStorage storage) {
        if (!storage.canReceive()) return 0;
        long total = storage.contents().values().stream().mapToLong(Integer::longValue).sum();
        if (total >= storage.capacity()) return 0;
        return Math.max(0, Math.min(128, storage instanceof AspectTubePort port ? port.suction() : 64));
    }
    private static String filter(AspectStorage storage) { return storage instanceof AspectTubePort port ? port.suctionAspect() : null; }
    private static int minimum(AspectStorage storage) { return Math.max(0, Math.min(128, storage instanceof AspectTubePort port ? port.minimumSuction() : 0)); }
    private static Object minimum(LegacyRedirects.Call call) throws Throwable {
        var ref = proxy(call.receiver());
        if (ref == null) return call.proceed();
        var storage = ref.storage(null);
        return storage == null ? 128 : minimum(storage);
    }
    private static Object transfer(LegacyRedirects.Call call, boolean receiving) throws Throwable {
        var ref = proxy(call.receiver());
        if (ref == null) return call.proceed();
        var args = call.arguments();
        if (!(args[0] instanceof LegacyObject aspect) || (int) args[1] <= 0 || !(args[2] instanceof LegacyObject side)) return 0;
        var direction = ref.runtime.accessor(OldDirection.class, side);
        var face = direction(direction);
        var storage = ref.storage(face);
        if (ref.level == null || ref.level.isClientSide || storage == null) return 0;
        String tag = ref.runtime.accessor(TcAspectAccessors.Aspect.class, aspect).tag();
        if (receiving && filter(storage) != null && !filter(storage).equals(tag)) return 0;
        int amount = receiving ? (storage.canReceive() ? storage.receive(tag, (int) args[1], false) : 0)
                : (storage.canExtract() ? storage.extract(tag, (int) args[1], false) : 0);
        if (amount > 0) { var be = ref.level.getBlockEntity(ref.pos); if (be != null) be.setChanged(); }
        return amount;
    }
    private record Proxy(LegacyRuntime runtime, Level level, BlockPos pos, Direction face) {
        AspectStorage storage(Direction side) {
            if (level == null || side != null && side != face) return null;
            return AspectTubeBridge.storage(level, pos, face);
        }
    }
    private static Proxy proxy(LegacyObject object) {
        var runtime = TcModernApi.runtime().orElse(null);
        if (runtime == null || object == null || !runtime.values().isInstance(object, TUBE)) return null;
        var tile = runtime.accessor(ProxyTile.class, object);
        if (tile.marker() != MARKER) return null;
        var world = tile.world();
        var level = world == null ? null : hostLevel(runtime, world);
        return level == null ? new Proxy(runtime, null, BlockPos.ZERO, Direction.DOWN) : new Proxy(runtime, level, new BlockPos(tile.x(), tile.y(), tile.z()), Direction.from3DDataValue(tile.face()));
    }
    private static String requested(LegacyRuntime runtime, LegacyObject world, BlockPos origin) {
        var source = runtime.accessor(OldWorld.class, world).tile(origin.getX(), origin.getY(), origin.getZ());
        if (source == null || !runtime.values().isInstance(source, TUBE)) return null;
        var aspect = runtime.accessor(Transport.class, source).suctionType(null);
        return aspect == null ? null : runtime.accessor(TcAspectAccessors.Aspect.class, aspect).tag();
    }
    private static void pull(LegacyRuntime runtime, LegacyObject world, Level level, BlockPos origin, BlockPos target,
                             Direction outward, AspectStorage storage, int suction, String filter) {
        var source = runtime.accessor(OldWorld.class, world).tile(origin.getX(), origin.getY(), origin.getZ());
        if (source == null || !runtime.values().isInstance(source, TUBE)) return;
        var tube = runtime.accessor(ProxyTile.class, source);
        if (tube.marker() % 5 != 0) return; // original tube's five-tick transfer cadence
        var oldSide = runtime.accessor(Directions.class).of(outward.get3DDataValue());
        var transport = runtime.accessor(Transport.class, source);
        if (!transport.canOutput(oldSide) || transport.amount(oldSide) <= 0 || transport.suction(oldSide) >= suction || transport.minimum() > suction) return;
        var aspect = transport.type(oldSide);
        if (aspect == null) return;
        String tag = runtime.accessor(TcAspectAccessors.Aspect.class, aspect).tag();
        if (filter != null && !filter.equals(tag) || storage.receive(tag, 1, true) != 1) return;
        if (transport.take(aspect, 1, oldSide) != 1) return;
        if (storage.receive(tag, 1, false) != 1) {
            if (transport.add(aspect, 1, oldSide) != 1)
                LogUtils.getLogger().error("Aspect storage at {} violated simulation; could not restore {} to tube at {}", target, tag, origin);
            return;
        }
        BlockEntity entity = level.getBlockEntity(target); if (entity != null) entity.setChanged();
        BlockEntity sourceEntity = level.getBlockEntity(origin); if (sourceEntity != null) sourceEntity.setChanged();
    }
    @LegacyType("net.minecraftforge.common.util.ForgeDirection") public interface OldDirection {
        @LegacyField(name="offsetX", descriptor="I") int x(); @LegacyField(name="offsetY", descriptor="I") int y(); @LegacyField(name="offsetZ", descriptor="I") int z();
    }
    @LegacyType("net.minecraftforge.common.util.ForgeDirection") public interface Directions {
        @LegacyMethod(name="getOrientation", descriptor="(I)Lnet/minecraftforge/common/util/ForgeDirection;", isStatic=true) LegacyObject of(int ordinal);
    }
    @LegacyType("net.minecraft.world.World") public interface OldWorld {
        @LegacyMethod(name="func_147438_o", descriptor="(III)Lnet/minecraft/tileentity/TileEntity;") LegacyObject tile(int x, int y, int z);
    }
    @LegacyType("thaumcraft.common.tiles.TileTube") public interface TubeFactory { @LegacyConstructor(descriptor="()V") LegacyObject create(); }
    @LegacyType("thaumcraft.common.tiles.TileTube") public interface ProxyTile {
        @LegacyField(name="ventColor", descriptor="I") int face(); @LegacyField(name="ventColor", descriptor="I") void face(int value);
        @LegacyField(name="count", descriptor="I") int marker(); @LegacyField(name="count", descriptor="I") void marker(int value);
        @LegacyField(name="field_145850_b", descriptor="Lnet/minecraft/world/World;") LegacyObject world(); @LegacyField(name="field_145850_b", descriptor="Lnet/minecraft/world/World;") void world(LegacyObject world);
        @LegacyField(name="field_145851_c", descriptor="I") int x(); @LegacyField(name="field_145851_c", descriptor="I") void x(int x);
        @LegacyField(name="field_145848_d", descriptor="I") int y(); @LegacyField(name="field_145848_d", descriptor="I") void y(int y);
        @LegacyField(name="field_145849_e", descriptor="I") int z(); @LegacyField(name="field_145849_e", descriptor="I") void z(int z);
        @LegacyField(name="essentiaType", descriptor="Lthaumcraft/api/aspects/Aspect;") void type(LegacyObject aspect);
        @LegacyField(name="essentiaAmount", descriptor="I") void amount(int amount);
        @LegacyMethod(name="setSuction", descriptor="(Lthaumcraft/api/aspects/Aspect;I)V") void suction(LegacyObject aspect, int amount);
    }
    @LegacyType("thaumcraft.api.aspects.IEssentiaTransport") public interface Transport {
        @LegacyMethod(name="canOutputTo", descriptor="(" + D + ")Z") boolean canOutput(LegacyObject side);
        @LegacyMethod(name="getEssentiaAmount", descriptor="(" + D + ")I") int amount(LegacyObject side);
        @LegacyMethod(name="getEssentiaType", descriptor="(" + D + ")" + A) LegacyObject type(LegacyObject side);
        @LegacyMethod(name="getSuctionAmount", descriptor="(" + D + ")I") int suction(LegacyObject side);
        @LegacyMethod(name="getSuctionType", descriptor="(" + D + ")" + A) LegacyObject suctionType(LegacyObject side);
        @LegacyMethod(name="getMinimumSuction", descriptor="()I") int minimum();
        @LegacyMethod(name="takeEssentia", descriptor="(" + A + "I" + D + ")I") int take(LegacyObject aspect, int amount, LegacyObject side);
        @LegacyMethod(name="addEssentia", descriptor="(" + A + "I" + D + ")I") int add(LegacyObject aspect, int amount, LegacyObject side);
    }
}
