package github.com.gengyoubo.tcmodern.tc4.init;

import github.com.gengyoubo.tcmodern.tc4.Tc4Modern;
import github.com.gengyoubo.tcmodern.tc4.content.ArchiveCabinetBlock;
import github.com.gengyoubo.tcmodern.tc4.content.DeepslateLegacyOreBlock;
import github.com.gengyoubo.tcmodern.tc4.content.ResearchDeskBlock;
import java.util.function.Supplier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Modern-only deepslate variants of the original Thaumcraft ores and elemental infused stones, plus the
 * modern research desk and its archive cabinets. The blocks are independently registered under
 * {@code tc4modern}; ore drops map back to the original TC4 items through MFCL at runtime.
 */
public final class Tc4Blocks {
    private static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, Tc4Modern.MOD_ID);

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

    private Tc4Blocks() { }

    public static void register(IEventBus modBus) { BLOCKS.register(modBus); }

    private static RegistryObject<Block> withItem(String id, Supplier<Block> factory) {
        RegistryObject<Block> block = BLOCKS.register(id, factory);
        Tc4Items.ITEMS.register(id, () -> new BlockItem(block.get(), new Item.Properties()));
        return block;
    }

    /** Registers the block together with its item; the item also lands in the creative tab. */
    private static RegistryObject<Block> ore(String id, DeepslateLegacyOreBlock.Drop drop, int metadata) {
        RegistryObject<Block> block = BLOCKS.register(id, () -> new DeepslateLegacyOreBlock(drop, metadata));
        Tc4Items.ITEMS.register(id, () -> new BlockItem(block.get(), new Item.Properties()));
        return block;
    }
}
