package github.com.gengyoubo.tcmodern.tc4.advancement;

import com.mojang.logging.LogUtils;
import github.com.gengyoubo.mfcl.api.LegacyMappings;
import github.com.gengyoubo.mfcl.api.LegacyObject;
import github.com.gengyoubo.mfcl.api.LegacyRedirects;
import github.com.gengyoubo.mfcl.api.spi.LegacyRedirectProvider;
import github.com.gengyoubo.tcmodern.api.TcModernApi;
import github.com.gengyoubo.tcmodern.api.research.TcResearchAccessors;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.server.ServerLifecycleHooks;

/** For the original arcane output slot, which fires only the legacy crafting event. */
public final class ArcaneCraftAdvancements implements LegacyRedirectProvider {
    @Override public void register(LegacyRedirects redirects) {
        redirects.redirect("thaumcraft.common.container.SlotCraftingArcaneWorkbench",
                "cpw.mods.fml.common.FMLCommonHandler", "firePlayerCraftingEvent",
                "(Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/item/ItemStack;Lnet/minecraft/inventory/IInventory;)V",
                call -> {
                    var args = call.arguments();
                    var result = call.proceed(args);
                    var server = ServerLifecycleHooks.getCurrentServer();
                    var runtime = TcModernApi.runtime().orElse(null);
                    if (server == null || !server.isSameThread() || runtime == null
                            || !(args[0] instanceof LegacyObject player) || !(args[1] instanceof LegacyObject output)) return result;
                    try {
                        var hostPlayer = server.getPlayerList().getPlayerByName(runtime.accessor(TcResearchAccessors.Player.class, player).name());
                        var hostStack = runtime.mappings().toHost(LegacyMappings.ITEM_STACK, output, ItemStack.class).orElse(ItemStack.EMPTY);
                        if (hostPlayer != null && !hostStack.isEmpty()) Tc4Advancements.awardCraft(hostPlayer, hostStack);
                    } catch (RuntimeException failure) {
                        LogUtils.getLogger().warn("Could not update arcane crafting advancement", failure);
                    }
                    return result;
                });
    }
}
