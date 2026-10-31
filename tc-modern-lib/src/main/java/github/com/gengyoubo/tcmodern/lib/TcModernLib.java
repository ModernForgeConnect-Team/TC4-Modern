package github.com.gengyoubo.tcmodern.lib;

import github.com.gengyoubo.tcmodern.api.TcModernApi;
import github.com.gengyoubo.tcmodern.lib.book.BookNetwork;
import github.com.gengyoubo.tcmodern.lib.creative.LateCreativeVariants;
import github.com.gengyoubo.tcmodern.lib.json.RecipeLoader;
import github.com.gengyoubo.tcmodern.lib.json.ResearchLoader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/** Forge container for the shared TC compatibility library and public API. */
@Mod(TcModernLib.MOD_ID)
public final class TcModernLib {
    public static final String MOD_ID = "tcmodernlib";
    public TcModernLib(FMLJavaModLoadingContext context) {
        github.com.gengyoubo.tcmodern.lib.scan.ModernScanning.install();
        LateCreativeVariants.install(context.getModEventBus());
        BookNetwork.register();
        context.getModEventBus().addListener(this::loadComplete);
    }

    private void loadComplete(FMLLoadCompleteEvent event) {
        // After every whenReady callback (custom aspects included) and before JEI reads the recipe list.
        event.enqueueWork(() -> TcModernApi.runtime().ifPresent(runtime -> {
            RecipeLoader.load(runtime);
            // Research pages show recipes, so research follows them.
            ResearchLoader.load(runtime);
        }));
    }
}
