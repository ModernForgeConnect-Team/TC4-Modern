package github.com.gengyoubo.tcmodern.api;

import github.com.gengyoubo.mfcl.api.LegacyRuntime;
import github.com.gengyoubo.mfcl.api.MfclApi;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/** Public entry point for the original TC4 runtime hosted by MFCL. */
public final class TcModernApi {
    public static final int API_VERSION = 1;
    public static final String LEGACY_MOD_ID = "Thaumcraft";

    private TcModernApi() { }

    /** Queries MFCL each time so a closed runtime is never retained here. */
    public static Optional<LegacyRuntime> runtime() {
        return MfclApi.runtime().filter(TcModernApi::supportsTc4);
    }

    /**
     * Called once if MFCL becomes ready with TC4 loaded, or synchronously if already ready.
     * Closing cancels a pending callback. Readiness does not imply a world/player exists;
     * gameplay calls still belong on the owning game thread.
     */
    public static AutoCloseable whenReady(Consumer<LegacyRuntime> listener) {
        Objects.requireNonNull(listener, "listener");
        return MfclApi.whenReady(runtime -> {
            if (supportsTc4(runtime)) listener.accept(runtime);
        });
    }

    private static boolean supportsTc4(LegacyRuntime runtime) {
        return runtime.isOpen() && runtime.loadedMods().contains(LEGACY_MOD_ID);
    }
}
