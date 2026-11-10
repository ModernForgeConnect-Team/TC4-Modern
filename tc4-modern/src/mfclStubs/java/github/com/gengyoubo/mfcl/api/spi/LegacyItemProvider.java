package github.com.gengyoubo.mfcl.api.spi;

import github.com.gengyoubo.mfcl.api.LegacyStack;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Optional split of one original item into several host items. Register through LegacyItemProviders or services.
 * Called during host item registration, after original declarations are available and before any original item
 * registers. Every split item is the same original item to old code: same key, metadata, count and complete NBT.
 * Host objects only; no original runtime objects cross this interface. Unhandled keys return null.
 */
public interface LegacyItemProvider {
    ItemSplit split(String legacyKey, ItemDeclaration declaration);

    /** Read-only facts about the original item declaration. */
    interface ItemDeclaration {
        /** Original subtypes found while capturing declarations, in ascending order. */
        List<Integer> metadata();
        /** Positive for original damageable items, whose metadata is durability damage. */
        int maxDamage();
        /** Original creative listing of this item, with NBT; empty when the original lists none. */
        List<LegacyStack> catalog();
    }

    interface ItemSplit {
        /**
         * Stable variant names. Each registers host item {@code <namespace>:<path>/<variant>} beside the unchanged
         * original host ID. Lowercase {@code [a-z0-9_.-]}; renaming or removing one orphans saved stacks of it.
         */
        List<String> variants();
        /** One of {@link #variants()}, or null to keep the stack on the original host item. Must be deterministic. */
        String select(Stack stack);
    }

    /** An original stack being given a host form. */
    interface Stack {
        int metadata();
        /** Original 1.7.10 SNBT, empty without NBT. */
        String snbt();
        /** A host CompoundTag copy of the original NBT, or null; converted on first call. */
        Object hostTag();
    }

    /** One variant per declared subtype, named {@code meta_<n>}; other metadata stays on the original host item. */
    static ItemSplit byMetadata(ItemDeclaration declaration) {
        if (declaration.maxDamage() > 0) throw new IllegalArgumentException("Damageable items keep durability in metadata");
        var names = new LinkedHashMap<Integer, String>();
        for (int metadata : declaration.metadata()) names.put(metadata, "meta_" + metadata);
        return byMetadata(names);
    }

    /** Explicitly named subtypes; unlisted metadata stays on the original host item. */
    static ItemSplit byMetadata(Map<Integer, String> names) {
        var copy = Map.copyOf(names);
        var variants = List.copyOf(new LinkedHashMap<>(names).values());
        return new ItemSplit() {
            @Override public List<String> variants() { return variants; }
            @Override public String select(Stack stack) { return copy.get(stack.metadata()); }
        };
    }
}
