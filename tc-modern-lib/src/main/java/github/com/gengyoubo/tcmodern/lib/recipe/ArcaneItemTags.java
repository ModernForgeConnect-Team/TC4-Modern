package github.com.gengyoubo.tcmodern.lib.recipe;

import com.mojang.logging.LogUtils;
import github.com.gengyoubo.mfcl.api.LegacyMappings;
import github.com.gengyoubo.mfcl.api.LegacyRuntime;
import github.com.gengyoubo.mfcl.api.LegacyStack;
import github.com.gengyoubo.tcmodern.api.TcModernApi;
import github.com.gengyoubo.tcmodern.api.recipe.TcRecipeRegistrar;
import github.com.gengyoubo.tcmodern.lib.TcModernLib;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Resolve modern tags for original TC4 arcane recipes when Forge updates item tags. */
@Mod.EventBusSubscriber(modid = TcModernLib.MOD_ID)
public final class ArcaneItemTags {
    private ArcaneItemTags() { }

    @SubscribeEvent
    public static void updated(TagsUpdatedEvent event) {
        if (!event.shouldUpdateStaticData()) return;
        TcModernApi.runtime().ifPresent(runtime -> {
            try {
                var items = event.getRegistryAccess().registryOrThrow(Registries.ITEM);
                TcRecipeRegistrar.refreshTags(runtime, name -> {
                    var result = new ArrayList<LegacyStack>();
                    var tag = TagKey.create(Registries.ITEM, ResourceLocation.tryParse(name));
                    items.getTag(tag).ifPresent(holders -> holders.forEach(holder -> {
                        var id = items.getKey(holder.value());
                        try {
                            runtime.hostRepresentations().item(id.toString());
                            runtime.mappings().toLegacy(LegacyMappings.ITEM_STACK, new ItemStack(holder.value()))
                                    .map(runtime::snapshot).ifPresent(result::add);
                        } catch (RuntimeException failure) {
                            LogUtils.getLogger().warn("TC4 arcane tag {}: could not map {}: {}", name, id, failure.toString());
                        }
                    }));
                    if (result.isEmpty()) LogUtils.getLogger().warn("TC4 arcane tag {} is empty or unavailable", name);
                    return List.copyOf(result);
                });
            } catch (RuntimeException failure) {
                LogUtils.getLogger().error("Could not update TC4 arcane recipe item tags", failure);
            }
        });
    }
}
