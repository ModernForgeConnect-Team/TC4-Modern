package github.com.gengyoubo.tcmodern.api.recipe;

import github.com.gengyoubo.mfcl.api.LegacyObject;
import github.com.gengyoubo.mfcl.api.mla.LegacyField;
import github.com.gengyoubo.mfcl.api.mla.LegacyMethod;
import github.com.gengyoubo.mfcl.api.mla.LegacyType;

/**
 * Exact public contracts of TC4 4.2.3.5. No old classes are linked; the JDK collections they return
 * are read with {@code LegacyValues#list}.
 */
public final class TcRecipeAccessors {
    private TcRecipeAccessors() { }

    @LegacyType("thaumcraft.api.ThaumcraftApi")
    public interface Registry {
        @LegacyMethod(name = "getCraftingRecipes", descriptor = "()Ljava/util/List;", isStatic = true)
        LegacyObject recipes();
    }

    @LegacyType("thaumcraft.api.crafting.IArcaneRecipe")
    public interface Arcane {
        @LegacyMethod(name = "getRecipeOutput", descriptor = "()Lnet/minecraft/item/ItemStack;") LegacyObject output();
        @LegacyMethod(name = "getAspects", descriptor = "()Lthaumcraft/api/aspects/AspectList;") LegacyObject aspects();
        @LegacyMethod(name = "getResearch", descriptor = "()Ljava/lang/String;") String research();
    }

    @LegacyType("thaumcraft.api.crafting.ShapedArcaneRecipe")
    public interface Shaped {
        @LegacyMethod(name = "getInput", descriptor = "()[Ljava/lang/Object;") LegacyObject[] inputs();
        @LegacyField(name = "width", descriptor = "I") int width();
        @LegacyField(name = "height", descriptor = "I") int height();
    }

    @LegacyType("thaumcraft.api.crafting.ShapelessArcaneRecipe")
    public interface Shapeless {
        @LegacyMethod(name = "getInput", descriptor = "()Ljava/util/ArrayList;") LegacyObject inputs();
    }

    @LegacyType("thaumcraft.api.crafting.CrucibleRecipe")
    public interface Crucible {
        @LegacyMethod(name = "getRecipeOutput", descriptor = "()Lnet/minecraft/item/ItemStack;") LegacyObject output();
        @LegacyField(name = "catalyst", descriptor = "Ljava/lang/Object;") LegacyObject catalyst();
        @LegacyField(name = "aspects", descriptor = "Lthaumcraft/api/aspects/AspectList;") LegacyObject aspects();
        @LegacyField(name = "key", descriptor = "Ljava/lang/String;") String research();
    }

    @LegacyType("thaumcraft.api.crafting.InfusionRecipe")
    public interface Infusion {
        @LegacyMethod(name = "getRecipeOutput", descriptor = "()Ljava/lang/Object;") LegacyObject output();
        @LegacyMethod(name = "getRecipeInput", descriptor = "()Lnet/minecraft/item/ItemStack;") LegacyObject central();
        @LegacyMethod(name = "getComponents", descriptor = "()[Lnet/minecraft/item/ItemStack;") LegacyObject[] components();
        @LegacyMethod(name = "getAspects", descriptor = "()Lthaumcraft/api/aspects/AspectList;") LegacyObject aspects();
        @LegacyMethod(name = "getResearch", descriptor = "()Ljava/lang/String;") String research();
        @LegacyMethod(name = "getInstability", descriptor = "()I") int instability();
    }

    @LegacyType("thaumcraft.api.aspects.AspectList")
    public interface Aspects {
        @LegacyMethod(name = "getAspects", descriptor = "()[Lthaumcraft/api/aspects/Aspect;") LegacyObject[] values();
        @LegacyMethod(name = "getAmount", descriptor = "(Lthaumcraft/api/aspects/Aspect;)I") int amount(LegacyObject aspect);
    }

    @LegacyType("thaumcraft.api.aspects.Aspect")
    public interface Aspect {
        @LegacyMethod(name = "getTag", descriptor = "()Ljava/lang/String;") String tag();
        @LegacyMethod(name = "getColor", descriptor = "()I") int color();
        @LegacyMethod(name = "getImage", descriptor = "()Lnet/minecraft/util/ResourceLocation;") LegacyObject image();
    }

    @LegacyType("net.minecraft.util.ResourceLocation")
    public interface Resource {
        @LegacyMethod(name = "func_110624_b", descriptor = "()Ljava/lang/String;") String namespace();
        @LegacyMethod(name = "func_110623_a", descriptor = "()Ljava/lang/String;") String path();
    }
}
