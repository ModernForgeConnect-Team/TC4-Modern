package github.com.gengyoubo.tcmodern.api.recipe;

import github.com.gengyoubo.mfcl.api.LegacyObject;
import github.com.gengyoubo.mfcl.api.mla.LegacyConstructor;
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

    /** The original's own registration entry points; each adds the recipe to {@link Registry#recipes()}. */
    @LegacyType("thaumcraft.api.ThaumcraftApi")
    public interface Registration {
        /** {@code recipe}: pattern rows, then symbol (Character) and ingredient (ItemStack or ore name) pairs. */
        @LegacyMethod(name = "addArcaneCraftingRecipe", isStatic = true,
                descriptor = "(Ljava/lang/String;Lnet/minecraft/item/ItemStack;Lthaumcraft/api/aspects/AspectList;[Ljava/lang/Object;)Lthaumcraft/api/crafting/ShapedArcaneRecipe;")
        LegacyObject arcane(String research, LegacyObject result, LegacyObject aspects, Object[] recipe);
        /** {@code recipe}: ItemStacks and ore names. */
        @LegacyMethod(name = "addShapelessArcaneCraftingRecipe", isStatic = true,
                descriptor = "(Ljava/lang/String;Lnet/minecraft/item/ItemStack;Lthaumcraft/api/aspects/AspectList;[Ljava/lang/Object;)Lthaumcraft/api/crafting/ShapelessArcaneRecipe;")
        LegacyObject shapeless(String research, LegacyObject result, LegacyObject aspects, Object[] recipe);
        /** {@code catalyst}: an ItemStack or an ore name. */
        @LegacyMethod(name = "addCrucibleRecipe", isStatic = true,
                descriptor = "(Ljava/lang/String;Lnet/minecraft/item/ItemStack;Ljava/lang/Object;Lthaumcraft/api/aspects/AspectList;)Lthaumcraft/api/crafting/CrucibleRecipe;")
        LegacyObject crucible(String research, LegacyObject result, Object catalyst, LegacyObject aspects);
        @LegacyMethod(name = "addInfusionCraftingRecipe", isStatic = true,
                descriptor = "(Ljava/lang/String;Ljava/lang/Object;ILthaumcraft/api/aspects/AspectList;Lnet/minecraft/item/ItemStack;[Lnet/minecraft/item/ItemStack;)Lthaumcraft/api/crafting/InfusionRecipe;")
        LegacyObject infusion(String research, Object result, int instability, LegacyObject aspects, LegacyObject input, LegacyObject[] components);
    }

    @LegacyType("thaumcraft.api.aspects.AspectList")
    public interface AspectListFactory {
        @LegacyConstructor(descriptor = "()V") LegacyObject create();
    }

    @LegacyType("thaumcraft.api.research.ResearchCategories")
    public interface ResearchLookup {
        /** Null when no research has that key. */
        @LegacyMethod(name = "getResearch", isStatic = true, descriptor = "(Ljava/lang/String;)Lthaumcraft/api/research/ResearchItem;")
        LegacyObject find(String key);
    }

    @LegacyType("net.minecraftforge.oredict.OreDictionary")
    public interface Ores {
        /** The live stacks of an ore name, empty when nothing is registered under it. */
        @LegacyMethod(name = "getOres", isStatic = true, descriptor = "(Ljava/lang/String;)Ljava/util/ArrayList;")
        LegacyObject stacks(String name);
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
        @LegacyMethod(name = "setMirrored", descriptor = "(Z)Lthaumcraft/api/crafting/ShapedArcaneRecipe;") LegacyObject mirrored(boolean mirrored);
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
        @LegacyMethod(name = "add", descriptor = "(Lthaumcraft/api/aspects/Aspect;I)Lthaumcraft/api/aspects/AspectList;") LegacyObject add(LegacyObject aspect, int amount);
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
