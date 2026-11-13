package github.com.gengyoubo.tcmodern.api;

import github.com.gengyoubo.mfcl.api.*;
import github.com.gengyoubo.tcmodern.lib.wand.TcWandCapacityRedirect;
import java.lang.reflect.Proxy;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TcWandCapacityRedirectTest {
    record Handle(String typeName) implements LegacyObject { }
    @Test void chargingHonorsCapacitySimulationAndUnits() throws Throwable {
        var stack = new Handle("net.minecraft.item.ItemStack");
        var aspect = new Handle("thaumcraft.api.aspects.Aspect");
        var tag = new Handle("net.minecraft.nbt.NBTTagCompound");
        var wand = new Handle("thaumcraft.common.items.wands.ItemWandCasting");
        int[] stored = {150}; boolean[] marked = {true};
        var runtime = (LegacyRuntime) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{LegacyRuntime.class}, (p,m,a) -> switch(m.getName()) {
            case "isOpen" -> true; case "loadedMods" -> Set.of("Thaumcraft");
            case "accessor" -> {
                Class<?> contract = (Class<?>) a[0];
                yield Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{contract}, (q, method, args) -> switch(method.getName()) {
                    case "primal" -> true; case "tag" -> contract == TcWandCapacityRedirect.Stack.class ? tag : "aer";
                    case "contains" -> marked[0]; case "getInt" -> 250; case "vis" -> stored[0];
                    case "store" -> { stored[0] = (int) args[2]; yield null; }
                    default -> throw new AssertionError(method);
                });
            }
            default -> throw new AssertionError(m);
        });
        var handlers = new HashMap<String,LegacyRedirects.Handler>();
        new TcWandCapacityRedirect().register((caller, owner, name, descriptor, handler) -> handlers.put(name, handler));
        TestRuntimeProvider.runtime = runtime;
        try {
            LegacyRedirects.Call simulated = call(wand, stack, aspect, 2, false);
            assertEquals(1, handlers.get("addVis").handle(simulated));
            assertEquals(150, stored[0]);
            assertEquals(1, handlers.get("addVis").handle(call(wand, stack, aspect, 2, true)));
            assertEquals(250, stored[0]);
            stored[0] = 240;
            assertEquals(10, handlers.get("addRealVis").handle(call(wand, stack, aspect, 20, true)));
            assertEquals(250, stored[0]);
            marked[0] = false;
            assertEquals(77, handlers.get("addVis").handle(call(wand, stack, aspect, 2, true)));
        } finally { TestRuntimeProvider.reset(); }
    }
    private static LegacyRedirects.Call call(LegacyObject wand, LegacyObject stack, LegacyObject aspect, int amount, boolean actual) {
        return new LegacyRedirects.Call() {
            @Override public String caller() { return "thaumcraft.test"; }
            @Override public LegacyObject receiver() { return wand; }
            @Override public Object[] arguments() { return new Object[]{stack, aspect, amount, actual}; }
            @Override public Object proceed(Object... args) { return 77; }
        };
    }
}
