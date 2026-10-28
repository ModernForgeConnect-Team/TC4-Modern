package github.com.gengyoubo.tcmodern.tc4.init;

import github.com.gengyoubo.tcmodern.tc4.Tc4Modern;
import github.com.gengyoubo.tcmodern.tc4.content.ElementWorkbookItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Every {@code tc4modern} item, block items included; the creative tab lists them in registration order. */
public final class Tc4Items {
    static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Tc4Modern.MOD_ID);
    public static final RegistryObject<Item> ELEMENT_WORKBOOK = ITEMS.register("element_workbook", ElementWorkbookItem::new);

    private Tc4Items() { }

    public static void register(IEventBus modBus) { ITEMS.register(modBus); }
}
