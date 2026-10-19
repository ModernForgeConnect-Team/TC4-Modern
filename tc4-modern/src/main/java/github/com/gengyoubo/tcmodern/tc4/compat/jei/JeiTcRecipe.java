package github.com.gengyoubo.tcmodern.tc4.compat.jei;

import github.com.gengyoubo.tcmodern.api.recipe.TcRecipe;
import java.util.List;
import net.minecraft.world.item.ItemStack;

/** Host stacks are copies; no legacy runtime or opaque object is retained by JEI. */
record JeiTcRecipe(TcRecipe source, List<List<ItemStack>> inputs, ItemStack output) {
    JeiTcRecipe {
        inputs = inputs.stream().map(List::copyOf).toList();
    }
}
