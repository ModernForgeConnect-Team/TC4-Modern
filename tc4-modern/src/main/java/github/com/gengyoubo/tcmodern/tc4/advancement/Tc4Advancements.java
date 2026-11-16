package github.com.gengyoubo.tcmodern.tc4.advancement;

import github.com.gengyoubo.tcmodern.tc4.Tc4Modern;
import github.com.gengyoubo.tcmodern.tc4.init.Tc4Items;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.Objects;

import static github.com.gengyoubo.tcmodern.tc4.Tc4Modern.TC;

@Mod.EventBusSubscriber(modid = Tc4Modern.MOD_ID)
public final class Tc4Advancements {
    private static final ResourceLocation WOOD_IRON_WAND = ResourceLocation.fromNamespaceAndPath(TC, "wand_casting/wood_iron");
    private static final ResourceLocation NOVICE_WAND = ResourceLocation.fromNamespaceAndPath(Tc4Modern.MOD_ID, "novice_wand");
    private static final ResourceLocation INKWELL = ResourceLocation.fromNamespaceAndPath(TC, "item_inkwell");
    private static final ResourceLocation WRITING_TOOLS = ResourceLocation.fromNamespaceAndPath(Tc4Modern.MOD_ID, "writing_tools");

    private Tc4Advancements() { }
    private record CraftAdvancement(ResourceLocation id, String criterion) {}
    private static final Map<ResourceLocation, CraftAdvancement> CRAFT_ADVANCEMENTS = Map.of(
            WOOD_IRON_WAND, new CraftAdvancement(NOVICE_WAND, "craft_wand"),
            INKWELL, new CraftAdvancement(WRITING_TOOLS, "craft_inkwell"),

            Objects.requireNonNull(Tc4Items.PENCIL.getId()),
            new CraftAdvancement(
                    ResourceLocation.fromNamespaceAndPath(Tc4Modern.MOD_ID, "disposable_tool"),
                    "craft_pencil"
            ),

            Objects.requireNonNull(Tc4Items.MECHANICAL_PENCIL.getId()),
            new CraftAdvancement(
                    ResourceLocation.fromNamespaceAndPath(Tc4Modern.MOD_ID, "fragile"),
                    "craft_mechanical_pencil"
            ),

            Objects.requireNonNull(Tc4Items.FOUNTAIN_PEN.getId()),
            new CraftAdvancement(
                    ResourceLocation.fromNamespaceAndPath(Tc4Modern.MOD_ID, "fountain_pen"),
                    "craft_fountain_pen"
            )
    );
    @SubscribeEvent
    public static void onCraft(PlayerEvent.ItemCraftedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ResourceLocation item = BuiltInRegistries.ITEM.getKey(event.getCrafting().getItem());
        CraftAdvancement target = CRAFT_ADVANCEMENTS.get(item);
        if (target == null) return;
        var advancement = player.server.getAdvancements().getAdvancement(target.id());
        if (advancement != null) {
            player.getAdvancements().award(advancement, target.criterion());
        }
    }
}
