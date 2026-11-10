package github.com.gengyoubo.tcmodern.tc4.mfclpatch;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Called from MFCL's {@code EmbeddedLegacyRuntime} by {@code EmbeddedLegacyRuntimeMixin}. */
public final class MfclPatchRuntime {
    private static final String LEGACY_JAR = "/tc4modern/mfclpatch/mfcl-legacy-runtime.jar";
    private MfclPatchRuntime() {}

    /**
     * MFCL 0.2.10's old-runtime helper JAR with the backported legacy classes, extracted exactly like the original:
     * into {@code mfcl/cache/1.7.10/helpers/<sha256>.jar}, so the patched and unpatched helpers never share a file.
     */
    public static Path legacyHelper(Path gameDirectory) throws IOException {
        byte[] bytes;
        try (InputStream in = MfclPatchPlugin.resource(LEGACY_JAR)) { bytes = in.readAllBytes(); }
        String hash;
        try { hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
        Path cache = gameDirectory.resolve("mfcl/cache/1.7.10/helpers");
        Files.createDirectories(cache);
        Path target = cache.resolve(hash + ".jar");
        if (!Files.exists(target)) Files.write(target, bytes, StandardOpenOption.CREATE_NEW);
        MfclPatchPlugin.LOGGER.info("MFCL backport: using patched legacy runtime helper {}", target.getFileName());
        return target;
    }
}
