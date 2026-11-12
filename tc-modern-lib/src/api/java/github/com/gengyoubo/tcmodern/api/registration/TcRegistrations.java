package github.com.gengyoubo.tcmodern.api.registration;

import github.com.gengyoubo.mfcl.api.LegacyObject;
import github.com.gengyoubo.mfcl.api.LegacyRuntime;
import github.com.gengyoubo.mfcl.api.LegacyStack;
import github.com.gengyoubo.mfcl.api.mla.LegacyMethod;
import github.com.gengyoubo.mfcl.api.mla.LegacyType;
import github.com.gengyoubo.tcmodern.api.TcModernApi;
import java.util.Objects;

/** Registrations delegated to the original TC4 API. Run on the owning game thread after TC4 is ready. */
public final class TcRegistrations {
    private TcRegistrations() { }
    public enum LootBag { COMMON, UNCOMMON, RARE }
    @LegacyType("thaumcraft.api.ThaumcraftApi") public interface Api {
        @LegacyMethod(name="addWarpToItem", descriptor="(Lnet/minecraft/item/ItemStack;I)V", isStatic=true) void itemWarp(LegacyObject stack, int amount);
        @LegacyMethod(name="addWarpToResearch", descriptor="(Ljava/lang/String;I)V", isStatic=true) void researchWarp(String key, int amount);
        @LegacyMethod(name="addLootBagItem", descriptor="(Lnet/minecraft/item/ItemStack;I[I)V", isStatic=true) void loot(LegacyObject stack, int weight, int[] bags);
    }
    @LegacyType("thaumcraft.api.ThaumcraftApiHelper") public interface PlayerWarp {
        @LegacyMethod(name="addWarpToPlayer", descriptor="(Lnet/minecraft/entity/player/EntityPlayer;IZ)V", isStatic=true) void add(LegacyObject player, int amount, boolean temporary);
        @LegacyMethod(name="addStickyWarpToPlayer", descriptor="(Lnet/minecraft/entity/player/EntityPlayer;I)V", isStatic=true) void sticky(LegacyObject player, int amount);
    }
    private static Api api(LegacyRuntime runtime) {
        if (!runtime.isOpen() || !runtime.loadedMods().contains(TcModernApi.LEGACY_MOD_ID)) throw new IllegalStateException("Thaumcraft runtime is not available");
        return runtime.accessor(Api.class);
    }
    /** Sets the registered value for this item's identity and metadata; TC4 replaces any previous value. */
    public static void itemWarp(LegacyRuntime runtime, LegacyStack item, int amount) {
        var api = api(runtime); api.itemWarp(runtime.stack(Objects.requireNonNull(item)), amount);
    }
    /** Sets the value for a research key; TC4 replaces any previous value. */
    public static void researchWarp(LegacyRuntime runtime, String research, int amount) {
        if (research == null || research.isBlank()) throw new IllegalArgumentException("Empty research key");
        api(runtime).researchWarp(research, amount);
    }
    /** Delegates to TC4, including its packet sync; use a server player on the owning game thread.
     * Permanent warp cannot be reduced by the original method; temporary warp accepts negative amounts.
     */
    public static void playerWarp(LegacyRuntime runtime, Object hostPlayer, int amount, boolean temporary) {
        api(runtime);
        runtime.withPlayer(hostPlayer, player -> { runtime.accessor(PlayerWarp.class).add(player, amount, temporary); return null; });
    }
    /** Adds the original sticky warp type, using the original sync path. */
    public static void stickyWarp(LegacyRuntime runtime, Object hostPlayer, int amount) {
        api(runtime);
        runtime.withPlayer(hostPlayer, player -> { runtime.accessor(PlayerWarp.class).sticky(player, amount); return null; });
    }
    /** Adds a weighted entry once to each selected bag; repeating a call adds another entry. */
    public static void loot(LegacyRuntime runtime, LegacyStack item, int weight, LootBag... bags) {
        if (weight <= 0 || bags == null || bags.length == 0) throw new IllegalArgumentException("Positive weight and at least one bag required");
        var ids = java.util.Arrays.stream(bags).map(Objects::requireNonNull).distinct().mapToInt(Enum::ordinal).toArray();
        var api = api(runtime); api.loot(runtime.stack(Objects.requireNonNull(item)), weight, ids);
    }
}
