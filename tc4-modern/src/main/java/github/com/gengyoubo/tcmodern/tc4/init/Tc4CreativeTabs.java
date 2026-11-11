package github.com.gengyoubo.tcmodern.tc4.init;

import github.com.gengyoubo.tcmodern.lib.creative.LateCreativeVariants;
import github.com.gengyoubo.tcmodern.tc4.Tc4Modern;
import github.com.gengyoubo.tcmodern.tc4.compat.Tc4WandStacks;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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

    /** Every wand, staff and sceptre build, fully charged. */
    public static final RegistryObject<CreativeModeTab> WANDS = TABS.register("wands", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.tc4modern.wands"))
            .withTabsBefore(CONTENT.getKey())
            .icon(() -> {
                var wand = Tc4WandStacks.icon();
                return wand.isEmpty() ? new ItemStack(Items.STICK) : wand;
            })
            .displayItems((parameters, output) -> Tc4WandStacks.all().forEach(output::accept))
            .build());

    private Tc4CreativeTabs() { }

    public static void register(IEventBus modBus) {
        TABS.register(modBus);
        // The wand tab lists its own charged builds; the original's uncharged creative variants would duplicate them.
        LateCreativeVariants.skip(WANDS.getKey());
    }
}
