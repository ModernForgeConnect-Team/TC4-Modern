package github.com.gengyoubo.tcmodern.lib.jei;

import github.com.gengyoubo.tcmodern.api.recipe.TcRecipe;
import java.util.List;
import net.minecraft.world.item.ItemStack;

/** Host stacks are copies; no legacy runtime or opaque object is retained by JEI. */
public record JeiTcRecipe(TcRecipe source, List<List<ItemStack>> inputs, ItemStack output) {
    public JeiTcRecipe {
        inputs = inputs.stream().map(List::copyOf).toList();
    }
}
