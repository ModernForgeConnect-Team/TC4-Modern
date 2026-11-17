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
    private record Pending(ServerLevel level, BlockPos pos, boolean table) {}
    private static final Map<UUID, List<Pending>> PENDING = new HashMap<>();
    private static final Map<ResourceLocation, CraftAdvancement> CRAFT_ADVANCEMENTS = Map.of(
            WOOD_IRON_WAND, new CraftAdvancement(NOVICE_WAND, "craft_wand"),
            INKWELL, new CraftAdvancement(WRITING_TOOLS, "craft_inkwell"),
            Objects.requireNonNull(Tc4Items.PENCIL.getId()),
            new CraftAdvancement(ResourceLocation.fromNamespaceAndPath(Tc4Modern.MOD_ID, "disposable_tool"), "craft_pencil"),
            Objects.requireNonNull(Tc4Items.MECHANICAL_PENCIL.getId()),
            new CraftAdvancement(ResourceLocation.fromNamespaceAndPath(Tc4Modern.MOD_ID, "fragile"), "craft_mechanical_pencil"),
            Objects.requireNonNull(Tc4Items.FOUNTAIN_PEN.getId()),
            new CraftAdvancement(ResourceLocation.fromNamespaceAndPath(Tc4Modern.MOD_ID, "fountain_pen"), "craft_fountain_pen"),
            ResourceLocation.fromNamespaceAndPath(TC, "block_table/meta_14"),
            new CraftAdvancement(ResourceLocation.fromNamespaceAndPath(Tc4Modern.MOD_ID, "lack_of_knowledge"), CRAFT_TABLE),
            Objects.requireNonNull(Tc4Blocks.DECONSTRUCTION_X.getId()),
            new CraftAdvancement(ResourceLocation.fromNamespaceAndPath(Tc4Modern.MOD_ID, "whole_aspects"), CRAFT_TABLE),
            Objects.requireNonNull(Tc4Blocks.DECONSTRUCTION_Y.getId()),
            new CraftAdvancement(ResourceLocation.fromNamespaceAndPath(Tc4Modern.MOD_ID, "many_aspects"), CRAFT_TABLE)
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
            PENDING.computeIfAbsent(player.getUUID(), ignored -> new ArrayList<>()).add(new Pending(level, event.getPos().immutable(), true));
        } else if (state.is(Blocks.CAULDRON) || state.is(Blocks.WATER_CAULDRON)) {
            var legacy = runtime.mappings().toLegacy(LegacyMappings.ITEM_STACK, event.getItemStack()).orElse(null);
            var wand = legacy == null ? null : runtime.accessor(DeskAccessors.StackView.class, legacy).item();
            if (wand != null && runtime.values().isInstance(wand, "thaumcraft.common.items.wands.ItemWandCasting"))
                PENDING.computeIfAbsent(player.getUUID(), ignored -> new ArrayList<>()).add(new Pending(level, event.getPos().immutable(), false));
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        var pending = PENDING.remove(player.getUUID());
        if (pending == null) return;
        var runtime = TcModernApi.runtime().orElse(null);
        if (runtime == null) return;
        for (var check : pending) checkFormation(player, runtime, check);
    }

    private static void checkFormation(ServerPlayer player, github.com.gengyoubo.mfcl.api.LegacyRuntime runtime, Pending pending) {
        var level = pending.level();
        if (!level.isLoaded(pending.pos())) return;
        var state = level.getBlockState(pending.pos());
        boolean formed = false;
        if (pending.table()) {
            for (int meta = 2; meta <= 9; meta++) {
                if (!runtime.hostBlock("Thaumcraft:blockTable", meta, BlockState.class).map(state::equals).orElse(false)) continue;
                var direction = Direction.from3DDataValue(meta < 6 ? meta : meta - 4);
                var other = pending.pos().relative(direction);
                int opposite = direction.getOpposite().get3DDataValue() + (meta < 6 ? 4 : 0);
                formed = level.isLoaded(other) && runtime.hostBlock("Thaumcraft:blockTable", opposite, BlockState.class)
                        .map(level.getBlockState(other)::equals).orElse(false);
                break;
            }
        } else {
            formed = runtime.hostBlock("Thaumcraft:blockMetalDevice", 0, BlockState.class).map(state::equals).orElse(false);
        }
        if (formed) {
            var id = ResourceLocation.fromNamespaceAndPath(Tc4Modern.MOD_ID, pending.table() ? "research_table" : "crucible");
            var advancement = player.server.getAdvancements().getAdvancement(id);
            if (advancement != null) player.getAdvancements().award(advancement, "formed");
        }
    }
}
