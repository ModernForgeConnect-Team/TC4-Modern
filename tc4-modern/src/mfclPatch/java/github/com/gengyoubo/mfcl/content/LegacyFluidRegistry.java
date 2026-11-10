package github.com.gengyoubo.mfcl.content;

import github.com.gengyoubo.mfcl.bridge.LegacyFluidAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Stable names and native fluid states over the original blocks; old fluid ticks remain authoritative. */
final class LegacyFluidRegistry {
    private final ModernContentBridge content;
    private final List<LegacyFluidAccess.Definition> definitions;
    private final Set<String> containerKeys;
    private final Map<String,LegacyFluidAccess.FluidBlock> blocks=new HashMap<>();
    private final Map<String,String> fluidOfBlock=new HashMap<>();
    private final Map<String,Integer> blockVariants=new HashMap<>();
    private final Map<String,FluidType> types=new LinkedHashMap<>();
    private final Map<String,Fluid> fluids=new LinkedHashMap<>();
    private final Map<Fluid,String> reverse=new IdentityHashMap<>();
    LegacyFluidRegistry(ModernContentBridge content)throws Exception {
        this.content=content;definitions=content.services().fluids().catalog();containerKeys=content.services().fluids().containerKeys();
        for(var def:definitions)types.put(def.name(),new OriginalType(def));
        for(var def:definitions){
            if(def.blocks().size()>16)throw new IllegalStateException("Too many block forms for old fluid "+def.name());
            for(int index=0;index<def.blocks().size();index++){
            var block=def.blocks().get(index);blockVariants.put(block.key(),index);
            if(fluidOfBlock.putIfAbsent(block.key(),def.name())!=null)throw new IllegalStateException("Duplicate original fluid block "+block.key());
            blocks.put(block.key(),block);
        }}
    }
    private static ResourceLocation id(String name){return ResourceLocation.fromNamespaceAndPath("mfcl","legacy_fluid/"+HexFormat.of().formatHex(name.getBytes(StandardCharsets.UTF_8)));}
    void register(RegisterEvent event){
        event.register(ForgeRegistries.Keys.FLUID_TYPES,registry->{for(var def:definitions)registry.register(id(def.name()),types.get(def.name()));});
        event.register(Registries.FLUID,registry->{for(var def:definitions){var fluid=new OriginalFluid(def);registry.register(id(def.name()),fluid);fluids.put(def.name(),fluid);reverse.put(fluid,def.name());}});
    }
    boolean isContainerItem(String key){return containerKeys.contains(key);}
    Collection<Fluid> fluids(){return List.copyOf(fluids.values());}
    Fluid host(String name){return fluids.get(name);}
    String legacy(Fluid fluid){return reverse.get(fluid);}
    FluidState state(String key,int metadata){
        String name=fluidOfBlock.get(key);if(name==null)return Fluids.EMPTY.defaultFluidState();
        var block=blocks.get(key);var fluid=fluids.get(name);
        // Old gases and non-liquid materials such as flux goo must not acquire water movement or drowning.
        if(fluid==null||block.densityDirection()>0||!block.liquidMaterial())return Fluids.EMPTY.defaultFluidState();
        return fluid.defaultFluidState().setValue(OriginalFluid.META,metadata).setValue(OriginalFluid.VARIANT,blockVariants.get(key));
    }
    private boolean nonLiquid(LegacyFluidAccess.Definition def){return def.gaseous()||!def.blocks().isEmpty()&&def.blocks().stream().noneMatch(b->b.liquidMaterial()&&b.densityDirection()<=0);}
    private final class OriginalType extends FluidType {
        private final LegacyFluidAccess.Definition definition;
        OriginalType(LegacyFluidAccess.Definition definition){super(Properties.create().descriptionId(definition.description()).density(definition.density()).viscosity(definition.viscosity()).temperature(definition.temperature()).lightLevel(definition.luminosity()).canSwim(!nonLiquid(definition)).canDrown(!nonLiquid(definition)).canPushEntity(!nonLiquid(definition)).canExtinguish(!nonLiquid(definition)));this.definition=definition;}
        @Override public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions> consumer){
            consumer.accept(new net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions(){
                private ResourceLocation texture(){try{return ResourceLocation.parse(content.pack().worldTexture(definition.icon()));}catch(Exception absent){return ResourceLocation.parse("minecraft:block/water_still");}}
                @Override public ResourceLocation getStillTexture(){return texture();}
                @Override public ResourceLocation getFlowingTexture(){return texture();}
                @Override public int getTintColor(){return definition.color()|0xff000000;}
                // The mirrored block already draws the original liquid model; this suppresses the second native mesh only.
                @Override public ResourceLocation getStillTexture(FluidState state,net.minecraft.world.level.BlockAndTintGetter level,BlockPos pos){return ResourceLocation.parse("mfcl:block/legacy/fluid_transparent");}
                @Override public ResourceLocation getFlowingTexture(FluidState state,net.minecraft.world.level.BlockAndTintGetter level,BlockPos pos){return ResourceLocation.parse("mfcl:block/legacy/fluid_transparent");}
            });
        }
    }
    private final class OriginalFluid extends Fluid {
        static final IntegerProperty META=IntegerProperty.create("legacy_level",0,15),VARIANT=IntegerProperty.create("legacy_variant",0,15);
        private final LegacyFluidAccess.Definition definition;
        private final LegacyFluidAccess.FluidBlock primary;
        OriginalFluid(LegacyFluidAccess.Definition definition){this.definition=definition;this.primary=definition.blocks().isEmpty()?null:definition.blocks().get(0);registerDefaultState(stateDefinition.any().setValue(VARIANT,0).setValue(META,sourceMeta(primary)));}
        private int sourceMeta(LegacyFluidAccess.FluidBlock block){return block!=null&&block.finite()?Math.max(0,Math.min(15,block.quanta()-1)):0;}
        private LegacyFluidAccess.FluidBlock block(FluidState state){int index=state.getValue(VARIANT);return index<definition.blocks().size()?definition.blocks().get(index):primary;}
        @Override protected void createFluidStateDefinition(StateDefinition.Builder<Fluid,FluidState> builder){builder.add(META,VARIANT);}
        @Override public FluidType getFluidType(){return Objects.requireNonNull(types.get(definition.name()),"Unregistered old fluid type "+definition.name());}
        @Override public Item getBucket(){return definition.bucket()==null?Items.AIR:content.gameplayStack(definition.bucket()).getItem();}
        @Override protected boolean canBeReplacedWith(FluidState state,BlockGetter level,BlockPos pos,Fluid fluid,Direction direction){return !isSame(fluid);}
        @Override protected Vec3 getFlow(BlockGetter level,BlockPos pos,FluidState state){
            double x=0,z=0;float height=getOwnHeight(state);
            for(Direction side:Direction.Plane.HORIZONTAL){
                var next=pos.relative(side);var neighbour=level.getFluidState(next);float delta;
                if(isSame(neighbour.getType()))delta=height-neighbour.getOwnHeight();
                else if(neighbour.isEmpty()&&!level.getBlockState(next).blocksMotion()){
                    var below=level.getFluidState(next.below());if(!isSame(below.getType()))continue;
                    delta=height-below.getOwnHeight()+1;
                } else continue;
                x+=side.getStepX()*delta;z+=side.getStepZ()*delta;
            }
            return new Vec3(x,0,z).normalize();
        }
        @Override public int getTickDelay(LevelReader level){return 5;}
        @Override protected float getExplosionResistance(){return 100;}
        @Override public float getHeight(FluidState state,BlockGetter level,BlockPos pos){return isSame(level.getFluidState(pos.above()).getType())?1:getOwnHeight(state);}
        @Override public float getOwnHeight(FluidState state){return getAmount(state)/9f;}
        @Override protected BlockState createLegacyBlock(FluidState state){
            var original=block(state);if(original==null)return Blocks.AIR.defaultBlockState();var block=content.blocks().get(original.key());
            return block==null?Blocks.AIR.defaultBlockState():block.defaultBlockState().setValue(ModernContentBridge.METADATA,state.getValue(META));
        }
        @Override public boolean isSource(FluidState state){return state.getValue(META)==sourceMeta(block(state));}
        @Override public int getAmount(FluidState state){
            var original=block(state);if(original==null)return 8;int meta=state.getValue(META),q=Math.max(1,original.quanta());
            if(original.finite())return Math.max(1,Math.min(8,(int)Math.ceil((meta+1)*8.0/q)));
            return meta>=8?8:Math.max(1,Math.min(8,q-meta));
        }
        @Override public VoxelShape getShape(FluidState state,BlockGetter level,BlockPos pos){return Shapes.box(0,0,0,1,getHeight(state,level,pos),1);}
    }
}
