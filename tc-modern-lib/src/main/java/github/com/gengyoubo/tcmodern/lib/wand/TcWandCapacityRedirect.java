package github.com.gengyoubo.tcmodern.lib.wand;

import github.com.gengyoubo.mfcl.api.*;
import github.com.gengyoubo.mfcl.api.mla.*;
import github.com.gengyoubo.mfcl.api.spi.LegacyRedirectProvider;
import github.com.gengyoubo.tcmodern.api.TcModernApi;
import github.com.gengyoubo.tcmodern.api.wand.TcWandCapacities;

/** Applies explicit per-aspect capacities only when a registered key is present on a TC4 wand. */
public final class TcWandCapacityRedirect implements LegacyRedirectProvider {
    private static final String WAND = "thaumcraft.common.items.wands.ItemWandCasting";
    private static final String DESC = "(Lnet/minecraft/item/ItemStack;Lthaumcraft/api/aspects/Aspect;IZ)I";
    @Override public void register(LegacyRedirects redirects) {
        redirects.redirect("thaumcraft.", WAND, "addVis", DESC, call -> add(call, 100));
        redirects.redirect("thaumcraft.", WAND, "addRealVis", DESC, call -> add(call, 1));
    }
    private static Object add(LegacyRedirects.Call call, int unit) throws Throwable {
        var runtime = TcModernApi.runtime().orElse(null); var args = call.arguments();
        if (runtime == null || !(args[0] instanceof LegacyObject stack) || !(args[1] instanceof LegacyObject aspect)
                || call.receiver() == null || (int)args[2] < 0) return call.proceed();
        var originalAspect = runtime.accessor(Aspect.class, aspect);
        if (!originalAspect.primal()) return call.proceed();
        var raw = runtime.accessor(Stack.class, stack).tag();
        if (raw == null) return call.proceed();
        var nbt = runtime.accessor(Tag.class, raw); String tag = originalAspect.tag();
        for (String prefix : TcWandCapacities.prefixes()) {
            String key = prefix + tag;
            if (!nbt.contains(key)) continue;
            var wand = runtime.accessor(Wand.class, call.receiver());
            int current = wand.vis(stack, aspect), max = Math.max(0, nbt.getInt(key));
            long result = (long) current + (long) (int) args[2] * unit;
            if ((boolean) args[3]) wand.store(stack, aspect, (int) Math.min(Math.max(0L, result), max));
            return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, result - max) / unit);
        }
        return call.proceed();
    }
    @LegacyType("net.minecraft.item.ItemStack") public interface Stack {
        @LegacyField(name="field_77990_d", descriptor="Lnet/minecraft/nbt/NBTTagCompound;") LegacyObject tag();
    }
    @LegacyType("net.minecraft.nbt.NBTTagCompound") public interface Tag {
        @LegacyMethod(name="func_74764_b", descriptor="(Ljava/lang/String;)Z") boolean contains(String key);
        @LegacyMethod(name="func_74762_e", descriptor="(Ljava/lang/String;)I") int getInt(String key);
    }
    @LegacyType("thaumcraft.api.aspects.Aspect") public interface Aspect {
        @LegacyMethod(name="getTag", descriptor="()Ljava/lang/String;") String tag();
        @LegacyMethod(name="isPrimal", descriptor="()Z") boolean primal();
    }
    @LegacyType(WAND) public interface Wand {
        @LegacyMethod(name="getVis", descriptor="(Lnet/minecraft/item/ItemStack;Lthaumcraft/api/aspects/Aspect;)I") int vis(LegacyObject stack, LegacyObject aspect);
        @LegacyMethod(name="storeVis", descriptor="(Lnet/minecraft/item/ItemStack;Lthaumcraft/api/aspects/Aspect;I)V") void store(LegacyObject stack, LegacyObject aspect, int amount);
    }
}
