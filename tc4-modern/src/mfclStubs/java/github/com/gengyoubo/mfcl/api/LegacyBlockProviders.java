package github.com.gengyoubo.mfcl.api;

import github.com.gengyoubo.mfcl.api.spi.LegacyBlockProvider;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

/** Register native host block providers from the addon's constructor, before host block registration. */
public final class LegacyBlockProviders {
    private static final LinkedHashMap<String, LegacyBlockProvider> PROVIDERS = new LinkedHashMap<>();
    private LegacyBlockProviders() { }
    public static synchronized void register(LegacyBlockProvider provider) {
        Objects.requireNonNull(provider, "provider");
        PROVIDERS.putIfAbsent(provider.getClass().getName(), provider);
    }
    /** Loader snapshot; providers registered explicitly take precedence over a service-loaded instance of the same class. */
    public static synchronized List<LegacyBlockProvider> providers() { return List.copyOf(PROVIDERS.values()); }
}
