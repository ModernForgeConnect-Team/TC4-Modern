package github.com.gengyoubo.tcmodern.tc4.compat.curios;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

/** Loaded only for a client-side wearer; remote Curios synchronization must not act on the local actor. */
final class Tc4CuriosClient {
    private Tc4CuriosClient() { }
    static boolean local(Player player) { return Minecraft.getInstance().player == player; }
}
