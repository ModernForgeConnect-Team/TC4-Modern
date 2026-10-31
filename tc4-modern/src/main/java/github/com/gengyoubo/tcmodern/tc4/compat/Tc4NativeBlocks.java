package github.com.gengyoubo.tcmodern.tc4.compat;

import github.com.gengyoubo.mfcl.api.spi.LegacyBlockProvider;
import github.com.gengyoubo.tcmodern.tc4.content.MagicalLeavesBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour;

/** Native leaf implementation registered by MFCL under the existing original block/item IDs. */
public final class Tc4NativeBlocks implements LegacyBlockProvider {
    @Override public NativeBlock create(String legacyKey, Object hostProperties, DisplayTicks display) {
        if (!legacyKey.equals("Thaumcraft:blockMagicalLeaves")) return null;
        var leaves = new MagicalLeavesBlock((BlockBehaviour.Properties) hostProperties, display);
        return new NativeBlock() {
            @Override public Object block() { return leaves; }
            @Override public Object state(int metadata) { return leaves.fromLegacy(metadata); }
            @Override public int metadata(Object hostState) { return MagicalLeavesBlock.legacyMetadata((BlockState) hostState); }
            @Override public Object update(Object previousState, int metadata) {
                var previous = (BlockState) previousState;
                var state = leaves.fromLegacy(metadata);
                if (previous.is(leaves)) state = state.setValue(LeavesBlock.DISTANCE, previous.getValue(LeavesBlock.DISTANCE))
                        .setValue(LeavesBlock.WATERLOGGED, previous.getValue(LeavesBlock.WATERLOGGED));
                return state;
            }
            @Override public Object placement(Object placementState, int metadata) {
                return ((BlockState) placementState).setValue(MagicalLeavesBlock.LEGACY_META, metadata | 4)
                        .setValue(LeavesBlock.PERSISTENT, true);
            }
        };
    }
}
