package github.com.gengyoubo.tcmodern.tc4;

import com.mojang.logging.LogUtils;
import github.com.gengyoubo.tcmodern.api.TcModernApi;
import github.com.gengyoubo.tcmodern.tc4.compat.curios.Tc4CuriosCompat;
import github.com.gengyoubo.tcmodern.tc4.compat.lang.Tc4LegacyLanguages;
import github.com.gengyoubo.tcmodern.tc4.compat.scan.Tc4ScanDefaults;
import github.com.gengyoubo.tcmodern.tc4.init.Tc4Aspects;
import github.com.gengyoubo.tcmodern.tc4.init.Tc4Blocks;
import github.com.gengyoubo.tcmodern.tc4.init.Tc4CreativeTabs;
import github.com.gengyoubo.tcmodern.tc4.init.Tc4Features;
import github.com.gengyoubo.tcmodern.tc4.init.Tc4Items;
import github.com.gengyoubo.tcmodern.tc4.init.Tc4RecipeSerializers;
import github.com.gengyoubo.tcmodern.tc4.world.Tc4ModernWorldgen;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/** Gameplay module. Shared runtime access belongs in tc-modern-lib. */
@Mod(Tc4Modern.MOD_ID)
public final class Tc4Modern {
    public static final String MOD_ID = "tc4modern";

    public Tc4Modern(FMLJavaModLoadingContext context) {
        var modBus = context.getModEventBus();
        Tc4Blocks.register(modBus);
        Tc4Items.register(modBus);
        Tc4CreativeTabs.register(modBus);
        Tc4RecipeSerializers.register(modBus);
        Tc4Features.register(modBus);
        modBus.addListener(this::commonSetup);
        // Aspects first: everything after may read or assign them.
        TcModernApi.whenReady(Tc4Aspects::install);
        TcModernApi.whenReady(Tc4ScanDefaults::install);
        TcModernApi.whenReady(Tc4ModernWorldgen::install);
        TcModernApi.whenReady(Tc4LegacyLanguages::install);
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
}
