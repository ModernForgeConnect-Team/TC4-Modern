package github.com.gengyoubo.tcmodern.tc4.init;

import github.com.gengyoubo.tcmodern.tc4.Tc4Modern;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class Tc4CreativeTabs {
    private static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Tc4Modern.MOD_ID);

    public static final RegistryObject<CreativeModeTab> CONTENT = TABS.register("content", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.tc4modern"))
            .icon(() -> new ItemStack(Tc4Blocks.DEEPSLATE_CINNABAR_ORE.get()))
            .displayItems((parameters, output) -> Tc4Items.ITEMS.getEntries().forEach(item -> output.accept(item.get())))
            .build());

    private Tc4CreativeTabs() { }

    public static void register(IEventBus modBus) { TABS.register(modBus); }
}
