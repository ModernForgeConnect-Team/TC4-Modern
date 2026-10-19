package github.com.gengyoubo.tcmodern.tc4.compat.jei;

import github.com.gengyoubo.mfcl.api.LegacyMappings;
import github.com.gengyoubo.mfcl.api.LegacyRuntime;
import github.com.gengyoubo.mfcl.api.LegacyStack;
import github.com.gengyoubo.tcmodern.api.recipe.TcRecipe;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.world.item.ItemStack;

final class JeiRecipeConverter {
    private final LegacyRuntime runtime;
    private final Map<String, Set<LegacyStack>> variants = new HashMap<>();
    private final Map<LegacyStack, Optional<ItemStack>> converted = new HashMap<>();

    JeiRecipeConverter(LegacyRuntime runtime, Collection<ItemStack> knownStacks, Set<String> wildcardKeys) {
        this.runtime = runtime;
        if (wildcardKeys.isEmpty()) return;
        for (var stack : knownStacks) {
            try {
                runtime.mappings().toLegacy(LegacyMappings.ITEM_STACK, stack).ifPresent(old -> {
                    var value = runtime.snapshot(old);
                    if (wildcardKeys.contains(value.key()) && value.metadata() != 32767) {
                        variants.computeIfAbsent(value.key(), ignored -> new LinkedHashSet<>())
                                .add(new LegacyStack(value.key(), value.metadata(), 1, value.snbt()));
                    }
                });
            } catch (RuntimeException unsupportedHostStack) {
                // Modern-only items and host NBT may have no lossless legacy representation.
            }
        }
    }

    JeiTcRecipe convert(TcRecipe recipe) {
        if (recipe.aspects().size() > TcRecipeCategory.MAX_ASPECTS
                || recipe.kind() == TcRecipe.Kind.INFUSION && recipe.inputs().size() > TcRecipeCategory.MAX_COMPONENTS + 1)
            throw new IllegalArgumentException("Recipe exceeds the supported JEI display dimensions");
        var inputs = new ArrayList<List<ItemStack>>();
        for (var slot : recipe.inputs()) {
            var choices = new ArrayList<ItemStack>();
            for (var old : slot) {
                if (old.metadata() == 32767) {
                    for (var variant : variants.getOrDefault(old.key(), Set.of())) {
                        var expanded = new LegacyStack(old.key(), variant.metadata(), old.count(),
                                old.snbt().isEmpty() ? variant.snbt() : old.snbt());
                        map(expanded).ifPresent(choices::add);
                    }
                    // Wildcard durability still accepts a fresh tool, even if JEI lacks that variant.
                    var fresh = map(new LegacyStack(old.key(), 0, old.count(), old.snbt()));
                    fresh.filter(ItemStack::isDamageableItem).ifPresent(choices::add);
                } else map(old).ifPresent(choices::add);
            }
            if (!slot.isEmpty() && choices.isEmpty())
                throw new IllegalArgumentException("No modern representation for required ingredient " + slot);
            inputs.add(List.copyOf(choices));
        }
        var output = map(recipe.output()).orElseThrow(() ->
                new IllegalArgumentException("No modern representation for output " + recipe.output()));
        return new JeiTcRecipe(recipe, inputs, output);
    }

    Optional<ItemStack> map(LegacyStack value) {
        if (value.metadata() == 32767) return Optional.empty();
        return converted.computeIfAbsent(value, old -> {
            try {
                return runtime.mappings().toHost(LegacyMappings.ITEM_STACK, runtime.stack(old), ItemStack.class)
                        .filter(stack -> !stack.isEmpty()).map(ItemStack::copy);
            } catch (RuntimeException unsupported) {
                return Optional.empty();
            }
        }).map(ItemStack::copy);
    }
}
