package github.com.gengyoubo.tcmodern.api.world;

import github.com.gengyoubo.mfcl.api.LegacyRuntime;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** Variants of the original Thaumcraft:blockCustomOre, by their 1.7.10 metadata. */
public enum OriginalOre {
    CINNABAR, AIR_INFUSED, FIRE_INFUSED, WATER_INFUSED, EARTH_INFUSED, ORDER_INFUSED, ENTROPY_INFUSED, AMBER;

    public static final String LEGACY_KEY = "Thaumcraft:blockCustomOre";
    private static final OriginalOre[] VALUES = values();

    /** Original block metadata, 0..7. */
    public int metadata() { return ordinal(); }

    public static OriginalOre byMetadata(int metadata) {
        if (metadata < 0 || metadata >= VALUES.length) throw new IllegalArgumentException("No original ore metadata " + metadata);
        return VALUES[metadata];
    }

    /**
     * Host values (for example BlockState) that MFCL mirrors for every variant. Call once the runtime is ready;
     * the results are plain host values that may be cached. Fails when a variant has no host representation;
     * MFCL builds before 0.2.3 throw UnsupportedOperationException.
     */
    public static <H> Map<OriginalOre, H> hostStates(LegacyRuntime runtime, Class<H> hostType) {
        Objects.requireNonNull(runtime, "runtime"); Objects.requireNonNull(hostType, "hostType");
        var states = new EnumMap<OriginalOre, H>(OriginalOre.class);
        for (var ore : VALUES) states.put(ore, runtime.hostBlock(LEGACY_KEY, ore.metadata(), hostType)
                .orElseThrow(() -> new IllegalStateException("MFCL has no host block for " + LEGACY_KEY + "@" + ore.metadata())));
        return states;
    }
}
