package github.com.gengyoubo.tcmodern.api.recipe;

import github.com.gengyoubo.mfcl.api.LegacyStack;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/** Detached display data, never an executable modern crafting recipe. Empty shaped slots are retained. */
public record TcRecipe(Kind kind, String research, int width, int height,
                       List<List<LegacyStack>> inputs, LegacyStack output,
                       List<AspectCost> aspects, int instability) {
    public enum Kind { ARCANE_SHAPED, ARCANE_SHAPELESS, CRUCIBLE, INFUSION }
    public record AspectCost(String tag, int color, int amount, String texture) {
        public AspectCost(String tag, int color, int amount) { this(tag, color, amount, ""); }
        public AspectCost {
            Objects.requireNonNull(tag);
            texture = Objects.requireNonNullElse(texture, "");
            if (tag.isBlank() || amount <= 0) throw new IllegalArgumentException("Invalid aspect cost");
        }
    }

    public TcRecipe {
        Objects.requireNonNull(kind);
        Objects.requireNonNull(output);
        research = Objects.requireNonNullElse(research, "");
        inputs = inputs.stream().map(List::copyOf).toList();
        aspects = aspects.stream().sorted(java.util.Comparator.comparing(AspectCost::tag)).toList();
        if (width < 1 || height < 1 || inputs.size() != width * height || instability < 0)
            throw new IllegalArgumentException("Invalid recipe dimensions or instability");
        if (kind == Kind.ARCANE_SHAPED) {
            if (width > 3 || height > 3) throw new IllegalArgumentException("Arcane grid exceeds 3x3");
        } else if (inputs.stream().anyMatch(List::isEmpty)) {
            throw new IllegalArgumentException("Missing required ingredient");
        }
        if (inputs.stream().allMatch(List::isEmpty)) throw new IllegalArgumentException("Recipe has no inputs");
    }

    /** Content-derived ID stays stable if the upstream list is reordered. */
    public String id() {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(toString().getBytes(StandardCharsets.UTF_8));
            return "legacy/" + kind.name().toLowerCase(java.util.Locale.ROOT) + "/" + HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
