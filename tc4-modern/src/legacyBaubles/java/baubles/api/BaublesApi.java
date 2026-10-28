package baubles.api;

import baubles.common.lib.PlayerHandler;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;

/** TC4 reads these slots; MFCL supplies their contents from Curios. */
public final class BaublesApi {
    public static IInventory getBaubles(EntityPlayer player) {
        return PlayerHandler.getPlayerBaubles(player);
    }
}
