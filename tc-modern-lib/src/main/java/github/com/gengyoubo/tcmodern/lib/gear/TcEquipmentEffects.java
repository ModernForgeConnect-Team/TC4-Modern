package github.com.gengyoubo.tcmodern.lib.gear;

import github.com.gengyoubo.mfcl.api.LegacyObject;
import net.minecraft.world.item.ItemStack;

/** Implement on a modern armor item to contribute to TC4's original equipment totals.
 * The player is a scoped legacy handle and must never be retained. Aspect is a TC4 tag, or null if absent.
 * Only the four armor slots are included; this contract does not automatically cover Curios or Baubles.
 */
public interface TcEquipmentEffects {
    default int visDiscount(ItemStack stack, LegacyObject player, String aspect) { return 0; }
    default int warp(ItemStack stack, LegacyObject player) { return 0; }
}
