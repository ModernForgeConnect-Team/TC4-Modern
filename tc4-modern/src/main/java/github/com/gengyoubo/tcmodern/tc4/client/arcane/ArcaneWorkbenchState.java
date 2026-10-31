package github.com.gengyoubo.tcmodern.tc4.client.arcane;

import github.com.gengyoubo.mfcl.api.*;
import github.com.gengyoubo.tcmodern.api.aspect.TcAspectAccessors;
import net.minecraft.world.item.ItemStack;
import java.util.*;
import static github.com.gengyoubo.tcmodern.tc4.client.arcane.ArcaneWorkbenchAccessors.*;

/** Immutable display data for one tick. No borrowed legacy references survive the scoped read. */
record ArcaneWorkbenchState(List<Cost> costs,boolean insufficient,ItemStack ghost) {
    /** A primal at its original ring position; {@code ready}: the wand holds enough of it. */
    record Cost(int position,String tag,float amount,boolean ready) {}
    static final ArcaneWorkbenchState EMPTY=new ArcaneWorkbenchState(List.of(),false,ItemStack.EMPTY);
    /** The same queries GuiArcaneWorkbench makes while drawing, once per tick. */
    static ArcaneWorkbenchState read(LegacyClientScreens.Context context){
        var runtime=context.runtime();
        return context.withScreen(reference->{
            var gui=runtime.accessor(Gui.class,reference);var tile=gui.tile();
            if(tile==null)return EMPTY;
            var player=runtime.accessor(PlayerInventory.class,gui.inventory()).player();
            var crafting=runtime.accessor(Crafting.class);
            var result=crafting.result(tile,player);
            if(result==null)return EMPTY;
            var cost=crafting.cost(tile,player);
            var stack=runtime.accessor(Table.class,tile).stack(10);
            var wand=ArcaneWands.isWand(runtime,stack)?runtime.accessor(Wand.class,runtime.accessor(Holder.class,stack).item()):null;
            var amounts=runtime.accessor(TcAspectAccessors.Aspects.class,cost);
            var costs=new ArrayList<Cost>();int position=0;
            for(var value:runtime.values().list(runtime.accessor(Primals.class).primals())){
                var primal=(LegacyObject)value;int base=amounts.amount(primal);
                if(base>0){
                    float amount=base;boolean ready=false;
                    if(wand!=null){amount*=wand.modifier(stack,player,primal,true);ready=amount*100.0F<=wand.vis(stack,primal);}
                    costs.add(new Cost(position,runtime.accessor(TcAspectAccessors.Aspect.class,primal).tag(),amount,ready));
                }
                if(++position>5)break;
            }
            boolean insufficient=wand!=null&&!wand.covers(stack,player,cost,false);
            var ghost=insufficient?runtime.mappings().toHost(LegacyMappings.ITEM_STACK,result,ItemStack.class).orElse(ItemStack.EMPTY):ItemStack.EMPTY;
            return new ArcaneWorkbenchState(List.copyOf(costs),insufficient,ghost);
        });
    }
}
