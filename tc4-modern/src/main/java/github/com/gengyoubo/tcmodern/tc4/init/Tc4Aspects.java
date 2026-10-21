package github.com.gengyoubo.tcmodern.tc4.init;

import com.mojang.logging.LogUtils;
import github.com.gengyoubo.mfcl.api.LegacyRuntime;
import github.com.gengyoubo.tcmodern.api.aspect.TcAspects;
import github.com.gengyoubo.tcmodern.tc4.Tc4Modern;

/**
 * Compound aspects added by this mod. Names live in the {@code tc.aspect.<tag>} translations
 * (host lang files and legacy_lang/supplement); icons in assets/tc4modern/textures/aspects.
 */
public final class Tc4Aspects {
    private Tc4Aspects() { }

    private record Definition(String tag, int color, String first, String second) { }

    private static final Definition[] DEFINITIONS = {
            new Definition("antiquitas", 0x9C7B4B, "terra", "cognitio"),
            new Definition("modernitas", 0x3FA9F5, "cognitio", "permutatio"),
            new Definition("echo", 0xB79CE6, "sensus", "motus"),
    };

    /** Runs before anything snapshots the registry; one rejected aspect never blocks the others. */
    public static void install(LegacyRuntime runtime) {
        for (var definition : DEFINITIONS) {
            try {
                TcAspects.register(runtime, definition.tag(), definition.color(), definition.first(), definition.second(),
                        Tc4Modern.MOD_ID + ":textures/aspects/" + definition.tag() + ".png");
            } catch (RuntimeException rejected) {
                LogUtils.getLogger().error("TC4: Cannot register aspect {}", definition.tag(), rejected);
            }
        }
    }
}
