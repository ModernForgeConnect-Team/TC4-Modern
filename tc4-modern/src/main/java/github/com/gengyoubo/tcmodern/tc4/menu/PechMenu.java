package github.com.gengyoubo.tcmodern.tc4.menu;

import github.com.gengyoubo.tcmodern.tc4.entity.Pech;
import github.com.gengyoubo.tcmodern.tc4.init.Tc4Menus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** The original one-input, four-output pech trade. The server owns all trade decisions. */
public final class PechMenu extends AbstractContainerMenu {
    private final Pech pech;
    private final SimpleContainer items = new SimpleContainer(5);

    public PechMenu(int id, Inventory inventory, FriendlyByteBuf data) {
        this(id, inventory, inventory.player.level().getEntity(data.readVarInt()) instanceof Pech p ? p : null);
    }
    public PechMenu(int id, Inventory inventory, Pech pech) {
        super(Tc4Menus.PECH.get(), id); this.pech = pech;
        addSlot(new Slot(items, 0, 36, 29));
        for (int row = 0; row < 2; row++) for (int col = 0; col < 2; col++)
            addSlot(new Slot(items, 1 + col + row * 2, 106 + col * 18, 20 + row * 18) {
                @Override public boolean mayPlace(ItemStack stack) { return false; }
            });
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++)
            addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 8 + col * 18, 142));
    }
    public boolean canTrade() {
        if (pech == null || !Pech.isValued(items.getItem(0))) return false;
        for (int i = 1; i < 5; i++) if (!items.getItem(i).isEmpty()) return false;
        return true;
    }
    @Override public boolean clickMenuButton(Player player, int button) {
        if (button != 0 || !(player.level() instanceof ServerLevel server) || !stillValid(player) || !canTrade()) return false;
        var input = items.getItem(0);
        var rewards = pech.trade(server, player, Pech.value(input));
        input.shrink(1); items.setChanged();
        for (var reward : rewards) {
            var remaining = reward.copy();
            for (int i = 1; i < 5 && !remaining.isEmpty(); i++) {
                var current = items.getItem(i);
                if (current.isEmpty()) { items.setItem(i, remaining); remaining = ItemStack.EMPTY; }
                else if (ItemStack.isSameItemSameTags(current, remaining)) {
                    int moved = Math.min(remaining.getCount(), current.getMaxStackSize() - current.getCount());
                    current.grow(moved); remaining.shrink(moved);
                }
            }
            if (!remaining.isEmpty() && !player.getInventory().add(remaining)) player.drop(remaining, false);
        }
        broadcastChanges();
        return true;
    }
    @Override public boolean stillValid(Player player) {
        return pech != null && pech.isAlive() && pech.isTamed() && player.distanceToSqr(pech) < 16
                && (player.level().isClientSide || pech.isTradingWith(player));
    }
    @Override public void removed(Player player) {
        super.removed(player);
        if (!player.level().isClientSide) { if (pech != null) pech.stopTrading(player); clearContainer(player, items); }
    }
    @Override public ItemStack quickMoveStack(Player player, int index) {
        var slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        var stack = slot.getItem(); var copy = stack.copy();
        if (index < 5 ? !moveItemStackTo(stack, 5, slots.size(), true) : !moveItemStackTo(stack, 0, 1, false)) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        slot.onTake(player, stack); return copy;
    }
}
