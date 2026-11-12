package github.com.gengyoubo.tcmodern.lib.essentia;

import github.com.gengyoubo.tcmodern.api.essentia.AspectStorage;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;

/** Forge capability for modern aspect storage; providers own persistence, invalidation and synchronization. */
public final class AspectCapabilities {
    public static final Capability<AspectStorage> STORAGE = CapabilityManager.get(new CapabilityToken<>() {});
    private AspectCapabilities() { }
    public static void register(RegisterCapabilitiesEvent event) { event.register(AspectStorage.class); }
}
