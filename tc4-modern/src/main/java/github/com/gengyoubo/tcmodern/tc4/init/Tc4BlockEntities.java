package github.com.gengyoubo.tcmodern.tc4.init;

import github.com.gengyoubo.tcmodern.tc4.Tc4Modern;
import github.com.gengyoubo.tcmodern.tc4.content.ArchiveCabinetBlockEntity;
import github.com.gengyoubo.tcmodern.tc4.content.ResearchDeskBlockEntity;
import github.com.gengyoubo.tcmodern.tc4.content.NodeReactorBlockEntity;
import github.com.gengyoubo.tcmodern.tc4.content.ModernDeconstructionBlockEntity;
import github.com.gengyoubo.tcmodern.tc4.content.PrimalFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class Tc4BlockEntities {
    private static final DeferredRegister<BlockEntityType<?>> TYPES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, Tc4Modern.MOD_ID);

    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<ResearchDeskBlockEntity>> RESEARCH_DESK = TYPES.register("research_desk",
            () -> BlockEntityType.Builder.of(ResearchDeskBlockEntity::new, Tc4Blocks.RESEARCH_DESK.get()).build(null));
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<ArchiveCabinetBlockEntity>> ARCHIVE_CABINET = TYPES.register("archive_cabinet",
            () -> BlockEntityType.Builder.of(ArchiveCabinetBlockEntity::new, Tc4Blocks.ARCHIVE_CABINET.get()).build(null));
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<NodeReactorBlockEntity>> NODE_REACTOR = TYPES.register("node_reactor",
            () -> BlockEntityType.Builder.of(NodeReactorBlockEntity::new, Tc4Blocks.NODE_REACTOR.get()).build(null));
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<ModernDeconstructionBlockEntity>> DECONSTRUCTION = TYPES.register("deconstruction",
            () -> BlockEntityType.Builder.of(ModernDeconstructionBlockEntity::new, Tc4Blocks.DECONSTRUCTION_X.get(), Tc4Blocks.DECONSTRUCTION_Y.get()).build(null));
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<PrimalFurnaceBlockEntity>> PRIMAL_FURNACE = TYPES.register("primal_furnace",
            () -> BlockEntityType.Builder.of(PrimalFurnaceBlockEntity::new, Tc4Blocks.PRIMAL_FURNACE.get()).build(null));

    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<github.com.gengyoubo.tcmodern.tc4.content.GiantSaplingEntity>> GIANT_SAPLING = TYPES.register("giant_sapling",
            () -> BlockEntityType.Builder.of(github.com.gengyoubo.tcmodern.tc4.content.GiantSaplingEntity::new,
                Tc4Blocks.MEGA_GREATWOOD_SAPLING.get(), Tc4Blocks.SACRED_GREATWOOD_SAPLING.get(), Tc4Blocks.ENCHANTED_SACRED_GREATWOOD_SAPLING.get(),
                Tc4Blocks.MEGA_SILVERWOOD_SAPLING.get(), Tc4Blocks.SACRED_SILVERWOOD_SAPLING.get(), Tc4Blocks.ENCHANTED_SACRED_SILVERWOOD_SAPLING.get()).build(null));

    private Tc4BlockEntities() { }

    public static void register(IEventBus modBus) { TYPES.register(modBus); }
}
