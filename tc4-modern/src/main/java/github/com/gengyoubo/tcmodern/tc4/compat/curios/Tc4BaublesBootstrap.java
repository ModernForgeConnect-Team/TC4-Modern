package github.com.gengyoubo.tcmodern.tc4.compat.curios;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.jar.JarFile;

/** Installs only our own tiny legacy API when Curios replaces original Baubles. */
final class Tc4BaublesBootstrap {
    static final String FILE_NAME = "tc4modern-baubles-bridge-1.7.10.jar";
    static final String MARKER = "tc4modern-baubles-bridge.marker";
    private Tc4BaublesBootstrap() { }

    static void prepare(Path game, boolean curios) throws IOException {
        try (var input = Tc4BaublesBootstrap.class.getResourceAsStream("/tc4modern/legacy/" + FILE_NAME)) {
            if (input == null) throw new IOException("Missing embedded " + FILE_NAME);
            prepare(game, curios, input.readAllBytes());
        }
    }

    static void prepare(Path game, boolean curios, byte[] bridge) throws IOException {
        Path mods = game.resolve("mods"), legacy = mods.resolve("1.7.10"), target = legacy.resolve(FILE_NAME);
        boolean original = hasProvider(mods, target) || hasProvider(legacy, target);
        if (Files.exists(target) && !owned(target)) throw new IOException("Refusing to replace an unrecognized file: " + target);
        if (!curios || original) {
            // Only our marked, fixed-name generated file is ever removed. Original mods are untouched.
            if (Files.exists(target)) Files.delete(target);
            if (!curios && !original)
                throw new IOException("TC4 requires either modern Curios or original Baubles 1.0.1.10; neither is installed");
            return;
        }
        Files.createDirectories(legacy);
        if (Files.exists(target) && Arrays.equals(Files.readAllBytes(target), bridge)) return;
        Path temporary = Files.createTempFile(legacy, "tc4modern-baubles-", ".tmp");
        try {
            Files.write(temporary, bridge);
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temporary); }
        LogUtils.getLogger().info("TC4 Curios: installed independent Baubles API compatibility provider at {} (original Baubles not required).", target);
    }

    private static boolean hasProvider(Path directory, Path target) throws IOException {
        if (!Files.isDirectory(directory)) return false;
        try (var files = Files.list(directory)) {
            for (Path file : files.filter(Files::isRegularFile).filter(path -> path.toString().endsWith(".jar")).toList()) {
                if (file.equals(target)) continue;
                try (var jar = new JarFile(file.toFile(), false)) {
                    if (jar.getEntry("baubles/api/IBauble.class") != null) return true;
                }
            }
        }
        return false;
    }

    private static boolean owned(Path file) throws IOException {
        try (var jar = new JarFile(file.toFile(), false)) { return jar.getEntry(MARKER) != null; }
    }
}
