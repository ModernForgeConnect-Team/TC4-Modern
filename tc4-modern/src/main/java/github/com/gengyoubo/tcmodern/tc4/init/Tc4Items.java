package github.com.gengyoubo.tcmodern.tc4.init;

import github.com.gengyoubo.tcmodern.tc4.Tc4Modern;
import github.com.gengyoubo.tcmodern.tc4.content.ElementWorkbookItem;
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

    public static final RegistryObject<Item> PENCIL_LEAD = ITEMS.register("pencil_lead", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> PEN_REFILL = ITEMS.register("pen_refill", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> PENCIL = ITEMS.register("pencil", () -> new WritingToolItem(16, 0));
    public static final RegistryObject<Item> MECHANICAL_PENCIL = ITEMS.register("mechanical_pencil", () -> new WritingToolItem(240, 16));
    public static final RegistryObject<Item> FOUNTAIN_PEN = ITEMS.register("fountain_pen", () -> new WritingToolItem(96, 96));

    private Tc4Items() { }

    public static void register(IEventBus modBus) { ITEMS.register(modBus); }
}
