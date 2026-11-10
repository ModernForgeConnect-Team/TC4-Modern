package github.com.gengyoubo.tcmodern.tc4.compat;

import github.com.gengyoubo.mfcl.api.LegacyClientScreens;
import github.com.gengyoubo.mfcl.api.LegacyRuntime;
import org.slf4j.Logger;

/**
 * Temporary bridge for MFCL 0.2.10, which lacks the native block/item provider and client-screen APIs.
 * Only this class links those types, and only after checking they exist, so older MFCL keeps the hosted
 * original blocks, items and GUIs. Remove once the CurseForge MFCL release ships these APIs.
 */
public final class MfclCompat {
    private static final Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private MfclCompat() {}

    /** True when the installed MFCL has native block and item providers (added after 0.2.10). */
    public static boolean hasNativeProviders() {
        return present("github.com.gengyoubo.mfcl.api.LegacyBlockProviders")
                && present("github.com.gengyoubo.mfcl.api.LegacyItemProviders");
    }

    /** Registers the native magical leaves and the item splits, or keeps the original host forms on older MFCL. */
    public static void registerNativeProviders() {
        if (!hasNativeProviders()) {
            LOGGER.warn("Installed MFCL has no native block/item providers; TC4 leaves and item subtypes keep their hosted original forms");
            return;
        }
        Providers.register();
    }

    /** The runtime's client-screen registry. Callers check {@code runtime.supports("client-screens")} first. */
    public static LegacyClientScreens clientScreens(LegacyRuntime runtime) {
        try {
            return (LegacyClientScreens) runtime.getClass().getMethod("clientScreens").invoke(runtime);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("MFCL reports client-screens support without LegacyRuntime.clientScreens()", e);
        }
    }

    private static boolean present(String name) {
        try {
            Class.forName(name, false, MfclCompat.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    /** Loaded only after the provider registries were found. */
    private static final class Providers {
        static void register() {
            github.com.gengyoubo.mfcl.api.LegacyBlockProviders.register(new Tc4NativeBlocks());
            github.com.gengyoubo.mfcl.api.LegacyItemProviders.register(new Tc4ItemSplits());
        }
    }
}
