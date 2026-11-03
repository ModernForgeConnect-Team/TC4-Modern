package github.com.gengyoubo.tcmodern.tc4.compat;

import github.com.gengyoubo.mfcl.api.*;
import github.com.gengyoubo.mfcl.api.mla.*;
import github.com.gengyoubo.mfcl.api.spi.LegacyRedirectProvider;
import github.com.gengyoubo.tcmodern.api.TcModernApi;
import github.com.gengyoubo.tcmodern.tc4.content.NodeReactorBlockEntity;
import github.com.gengyoubo.tcmodern.tc4.content.PrimalFurnaceBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/** Makes the native reactor visible to TC4's tube connection and suction queries. */
public final class ReactorTubeBridge implements LegacyRedirectProvider {
    private static final String RESULT = ")Lnet/minecraft/tileentity/TileEntity;";
    private static final String DIRECTION = "Lnet/minecraftforge/common/util/ForgeDirection;";

    @Override public void register(LegacyRedirects redirects) {
        redirects.redirect("thaumcraft.", "thaumcraft.api.ThaumcraftApiHelper", "getConnectableTile",
                "(Lnet/minecraft/world/World;III" + DIRECTION + RESULT, ReactorTubeBridge::connect);
        redirects.redirect("thaumcraft.", "thaumcraft.api.ThaumcraftApiHelper", "getConnectableTile",
                "(Lnet/minecraft/world/IBlockAccess;III" + DIRECTION + RESULT, ReactorTubeBridge::connect);
        redirects.redirect("thaumcraft.client.renderers.block.BlockTubeRenderer", "thaumcraft.client.renderers.block.BlockTubeRenderer",
                "getConnectableTile", "(Lnet/minecraft/world/IBlockAccess;III" + DIRECTION + RESULT, ReactorTubeBridge::connect);
    }

    private static Object connect(LegacyRedirects.Call call) throws Throwable {
        var args = call.arguments();
        var runtime = TcModernApi.runtime().orElse(null);
        if (runtime == null || !(args[0] instanceof LegacyObject oldWorld) || !(args[4] instanceof LegacyObject oldDirection))
            return call.proceed();
        try {
            var level = runtime.mappings().toHost(LegacyMappings.WORLD, oldWorld, Level.class).orElse(null);
            if (level != null) {
                var direction = runtime.accessor(Direction.class, oldDirection);
                int dx = direction.x(), dy = direction.y(), dz = direction.z();
                // The top is reserved for the node; do not attach tubes there.
                if (Math.abs(dx) + Math.abs(dy) + Math.abs(dz) == 1) {
                    var target = new BlockPos((int)args[1] + dx, (int)args[2] + dy, (int)args[3] + dz);
                    var entity = level.isLoaded(target) ? level.getBlockEntity(target) : null;
                    boolean reactor = entity instanceof NodeReactorBlockEntity && dy != -1;
                    // The furnace receives essentia only from below; its upper face holds alembics.
                    boolean furnace = entity instanceof PrimalFurnaceBlockEntity && dy == 1;
                    if (reactor || furnace) {
                        var proxy = runtime.accessor(TubeFactory.class).create();
                        var tube = runtime.accessor(Tube.class, proxy);
                        // An input proxy refuses direct pushes; an output proxy must be empty so a tube
                        // cannot pull a phantom null aspect. Native devices handle the actual transfers.
                        tube.amount(1);
                        tube.suction(null,128);
                        return proxy;
                    }
                }
            }
        } catch (RuntimeException unsupported) {
            // Rendering previews may use a legacy world that has no host Level.
        }
        return call.proceed();
    }

    @LegacyType("net.minecraftforge.common.util.ForgeDirection") public interface Direction {
        @LegacyField(name="offsetX", descriptor="I") int x();
        @LegacyField(name="offsetY", descriptor="I") int y();
        @LegacyField(name="offsetZ", descriptor="I") int z();
    }
    @LegacyType("thaumcraft.common.tiles.TileTube") public interface TubeFactory {
        @LegacyConstructor(descriptor="()V") LegacyObject create();
    }
    @LegacyType("thaumcraft.common.tiles.TileTube") public interface Tube {
        @LegacyField(name="essentiaAmount", descriptor="I") void amount(int amount);
        @LegacyMethod(name="setSuction", descriptor="(Lthaumcraft/api/aspects/Aspect;I)V") void suction(LegacyObject aspect, int amount);
    }
}
