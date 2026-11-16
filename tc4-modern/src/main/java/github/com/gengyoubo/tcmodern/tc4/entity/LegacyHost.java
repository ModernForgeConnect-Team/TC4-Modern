package github.com.gengyoubo.tcmodern.tc4.entity;

import com.mojang.logging.LogUtils;
import github.com.gengyoubo.mfcl.api.LegacyFeatures;
import github.com.gengyoubo.mfcl.api.LegacyRuntime;
import github.com.gengyoubo.tcmodern.api.TcModernApi;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;

/**
 * The TC4 original bound to a modern host entity. The modern entity runs all gameplay; the original only follows it,
 * so that old code which looks entities up (warp events, champion modifiers, other addons) still finds it, and it is
 * saved with the host and restored after load.
 */
public final class LegacyHost {
    private final String legacyClass, tagKey;
    private byte[] pending;
    private boolean failed;

    public LegacyHost(String legacyClass) {
        this.legacyClass = legacyClass;
        tagKey = "Tc4Legacy" + legacyClass.substring(legacyClass.lastIndexOf('.') + 1);
    }

    public String legacyClass() { return legacyClass; }

    private static LegacyRuntime runtime() {
        var runtime = TcModernApi.runtime().orElse(null);
        return runtime != null && runtime.supports(LegacyFeatures.ENTITY_HOSTS) ? runtime : null;
    }

    public void tick(Entity host) {
        if (host.level().isClientSide || failed) return;
        var runtime = runtime();
        if (runtime == null) return;
        try {
            var hosts = runtime.entityHosts();
            if (pending != null) { byte[] data = pending; pending = null; hosts.restore(host, legacyClass, data); }
            if (hosts.original(host).isPresent()) hosts.sync(host);
        } catch (RuntimeException failure) {
            // A broken original must never stop the modern entity from working.
            failed = true;
            LogUtils.getLogger().warn("TC4 original {} could not follow its modern host", legacyClass, failure);
        }
    }

    public void save(Entity host, CompoundTag tag) {
        if (pending != null) { tag.putByteArray(tagKey, pending); return; }
        if (host.level().isClientSide || failed) return;
        var runtime = runtime();
        if (runtime == null) return;
        try {
            var hosts = runtime.entityHosts();
            if (hosts.original(host).isPresent()) tag.putByteArray(tagKey, hosts.save(host));
        } catch (RuntimeException failure) {
            LogUtils.getLogger().warn("TC4 original {} could not be saved with its modern host", legacyClass, failure);
        }
    }

    public void load(CompoundTag tag) { pending = tag.contains(tagKey, 7) ? tag.getByteArray(tagKey) : null; }
}
