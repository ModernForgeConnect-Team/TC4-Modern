package github.com.gengyoubo.tcmodern.tc4.content;

import github.com.gengyoubo.tcmodern.tc4.init.Tc4BlockEntities;
import github.com.gengyoubo.tcmodern.tc4.menu.ArchiveCabinetMenu;
import github.com.gengyoubo.tcmodern.tc4.research.ResearchItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Three drawers of nine notes. Hoppers and pipes see the same note-only filter. */
public final class ArchiveCabinetBlockEntity extends BlockEntity implements MenuProvider {
    public static final int SIZE = 27;

    private final ItemStackHandler items = new ItemStackHandler(SIZE) {
        @Override public boolean isItemValid(int slot, @NotNull ItemStack stack) { return ResearchItems.isNote(stack); }
        @Override protected void onContentsChanged(int slot) { setChanged(); }
    };
    private final LazyOptional<ItemStackHandler> capability = LazyOptional.of(() -> items);

    public ArchiveCabinetBlockEntity(BlockPos pos, BlockState state) { super(Tc4BlockEntities.ARCHIVE_CABINET.get(), pos, state); }

    public ItemStackHandler items() { return items; }

    /** First empty drawer slot, or -1. */
    public int freeSlot() {
        for (int i = 0; i < SIZE; i++) if (items.getStackInSlot(i).isEmpty()) return i;
        return -1;
    }

    public void dropContents() {
        if (level == null) return;
        for (int i = 0; i < SIZE; i++) Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), items.getStackInSlot(i));
        for (int i = 0; i < SIZE; i++) items.setStackInSlot(i, ItemStack.EMPTY);
    }

    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Items", items.serializeNBT());
    }

    @Override public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("Items")) items.deserializeNBT(tag.getCompound("Items"));
    }

    @Override public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        return cap == ForgeCapabilities.ITEM_HANDLER ? capability.cast() : super.getCapability(cap, side);
    }

    @Override public void invalidateCaps() {
        super.invalidateCaps();
        capability.invalidate();
    }

    @Override public Component getDisplayName() { return Component.translatable("block.tc4modern.archive_cabinet"); }

    @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new ArchiveCabinetMenu(id, inventory, this);
    }
}
