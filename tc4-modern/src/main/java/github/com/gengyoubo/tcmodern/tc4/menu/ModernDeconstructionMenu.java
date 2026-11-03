package github.com.gengyoubo.tcmodern.tc4.menu;

import github.com.gengyoubo.tcmodern.tc4.content.ModernDeconstructionBlockEntity;
import github.com.gengyoubo.tcmodern.tc4.init.Tc4Menus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;
import github.com.gengyoubo.tcmodern.api.TcModernApi;
import github.com.gengyoubo.tcmodern.api.aspect.TcAspects;
import github.com.gengyoubo.tcmodern.api.aspect.TcAspect;
import java.util.*;

public final class ModernDeconstructionMenu extends AbstractContainerMenu {
    public static final int WIDTH=184, HEIGHT=204, INPUT_X=20, INPUT_Y=38, INVENTORY_X=12, INVENTORY_Y=122, HOTBAR_Y=180;
    private final ModernDeconstructionBlockEntity table;
    private final List<TcAspect> aspectRegistry;
    private final DataSlot[] aspectIds = new DataSlot[6], amounts = new DataSlot[6];
    private final DataSlot progress=DataSlot.standalone(), status=DataSlot.standalone();

    public ModernDeconstructionMenu(int id, Inventory inventory, FriendlyByteBuf data) {
        this(id, inventory, inventory.player.level().getBlockEntity(data.readBlockPos()) instanceof ModernDeconstructionBlockEntity found ? found : null);
    }
    public ModernDeconstructionMenu(int id, Inventory inventory, ModernDeconstructionBlockEntity table) {
        super(Tc4Menus.DECONSTRUCTION.get(), id); this.table = table; this.aspectRegistry = registry();
        addSlot(new SlotItemHandler(table == null ? new ItemStackHandler(1) : table.items(), 0, INPUT_X, INPUT_Y));
        for (int row=0; row<3; row++) for (int col=0; col<9; col++)
            addSlot(new Slot(inventory, 9 + col + row*9, INVENTORY_X + col*18, INVENTORY_Y + row*18));
        for (int col=0; col<9; col++) addSlot(new Slot(inventory, col, INVENTORY_X + col*18, HOTBAR_Y));
        addDataSlot(progress); addDataSlot(status);
        for (int i=0; i<6; i++) {
            aspectIds[i]=DataSlot.standalone(); amounts[i]=DataSlot.standalone();
            addDataSlot(aspectIds[i]); addDataSlot(amounts[i]);
        }
    }
    public record Result(TcAspect aspect, int amount) { }
    private static List<TcAspect> registry() {
        return TcModernApi.runtime().map(TcAspects::snapshot).orElse(List.of()).stream().sorted(Comparator.comparing(TcAspect::tag)).toList();
    }
    public boolean hasPending() { return amounts[0].get() > 0; }
    public int progress() { return progress.get(); }
    public int status() { return status.get(); }
    public List<Result> results() {
        var registry=aspectRegistry; var result=new ArrayList<Result>();
        for (int i=0; i<6; i++) {
            int index=aspectIds[i].get()-1, amount=amounts[i].get();
            if (index>=0 && index<registry.size() && amount>0) result.add(new Result(registry.get(index),amount));
        }
        return result;
    }
    @Override public void broadcastChanges() {
        if (table != null && table.getLevel() != null && !table.getLevel().isClientSide) {
            progress.set(table.progress()); status.set(table.status());
            var registry=aspectRegistry; var pending=table.pendingAspects(); int slot=0;
            for (int i=0; i<registry.size() && slot<6; i++) {
                int count=pending.getOrDefault(registry.get(i).tag(),0);
                if (count>0) { aspectIds[slot].set(i+1); amounts[slot].set(count); slot++; }
            }
            while (slot<6) { aspectIds[slot].set(0); amounts[slot].set(0); slot++; }
        }
        super.broadcastChanges();
    }
    @Override public boolean clickMenuButton(Player player, int id) {
        if (!(player instanceof ServerPlayer server) || table == null || !stillValid(player)) return false;
        if (id == 1) table.claim(server);
        else return false;
        broadcastChanges(); return true;
    }
    @Override public boolean stillValid(Player player) {
        return table != null && !table.isRemoved() && player.level().getBlockEntity(table.getBlockPos()) == table
                && player.distanceToSqr(table.getBlockPos().getCenter()) <= 64;
    }
    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        var slot = slots.get(index); if (!slot.hasItem()) return ItemStack.EMPTY;
        var stack = slot.getItem(); var copy = stack.copy();
        if (!(index == 0 ? moveItemStackTo(stack, 1, slots.size(), true) : moveItemStackTo(stack, 0, 1, false))) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        return copy;
    }
}
