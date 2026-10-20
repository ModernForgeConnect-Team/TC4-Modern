package github.com.gengyoubo.tcmodern.tc4.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import github.com.gengyoubo.mfcl.api.MfclApi;
import github.com.gengyoubo.tcmodern.tc4.Tc4Modern;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;

/**
 * Derives the deepslate ore overlays from the installed Thaumcraft at runtime, so no Thaumcraft art is
 * redistributed: cinnabar and amber keep only the pixels TC4 painted over 1.7.10 stone, and the infused stones
 * use TC4's animated vein layer tinted with BlockCustomOreItem's colors. These are the only overlay textures.
 */
@Mod.EventBusSubscriber(modid = Tc4Modern.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class DeepslateOreTextures {
    private static final String BLOCKS = "assets/thaumcraft/textures/blocks/";
    private static final String[] INFUSED = {"air", "fire", "water", "earth", "order", "entropy"};
    private static final int[] COLORS = {0xFFFF7E, 0xFF3C01, 0x0090FF, 0x00A000, 0xEECCFF, 0x555577};
    private DeepslateOreTextures() { }

    @SubscribeEvent public static void findPacks(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES) return;
        Path directory;
        try { directory = generate(); }
        catch (IOException | RuntimeException failure) {
            LogUtils.getLogger().error("TC4: Modern could not derive deepslate ore textures from Thaumcraft", failure);
            return;
        }
        if (directory == null) {
            LogUtils.getLogger().error("TC4: Modern found no original Thaumcraft ore textures; deepslate ore overlays will be missing");
            return;
        }
        event.addRepositorySource(consumer -> {
            var pack = Pack.readMetaAndCreate("tc4modern_original_ores", Component.literal("TC4: Modern original ores"), true,
                    name -> new PathPackResources(name, directory, false), PackType.CLIENT_RESOURCES, Pack.Position.TOP, PackSource.BUILT_IN);
            if (pack != null) consumer.accept(pack);
        });
    }

    private static Path generate() throws IOException {
        var stone = MfclApi.legacyResource("assets/minecraft/textures/blocks/stone.png");
        var cinnabar = MfclApi.legacyResource(BLOCKS + "cinnibar.png");
        var amber = MfclApi.legacyResource(BLOCKS + "amberore.png");
        var veins = MfclApi.legacyResource(BLOCKS + "infusedore.png");
        if (stone.isEmpty() || cinnabar.isEmpty() || amber.isEmpty() || veins.isEmpty()) return null;
        Path directory = FMLPaths.GAMEDIR.get().resolve("tc4modern/generated-pack");
        Path textures = directory.resolve("assets/tc4modern/textures/block");
        Files.createDirectories(textures);
        Files.writeString(directory.resolve("pack.mcmeta"),
                "{\"pack\":{\"pack_format\":15,\"description\":\"Ore overlays derived from the installed Thaumcraft\"}}", StandardCharsets.UTF_8);
        try (var base = read(stone.get())) {
            overlay(base, cinnabar.get(), textures.resolve("deepslate_cinnabar_ore.png"));
            overlay(base, amber.get(), textures.resolve("deepslate_amber_bearing_stone.png"));
        }
        var animation = MfclApi.legacyResource(BLOCKS + "infusedore.png.mcmeta");
        try (var vein = read(veins.get())) {
            for (int index = 0; index < INFUSED.length; index++) {
                Path target = textures.resolve("deepslate_" + INFUSED[index] + "_infused_stone.png");
                try (var tinted = tint(vein, COLORS[index])) { tinted.writeToFile(target); }
                if (animation.isPresent()) Files.write(target.resolveSibling(target.getFileName() + ".mcmeta"), animation.get());
            }
        }
        return directory;
    }

    /** Keeps only the pixels the original ore texture changed relative to the 1.7.10 stone it was painted on. */
    private static void overlay(NativeImage stone, byte[] ore, Path target) throws IOException {
        try (var image = read(ore)) {
            if (image.getWidth() != stone.getWidth() || image.getHeight() != stone.getHeight())
                throw new IOException("Original ore and stone textures differ in size: " + target.getFileName());
            for (int x = 0; x < image.getWidth(); x++) for (int y = 0; y < image.getHeight(); y++)
                if (image.getPixelRGBA(x, y) == stone.getPixelRGBA(x, y)) image.setPixelRGBA(x, y, 0);
            image.writeToFile(target);
        }
    }

    /** NativeImage pixels are ABGR; the original renderer multiplies the vein layer by the aspect color. */
    private static NativeImage tint(NativeImage source, int rgb) {
        var result = new NativeImage(source.getWidth(), source.getHeight(), true);
        int r = rgb >> 16 & 255, g = rgb >> 8 & 255, b = rgb & 255;
        for (int x = 0; x < source.getWidth(); x++) for (int y = 0; y < source.getHeight(); y++) {
            int pixel = source.getPixelRGBA(x, y);
            result.setPixelRGBA(x, y, pixel & 0xFF000000 | ((pixel >> 16 & 255) * b / 255) << 16
                    | ((pixel >> 8 & 255) * g / 255) << 8 | (pixel & 255) * r / 255);
        }
        return result;
    }

    private static NativeImage read(byte[] png) throws IOException { return NativeImage.read(new ByteArrayInputStream(png)); }
}
