package github.com.gengyoubo.tcmodern.tc4.content;

import com.mojang.logging.LogUtils;
import github.com.gengyoubo.mfcl.api.LegacyMappings;
import github.com.gengyoubo.mfcl.api.LegacyRuntime;
import github.com.gengyoubo.tcmodern.api.TcModernApi;
import github.com.gengyoubo.tcmodern.api.aspect.*;
import github.com.gengyoubo.tcmodern.api.scan.ScanKey;
import github.com.gengyoubo.tcmodern.api.scan.TcScanApi;
import github.com.gengyoubo.tcmodern.lib.scan.ScanRules;
import github.com.gengyoubo.tcmodern.tc4.init.Tc4BlockEntities;
import github.com.gengyoubo.tcmodern.tc4.menu.ModernDeconstructionMenu;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

/** The input and unclaimed results belong to the block, not the player currently viewing it. */
public final class ModernDeconstructionBlockEntity extends BlockEntity implements MenuProvider {
    public static final int PROCESS_TICKS = 40;
    public static final int IDLE=0, WORKING=1, INVALID=2, FAILED=3, READY=4;
    private final ItemStackHandler items = new ItemStackHandler(1) {
        @Override protected void onContentsChanged(int slot) { progress=0; status=IDLE; cachedInput=ItemStack.EMPTY; cachedAmounts=Map.of(); setChanged(); }
    };
    private final Map<String, Integer> pending = new LinkedHashMap<>();
    private int progress,status;
    private ItemStack cachedInput=ItemStack.EMPTY;
    private Map<String,Integer> cachedAmounts=Map.of();
    private LegacyRuntime cachedRuntime;
    private long cachedRevision=-1;
    private boolean loggedFailure;

    public ModernDeconstructionBlockEntity(BlockPos pos, BlockState state) { super(Tc4BlockEntities.DECONSTRUCTION.get(), pos, state); }
    public ItemStackHandler items() { return items; }
    public int pendingCount() { return pending.values().stream().mapToInt(Integer::intValue).sum(); }
    public Map<String, Integer> pendingAspects() { return Map.copyOf(pending); }
    public int progress() { return progress; }
    public int status() { return status; }
    private boolean primal() { return getBlockState().getBlock() instanceof ModernDeconstructionBlock block && block.primal(); }
    @Override public Component getDisplayName() { return getBlockState().getBlock().getName(); }
    @Nullable @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) { return new ModernDeconstructionMenu(id, inventory, this); }
    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag); tag.put("Items", items.serializeNBT());
        var result = new CompoundTag(); pending.forEach(result::putInt); tag.put("Pending", result); tag.putInt("Progress",progress);
    }
    @Override public void load(CompoundTag tag) {
        super.load(tag); if (tag.contains("Items")) items.deserializeNBT(tag.getCompound("Items"));
        pending.clear(); var result = tag.getCompound("Pending");
        for (String key : result.getAllKeys()) { int n = result.getInt(key); if (n > 0 && n <= TcPlayerAspects.MAX_POOL) pending.put(key, n); }
        progress=Math.max(0,Math.min(PROCESS_TICKS-1,tag.getInt("Progress"))); status=pending.isEmpty()?IDLE:READY;
        cachedInput=ItemStack.EMPTY; cachedRuntime=null; cachedRevision=-1;
    }
    public void dropContents() {
        if (level != null && !level.isClientSide) {
            var stack = items.getStackInSlot(0);
            if (!stack.isEmpty()) Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), stack);
            items.setStackInSlot(0, ItemStack.EMPTY);
        }
    }
    private static void message(Player player, String key) { player.displayClientMessage(Component.translatable("deconstruction.tc4modern." + key), true); }

    /** As in TC4, processing begins automatically and takes 40 server ticks per item. */
    public static void tick(net.minecraft.world.level.Level level,BlockPos pos,BlockState state,ModernDeconstructionBlockEntity table) {
        if (level.isClientSide) return;
        if (!table.pending.isEmpty()) { table.status=READY; return; }
        var stack=table.items.getStackInSlot(0);
        if (stack.isEmpty()) { if (table.status!=FAILED) table.status=IDLE; return; }
        var runtime=TcModernApi.runtime().orElse(null); if (runtime==null) return;
        try {
            long revision=TcScanApi.registry().revision();
            if (runtime!=table.cachedRuntime || revision!=table.cachedRevision || !ItemStack.isSameItemSameTags(stack,table.cachedInput)) {
                table.cachedInput=stack.copyWithCount(1); table.cachedRuntime=runtime; table.cachedRevision=revision;
                var amounts=itemAspects(runtime,stack);
                table.cachedAmounts=table.primal() && !amounts.isEmpty()?PrimalDecomposition.expand(amounts,TcAspects.snapshot(runtime)):amounts;
            }
            if (table.cachedAmounts.isEmpty() || table.primal() && table.cachedAmounts.values().stream().anyMatch(n->n>TcPlayerAspects.MAX_POOL)) {
                if (table.progress!=0) { table.progress=0; table.setChanged(); }
                table.status=INVALID; return;
            }
            table.status=WORKING; table.progress++; table.setChanged();
            if (table.progress<PROCESS_TICKS) return;
            Map<String,Integer> result;
            if (table.primal()) result=table.cachedAmounts;
            else {
                var tags = new ArrayList<>(table.cachedAmounts.keySet());
                result = level.random.nextBoolean() ? Map.of(tags.get(level.random.nextInt(tags.size())), 1) : Map.of();
            }
            table.pending.putAll(result);
            table.items.extractItem(0,1,false);
            table.status=result.isEmpty()?FAILED:READY; table.setChanged();
        } catch (RuntimeException failure) {
            table.status=INVALID;
            if (!table.loggedFailure) { table.loggedFailure=true; LogUtils.getLogger().warn("Could not deconstruct item at {}",pos,failure); }
        }
    }
    public void claim(ServerPlayer player) {
        if (level == null || level.isClientSide || pending.isEmpty()) return;
        var runtime = TcModernApi.runtime().orElse(null); if (runtime == null) return;
        try {
            for (var entry : pending.entrySet()) if (TcPlayerAspects.pool(runtime, player, entry.getKey()) > TcPlayerAspects.MAX_POOL - entry.getValue()) {
                message(player, "full"); return;
            }
            TcPlayerAspects.give(runtime, player, pending);
            pending.clear(); status=IDLE; setChanged(); message(player, "success");
        } catch (RuntimeException failure) { LogUtils.getLogger().warn("Could not claim deconstruction results", failure); message(player, "error"); }
    }
    public static Map<String, Integer> itemAspects(LegacyRuntime runtime, ItemStack stack) {
        var amounts = new LinkedHashMap<String, Integer>();
        var source = ScanRules.server().get(ScanKey.item(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()));
        if (source != null) { amounts.putAll(source.amounts()); return amounts; }
        var legacy = runtime.mappings().toLegacy(LegacyMappings.ITEM_STACK, stack.copyWithCount(1)).orElse(null);
        if (legacy == null) return amounts;
        var crafting = runtime.accessor(TcAspectAccessors.Crafting.class);
        var tags = crafting.bonus(legacy, crafting.tags(legacy));
        if (tags != null) {
            var list = runtime.accessor(TcAspectAccessors.Aspects.class, tags);
            for (var aspect : list.values()) {
                int n = list.amount(aspect);
                if (n > 0) amounts.put(runtime.accessor(TcAspectAccessors.Aspect.class, aspect).tag(), n);
            }
        }
        return amounts;
    }
}
