package github.com.gengyoubo.mfcl.content;

import com.mojang.logging.LogUtils;
import github.com.gengyoubo.mfcl.api.LegacyEquipment;
import github.com.gengyoubo.mfcl.api.LegacyEvents;
import github.com.gengyoubo.mfcl.bridge.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Developer API player services: equipment sources and their write-back, worn ticks, host events forwarded to the old
 * event bus, and the persistent old player's identity, state and entity data. Nothing runs per tick until an addon
 * registers an equipment source or forwards an event.
 */
public final class HostPlayers implements LegacyEquipment, LegacyEvents {
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD};
    private static final Slot[] SLOTS = Slot.values();
    /** 1.7.10 players step 0.5 blocks, host players 0.6; old code compares against and resets to its own default. */
    private static final float STEP_OFFSET = 0.1f;
    private static final int PERSIST_INTERVAL = 1200;
    private record Registration(int priority, long order, Source source) {}
    /** The source that answered a slot and the host stack it gave, per player and side. */
    private record Supplied(Source source, ItemStack host) {}
    private interface ActorWork<T> { T run(Object world, Object actor) throws Exception; }
    /**
     * A host event raised while original code is already running on this thread (old code hurting the player's host
     * entity, say) is not forwarded: refreshing the actor would rewrite it under the running code.
     */
    private static final class Reentered extends Exception { Reentered() { super(null, null, false, false); } }
    private final ModernContentBridge content;
    private final LegacyServices services;
    private final LegacyPlayerEvents events;
    private final Source vanilla = new VanillaArmor();
    private volatile List<Registration> sources;
    private final AtomicLong order = new AtomicLong();
    private volatile int ticking = 0xFF;
    private final Map<Kind, AtomicInteger> forwarded = new EnumMap<>(Kind.class);
    private final Map<UUID, Supplied[]> serverSupplied = new ConcurrentHashMap<>(), clientSupplied = new ConcurrentHashMap<>();
    private final Set<String> failures = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean baublesScoped = new AtomicBoolean();
    private volatile Diagnostics diagnostics = (code, operation, failure) -> { };
    public interface Diagnostics { void note(String code, String operation, Throwable failure); }

    HostPlayers(ModernContentBridge content) throws Exception {
        this.content = content; this.services = content.services();
        this.events = new LegacyPlayerEvents(services);
        for (var kind : Kind.values()) forwarded.put(kind, new AtomicInteger());
        sources = List.of(new Registration(DEFAULT_PRIORITY, order.getAndIncrement(), vanilla));
        LegacyPlayerArmor.source(new ArmorSource());
        LegacyPlayerBody.host(new Body());
        services.playerData().persistence(id -> {
            var player = serverPlayer(id);
            return player == null ? null : player.getPersistentData().getByteArray(ModernContentBridge.LEGACY_ENTITY_DATA);
        });
        var bus = MinecraftForge.EVENT_BUS;
        bus.addListener(this::livingTick); bus.addListener(this::hurt); bus.addListener(this::fall);
        bus.addListener(this::breakSpeed); bus.addListener(this::tooltip);
        bus.addListener(this::loggedOut); bus.addListener(this::changedDimension); bus.addListener(this::serverTick);
    }
    public void diagnostics(Diagnostics value) { diagnostics = Objects.requireNonNull(value); }
    /** Adapter-owned player effects use the same scoped inventory/equipment transaction as forwarded events. */
    public void callback(Player player,ModernContentBridge.ActorAction action)throws Exception {
        session(player,(world,actor)->{action.run(actor);return null;});
    }
    public boolean baubles() { return LegacyPlayerArmor.baubles(services.loader()); }

    // ---- LegacyEquipment

    @Override public AutoCloseable register(int priority, Source source) {
        Objects.requireNonNull(source);
        var registration = new Registration(priority, order.getAndIncrement(), source);
        synchronized (this) {
            var next = new ArrayList<>(sources); next.add(registration);
            next.sort(Comparator.comparingInt(Registration::priority).reversed().thenComparingLong(Registration::order));
            sources = List.copyOf(next);
        }
        scopeBaubles();
        LegacyPlayerArmor.invalidate();
        return () -> {
            synchronized (this) { var next = new ArrayList<>(sources); next.remove(registration); sources = List.copyOf(next); }
            LegacyPlayerArmor.invalidate();
        };
    }
    @Override public void invalidate(Object hostPlayer) { Objects.requireNonNull(hostPlayer); LegacyPlayerArmor.invalidate(); }
    @Override public void setTicking(Slot slot, boolean enabled) {
        synchronized (this) { ticking = enabled ? ticking | 1 << slot.ordinal() : ticking & ~(1 << slot.ordinal()); }
    }
    @Override public boolean ticking(Slot slot) { return (ticking & 1 << slot.ordinal()) != 0; }

    /** MFCL's own priority-0 source: the host's four vanilla armor slots, deferring on an empty slot. */
    private static final class VanillaArmor implements Source {
        @Override public Object worn(Object hostPlayer, Slot slot, boolean client) {
            if (slot.bauble()) return null;
            var stack = ((Player) hostPlayer).getItemBySlot(ARMOR[slot.legacyIndex()]);
            return stack.isEmpty() ? null : stack;
        }
        @Override public void changed(Object hostPlayer, Slot slot, Object newHostStack) {
            ((Player) hostPlayer).setItemSlot(ARMOR[slot.legacyIndex()], (ItemStack) newHostStack);
        }
    }

    private final class ArmorSource implements LegacyPlayerArmor.Source {
        @Override public List<LegacyPlayerArmor.Worn> worn(UUID id, boolean client) {
            var player = player(id, client);
            if (player == null) return null;
            var supplied = new Supplied[LegacyPlayerArmor.SLOTS];
            var result = new ArrayList<LegacyPlayerArmor.Worn>(LegacyPlayerArmor.SLOTS);
            var registered = sources;
            for (int slot = 0; slot < LegacyPlayerArmor.SLOTS; slot++) {
                LegacyPlayerArmor.Worn worn = null;
                for (var registration : registered) {
                    Object value;
                    try { value = registration.source().worn(player, SLOTS[slot], client); }
                    catch (RuntimeException failure) { note("EQUIPMENT_SOURCE", SLOTS[slot].name(), failure); continue; }
                    if (value == null) continue;
                    if (!(value instanceof ItemStack stack)) {
                        note("EQUIPMENT_SOURCE", SLOTS[slot].name(), new IllegalArgumentException("Not a host ItemStack: " + value.getClass().getName()));
                        continue;
                    }
                    supplied[slot] = new Supplied(registration.source(), stack);
                    worn = new LegacyPlayerArmor.Worn(stack.isEmpty() ? null : legacy(stack));
                    break;
                }
                // Armor nobody supplies is empty, as on the host; bauble storage nobody supplies is left to original Baubles.
                if (worn == null && slot < 4) worn = new LegacyPlayerArmor.Worn(null);
                result.add(worn);
            }
            (client ? clientSupplied : serverSupplied).put(player.getUUID(), supplied);
            return Collections.unmodifiableList(result);
        }
        @Override public void changed(UUID id, int slot, LegacyClientData.Stack after) {
            var player = serverPlayer(id); var supplied = serverSupplied.get(id);
            if (player == null || supplied == null || supplied[slot] == null) return;
            ItemStack updated;
            try { updated = content.applyTo(supplied[slot].host(), after); }
            catch (RuntimeException unmapped) { note("EQUIPMENT_WRITE_BACK", SLOTS[slot].name(), unmapped); return; }
            supplied[slot] = new Supplied(supplied[slot].source(), updated);
            try { supplied[slot].source().changed(player, SLOTS[slot], updated); }
            catch (RuntimeException failure) { note("EQUIPMENT_WRITE_BACK", SLOTS[slot].name(), failure); }
        }
    }
    /** Enchantments and other host tags must not hide original equipment from old "is wearing" checks. */
    private LegacyClientData.Stack legacy(ItemStack stack) {
        try { var old = content.renderSnapshot(stack); return old != null ? old : content.gameplayStack(stack); }
        catch (Exception unmapped) { return null; }
    }
    /**
     * Original Baubles keys its storage by player name, so the integrated server and the client would share one
     * inventory. Once addons supply equipment, each server world gets its own, like the knowledge of other adapters.
     */
    private void scopeBaubles() {
        if (!baubles() || !baublesScoped.compareAndSet(false, true)) return;
        try {
            var field = services.loader().loadClass("baubles.common.lib.PlayerHandler").getDeclaredField("playerBaubles");
            field.setAccessible(true);
            var scoped = new LegacyPlayerState.ServerScoped(() -> null, field, HashMap::new);
            services.playerState().register(new LegacyPlayerState.Hooks() {
                @Override public String id() { return "mfcl:baubles-storage"; }
                @Override public AutoCloseable enter(Object world) throws Exception { return scoped.enter(world); }
            });
        } catch (ReflectiveOperationException failure) { note("EQUIPMENT_BAUBLES", "scope", failure); }
    }

    // ---- LegacyEvents

    @Override public AutoCloseable forward(Kind kind) {
        var count = forwarded.get(Objects.requireNonNull(kind)); count.incrementAndGet();
        var closed = new AtomicBoolean();
        return () -> { if (closed.compareAndSet(false, true)) count.decrementAndGet(); };
    }
    @Override public boolean forwarding(Kind kind) { return forwarded.get(kind).get() > 0; }

    private void livingTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof Player player) || !owned(player)) return;
        boolean update = forwarding(Kind.LIVING_UPDATE);
        if (!update && sources.size() == 1) return;
        try {
            boolean canceled = session(player, (world, actor) -> {
                if (update && events.update(actor)) return true;
                LegacyPlayerArmor.tick(services, world, actor, tickMask(player, update));
                return false;
            });
            if (canceled) event.setCanceled(true);
        } catch (Exception | LinkageError failure) { report("LIVING_UPDATE", failure); }
    }
    /** Supplied, non-vanilla, ticking slots; vanilla armor already ticks as a host item. */
    private int tickMask(Player player, boolean update) {
        var supplied = (player.level().isClientSide ? clientSupplied : serverSupplied).get(player.getUUID());
        if (supplied == null) return 0;
        int mask = 0, enabled = ticking;
        for (int slot = 0; slot < supplied.length; slot++)
            if (supplied[slot] != null && supplied[slot].source() != vanilla && !supplied[slot].host().isEmpty() && (enabled & 1 << slot) != 0) mask |= 1 << slot;
        // Original Baubles ticks its slots from the forwarded LivingUpdateEvent.
        if (update && baubles()) mask &= 0x0F;
        return mask;
    }
    private void hurt(LivingHurtEvent event) {
        if (!forwarding(Kind.LIVING_HURT) || !(event.getEntity() instanceof ServerPlayer player)) return;
        float amount = event.getAmount(); var damage = damage(event.getSource());
        try {
            var result = session(player, (world, actor) -> events.hurt(world, actor, damage, amount));
            if (result.canceled()) event.setCanceled(true);
            else if (result.value() != amount) event.setAmount(result.value());
        } catch (Exception | LinkageError failure) { report("LIVING_HURT", failure); }
    }
    private void fall(LivingFallEvent event) {
        if (!forwarding(Kind.LIVING_FALL) || !(event.getEntity() instanceof ServerPlayer player)) return;
        float distance = event.getDistance();
        try {
            var result = session(player, (world, actor) -> events.fall(actor, distance));
            if (result.canceled()) event.setCanceled(true);
            else if (result.value() != distance) event.setDistance(result.value());
        } catch (Exception | LinkageError failure) { report("LIVING_FALL", failure); }
    }
    private void breakSpeed(PlayerEvent.BreakSpeed event) {
        var player = event.getEntity();
        if (!forwarding(Kind.BREAK_SPEED) || !owned(player)) return;
        HostWorldAccess.Block block;
        try { block = content.legacyBlock(event.getState()); } catch (RuntimeException unmapped) { return; }
        if (block == null) return;
        var pos = event.getPosition().orElse(player.blockPosition()); float speed = event.getNewSpeed();
        try {
            var result = session(player, (world, actor) -> events.breakSpeed(actor, block.legacyKey(), block.metadata(), speed, pos.getX(), pos.getY(), pos.getZ()));
            if (result.canceled()) event.setCanceled(true);
            else if (result.value() != speed) event.setNewSpeed(result.value());
        } catch (Exception | LinkageError failure) { report("BREAK_SPEED", failure); }
    }
    private void tooltip(ItemTooltipEvent event) {
        var player = event.getEntity(); var stack = event.getItemStack();
        if (!forwarding(Kind.ITEM_TOOLTIP) || player == null || stack.isEmpty() || !player.level().isClientSide || !owned(player)) return;
        try {
            // Original items post the old ItemTooltipEvent from their own getTooltip.
            if (content.renderSnapshot(stack) != null) return;
            LegacyClientData.Stack old;
            try { old = content.gameplayStack(stack); } catch (UnsupportedOperationException unmapped) { return; }
            if (old == null) return;
            var initial = event.getToolTip().stream().map(Component::getString).toList();
            var added = session(player, (world, actor) -> events.tooltip(old, actor, initial, event.getFlags().isAdvanced()));
            added.forEach(line -> event.getToolTip().add(Component.literal(line)));
        } catch (Exception | LinkageError failure) { report("ITEM_TOOLTIP", failure); }
    }
    private static LegacyPlayerEvents.Damage damage(DamageSource source) {
        return new LegacyPlayerEvents.Damage(source.getMsgId(), source.is(DamageTypeTags.IS_FIRE), source.is(DamageTypeTags.IS_PROJECTILE),
                source.is(DamageTypes.MAGIC) || source.is(DamageTypes.INDIRECT_MAGIC), source.is(DamageTypeTags.IS_EXPLOSION),
                source.is(DamageTypeTags.BYPASSES_ARMOR), source.is(DamageTypeTags.BYPASSES_EFFECTS), source.is(DamageTypeTags.BYPASSES_INVULNERABILITY),
                source.getDirectEntity() == null ? null : source.getDirectEntity().getUUID(), source.getEntity() == null ? null : source.getEntity().getUUID());
    }

    /**
     * One pass over the player's persistent old actor: refreshed (equipment, state), the work, then everything old code
     * changed written back and its queued packets sent. Server work runs as a logic transaction.
     */
    private <T> T session(Player player, ActorWork<T> work) throws Exception {
        boolean client = player.level().isClientSide;
        if (Thread.holdsLock(services.items())) throw new Reentered();
        Object world = services.worlds().bound(player.level());
        var placer = ModernContentBridge.placer(player);
        java.util.concurrent.Callable<T> body = () -> services.items().inLegacy(() -> {
            try (var state = services.playerState().enter(world); var packets = client ? null : LegacyPackets.capture(player.getUUID())) {
                Object actor = content.itemUse().clientActor(world, placer, null);
                if (!client) {
                    services.playerData().restore(actor, player.getPersistentData().getByteArray(ModernContentBridge.LEGACY_ENTITY_DATA));
                    services.playerInventory().expose(actor, content.hostInventory(player));
                }
                T result = work.run(world, actor);
                content.writeBack(player, actor);
                if (packets != null && player instanceof ServerPlayer server) packets.messages().forEach(message -> ScanSyncChannel.sendToPlayer(server, message));
                return result;
            }
        });
        return client ? body.call() : content.logicTransaction(player.level(), player.blockPosition(), body, false);
    }
    /** Server players on the server; only the local player on the client. */
    private static boolean owned(Player player) {
        if (!player.level().isClientSide) return player instanceof ServerPlayer;
        return net.minecraftforge.fml.loading.FMLEnvironment.dist.isClient() && player == github.com.gengyoubo.mfcl.client.LocalPlayerAccess.player();
    }

    // ---- Persistent entity data: actors keep changes in memory; the host copy is refreshed at these points.

    private void loggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) { persist(player, player.level()); serverSupplied.remove(player.getUUID()); }
    }
    private void changedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        var from = player.server.getLevel(event.getFrom());
        if (from != null) persist(player, from);
    }
    private void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.getServer().getTickCount() % PERSIST_INTERVAL != 0) return;
        for (var player : event.getServer().getPlayerList().getPlayers()) persist(player, player.level());
    }
    private void persist(ServerPlayer player, net.minecraft.world.level.Level level) {
        try {
            Object world = services.worlds().findBound(level);
            if (world == null) return;
            byte[] data = content.itemUse().savePersistedIfPresent(world, ModernContentBridge.placer(player));
            if (data != null) player.getPersistentData().putByteArray(ModernContentBridge.LEGACY_ENTITY_DATA, data);
        } catch (Exception failure) { report("PLAYER_PERSIST", failure); }
    }

    // ---- Player state

    private final class Body implements LegacyPlayerBody.Host {
        @Override public float eyeHeight(UUID id, boolean client) {
            var player = player(id, client);
            return player == null ? 1.62f : player.getEyeHeight();
        }
        @Override public LegacyPlayerBody.State read(UUID id, boolean client) {
            var player = player(id, client);
            if (player == null) return null;
            var abilities = player.getAbilities(); var motion = player.getDeltaMovement();
            var effects = new TreeMap<Integer, LegacyPlayerBody.Effect>();
            for(var effect:content.potions().snapshot(player))effects.put(effect.id(),effect);
            return new LegacyPlayerBody.State(player.getId(), abilities.mayfly, abilities.flying, abilities.getWalkingSpeed(), abilities.getFlyingSpeed(),
                    player.fallDistance, player.getStepHeight() - STEP_OFFSET, motion.x, motion.y, motion.z, Collections.unmodifiableMap(effects));
        }
        @Override public void write(UUID id, boolean client, LegacyPlayerBody.State before, LegacyPlayerBody.State after) {
            var player = player(id, client);
            if (player == null) return;
            var abilities = player.getAbilities();
            if (before.allowFlying() != after.allowFlying() || before.flying() != after.flying() || before.walkSpeed() != after.walkSpeed() || before.flySpeed() != after.flySpeed()) {
                abilities.mayfly = after.allowFlying(); abilities.flying = after.flying();
                abilities.setWalkingSpeed(after.walkSpeed()); abilities.setFlyingSpeed(after.flySpeed());
                player.onUpdateAbilities();
            }
            if (before.fallDistance() != after.fallDistance()) player.fallDistance = after.fallDistance();
            if (before.stepHeight() != after.stepHeight()) player.setMaxUpStep(after.stepHeight() + STEP_OFFSET - (player.getStepHeight() - player.maxUpStep()));
            if (before.motionX() != after.motionX() || before.motionY() != after.motionY() || before.motionZ() != after.motionZ()) {
                player.setDeltaMovement(after.motionX(), after.motionY(), after.motionZ());
                if (!client) player.hurtMarked = true;
            }
            if (client) return; // effects are the server's
            for (var effect : after.effects().values()) {
                if (effect.equals(before.effects().get(effect.id()))) continue;
                content.potions().apply(player,effect);
            }
            for (var potion : before.effects().keySet()) if (!after.effects().containsKey(potion)) {
                var type = content.potions().effect(potion);
                if (type != null) player.removeEffect(type);
            }
        }
    }

    private static Player player(UUID id, boolean client) {
        if (client) {
            if (!net.minecraftforge.fml.loading.FMLEnvironment.dist.isClient()) return null;
            var local = github.com.gengyoubo.mfcl.client.LocalPlayerAccess.player();
            return local != null && (id == null || local.getUUID().equals(id)) ? local : null;
        }
        return serverPlayer(id);
    }
    private static ServerPlayer serverPlayer(UUID id) {
        var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        return server == null || id == null ? null : server.getPlayerList().getPlayer(id);
    }
    private void note(String code, String operation, Throwable failure) {
        try { diagnostics.note(code, operation, failure); } catch (RuntimeException ignored) { /* diagnostics are best effort */ }
    }
    private void report(String code, Throwable failure) {
        if (failure instanceof Reentered) return;
        note(code, "forward", failure);
        if (failures.add(code + failure)) LogUtils.getLogger().error("MFCL developer API {} failed", code, failure);
    }
}
