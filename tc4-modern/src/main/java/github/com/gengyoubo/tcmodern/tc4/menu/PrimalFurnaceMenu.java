package github.com.gengyoubo.tcmodern.tc4.menu;

import github.com.gengyoubo.tcmodern.api.TcModernApi;
import github.com.gengyoubo.tcmodern.api.aspect.*;
import github.com.gengyoubo.tcmodern.tc4.content.PrimalFurnaceBlockEntity;
import github.com.gengyoubo.tcmodern.tc4.init.Tc4Menus;
import java.util.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;

/** Synchronizes fuel, processing and primal output without granting access to the machine's source data. */
public final class PrimalFurnaceMenu extends AbstractContainerMenu {
    public static final int WIDTH=184,HEIGHT=216,INPUT_X=20,INPUT_Y=34,FUEL_X=20,FUEL_Y=68,INVENTORY_X=12,INVENTORY_Y=134,HOTBAR_Y=192;
    private static final int DISPLAY_ASPECTS=12;
    private final PrimalFurnaceBlockEntity furnace;
    private final List<TcAspect> aspects;
    private final DataSlot burn=DataSlot.standalone(),cook=DataSlot.standalone();
    private final DataSlot[] ids=new DataSlot[DISPLAY_ASPECTS],amounts=new DataSlot[DISPLAY_ASPECTS];
    public PrimalFurnaceMenu(int id,Inventory inventory,FriendlyByteBuf data) {
        this(id,inventory,inventory.player.level().getBlockEntity(data.readBlockPos()) instanceof PrimalFurnaceBlockEntity found?found:null);
    }
    public PrimalFurnaceMenu(int id,Inventory inventory,PrimalFurnaceBlockEntity furnace) {
        super(Tc4Menus.PRIMAL_FURNACE.get(),id); this.furnace=furnace;
        aspects=TcModernApi.runtime().map(TcAspects::snapshot).orElse(List.of()).stream().filter(TcAspect::primal).sorted(Comparator.comparing(TcAspect::tag)).toList();
        var items=furnace==null?new ItemStackHandler(2):furnace.items();
        addSlot(new SlotItemHandler(items,0,INPUT_X,INPUT_Y));
        addSlot(new SlotItemHandler(items,1,FUEL_X,FUEL_Y) {
            @Override public boolean mayPlace(ItemStack stack) { return PrimalFurnaceBlockEntity.fuelTime(stack)>0; }
        });
        for(int row=0;row<3;row++) for(int col=0;col<9;col++) addSlot(new Slot(inventory,9+col+row*9,INVENTORY_X+col*18,INVENTORY_Y+row*18));
        for(int col=0;col<9;col++) addSlot(new Slot(inventory,col,INVENTORY_X+col*18,HOTBAR_Y));
        addDataSlot(burn); addDataSlot(cook);
        for(int i=0;i<DISPLAY_ASPECTS;i++) { ids[i]=DataSlot.standalone(); amounts[i]=DataSlot.standalone(); addDataSlot(ids[i]); addDataSlot(amounts[i]); }
    }
    public record Result(TcAspect aspect,int amount) { }
    public int burnFraction() { return burn.get(); }
    public int cookFraction() { return cook.get(); }
    public List<Result> results() {
        var result=new ArrayList<Result>();
        for(int i=0;i<DISPLAY_ASPECTS;i++) { int index=ids[i].get()-1,n=amounts[i].get(); if(index>=0 && index<aspects.size() && n>0) result.add(new Result(aspects.get(index),n)); }
        return result;
    }
    @Override public void broadcastChanges() {
        if(furnace!=null && furnace.getLevel()!=null && !furnace.getLevel().isClientSide) {
            burn.set(furnace.burnFraction()); cook.set(furnace.cookFraction());
            var contents=furnace.contents(); int slot=0;
            for(int i=0;i<aspects.size() && slot<DISPLAY_ASPECTS;i++) {
                int n=contents.getOrDefault(aspects.get(i).tag(),0);
                if(n>0) { ids[slot].set(i+1); amounts[slot].set(n); slot++; }
            }
            while(slot<DISPLAY_ASPECTS) { ids[slot].set(0); amounts[slot].set(0); slot++; }
        }
        super.broadcastChanges();
    }
    @Override public boolean stillValid(Player player) {
        return furnace!=null && !furnace.isRemoved() && player.level().getBlockEntity(furnace.getBlockPos())==furnace && player.distanceToSqr(furnace.getBlockPos().getCenter())<=64;
    }
    @Override public ItemStack quickMoveStack(Player player,int index) {
        if(index<0 || index>=slots.size()) return ItemStack.EMPTY;
        var slot=slots.get(index); if(!slot.hasItem()) return ItemStack.EMPTY;
        var stack=slot.getItem(); var copy=stack.copy();
        if(index<2) { if(!moveItemStackTo(stack,2,slots.size(),true)) return ItemStack.EMPTY; }
        else if(PrimalFurnaceBlockEntity.fuelTime(stack)>0) {
            if(!moveItemStackTo(stack,1,2,false)) return ItemStack.EMPTY;
        } else if(!moveItemStackTo(stack,0,1,false)) return ItemStack.EMPTY;
        if(stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged(); return copy;
    }
}
