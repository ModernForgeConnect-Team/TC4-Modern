package github.com.gengyoubo.tcmodern.tc4.menu;

import github.com.gengyoubo.tcmodern.tc4.content.ArchiveCabinetBlockEntity;
import github.com.gengyoubo.tcmodern.tc4.init.Tc4Menus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;

/** Three drawers of nine note slots over the viewer's inventory. */
public final class ArchiveCabinetMenu extends AbstractContainerMenu {
    public static final int WIDTH = 184, HEIGHT = 196, DRAWER_X = 12, DRAWER_Y = 30, INVENTORY_X = 12, INVENTORY_Y = 114, HOTBAR_Y = 172;

    private final ArchiveCabinetBlockEntity cabinet;

    public ArchiveCabinetMenu(int id, Inventory inventory, FriendlyByteBuf data) {
        this(id, inventory, inventory.player.level().getBlockEntity(data.readBlockPos()) instanceof ArchiveCabinetBlockEntity cabinet ? cabinet : null);
    }

    public ArchiveCabinetMenu(int id, Inventory inventory, ArchiveCabinetBlockEntity cabinet) {
        super(Tc4Menus.ARCHIVE_CABINET.get(), id);
        this.cabinet = cabinet;
        // A missing client-side entity still yields a usable, empty drawer view.
        var items = cabinet != null ? cabinet.items() : new ItemStackHandler(ArchiveCabinetBlockEntity.SIZE);
        for (int row = 0; row < 3; row++) for (int column = 0; column < 9; column++)
            addSlot(new SlotItemHandler(items, column + row * 9, DRAWER_X + column * 18, DRAWER_Y + row * 26));
        for (int row = 0; row < 3; row++) for (int column = 0; column < 9; column++)
            addSlot(new Slot(inventory, column + row * 9 + 9, INVENTORY_X + column * 18, INVENTORY_Y + row * 18));
        for (int column = 0; column < 9; column++) addSlot(new Slot(inventory, column, INVENTORY_X + column * 18, HOTBAR_Y));
    }

    @Override public boolean stillValid(Player player) {
        return cabinet != null && !cabinet.isRemoved() && player.level().getBlockEntity(cabinet.getBlockPos()) == cabinet
                && player.distanceToSqr(cabinet.getBlockPos().getCenter()) <= 64;
    }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        var slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        var stack = slot.getItem();
        var copy = stack.copy();
        int size = ArchiveCabinetBlockEntity.SIZE;
        if (index < size) {
            if (!moveItemStackTo(stack, size, slots.size(), true)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, 0, size, false)) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return copy;
    }
}
