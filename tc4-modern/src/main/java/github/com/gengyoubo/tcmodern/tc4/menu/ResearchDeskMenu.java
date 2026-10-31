package github.com.gengyoubo.tcmodern.tc4.menu;

import github.com.gengyoubo.tcmodern.tc4.content.ResearchDeskBlockEntity;
import github.com.gengyoubo.tcmodern.tc4.init.Tc4Menus;
import github.com.gengyoubo.tcmodern.tc4.network.DeskNetwork;
import github.com.gengyoubo.tcmodern.tc4.research.DeskLogic;
import github.com.gengyoubo.tcmodern.tc4.research.DeskSnapshot;
import github.com.gengyoubo.tcmodern.tc4.research.ResearchItems;
import java.util.Arrays;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;

/**
 * Desk slots plus the viewer's inventory. The server pushes a {@link DeskSnapshot} when it changes; research actions
 * arrive through {@link DeskNetwork} and are checked against this menu.
 */
public final class ResearchDeskMenu extends AbstractContainerMenu {
    // Canvas layout shared with the screen.
    public static final int WIDTH = 440, HEIGHT = 264;
    public static final int TOOLS_X = 317, TOOLS_Y = 39, NOTE_X = 317, NOTE_Y = 61;
    public static final int INVENTORY_X = 142, INVENTORY_Y = 185, HOTBAR_Y = 243;
    private static final int REFRESH_TICKS = 10;

    private final ResearchDeskBlockEntity desk;
    private final Player player;
    private DeskSnapshot snapshot = DeskSnapshot.EMPTY;
    private byte[] sent;
    private int ticks;
    private boolean dirty = true;

    public ResearchDeskMenu(int id, Inventory inventory, FriendlyByteBuf data) {
        this(id, inventory, inventory.player.level().getBlockEntity(data.readBlockPos()) instanceof ResearchDeskBlockEntity desk ? desk : null);
    }

    public ResearchDeskMenu(int id, Inventory inventory, ResearchDeskBlockEntity desk) {
        super(Tc4Menus.RESEARCH_DESK.get(), id);
        this.desk = desk;
        this.player = inventory.player;
        // A missing client-side entity still keeps the slot indices aligned with the server.
        var items = desk != null ? desk.items() : new ItemStackHandler(2);
        addSlot(new SlotItemHandler(items, ResearchDeskBlockEntity.TOOLS, TOOLS_X, TOOLS_Y));
        addSlot(new SlotItemHandler(items, ResearchDeskBlockEntity.NOTE, NOTE_X, NOTE_Y));
        for (int row = 0; row < 3; row++) for (int column = 0; column < 9; column++)
            addSlot(new Slot(inventory, column + row * 9 + 9, INVENTORY_X + column * 18, INVENTORY_Y + row * 18));
        for (int column = 0; column < 9; column++) addSlot(new Slot(inventory, column, INVENTORY_X + column * 18, HOTBAR_Y));
    }

    public ResearchDeskBlockEntity desk() { return desk; }
    public DeskSnapshot snapshot() { return snapshot; }
    public void snapshot(DeskSnapshot snapshot) { this.snapshot = snapshot; }

    /** Asks for a fresh snapshot on the next tick, after an action changed the desk or the player. */
    public void invalidate() { dirty = true; }

    @Override public void broadcastChanges() {
        super.broadcastChanges();
        if (desk == null || !(player instanceof ServerPlayer server)) return;
        if (!dirty && ++ticks < REFRESH_TICKS) return;
        ticks = 0;
        dirty = false;
        var next = DeskLogic.snapshot(server, desk);
        var buffer = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        next.write(buffer);
        var bytes = Arrays.copyOf(buffer.array(), buffer.writerIndex());
        buffer.release();
        if (Arrays.equals(bytes, sent)) return;
        sent = bytes;
        snapshot = next;
        DeskNetwork.snapshot(server, containerId, next);
    }

    @Override public boolean stillValid(Player player) {
        return desk != null && !desk.isRemoved() && player.level().getBlockEntity(desk.getBlockPos()) == desk
                && player.distanceToSqr(desk.getBlockPos().getCenter()) <= 64;
    }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        var slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        var stack = slot.getItem();
        var copy = stack.copy();
        if (index < 2) {
            if (!moveItemStackTo(stack, 2, slots.size(), true)) return ItemStack.EMPTY;
        } else if (ResearchItems.isScribeTools(stack)) {
            if (!moveItemStackTo(stack, 0, 1, false)) return ItemStack.EMPTY;
        } else if (ResearchItems.isNote(stack)) {
            if (!moveItemStackTo(stack, 1, 2, false)) return ItemStack.EMPTY;
        } else return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return copy;
    }
}
