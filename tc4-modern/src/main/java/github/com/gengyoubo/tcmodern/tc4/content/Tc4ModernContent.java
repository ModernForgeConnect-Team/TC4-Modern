package github.com.gengyoubo.tcmodern.tc4.content;

import github.com.gengyoubo.tcmodern.tc4.Tc4Modern;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Modern-only deepslate variants of the original Thaumcraft ores and elemental infused stones.
 * The blocks are independently registered under {@code tc4modern}; drops map back to the original
 * TC4 items through MFCL at runtime.
 */
public final class Tc4ModernContent {
    private static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, Tc4Modern.MOD_ID);
    private static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, Tc4Modern.MOD_ID);
    private static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Tc4Modern.MOD_ID);
    private static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, Tc4Modern.MOD_ID);
    private static final List<RegistryObject<Block>> CONTENT = new ArrayList<>();

    public static final RegistryObject<RecipeSerializer<?>> LEGACY_RESULT_SMELTING =
            RECIPE_SERIALIZERS.register("legacy_result_smelting", LegacyResultSmeltingSerializer::new);

    public static final RegistryObject<Block> DEEPSLATE_CINNABAR_ORE =
            register("deepslate_cinnabar_ore", DeepslateLegacyOreBlock.Drop.CINNABAR, 0);
    public static final RegistryObject<Block> DEEPSLATE_AIR_INFUSED_STONE =
            register("deepslate_air_infused_stone", DeepslateLegacyOreBlock.Drop.INFUSED, 0);
    public static final RegistryObject<Block> DEEPSLATE_FIRE_INFUSED_STONE =
            register("deepslate_fire_infused_stone", DeepslateLegacyOreBlock.Drop.INFUSED, 1);
    public static final RegistryObject<Block> DEEPSLATE_WATER_INFUSED_STONE =
            register("deepslate_water_infused_stone", DeepslateLegacyOreBlock.Drop.INFUSED, 2);
    public static final RegistryObject<Block> DEEPSLATE_EARTH_INFUSED_STONE =
            register("deepslate_earth_infused_stone", DeepslateLegacyOreBlock.Drop.INFUSED, 3);
    public static final RegistryObject<Block> DEEPSLATE_ORDER_INFUSED_STONE =
            register("deepslate_order_infused_stone", DeepslateLegacyOreBlock.Drop.INFUSED, 4);
    public static final RegistryObject<Block> DEEPSLATE_ENTROPY_INFUSED_STONE =
            register("deepslate_entropy_infused_stone", DeepslateLegacyOreBlock.Drop.INFUSED, 5);
    public static final RegistryObject<Block> DEEPSLATE_AMBER_BEARING_STONE =
            register("deepslate_amber_bearing_stone", DeepslateLegacyOreBlock.Drop.AMBER, 6);

    private Tc4ModernContent() { }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        TABS.register(modBus);
        RECIPE_SERIALIZERS.register(modBus);
        TABS.register("content", () -> CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.tc4modern"))
                .icon(() -> new ItemStack(DEEPSLATE_CINNABAR_ORE.get()))
                .displayItems((parameters, output) -> CONTENT.forEach(block -> output.accept(block.get())))
                .build());
    }

    private static RegistryObject<Block> register(String id, DeepslateLegacyOreBlock.Drop drop, int metadata) {
        RegistryObject<Block> block = BLOCKS.register(id, () -> new DeepslateLegacyOreBlock(drop, metadata));
        ITEMS.register(id, () -> new BlockItem(block.get(), new Item.Properties()));
        CONTENT.add(block);
        return block;
    }
}
