package github.com.gengyoubo.tcmodern.tc4.compat.curios;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.JarInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Tc4BaublesBootstrapTest {
    @TempDir Path game;
    private Path generated() { return game.resolve("mods/1.7.10/" + Tc4BaublesBootstrap.FILE_NAME); }
    private static byte[] jar(String entry) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var jar = new JarOutputStream(bytes)) { jar.putNextEntry(new JarEntry(entry)); jar.closeEntry(); }
        return bytes.toByteArray();
    }
    @Test void curiosAloneInstallsAndReusesEmbeddedProvider() throws Exception {
        Tc4BaublesBootstrap.prepare(game, true);
        byte[] bytes = Files.readAllBytes(generated());
        Tc4BaublesBootstrap.prepare(game, true);
        assertArrayEquals(bytes, Files.readAllBytes(generated()));
        var names = new java.util.HashSet<String>();
        try (var jar = new JarInputStream(new java.io.ByteArrayInputStream(bytes))) {
            JarEntry entry;
            while ((entry = jar.getNextJarEntry()) != null) names.add(entry.getName());
        }
        assertTrue(names.contains("baubles/api/IBauble.class"));
        assertTrue(names.contains(Tc4BaublesBootstrap.MARKER));
        assertFalse(names.stream().anyMatch(name -> name.startsWith("net/") || name.startsWith("cpw/")));
    }
    @Test void originalInEitherDirectoryWinsAndGeneratedProviderIsRemoved() throws Exception {
        for (String directory : new String[]{"mods", "mods/1.7.10"}) {
            Tc4BaublesBootstrap.prepare(game, true);
            Path original = game.resolve(directory).resolve("renamed-original.jar");
            Files.createDirectories(original.getParent());
            byte[] originalBytes = jar("baubles/api/IBauble.class");
            Files.write(original, originalBytes);
            Tc4BaublesBootstrap.prepare(game, true);
            assertFalse(Files.exists(generated()));
            Tc4BaublesBootstrap.prepare(game, false);
            assertArrayEquals(originalBytes, Files.readAllBytes(original));
            Files.delete(original);
        }
    }
    @Test void removingCuriosRemovesOnlyGeneratedProviderAndRequiresOriginal() throws Exception {
        Tc4BaublesBootstrap.prepare(game, true);
        IOException error = assertThrows(IOException.class, () -> Tc4BaublesBootstrap.prepare(game, false));
        assertTrue(error.getMessage().contains("neither is installed"));
        assertFalse(Files.exists(generated()));
    }
    @Test void unrecognizedTargetIsNeverOverwrittenOrDeleted() throws Exception {
        Files.createDirectories(generated().getParent());
        byte[] foreign = jar("unrelated.txt");
        Files.write(generated(), foreign);
        assertThrows(IOException.class, () -> Tc4BaublesBootstrap.prepare(game, true));
        assertThrows(IOException.class, () -> Tc4BaublesBootstrap.prepare(game, false));
        assertArrayEquals(foreign, Files.readAllBytes(generated()));
    }
}
