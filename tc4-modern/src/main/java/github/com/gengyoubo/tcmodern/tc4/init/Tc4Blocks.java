package github.com.gengyoubo.tcmodern.tc4.init;

import github.com.gengyoubo.tcmodern.tc4.Tc4Modern;
import github.com.gengyoubo.tcmodern.tc4.content.ArchiveCabinetBlock;
import github.com.gengyoubo.tcmodern.tc4.content.DeepslateLegacyOreBlock;
import github.com.gengyoubo.tcmodern.tc4.content.ResearchDeskBlock;
import github.com.gengyoubo.tcmodern.tc4.content.ModernDeconstructionBlock;
import github.com.gengyoubo.tcmodern.tc4.content.NodeReactorBlock;
import github.com.gengyoubo.tcmodern.tc4.content.PrimalFurnaceBlock;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Modern-only deepslate variants of the original Thaumcraft ores and elemental infused stones, plus the
 * modern research desk, its archive cabinets and fences of the original magical woods. The blocks are
 * independently registered under {@code tc4modern}; ore drops map back to the original TC4 items through
 * MFCL at runtime.
 */
public final class Tc4Blocks {
    private static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, Tc4Modern.MOD_ID);
    /** The values vanilla FireBlock gives wooden fences and fence gates. */
    private static final int FENCE_FLAMMABILITY = 20, FENCE_FIRE_SPREAD = 5;

    public static final RegistryObject<Block> DEEPSLATE_CINNABAR_ORE =
            ore("deepslate_cinnabar_ore", DeepslateLegacyOreBlock.Drop.CINNABAR, 0);
    public static final RegistryObject<Block> DEEPSLATE_AIR_INFUSED_STONE =
            ore("deepslate_air_infused_stone", DeepslateLegacyOreBlock.Drop.INFUSED, 0);
    public static final RegistryObject<Block> DEEPSLATE_FIRE_INFUSED_STONE =
            ore("deepslate_fire_infused_stone", DeepslateLegacyOreBlock.Drop.INFUSED, 1);
    public static final RegistryObject<Block> DEEPSLATE_WATER_INFUSED_STONE =
            ore("deepslate_water_infused_stone", DeepslateLegacyOreBlock.Drop.INFUSED, 2);
    public static final RegistryObject<Block> DEEPSLATE_EARTH_INFUSED_STONE =
            ore("deepslate_earth_infused_stone", DeepslateLegacyOreBlock.Drop.INFUSED, 3);
    public static final RegistryObject<Block> DEEPSLATE_ORDER_INFUSED_STONE =
            ore("deepslate_order_infused_stone", DeepslateLegacyOreBlock.Drop.INFUSED, 4);
    public static final RegistryObject<Block> DEEPSLATE_ENTROPY_INFUSED_STONE =
            ore("deepslate_entropy_infused_stone", DeepslateLegacyOreBlock.Drop.INFUSED, 5);
    public static final RegistryObject<Block> DEEPSLATE_AMBER_BEARING_STONE =
            ore("deepslate_amber_bearing_stone", DeepslateLegacyOreBlock.Drop.AMBER, 6);
    public static final RegistryObject<Block> RESEARCH_DESK = withItem("research_desk", ResearchDeskBlock::new);
    public static final RegistryObject<Block> ARCHIVE_CABINET = withItem("archive_cabinet", ArchiveCabinetBlock::new);
    public static final RegistryObject<Block> DECONSTRUCTION_X = withItem("deconstruction_x", () -> new ModernDeconstructionBlock(false));
    public static final RegistryObject<Block> DECONSTRUCTION_Y = withItem("deconstruction_y", () -> new ModernDeconstructionBlock(true));
    public static final RegistryObject<Block> NODE_REACTOR = withItem("node_reactor", NodeReactorBlock::new);
    public static final RegistryObject<Block> PRIMAL_FURNACE = withItem("primal_furnace", PrimalFurnaceBlock::new);
    public static final RegistryObject<Block> INTERMEDIATE_CRUCIBLE = withItem("intermediate_crucible", github.com.gengyoubo.tcmodern.tc4.content.IntermediateCrucibleBlock::new);
    public static final RegistryObject<Block> GREATWOOD_WOOD = barkWood("greatwood_wood", MapColor.TERRACOTTA_BLACK);
    public static final RegistryObject<Block> SILVERWOOD_WOOD = barkWood("silverwood_wood", MapColor.WOOL);
    public static final RegistryObject<Block> STRIPPED_GREATWOOD_LOG = barkWood("stripped_greatwood_log", MapColor.TERRACOTTA_BROWN);
    public static final RegistryObject<Block> STRIPPED_GREATWOOD_WOOD = barkWood("stripped_greatwood_wood", MapColor.TERRACOTTA_BROWN);
    public static final RegistryObject<Block> STRIPPED_SILVERWOOD_LOG = barkWood("stripped_silverwood_log", MapColor.SAND);
    public static final RegistryObject<Block> STRIPPED_SILVERWOOD_WOOD = barkWood("stripped_silverwood_wood", MapColor.SAND);
    public static final RegistryObject<Block> GREATWOOD_FENCE = fence("greatwood_fence", MapColor.TERRACOTTA_BLACK);
    public static final RegistryObject<Block> GREATWOOD_FENCE_GATE = fenceGate("greatwood_fence_gate", MapColor.TERRACOTTA_BLACK);
    public static final RegistryObject<Block> SILVERWOOD_FENCE = fence("silverwood_fence", MapColor.WOOL);
    public static final RegistryObject<Block> SILVERWOOD_FENCE_GATE = fenceGate("silverwood_fence_gate", MapColor.WOOL);

    public static final RegistryObject<Block> MEGA_GREATWOOD_SAPLING = withItem("mega_greatwood_sapling", () -> new github.com.gengyoubo.tcmodern.tc4.content.GiantSaplingBlock(false, 0));
    public static final RegistryObject<Block> SACRED_GREATWOOD_SAPLING = withItem("sacred_greatwood_sapling", () -> new github.com.gengyoubo.tcmodern.tc4.content.GiantSaplingBlock(false, 1));
    public static final RegistryObject<Block> ENCHANTED_SACRED_GREATWOOD_SAPLING = withItem("enchanted_sacred_greatwood_sapling", () -> new github.com.gengyoubo.tcmodern.tc4.content.GiantSaplingBlock(false, 2));
    public static final RegistryObject<Block> MEGA_SILVERWOOD_SAPLING = withItem("mega_silverwood_sapling", () -> new github.com.gengyoubo.tcmodern.tc4.content.GiantSaplingBlock(true, 0));
    public static final RegistryObject<Block> SACRED_SILVERWOOD_SAPLING = withItem("sacred_silverwood_sapling", () -> new github.com.gengyoubo.tcmodern.tc4.content.GiantSaplingBlock(true, 1));
    public static final RegistryObject<Block> ENCHANTED_SACRED_SILVERWOOD_SAPLING = withItem("enchanted_sacred_silverwood_sapling", () -> new github.com.gengyoubo.tcmodern.tc4.content.GiantSaplingBlock(true, 2));

    private Tc4Blocks() { }

    public static void register(IEventBus modBus) { BLOCKS.register(modBus); }

    private static RegistryObject<Block> withItem(String id, Supplier<Block> factory) {
        RegistryObject<Block> block = BLOCKS.register(id, factory);
        Tc4Items.ITEMS.register(id, () -> new BlockItem(block.get(), new Item.Properties()) {
            @Override public boolean isFoil(net.minecraft.world.item.ItemStack stack) {
                return id.startsWith("enchanted_sacred_") || super.isFoil(stack);
            }
        });
        return block;
    }

    /** Six bark faces, with the orientation and fire behavior of vanilla wood. */
    private static RegistryObject<Block> barkWood(String id, MapColor color) {
        return withItem(id, () -> new RotatedPillarBlock(BlockBehaviour.Properties.copy(Blocks.OAK_WOOD).mapColor(color)) {
            @Override public int getFlammability(BlockState state, BlockGetter level, BlockPos pos, Direction face) { return 5; }
            @Override public int getFireSpreadSpeed(BlockState state, BlockGetter level, BlockPos pos, Direction face) { return 5; }
        });
    }

    private static RegistryObject<Block> fence(String id, MapColor color) {
        return withItem(id, () -> new FenceBlock(plankProperties(color)) {
            @Override public int getFlammability(BlockState state, BlockGetter level, BlockPos pos, Direction face) { return FENCE_FLAMMABILITY; }
            @Override public int getFireSpreadSpeed(BlockState state, BlockGetter level, BlockPos pos, Direction face) { return FENCE_FIRE_SPREAD; }
        });
    }

    private static RegistryObject<Block> fenceGate(String id, MapColor color) {
        return withItem(id, () -> new FenceGateBlock(plankProperties(color), WoodType.OAK) {
            @Override public int getFlammability(BlockState state, BlockGetter level, BlockPos pos, Direction face) { return FENCE_FLAMMABILITY; }
            @Override public int getFireSpreadSpeed(BlockState state, BlockGetter level, BlockPos pos, Direction face) { return FENCE_FIRE_SPREAD; }
        });
    }

    /** Hardness and blast resistance of the original BlockWoodenDevice planks; the rest matches vanilla wooden fences. */
    private static BlockBehaviour.Properties plankProperties(MapColor color) {
        return BlockBehaviour.Properties.of().mapColor(color).forceSolidOn().instrument(NoteBlockInstrument.BASS)
                .strength(2.5F, 10.0F).sound(SoundType.WOOD).ignitedByLava();
    }

    /** Registers the block together with its item; the item also lands in the creative tab. */
    private static RegistryObject<Block> ore(String id, DeepslateLegacyOreBlock.Drop drop, int metadata) {
        RegistryObject<Block> block = BLOCKS.register(id, () -> new DeepslateLegacyOreBlock(drop, metadata));
        Tc4Items.ITEMS.register(id, () -> new BlockItem(block.get(), new Item.Properties()));
        return block;
    }
}
