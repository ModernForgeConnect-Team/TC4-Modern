package github.com.gengyoubo.tcmodern.tc4.compat.jei;

import github.com.gengyoubo.mfcl.api.LegacyMappings;
import github.com.gengyoubo.mfcl.api.LegacyStack;
import github.com.gengyoubo.tcmodern.api.TcModernApi;
import github.com.gengyoubo.tcmodern.api.recipe.TcRecipe;
import github.com.gengyoubo.tcmodern.tc4.client.arcane.ArcaneWands;
import github.com.gengyoubo.tcmodern.tc4.client.arcane.ArcaneWorkbenchGui;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

/**
 * Fills the original arcane worktable from JEI with ordinary slot clicks, so the original container moves every
 * item and checks it on the server; no JEI server packet is involved. A wand slot that cannot pay the recipe's vis
 * is swapped for the first inventory wand that can, judged by the original ItemWandCasting. Without one it is
 * left as it is.
 */
final class ArcaneTransferHandler implements IRecipeTransferHandler<AbstractContainerMenu, JeiTcRecipe> {
    private final Class<? extends AbstractContainerMenu> menu;
    private final IRecipeTransferHandlerHelper helper;

    ArcaneTransferHandler(Class<? extends AbstractContainerMenu> menu, IRecipeTransferHandlerHelper helper) {
        this.menu = menu;
        this.helper = helper;
    }

    @Override public Class<? extends AbstractContainerMenu> getContainerClass() { return menu; }
    @Override public Optional<MenuType<AbstractContainerMenu>> getMenuType() { return Optional.empty(); }
    @Override public RecipeType<JeiTcRecipe> getRecipeType() { return Tc4JeiPlugin.ARCANE; }

    @Override
    public IRecipeTransferError transferRecipe(AbstractContainerMenu container, JeiTcRecipe recipe, IRecipeSlotsView slots,
                                               Player player, boolean maxTransfer, boolean doTransfer) {
        // Other original devices share the hosted menu class.
        if (!ArcaneWorkbenchGui.isWorkbench(container) || container.slots.size() != ArcaneTransferPlan.SLOTS
                || !container.getCarried().isEmpty()) return helper.createInternalError();
        var source = recipe.source();
        var stacks = new ArrayList<ArcaneTransferPlan.Stack>();
        var byId = new HashMap<String, ItemStack>();
        for (var slot : container.slots) {
            var stack = slot.getItem();
            if (stack.isEmpty()) {
                stacks.add(null);
                continue;
            }
            String id = id(stack);
            byId.putIfAbsent(id, stack);
            stacks.add(new ArcaneTransferPlan.Stack(id, stack.getCount(), stack.getMaxStackSize()));
        }
        var snapshots = new HashMap<String, Optional<LegacyStack>>();
        var grid = new ArrayList<Predicate<String>>(Collections.nCopies(9, null));
        var recipeIndex = new int[9];
        for (int i = 0; i < source.inputs().size(); i++) {
            if (source.inputs().get(i).isEmpty()) continue;
            int position = source.kind() == TcRecipe.Kind.ARCANE_SHAPED ? i / source.width() * 3 + i % source.width() : i;
            var original = source.inputs().get(i);
            var shown = recipe.inputs().get(i);
            grid.set(position, id -> matches(original, shown, byId.get(id), snapshots.computeIfAbsent(id, key -> snapshot(byId.get(key)))));
            recipeIndex[position] = i;
        }
        int wand = -1;
        if (doTransfer && !source.aspects().isEmpty()) {
            var cost = new LinkedHashMap<String, Integer>();
            for (var aspect : source.aspects()) cost.put(aspect.tag(), aspect.amount());
            var candidates = new ArrayList<ItemStack>();
            candidates.add(container.getSlot(ArcaneTransferPlan.WAND).getItem());
            for (int i = ArcaneTransferPlan.INVENTORY; i < ArcaneTransferPlan.SLOTS; i++) candidates.add(container.getSlot(i).getItem());
            int found = ArcaneWands.firstCovering(candidates, cost);
            if (found > 0) wand = ArcaneTransferPlan.INVENTORY + found - 1;
        }
        var plan = ArcaneTransferPlan.plan(stacks, grid, wand, maxTransfer);
        if (plan.full())
            return helper.createUserErrorWithTooltip(Component.translatable("jei.tooltip.error.recipe.transfer.inventory.full"));
        if (!plan.missing().isEmpty())
            return helper.createUserErrorForMissingSlots(Component.translatable("jei.tooltip.error.recipe.transfer.missing"),
                    missingViews(slots, source, plan.missing(), recipeIndex));
        if (doTransfer) {
            var mode = Minecraft.getInstance().gameMode;
            if (mode == null) return helper.createInternalError();
            for (var click : plan.clicks())
                mode.handleInventoryMouseClick(container.containerId, click.slot(), click.button(),
                        click.quickMove() ? ClickType.QUICK_MOVE : ClickType.PICKUP, player);
        }
        return null;
    }

    /** Item input views follow the recipe's non-empty inputs in order; aspect cost views come after them. */
    private static List<IRecipeSlotView> missingViews(IRecipeSlotsView slots, TcRecipe source, List<Integer> missing, int[] recipeIndex) {
        var views = slots.getSlotViews(RecipeIngredientRole.INPUT);
        var result = new ArrayList<IRecipeSlotView>();
        for (int position : missing) {
            int index = recipeIndex[position], view = 0;
            for (int i = 0; i < index; i++) if (!source.inputs().get(i).isEmpty()) view++;
            if (view < views.size()) result.add(views.get(view));
        }
        return result;
    }

    /** The original ShapedArcaneRecipe test: same item, wildcard or equal damage, recipe NBT only when it has some. */
    private static boolean matches(List<LegacyStack> original, List<ItemStack> shown, ItemStack stack, Optional<LegacyStack> old) {
        if (stack == null) return false;
        for (var choice : shown) if (ItemStack.isSameItemSameTags(choice, stack)) return true;
        if (old.isEmpty()) return false;
        var value = old.get();
        for (var choice : original)
            if (choice.key().equals(value.key()) && (choice.metadata() == 32767 || choice.metadata() == value.metadata())
                    && (choice.snbt().isEmpty() || choice.snbt().equals(value.snbt()))) return true;
        return false;
    }

    private static Optional<LegacyStack> snapshot(ItemStack stack) {
        return TcModernApi.runtime().flatMap(runtime -> {
            try {
                return runtime.mappings().toLegacy(LegacyMappings.ITEM_STACK, stack.copyWithCount(1)).map(runtime::snapshot);
            } catch (RuntimeException unsupported) {
                return Optional.empty();
            }
        });
    }

    private static String id(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()) + (stack.hasTag() ? stack.getTag().toString() : "");
    }
}
