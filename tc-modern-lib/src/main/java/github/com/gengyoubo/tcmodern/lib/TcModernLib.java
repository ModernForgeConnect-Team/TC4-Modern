package github.com.gengyoubo.tcmodern.lib;

import net.minecraftforge.fml.common.Mod;

/** Forge container for the shared TC compatibility library and public API. */
@Mod(TcModernLib.MOD_ID)
public final class TcModernLib {
    public static final String MOD_ID = "tcmodernlib";
    public TcModernLib() { github.com.gengyoubo.tcmodern.lib.scan.ModernScanning.install(); }
}
