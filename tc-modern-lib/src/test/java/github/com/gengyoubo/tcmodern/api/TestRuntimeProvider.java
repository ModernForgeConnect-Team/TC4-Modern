package github.com.gengyoubo.tcmodern.api;

import github.com.gengyoubo.mfcl.api.LegacyRuntime;
import github.com.gengyoubo.mfcl.api.spi.RuntimeProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/** In-memory provider to exercise the real MFCL ServiceLoader entry point. */
public final class TestRuntimeProvider implements RuntimeProvider {
    static LegacyRuntime runtime;
    static final List<Consumer<LegacyRuntime>> listeners = new ArrayList<>();

    static void reset() {
        runtime = null;
        listeners.clear();
    }

    static void publish(LegacyRuntime value) {
        runtime = value;
        var pending = List.copyOf(listeners);
        listeners.clear();
        pending.forEach(listener -> listener.accept(value));
    }

    @Override
    public Optional<LegacyRuntime> current() {
        return Optional.ofNullable(runtime);
    }

    @Override
    public AutoCloseable whenReady(Consumer<LegacyRuntime> listener) {
        if (runtime != null) listener.accept(runtime);
        else listeners.add(listener);
        return () -> listeners.remove(listener);
    }
}
