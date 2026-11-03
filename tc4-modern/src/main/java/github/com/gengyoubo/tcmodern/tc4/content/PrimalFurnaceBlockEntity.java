package github.com.gengyoubo.tcmodern.tc4.content;

import com.mojang.logging.LogUtils;
import github.com.gengyoubo.mfcl.api.*;
import github.com.gengyoubo.tcmodern.api.TcModernApi;
import github.com.gengyoubo.tcmodern.api.aspect.*;
import github.com.gengyoubo.tcmodern.tc4.init.Tc4BlockEntities;
import github.com.gengyoubo.tcmodern.tc4.menu.PrimalFurnaceMenu;
import github.com.gengyoubo.tcmodern.api.scan.TcScanApi;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

/** Accepts essentia below and feeds its primal buffer into up to five alembics above. */
public final class PrimalFurnaceBlockEntity extends BlockEntity implements MenuProvider {
    private static final String TRANSPORT="thaumcraft.api.aspects.IEssentiaTransport";
    private static final String ALEMBIC="thaumcraft.common.tiles.TileAlembic";
    private final PrimalFurnaceBuffer buffer=new PrimalFurnaceBuffer();
    private final ItemStackHandler items=new ItemStackHandler(2) {
        @Override public boolean isItemValid(int slot,@NotNull ItemStack stack) { return slot==0 || slot==1 && fuelTime(stack)>0; }
        @Override protected void onContentsChanged(int slot) {
            if (slot==0) { cachedInput=ItemStack.EMPTY; cachedYield=Map.of(); cookTime=0; }
            setChanged();
        }
    };
    private final LazyOptional<ItemStackHandler> itemCapability=LazyOptional.of(()->items);
    private ItemStack cachedInput=ItemStack.EMPTY;
    private Map<String,Integer> cachedYield=Map.of();
    private LegacyRuntime cachedRuntime;
    private long cachedRevision=-1;
    private int burnTime,burnTotal,cookTime,cookTotal;
    private boolean loggedFailure;

    public PrimalFurnaceBlockEntity(BlockPos pos,BlockState state) { super(Tc4BlockEntities.PRIMAL_FURNACE.get(),pos,state); }
    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag); var contents=new CompoundTag(); buffer.snapshot().forEach(contents::putInt); tag.put("PrimalAspects",contents);
        tag.put("Items",items.serializeNBT()); tag.putInt("BurnTime",burnTime); tag.putInt("BurnTotal",burnTotal);
        tag.putInt("CookTime",cookTime); tag.putInt("CookTotal",cookTotal);
    }
    @Override public void load(CompoundTag tag) {
        super.load(tag); buffer.clear(); var contents=tag.getCompound("PrimalAspects");
        for (String key:contents.getAllKeys()) {
            int amount=contents.getInt(key);
            if (key.matches("[a-z][a-z0-9_]*") && amount>0 && amount<=PrimalFurnaceBuffer.CAPACITY_PER_ASPECT) buffer.add(Map.of(key,amount));
        }
        if (tag.contains("Items")) items.deserializeNBT(tag.getCompound("Items"));
        burnTime=Math.max(0,tag.getInt("BurnTime")); burnTotal=Math.max(burnTime,tag.getInt("BurnTotal"));
        cookTotal=Math.max(0,tag.getInt("CookTotal")); cookTime=Math.max(0,Math.min(tag.getInt("CookTime"),cookTotal));
        cachedInput=ItemStack.EMPTY; cachedRuntime=null; cachedRevision=-1;
    }
    public ItemStackHandler items() { return items; }
    public int burnFraction() { return burnTotal<=0?0:(int)Math.min(1000,1000L*burnTime/burnTotal); }
    public int cookFraction() { return cookTotal<=0?0:(int)Math.min(1000,1000L*cookTime/cookTotal); }
    public static int fuelTime(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        int modern=ForgeHooks.getBurnTime(stack,RecipeType.SMELTING);
        if (modern>0) return modern;
        var runtime=TcModernApi.runtime().orElse(null);
        if (runtime==null) return 0;
        try {
            var legacy=runtime.mappings().toLegacy(LegacyMappings.ITEM_STACK,stack.copyWithCount(1)).orElse(null);
            return legacy==null?0:Math.max(0,runtime.accessor(ReactorAccessors.FurnaceFuel.class).burnTime(legacy));
        } catch (RuntimeException unavailable) { return 0; }
    }
    @Override public Component getDisplayName() { return getBlockState().getBlock().getName(); }
    @Nullable @Override public AbstractContainerMenu createMenu(int id,Inventory inventory,Player player) { return new PrimalFurnaceMenu(id,inventory,this); }
    @Override public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> cap,@Nullable Direction side) {
        return cap==ForgeCapabilities.ITEM_HANDLER?itemCapability.cast():super.getCapability(cap,side);
    }
    @Override public void invalidateCaps() { super.invalidateCaps(); itemCapability.invalidate(); }
    public void dropItems() {
        if (level==null || level.isClientSide) return;
        for (int slot=0;slot<items.getSlots();slot++) {
            var stack=items.getStackInSlot(slot);
            if (!stack.isEmpty()) Containers.dropItemStack(level,worldPosition.getX(),worldPosition.getY(),worldPosition.getZ(),stack);
            items.setStackInSlot(slot,ItemStack.EMPTY);
        }
    }
    public Map<String,Integer> contents() { return buffer.snapshot(); }
    public Component describe() {
        if (buffer.snapshot().isEmpty()) return Component.translatable("primal_furnace.tc4modern.empty");
        var result=Component.empty(); boolean first=true;
        for (var entry:new TreeMap<>(buffer.snapshot()).entrySet()) {
            if (!first) result.append(", "); first=false;
            result.append(Component.translatable("tc.aspect."+entry.getKey())).append(" "+entry.getValue());
        }
        return result;
    }

    public static void tick(Level level,BlockPos pos,BlockState state,PrimalFurnaceBlockEntity furnace) {
        if (level.isClientSide) return;
        if (level.getGameTime()%20==0) PrimalFurnaceBlock.refreshConnections(level,pos,state);
        var runtime=TcModernApi.runtime().orElse(null); if (runtime==null) return;
        try {
            furnace.tickSmelting(runtime);
            if (level.getGameTime()%10!=0) return;
            var oldWorld=runtime.mappings().toLegacy(LegacyMappings.WORLD,level).orElse(null); if (oldWorld==null) return;
            var world=runtime.accessor(ReactorAccessors.World.class,oldWorld);
            if (level.getGameTime()%40==0) furnace.fillAlembics(runtime,world);
            furnace.pull(runtime,world);
        } catch (RuntimeException failure) {
            if (!furnace.loggedFailure) { furnace.loggedFailure=true; LogUtils.getLogger().error("Primal furnace at {} could not transfer essentia",pos,failure); }
        }
    }
    /** Item tags use the same TC4 and modern overrides as the original furnace and scanner. */
    private void refreshRecipe(LegacyRuntime runtime) {
        var input=items.getStackInSlot(0); long revision=TcScanApi.registry().revision();
        if (runtime==cachedRuntime && revision==cachedRevision && ItemStack.isSameItemSameTags(input,cachedInput)) return;
        boolean changed=!ItemStack.isSameItemSameTags(input,cachedInput) && !cachedInput.isEmpty();
        cachedInput=input.copyWithCount(input.isEmpty()?0:1); cachedRuntime=runtime; cachedRevision=revision;
        if (changed) cookTime=0;
        var aspects=input.isEmpty()?Map.<String,Integer>of():ModernDeconstructionBlockEntity.itemAspects(runtime,input);
        if (aspects.isEmpty()) { cachedYield=Map.of(); cookTime=0; cookTotal=0; return; }
        cachedYield=PrimalDecomposition.expand(aspects,TcAspects.snapshot(runtime));
        long total=aspects.values().stream().mapToLong(Integer::longValue).sum();
        cookTotal=(int)Math.min(Integer.MAX_VALUE,Math.max(1,total*10));
        cookTime=Math.min(cookTime,cookTotal);
    }
    private void tickSmelting(LegacyRuntime runtime) {
        boolean dirty=burnTime>0;
        if (burnTime>0) burnTime--;
        refreshRecipe(runtime);
        if (!cachedYield.isEmpty() && buffer.canAdd(cachedYield)) {
            if (burnTime==0) {
                var fuel=items.getStackInSlot(1); int duration=fuelTime(fuel);
                if (duration>0) {
                    var remainder=fuel.getCraftingRemainingItem();
                    items.extractItem(1,1,false);
                    if (!remainder.isEmpty()) {
                        if (items.getStackInSlot(1).isEmpty()) items.setStackInSlot(1,remainder);
                        else Containers.dropItemStack(level,worldPosition.getX()+0.5,worldPosition.getY()+1,worldPosition.getZ()+0.5,remainder);
                    }
                    burnTime=duration; burnTotal=duration; dirty=true;
                }
            }
            if (burnTime>0) {
                cookTime++; dirty=true;
                if (cookTime>=cookTotal) {
                    if (!buffer.add(cachedYield)) throw new IllegalStateException("Primal buffer changed during smelting");
                    items.extractItem(0,1,false); cookTime=0; dirty=true;
                }
            }
        }
        if (dirty) setChanged();
    }
    private LegacyObject neighbor(LegacyRuntime runtime,ReactorAccessors.World world,BlockPos pos) {
        if (!level.isLoaded(pos)) return null;
        var tile=world.tile(pos.getX(),pos.getY(),pos.getZ());
        return tile!=null && runtime.values().isInstance(tile,TRANSPORT)?tile:null;
    }
    private LegacyObject side(LegacyRuntime runtime,Direction outward) {
        return runtime.accessor(ReactorAccessors.Directions.class).of(outward.getOpposite().get3DDataValue());
    }
    private void changed(ReactorAccessors.World world,BlockPos pos) {
        var tile=level.getBlockEntity(pos); if (tile!=null) tile.setChanged();
        world.update(pos.getX(),pos.getY(),pos.getZ());
    }
    private void pull(LegacyRuntime runtime,ReactorAccessors.World world) {
        var pos=worldPosition.below(); var source=neighbor(runtime,world,pos); if (source==null) return;
        var transport=runtime.accessor(ReactorAccessors.Transport.class,source); var side=side(runtime,Direction.DOWN);
        if (!transport.canOutput(side) || transport.amount(side)<=0) return;
        var aspect=transport.type(side); if (aspect==null) return;
        String tag=runtime.accessor(TcAspectAccessors.Aspect.class,aspect).tag();
        var yield=PrimalDecomposition.expand(Map.of(tag,1),TcAspects.snapshot(runtime));
        if (!buffer.canAdd(yield)) return;
        if (transport.take(aspect,1,side)==1) {
            if (!buffer.add(yield)) throw new IllegalStateException("Primal buffer changed during transfer");
            setChanged(); changed(world,pos);
        }
    }
    private void fillAlembics(LegacyRuntime runtime,ReactorAccessors.World world) {
        if (buffer.snapshot().isEmpty()) return;
        var lookup=runtime.accessor(TcAspectAccessors.AspectLookup.class);
        var alembics=new ArrayList<Map.Entry<BlockPos,ReactorAccessors.Alembic>>();
        for (int offset=1;offset<=5;offset++) {
            var pos=worldPosition.above(offset); if (!level.isLoaded(pos)) break;
            var target=world.tile(pos.getX(),pos.getY(),pos.getZ());
            if (target==null || !runtime.values().isInstance(target,ALEMBIC)) break;
            alembics.add(Map.entry(pos,runtime.accessor(ReactorAccessors.Alembic.class,target)));
        }
        var filled=new HashSet<String>();
        // Continue filling already assigned alembics first, as the original alchemical furnace does.
        for (var entry:alembics) {
            var alembic=entry.getValue(); var aspect=alembic.aspect();
            if (aspect==null || alembic.amount()<=0 || alembic.amount()>=alembic.capacity()) continue;
            String tag=runtime.accessor(TcAspectAccessors.Aspect.class,aspect).tag();
            if (buffer.snapshot().getOrDefault(tag,0)>0 && alembic.add(aspect,1)==0) {
                buffer.takeOne(tag); filled.add(tag); setChanged(); changed(world,entry.getKey());
            }
        }
        // An empty labelled alembic takes only its filter. A new unlabelled one chooses a remaining type.
        for (var entry:alembics) {
            var alembic=entry.getValue(); if (alembic.amount()>0) continue;
            var filter=alembic.filter();
            for (String tag:new TreeSet<>(buffer.snapshot().keySet())) {
                var aspect=lookup.find(tag);
                if (aspect==null || (filter!=null && !runtime.values().same(filter,aspect)) || (filter==null && filled.contains(tag))) continue;
                if (alembic.add(aspect,1)==0) {
                    buffer.takeOne(tag); setChanged(); changed(world,entry.getKey()); return;
                }
            }
        }
    }
}
