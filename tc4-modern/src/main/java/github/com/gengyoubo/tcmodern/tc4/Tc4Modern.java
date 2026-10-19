package github.com.gengyoubo.tcmodern.tc4;

import com.mojang.logging.LogUtils;
import github.com.gengyoubo.mfcl.api.MfclApi;
import github.com.gengyoubo.tcmodern.api.TcModernApi;
import github.com.gengyoubo.tcmodern.tc4.compat.curios.Tc4CuriosCompat;
import github.com.gengyoubo.tcmodern.tc4.content.Tc4ModernContent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/** Gameplay module. Shared runtime access belongs in tc-modern-lib. */
@Mod(Tc4Modern.MOD_ID)
public final class Tc4Modern {
    public static final String MOD_ID = "tc4modern";

    public Tc4Modern(FMLJavaModLoadingContext context) {
        var modBus = context.getModEventBus();
        Tc4ModernContent.register(modBus);
        modBus.addListener(this::commonSetup);
        modBus.addListener(this::loadComplete);
        TcModernApi.whenReady(runtime -> LogUtils.getLogger().info(
                "TC4: Modern connected to original Thaumcraft through MFCL (legacy {}).",
                runtime.sourceVersion()));
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        // Curios takes precedence over the original Baubles slots. Without Curios this mod does
        // not touch the legacy slot system at all; Curios classes are never loaded.
        if (!ModList.get().isLoaded("curios")) {
            LogUtils.getLogger().info("TC4: Modern did not detect Curios; leaving the original Baubles slot system unchanged.");
            return;
        }
        event.enqueueWork(Tc4CuriosCompat::install);
    }

    /**
     * Original Thaumcraft and Baubles are legacy mods hosted by MFCL: they are never part of the
     * modern mod list, so they cannot be declared in {@code mods.toml}. Enforce the prerequisite
     * once loading is complete, after MFCL publishes its runtime during common setup.
     */
    private void loadComplete(FMLLoadCompleteEvent event) {
        var runtime = MfclApi.runtime();
        if (runtime.isEmpty()) {
            throw new IllegalStateException("TC4: Modern requires Modern Forge Connect Loader with an original "
                    + "Thaumcraft legacy runtime, but none is available. Install MFCL and place "
                    + "Thaumcraft-1.7.10-4.2.3.5.jar with Baubles-1.7.10-1.0.1.10.jar in mods/1.7.10/.");
        }
        if (!runtime.get().loadedMods().contains(TcModernApi.LEGACY_MOD_ID)) {
            throw new IllegalStateException("TC4: Modern requires the original Thaumcraft 4 in the MFCL legacy "
                    + "runtime. Place Thaumcraft-1.7.10-4.2.3.5.jar in mods/1.7.10/.");
        }
    }
}
