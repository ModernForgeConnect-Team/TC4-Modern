import static org.junit.jupiter.api.Assertions.*;

import baubles.api.BaubleType;
import baubles.api.IBauble;
import baubles.api.BaublesApi;
import baubles.common.lib.PlayerHandler;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import org.junit.jupiter.api.Test;

class BaublesViewTest {
    private static class Actor extends EntityPlayer {
        @Override public boolean equals(Object other) { return other instanceof Actor; }
        @Override public int hashCode() { return 42; }
    }
    private static class Bauble extends Item implements IBauble {
        final BaubleType type;
        Bauble(BaubleType type) { this.type = type; }
        public BaubleType getBaubleType(ItemStack stack) { return type; }
        public boolean canEquip(ItemStack stack, EntityLivingBase wearer) { return true; }
        public boolean canUnequip(ItemStack stack, EntityLivingBase wearer) { return true; }
        public void onWornTick(ItemStack stack, EntityLivingBase wearer) { }
        public void onEquipped(ItemStack stack, EntityLivingBase wearer) { }
        public void onUnequipped(ItemStack stack, EntityLivingBase wearer) { }
    }
    private static class Stack extends ItemStack {
        final Item item;
        Stack(Item item, int count) { this.item = item; field_77994_a = count; }
        @Override public Item func_77973_b() { return item; }
        @Override public ItemStack func_77979_a(int count) { field_77994_a -= count; return new Stack(item, count); }
    }
    @Test void clientAndServerActorsWithEqualEntityIdsNeverShareSlots() {
        var server = new Actor(); var client = new Actor();
        assertEquals(server, client);
        var serverSlots = PlayerHandler.getPlayerBaubles(server);
        var clientSlots = PlayerHandler.getPlayerBaubles(client);
        assertNotSame(serverSlots, clientSlots);
        assertSame(serverSlots, BaublesApi.getBaubles(server));
        var ring = new Stack(new Bauble(BaubleType.RING), 1);
        serverSlots.stackList[1] = ring;
        assertNull(clientSlots.func_70301_a(1));
        assertSame(ring, serverSlots.func_70304_b(1));
        assertNull(serverSlots.func_70301_a(1));
    }
    @Test void validatesBothRingsAmuletAndBeltAndSplitsWithoutDuplicating() {
        var actor = new Actor(); var view = PlayerHandler.getPlayerBaubles(actor);
        var ring = new Stack(new Bauble(BaubleType.RING), 2);
        assertFalse(view.func_94041_b(0, ring));
        assertTrue(view.func_94041_b(1, ring)); assertTrue(view.func_94041_b(2, ring));
        assertFalse(view.func_94041_b(3, ring));
        assertTrue(view.func_94041_b(0, new Stack(new Bauble(BaubleType.AMULET), 1)));
        assertTrue(view.func_94041_b(3, new Stack(new Bauble(BaubleType.BELT), 1)));
        view.func_70299_a(1, ring);
        assertEquals(1, view.func_70298_a(1, 1).field_77994_a);
        assertEquals(1, view.func_70301_a(1).field_77994_a);
        assertSame(ring, view.func_70298_a(1, 10)); assertNull(view.func_70301_a(1));
        assertNull(view.func_70301_a(-1)); assertNull(view.func_70301_a(4));
        assertSame(actor, view.player.get());
    }
}
