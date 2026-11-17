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
    private int stage, frames;
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
                net.minecraft.world.item.CreativeModeTabs.tryRebuildTabContents(mc.level.enabledFeatures(), mc.player.canUseGameMasterBlocks(), mc.level.registryAccess());
                var eggsTab = net.minecraft.core.registries.BuiltInRegistries.CREATIVE_MODE_TAB.get(net.minecraft.world.item.CreativeModeTabs.SPAWN_EGGS.location());
                var tcTab = net.minecraft.core.registries.BuiltInRegistries.CREATIVE_MODE_TAB.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("thaumcraft", "creative/thaumcraft"));
                require(tcTab != null, "TC4 creative tab is missing");
                int modernEggCount = 0;
                for (var item : net.minecraftforge.registries.ForgeRegistries.ITEMS) {
                    var id = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(item);
                    if (id == null || !id.getNamespace().equals("tc4modern") || !(item instanceof net.minecraft.world.item.SpawnEggItem)) continue;
                    modernEggCount++;
                }
                require(modernEggCount == 0, "TC4 Modern still registers spawn eggs: " + modernEggCount);
                var runtime = TcModernApi.runtime().orElseThrow();
                var originalEgg = runtime.mappings().toHost(LegacyMappings.ITEM_STACK,
                        runtime.stack(new LegacyStack("Thaumcraft:ItemSpawnerEgg", 0, 1, "")), ItemStack.class).orElseThrow();
                require(eggsTab.contains(originalEgg), "Original TC4 spawn egg is missing from vanilla spawn egg tab");
                require(!tcTab.contains(originalEgg), "Original TC4 spawn egg is in the TC4 item tab");
                proof.put("noModernSpawnEggs", true);
                proof.put("originalTc4SpawnEggInVanillaTab", true);
                var server = mc.getSingleplayerServer();
                checks = server.submit(() -> Checks.validate(TcModernApi.runtime().orElseThrow(),
                        server.getPlayerList().getPlayer(mc.player.getUUID()), proof));
                stage = 2;
            } else if (stage == 2 && checks.isDone()) {
                checks.join();
                if (!ModList.get().isLoaded("shape_shifter_curse")) { finish(mc, null); return; }
                mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT);
                mc.options.keyShift.setDown(true);
                stage = 3;
            } else if (stage == 3 && ++frames >= 30) {
                net.minecraft.client.Screenshot.grab(mc.gameDirectory, "tc4modern-ssc-goggles.png", mc.getMainRenderTarget(), message -> { });
                mc.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player));
                frames = 0; stage = 4;
            } else if (stage == 4 && ++frames >= 30) {
                net.minecraft.client.Screenshot.grab(mc.gameDirectory, "tc4modern-ssc-goggles-inventory.png", mc.getMainRenderTarget(), message -> { });
                frames = 0; stage = 5;
            } else if (stage == 5 && ++frames >= 20) {
                finish(mc, null);
            }
        } catch (Exception | AssertionError failure) { finish(mc, failure); }
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
    private void finish(Minecraft mc, Throwable failure) {
        stage = 99;
        mc.options.keyShift.setDown(false);
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

    @LegacyType("net.minecraft.entity.player.EntityPlayer") public interface PlayerArmor {
        @LegacyMethod(name = "func_82169_q", descriptor = "(I)Lnet/minecraft/item/ItemStack;") LegacyObject armor(int index);
    }

    private static final class Checks {
        static void validate(LegacyRuntime runtime, ServerPlayer player, Map<String, Object> proof) {
            var pech = github.com.gengyoubo.tcmodern.tc4.init.Tc4Entities.PECH.get().create(player.serverLevel());
            require(pech != null, "Could not create pech");
            pech.moveTo(player.getX() + 1, player.getY(), player.getZ(), 0, 0);
            pech.setTamed(true);
            require(player.serverLevel().addFreshEntity(pech), "Could not spawn pech");
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            pech.interact(player, net.minecraft.world.InteractionHand.MAIN_HAND);
            require(player.containerMenu instanceof github.com.gengyoubo.tcmodern.tc4.menu.PechMenu, "Tamed pech did not open its trade menu");
            var pechMenu = (github.com.gengyoubo.tcmodern.tc4.menu.PechMenu) player.containerMenu;
            pechMenu.getSlot(0).set(new ItemStack(net.minecraft.world.item.Items.GOLD_INGOT, 2));
            require(pechMenu.canTrade() && pechMenu.clickMenuButton(player, 0), "Pech trade button failed");
            require(pechMenu.getSlot(0).getItem().getCount() == 1, "Pech trade did not consume exactly one item");
            require(!pechMenu.getSlot(1).mayPlace(new ItemStack(net.minecraft.world.item.Items.DIAMOND)), "Pech output accepts inserted items");
            player.closeContainer(); pech.discard();
            require(net.minecraftforge.registries.ForgeRegistries.ENCHANTMENTS.getValues().stream().anyMatch(e -> e.getDescriptionId().equals("enchantment.haste")), "TC4 haste enchantment unavailable");
            require(net.minecraftforge.registries.ForgeRegistries.ENCHANTMENTS.getValues().stream().anyMatch(e -> e.getDescriptionId().equals("enchantment.repair")), "TC4 repair enchantment unavailable");
            proof.put("pechMenuAndTrade", true);
            proof.put("originalPechEnchantmentBooksAvailable", true);
            var bell = runtime.mappings().toHost(LegacyMappings.ITEM_STACK,
                    runtime.stack(new LegacyStack("Thaumcraft:GolemBell", 0, 1, "")), ItemStack.class).orElseThrow();
            var selectedId = java.util.UUID.randomUUID();
            bell.getOrCreateTag().putUUID("tc4modern_selected_golem", selectedId);
            bell.getOrCreateTag().putString("tc4modern_selected_dimension", "minecraft:overworld");
            player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, bell);
            MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.entity.living.LivingEquipmentChangeEvent(player,
                    net.minecraft.world.entity.EquipmentSlot.MAINHAND, ItemStack.EMPTY, bell));
            bell.getAttributeModifiers(net.minecraft.world.entity.EquipmentSlot.MAINHAND);
            require(runtime.mappings().toLegacy(LegacyMappings.ITEM_STACK, bell).isPresent(), "Migrated golem bell cannot map to original");
            var selection = bell.getTagElement("display").getCompound("tc4modern_golem_data");
            require(selection.hasUUID("tc4modern_selected_golem") && selectedId.equals(selection.getUUID("tc4modern_selected_golem"))
                    && "minecraft:overworld".equals(selection.getString("tc4modern_selected_dimension")), "Golem selection was not preserved");
            var placer = runtime.mappings().toHost(LegacyMappings.ITEM_STACK,
                    runtime.stack(new LegacyStack("Thaumcraft:ItemGolemPlacer", 0, 1, "")), ItemStack.class).orElseThrow();
            var portable = new net.minecraft.nbt.CompoundTag(); portable.putInt("Tc4Core", 6);
            placer.getOrCreateTag().put("tc4modern_golem", portable); placer.getOrCreateTag().putByte("core", (byte)6);
            MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.entity.living.LivingEquipmentChangeEvent(player,
                    net.minecraft.world.entity.EquipmentSlot.MAINHAND, bell, placer));
            placer.getAttributeModifiers(net.minecraft.world.entity.EquipmentSlot.MAINHAND);
            require(runtime.mappings().toLegacy(LegacyMappings.ITEM_STACK, placer).isPresent(), "Migrated portable golem cannot map to original");
            require(placer.getTagElement("display").getCompound("tc4modern_golem_data").getCompound("tc4modern_golem").getInt("Tc4Core") == 6,
                    "Portable golem state lost during migration");
            player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            proof.put("golemBellAndPortableNbtMigration", true);
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
            var head = curios.getStacksHandler("head").orElseThrow().getStacks();
            var curioGoggles = stack(runtime, "Thaumcraft:ItemGoggles", "curio-head");
            head.setStackInSlot(0, curioGoggles);
            player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(net.minecraft.world.item.Items.IRON_HELMET));
            runtime.withPlayer(player, actor -> {
                var worn = runtime.accessor(PlayerArmor.class, actor).armor(3);
                require(worn != null && runtime.snapshot(worn).key().equals("Thaumcraft:ItemGoggles"),
                        "Curios goggles did not take effect with a helmet equipped");
                return null;
            });
            head.setStackInSlot(0, ItemStack.EMPTY);
            player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, ItemStack.EMPTY);
            proof.put("curiosGogglesWithHelmet", true);
            if (ModList.get().isLoaded("shape_shifter_curse")) {
                var goggles = stack(runtime, "Thaumcraft:ItemGoggles", "ssc-head");
                require(net.onixary.shapeShifterCurseForge.form.FormManager.setForm(player, "shape-shifter-curse:axolotl_2"), "Could not select axolotl stage 2");
                boolean stage2HelmetRestricted = net.onixary.shapeShifterCurseForge.power.MissingPowerEvents.isArmorRestricted(player,
                        net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(net.minecraft.world.item.Items.IRON_HELMET));
                require(net.onixary.shapeShifterCurseForge.form.FormManager.current(player).stage() == 3 && stage2HelmetRestricted,
                        "Stage 2 fixture or its normal SSC helmet restriction changed");
                proof.put("sscStage2KeepsHelmetRestriction", true);
                require(net.onixary.shapeShifterCurseForge.form.FormManager.setForm(player, "shape-shifter-curse:axolotl_3"), "Could not select axolotl form");
                var slot = net.minecraft.world.entity.EquipmentSlot.HEAD;
                require(net.onixary.shapeShifterCurseForge.power.MissingPowerEvents.isArmorRestricted(player, slot,
                        new ItemStack(net.minecraft.world.item.Items.IRON_HELMET)), "Fixture form does not restrict ordinary helmets");
                require(!net.onixary.shapeShifterCurseForge.power.MissingPowerEvents.isArmorRestricted(player, slot, goggles), "SSC rejected goggles");
                var headSlot = player.inventoryMenu.slots.stream().filter(s -> s.container == player.getInventory() && s.getSlotIndex() == 39).findFirst().orElseThrow();
                require(headSlot.mayPlace(goggles), "SSC inventory slot rejected goggles");
                player.setItemSlot(slot, goggles);
                net.onixary.shapeShifterCurseForge.power.MissingPowerEvents.onFormChanged(player);
                require(player.getItemBySlot(slot).is(goggles.getItem()), "SSC removed equipped goggles");
                proof.put("sscRestrictedFormAllowsGogglesInHelmetSlot", true);
                net.onixary.shapeShifterCurseForge.form.FormManager.setForm(player, "shape-shifter-curse:anubis_wolf_3");
                player.setGameMode(GameType.SURVIVAL);
            }
        }
        private static ItemStack stack(LegacyRuntime runtime, String key, String marker) {
            return runtime.mappings().toHost(LegacyMappings.ITEM_STACK,
                    runtime.stack(new LegacyStack(key, 0, 1, "{tc4modernCuriosProbe:\"" + marker + "\"}")), ItemStack.class).orElseThrow();
        }
    }
}
