package github.com.gengyoubo.tcmodern.api.research;

import github.com.gengyoubo.mfcl.api.LegacyObject;
import github.com.gengyoubo.mfcl.api.mla.LegacyConstructor;
import github.com.gengyoubo.mfcl.api.mla.LegacyField;
import github.com.gengyoubo.mfcl.api.mla.LegacyMethod;
import github.com.gengyoubo.mfcl.api.mla.LegacyType;

/**
 * Exact contracts of the TC4 4.2.3.5 Thaumonomicon: research categories, research items and their pages, plus the
 * vanilla 1.7.10 recipes a page can show. No old classes are linked.
 */
public final class TcBookAccessors {
    private TcBookAccessors() { }

    private static final String ITEM = "Lthaumcraft/api/research/ResearchItem;";

    @LegacyType("thaumcraft.api.research.ResearchCategories")
    public interface CategoryRegistry {
        @LegacyMethod(name = "registerCategory", isStatic = true,
                descriptor = "(Ljava/lang/String;Lnet/minecraft/util/ResourceLocation;Lnet/minecraft/util/ResourceLocation;)V")
        void register(String key, LegacyObject icon, LegacyObject background);
        /** Null when no category has that key. */
        @LegacyMethod(name = "getResearchList", isStatic = true, descriptor = "(Ljava/lang/String;)Lthaumcraft/api/research/ResearchCategoryList;")
        LegacyObject find(String key);
    }

    @LegacyType("thaumcraft.api.research.ResearchCategoryList")
    public interface CategoryList {
        @LegacyField(name = "icon", descriptor = "Lnet/minecraft/util/ResourceLocation;") LegacyObject icon();
        @LegacyField(name = "background", descriptor = "Lnet/minecraft/util/ResourceLocation;") LegacyObject background();
        /** Research by key; read and changed with {@code LegacyValues}. */
        @LegacyField(name = "research", descriptor = "Ljava/util/Map;") LegacyObject research();
        @LegacyField(name = "minDisplayColumn", descriptor = "I") void minColumn(int value);
        @LegacyField(name = "minDisplayRow", descriptor = "I") void minRow(int value);
        @LegacyField(name = "maxDisplayColumn", descriptor = "I") void maxColumn(int value);
        @LegacyField(name = "maxDisplayRow", descriptor = "I") void maxRow(int value);
    }

    @LegacyType("thaumcraft.api.research.ResearchItem")
    public interface Research {
        @LegacyField(name = "key", descriptor = "Ljava/lang/String;") String key();
        @LegacyField(name = "category", descriptor = "Ljava/lang/String;") String category();
        @LegacyField(name = "tags", descriptor = "Lthaumcraft/api/aspects/AspectList;") LegacyObject tags();
        @LegacyField(name = "parents", descriptor = "[Ljava/lang/String;") String[] parents();
        @LegacyField(name = "parentsHidden", descriptor = "[Ljava/lang/String;") String[] parentsHidden();
        @LegacyField(name = "siblings", descriptor = "[Ljava/lang/String;") String[] siblings();
        @LegacyField(name = "displayColumn", descriptor = "I") int column();
        @LegacyField(name = "displayRow", descriptor = "I") int row();
        @LegacyField(name = "icon_item", descriptor = "Lnet/minecraft/item/ItemStack;") LegacyObject iconItem();
        @LegacyField(name = "icon_resource", descriptor = "Lnet/minecraft/util/ResourceLocation;") LegacyObject iconResource();
        @LegacyMethod(name = "getComplexity", descriptor = "()I") int complexity();
        @LegacyMethod(name = "isSpecial", descriptor = "()Z") boolean special();
        @LegacyMethod(name = "isSecondary", descriptor = "()Z") boolean secondary();
        @LegacyMethod(name = "isRound", descriptor = "()Z") boolean round();
        @LegacyMethod(name = "isStub", descriptor = "()Z") boolean stub();
        @LegacyMethod(name = "isVirtual", descriptor = "()Z") boolean virtual();
        @LegacyMethod(name = "isConcealed", descriptor = "()Z") boolean concealed();
        @LegacyMethod(name = "isHidden", descriptor = "()Z") boolean hidden();
        @LegacyMethod(name = "isLost", descriptor = "()Z") boolean lost();
        @LegacyMethod(name = "isAutoUnlock", descriptor = "()Z") boolean autoUnlock();
        @LegacyMethod(name = "getItemTriggers", descriptor = "()[Lnet/minecraft/item/ItemStack;") LegacyObject[] itemTriggers();
        @LegacyMethod(name = "getEntityTriggers", descriptor = "()[Ljava/lang/String;") String[] entityTriggers();
        @LegacyMethod(name = "getAspectTriggers", descriptor = "()[Lthaumcraft/api/aspects/Aspect;") LegacyObject[] aspectTriggers();
        @LegacyMethod(name = "getPages", descriptor = "()[Lthaumcraft/api/research/ResearchPage;") LegacyObject[] pages();
    }

    /** The original builder; every setter returns the same item. */
    @LegacyType("thaumcraft.api.research.ResearchItem")
    public interface ResearchBuilder {
        @LegacyMethod(name = "setSpecial", descriptor = "()" + ITEM) LegacyObject special();
        @LegacyMethod(name = "setSecondary", descriptor = "()" + ITEM) LegacyObject secondary();
        @LegacyMethod(name = "setRound", descriptor = "()" + ITEM) LegacyObject round();
        @LegacyMethod(name = "setStub", descriptor = "()" + ITEM) LegacyObject stub();
        @LegacyMethod(name = "setVirtual", descriptor = "()" + ITEM) LegacyObject virtual();
        @LegacyMethod(name = "setConcealed", descriptor = "()" + ITEM) LegacyObject concealed();
        @LegacyMethod(name = "setHidden", descriptor = "()" + ITEM) LegacyObject hidden();
        @LegacyMethod(name = "setLost", descriptor = "()" + ITEM) LegacyObject lost();
        @LegacyMethod(name = "setAutoUnlock", descriptor = "()" + ITEM) LegacyObject autoUnlock();
        @LegacyMethod(name = "setParents", descriptor = "([Ljava/lang/String;)" + ITEM) LegacyObject parents(String[] keys);
        @LegacyMethod(name = "setParentsHidden", descriptor = "([Ljava/lang/String;)" + ITEM) LegacyObject parentsHidden(String[] keys);
        @LegacyMethod(name = "setSiblings", descriptor = "([Ljava/lang/String;)" + ITEM) LegacyObject siblings(String[] keys);
        @LegacyMethod(name = "setPages", descriptor = "([Lthaumcraft/api/research/ResearchPage;)" + ITEM) LegacyObject pages(LegacyObject[] pages);
        @LegacyMethod(name = "setItemTriggers", descriptor = "([Lnet/minecraft/item/ItemStack;)" + ITEM) LegacyObject itemTriggers(LegacyObject[] stacks);
        @LegacyMethod(name = "setEntityTriggers", descriptor = "([Ljava/lang/String;)" + ITEM) LegacyObject entityTriggers(String[] entities);
        @LegacyMethod(name = "setAspectTriggers", descriptor = "([Lthaumcraft/api/aspects/Aspect;)" + ITEM) LegacyObject aspectTriggers(LegacyObject[] aspects);
        @LegacyMethod(name = "registerResearchItem", descriptor = "()" + ITEM) LegacyObject register();
    }

    @LegacyType("thaumcraft.api.research.ResearchItem")
    public interface ResearchFactory {
        @LegacyConstructor(descriptor = "(Ljava/lang/String;Ljava/lang/String;Lthaumcraft/api/aspects/AspectList;IIILnet/minecraft/item/ItemStack;)V")
        LegacyObject withItem(String key, String category, LegacyObject tags, int column, int row, int complexity, LegacyObject icon);
    }

    @LegacyType("thaumcraft.api.research.ResearchItem")
    public interface ResearchTextureFactory {
        @LegacyConstructor(descriptor = "(Ljava/lang/String;Ljava/lang/String;Lthaumcraft/api/aspects/AspectList;IIILnet/minecraft/util/ResourceLocation;)V")
        LegacyObject withTexture(String key, String category, LegacyObject tags, int column, int row, int complexity, LegacyObject icon);
    }

    @LegacyType("thaumcraft.api.research.ResearchItem")
    public interface VirtualFactory {
        @LegacyConstructor(descriptor = "(Ljava/lang/String;Ljava/lang/String;)V") LegacyObject create(String key, String category);
    }

    @LegacyType("thaumcraft.api.research.ResearchPage")
    public interface Page {
        @LegacyField(name = "type", descriptor = "Lthaumcraft/api/research/ResearchPage$PageType;") LegacyObject type();
        @LegacyField(name = "text", descriptor = "Ljava/lang/String;") String text();
        @LegacyField(name = "research", descriptor = "Ljava/lang/String;") String research();
        @LegacyField(name = "image", descriptor = "Lnet/minecraft/util/ResourceLocation;") LegacyObject image();
        @LegacyField(name = "aspects", descriptor = "Lthaumcraft/api/aspects/AspectList;") LegacyObject aspects();
        /** A recipe, an array of recipes, a smelting input or a structure list, depending on the type. */
        @LegacyField(name = "recipe", descriptor = "Ljava/lang/Object;") LegacyObject recipe();
        @LegacyField(name = "recipeOutput", descriptor = "Lnet/minecraft/item/ItemStack;") LegacyObject output();
    }

    /** Page kinds; compared by identity with {@code LegacyValues#same}. */
    @LegacyType("thaumcraft.api.research.ResearchPage$PageType")
    public interface PageTypes {
        @LegacyField(name = "TEXT", descriptor = "Lthaumcraft/api/research/ResearchPage$PageType;", isStatic = true) LegacyObject text();
        @LegacyField(name = "TEXT_CONCEALED", descriptor = "Lthaumcraft/api/research/ResearchPage$PageType;", isStatic = true) LegacyObject concealed();
        @LegacyField(name = "IMAGE", descriptor = "Lthaumcraft/api/research/ResearchPage$PageType;", isStatic = true) LegacyObject image();
        @LegacyField(name = "CRUCIBLE_CRAFTING", descriptor = "Lthaumcraft/api/research/ResearchPage$PageType;", isStatic = true) LegacyObject crucible();
        @LegacyField(name = "ARCANE_CRAFTING", descriptor = "Lthaumcraft/api/research/ResearchPage$PageType;", isStatic = true) LegacyObject arcane();
        @LegacyField(name = "ASPECTS", descriptor = "Lthaumcraft/api/research/ResearchPage$PageType;", isStatic = true) LegacyObject aspects();
        @LegacyField(name = "NORMAL_CRAFTING", descriptor = "Lthaumcraft/api/research/ResearchPage$PageType;", isStatic = true) LegacyObject crafting();
        @LegacyField(name = "INFUSION_CRAFTING", descriptor = "Lthaumcraft/api/research/ResearchPage$PageType;", isStatic = true) LegacyObject infusion();
        @LegacyField(name = "COMPOUND_CRAFTING", descriptor = "Lthaumcraft/api/research/ResearchPage$PageType;", isStatic = true) LegacyObject compound();
        @LegacyField(name = "INFUSION_ENCHANTMENT", descriptor = "Lthaumcraft/api/research/ResearchPage$PageType;", isStatic = true) LegacyObject enchantment();
        @LegacyField(name = "SMELTING", descriptor = "Lthaumcraft/api/research/ResearchPage$PageType;", isStatic = true) LegacyObject smelting();
    }

    @LegacyType("thaumcraft.api.research.ResearchPage")
    public interface TextPages {
        @LegacyConstructor(descriptor = "(Ljava/lang/String;)V") LegacyObject text(String text);
    }

    @LegacyType("thaumcraft.api.research.ResearchPage")
    public interface ConcealedPages {
        @LegacyConstructor(descriptor = "(Ljava/lang/String;Ljava/lang/String;)V") LegacyObject concealed(String research, String text);
    }

    @LegacyType("thaumcraft.api.research.ResearchPage")
    public interface ImagePages {
        @LegacyConstructor(descriptor = "(Lnet/minecraft/util/ResourceLocation;Ljava/lang/String;)V") LegacyObject image(LegacyObject image, String caption);
    }

    @LegacyType("thaumcraft.api.research.ResearchPage")
    public interface AspectPages {
        @LegacyConstructor(descriptor = "(Lthaumcraft/api/aspects/AspectList;)V") LegacyObject aspects(LegacyObject aspects);
    }

    @LegacyType("thaumcraft.api.research.ResearchPage")
    public interface SmeltingPages {
        @LegacyConstructor(descriptor = "(Lnet/minecraft/item/ItemStack;)V") LegacyObject smelting(LegacyObject input);
    }

    @LegacyType("thaumcraft.api.research.ResearchPage")
    public interface ArcanePages {
        @LegacyConstructor(descriptor = "([Lthaumcraft/api/crafting/IArcaneRecipe;)V") LegacyObject arcane(LegacyObject recipes);
    }

    @LegacyType("thaumcraft.api.research.ResearchPage")
    public interface CruciblePages {
        @LegacyConstructor(descriptor = "([Lthaumcraft/api/crafting/CrucibleRecipe;)V") LegacyObject crucible(LegacyObject recipes);
    }

    @LegacyType("thaumcraft.api.research.ResearchPage")
    public interface InfusionPages {
        @LegacyConstructor(descriptor = "([Lthaumcraft/api/crafting/InfusionRecipe;)V") LegacyObject infusion(LegacyObject recipes);
    }

    @LegacyType("thaumcraft.api.research.ResearchPage")
    public interface CraftingPages {
        @LegacyConstructor(descriptor = "([Lnet/minecraft/item/crafting/IRecipe;)V") LegacyObject crafting(LegacyObject recipes);
    }

    @LegacyType("thaumcraft.api.ThaumcraftApi")
    public interface Warp {
        @LegacyMethod(name = "addWarpToResearch", isStatic = true, descriptor = "(Ljava/lang/String;I)V") void set(String research, int amount);
        @LegacyMethod(name = "getWarp", isStatic = true, descriptor = "(Ljava/lang/Object;)I") int get(Object key);
    }

    @LegacyType("thaumcraft.common.config.Config")
    public interface Options {
        /** -1 easy (all bought with points), 0 normal (secondary bought), 1 hard (all need notes). */
        @LegacyField(name = "researchDifficulty", descriptor = "I", isStatic = true) int difficulty();
    }

    @LegacyType("thaumcraft.common.lib.research.ResearchManager")
    public interface Progress {
        /** The live list of completed keys ({@code "@KEY"} marks a discovered hidden research); never null. */
        @LegacyMethod(name = "getResearchForPlayerSafe", isStatic = true, descriptor = "(Ljava/lang/String;)Ljava/util/ArrayList;")
        LegacyObject completed(String player);
        @LegacyMethod(name = "scheduleSave", isStatic = true, descriptor = "(Lnet/minecraft/entity/player/EntityPlayer;)V") void save(LegacyObject player);
        @LegacyMethod(name = "doesPlayerHaveRequisites", isStatic = true, descriptor = "(Ljava/lang/String;Ljava/lang/String;)Z")
        boolean requisites(String player, String key);
        @LegacyMethod(name = "createResearchNoteForPlayer", isStatic = true,
                descriptor = "(Lnet/minecraft/world/World;Lnet/minecraft/entity/player/EntityPlayer;Ljava/lang/String;)Lnet/minecraft/item/ItemStack;")
        LegacyObject note(LegacyObject world, LegacyObject player, String key);
    }

    /** The hidden-research and clue caches, rebuilt lazily when cleared. */
    @LegacyType("thaumcraft.common.lib.research.ResearchManager")
    public interface Caches {
        @LegacyField(name = "allHiddenResearch", descriptor = "Ljava/util/ArrayList;", isStatic = true) void hidden(LegacyObject value);
        @LegacyField(name = "allValidResearch", descriptor = "Ljava/util/ArrayList;", isStatic = true) void valid(LegacyObject value);
    }

    @LegacyType("thaumcraft.common.lib.network.playerdata.PacketSyncResearch")
    public interface SyncPacket {
        @LegacyConstructor(descriptor = "(Lnet/minecraft/entity/player/EntityPlayer;)V") LegacyObject create(LegacyObject player);
    }

    @LegacyType("thaumcraft.common.lib.network.PacketHandler")
    public interface Network {
        @LegacyField(name = "INSTANCE", descriptor = "Lcpw/mods/fml/common/network/simpleimpl/SimpleNetworkWrapper;", isStatic = true) LegacyObject channel();
    }

    @LegacyType("cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper")
    public interface Channel {
        @LegacyMethod(name = "sendTo", descriptor = "(Lcpw/mods/fml/common/network/simpleimpl/IMessage;Lnet/minecraft/entity/player/EntityPlayerMP;)V")
        void sendTo(LegacyObject message, LegacyObject player);
    }

    @LegacyType("net.minecraft.util.StatCollector")
    public interface Translations {
        @LegacyMethod(name = "func_74838_a", isStatic = true, descriptor = "(Ljava/lang/String;)Ljava/lang/String;") String translate(String key);
        @LegacyMethod(name = "func_94522_b", isStatic = true, descriptor = "(Ljava/lang/String;)Z") boolean has(String key);
    }

    @LegacyType("net.minecraft.item.crafting.CraftingManager")
    public interface CraftingRegistry {
        @LegacyMethod(name = "func_77594_a", isStatic = true, descriptor = "()Lnet/minecraft/item/crafting/CraftingManager;") LegacyObject instance();
    }

    @LegacyType("net.minecraft.item.crafting.CraftingManager")
    public interface CraftingList {
        @LegacyMethod(name = "func_77592_b", descriptor = "()Ljava/util/List;") LegacyObject recipes();
    }

    @LegacyType("net.minecraft.item.crafting.IRecipe")
    public interface Recipe {
        @LegacyMethod(name = "func_77571_b", descriptor = "()Lnet/minecraft/item/ItemStack;") LegacyObject output();
    }

    @LegacyType("net.minecraft.item.crafting.ShapedRecipes")
    public interface ShapedVanilla {
        @LegacyField(name = "field_77576_b", descriptor = "I") int width();
        @LegacyField(name = "field_77577_c", descriptor = "I") int height();
        @LegacyField(name = "field_77574_d", descriptor = "[Lnet/minecraft/item/ItemStack;") LegacyObject[] inputs();
    }

    @LegacyType("net.minecraft.item.crafting.ShapelessRecipes")
    public interface ShapelessVanilla {
        @LegacyField(name = "field_77579_b", descriptor = "Ljava/util/List;") LegacyObject inputs();
    }

    @LegacyType("net.minecraftforge.oredict.ShapedOreRecipe")
    public interface ShapedOre {
        @LegacyField(name = "width", descriptor = "I") int width();
        @LegacyField(name = "height", descriptor = "I") int height();
        /** ItemStacks or ore lists ({@code ArrayList<ItemStack>}). */
        @LegacyField(name = "input", descriptor = "[Ljava/lang/Object;") LegacyObject[] inputs();
    }

    @LegacyType("net.minecraftforge.oredict.ShapelessOreRecipe")
    public interface ShapelessOre {
        @LegacyField(name = "input", descriptor = "Ljava/util/ArrayList;") LegacyObject inputs();
    }

    @LegacyType("net.minecraft.item.crafting.FurnaceRecipes")
    public interface Furnace {
        @LegacyMethod(name = "func_77602_a", isStatic = true, descriptor = "()Lnet/minecraft/item/crafting/FurnaceRecipes;") LegacyObject instance();
    }

    @LegacyType("net.minecraft.item.crafting.FurnaceRecipes")
    public interface FurnaceResults {
        @LegacyMethod(name = "func_151395_a", descriptor = "(Lnet/minecraft/item/ItemStack;)Lnet/minecraft/item/ItemStack;") LegacyObject result(LegacyObject input);
    }
}
