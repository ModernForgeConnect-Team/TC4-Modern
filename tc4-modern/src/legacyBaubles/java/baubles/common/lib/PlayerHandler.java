package baubles.common.lib;

import baubles.common.container.InventoryBaubles;
import java.util.Map;
import java.util.HashMap;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import net.minecraft.entity.player.EntityPlayer;

/** Runtime actors own views only. Curios owns saves, cloning, synchronization and death drops. */
public final class PlayerHandler {
    // Entity.equals uses the entity id. The local client actor and server actor can share
    // that id, so weak keys must compare object identity, not Entity.equals.
    private static final ReferenceQueue<EntityPlayer> QUEUE = new ReferenceQueue<>();
    private static final Map<ActorKey, InventoryBaubles> INVENTORIES = new HashMap<>();
    private static final class ActorKey extends WeakReference<EntityPlayer> {
        private final int hash;
        ActorKey(EntityPlayer player) { super(player, QUEUE); hash = System.identityHashCode(player); }
        @Override public int hashCode() { return hash; }
        @Override public boolean equals(Object other) {
            return this == other || other instanceof ActorKey && get() != null && get() == ((ActorKey) other).get();
        }
    }
    private static void clean() {
        ActorKey key;
        while ((key = (ActorKey) QUEUE.poll()) != null) INVENTORIES.remove(key);
    }
    public static synchronized InventoryBaubles getPlayerBaubles(EntityPlayer player) {
        clean();
        ActorKey key = new ActorKey(player);
        InventoryBaubles inventory = INVENTORIES.get(key);
        if (inventory == null) {
            inventory = new InventoryBaubles(player);
            INVENTORIES.put(key, inventory);
        }
        return inventory;
    }
    public static synchronized void clearPlayerBaubles(EntityPlayer player) { clean(); INVENTORIES.remove(new ActorKey(player)); }
    public static synchronized void setPlayerBaubles(EntityPlayer player, InventoryBaubles inventory) {
        clean(); INVENTORIES.put(new ActorKey(player), inventory);
    }
}
