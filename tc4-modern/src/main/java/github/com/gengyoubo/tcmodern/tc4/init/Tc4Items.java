package github.com.gengyoubo.tcmodern.tc4.init;

import github.com.gengyoubo.tcmodern.tc4.Tc4Modern;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

/** Every {@code tc4modern} item, block items included; the creative tab lists them in registration order. */
public final class Tc4Items {
    static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Tc4Modern.MOD_ID);

    private Tc4Items() { }

    public static void register(IEventBus modBus) { ITEMS.register(modBus); }
}
