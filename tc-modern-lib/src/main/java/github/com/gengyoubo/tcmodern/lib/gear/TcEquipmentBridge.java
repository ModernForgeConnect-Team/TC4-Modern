package github.com.gengyoubo.tcmodern.lib.gear;

import github.com.gengyoubo.mfcl.api.LegacyMappings;
import github.com.gengyoubo.mfcl.api.LegacyObject;
import github.com.gengyoubo.mfcl.api.LegacyRedirects;
import github.com.gengyoubo.mfcl.api.mla.LegacyMethod;
import github.com.gengyoubo.mfcl.api.mla.LegacyType;
import github.com.gengyoubo.mfcl.api.spi.LegacyRedirectProvider;
import github.com.gengyoubo.tcmodern.api.TcModernApi;
import github.com.gengyoubo.tcmodern.api.aspect.TcAspectAccessors;
import net.minecraft.world.item.ItemStack;

/** Adapts modern armor effects to the totals calculated by the original TC4. */
public final class TcEquipmentBridge implements LegacyRedirectProvider {
    private static final String PLAYER = "Lnet/minecraft/entity/player/EntityPlayer;";
    @LegacyType("net.minecraft.entity.player.EntityPlayer") public interface Player {
        @LegacyMethod(name="func_82169_q", descriptor="(I)Lnet/minecraft/item/ItemStack;") LegacyObject armor(int slot);
    }
    @Override public void register(LegacyRedirects redirects) {
        redirects.redirect("thaumcraft.", "thaumcraft.common.items.wands.WandManager", "getTotalVisDiscount",
                "(" + PLAYER + "Lthaumcraft/api/aspects/Aspect;)F", call ->
                        (Float) call.proceed() + worn(call.arguments()[0], call.arguments()[1], true) / 100F);
        redirects.redirect("thaumcraft.common.lib.WarpEvents", "thaumcraft.common.lib.WarpEvents", "getWarpFromGear",
                "(" + PLAYER + ")I", call -> (Integer) call.proceed() + worn(call.arguments()[0], null, false));
    }
    private static int worn(Object player, Object aspect, boolean vis) {
        var runtime = TcModernApi.runtime().orElse(null);
        if (runtime == null || !(player instanceof LegacyObject oldPlayer)) return 0;
        try {
            String tag = aspect instanceof LegacyObject oldAspect ? runtime.accessor(TcAspectAccessors.Aspect.class, oldAspect).tag() : null;
            var armor = runtime.accessor(Player.class, oldPlayer);
            int total = 0;
            for (int slot = 0; slot < 4; slot++) {
                var oldStack = armor.armor(slot);
                if (oldStack == null) continue;
                var stack = runtime.mappings().toHost(LegacyMappings.ITEM_STACK, oldStack, ItemStack.class).orElse(ItemStack.EMPTY);
                if (stack.getItem() instanceof TcEquipmentEffects effects)
                    total += vis ? effects.visDiscount(stack, oldPlayer, tag) : effects.warp(stack, oldPlayer);
            }
            return total;
        } catch (RuntimeException unreadable) { return 0; }
    }
}
