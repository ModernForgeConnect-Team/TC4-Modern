package github.com.gengyoubo.tcmodern.lib.scan;

import github.com.gengyoubo.mfcl.api.LegacyMappings;
import github.com.gengyoubo.tcmodern.api.TcModernApi;
import github.com.gengyoubo.tcmodern.api.scan.*;
import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;

/** The same bounded, occlusion-aware target search runs on client and authoritative server. */
public final class ModernScanTargets {
    public static final double RANGE = 9.0;
    public record Target(ScanKey key, String location, String label, AspectValues aspects) { }
    private static final Map<Item, Boolean> ORIGINAL_ITEMS = new IdentityHashMap<>();
    private static Object runtime;
    private static final Set<String> ORIGINAL_ENTITIES = Set.of("item","experience_orb","area_effect_cloud","arrow","snowball","egg","ender_pearl",
            "eye_of_ender","fireball","small_fireball","potion","experience_bottle","wither_skull","tnt","falling_block","firework_rocket",
            "boat","minecart","chest_minecart","furnace_minecart","tnt_minecart","hopper_minecart","spawner_minecart","command_block_minecart",
            "creeper","skeleton","spider","giant","zombie","slime","ghast","zombified_piglin","enderman","cave_spider","silverfish","blaze",
            "magma_cube","ender_dragon","wither","bat","witch","pig","sheep","cow","chicken","squid","wolf","mooshroom","snow_golem",
            "ocelot","iron_golem","horse","villager","player","item_frame","painting","leash_knot","fishing_bobber","lightning_bolt");
    private ModernScanTargets() { }
    public static boolean scanner(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return TcModernApi.runtime().map(api -> {
            try { return api.mappings().toLegacy(LegacyMappings.ITEM_STACK, stack).map(api::snapshot)
                    .map(old -> old.key().equals("Thaumcraft:ItemThaumometer")).orElse(false); }
            catch (RuntimeException unsupported) { return false; }
        }).orElse(false);
    }
    private static synchronized boolean original(Item item) {
        var api = TcModernApi.runtime().orElse(null);
        if (runtime != api) { ORIGINAL_ITEMS.clear(); runtime = api; }
        if (api == null) return false;
        return ORIGINAL_ITEMS.computeIfAbsent(item, value -> {
            try { return api.mappings().toLegacy(LegacyMappings.ITEM_STACK, new ItemStack(value)).isPresent(); }
            catch (RuntimeException unsupported) { return false; }
        });
    }
    public static Optional<Target> find(Player player) {
        var level = player.level();
        var eye = player.getEyePosition(); var end = eye.add(player.getLookAngle().scale(RANGE));
        var blockHit = level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        double closest = blockHit.getType() == HitResult.Type.MISS ? RANGE * RANGE : eye.distanceToSqr(blockHit.getLocation());
        Entity entity = null;
        for (var candidate : level.getEntities(player, player.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1),
                value -> value.isAlive() && !value.isSpectator() && (value.isPickable() || value instanceof ItemEntity))) {
            var hit = candidate.getBoundingBox().inflate(0.15).clip(eye, end);
            double distance = candidate.getBoundingBox().contains(eye) ? 0 : hit.map(eye::distanceToSqr).orElse(Double.MAX_VALUE);
            if (distance < closest) { closest = distance; entity = candidate; }
        }
        if (entity instanceof ItemEntity dropped) {
            var stack = dropped.getItem(); var key = itemKey(stack);
            if (original(stack.getItem()) && !TcScanApi.registry().isOverride(key)) return Optional.empty();
            return target(player, key, "entity/" + entity.getId(), stack.getHoverName().getString());
        }
        if (entity != null) {
            String id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString(); var key = ScanKey.entity(id);
            if (id.startsWith("minecraft:") && ORIGINAL_ENTITIES.contains(id.substring(10)) && !TcScanApi.registry().isOverride(key)) return Optional.empty();
            return target(player, key, "entity/" + entity.getId(), entity.getName().getString());
        }
        if (blockHit.getType() != HitResult.Type.BLOCK) return Optional.empty();
        var state = level.getBlockState(blockHit.getBlockPos());
        var block = state.getBlock(); var key = ScanKey.block(BuiltInRegistries.BLOCK.getKey(block).toString());
        if (block.asItem() != Items.AIR && original(block.asItem()) && !TcScanApi.registry().isOverride(key)) return Optional.empty();
        return target(player, key, "block/" + blockHit.getBlockPos().asLong() + "/" + state, block.getName().getString());
    }
    public static ScanKey itemKey(ItemStack stack) {
        if (stack.getItem() instanceof BlockItem item) return ScanKey.block(BuiltInRegistries.BLOCK.getKey(item.getBlock()).toString());
        return ScanKey.item(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
    }
    private static Optional<Target> target(Player player, ScanKey key, String location, String label) {
        return Optional.ofNullable(ScanRules.get(player.level().isClientSide).get(key)).map(values ->
                new Target(key, player.level().dimension().location() + "/" + location, label, values));
    }
}
