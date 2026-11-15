package github.com.gengyoubo.tcmodern.tc4.advancement;

import github.com.gengyoubo.tcmodern.tc4.Tc4Modern;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Tc4Modern.MOD_ID)
public final class Tc4Advancements {
    private static final ResourceLocation WOOD_IRON_WAND = ResourceLocation.fromNamespaceAndPath("thaumcraft", "wand_casting/wood_iron");
    private static final ResourceLocation NOVICE_WAND = ResourceLocation.fromNamespaceAndPath(Tc4Modern.MOD_ID, "novice_wand");

    private Tc4Advancements() { }

    @SubscribeEvent
    public static void onCraft(PlayerEvent.ItemCraftedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !WOOD_IRON_WAND.equals(BuiltInRegistries.ITEM.getKey(event.getCrafting().getItem()))) return;
        var advancement = player.server.getAdvancements().getAdvancement(NOVICE_WAND);
        if (advancement != null) player.getAdvancements().award(advancement, "craft_wand");
    }
}
