package github.com.gengyoubo.tcmodern.tc4.research;

import com.mojang.logging.LogUtils;
import github.com.gengyoubo.mfcl.api.LegacyMappings;
import github.com.gengyoubo.mfcl.api.LegacyRuntime;
import github.com.gengyoubo.tcmodern.api.TcModernApi;
import github.com.gengyoubo.tcmodern.api.research.TcResearch;
import github.com.gengyoubo.tcmodern.tc4.Tc4Modern;
import github.com.gengyoubo.tcmodern.tc4.content.ResearchDeskBlock;
import github.com.gengyoubo.tcmodern.tc4.content.ResearchDeskBlockEntity;
import github.com.gengyoubo.tcmodern.tc4.init.Tc4Blocks;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.items.IItemHandler;

/** Upgrades the original two-part research table without replacing its existing contents. */
@Mod.EventBusSubscriber(modid = Tc4Modern.MOD_ID)
public final class DeskConversion {
    public static final String RESEARCH = "modern1";
    private static final String TABLE = "Thaumcraft:blockTable";
    private static final String WAND = "thaumcraft.common.items.wands.ItemWandCasting";
    private DeskConversion() {}

    private record Table(BlockPos main, BlockPos side, Direction facing) {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void interact(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getEntity().isShiftKeyDown() || event.getItemStack().isEmpty()) return;
        var runtime = TcModernApi.runtime().orElse(null);
        if (runtime == null) return;
        try {
            var legacy = runtime.mappings().toLegacy(LegacyMappings.ITEM_STACK, event.getItemStack()).orElse(null);
            var item = legacy == null ? null : runtime.accessor(DeskAccessors.StackView.class, legacy).item();
            if (item == null || !runtime.values().isInstance(item, WAND)) return;
            var level = event.getLevel();
            var table = table(runtime, level, event.getPos());
            if (table == null) return;
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide));
            if (!(event.getEntity() instanceof ServerPlayer player)) return;
            if (!TcResearch.isComplete(runtime, player, RESEARCH)) {
                player.displayClientMessage(Component.translatable("desk.tc4modern.upgrade_locked"), true);
                return;
            }
            if (!convert(level, table)) {
                player.displayClientMessage(Component.translatable("desk.tc4modern.upgrade_unavailable"), true);
                return;
            }
            level.playSound(null, table.main(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 1F, 1F);
        } catch (RuntimeException failure) {
            LogUtils.getLogger().warn("Could not upgrade the original research table", failure);
        }
    }

    private static Table table(LegacyRuntime runtime, Level level, BlockPos clicked) {
        BlockState state = level.getBlockState(clicked);
        for (int meta = 2; meta <= 9; meta++) {
            if (!runtime.hostBlock(TABLE, meta, BlockState.class).map(state::equals).orElse(false)) continue;
            Direction towardPartner = Direction.from3DDataValue(meta < 6 ? meta : meta - 4);
            BlockPos other = clicked.relative(towardPartner);
            int oppositeMeta = towardPartner.getOpposite().get3DDataValue() + (meta < 6 ? 4 : 0);
            if (!level.isLoaded(other) || !runtime.hostBlock(TABLE, oppositeMeta, BlockState.class)
                    .map(level.getBlockState(other)::equals).orElse(false)) return null;
            BlockPos main = meta < 6 ? clicked : other, side = meta < 6 ? other : clicked;
            Direction sideDirection = meta < 6 ? towardPartner : towardPartner.getOpposite();
            return new Table(main, side, sideDirection.getClockWise());
        }
        return null;
    }

    private static boolean convert(Level level, Table table) {
        var oldTile = level.getBlockEntity(table.main());
        if (oldTile == null) return false;
        IItemHandler old = oldTile.getCapability(ForgeCapabilities.ITEM_HANDLER).orElse(null);
        if (old == null || old.getSlots() != 2) return false;
        var contents = new ArrayList<ItemStack>();
        for (int slot = 0; slot < old.getSlots(); slot++) {
            ItemStack present = old.getStackInSlot(slot);
            ItemStack extracted = present.isEmpty() ? ItemStack.EMPTY : old.extractItem(slot, present.getCount(), true);
            if (!ItemStack.matches(present, extracted)) return false;
            contents.add(extracted.copy());
        }
        var removed = new ArrayList<ItemStack>();
        for (int slot = 0; slot < contents.size(); slot++) {
            ItemStack expected = contents.get(slot);
            ItemStack taken = expected.isEmpty() ? ItemStack.EMPTY : old.extractItem(slot, expected.getCount(), false);
            removed.add(taken);
            if (!ItemStack.matches(expected, taken)) {
                restore(level, table.main(), old, removed);
                return false;
            }
        }
        var desk = Tc4Blocks.RESEARCH_DESK.get();
        var main = desk.defaultBlockState().setValue(ResearchDeskBlock.FACING, table.facing()).setValue(ResearchDeskBlock.HALF, ResearchDeskBlock.Half.MAIN);
        var side = main.setValue(ResearchDeskBlock.HALF, ResearchDeskBlock.Half.SIDE);
        // Replace both halves before issuing neighbour updates, so neither multiblock destroys its partner.
        level.setBlock(table.main(), main, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        level.setBlock(table.side(), side, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        if (!(level.getBlockEntity(table.main()) instanceof ResearchDeskBlockEntity modern)) {
            for (ItemStack stack : removed) if (!stack.isEmpty()) net.minecraft.world.Containers.dropItemStack(level, table.main().getX(), table.main().getY(), table.main().getZ(), stack);
            throw new IllegalStateException("Modern research desk block entity did not appear");
        }
        for (int slot = 0; slot < removed.size(); slot++) modern.items().setStackInSlot(slot, removed.get(slot));
        level.blockUpdated(table.main(), desk);
        level.blockUpdated(table.side(), desk);
        return true;
    }

    private static void restore(Level level, BlockPos pos, IItemHandler inventory, List<ItemStack> removed) {
        for (int slot = 0; slot < removed.size(); slot++) if (!removed.get(slot).isEmpty()) {
            var leftover = inventory.insertItem(slot, removed.get(slot), false);
            if (!leftover.isEmpty()) net.minecraft.world.Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), leftover);
        }
    }
}
