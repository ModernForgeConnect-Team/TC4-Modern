package github.com.gengyoubo.tcmodern.tc4.init;

import github.com.gengyoubo.tcmodern.tc4.Tc4Modern;
import github.com.gengyoubo.tcmodern.tc4.menu.ArchiveCabinetMenu;
import github.com.gengyoubo.tcmodern.tc4.menu.ResearchDeskMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class Tc4Menus {
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, Tc4Modern.MOD_ID);

    public static final RegistryObject<MenuType<ResearchDeskMenu>> RESEARCH_DESK =
            MENUS.register("research_desk", () -> IForgeMenuType.create(ResearchDeskMenu::new));
    public static final RegistryObject<MenuType<ArchiveCabinetMenu>> ARCHIVE_CABINET =
            MENUS.register("archive_cabinet", () -> IForgeMenuType.create(ArchiveCabinetMenu::new));

    private Tc4Menus() { }

    public static void register(IEventBus modBus) { MENUS.register(modBus); }
}
