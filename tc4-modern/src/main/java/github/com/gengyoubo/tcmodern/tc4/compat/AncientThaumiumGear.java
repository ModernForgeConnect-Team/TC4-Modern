package github.com.gengyoubo.tcmodern.tc4.compat;

import github.com.gengyoubo.mfcl.api.LegacyMappings;
import github.com.gengyoubo.mfcl.api.LegacyObject;
import github.com.gengyoubo.mfcl.api.LegacyRedirects;
import github.com.gengyoubo.mfcl.api.mla.LegacyMethod;
import github.com.gengyoubo.mfcl.api.mla.LegacyType;
import github.com.gengyoubo.mfcl.api.spi.LegacyRedirectProvider;
import github.com.gengyoubo.tcmodern.api.TcModernApi;
import github.com.gengyoubo.tcmodern.tc4.content.AncientThaumiumArmorItem;
import java.util.function.ToIntFunction;
import net.minecraft.world.item.ItemStack;

/**
 * Lets the original Thaumcraft count worn ancient thaumium armour. It is a modern item, so it is no IVisDiscountGear
 * or IWarpingGear to the original's {@code instanceof} checks. Instead the two totals those checks feed get the
 * armour's share added: {@code WandManager.getTotalVisDiscount} (every wand cost, as called from Thaumcraft) and
 * {@code WarpEvents.getWarpFromGear} (the warp that warp events count; a negative share lowers it).
 */
public final class AncientThaumiumGear implements LegacyRedirectProvider {
    private static final String WAND_MANAGER = "thaumcraft.common.items.wands.WandManager";
    private static final String WARP_EVENTS = "thaumcraft.common.lib.WarpEvents";
    private static final String PLAYER = "Lnet/minecraft/entity/player/EntityPlayer;";

    @LegacyType("net.minecraft.entity.player.EntityPlayer")
    public interface Player {
        /** 0 boots, 1 leggings, 2 chestplate, 3 helmet; null when empty. */
        @LegacyMethod(name = "func_82169_q", descriptor = "(I)Lnet/minecraft/item/ItemStack;") LegacyObject armor(int slot);
    }

    @Override public void register(LegacyRedirects redirects) {
        redirects.redirect("thaumcraft.", WAND_MANAGER, "getTotalVisDiscount", "(" + PLAYER + "Lthaumcraft/api/aspects/Aspect;)F",
                call -> (Float) call.proceed() + worn(call.arguments()[0], piece -> piece.visDiscount()) / 100F);
        redirects.redirect(WARP_EVENTS, WARP_EVENTS, "getWarpFromGear", "(" + PLAYER + ")I",
                call -> (Integer) call.proceed() + worn(call.arguments()[0], piece -> piece.warp()));
    }

    /** The sum of {@code value} over the ancient thaumium pieces an original player wears; 0 when unreadable. */
    static int worn(Object player, ToIntFunction<AncientThaumiumArmorItem> value) {
        var runtime = TcModernApi.runtime().orElse(null);
        if (runtime == null || !(player instanceof LegacyObject original)) return 0;
        int total = 0;
        try {
            var armor = runtime.accessor(Player.class, original);
            for (int slot = 0; slot < 4; slot++) {
                var stack = armor.armor(slot);
                if (stack == null) continue;
                var host = runtime.mappings().toHost(LegacyMappings.ITEM_STACK, stack, ItemStack.class).orElse(ItemStack.EMPTY);
                if (host.getItem() instanceof AncientThaumiumArmorItem piece) total += value.applyAsInt(piece);
            }
        } catch (RuntimeException unreadable) {
            // A fake or half-built player: count no armour rather than break the original's calculation.
        }
        return total;
    }
}
