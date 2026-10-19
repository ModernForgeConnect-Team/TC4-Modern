package github.com.gengyoubo.tcmodern.tc4.content;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import github.com.gengyoubo.mfcl.api.LegacyMappings;
import github.com.gengyoubo.mfcl.api.LegacyStack;
import github.com.gengyoubo.tcmodern.api.TcModernApi;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CookingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SmeltingRecipe;

/** Loads a normal furnace recipe with a legacy result, preserving its metadata/NBT envelope. */
final class LegacyResultSmeltingSerializer implements RecipeSerializer<SmeltingRecipe> {
    @Override
    public SmeltingRecipe fromJson(ResourceLocation id, JsonObject json) {
        var output = GsonHelper.getAsJsonObject(json, "result");
        var legacy = new LegacyStack(GsonHelper.getAsString(output, "legacy_item"),
                GsonHelper.getAsInt(output, "metadata", 0), GsonHelper.getAsInt(output, "count", 1), "");
        ItemStack result;
        try {
            var runtime = TcModernApi.runtime().orElseThrow(() ->
                    new IllegalStateException("Original Thaumcraft runtime is not ready"));
            result = runtime.mappings().toHost(LegacyMappings.ITEM_STACK, runtime.stack(legacy), ItemStack.class)
                    .filter(stack -> !stack.isEmpty()).orElseThrow(() ->
                            new IllegalStateException("No modern item mapping for " + legacy));
        } catch (RuntimeException failure) {
            throw new JsonParseException("Cannot resolve legacy smelting result for " + id, failure);
        }
        return new SmeltingRecipe(id, GsonHelper.getAsString(json, "group", ""),
                CookingBookCategory.CODEC.byName(GsonHelper.getAsString(json, "category", null), CookingBookCategory.MISC),
                Ingredient.fromJson(json.get("ingredient"), false), result,
                GsonHelper.getAsFloat(json, "experience", 0.0F), GsonHelper.getAsInt(json, "cookingtime", 200));
    }

    // The resolved recipe uses vanilla's serializer, so recipe sync carries the full result stack
    // instead of resolving a legacy object again on the client. JEI also sees a normal smelting recipe.
    @Override
    public SmeltingRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
        return RecipeSerializer.SMELTING_RECIPE.fromNetwork(id, buffer);
    }

    @Override
    public void toNetwork(FriendlyByteBuf buffer, SmeltingRecipe recipe) {
        RecipeSerializer.SMELTING_RECIPE.toNetwork(buffer, recipe);
    }
}
