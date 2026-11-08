package github.com.gengyoubo.tcmodern.tc4.content;

import github.com.gengyoubo.tcmodern.tc4.Tc4Modern;
import github.com.gengyoubo.tcmodern.tc4.init.Tc4Blocks;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraftforge.common.ToolActions;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/** Preserve old TC4 log orientation without stripping silverwood node blocks. */
@Mod.EventBusSubscriber(modid = Tc4Modern.MOD_ID)
public final class MagicalWoodStripping {
    private static final ResourceLocation LEGACY_LOG = ResourceLocation.fromNamespaceAndPath("thaumcraft", "block_magical_log");
    private MagicalWoodStripping() { }

    @SubscribeEvent public static void onToolModification(BlockEvent.BlockToolModificationEvent event) {
        if (event.getToolAction() != ToolActions.AXE_STRIP || event.getFinalState() != event.getState()
                || !event.getHeldItemStack().canPerformAction(ToolActions.AXE_STRIP)) return;
        BlockState state = event.getState(), result;
        if (state.is(Tc4Blocks.GREATWOOD_WOOD.get())) result = Tc4Blocks.STRIPPED_GREATWOOD_WOOD.get().defaultBlockState();
        else if (state.is(Tc4Blocks.SILVERWOOD_WOOD.get())) result = Tc4Blocks.STRIPPED_SILVERWOOD_WOOD.get().defaultBlockState();
        else if (LEGACY_LOG.equals(ForgeRegistries.BLOCKS.getKey(state.getBlock()))
                && state.getBlock().getStateDefinition().getProperty("legacy_meta") instanceof IntegerProperty metadata) {
            int value = state.getValue(metadata), species = value & 3, orientation = value & 12;
            if (species > 1) return;
            result = (orientation == 12
                    ? species == 0 ? Tc4Blocks.STRIPPED_GREATWOOD_WOOD : Tc4Blocks.STRIPPED_SILVERWOOD_WOOD
                    : species == 0 ? Tc4Blocks.STRIPPED_GREATWOOD_LOG : Tc4Blocks.STRIPPED_SILVERWOOD_LOG).get().defaultBlockState();
            Direction.Axis axis = orientation == 4 ? Direction.Axis.X : orientation == 8 ? Direction.Axis.Z : Direction.Axis.Y;
            event.setFinalState(result.setValue(RotatedPillarBlock.AXIS, axis));
            return;
        } else return;
        event.setFinalState(result.setValue(RotatedPillarBlock.AXIS, state.getValue(RotatedPillarBlock.AXIS)));
    }
}
