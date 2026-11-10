package github.com.gengyoubo.mfcl.bridge;

import java.util.*;

/** Original Forge 1.7.10 IFluidHandler tanks, addressed by old fluid registry names. No independent fluid buffer. */
public final class LegacyFluidAccess {
    /** {@code fluid} is null for an empty tank; {@code tagged} tanks hold NBT-bearing fluid that is not bridged. */
    public record Tank(String fluid,int amount,int capacity,boolean tagged) {}
    private final LegacyServices services;
    private Class<?> handler,direction,fluidType,stackType,registry;
    private boolean checked;
    public LegacyFluidAccess(LegacyServices services){this.services=services;}
    public synchronized boolean available() {
        if(!checked) {
            try {
                var loader=services.loader();
                handler=loader.loadClass("net.minecraftforge.fluids.IFluidHandler");direction=loader.loadClass("net.minecraftforge.common.util.ForgeDirection");
                fluidType=loader.loadClass("net.minecraftforge.fluids.Fluid");stackType=loader.loadClass("net.minecraftforge.fluids.FluidStack");
                registry=loader.loadClass("net.minecraftforge.fluids.FluidRegistry");
            }catch(ClassNotFoundException absent){handler=null;}
            checked=true;
        }
        return handler!=null;
    }
    /** Frozen catalogue of old fluids and fluid blocks; block metadata stays owned by the old implementation. */
    public record FluidBlock(String key,boolean finite,int quanta,int densityDirection,boolean liquidMaterial) {}
    public record Definition(String name,String description,int density,int viscosity,int temperature,int luminosity,boolean gaseous,int color,String icon,LegacyClientData.Stack bucket,List<FluidBlock> blocks) {}
    public List<Definition> catalog() throws Exception {
        if(!available())return List.of();
        return services.items().inLegacy(()->{
            var loader=services.loader();var base=loader.loadClass("net.minecraftforge.fluids.BlockFluidBase");
            var finite=loader.loadClass("net.minecraftforge.fluids.BlockFluidFinite");
            var oldBlock=loader.loadClass("net.minecraft.block.Block");Object blocks=oldBlock.getField("field_149771_c").get(null);
            var keyOf=blocks.getClass().getMethod("func_148750_c",Object.class);
            var found=new LinkedHashMap<String,Object>((Map<String,Object>)registry.getMethod("getRegisteredFluids").invoke(null));
            var byName=new HashMap<String,List<FluidBlock>>();
            var quanta=base.getDeclaredField("quantaPerBlock");quanta.setAccessible(true);
            var directionField=base.getDeclaredField("densityDir");directionField.setAccessible(true);
            for(Object block:(Iterable<?>)blocks){
                if(!base.isInstance(block))continue;
                String key=(String)keyOf.invoke(blocks,block);if(key==null||key.startsWith("minecraft:"))continue;
                Object fluid=base.getMethod("getFluid").invoke(block);if(fluid==null)continue;
                String name=(String)fluidType.getMethod("getName").invoke(fluid);
                Object existing=found.putIfAbsent(name,fluid);
                if(existing!=null&&existing!=fluid)throw new IllegalStateException("Conflicting original fluid identities: "+name);
                Object material=oldBlock.getMethod("func_149688_o").invoke(block);
                boolean liquid=(boolean)loader.loadClass("net.minecraft.block.material.Material").getMethod("func_76224_d").invoke(material);
                byName.computeIfAbsent(name,ignored->new ArrayList<>()).add(new FluidBlock(key,finite.isInstance(block),quanta.getInt(block),directionField.getInt(block),liquid));
            }
            var bucketItems=new HashMap<String,LegacyClientData.Stack>();
            Class<?> containers=loader.loadClass("net.minecraftforge.fluids.FluidContainerRegistry");
            Object emptyBucket=containers.getField("EMPTY_BUCKET").get(null);
            Object bucketItem=stackTypeForItems(loader).getMethod("func_77973_b").invoke(emptyBucket);
            for(Object container:(Object[])containers.getMethod("getRegisteredFluidContainerData").invoke(null)){
                var ct=container.getClass();Object oldFluid=ct.getField("fluid").get(container),empty=ct.getField("emptyContainer").get(container),filled=ct.getField("filledContainer").get(container);
                if(oldFluid==null||empty==null||filled==null||stackType.getField("amount").getInt(oldFluid)!=1000||stackTypeForItems(loader).getMethod("func_77973_b").invoke(empty)!=bucketItem)continue;
                String name=name(oldFluid);if(name!=null)bucketItems.putIfAbsent(name,services.items().snapshot(filled));
            }
            var definitions=new ArrayList<Definition>();
            for(var entry:found.entrySet()){
                String name=entry.getKey();if(name.equals("water")||name.equals("lava"))continue;
                Object fluid=entry.getValue(),icon=fluidType.getMethod("getStillIcon").invoke(fluid);String iconName=null;
                if(icon!=null)iconName=(String)loader.loadClass("net.minecraft.util.IIcon").getMethod("func_94215_i").invoke(icon);
                definitions.add(new Definition(name,(String)fluidType.getMethod("getUnlocalizedName").invoke(fluid),(int)fluidType.getMethod("getDensity").invoke(fluid),(int)fluidType.getMethod("getViscosity").invoke(fluid),
                        (int)fluidType.getMethod("getTemperature").invoke(fluid),(int)fluidType.getMethod("getLuminosity").invoke(fluid),(boolean)fluidType.getMethod("isGaseous").invoke(fluid),
                        (int)fluidType.getMethod("getColor").invoke(fluid),iconName,bucketItems.get(name),List.copyOf(byName.getOrDefault(name,List.of()))));
            }
            definitions.sort(Comparator.comparing(Definition::name));return List.copyOf(definitions);
        });
    }
    private static Class<?> stackTypeForItems(ClassLoader loader) throws ClassNotFoundException {return loader.loadClass("net.minecraft.item.ItemStack");}
    public Set<String> containerKeys() throws Exception {
        if(!available())return Set.of();
        return services.items().inLegacy(()->{
            var result=new HashSet<String>();var type=services.loader().loadClass("net.minecraftforge.fluids.FluidContainerRegistry");
            for(Object data:(Object[])type.getMethod("getRegisteredFluidContainerData").invoke(null))for(String field:List.of("filledContainer","emptyContainer")){
                Object stack=data.getClass().getField(field).get(data);if(stack!=null){var snapshot=services.items().snapshot(stack);if(snapshot!=null)result.add(snapshot.key());}
            }
            return Set.copyOf(result);
        });
    }
    public record Container(String fluid,int amount,int capacity,boolean tagged) {}
    public Container container(LegacyClientData.Stack value) throws Exception {
        if(value==null||!available())return null;
        return services.items().inLegacy(()->{
            var itemStack=stackTypeForItems(services.loader());var type=services.loader().loadClass("net.minecraftforge.fluids.FluidContainerRegistry");
            Object stack=services.items().materialize(value);
            if(!(boolean)type.getMethod("isContainer",itemStack).invoke(null,stack))return null;
            Object fluid=type.getMethod("getFluidForFilledItem",itemStack).invoke(null,stack);
            int capacity=(int)type.getMethod("getContainerCapacity",itemStack).invoke(null,stack);
            return fluid==null?new Container(null,0,Math.max(0,capacity),false):new Container(name(fluid),stackType.getField("amount").getInt(fluid),Math.max(0,capacity),stackType.getField("tag").get(fluid)!=null);
        });
    }
    public LegacyClientData.Stack fillContainer(LegacyClientData.Stack value,String fluid,int amount) throws Exception {
        if(value==null||fluid==null||amount<=0||!available())return null;
        return services.items().inLegacy(()->{
            var itemStack=stackTypeForItems(services.loader());var type=services.loader().loadClass("net.minecraftforge.fluids.FluidContainerRegistry");
            Object oldFluid=stack(fluid,amount);if(oldFluid==null)return null;
            Object result=type.getMethod("fillFluidContainer",stackType,itemStack).invoke(null,oldFluid,services.items().materialize(value));
            return result==null?null:services.items().snapshot(result);
        });
    }
    public LegacyClientData.Stack drainContainer(LegacyClientData.Stack value) throws Exception {
        if(value==null||!available())return null;
        return services.items().inLegacy(()->{
            var itemStack=stackTypeForItems(services.loader());var type=services.loader().loadClass("net.minecraftforge.fluids.FluidContainerRegistry");
            Object result=type.getMethod("drainFluidContainer",itemStack).invoke(null,services.items().materialize(value));
            return result==null?null:services.items().snapshot(result);
        });
    }
    public boolean supports(Object tile){return tile!=null&&available()&&handler.isInstance(tile);}
    private Object side(Integer side) {
        int value=side==null?6:side;if(value<0||value>6)throw new IllegalArgumentException("Invalid fluid side: "+value);
        return direction.getEnumConstants()[value];
    }
    private boolean live(Object tile) throws Exception {
        var type=services.loader().loadClass("net.minecraft.tileentity.TileEntity");
        if(!type.isInstance(tile)||(boolean)type.getMethod("func_145837_r").invoke(tile))return false;
        return LegacyWorlds.hostOrNull(type.getMethod("func_145831_w").invoke(tile))!=null;
    }
    private boolean server(Object tile) throws Exception {
        var host=LegacyWorlds.hostOrNull(services.loader().loadClass("net.minecraft.tileentity.TileEntity").getMethod("func_145831_w").invoke(tile));
        return host!=null&&!host.clientSide();
    }
    /** Old registry name of a registered fluid, or null when this runtime has no such fluid. */
    public String registered(String name) throws Exception {
        if(name==null||!available())return null;
        return services.items().inLegacy(()->registry.getMethod("getFluid",String.class).invoke(null,name)==null?null:name);
    }
    public List<Tank> tanks(Object tile,Integer side) throws Exception {
        if(!supports(tile))return List.of();
        return services.items().inLegacy(()->{
            if(!live(tile))return List.<Tank>of();
            Object[] info=(Object[])handler.getMethod("getTankInfo",direction).invoke(tile,side(side));
            if(info==null)return List.<Tank>of();
            var result=new ArrayList<Tank>();
            for(Object tank:info) {
                if(tank==null)continue;
                int capacity=tank.getClass().getField("capacity").getInt(tank);
                Object stack=tank.getClass().getField("fluid").get(tank);
                if(stack==null){result.add(new Tank(null,0,Math.max(0,capacity),false));continue;}
                int amount=stackType.getField("amount").getInt(stack);
                result.add(new Tank(amount<=0?null:name(stack),Math.max(0,amount),Math.max(0,capacity),stackType.getField("tag").get(stack)!=null));
            }
            return List.copyOf(result);
        });
    }
    private String name(Object stack) throws Exception {
        Object fluid=stackType.getMethod("getFluid").invoke(stack);
        return fluid==null?null:(String)fluidType.getMethod("getName").invoke(fluid);
    }
    private Object stack(String name,int amount) throws Exception {
        Object fluid=registry.getMethod("getFluid",String.class).invoke(null,name);
        return fluid==null?null:stackType.getConstructor(fluidType,int.class).newInstance(fluid,amount);
    }
    public int fill(Object tile,Integer side,String fluid,int amount,boolean simulate) throws Exception {
        if(amount<=0||fluid==null||!supports(tile))return 0;
        return services.items().inLegacy(()->{
            if(!live(tile)||!server(tile))return 0;
            Object stack=stack(fluid,amount);if(stack==null)return 0;
            if(!(boolean)handler.getMethod("canFill",direction,fluidType).invoke(tile,side(side),stackType.getMethod("getFluid").invoke(stack)))return 0;
            return LegacyEnergyAccess.transferred((int)handler.getMethod("fill",direction,stackType,boolean.class).invoke(tile,side(side),stack,!simulate),amount);
        });
    }
    /** Drains up to {@code amount}; {@code fluid} null drains whatever the original handler offers. Returns null when nothing moved. */
    public Tank drain(Object tile,Integer side,String fluid,int amount,boolean simulate) throws Exception {
        if(amount<=0||!supports(tile))return null;
        return services.items().inLegacy(()->{
            if(!live(tile)||!server(tile))return null;
            Object drained;
            if(fluid==null) {
                Object preview=handler.getMethod("drain",direction,int.class,boolean.class).invoke(tile,side(side),amount,false);
                if(preview==null||stackType.getField("tag").get(preview)!=null||name(preview)==null)return null;
                if(!(boolean)handler.getMethod("canDrain",direction,fluidType).invoke(tile,side(side),stackType.getMethod("getFluid").invoke(preview)))return null;
                drained=simulate?preview:handler.getMethod("drain",direction,int.class,boolean.class).invoke(tile,side(side),amount,true);
            } else {
                Object request=stack(fluid,amount);if(request==null)return null;
                if(!(boolean)handler.getMethod("canDrain",direction,fluidType).invoke(tile,side(side),stackType.getMethod("getFluid").invoke(request)))return null;
                drained=handler.getMethod("drain",direction,stackType,boolean.class).invoke(tile,side(side),request,!simulate);
            }
            if(drained==null)return null;
            int moved=LegacyEnergyAccess.transferred(stackType.getField("amount").getInt(drained),amount);
            String name=name(drained);
            if(moved==0||name==null)return null;
            if(fluid!=null&&!fluid.equals(name))throw new IllegalStateException("Original drain returned "+name+" for request "+fluid);
            if(stackType.getField("tag").get(drained)!=null) {
                if(simulate)return null;
                throw new IllegalStateException("Original drain returned NBT-bearing "+name);
            }
            return new Tank(name,moved,0,false);
        });
    }
}
