package github.com.gengyoubo.tcmodern.tc4.content;

import github.com.gengyoubo.tcmodern.tc4.init.Tc4BlockEntities;
import github.com.gengyoubo.tcmodern.tc4.menu.ResearchDeskMenu;
import github.com.gengyoubo.tcmodern.tc4.research.ResearchItems;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Scribing tools and the working note, plus the ink saving. Lives on the desk's main half only. */
public final class ResearchDeskBlockEntity extends BlockEntity implements MenuProvider {
    public static final int TOOLS = 0, NOTE = 1;
    /** Every fifth written hex leaves the ink untouched: 20% less ink than the original table. */
    public static final int INK_FREE_EVERY = 5;
    /** Cabinets farther than this many links from the desk are not part of the structure. */
    public static final int MAX_CABINETS = 16;

    private final ItemStackHandler items = new ItemStackHandler(2) {
        @Override public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return slot == TOOLS ? ResearchItems.isScribeTools(stack) : ResearchItems.isNote(stack);
        }
        @Override public int getSlotLimit(int slot) { return slot == TOOLS ? 1 : 64; }
        @Override protected void onContentsChanged(int slot) { changed(); }
    };
    private final LazyOptional<ItemStackHandler> capability = LazyOptional.of(() -> items);
    private int writes;

    public ResearchDeskBlockEntity(BlockPos pos, BlockState state) { super(Tc4BlockEntities.RESEARCH_DESK.get(), pos, state); }

    public ItemStackHandler items() { return items; }
    public ItemStack tools() { return items.getStackInSlot(TOOLS); }
    public ItemStack note() { return items.getStackInSlot(NOTE); }

    /** Counts one written hex; true when this one costs no ink. */
    public boolean inkFree() {
        writes = (writes + 1) % INK_FREE_EVERY;
        setChanged();
        return writes == 0;
    }

    /** Hexes written since the last free one, for the ink gauge. */
    public int writes() { return writes; }

    /**
     * The archive cabinets joined to this desk: every cabinet touching either half, then every cabinet touching one
     * of those, breadth first, up to {@link #MAX_CABINETS}. The order is stable for the same build.
     */
    public List<ArchiveCabinetBlockEntity> cabinets() {
        if (level == null) return List.of();
        var start = new ArrayList<BlockPos>();
        start.add(worldPosition);
        var state = getBlockState();
        if (state.getBlock() instanceof ResearchDeskBlock) start.add(ResearchDeskBlock.partner(worldPosition, state));
        var seen = new HashSet<>(start);
        var queue = new ArrayDeque<>(start);
        var result = new ArrayList<ArchiveCabinetBlockEntity>();
        while (!queue.isEmpty() && result.size() < MAX_CABINETS) {
            var current = queue.poll();
            for (var direction : Direction.values()) {
                var next = current.relative(direction);
                if (!seen.add(next) || !level.isLoaded(next)) continue;
                if (level.getBlockEntity(next) instanceof ArchiveCabinetBlockEntity cabinet) {
                    result.add(cabinet);
                    queue.add(next);
                    if (result.size() == MAX_CABINETS) break;
                }
            }
        }
        return result;
    }

    private void changed() {
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    public void dropContents() {
        if (level == null) return;
        for (int i = 0; i < items.getSlots(); i++) Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), items.getStackInSlot(i));
        for (int i = 0; i < items.getSlots(); i++) items.setStackInSlot(i, ItemStack.EMPTY);
    }

    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Items", items.serializeNBT());
        tag.putInt("Writes", writes);
    }

    @Override public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("Items")) items.deserializeNBT(tag.getCompound("Items"));
        writes = tag.getInt("Writes");
    }

    /** Clients render the tools and note on the desktop. */
    @Override public CompoundTag getUpdateTag() { return saveWithoutMetadata(); }
    @Override public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }

    /** The rendered note overhangs onto the side half. */
    @Override public AABB getRenderBoundingBox() { return new AABB(worldPosition).inflate(1, 0.5, 1); }

    @Override public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        return cap == ForgeCapabilities.ITEM_HANDLER ? capability.cast() : super.getCapability(cap, side);
    }

    @Override public void invalidateCaps() {
        super.invalidateCaps();
        capability.invalidate();
    }

    @Override public Component getDisplayName() { return Component.translatable("block.tc4modern.research_desk"); }

    @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new ResearchDeskMenu(id, inventory, this);
    }
}
