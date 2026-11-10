package github.com.gengyoubo.mfcl.api;

import github.com.gengyoubo.mfcl.api.spi.LegacyItemProvider;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

/** Register host item splits from the addon's constructor, before host item registration. */
public final class LegacyItemProviders {
    private static final LinkedHashMap<String, LegacyItemProvider> PROVIDERS = new LinkedHashMap<>();
    private LegacyItemProviders() { }
    public static synchronized void register(LegacyItemProvider provider) {
        Objects.requireNonNull(provider, "provider");
        PROVIDERS.putIfAbsent(provider.getClass().getName(), provider);
    }
    /** Loader snapshot; providers registered explicitly take precedence over a service-loaded instance of the same class. */
    public static synchronized List<LegacyItemProvider> providers() { return List.copyOf(PROVIDERS.values()); }
}
