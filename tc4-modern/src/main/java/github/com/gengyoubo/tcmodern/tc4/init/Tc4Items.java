package github.com.gengyoubo.tcmodern.tc4.init;

import github.com.gengyoubo.tcmodern.tc4.Tc4Modern;
import github.com.gengyoubo.tcmodern.tc4.content.AncientThaumiumArmorItem;
import github.com.gengyoubo.tcmodern.tc4.content.ElementWorkbookItem;
import net.minecraft.world.item.ArmorItem;
import github.com.gengyoubo.tcmodern.tc4.content.MagicBoneMealItem;
import github.com.gengyoubo.tcmodern.tc4.content.WritingToolItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Every {@code tc4modern} item, block items included; the creative tab lists them in registration order. */
public final class Tc4Items {
    static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Tc4Modern.MOD_ID);
    public static final RegistryObject<Item> ELEMENT_WORKBOOK = ITEMS.register("element_workbook", ElementWorkbookItem::new);
    public static final RegistryObject<Item> MAGIC_BONE_MEAL = ITEMS.register("magic_bone_meal", MagicBoneMealItem::new);
    public static final RegistryObject<Item> ANCIENT_THAUMIUM_INGOT = ITEMS.register("ancient_thaumium_ingot", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> ANCIENT_THAUMIUM_TEMPLATE = ITEMS.register("ancient_thaumium_template", () -> new Item(new Item.Properties()));
    // Vis discount (percent) and warp per piece.
    public static final RegistryObject<Item> ANCIENT_THAUMIUM_HELMET = ITEMS.register("ancient_thaumium_helmet",
            () -> new AncientThaumiumArmorItem(ArmorItem.Type.HELMET, 5, -1));
    public static final RegistryObject<Item> ANCIENT_THAUMIUM_CHESTPLATE = ITEMS.register("ancient_thaumium_chestplate",
            () -> new AncientThaumiumArmorItem(ArmorItem.Type.CHESTPLATE, 15, -3));
    public static final RegistryObject<Item> ANCIENT_THAUMIUM_LEGGINGS = ITEMS.register("ancient_thaumium_leggings",
            () -> new AncientThaumiumArmorItem(ArmorItem.Type.LEGGINGS, 10, -2));
    public static final RegistryObject<Item> ANCIENT_THAUMIUM_BOOTS = ITEMS.register("ancient_thaumium_boots",
            () -> new AncientThaumiumArmorItem(ArmorItem.Type.BOOTS, 5, -1));

    public static final RegistryObject<Item> PENCIL_LEAD = ITEMS.register("pencil_lead", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> PEN_REFILL = ITEMS.register("pen_refill", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> PENCIL = ITEMS.register("pencil", () -> new WritingToolItem(16, 0));
    public static final RegistryObject<Item> MECHANICAL_PENCIL = ITEMS.register("mechanical_pencil", () -> new WritingToolItem(240, 16));
    public static final RegistryObject<Item> FOUNTAIN_PEN = ITEMS.register("fountain_pen", () -> new WritingToolItem(96, 96));

    private Tc4Items() { }

    public static void register(IEventBus modBus) { ITEMS.register(modBus); }
}
