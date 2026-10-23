package github.com.gengyoubo.tcmodern.lib;

import github.com.gengyoubo.tcmodern.lib.creative.LateCreativeVariants;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/** Forge container for the shared TC compatibility library and public API. */
@Mod(TcModernLib.MOD_ID)
public final class TcModernLib {
    public static final String MOD_ID = "tcmodernlib";
    public TcModernLib(FMLJavaModLoadingContext context) {
        github.com.gengyoubo.tcmodern.lib.scan.ModernScanning.install();
        LateCreativeVariants.install(context.getModEventBus());
    }
}
