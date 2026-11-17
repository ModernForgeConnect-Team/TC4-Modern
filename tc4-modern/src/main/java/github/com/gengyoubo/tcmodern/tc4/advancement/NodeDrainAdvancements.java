package github.com.gengyoubo.tcmodern.tc4.advancement;

import com.mojang.logging.LogUtils;
import github.com.gengyoubo.mfcl.api.LegacyObject;
import github.com.gengyoubo.mfcl.api.LegacyRedirects;
import github.com.gengyoubo.mfcl.api.LegacyRuntime;
import github.com.gengyoubo.mfcl.api.spi.LegacyRedirectProvider;
import github.com.gengyoubo.tcmodern.api.TcModernApi;
import github.com.gengyoubo.tcmodern.api.essentia.TcContainers;
import github.com.gengyoubo.tcmodern.api.research.TcResearchAccessors;
import github.com.gengyoubo.tcmodern.api.wand.TcWandVis;
import java.util.Map;
import net.minecraftforge.server.ServerLifecycleHooks;

/** Observe actual Vis gained while the original wand drains a node. */
public final class NodeDrainAdvancements implements LegacyRedirectProvider {
    @Override public void register(LegacyRedirects redirects) {
        redirects.redirect("thaumcraft.common.items.wands.ItemWandCasting", "thaumcraft.api.wands.IWandable", "onUsingWandTick",
                "(Lnet/minecraft/item/ItemStack;Lnet/minecraft/entity/player/EntityPlayer;I)V", call -> {
                    var args = call.arguments();
                    var runtime = TcModernApi.runtime().orElse(null);
                    var server = ServerLifecycleHooks.getCurrentServer();
                    if (runtime == null || server == null || !server.isSameThread() || call.receiver() == null
                            || !runtime.values().isInstance(call.receiver(), "thaumcraft.api.nodes.INode")
                            || !(args[0] instanceof LegacyObject wand) || !(args[1] instanceof LegacyObject player))
                        return call.proceed(args);
                    Map<String, Integer> before = null;
                    try { before = vis(runtime, wand); }
                    catch (RuntimeException failure) { LogUtils.getLogger().debug("Cannot inspect wand before node drain", failure); }
                    var result = call.proceed(args);
                    if (before == null) return result;
                    try {
                        var after = vis(runtime, wand);
                        boolean gained = false;
                        for (var entry : after.entrySet()) if (entry.getValue() > before.getOrDefault(entry.getKey(), 0)) { gained = true; break; }
                        if (gained) {
                            var host = server.getPlayerList().getPlayerByName(runtime.accessor(TcResearchAccessors.Player.class, player).name());
                            if (host != null) Tc4Advancements.award(host, "node_drain", "drained");
                        }
                    } catch (RuntimeException failure) { LogUtils.getLogger().warn("Could not update node drain advancement", failure); }
                    return result;
                });
    }

    private static Map<String, Integer> vis(LegacyRuntime runtime, LegacyObject stack) {
        var item = runtime.accessor(TcWandVis.Stack.class, stack).item();
        return Map.copyOf(TcContainers.aspects(runtime, runtime.accessor(TcWandVis.Wand.class, item).all(stack)));
    }
}
