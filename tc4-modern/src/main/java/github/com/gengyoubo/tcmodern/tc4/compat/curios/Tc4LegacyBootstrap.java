package github.com.gengyoubo.tcmodern.tc4.compat.curios;

import github.com.gengyoubo.mfcl.api.LegacyRedirects;
import github.com.gengyoubo.mfcl.api.spi.LegacyRedirectProvider;
import java.io.IOException;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;

/** MFCL's public pre-boot hook runs before discovery, unlike our own mod constructor. */
public final class Tc4LegacyBootstrap implements LegacyRedirectProvider {
    @Override public void register(LegacyRedirects redirects) {
        try {
            Tc4BaublesBootstrap.prepare(FMLPaths.GAMEDIR.get(), ModList.get().isLoaded("curios"));
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot prepare TC4's Curios/Baubles API compatibility provider", failure);
        }
    }
}
