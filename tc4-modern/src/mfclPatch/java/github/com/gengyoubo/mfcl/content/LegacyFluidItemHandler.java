package github.com.gengyoubo.mfcl.content;

import github.com.gengyoubo.mfcl.bridge.LegacyClientData;
import github.com.gengyoubo.mfcl.bridge.LegacyFluidAccess;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Registered old Forge fluid containers expose their original full-container exchange to modern tanks. */
final class LegacyFluidItemHandler implements IFluidHandlerItem,ICapabilityProvider {
    private final ModernContentBridge content;
    private final LazyOptional<IFluidHandlerItem> capability=LazyOptional.of(()->this);
    private ItemStack container;
    LegacyFluidItemHandler(ModernContentBridge content,ItemStack stack){this.content=content;this.container=stack;}
    @NotNull @Override public <T> LazyOptional<T> getCapability(@NotNull Capability<T> capability,@Nullable Direction side){return capability==ForgeCapabilities.FLUID_HANDLER_ITEM?this.capability.cast():LazyOptional.empty();}
    @Override public ItemStack getContainer(){return container;}
    private LegacyClientData.Stack snapshot() throws Exception{return content.gameplayStack(container);}
    private LegacyFluidAccess.Container info(){try{return content.services().fluids().container(snapshot());}catch(Exception failure){return null;}}
    private FluidStack fluid(LegacyFluidAccess.Container info){
        if(info==null||info.fluid()==null||info.tagged()||info.amount()<=0)return FluidStack.EMPTY;
        var type=LegacyFluidHandler.host(info.fluid());return type==null?FluidStack.EMPTY:new FluidStack(type,info.amount());
    }
    @Override public int getTanks(){return 1;}
    @Override public FluidStack getFluidInTank(int tank){return tank==0?fluid(info()):FluidStack.EMPTY;}
    @Override public int getTankCapacity(int tank){var value=info();return tank==0&&value!=null?value.capacity():0;}
    @Override public boolean isFluidValid(int tank,FluidStack stack){
        if(tank!=0||stack.isEmpty()||stack.hasTag())return false;
        var probe=stack.copy();probe.setAmount(Integer.MAX_VALUE);return candidate(probe)!=null;
    }
    private LegacyClientData.Stack candidate(FluidStack stack){
        if(container.getCount()!=1||stack.isEmpty()||stack.hasTag())return null;
        var name=LegacyFluidHandler.legacy(stack.getFluid());if(name==null)return null;
        try{
            var result=content.services().fluids().fillContainer(snapshot(),name,stack.getAmount());
            var target=result==null?null:content.services().fluids().container(result);
            return target!=null&&!target.tagged()&&name.equals(target.fluid())&&target.amount()>0&&target.amount()<=stack.getAmount()?result:null;
        }catch(Exception failure){return null;}
    }
    @Override public int fill(FluidStack resource,FluidAction action){
        var target=candidate(resource);if(target==null)return 0;
        try {var value=content.services().fluids().container(target);if(action.execute())container=content.gameplayStack(target);return value.amount();}
        catch(Exception failure){return 0;}
    }
    @Override public FluidStack drain(FluidStack resource,FluidAction action){
        if(resource.isEmpty()||resource.hasTag())return FluidStack.EMPTY;
        var present=getFluidInTank(0);if(present.isEmpty()||!present.isFluidEqual(resource)||resource.getAmount()<present.getAmount())return FluidStack.EMPTY;
        return drain(present.getAmount(),action);
    }
    @Override public FluidStack drain(int amount,FluidAction action){
        if(container.getCount()!=1)return FluidStack.EMPTY;var present=getFluidInTank(0);
        if(present.isEmpty()||amount<present.getAmount())return FluidStack.EMPTY;
        try {var empty=content.services().fluids().drainContainer(snapshot());if(action.execute())container=empty==null?ItemStack.EMPTY:content.gameplayStack(empty);return present;}
        catch(Exception failure){return FluidStack.EMPTY;}
    }
}
