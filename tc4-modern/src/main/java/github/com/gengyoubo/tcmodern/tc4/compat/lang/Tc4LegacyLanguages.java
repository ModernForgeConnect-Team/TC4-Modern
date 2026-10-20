package github.com.gengyoubo.tcmodern.tc4.compat.lang;

import com.mojang.logging.LogUtils;
import github.com.gengyoubo.mfcl.api.LegacyLanguages;
import github.com.gengyoubo.mfcl.api.LegacyRuntime;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Fixes the original Thaumcraft language files. {@code supplement/} fills untranslated keys and never
 * replaces an original translation; {@code override/} corrects original translations that are wrong.
 */
public final class Tc4LegacyLanguages {
    private static final List<String> LANGUAGES = List.of("zh_CN");
    private Tc4LegacyLanguages() { }

    private interface Apply { void to(LegacyLanguages languages, String language, InputStream in) throws IOException; }

    public static void install(LegacyRuntime runtime) {
        var languages = runtime.languages();
        for (String language : LANGUAGES) {
            load(languages, "supplement", language, LegacyLanguages::supplement);
            load(languages, "override", language, LegacyLanguages::override);
        }
    }

    private static void load(LegacyLanguages languages, String kind, String language, Apply apply) {
        String path = "/assets/tc4modern/legacy_lang/" + kind + "/" + language + ".lang";
        try (var in = Tc4LegacyLanguages.class.getResourceAsStream(path)) {
            if (in != null) apply.to(languages, language, in);
        } catch (IOException failure) {
            LogUtils.getLogger().warn("TC4: Cannot read legacy language {}", path, failure);
        }
    }
}
