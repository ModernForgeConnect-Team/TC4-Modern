package github.com.gengyoubo.tcmodern.tc4.client.arcane;

import github.com.gengyoubo.mfcl.api.LegacyObject;
import github.com.gengyoubo.mfcl.api.mla.*;

/** TC4 4.2.3.5 arcane worktable contracts. No legacy classes enter the modern classpath. */
public final class ArcaneWorkbenchAccessors {
    private ArcaneWorkbenchAccessors() {}
    public static final String WAND="thaumcraft.common.items.wands.ItemWandCasting";
    @LegacyType("thaumcraft.client.gui.GuiArcaneWorkbench") public interface Gui {
        @LegacyField(name="tileEntity",descriptor="Lthaumcraft/common/tiles/TileArcaneWorkbench;") LegacyObject tile();
        @LegacyField(name="ip",descriptor="Lnet/minecraft/entity/player/InventoryPlayer;") LegacyObject inventory();
    }
    @LegacyType("net.minecraft.entity.player.InventoryPlayer") public interface PlayerInventory {
        @LegacyField(name="field_70458_d",descriptor="Lnet/minecraft/entity/player/EntityPlayer;") LegacyObject player();
    }
    @LegacyType("thaumcraft.common.tiles.TileArcaneWorkbench") public interface Table {
        @LegacyMethod(name="func_70301_a",descriptor="(I)Lnet/minecraft/item/ItemStack;") LegacyObject stack(int slot);
    }
    @LegacyType("net.minecraft.item.ItemStack") public interface Holder {
        @LegacyMethod(name="func_77973_b",descriptor="()Lnet/minecraft/item/Item;") LegacyObject item();
    }
    @LegacyType("thaumcraft.common.lib.crafting.ThaumcraftCraftingManager") public interface Crafting {
        @LegacyMethod(name="findMatchingArcaneRecipe",isStatic=true,descriptor="(Lnet/minecraft/inventory/IInventory;Lnet/minecraft/entity/player/EntityPlayer;)Lnet/minecraft/item/ItemStack;")
        LegacyObject result(LegacyObject table,LegacyObject player);
        @LegacyMethod(name="findMatchingArcaneRecipeAspects",isStatic=true,descriptor="(Lnet/minecraft/inventory/IInventory;Lnet/minecraft/entity/player/EntityPlayer;)Lthaumcraft/api/aspects/AspectList;")
        LegacyObject cost(LegacyObject table,LegacyObject player);
    }
    @LegacyType("thaumcraft.api.aspects.Aspect") public interface Primals {
        /** Registration order: aer, terra, ignis, aqua, ordo, perditio. */
        @LegacyMethod(name="getPrimalAspects",isStatic=true,descriptor="()Ljava/util/ArrayList;") LegacyObject primals();
    }
    @LegacyType(WAND) public interface Wand {
        @LegacyMethod(name="getConsumptionModifier",descriptor="(Lnet/minecraft/item/ItemStack;Lnet/minecraft/entity/player/EntityPlayer;Lthaumcraft/api/aspects/Aspect;Z)F")
        float modifier(LegacyObject stack,LegacyObject player,LegacyObject aspect,boolean crafting);
        @LegacyMethod(name="getVis",descriptor="(Lnet/minecraft/item/ItemStack;Lthaumcraft/api/aspects/Aspect;)I") int vis(LegacyObject stack,LegacyObject aspect);
        /** With {@code consume} false this only tests the stored vis, exactly as the original GUI does. */
        @LegacyMethod(name="consumeAllVisCrafting",descriptor="(Lnet/minecraft/item/ItemStack;Lnet/minecraft/entity/player/EntityPlayer;Lthaumcraft/api/aspects/AspectList;Z)Z")
        boolean covers(LegacyObject stack,LegacyObject player,LegacyObject cost,boolean consume);
        @LegacyMethod(name="isStaff",descriptor="(Lnet/minecraft/item/ItemStack;)Z") boolean staff(LegacyObject stack);
    }
}
