package github.com.gengyoubo.tcmodern.tc4.init;

import github.com.gengyoubo.tcmodern.tc4.Tc4Modern;
import github.com.gengyoubo.tcmodern.tc4.content.LegacyResultSmeltingSerializer;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class Tc4RecipeSerializers {
    private static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, Tc4Modern.MOD_ID);

    public static final RegistryObject<RecipeSerializer<?>> LEGACY_RESULT_SMELTING =
            RECIPE_SERIALIZERS.register("legacy_result_smelting", LegacyResultSmeltingSerializer::new);

    private Tc4RecipeSerializers() { }

    public static void register(IEventBus modBus) { RECIPE_SERIALIZERS.register(modBus); }
}
