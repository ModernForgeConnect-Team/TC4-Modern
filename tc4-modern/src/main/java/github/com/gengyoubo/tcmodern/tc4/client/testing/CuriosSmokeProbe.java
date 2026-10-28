package github.com.gengyoubo.tcmodern.tc4.client.testing;

import com.mojang.logging.LogUtils;
import github.com.gengyoubo.mfcl.api.LegacyFeatures;
import github.com.gengyoubo.mfcl.api.LegacyMappings;
import github.com.gengyoubo.mfcl.api.LegacyObject;
import github.com.gengyoubo.mfcl.api.LegacyRuntime;
import github.com.gengyoubo.mfcl.api.LegacyStack;
import github.com.gengyoubo.mfcl.api.mla.LegacyMethod;
import github.com.gengyoubo.mfcl.api.mla.LegacyType;
import github.com.gengyoubo.tcmodern.api.TcModernApi;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Opt-in real-runtime test; creates a disposable world and exits. No Curios linkage on fallback. */
@Mod.EventBusSubscriber(modid = "tc4modern", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class CuriosSmokeProbe {
    private final Map<String, Object> proof = new LinkedHashMap<>();
    private final long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(4);
    private int stage;
    private CompletableFuture<Void> checks;

    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        if (Boolean.getBoolean("tc4modern.curiosSmokeTest"))
            event.enqueueWork(() -> MinecraftForge.EVENT_BUS.addListener(new CuriosSmokeProbe()::tick));
    }
    private void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == 99) return;
        var mc = Minecraft.getInstance();
        try {
            require(System.nanoTime() < deadline, "Curios acceptance timed out at " + stage);
            if (stage == 0 && mc.screen instanceof TitleScreen && mc.getOverlay() == null) {
                var runtime = TcModernApi.runtime().orElseThrow();
                require(runtime.supports(LegacyFeatures.EQUIPMENT_BAUBLES), "Baubles ABI storage unavailable");
                proof.put("legacyMods", runtime.loadedMods());
                proof.put("curiosInstalled", ModList.get().isLoaded("curios"));
                if (!ModList.get().isLoaded("curios")) { finish(mc, null); return; }
                org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());
                mc.options.pauseOnLostFocus = false;
                mc.options.renderDistance().set(2);
                mc.createWorldOpenFlows().createFreshLevel("tc4modern-curios-smoke-" + System.currentTimeMillis(),
                        new LevelSettings("TC4 Curios", GameType.CREATIVE, false, Difficulty.PEACEFUL,
                                true, new GameRules(), WorldDataConfiguration.DEFAULT),
                        new WorldOptions(42, false, false), access -> access.registryOrThrow(Registries.WORLD_PRESET)
                                .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
                stage = 1;
            } else if (stage == 1 && mc.level != null && mc.player != null && mc.screen == null) {
                var server = mc.getSingleplayerServer();
                checks = server.submit(() -> Checks.validate(TcModernApi.runtime().orElseThrow(),
                        server.getPlayerList().getPlayer(mc.player.getUUID()), proof));
                stage = 2;
            } else if (stage == 2 && checks.isDone()) { checks.join(); finish(mc, null); }
        } catch (Exception | AssertionError failure) { finish(mc, failure); }
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
    private void finish(Minecraft mc, Throwable failure) {
        stage = 99;
        proof.put("status", failure == null ? "PASS" : "FAIL");
        if (failure != null) { proof.put("failure", failure.toString()); LogUtils.getLogger().error("TC4_CURIOS_SMOKE_FAIL", failure); }
        try {
            var file = mc.gameDirectory.toPath().resolve("logs/tc4modern/curios-smoke.json");
            Files.createDirectories(file.getParent());
            Files.writeString(file, new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(proof));
        } catch (java.io.IOException error) { throw new IllegalStateException(error); }
        LogUtils.getLogger().info("TC4_CURIOS_SMOKE_RESULT: {}", proof);
        if (mc.level != null) { mc.level.disconnect(); mc.clearLevel(); }
        mc.stop();
    }

    @LegacyType("baubles.api.BaublesApi") public interface Api {
        @LegacyMethod(name = "getBaubles", descriptor = "(Lnet/minecraft/entity/player/EntityPlayer;)Lnet/minecraft/inventory/IInventory;", isStatic = true)
        LegacyObject inventory(LegacyObject player);
    }
    @LegacyType("net.minecraft.inventory.IInventory") public interface Inventory {
        @LegacyMethod(name = "func_70301_a", descriptor = "(I)Lnet/minecraft/item/ItemStack;") LegacyObject get(int index);
        @LegacyMethod(name = "func_70299_a", descriptor = "(ILnet/minecraft/item/ItemStack;)V") void set(int index, LegacyObject stack);
    }

    private static final class Checks {
        static void validate(LegacyRuntime runtime, ServerPlayer player, Map<String, Object> proof) {
            var curios = top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(player).resolve().orElseThrow();
            var ring = curios.getStacksHandler("ring").orElseThrow().getStacks();
            var amulet = curios.getStacksHandler("amulet").orElseThrow().getStacks();
            var belt = curios.getStacksHandler("belt").orElseThrow().getStacks();
            ring.setStackInSlot(0, stack(runtime, "Thaumcraft:ItemRingRunic", "ring0"));
            ring.setStackInSlot(1, stack(runtime, "Thaumcraft:ItemRingRunic", "ring1"));
            amulet.setStackInSlot(0, stack(runtime, "Thaumcraft:ItemAmuletVis", "amulet"));
            belt.setStackInSlot(0, stack(runtime, "Thaumcraft:ItemGirdleHover", "belt"));
            var curio = top.theillusivec4.curios.api.CuriosApi.getCurio(ring.getStackInSlot(0)).resolve().orElseThrow();
            require(curio.canEquip(new top.theillusivec4.curios.api.SlotContext("ring", player, 0, false, true)),
                    "Original ring canEquip callback failed");
            require(!curio.canEquip(new top.theillusivec4.curios.api.SlotContext("amulet", player, 0, false, true)),
                    "Ring accepted the amulet slot");
            require(!curio.canEquip(new top.theillusivec4.curios.api.SlotContext("ring", player, 0, true, true)),
                    "Ring accepted a cosmetic slot");
            require(!curio.canEquip(new top.theillusivec4.curios.api.SlotContext("ring", player, 2, false, true)),
                    "Ring accepted a slot outside the original four-slot inventory");
            require(curio.canUnequip(new top.theillusivec4.curios.api.SlotContext("ring", player, 0, false, true)),
                    "Original ring canUnequip callback failed");
            proof.put("equipChecks", true);
            runtime.withPlayer(player, actor -> {
                var inventory = runtime.accessor(Inventory.class, runtime.accessor(Api.class).inventory(actor));
                for (int slot = 0; slot < 4; slot++) {
                    var snapshot = runtime.snapshot(inventory.get(slot));
                    String marker = new String[]{"amulet", "ring0", "ring1", "belt"}[slot];
                    require(snapshot.count() == 1 && snapshot.snbt().contains(marker), "Wrong old slot or lost NBT at " + slot + ": " + snapshot);
                }
                return null;
            });
            proof.put("fourSlotsAndNbt", true);
            player.fallDistance = 10;
            MinecraftForge.EVENT_BUS.post(new LivingEvent.LivingTickEvent(player));
            float reduction = 10 - player.fallDistance;
            require(Math.abs(reduction - 0.33f) < 0.005f, "Hover belt worn tick must run exactly once: " + reduction);
            proof.put("hoverBeltWornTickExactlyOnce", reduction);
            runtime.withPlayer(player, actor -> {
                var inventory = runtime.accessor(Inventory.class, runtime.accessor(Api.class).inventory(actor));
                inventory.set(2, null);
                return null;
            });
            require(ring.getStackInSlot(1).isEmpty(), "Old removal did not reach Curios");
            require(!ring.getStackInSlot(0).isEmpty(), "Old removal affected the other ring");
            proof.put("oldInventoryWriteback", true);
            // Curios owns serialization; restoring a save must keep the original item NBT.
            var saved = curios.saveInventory(false);
            ring.setStackInSlot(0, ItemStack.EMPTY);
            curios.loadInventory(saved);
            var restored = curios.getStacksHandler("ring").orElseThrow().getStacks().getStackInSlot(0);
            require(!restored.isEmpty(), "Curios persistence lost equipped ring");
            require(runtime.snapshot(runtime.mappings().toLegacy(LegacyMappings.ITEM_STACK, restored).orElseThrow())
                    .snbt().contains("ring0"), "Curios persistence lost original ring NBT");
            proof.put("curiosPersistence", true);
            if (runtime.loadedMods().contains("TaintedMagic")) {
                var sash = stack(runtime, "TaintedMagic:ItemVoidwalkerSash", "addon-belt");
                var addonRing = stack(runtime, "TaintedMagic:ItemLumosRing", "addon-ring");
                require(belt.isItemValid(0, sash), "Tainted Magic Voidwalker Sash rejected by Curios belt predicate/canEquip");
                require(!ring.isItemValid(1, sash), "Tainted Magic belt accepted a ring slot");
                require(ring.isItemValid(1, addonRing), "Tainted Magic Lumos Ring rejected by Curios ring predicate/canEquip");
                belt.setStackInSlot(0, sash);
                ring.setStackInSlot(1, addonRing);
                runtime.withPlayer(player, actor -> {
                    var inventory = runtime.accessor(Inventory.class, runtime.accessor(Api.class).inventory(actor));
                    require(runtime.snapshot(inventory.get(3)).key().equals("TaintedMagic:ItemVoidwalkerSash"), "Addon belt missing from original Baubles API");
                    require(runtime.snapshot(inventory.get(2)).key().equals("TaintedMagic:ItemLumosRing"), "Addon ring missing from original Baubles API");
                    return null;
                });
                proof.put("taintedMagicSashAndRing", true);
            }
            if (runtime.loadedMods().contains("tc4modern_bauble_fixture")) {
                var old = new LegacyStack("tc4modern_bauble_fixture:variant", 1, 1, "");
                var variant = runtime.mappings().toHost(LegacyMappings.ITEM_STACK, runtime.stack(old), ItemStack.class).orElseThrow();
                require(belt.isItemValid(0, variant), "Metadata 1 belt was classified as default metadata 0 ring");
                require(!ring.isItemValid(0, variant), "Metadata 1 belt incorrectly accepted ring slot");
                var nbtVariant = runtime.mappings().toHost(LegacyMappings.ITEM_STACK,
                        runtime.stack(new LegacyStack(old.key(), 1, 1, "{amulet:1b}")), ItemStack.class).orElseThrow();
                require(amulet.isItemValid(0, nbtVariant), "Stack-dependent NBT amulet type was ignored");
                require(!belt.isItemValid(0, nbtVariant), "NBT amulet incorrectly accepted belt slot");
                proof.put("addonMetadataAndNbtSlotTypes", true);
            }
        }
        private static ItemStack stack(LegacyRuntime runtime, String key, String marker) {
            return runtime.mappings().toHost(LegacyMappings.ITEM_STACK,
                    runtime.stack(new LegacyStack(key, 0, 1, "{tc4modernCuriosProbe:\"" + marker + "\"}")), ItemStack.class).orElseThrow();
        }
    }
}
