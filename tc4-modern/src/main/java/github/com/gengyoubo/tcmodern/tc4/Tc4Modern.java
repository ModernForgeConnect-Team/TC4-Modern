package github.com.gengyoubo.tcmodern.tc4;

import com.mojang.logging.LogUtils;
import github.com.gengyoubo.tcmodern.api.TcModernApi;
import net.minecraftforge.fml.common.Mod;

/** Gameplay module. Shared runtime access belongs in tc-modern-lib. */
@Mod(Tc4Modern.MOD_ID)
public final class Tc4Modern {
    public static final String MOD_ID = "tc4modern";

    public Tc4Modern() {
        TcModernApi.whenReady(runtime -> LogUtils.getLogger().info(
                "TC4: Modern connected to original Thaumcraft through MFCL (legacy {}).",
                runtime.sourceVersion()));
    }
}
