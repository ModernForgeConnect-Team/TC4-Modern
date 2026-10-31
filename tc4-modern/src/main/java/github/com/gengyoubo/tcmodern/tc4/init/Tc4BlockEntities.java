package github.com.gengyoubo.tcmodern.tc4.init;

import github.com.gengyoubo.tcmodern.tc4.Tc4Modern;
import github.com.gengyoubo.tcmodern.tc4.content.ArchiveCabinetBlockEntity;
import github.com.gengyoubo.tcmodern.tc4.content.ResearchDeskBlockEntity;
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

    private Tc4BlockEntities() { }

    public static void register(IEventBus modBus) { TYPES.register(modBus); }
}
