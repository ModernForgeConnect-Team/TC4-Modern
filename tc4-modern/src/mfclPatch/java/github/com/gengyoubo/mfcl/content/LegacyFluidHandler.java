package github.com.gengyoubo.mfcl.content;

import github.com.gengyoubo.mfcl.bridge.LegacyFluidAccess;
import net.minecraft.core.Direction;
import net.minecraft.world.level.material.*;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.List;
import java.util.concurrent.Callable;

/** Modern fluid capability backed directly by the original IFluidHandler tanks and host transaction. */
final class LegacyFluidHandler implements IFluidHandler {
    private final LegacyBlockEntity tile;
    private final Integer side;
    private boolean reported;
    LegacyFluidHandler(LegacyBlockEntity tile,Direction side){this.tile=tile;this.side=side==null?null:side.get3DDataValue();}
    private boolean live() {
        var level=tile.getLevel();
        return level!=null&&!tile.isRemoved()&&level.hasChunkAt(tile.getBlockPos())&&level.getBlockEntity(tile.getBlockPos())==tile;
    }
    private <T> T call(Callable<T> action,T rejected) {
        if(tile.getLevel()==null||tile.isRemoved())return rejected;
        try {
            tile.content().services().worlds().bound(tile.getLevel());
            if(!live())return rejected;
            return action.call();
        }catch(Exception failure){
            if(!reported){reported=true;com.mojang.logging.LogUtils.getLogger().warn("MFCL rejected fluid capability operation at {}",tile.getBlockPos(),failure);}
            return rejected;
        }
    }
    private LegacyFluidAccess access(){return tile.content().services().fluids();}
    private List<LegacyFluidAccess.Tank> tanks(){return call(()->access().tanks(tile.legacyOrNull(),side),List.of());}
    /** Old fluid names follow the 1.7.10 registry: water/lava are vanilla, other names match a host source fluid's path. */
    static Fluid host(String legacy) {
        if(legacy==null)return null;
        if(legacy.equals("water"))return Fluids.WATER;
        if(legacy.equals("lava"))return Fluids.LAVA;
        var content=ModernContentBridge.active();
        if(content!=null){var mapped=content.fluidRegistry().host(legacy);if(mapped!=null)return mapped;}
        Fluid match=null;
        for(var entry:ForgeRegistries.FLUIDS.getEntries()) {
            var fluid=entry.getValue();
            if(!entry.getKey().location().getPath().equals(legacy)||fluid==Fluids.EMPTY||!fluid.isSource(fluid.defaultFluidState()))continue;
            if(match!=null)return null; // Ambiguous between namespaces.
            match=fluid;
        }
        return match;
    }
    static String legacy(Fluid fluid) {
        if(fluid==null||fluid==Fluids.EMPTY)return null;
        if(fluid.isSame(Fluids.WATER))return "water";
        if(fluid.isSame(Fluids.LAVA))return "lava";
        var content=ModernContentBridge.active();
        if(content!=null){var mapped=content.fluidRegistry().legacy(fluid);if(mapped!=null)return mapped;}
        var key=ForgeRegistries.FLUIDS.getKey(fluid);
        return key==null||!fluid.isSource(fluid.defaultFluidState())?null:key.getPath();
    }
    private static FluidStack stack(LegacyFluidAccess.Tank tank) {
        if(tank==null||tank.fluid()==null||tank.tagged()||tank.amount()<=0)return FluidStack.EMPTY;
        var fluid=host(tank.fluid());
        return fluid==null?FluidStack.EMPTY:new FluidStack(fluid,tank.amount());
    }
    @Override public int getTanks(){return tanks().size();}
    @Override public FluidStack getFluidInTank(int index){var all=tanks();return index<0||index>=all.size()?FluidStack.EMPTY:stack(all.get(index));}
    @Override public int getTankCapacity(int index){var all=tanks();return index<0||index>=all.size()?0:all.get(index).capacity();}
    @Override public boolean isFluidValid(int index,FluidStack stack) {
        if(stack.isEmpty()||stack.hasTag())return false;
        String name=legacy(stack.getFluid());
        return name!=null&&call(()->access().registered(name)!=null,false);
    }
    @Override public int fill(FluidStack resource,FluidAction action) {
        if(resource.isEmpty()||resource.hasTag())return 0;
        String name=legacy(resource.getFluid());if(name==null)return 0;
        return transfer(()->access().fill(tile.legacyOrNull(),side,name,resource.getAmount(),true),
                ()->access().fill(tile.legacyOrNull(),side,name,resource.getAmount(),false),action,0);
    }
    @Override public FluidStack drain(FluidStack resource,FluidAction action) {
        if(resource.isEmpty()||resource.hasTag())return FluidStack.EMPTY;
        String name=legacy(resource.getFluid());if(name==null)return FluidStack.EMPTY;
        return drained(name,resource.getAmount(),action);
    }
    @Override public FluidStack drain(int amount,FluidAction action){return drained(null,amount,action);}
    private FluidStack drained(String name,int amount,FluidAction action) {
        if(amount<=0)return FluidStack.EMPTY;
        // Without a fluid named by the caller, only drain what the host can represent.
        if(name==null) {
            var preview=stack(call(()->access().drain(tile.legacyOrNull(),side,null,amount,true),null));
            if(preview.isEmpty())return FluidStack.EMPTY;
            name=legacy(preview.getFluid());
        }
        String fluid=name;
        return transfer(()->stack(access().drain(tile.legacyOrNull(),side,fluid,amount,true)),
                ()->stack(access().drain(tile.legacyOrNull(),side,fluid,amount,false)),action,FluidStack.EMPTY);
    }
    private <T> T transfer(Callable<T> simulate,Callable<T> execute,FluidAction action,T rejected) {
        return call(()->{
            if(tile.getLevel().isClientSide)return rejected;
            if(action.simulate())return simulate.call();
            var content=tile.content();
            return content.logicTransaction(tile.getLevel(),tile.getBlockPos(),execute,false);
        },rejected);
    }
}
