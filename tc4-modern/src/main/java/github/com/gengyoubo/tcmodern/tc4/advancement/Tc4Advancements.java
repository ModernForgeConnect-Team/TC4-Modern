package github.com.gengyoubo.tcmodern.tc4.advancement;

import github.com.gengyoubo.tcmodern.tc4.Tc4Modern;
import github.com.gengyoubo.tcmodern.tc4.init.Tc4Items;
import github.com.gengyoubo.tcmodern.tc4.init.Tc4Blocks;
import github.com.gengyoubo.tcmodern.api.TcModernApi;
import github.com.gengyoubo.mfcl.api.LegacyMappings;
import github.com.gengyoubo.tcmodern.tc4.research.DeskAccessors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.Objects;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import static github.com.gengyoubo.tcmodern.tc4.Tc4Modern.TC;

@Mod.EventBusSubscriber(modid = Tc4Modern.MOD_ID)
public final class Tc4Advancements {
    private static final ResourceLocation WOOD_IRON_WAND = ResourceLocation.fromNamespaceAndPath(TC, "wand_casting/wood_iron");
    private static final ResourceLocation NOVICE_WAND = ResourceLocation.fromNamespaceAndPath(Tc4Modern.MOD_ID, "novice_wand");
    private static final ResourceLocation INKWELL = ResourceLocation.fromNamespaceAndPath(TC, "item_inkwell");
    private static final ResourceLocation WRITING_TOOLS = ResourceLocation.fromNamespaceAndPath(Tc4Modern.MOD_ID, "writing_tools");
    private static final String CRAFT_TABLE ="craft_table";

    private Tc4Advancements() { }
    private record CraftAdvancement(ResourceLocation id, String criterion) {}
    private enum Formation { TABLE, CRUCIBLE, INFUSION }
    private record Pending(ServerLevel level, BlockPos pos, Formation formation) {}
    private static final Map<UUID, List<Pending>> PENDING = new HashMap<>();
    private static final Map<UUID, Integer> INVENTORY_VERSIONS = new HashMap<>();
    private static final Map<net.minecraft.world.item.Item, Boolean> FOCUS_ITEMS = new java.util.IdentityHashMap<>();
    private static final Map<ResourceLocation, CraftAdvancement> CRAFT_ADVANCEMENTS = Map.ofEntries(
            Map.entry(WOOD_IRON_WAND, new CraftAdvancement(NOVICE_WAND, "craft_wand")),
            Map.entry(INKWELL, new CraftAdvancement(WRITING_TOOLS, "craft_inkwell")),
            Map.entry(Objects.requireNonNull(Tc4Items.PENCIL.getId()), new CraftAdvancement(ResourceLocation.fromNamespaceAndPath(Tc4Modern.MOD_ID, "disposable_tool"), "craft_pencil")),
            Map.entry(Objects.requireNonNull(Tc4Items.MECHANICAL_PENCIL.getId()), new CraftAdvancement(ResourceLocation.fromNamespaceAndPath(Tc4Modern.MOD_ID, "fragile"), "craft_mechanical_pencil")),
            Map.entry(Objects.requireNonNull(Tc4Items.FOUNTAIN_PEN.getId()), new CraftAdvancement(ResourceLocation.fromNamespaceAndPath(Tc4Modern.MOD_ID, "fountain_pen"), "craft_fountain_pen")),
            Map.entry(ResourceLocation.fromNamespaceAndPath(TC, "block_table/meta_14"), new CraftAdvancement(ResourceLocation.fromNamespaceAndPath(Tc4Modern.MOD_ID, "lack_of_knowledge"), CRAFT_TABLE)),
            Map.entry(Objects.requireNonNull(Tc4Blocks.DECONSTRUCTION_X.getId()), new CraftAdvancement(ResourceLocation.fromNamespaceAndPath(Tc4Modern.MOD_ID, "whole_aspects"), CRAFT_TABLE)),
            Map.entry(Objects.requireNonNull(Tc4Blocks.DECONSTRUCTION_Y.getId()), new CraftAdvancement(ResourceLocation.fromNamespaceAndPath(Tc4Modern.MOD_ID, "many_aspects"), CRAFT_TABLE)),
            Map.entry(ResourceLocation.fromNamespaceAndPath(TC, "focus_pouch"), new CraftAdvancement(ResourceLocation.fromNamespaceAndPath(Tc4Modern.MOD_ID, "focus_pouch"), "crafted")),
            Map.entry(ResourceLocation.fromNamespaceAndPath(TC, "item_thaumometer"), new CraftAdvancement(ResourceLocation.fromNamespaceAndPath(Tc4Modern.MOD_ID, "thaumometer"), "crafted")),
            Map.entry(ResourceLocation.fromNamespaceAndPath(TC, "item_goggles"), new CraftAdvancement(ResourceLocation.fromNamespaceAndPath(Tc4Modern.MOD_ID, "goggles"), "crafted"))
    );
    @SubscribeEvent
    public static void onCraft(PlayerEvent.ItemCraftedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        awardCraft(player, event.getCrafting());
    }

    public static void awardCraft(ServerPlayer player, net.minecraft.world.item.ItemStack stack) {
        ResourceLocation item = BuiltInRegistries.ITEM.getKey(stack.getItem());
        CraftAdvancement target = CRAFT_ADVANCEMENTS.get(item);
        if (target == null) return;
        var advancement = player.server.getAdvancements().getAdvancement(target.id());
        if (advancement != null) {
            player.getAdvancements().award(advancement, target.criterion());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onUse(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.isCanceled()) return;
        var runtime = TcModernApi.runtime().orElse(null);
        if (runtime == null) return;
        var level = player.serverLevel();
        var state = level.getBlockState(event.getPos());
        var item = BuiltInRegistries.ITEM.getKey(event.getItemStack().getItem());
        if (INKWELL.equals(item) && runtime.hostBlock("Thaumcraft:blockTable", 0, BlockState.class).map(state::equals).orElse(false)) {
            PENDING.computeIfAbsent(player.getUUID(), ignored -> new ArrayList<>()).add(new Pending(level, event.getPos().immutable(), Formation.TABLE));
        } else if (state.is(Blocks.CAULDRON) || state.is(Blocks.WATER_CAULDRON)
                || runtime.hostBlock("Thaumcraft:blockStoneDevice", 2, BlockState.class).map(state::equals).orElse(false)) {
            boolean cauldron = state.is(Blocks.CAULDRON) || state.is(Blocks.WATER_CAULDRON);
            if (isWand(runtime, event.getItemStack())
                    && (cauldron || !infusionFormed(runtime, level, event.getPos())))
                PENDING.computeIfAbsent(player.getUUID(), ignored -> new ArrayList<>()).add(new Pending(level, event.getPos().immutable(), cauldron ? Formation.CRUCIBLE : Formation.INFUSION));
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        var pending = PENDING.remove(player.getUUID());
        var runtime = TcModernApi.runtime().orElse(null);
        if (runtime == null) return;
        if (pending != null) for (var check : pending) checkFormation(player, runtime, check);
        checkFocus(player, runtime);
    }

    private static void checkFormation(ServerPlayer player, github.com.gengyoubo.mfcl.api.LegacyRuntime runtime, Pending pending) {
        var level = pending.level();
        if (!level.isLoaded(pending.pos())) return;
        var state = level.getBlockState(pending.pos());
        boolean formed = false;
        if (pending.formation() == Formation.TABLE) {
            for (int meta = 2; meta <= 9; meta++) {
                if (!runtime.hostBlock("Thaumcraft:blockTable", meta, BlockState.class).map(state::equals).orElse(false)) continue;
                var direction = Direction.from3DDataValue(meta < 6 ? meta : meta - 4);
                var other = pending.pos().relative(direction);
                int opposite = direction.getOpposite().get3DDataValue() + (meta < 6 ? 4 : 0);
                formed = level.isLoaded(other) && runtime.hostBlock("Thaumcraft:blockTable", opposite, BlockState.class)
                        .map(level.getBlockState(other)::equals).orElse(false);
                break;
            }
        } else if (pending.formation() == Formation.CRUCIBLE) {
            formed = runtime.hostBlock("Thaumcraft:blockMetalDevice", 0, BlockState.class).map(state::equals).orElse(false);
        } else formed = infusionFormed(runtime, level, pending.pos());
        if (formed) {
            award(player, switch (pending.formation()) { case TABLE -> "research_table"; case CRUCIBLE -> "crucible"; case INFUSION -> "infusion_altar"; }, "formed");
        }
    }

    private static boolean infusionFormed(github.com.gengyoubo.mfcl.api.LegacyRuntime runtime, ServerLevel level, BlockPos matrix) {
        for (int x : new int[]{-1, 1}) for (int z : new int[]{-1, 1}) for (int dy = -2; dy <= -1; dy++) {
            var pos = matrix.offset(x, dy, z);
            if (!level.isLoaded(pos) || !runtime.hostBlock("Thaumcraft:blockStoneDevice", dy == -2 ? 3 : 4, BlockState.class)
                    .map(level.getBlockState(pos)::equals).orElse(false)) return false;
        }
        return true;
    }

    private static void checkFocus(ServerPlayer player, github.com.gengyoubo.mfcl.api.LegacyRuntime runtime) {
        var advancement = player.server.getAdvancements().getAdvancement(ResourceLocation.fromNamespaceAndPath(Tc4Modern.MOD_ID, "wand_focus"));
        if (advancement == null || player.getAdvancements().getOrStartProgress(advancement).isDone()) return;
        int version = player.getInventory().getTimesChanged();
        if (Objects.equals(INVENTORY_VERSIONS.put(player.getUUID(), version), version) && player.tickCount % 20 != 0) return;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            var stack = player.getInventory().getItem(slot);
            if (stack.isEmpty()) continue;
            if (FOCUS_ITEMS.computeIfAbsent(stack.getItem(), ignored -> isFocus(runtime, stack))) {
                award(player, "wand_focus", "obtained");
                break;
            }
        }
    }

    private static boolean isFocus(github.com.gengyoubo.mfcl.api.LegacyRuntime runtime, net.minecraft.world.item.ItemStack stack) {
        try {
            var legacy = runtime.mappings().toLegacy(LegacyMappings.ITEM_STACK, stack).orElse(null);
            var item = legacy == null ? null : runtime.accessor(DeskAccessors.StackView.class, legacy).item();
            return item != null && runtime.values().isInstance(item, "thaumcraft.api.wands.ItemFocusBasic");
        } catch (RuntimeException unsupported) { return false; }
    }

    private static boolean isWand(github.com.gengyoubo.mfcl.api.LegacyRuntime runtime, net.minecraft.world.item.ItemStack stack) {
        if (stack.isEmpty()) return false;
        try {
            var legacy = runtime.mappings().toLegacy(LegacyMappings.ITEM_STACK, stack).orElse(null);
            var item = legacy == null ? null : runtime.accessor(DeskAccessors.StackView.class, legacy).item();
            return item != null && runtime.values().isInstance(item, "thaumcraft.common.items.wands.ItemWandCasting");
        } catch (RuntimeException unsupported) { return false; }
    }

    @SubscribeEvent public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        PENDING.remove(event.getEntity().getUUID());
        INVENTORY_VERSIONS.remove(event.getEntity().getUUID());
    }

    public static void award(ServerPlayer player, String id, String criterion) {
        var advancement = player.server.getAdvancements().getAdvancement(ResourceLocation.fromNamespaceAndPath(Tc4Modern.MOD_ID, id));
        if (advancement != null) player.getAdvancements().award(advancement, criterion);
    }
}
