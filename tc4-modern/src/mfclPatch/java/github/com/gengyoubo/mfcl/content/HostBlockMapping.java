package github.com.gengyoubo.mfcl.content;

import github.com.gengyoubo.mfcl.bridge.*;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.*;
import java.util.*;

/** Explicit vanilla state flattening, including the decorations used by original portal arrival rooms. */
public final class HostBlockMapping {
    private final Map<Block,HostWorldAccess.Block> blocks=new IdentityHashMap<>();
    /** Exact inverse of the item-variant table (web -> cobweb, wool@3 -> light_blue_wool); aliases below are read-only. */
    private final Map<HostWorldAccess.Block,Block> variants=new HashMap<>();
    public HostBlockMapping(Set<String> oldKeys) {
        // Reuse the explicit old item variant table rather than maintaining a second list of wood/color names.
        for(int meta=0;meta<16;meta++)for(String key:oldKeys)if(key.startsWith("minecraft:")) {
            var stack=HostStackMapping.renderStack(new LegacyClientData.Stack(key,meta,1,"",""));
            if(stack.getItem() instanceof BlockItem item){var old=new HostWorldAccess.Block(key,meta);blocks.putIfAbsent(item.getBlock(),old);variants.putIfAbsent(old,item.getBlock());}
        }
        // Blocks without an item (air, fluids, crops...) can retain their registry identity.
        // Do not alias reused names: old snow is modern snow_block; old stone_stairs is cobblestone_stairs.
        for(String key:oldKeys)if(key.startsWith("minecraft:")&&HostStackMapping.renderStack(new LegacyClientData.Stack(key,0,1,"","")).isEmpty()) {
            var id=ResourceLocation.tryParse(key);
            if(BuiltInRegistries.BLOCK.containsKey(id))blocks.putIfAbsent(BuiltInRegistries.BLOCK.get(id),new HostWorldAccess.Block(key,0));
        }
        Map<String,String> blockOnly=Map.ofEntries(
                Map.entry("oak_sign","standing_sign"),Map.entry("oak_wall_sign","wall_sign"),
                Map.entry("redstone_wall_torch","redstone_torch"),Map.entry("wall_torch","torch"),
                Map.entry("water_cauldron","cauldron"),
                Map.entry("spawner","mob_spawner"),
                // The old wheat crop shares its name with an item that is no BlockItem, so neither rule above maps it.
                Map.entry("wheat","wheat"),Map.entry("carrots","carrots"),Map.entry("potatoes","potatoes"),
                Map.entry("nether_portal","portal"),Map.entry("tripwire","tripwire"),Map.entry("dead_bush","deadbush"),
                // Post-1.7 variants that are the same thing to old logic (crucible heat, bellows and spill neighbour reads).
                Map.entry("cave_air","air"),Map.entry("void_air","air"),Map.entry("soul_fire","fire"));
        blockOnly.forEach((modern,old)->{
            if(oldKeys.contains("minecraft:"+old))blocks.put(BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("minecraft",modern)),new HostWorldAccess.Block("minecraft:"+old,0));
        });
    }
    public HostWorldAccess.Block read(BlockState state) {
        var mapped=blocks.get(state.getBlock());
        // Modded bookshelves read as the old bookshelf (TC4's wand trigger, enchanting power), so old logic treats them alike.
        if(mapped==null&&state.is(net.minecraftforge.common.Tags.Blocks.BOOKSHELVES))mapped=blocks.get(Blocks.BOOKSHELF);
        if(mapped==null){github.com.gengyoubo.mfcl.bridge.LegacyDiagnostics.missingMapping("block "+net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()),"host block has no original representation");throw new UnmappedLegacyBlockException("No old Block representation: "+state);}
        String key=mapped.legacyKey();int meta=mapped.metadata();
        // Connection, shape and waterlogged properties do not change the original block/item variant.
        // Old fences/panes/stairs compute their connections from neighboring world cells.
        if(state.getBlock() instanceof TorchBlock) {
            meta=state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)?switch(state.getValue(BlockStateProperties.HORIZONTAL_FACING)){case EAST->1;case WEST->2;case SOUTH->3;default->4;}:5;
            if(state.getBlock() instanceof RedstoneTorchBlock&&!state.getValue(BlockStateProperties.LIT))key="minecraft:unlit_redstone_torch";
        } else if(state.is(Blocks.WATER_CAULDRON)) {
            key="minecraft:cauldron";meta=state.getValue(LayeredCauldronBlock.LEVEL);
        } else if(state.getBlock() instanceof RotatedPillarBlock&&state.hasProperty(BlockStateProperties.AXIS)) {
            var axis=state.getValue(BlockStateProperties.AXIS);
            if(key.equals("minecraft:log")||key.equals("minecraft:log2"))meta|=axis==Direction.Axis.X?4:axis==Direction.Axis.Z?8:0;
            else if(key.equals("minecraft:quartz_block")&&meta==2)meta=axis==Direction.Axis.X?3:axis==Direction.Axis.Z?4:2;
            else if(key.equals("minecraft:hay_block"))meta=axis==Direction.Axis.X?4:axis==Direction.Axis.Z?8:0;
        }
        if(state.getBlock() instanceof SlabBlock) {
            var slab=state.getValue(BlockStateProperties.SLAB_TYPE);
            if(slab==SlabType.DOUBLE)key=key.equals("minecraft:wooden_slab")?"minecraft:double_wooden_slab":"minecraft:double_stone_slab";
            else if(slab==SlabType.TOP)meta|=8;
        } else if(state.getBlock() instanceof StairBlock) {
            meta=switch(state.getValue(BlockStateProperties.HORIZONTAL_FACING)){case EAST->0;case WEST->1;case SOUTH->2;default->3;};
            if(state.getValue(BlockStateProperties.HALF)==Half.TOP)meta|=4;
        } else if(state.getBlock() instanceof FenceGateBlock) {
            meta=horizontal(state);if(state.getValue(BlockStateProperties.OPEN))meta|=4;
        } else if(state.getBlock() instanceof LeavesBlock) {
            if(state.getValue(BlockStateProperties.PERSISTENT))meta|=4;
        } else if(state.getBlock() instanceof LiquidBlock)meta=state.getValue(LiquidBlock.LEVEL);
        // Old growth state is metadata: original crop logic (CropUtils.isGrownCrop, bone meal) tells ripeness from it.
        else if(state.getBlock() instanceof CropBlock crop)meta=crop.getAge(state);
        else if(state.is(Blocks.NETHER_WART))meta=state.getValue(BlockStateProperties.AGE_3);
        else if(state.is(Blocks.COCOA))meta=horizontal(state)|state.getValue(BlockStateProperties.AGE_2)<<2;
        return new HostWorldAccess.Block(key,meta);
    }
    /** Null leaves unsupported states to the caller's explicit mapping error. */
    public BlockState write(HostWorldAccess.Block old) {
        String key=old.legacyKey();int meta=old.metadata();
        if(key.equals("minecraft:leaves")||key.equals("minecraft:leaves2")) {
            if(meta<0||meta>15)return null;
            var leaf=variants.get(new HostWorldAccess.Block(key,meta&3));
            return leaf instanceof LeavesBlock ? leaf.defaultBlockState().setValue(BlockStateProperties.PERSISTENT,(meta&4)!=0) : null;
        }
        if(key.equals("minecraft:fence")&&meta==0)return Blocks.OAK_FENCE.defaultBlockState();
        if(key.equals("minecraft:mob_spawner")&&meta==0)return Blocks.SPAWNER.defaultBlockState();
        if(key.equals("minecraft:water")||key.equals("minecraft:flowing_water")||key.equals("minecraft:lava")||key.equals("minecraft:flowing_lava")) {
            if(meta<0||meta>15)return null;
            return (key.endsWith("water")?Blocks.WATER:Blocks.LAVA).defaultBlockState().setValue(LiquidBlock.LEVEL,meta);
        }
        if(key.equals("minecraft:torch")||key.equals("minecraft:redstone_torch")||key.equals("minecraft:unlit_redstone_torch")) {
            if(meta<0||meta>5)return null;
            boolean redstone=!key.equals("minecraft:torch"),wall=meta>=1&&meta<=4;
            BlockState state=(redstone?(wall?Blocks.REDSTONE_WALL_TORCH:Blocks.REDSTONE_TORCH):(wall?Blocks.WALL_TORCH:Blocks.TORCH)).defaultBlockState();
            if(wall)state=state.setValue(BlockStateProperties.HORIZONTAL_FACING,switch(meta){case 1->Direction.EAST;case 2->Direction.WEST;case 3->Direction.SOUTH;default->Direction.NORTH;});
            return redstone?state.setValue(BlockStateProperties.LIT,!key.equals("minecraft:unlit_redstone_torch")):state;
        }
        if(key.equals("minecraft:wheat")||key.equals("minecraft:carrots")||key.equals("minecraft:potatoes")) {
            if(meta<0||meta>7)return null;
            var crop=key.equals("minecraft:wheat")?Blocks.WHEAT:key.equals("minecraft:carrots")?Blocks.CARROTS:Blocks.POTATOES;
            return crop.defaultBlockState().setValue(BlockStateProperties.AGE_7,meta);
        }
        if(key.equals("minecraft:nether_wart"))return meta>=0&&meta<=3?Blocks.NETHER_WART.defaultBlockState().setValue(BlockStateProperties.AGE_3,meta):null;
        if(key.equals("minecraft:cocoa")) {
            if(meta<0||meta>>2>2)return null;
            return Blocks.COCOA.defaultBlockState().setValue(BlockStateProperties.AGE_2,meta>>2).setValue(BlockStateProperties.HORIZONTAL_FACING,switch(meta&3){case 0->Direction.SOUTH;case 1->Direction.WEST;case 2->Direction.NORTH;default->Direction.EAST;});
        }
        if(key.equals("minecraft:cauldron"))return meta==0?Blocks.CAULDRON.defaultBlockState():meta>=1&&meta<=3?Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL,meta):null;
        if((key.equals("minecraft:stonebrick")&&meta>=0&&meta<=3)||(key.equals("minecraft:stone_slab")&&meta>=0&&meta<=15)) {
            int variant=key.equals("minecraft:stone_slab")?meta&7:meta;
            var stack=HostStackMapping.renderStack(new LegacyClientData.Stack(key,variant,1,"",""));
            if(stack.getItem() instanceof BlockItem item){
                var state=item.getBlock().defaultBlockState();
                return state.getBlock() instanceof SlabBlock?state.setValue(BlockStateProperties.SLAB_TYPE,(meta&8)!=0?SlabType.TOP:SlabType.BOTTOM):state;
            }
        }
        var renamed=variants.get(new HostWorldAccess.Block(key,meta));
        return renamed==null?null:renamed.defaultBlockState();
    }
    private static int horizontal(BlockState state) {
        return switch(state.getValue(BlockStateProperties.HORIZONTAL_FACING)){case SOUTH->0;case WEST->1;case NORTH->2;default->3;};
    }
}
