package github.com.gengyoubo.tcmodern.tc4.menu;

import github.com.gengyoubo.tcmodern.tc4.content.WritingToolItem;
import github.com.gengyoubo.tcmodern.tc4.init.Tc4Items;
import github.com.gengyoubo.tcmodern.tc4.init.Tc4Menus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class WritingToolMenu extends AbstractContainerMenu {
    public static final int WIDTH = 184, HEIGHT = 204, REFILL_X = 20, REFILL_Y = 38, INVENTORY_X = 12, INVENTORY_Y = 122, HOTBAR_Y = 180;
    private final Inventory inventory;
    private final int toolSlot;
    private final Item toolItem;
    private final SimpleContainer input = new SimpleContainer(1);
    private final DataSlot remaining = DataSlot.standalone(), capacity = DataSlot.standalone();

    public WritingToolMenu(int id, Inventory inventory, FriendlyByteBuf data) { this(id, inventory, data.readVarInt()); }
    public WritingToolMenu(int id, Inventory inventory, int toolSlot) {
        super(Tc4Menus.WRITING_TOOL.get(), id);
        this.inventory = inventory;
        this.toolSlot = toolSlot;
        this.toolItem = toolSlot >= 0 && toolSlot < inventory.getContainerSize() ? inventory.getItem(toolSlot).getItem() : null;
        if (toolItem instanceof WritingToolItem writing) {
            int count = writing.stored(tool());
            if (count > 0) input.setItem(0, new ItemStack(writing.refillItem(), count));
        }
        input.addListener(container -> {
            if (!inventory.player.level().isClientSide && stillValid(inventory.player) && toolItem instanceof WritingToolItem writing) {
                writing.store(tool(), input.getItem(0).getCount(), writing.wear(tool()));
                inventory.setChanged();
            }
        });
        addSlot(new Slot(input, 0, REFILL_X, REFILL_Y) {
            @Override public boolean mayPlace(ItemStack stack) { return accepts(stack); }
            @Override public int getMaxStackSize() { return toolItem instanceof WritingToolItem writing ? writing.refillCapacity() : 0; }
        });
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) addInventorySlot(inventory, 9 + col + row * 9, INVENTORY_X + col * 18, INVENTORY_Y + row * 18);
        for (int col = 0; col < 9; col++) addInventorySlot(inventory, col, INVENTORY_X + col * 18, HOTBAR_Y);
        addDataSlot(remaining); addDataSlot(capacity);
    }
    private void addInventorySlot(Inventory inventory, int index, int x, int y) {
        addSlot(new Slot(inventory, index, x, y) {
            @Override public boolean mayPickup(Player player) { return index != toolSlot; }
            @Override public boolean mayPlace(ItemStack stack) { return index != toolSlot; }
        });
    }
    public ItemStack tool() { return toolSlot >= 0 && toolSlot < inventory.getContainerSize() ? inventory.getItem(toolSlot) : ItemStack.EMPTY; }
    public boolean usesLead() { return toolItem == Tc4Items.MECHANICAL_PENCIL.get(); }
    private boolean accepts(ItemStack stack) {
        return !stack.hasTag() && (toolItem == Tc4Items.MECHANICAL_PENCIL.get() ? stack.is(Tc4Items.PENCIL_LEAD.get())
                : toolItem == Tc4Items.FOUNTAIN_PEN.get() && stack.is(Tc4Items.PEN_REFILL.get()));
    }
    public int remaining() { return remaining.get(); }
    public int capacity() { return capacity.get(); }
    @Override public void broadcastChanges() {
        if (!inventory.player.level().isClientSide && stillValid(inventory.player)) {
            var stack = tool(); capacity.set(stack.getMaxDamage()); remaining.set(((WritingToolItem) stack.getItem()).remaining(stack));
        }
        super.broadcastChanges();
    }
    @Override public boolean stillValid(Player player) {
        ItemStack stack = tool();
        return player.isAlive() && stack.getItem() == toolItem && stack.getItem() instanceof WritingToolItem writing && writing.refillAmount() > 0;
    }
    @Override public void clicked(int slot, int button, ClickType type, Player player) {
        if (type == ClickType.SWAP && (button == toolSlot || button == 40 && toolSlot == 40)) return;
        super.clicked(slot, button, type, player);
    }
    @Override public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) { return slot.container != inventory || slot.getContainerSlot() != toolSlot; }
    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        var slot = slots.get(index); if (!slot.hasItem() || !slot.mayPickup(player)) return ItemStack.EMPTY;
        var stack = slot.getItem(); var copy = stack.copy();
        if (!(index == 0 ? moveItemStackTo(stack, 1, slots.size(), true) : accepts(stack) && moveItemStackTo(stack, 0, 1, false))) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        return copy;
    }
}
