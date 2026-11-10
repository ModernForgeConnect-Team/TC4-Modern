package github.com.gengyoubo.mfcl.content;

import com.mojang.logging.LogUtils;
import github.com.gengyoubo.mfcl.bridge.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.RegisterEvent;
import java.util.*;

/** Registration barrier: frozen old declarations are published only inside modern RegisterEvents. */
public final class ModernContentBridge {
    public static final IntegerProperty METADATA = IntegerProperty.create("legacy_meta", 0, 15);
    public static final String STACK_TAG = "mfcl_legacy";
    /** Native-only enchantments and presentation survive original callbacks; mapped enchantments synchronize both ways. */
    private static final Set<String> HOST_OWNED_TAGS = Set.of("Enchantments", "RepairCost", "display");
    private static ModernContentBridge active;
    private final LegacyServices services;
    private final LegacyContent declarations;
    private final LegacyFluidRegistry fluidRegistry;
    private final LegacyBlockRendering blockRendering;
    private final LegacyBlockShapes blockShapes;
    private final LegacyItemUse itemUse;
    private HostDroppedItems droppedItems;
    private final LegacyCrafting crafting;
    private final LegacyOreDictionary oreDictionary;
    private final Set<String> legacyBlockKeys;
    private final HostBlockMapping hostBlocks;
    private final HostRepresentations hostRepresentations = new HostRepresentations(this);
    public HostRepresentations hostRepresentations() { return hostRepresentations; }
    private final ThreadLocal<PlacementTransaction> placement = new ThreadLocal<>();
    private final Map<String, Block> blocks = new LinkedHashMap<>();
    private final Map<String, Item> items = new LinkedHashMap<>();
    private final Map<Item, LegacyContent.Item> reverseItems = new IdentityHashMap<>();
    private final LegacyContentPack pack;
    private final LegacyEnchantmentBridge enchantments;
    private final LegacyPotionBridge potions;
    private final LegacyAttributeBridge attributes;
    private final LegacyStatisticsBridge statistics;
    private final LegacyVillagerBridge villagers;
    private final HostToolStacks hostTools;
    private final HostInventoryStacks hostInventoryStacks;
    private final CreativeTabBridge creative;
    private boolean blocksRegistered, itemsRegistered;
    private Map<Item,LegacyClientData.Stack> vanillaStacks;
    private BlockEntityType<LegacyBlockEntity> tileType;
    private net.minecraft.world.inventory.MenuType<LegacyDeviceMenu> deviceMenuType;
    private net.minecraft.world.entity.EntityType<LegacyHostedEntity> legacyEntityType;
    public net.minecraft.world.entity.EntityType<LegacyHostedEntity> legacyEntityType(){return Objects.requireNonNull(legacyEntityType,"Legacy entity registry window");}
    public net.minecraft.world.inventory.MenuType<LegacyDeviceMenu> deviceMenuType(){return Objects.requireNonNull(deviceMenuType,"Legacy device menu registry window");}

    private ModernContentBridge(LegacyServices services) throws Exception {
        this.services = services;
        this.attributes = new LegacyAttributeBridge(services);
        this.statistics = new LegacyStatisticsBridge(services);
        this.villagers = new LegacyVillagerBridge(this);
        this.enchantments = new LegacyEnchantmentBridge(this);
        this.potions = new LegacyPotionBridge(this);
        try (var context = new LegacyInventoryContext(services)) {
            declarations = new LegacyContent(services.items());
            blockShapes = new LegacyBlockShapes(services);
            blockRendering = new LegacyBlockRendering(services, declarations);
            itemUse = new LegacyItemUse(services);
            crafting = new LegacyCrafting(services);
            oreDictionary = new LegacyOreDictionary(services);
            var registry = services.loader().loadClass("net.minecraft.block.Block").getField("field_149771_c").get(null);
            legacyBlockKeys = Set.copyOf((Set<String>)registry.getClass().getMethod("func_148742_b").invoke(registry));
            hostBlocks = new HostBlockMapping(legacyBlockKeys);
        }
        fluidRegistry = new LegacyFluidRegistry(this);
        validateIds(declarations.blocks().stream().map(LegacyContent.Block::key).toList());
        validateIds(declarations.items().stream().map(LegacyContent.Item::key).toList());
        LogUtils.getLogger().info("MFCL content: generating resource pack");
        net.minecraftforge.fml.loading.progress.StartupNotificationManager.addModMessage("MFCL: Generating legacy resource pack");
        pack = new LegacyContentPack(services.gameDirectory(), declarations, blockRendering);
        statistics.generate(pack);
        villagers.generateSkins(pack);
        LegacyChestLoot.generate(pack);
        potions.generateIcons();
        LegacyBiomePack.generate(pack,services.biomes());
        LegacyDimensionPack.generate(pack,services.dimensions());
        var recipes = crafting.definitions();
        LogUtils.getLogger().info("MFCL content: exporting {} crafting recipes", recipes.size());
        int exportedRecipes = 0;
        for (var recipe : recipes) {
            pack.craftingRecipe(LegacyRegistryIds.recipeNamespace(recipe.output()), recipe.hash());
            if (++exportedRecipes % 4096 == 0) {
                String message = "Exporting crafting recipes (" + exportedRecipes + "/" + recipes.size() + ")";
                LogUtils.getLogger().info("MFCL content: {}", message);
                net.minecraftforge.fml.loading.progress.StartupNotificationManager.addModMessage("MFCL: " + message);
            }
        }
        var smeltingRecipes = crafting.smelting();
        LogUtils.getLogger().info("MFCL content: exporting {} smelting recipes", smeltingRecipes.size());
        for(var recipe:smeltingRecipes)pack.smeltingRecipe(LegacyRegistryIds.recipeNamespace(recipe.output()),recipe.hash());
        var variantsByItem=new HashMap<String,Set<Integer>>();
        for(var item:declarations.items())variantsByItem.put(item.key(),item.variants().stream().map(LegacyContent.Variant::metadata).collect(java.util.stream.Collectors.toSet()));
        oreDictionary.tag(services,variantsByItem);
        for(var tag:services.hostItemTags().entrySet())pack.itemTag(tag.getKey(),tag.getValue().stream().filter(key->declarations.items().stream().anyMatch(item->item.key().equals(key))).map(key->id(key).toString()).toList());
        creative = new CreativeTabBridge(this);
        hostTools = new HostToolStacks(services);
        hostInventoryStacks = new HostInventoryStacks(services);
    }
    public static ModernContentBridge install(LegacyServices services, IEventBus bus) {
        try {
            var bridge = new ModernContentBridge(services); active = bridge;
            ScanSyncChannel.register();
            LegacyCraftingRecipes.install(bus);
            LegacySmeltingRecipes.install(bus);
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(bridge::playerLoggedIn);
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(bridge::missingMappings);
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(bridge::arrowNock);
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(bridge::arrowLoose);
            bus.addListener(bridge::register); bus.addListener(bridge.pack::findPacks);
            bus.addListener(bridge.creative::buildVanillaContents);
            bus.addListener(bridge.attributes::players);
            LevelWorldBridge.install(bridge);
            bridge.droppedItems = new HostDroppedItems(bridge);
            LegacyCommands.install(bridge);
            bridge.players = new HostPlayers(bridge);
            new LegacySpawnBridge(bridge);
            HostCrossbowProjectiles.install(bridge);
            LogUtils.getLogger().info("MFCL content barrier: {} blocks, {} items declared before modern registration", bridge.declarations.blocks().size(), bridge.declarations.items().size());
            LogUtils.getLogger().info("MFCL legacy static models: {} items custom-rendered", bridge.declarations.customItems().size());
            for (String diagnostic : bridge.declarations.captureDiagnostics()) LogUtils.getLogger().info("MFCL capture: {}", diagnostic);
            LogUtils.getLogger().info("MFCL world ISBRH support: {}", bridge.blockRendering.supported());
            for (String diagnostic : bridge.blockRendering.diagnostics()) LogUtils.getLogger().info("MFCL world capture: {}", diagnostic);
            return bridge;
        } catch (Exception failure) { throw new IllegalStateException("Cannot freeze legacy content declarations", failure); }
    }
    public static ModernContentBridge active() { return active; }
    private HostPlayers players;
    /** Equipment sources, forwarded events and persistent player state of the developer API. */
    public HostPlayers players() { return players; }
    public LegacyContent declarations() { return declarations; }
    LegacyFluidRegistry fluidRegistry() { return fluidRegistry; }
    public java.util.Collection<net.minecraft.world.level.material.Fluid> legacyFluids(){return fluidRegistry.fluids();}
    public net.minecraft.world.level.material.Fluid legacyFluid(String name){return fluidRegistry.host(name);}
    public LegacyBlockRendering blockRendering() { return blockRendering; }
    public LegacyBlockShapes blockShapes() { return blockShapes; }
    public LegacyServices services() { return services; }
    public LegacyItemUse itemUse() {return itemUse;}
    public LegacyEnchantmentBridge enchantments() {return enchantments;}
    public LegacyPotionBridge potions() {return potions;}
    public LegacyAttributeBridge attributes() {return attributes;}
    public LegacyStatisticsBridge statistics() {return statistics;}
    public LegacyVillagerBridge villagers() {return villagers;}
    public LegacyCrafting crafting() {return crafting;}
    public LegacyContentPack pack() { return pack; }
    public CreativeTabBridge creative() { return creative; }
    public Map<String, Block> blocks() { return Collections.unmodifiableMap(blocks); }
    public Map<String, Item> items() { return Collections.unmodifiableMap(items); }
    public boolean registered() { return blocksRegistered && itemsRegistered; }
    public BlockEntityType<LegacyBlockEntity> tileType() { return Objects.requireNonNull(tileType, "Legacy block entity registry window"); }
    public static boolean canPlace(LegacyContent.Block entry) { return canPlace(entry, 0); }
    public static boolean canPlace(LegacyContent.Block entry, int metadata) {
        return entry.renderType() == 0 || active != null && active.blockRendering.supports(entry.key(), metadata);
    }
    /** Item-side gate: placement metadata is only known once the original callback picks it from the clicked face. */
    static boolean canPlaceSomeForm(LegacyContent.Block entry) {
        return entry.renderType() == 0 || active != null && active.blockRendering.supportsAny(entry.key());
    }
    public static final String LEGACY_ENTITY_DATA = "mfcl_legacy_entity";
    /** Generic public-API transaction; addon-specific research rules remain outside MFCL. */
    public <T> T withApiPlayer(net.minecraft.server.level.ServerPlayer player, java.util.function.Function<Object,T> operation) {
        if (!player.server.isSameThread()) throw new IllegalStateException("Player access requires the server thread");
        try {
            return services.items().inLegacy(() -> {
                Object world = services.worlds().bound(player.serverLevel());
                var source = placer(player);
                try (var state = services.playerState().enter(world)) {
                    Object actor = itemUse.clientActor(world, source, null);
                    byte[] saved = player.getPersistentData().getByteArray(LEGACY_ENTITY_DATA);
                    if (saved.length != 0) services.playerData().restore(actor, saved);
                    services.playerState().initialize(actor);
                    byte[] before = services.playerData().read(actor);
                    var inventory = hostInventory(player);
                    services.playerInventory().expose(actor, inventory);
                    T result;
                    java.util.List<LegacyPackets.Message> outgoing;
                    try (var packets = LegacyPackets.capture(player.getUUID())) {
                        try { result = operation.apply(actor); }
                        catch (RuntimeException | Error failure) { services.playerData().write(actor, before); throw failure; }
                        writeBack(player, actor);
                        outgoing = packets.messages();
                    }
                    byte[] after = services.playerData().read(actor);
                    var synchronizedPackets = itemUse.sync(world, source);
                    player.getPersistentData().putByteArray(LEGACY_ENTITY_DATA, after);
                    outgoing.forEach(packet -> ScanSyncChannel.sendToPlayer(player, packet));
                    synchronizedPackets.forEach(packet -> ScanSyncChannel.sendToPlayer(player, packet));
                    return result;
                }
            });
        } catch (RuntimeException failure) { throw failure; }
        catch (Exception failure) { throw new IllegalStateException("Legacy player transaction failed", failure); }
    }
    public interface ActorAction { void run(Object actor) throws Exception; }
    /**
     * Runs an original per-player callback (an old event handler or lifecycle hook) with the player's actor in its bound
     * world, inside the player state scope. Unlike {@link #withApiPlayer} it neither restores nor persists player data.
     * Returns false while the player's level has no bound legacy world.
     */
    public boolean withServerActor(net.minecraft.server.level.ServerPlayer player, ActorAction action) throws Exception {
        Object world = services.worlds().findBound(player.serverLevel());
        if (world == null) return false;
        services.items().inLegacy(() -> {
            try (var state = services.playerState().enter(world)) { action.run(itemUse.clientActor(world, placer(player), null)); }
            return null;
        });
        return true;
    }
    /**
     * The old client player, refreshed from the local player, for {@code withClientPlayer}. Client thread only; nothing
     * is persisted or written back.
     */
    public <T> T withApiClientPlayer(java.util.function.Function<Object,T> operation) {
        if (!net.minecraftforge.fml.loading.FMLEnvironment.dist.isClient() || !github.com.gengyoubo.mfcl.client.LocalPlayerAccess.onClientThread())
            throw new IllegalStateException("Client player access requires the client thread");
        var player = github.com.gengyoubo.mfcl.client.LocalPlayerAccess.player();
        if (player == null) throw new IllegalStateException("No local player");
        try {
            return services.items().inLegacy(() -> {
                Object world = services.worlds().bound(player.level());
                try (var state = services.playerState().enter(world)) { return operation.apply(itemUse.clientActor(world, placer(player), null)); }
            });
        } catch (RuntimeException failure) { throw failure; }
        catch (Exception failure) { throw new IllegalStateException("Legacy client player access failed", failure); }
    }
    /**
     * Applies what old code changed on a server actor to the host player: supplied equipment (through its sources),
     * main inventory slots, player state and attribute modifiers. Runs inside the runtime and player-state scope.
     */
    void writeBack(net.minecraft.world.entity.player.Player player, Object actor) throws Exception {
        LegacyPlayerBody.push(actor);
        if (player.level().isClientSide) return;
        LegacyPlayerArmor.collect(services, actor, player.getUUID());
        for (var change : services.playerInventory().changes(actor).entrySet())
            if (change.getKey() < 36) writeBackSlot(player, change.getKey(), change.getValue(), net.minecraft.world.InteractionHand.MAIN_HAND);
        player.getInventory().setChanged();
        LegacyPlayerAttributes.push(actor);
    }
    /** {@code LegacyMappings.applyTo} for item stacks: the converted stack, keeping host-owned tags and capabilities of an unchanged item. */
    public ItemStack applyTo(ItemStack existing, LegacyClientData.Stack old) {
        var updated = gameplayStack(old);
        if (existing.isEmpty() || updated.isEmpty() || existing.getItem() != updated.getItem()) return updated;
        var merged = existing.copy(); merged.setCount(updated.getCount());
        merged.setTag(updated.getTag() == null ? null : updated.getTag().copy());
        return keepHostTags(existing, merged);
    }
    private void playerLoggedIn(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)) return;
        byte[] data = player.getPersistentData().getByteArray(LEGACY_ENTITY_DATA);
        try {
            for(int slot=0;slot<player.getInventory().getContainerSize();slot++)enchantments.migrate(player.getInventory().getItem(slot));
            itemUse.loadPersisted(services.worlds().bound(player.serverLevel()), placer(player), data);
            itemUse.sync(services.worlds().bound(player.serverLevel()),placer(player)).forEach(message->ScanSyncChannel.sendToPlayer(player,message));
        } catch (Exception failure) {
            LogUtils.getLogger().error("MFCL legacy player data restore failed for {}", player.getName().getString(), failure);
        }
    }
    /** Applies a client-reported legacy scan on the authoritative server thread and persists the resulting research. */
    public void applyServerScan(net.minecraft.server.level.ServerPlayer player, ScanSyncChannel.ScanMessage message) {
        try {
            var held=snapshot(player.getUseItem());
            if(!player.isUsingItem()||held==null||services.scanners().forItem(held.key()).isEmpty()||player.getUseItemRemainingTicks()>8)return;
            var world = services.worlds().bound(player.serverLevel());
            byte[] prior = player.getPersistentData().contains(LEGACY_ENTITY_DATA) ? player.getPersistentData().getByteArray(LEGACY_ENTITY_DATA) : null;
            var updated = itemUse.applyScan(world, placer(player), held, new LegacyItemUse.Scan(message.type(), message.id(), message.meta(), message.entityId(), message.phenomena()), message.prefix(), prior);
            player.getPersistentData().putByteArray(LEGACY_ENTITY_DATA, updated.data());
            updated.messages().forEach(packet->ScanSyncChannel.sendToPlayer(player,packet));
            LogUtils.getLogger().info("MFCL legacy server scan for {}: accepted={}, type={}, id={}, meta={}", player.getName().getString(),updated.accepted(), message.type(), message.id(), message.meta());
        } catch (Exception failure) {
            LogUtils.getLogger().error("MFCL legacy server scan application failed for {}", player.getName().getString(), failure);
        }
    }
    /** Executes a legacy client packet from the Thaumonomicon (purchase research / create note) for the real player. */
    public void applyServerLegacyMessage(net.minecraft.server.level.ServerPlayer player, LegacyPackets.Message message) {
        try {
            Object world=services.worlds().bound(player.serverLevel());
            var source=placer(player);
            services.items().inLegacy(()->{
                try(var state=services.playerState().enter(world);var outgoing=LegacyPackets.capture(player.getUUID())) {
                    Object actor=itemUse.clientActor(world,source,null);
                    prepareActorContainers(actor);
                    var before=hostInventory(player);
                    services.playerInventory().write(actor,before);
                    services.playerInventory().select(actor,player.getInventory().selected);
                    byte[] persisted=player.getPersistentData().getByteArray(LEGACY_ENTITY_DATA);
                    if(persisted.length!=0)services.playerData().write(actor,persisted);
                    services.playerState().initialize(actor);
                    byte[] prior=services.playerData().read(actor);
                    try { logicTransaction(player.serverLevel(),player.blockPosition(),()->{LegacyPackets.receiveServer(services,message,actor,null);return null;},true); }
                    catch(Exception failure){services.playerData().write(actor,prior);throw failure;}
                    collectLegacyInventory(player,actor,before);
                    player.getPersistentData().putByteArray(LEGACY_ENTITY_DATA,services.playerData().read(actor));
                    outgoing.messages().forEach(packet->ScanSyncChannel.sendToPlayer(player,packet));
                    return null;
                }
            });
        } catch(Exception failure) { github.com.gengyoubo.mfcl.bridge.LegacyDiagnostics.legacyFailure("book packet "+message.channel()+"#"+message.discriminator(),failure); LogUtils.getLogger().error("MFCL original book packet failed for {}",player.getName().getString(),failure); }
    }
    /** Original ArrowNockEvent: an old handler that cancels it (Thaumcraft primal arrows) draws the host bow without vanilla ammo. */
    private void arrowNock(net.minecraftforge.event.entity.player.ArrowNockEvent event) {
        var result=legacyBowEvent(event.getEntity(),event.getLevel(),event.getBow(),-1);
        if(result==null||!result.canceled())return;
        event.getEntity().startUsingItem(event.getHand());
        event.setAction(net.minecraft.world.InteractionResultHolder.consume(event.getBow()));
    }
    /** Original ArrowLooseEvent: a canceled old event already fired its own projectile and used its own ammo, so the vanilla shot is skipped. */
    private void arrowLoose(net.minecraftforge.event.entity.player.ArrowLooseEvent event) {
        var player=event.getEntity();var hand=player.getUsedItemHand();
        var result=legacyBowEvent(player,event.getLevel(),event.getBow(),event.getCharge());
        if(result==null||!result.canceled())return;
        event.setCanceled(true);
        // The old handler wore its view of the bow; wear the host bow the same way (host Unbreaking applies, as the old one did).
        if(result.bowDamaged()&&!event.getLevel().isClientSide)event.getBow().hurtAndBreak(1,player,user->user.broadcastBreakEvent(hand));
    }
    private LegacyItemUse.BowEvent legacyBowEvent(net.minecraft.world.entity.player.Player player, Level level, ItemStack stack, int charge) {
        // Host enchantment NBT has no old mapping; the old handler sees a plain bow plus the four bow enchantments by old id.
        var plain=new ItemStack(stack.getItem());if(stack.isDamageableItem())plain.setDamageValue(stack.getDamageValue());
        var bow=toLegacy(plain);if(bow==null)return null;
        var enchantments=new HashMap<Integer,Integer>();
        Map.of(net.minecraft.world.item.enchantment.Enchantments.POWER_ARROWS,48,net.minecraft.world.item.enchantment.Enchantments.PUNCH_ARROWS,49,
                net.minecraft.world.item.enchantment.Enchantments.FLAMING_ARROWS,50,net.minecraft.world.item.enchantment.Enchantments.INFINITY_ARROWS,51)
                .forEach((enchantment,id)->{int value=net.minecraft.world.item.enchantment.EnchantmentHelper.getItemEnchantmentLevel(enchantment,stack);if(value>0)enchantments.put(id,value);});
        try {
            var world=services.worlds().bound(level);var source=placer(player);
            var before=hostInventory(player);before.set(player.getInventory().selected,bow);
            Object actor=exposeLegacyInventory(player,world,before,bow);
            LegacyItemUse.BowEvent result;
            try {
                result=level.isClientSide?itemUse.bowEvent(world,source,bow,enchantments,charge)
                        :logicTransaction(level,player.blockPosition(),()->LegacyItemDrops.capture(this,player,()->itemUse.bowEvent(world,source,bow,enchantments,charge)),true);
            } catch(Exception failure){services.playerInventory().write(actor,before);throw failure;}
            // Deliberate deviation: TC4's bowShot ignores creative mode (only Infinity's 33% roll skips ammo); creative never spends it.
            if(!level.isClientSide&&charge>=0&&result.canceled()&&player.getAbilities().instabuild)keepCreativeAmmo(actor,before,player.getInventory().selected);
            // The client only predicts; the server's consumed ammo reaches it through the normal inventory sync.
            if(!level.isClientSide)collectLegacyInventory(player,actor,before);
            return result;
        } catch(Exception failure) { LogUtils.getLogger().error("MFCL original bow event failed for {}",player.getName().getString(),failure); return null; }
    }
    /** Restores every non-held stack the old bow handler shrank or used up, so the collected inventory keeps the ammo. */
    private void keepCreativeAmmo(Object actor,List<LegacyClientData.Stack> before,int held) throws Exception {
        var after=new ArrayList<>(services.playerInventory().read(actor));boolean restored=false;
        for(int slot=0;slot<after.size()&&slot<before.size();slot++) {
            var prior=before.get(slot);var now=after.get(slot);
            if(slot==held||prior==null)continue;
            boolean spent=now==null||prior.key().equals(now.key())&&prior.metadata()==now.metadata()&&prior.tag().equals(now.tag())&&now.count()<prior.count();
            if(spent){after.set(slot,prior);restored=true;}
        }
        if(restored)services.playerInventory().write(actor,after);
    }
    private void prepareActorContainers(Object actor) throws Exception {
        var loader=services.loader();
        var playerType=loader.loadClass("net.minecraft.entity.player.EntityPlayer");
        Object inventory=playerType.getField("field_71071_by").get(actor);
        Object container=loader.loadClass("net.minecraft.inventory.ContainerPlayer").getConstructor(loader.loadClass("net.minecraft.entity.player.InventoryPlayer"),boolean.class,playerType).newInstance(inventory,false,actor);
        playerType.getField("field_71069_bz").set(actor,container);
        playerType.getField("field_71070_bA").set(actor,container);
    }
    public static ResourceLocation id(String key) {
        return LegacyRegistryIds.id(key);
    }
    private static void validateIds(List<String> keys) {
        LegacyRegistryIds.validate(keys);
    }
    private void missingMappings(net.minecraftforge.registries.MissingMappingsEvent event) {
        LegacyRegistryIds.remap(event.getMappings(Registries.ITEM,"mfcl"),items);
        LegacyRegistryIds.remap(event.getMappings(Registries.BLOCK,"mfcl"),blocks);
    }
    private void register(RegisterEvent event) {
        fluidRegistry.register(event);
        LegacyChestLoot.register(event);
        attributes.register(event);
        statistics.register(event);
        villagers.register(event);
        enchantments.register(event);
        potions.register(event);
        event.register(Registries.BIOME_SOURCE,registry->registry.register(ResourceLocation.parse("mfcl:thaumcraft_biomes"),github.com.gengyoubo.mfcl.compat.thaumcraft.ThaumcraftBiomeSource.CODEC));
        event.register(Registries.BLOCK, registry -> {
            if (blocksRegistered) throw new IllegalStateException("Legacy block registration repeated");
            for (var entry : declarations.blocks()) {
                if (BuiltInRegistries.BLOCK.containsKey(id(entry.key()))) throw new IllegalStateException("Host already owns block ID " + entry.key());
                var block = entry.tileEntity() ? new MirroredTileBlock(entry) : new MirroredBlock(entry);
                registry.register(id(entry.key()), block); blocks.put(entry.key(), block);
            }
            blocksRegistered = true;
        });
        event.register(Registries.ITEM, registry -> {
            if (!blocksRegistered || itemsRegistered) throw new IllegalStateException("Legacy registry window ordering violated");
            for (var entry : declarations.items()) {
                if (BuiltInRegistries.ITEM.containsKey(id(entry.key()))) throw new IllegalStateException("Host already owns item ID " + entry.key());
                var properties = new Item.Properties().stacksTo(entry.maxDamage() > 0 ? 1 : entry.stackLimit());
                if (entry.maxDamage() > 0) properties.durability(entry.maxDamage());
                Item item = entry.blockKey() != null && blocks.containsKey(entry.blockKey())
                        ? new MirroredBlockItem((MirroredBlock) blocks.get(entry.blockKey()), properties, entry)
                        : entry.armor() == null ? new MirroredItem(properties, entry)
                        : entry.armor().dyeable() ? new MirroredDyeableArmorItem(properties, entry) : new MirroredArmorItem(properties, entry);
                registry.register(id(entry.key()), item); items.put(entry.key(), item); reverseItems.put(item, entry);
            }
            itemsRegistered = true;
            LogUtils.getLogger().info("MFCL_CONTENT_REGISTRATION_PASS: blocks={} items={}", blocks.size(), items.size());
        });
        event.register(Registries.CREATIVE_MODE_TAB, creative::register);
        event.register(Registries.MENU,registry->{
            deviceMenuType=net.minecraftforge.common.extensions.IForgeMenuType.create(LegacyDeviceMenu::fromNetwork);
            registry.register(ResourceLocation.fromNamespaceAndPath("mfcl","legacy_device"),deviceMenuType);
        });
        event.register(Registries.ENTITY_TYPE, registry -> {
            // One envelope type for every original entity; its renderer and dimensions come from the hosted original.
            legacyEntityType = net.minecraft.world.entity.EntityType.Builder.<LegacyHostedEntity>of(LegacyHostedEntity::new, net.minecraft.world.entity.MobCategory.MISC)
                    .sized(0.25f, 0.25f).clientTrackingRange(8).updateInterval(1).setShouldReceiveVelocityUpdates(true).fireImmune().build("mfcl:legacy_entity");
            registry.register(ResourceLocation.fromNamespaceAndPath("mfcl", "legacy_entity"), legacyEntityType);
        });
        event.register(Registries.BLOCK_ENTITY_TYPE, registry -> {
            var supported = declarations.blocks().stream().filter(LegacyContent.Block::tileEntity).map(entry -> blocks.get(entry.key())).toArray(Block[]::new);
            if (supported.length != 0) {
                tileType = BlockEntityType.Builder.of((pos, state) -> new LegacyBlockEntity(this, pos, state), supported).build(null);
                registry.register(ResourceLocation.tryParse("mfcl:legacy_tile_1_7_10"), tileType);
            }
        });
    }
    public ItemStack mapStack(LegacyClientData.Stack old) {
        if (old.key().equals(LegacyHostTools.PICKAXE) || old.key().equals(LegacyHostStacks.ITEM)) return gameplayStack(old);
        var represented = hostRepresentations.stack(old);
        if (!represented.isEmpty()) return represented;
        return items.containsKey(old.key()) ? stack(old.key(), old.metadata(), old.count(), old.tag()) : HostStackMapping.renderStack(old);
    }
    public LegacyClientData.Stack gameplayStack(ItemStack stack) throws Exception {
        if (stack.isEmpty()) return null;
        if (!reverseItems.containsKey(stack.getItem())) {
            var tool = hostTools.toLegacy(stack); if (tool != null) return tool;
        }
        var vanillaLoot=LegacyVanillaStacks.toLegacy(services,stack);if(vanillaLoot!=null)return vanillaLoot;
        var represented = hostRepresentations.stack(stack);
        if (represented != null) return represented;
        if (reverseItems.containsKey(stack.getItem())) return snapshot(stack);
        if (stack.hasTag() && stack.getTag().getAllKeys().stream().anyMatch(key -> !key.equals("Damage")))
            throw new UnsupportedOperationException("Unmapped host inventory NBT: " + stack);
        var nonCreative = HostStackMapping.nonCreativeStack(stack);
        if (nonCreative != null) return nonCreative;
        if (vanillaStacks == null) {
            var mapped = new IdentityHashMap<Item,LegacyClientData.Stack>();
            for (var original : services.items().catalog()) if (original.key().startsWith("minecraft:") && original.tag().isEmpty()) {
                var modern = HostStackMapping.renderStack(original);
                if (!modern.isEmpty()) mapped.putIfAbsent(modern.getItem(),original);
            }
            vanillaStacks = mapped;
        }
        var original = vanillaStacks.get(stack.getItem());
        if (original == null) {
            github.com.gengyoubo.mfcl.bridge.LegacyDiagnostics.missingMapping("item "+BuiltInRegistries.ITEM.getKey(stack.getItem()),"host item has no original representation");
            throw new UnsupportedOperationException("No original item representation: " + BuiltInRegistries.ITEM.getKey(stack.getItem()));
        }
        return new LegacyClientData.Stack(original.key(),stack.isDamageableItem() ? stack.getDamageValue() : original.metadata(),stack.getCount(),original.name(),"");
    }
    public ItemStack gameplayStack(LegacyClientData.Stack stack) {
        if (stack == null || stack.count() <= 0) return ItemStack.EMPTY;
        if (stack.key().equals(LegacyHostStacks.ITEM)) {
            try { return hostInventoryStacks.toHost(stack); }
            catch (Exception failure) { throw new IllegalStateException("Cannot restore native inventory stack", failure); }
        }
        if (stack.key().equals(LegacyHostTools.PICKAXE)) {
            try { return hostTools.toHost(stack); }
            catch (Exception failure) { throw new IllegalStateException("Cannot restore native pickaxe", failure); }
        }
        var vanillaLoot=LegacyVanillaStacks.toHost(services,stack);if(!vanillaLoot.isEmpty())return vanillaLoot;
        var represented = hostRepresentations.stack(stack);
        if (!represented.isEmpty()) return represented;
        if (!items.containsKey(stack.key()) && !stack.tag().isEmpty()) throw new UnsupportedOperationException("Original inventory NBT needs a host mapping: " + stack.key());
        var mapped = mapStack(stack);
        if (mapped.isEmpty()) {
            github.com.gengyoubo.mfcl.bridge.LegacyDiagnostics.missingMapping("legacy item " + stack.key(), "original item has no host item");
            throw new UnsupportedOperationException("Original inventory item needs a host mapping: " + stack.key());
        }
        return mapped;
    }
    public ItemStack stack(String key, int metadata, int count, String tag) {
        Item item = items.get(key); if (item == null) return ItemStack.EMPTY;
        ItemStack stack = new ItemStack(item, count);
        if (item.canBeDepleted()) stack.setDamageValue(metadata);
        if ((!item.canBeDepleted() && metadata != 0) || (tag != null && !tag.isEmpty())) {
            var legacy = stack.getOrCreateTagElement(STACK_TAG);
            if (!item.canBeDepleted()) legacy.putInt("metadata", metadata);
            if (tag != null && !tag.isEmpty()) legacy.putString("snbt", tag);
        }
        try {enchantments.importOld(stack,new LegacyClientData.Stack(key,metadata,count,"",tag==null?"":tag));}
        catch(Exception failure){throw new IllegalStateException("Cannot expose original enchantments for "+key,failure);}
        return stack;
    }
    public static int metadata(ItemStack stack) {
        if (stack.isDamageableItem()) return stack.getDamageValue();
        CompoundTag tag = stack.getTagElement(STACK_TAG); return tag == null ? 0 : tag.getInt("metadata");
    }
    public LegacyClientData.Stack snapshot(ItemStack stack) {
        var item = reverseItems.get(stack.getItem()); if (item == null) return null;
        if (stack.hasTag()) for (String key : stack.getTag().getAllKeys())
            if (!key.equals(STACK_TAG) && !key.equals("Damage") && !HOST_OWNED_TAGS.contains(key) && !(key.equals("BlockEntityTag") && stack.getItem() instanceof MirroredBlockItem)) throw new UnsupportedOperationException("Host item NBT needs an explicit legacy mapping: " + key);
        CompoundTag tag = stack.getTagElement(STACK_TAG);
        var original=new LegacyClientData.Stack(item.key(), metadata(stack), stack.getCount(), "", tag == null ? "" : tag.getString("snbt"));
        try{return enchantments.exportNative(stack,original);}catch(Exception failure){throw new IllegalStateException("Cannot map native enchantments",failure);}
    }
    LegacyClientData.Stack storedSnapshot(ItemStack stack) {
        var item=reverseItems.get(stack.getItem());if(item==null)return null;
        var stored=stack.getTagElement(STACK_TAG);
        return new LegacyClientData.Stack(item.key(),metadata(stack),stack.getCount(),"",stored==null?"":stored.getString("snbt"));
    }
    /** Read-only view for old renderers/tooltips. Host-only tags must not prevent drawing an item. */
    public LegacyClientData.Stack renderSnapshot(ItemStack stack) throws Exception {
        var item = reverseItems.get(stack.getItem()); if (item == null) return null;
        CompoundTag stored = stack.getTagElement(STACK_TAG);
        String snbt = stored == null ? "" : stored.getString("snbt");
        var original = enchantments.exportNative(stack,new LegacyClientData.Stack(item.key(), metadata(stack), stack.getCount(), "", snbt));
        CompoundTag host = stack.getTag(); if (host == null) return original;
        CompoundTag display = host.getCompound("display");
        Integer cost = host.contains("RepairCost", 3) ? host.getInt("RepairCost") : null;
        Integer color = display.contains("color", 3) ? display.getInt("color") : null;
        String name = stack.hasCustomHoverName() ? stack.getHoverName().getString() : null;
        // 1.7.10 SNBT uses indexed lists and cannot be read by the modern TagParser.
        return cost == null && color == null && name == null ? original : services.items().renderTags(original, cost, color, name);
    }
    public HostWorldAccess.Block legacyBlock(BlockState state) {
        var represented = hostRepresentations.block(state);
        if (represented != null) return represented;
        if (state.getBlock() instanceof MirroredBlock mirrored) return new HostWorldAccess.Block(mirrored.entry.key(), state.getValue(METADATA));
        return hostBlocks.read(state);
    }
    public BlockState modernBlock(HostWorldAccess.Block old) {
        var represented = hostRepresentations.block(old);
        if (represented != null) return represented;
        var vanilla=hostBlocks.write(old);
        if(vanilla!=null)return vanilla;
        Block block = blocks.get(old.legacyKey());
        if (block != null) {
            if (!canPlace(((MirroredBlock) block).entry, old.metadata())) throw new UnsupportedOperationException("Legacy special rendering/placement bridge is pending: " + old.legacyKey() + "@" + old.metadata());
            if (old.metadata() < 0 || old.metadata() > 15) throw new IllegalArgumentException("Legacy block metadata outside 0..15");
            return block.defaultBlockState().setValue(METADATA, old.metadata());
        }
        if (old.metadata() != 0) throw new UnsupportedOperationException("No vanilla block metadata mapping for " + old);
        String key = old.legacyKey().equals("minecraft:grass") ? "minecraft:grass_block" : old.legacyKey();
        var id = ResourceLocation.tryParse(key);
        if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) throw new UnsupportedOperationException("No host block representation: " + old);
        return BuiltInRegistries.BLOCK.get(id).defaultBlockState();
    }
    private Component name(LegacyContent.Item entry, ItemStack stack) {
        try {
            CompoundTag stored=stack.getTagElement(STACK_TAG);
            return Component.literal(services.items().displayName(new LegacyClientData.Stack(entry.key(),metadata(stack),stack.getCount(),"",stored==null?"":stored.getString("snbt"))));
        }catch(Exception failure){LogUtils.getLogger().debug("Cannot evaluate original item name for {}",entry.key(),failure);}
        int metadata = metadata(stack);
        var variant = entry.variants().stream().filter(v -> v.metadata() == metadata).findFirst().orElse(entry.variants().get(0));
        return Component.translatable(variant.nameKey());
    }
    private int stackLimit(LegacyContent.Item entry,ItemStack stack) {
        if(entry.maxDamage()>0)return 1;
        try {return Math.max(1,Math.min(64,services.items().stackLimit(renderSnapshot(stack))));}
        catch(Exception failure){LogUtils.getLogger().debug("Cannot evaluate original stack limit for {}",entry.key(),failure);return entry.stackLimit();}
    }
    private void tooltip(LegacyContent.Item entry, ItemStack stack, List<Component> lines) {
        lines.add(Component.literal("1.7.10 · " + entry.key() + " @ " + metadata(stack)).withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        if (entry.blockKey() != null && blocks.get(entry.blockKey()) instanceof MirroredBlock block) {
            if (!canPlace(block.entry, metadata(stack))) lines.add(Component.translatable("mfcl.content.special_block_pending").withStyle(net.minecraft.ChatFormatting.YELLOW));
            // Only runtime evidence: original tiles tick and interact; a warning without a failure would be a false alarm.
            else if (block.entry.tileEntity() && LegacyBlockEntity.logicPaused(block.entry.key(),metadata(stack)))
                lines.add(Component.translatable("mfcl.content.machine_logic_pending").withStyle(net.minecraft.ChatFormatting.YELLOW));
        }
    }
    private static net.minecraft.world.item.ArmorItem.Type armorType(LegacyContent.Item entry) {
        return switch (entry.armor().slot()) {
            case 0 -> net.minecraft.world.item.ArmorItem.Type.HELMET; case 1 -> net.minecraft.world.item.ArmorItem.Type.CHESTPLATE;
            case 2 -> net.minecraft.world.item.ArmorItem.Type.LEGGINGS; default -> net.minecraft.world.item.ArmorItem.Type.BOOTS;
        };
    }
    /** One host material per original ItemArmor, carrying its own durability, points and enchantability. */
    private record LegacyArmorMaterial(LegacyContent.Item entry) implements net.minecraft.world.item.ArmorMaterial {
        @Override public int getDurabilityForType(net.minecraft.world.item.ArmorItem.Type type) { return entry.maxDamage(); }
        @Override public int getDefenseForType(net.minecraft.world.item.ArmorItem.Type type) { return entry.armor().protection(); }
        @Override public int getEnchantmentValue() { return entry.armor().enchantability(); }
        @Override public net.minecraft.sounds.SoundEvent getEquipSound() { return net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_GENERIC; }
        @Override public net.minecraft.world.item.crafting.Ingredient getRepairIngredient() { return net.minecraft.world.item.crafting.Ingredient.EMPTY; }
        @Override public String getName() { return "mfcl_legacy"; }
        @Override public float getToughness() { return 0; }
        @Override public float getKnockbackResistance() { return 0; }
    }
    /**
     * Original ItemArmor as a real host ArmorItem, so equipping, the worn layer, durability loss and armor enchanting
     * follow vanilla rules. Subclasses that replace old right-click equipping retain their original callback/GUI.
     */
    private class MirroredArmorItem extends net.minecraft.world.item.ArmorItem {
        final LegacyContent.Item entry;
        MirroredArmorItem(Properties properties, LegacyContent.Item entry) { super(new LegacyArmorMaterial(entry), armorType(entry), properties); this.entry = entry; }
        @Override public net.minecraftforge.common.capabilities.ICapabilityProvider initCapabilities(ItemStack stack,CompoundTag nbt) {
            return entry!=null&&fluidRegistry.isContainerItem(entry.key())?new LegacyFluidItemHandler(ModernContentBridge.this,stack):super.initCapabilities(stack,nbt);
        }
        @Override public int getEntityLifespan(ItemStack stack, Level level) { return droppedItems.lifespan(stack, level); }
        @Override public net.minecraft.world.InteractionResultHolder<ItemStack> use(Level level,net.minecraft.world.entity.player.Player player,net.minecraft.world.InteractionHand hand) {
            try {
                if(itemUse.customArmorUse(snapshot(player.getItemInHand(hand))))return useLegacyHeldItem(level,player,hand,entry.key());
            }catch(Exception failure){LogUtils.getLogger().error("Cannot inspect original armor use for {}",entry.key(),failure);return net.minecraft.world.InteractionResultHolder.fail(player.getItemInHand(hand));}
            return super.use(level,player,hand);
        }
        @Override public boolean isValidRepairItem(ItemStack target, ItemStack repair) { return legacyRepairable(target, repair); }
        @Override public void inventoryTick(ItemStack stack, Level level, net.minecraft.world.entity.Entity entity, int slot, boolean selected) {
            // Inventory.tick also visits armor slots; only the stack actually worn gets the original onArmorTick.
            // 1.7.10 ran it on both sides: movement is the client's, effects and durability the server's.
            if (!entry.armor().ticks() || !(entity instanceof net.minecraft.world.entity.player.Player player) || player.getItemBySlot(getEquipmentSlot()) != stack) return;
            if (level.isClientSide ? player.isLocalPlayer() : player instanceof net.minecraft.server.level.ServerPlayer) armorTick(entry, stack, level, player);
        }
        @Override public Component getName(ItemStack stack) { return name(entry, stack); }
        @Override public void appendHoverText(ItemStack stack, Level level, List<Component> lines, TooltipFlag flag) { tooltip(entry, stack, lines); }
        @Override public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientItemExtensions> consumer) {
            consumer.accept(customRenderer(() -> entry == null ? null : entry.key()));
        }
        @Override public String getArmorTexture(ItemStack stack, net.minecraft.world.entity.Entity entity, net.minecraft.world.entity.EquipmentSlot slot, String type) {
            if (type == null) return pack.armorTexture(entry.armor().texture());
            if (!type.equals("overlay")) return null;
            String overlay = pack.armorTexture(entry.armor().overlay());
            return overlay == null ? LegacyContentPack.TRANSPARENT_ARMOR : overlay;
        }
    }
    /** Dyed like leather (robes...): host dye recipes and the worn tint use the original getColor. */
    private final class MirroredDyeableArmorItem extends MirroredArmorItem implements net.minecraft.world.item.DyeableLeatherItem {
        MirroredDyeableArmorItem(Properties properties, LegacyContent.Item entry) { super(properties, entry); }
        @Override public int getColor(ItemStack stack) {
            try { return itemUse.armorColor(renderSnapshot(stack)) & 0xFFFFFF; }
            catch (Exception failure) { return net.minecraft.world.item.DyeableLeatherItem.super.getColor(stack); }
        }
    }
    private final Set<String> displayFailures = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final Set<String> collisionFailures = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final Set<String> armorTickFailures = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private void armorTick(LegacyContent.Item entry, ItemStack stack, Level level, net.minecraft.world.entity.player.Player player) {
        if (armorTickFailures.contains(entry.key())) return;
        try {
            var worn = renderSnapshot(stack);
            var motion = player.getDeltaMovement();
            var body = new LegacyItemUse.Body(motion.x, motion.y, motion.z, player.fallDistance, player.onGround(), player.isInWater(), player.zza, player.xxa, player.tickCount);
            var world = services.worlds().bound(level); var source = placer(player);
            var result = level.isClientSide ? itemUse.armorTick(world, source, worn, body)
                    : logicTransaction(level, player.blockPosition(), () -> itemUse.armorTick(world, source, worn, body), false);
            var after = result.body();
            if (level.isClientSide) {
                var change = new net.minecraft.world.phys.Vec3(after.motionX() - body.motionX(), after.motionY() - body.motionY(), after.motionZ() - body.motionZ());
                if (change.lengthSqr() > 0) player.setDeltaMovement(player.getDeltaMovement().add(change));
            } else {
                // Resolve configurable original IDs through the native effect mapping.
                for (var effect : result.effects()) {
                    var type = potions.effect(effect.id());
                    if (type != null) player.addEffect(new net.minecraft.world.effect.MobEffectInstance(type, effect.duration(), effect.amplifier(), effect.ambient(), true));
                }
                if (result.stack() != null && stack.isDamageableItem() && result.stack().metadata() != stack.getDamageValue()) stack.setDamageValue(result.stack().metadata());
            }
            if (after.fallDistance() != body.fallDistance()) player.fallDistance = after.fallDistance();
        } catch (Exception | LinkageError failure) {
            if (armorTickFailures.add(entry.key())) LogUtils.getLogger().error("MFCL original onArmorTick failed for {}; disabled for this item", entry.key(), failure);
        }
    }
    /** Original Item.getIsRepairable (armor/tool material); unmappable stacks never repair. */
    private boolean legacyRepairable(ItemStack target, ItemStack repair) {
        if (target.isEmpty() || repair.isEmpty()) return false;
        try {
            var old = renderSnapshot(target);
            var material = renderSnapshot(repair); if (material == null) material = gameplayStack(repair);
            return old != null && material != null && itemUse.repairable(old, material);
        } catch (Exception unmapped) { return false; }
    }
    private final class MirroredItem extends Item {
        private final LegacyContent.Item entry;
        MirroredItem(Properties properties, LegacyContent.Item entry) { super(properties); this.entry = entry; }
        @Override public net.minecraftforge.common.capabilities.ICapabilityProvider initCapabilities(ItemStack stack,CompoundTag nbt) {
            return entry!=null&&fluidRegistry.isContainerItem(entry.key())?new LegacyFluidItemHandler(ModernContentBridge.this,stack):super.initCapabilities(stack,nbt);
        }
        @Override public int getEntityLifespan(ItemStack stack, Level level) { return droppedItems.lifespan(stack, level); }
        @Override public int getMaxStackSize(ItemStack stack) { return entry==null?super.getMaxStackSize(stack):stackLimit(entry,stack); }
        @Override public Component getName(ItemStack stack) { return name(entry, stack); }
        @Override public void appendHoverText(ItemStack stack, Level level, List<Component> lines, TooltipFlag flag) { tooltip(entry, stack, lines); }
        @Override public boolean isValidRepairItem(ItemStack target, ItemStack repair) { return legacyRepairable(target, repair); }
        @Override public com.google.common.collect.Multimap<net.minecraft.world.entity.ai.attributes.Attribute,net.minecraft.world.entity.ai.attributes.AttributeModifier> getAttributeModifiers(net.minecraft.world.entity.EquipmentSlot slot,ItemStack stack){
            var result=com.google.common.collect.HashMultimap.<net.minecraft.world.entity.ai.attributes.Attribute,net.minecraft.world.entity.ai.attributes.AttributeModifier>create();
            if(slot!=net.minecraft.world.entity.EquipmentSlot.MAINHAND)return result;
            try{for(var value:itemUse.itemAttributes(snapshot(stack))){var attribute=HostPlayerAttributes.type(value.name());if(attribute==null)continue;var m=value.modifier();result.put(attribute,new net.minecraft.world.entity.ai.attributes.AttributeModifier(m.id(),m.name(),m.amount(),net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.fromValue(m.operation())));}}
            catch(Exception failure){throw new IllegalStateException("Cannot bridge original item attributes",failure);}
            // 1.7.10 had no attack cooldown; an original sword/tool swings at the speed of its host counterpart.
            var kind=equipment(stack);
            if(kind!=null)result.put(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED,new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                    BASE_ATTACK_SPEED_UUID,kind.sword()?"Weapon modifier":"Tool modifier",attackSpeed(kind),net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION));
            return result;
        }
        /** Original ItemSword/ItemTool, or null for every other original item and unmappable stacks. */
        private LegacyItemUse.Equipment equipment(ItemStack stack){
            try{var kind=itemUse.equipment(snapshot(stack));return kind.sword()||kind.tool()?kind:null;}catch(Exception unmapped){return null;}
        }
        private static double attackSpeed(LegacyItemUse.Equipment kind){
            if(kind.sword())return -2.4;
            return kind.toolClasses().contains("axe")||kind.toolClasses().contains("shovel")&&!kind.toolClasses().contains("pickaxe")?-3:-2.8;
        }
        @Override public boolean canPerformAction(ItemStack stack,net.minecraftforge.common.ToolAction action){
            var kind=equipment(stack);if(kind==null)return false;
            if(kind.sword())return net.minecraftforge.common.ToolActions.DEFAULT_SWORD_ACTIONS.contains(action);
            return kind.toolClasses().contains("pickaxe")&&net.minecraftforge.common.ToolActions.DEFAULT_PICKAXE_ACTIONS.contains(action)
                    ||kind.toolClasses().contains("axe")&&net.minecraftforge.common.ToolActions.DEFAULT_AXE_ACTIONS.contains(action)
                    ||kind.toolClasses().contains("shovel")&&net.minecraftforge.common.ToolActions.DEFAULT_SHOVEL_ACTIONS.contains(action);
        }
        /** LivingEntity.canDisableShield only knows host AxeItem. */
        @Override public boolean canDisableShield(ItemStack stack,ItemStack shield,LivingEntity entity,LivingEntity attacker){
            var kind=equipment(stack);return kind!=null&&kind.toolClasses().contains("axe")||super.canDisableShield(stack,shield,entity,attacker);
        }
        /** A creative player's sword hits instead of breaking blocks, as with host swords. */
        @Override public boolean canAttackBlock(BlockState state,Level level,BlockPos pos,net.minecraft.world.entity.player.Player player){
            var kind=equipment(player.getMainHandItem());return kind==null||!kind.sword()||!player.isCreative();
        }
        @Override public int getEnchantmentValue(ItemStack stack){var kind=equipment(stack);return kind==null?0:kind.enchantability();}
        /** Sword enchantments for ItemSword, digger ones for ItemTool (axes also take damage enchantments, as host axes do). */
        @Override public boolean canApplyAtEnchantingTable(ItemStack stack,net.minecraft.world.item.enchantment.Enchantment enchantment){
            if(super.canApplyAtEnchantingTable(stack,enchantment))return true;
            var kind=equipment(stack);if(kind==null)return false;
            var category=enchantment.category;
            if(kind.sword())return category==net.minecraft.world.item.enchantment.EnchantmentCategory.WEAPON;
            return category==net.minecraft.world.item.enchantment.EnchantmentCategory.DIGGER
                    ||kind.toolClasses().contains("axe")&&enchantment instanceof net.minecraft.world.item.enchantment.DamageEnchantment;
        }
        @Override public float getDestroySpeed(ItemStack stack,BlockState state){
            try{var block=legacyBlock(state);return block==null?1:itemUse.destroySpeed(snapshot(stack),block.legacyKey());}catch(Exception failure){return 1;}
        }
        @Override public boolean isCorrectToolForDrops(ItemStack stack,BlockState state){
            try{var block=legacyBlock(state);return block!=null&&itemUse.harvest(snapshot(stack),block.legacyKey());}catch(Exception failure){return false;}
        }
        @Override public boolean onBlockStartBreak(ItemStack stack,BlockPos pos,net.minecraft.world.entity.player.Player player){
            if(player.level().isClientSide)return false;
            return mineLegacyItem(stack,player,player.level().getBlockState(pos),pos,true);
        }
        @Override public boolean mineBlock(ItemStack stack,Level level,BlockState state,BlockPos pos,LivingEntity actor){
            return actor instanceof net.minecraft.world.entity.player.Player player&&!level.isClientSide&&mineLegacyItem(stack,player,state,pos,false);
        }
        @Override public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) { return useLegacyItem(context, true); }
        @Override public InteractionResult interactLivingEntity(ItemStack stack,net.minecraft.world.entity.player.Player player,LivingEntity target,net.minecraft.world.InteractionHand hand){
            try{
                if(!itemUse.entityInteraction(snapshot(stack)))return InteractionResult.PASS;
                if(player.level().isClientSide)return InteractionResult.SUCCESS;
                return interactLegacyEntity(stack,player,target,hand,false)?InteractionResult.CONSUME:InteractionResult.PASS;
            }catch(Exception failure){LogUtils.getLogger().error("MFCL original entity interaction failed for {}",entry.key(),failure);return InteractionResult.FAIL;}
        }
        @Override public boolean hurtEnemy(ItemStack stack,LivingEntity target,LivingEntity attacker){
            if(!(attacker instanceof net.minecraft.world.entity.player.Player player)||player.level().isClientSide)return false;
            try{return interactLegacyEntity(stack,player,target,net.minecraft.world.InteractionHand.MAIN_HAND,true);}
            catch(Exception failure){LogUtils.getLogger().error("MFCL original weapon callback failed for {}",entry.key(),failure);return false;}
        }
        @Override public boolean onLeftClickEntity(ItemStack stack,net.minecraft.world.entity.player.Player player,net.minecraft.world.entity.Entity target) {
            try{return interactLegacyEntity(stack,player,target,net.minecraft.world.InteractionHand.MAIN_HAND,2);}
            catch(Exception failure){LogUtils.getLogger().error("MFCL original left-click callback failed for {}",entry.key(),failure);return true;}
        }
        @Override public void inventoryTick(ItemStack stack,Level level,net.minecraft.world.entity.Entity entity,int slot,boolean selected) {
            if(level.isClientSide||!(entity instanceof net.minecraft.world.entity.player.Player player)||slot<0||slot>=36)return;
            try {
                if(!itemUse.inventoryUpdates(snapshot(stack)))return;
                var world=services.worlds().bound(level);var source=placer(player);var before=hostInventory(player);
                Object actor=exposeLegacyInventory(player,world,before,null);
                var outgoing=logicTransaction(level,player.blockPosition(),()->LegacyItemDrops.capture(ModernContentBridge.this,player,()->itemUse.inventoryTick(world,source,slot,player.tickCount,selected)),false);
                collectLegacyInventory(player,actor,before);
                if(player instanceof net.minecraft.server.level.ServerPlayer server)outgoing.forEach(packet->ScanSyncChannel.sendToPlayer(server,packet));
            }catch(Exception failure){if(itemTickFailures.add(entry.key()))LogUtils.getLogger().error("MFCL original inventory tick failed for {}",entry.key(),failure);}
        }
        @Override public InteractionResult useOn(UseOnContext context) {
            // A held scanner has no block-use action. PASS lets Minecraft invoke its held-use callback.
            if(services.scanners().forItem(entry.key()).isPresent())return InteractionResult.PASS;
            var result=useLegacyItem(context);if(result!=InteractionResult.PASS)return result;
            // Original axes/shovels have no use-on action of their own; the host one strips, scrapes, waxes off and flattens.
            var kind=equipment(context.getItemInHand());
            if(kind!=null&&kind.toolClasses().contains("axe"))return Items.IRON_AXE.useOn(context);
            if(kind!=null&&kind.toolClasses().contains("shovel"))return Items.IRON_SHOVEL.useOn(context);
            return result;
        }
        @Override public net.minecraft.world.InteractionResultHolder<ItemStack> use(Level level, net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand) {
            return useLegacyHeldItem(level,player,hand,entry.key());
        }
        @Override public int getUseDuration(ItemStack stack) {
            try { return itemUse.useDuration(snapshot(stack)); }
            catch (Exception failure) { return 0; }
        }
        @Override public net.minecraft.world.item.UseAnim getUseAnimation(ItemStack stack) {
            try {
                return switch (itemUse.useAction(snapshot(stack))) {
                    case "bow" -> net.minecraft.world.item.UseAnim.BOW;
                    case "eat" -> net.minecraft.world.item.UseAnim.EAT;
                    case "drink" -> net.minecraft.world.item.UseAnim.DRINK;
                    case "block" -> net.minecraft.world.item.UseAnim.BLOCK;
                    default -> net.minecraft.world.item.UseAnim.NONE;
                };
            } catch (Exception failure) { return net.minecraft.world.item.UseAnim.NONE; }
        }
        @Override public void onUseTick(Level level, LivingEntity living, ItemStack stack, int remaining) {
            if (!(living instanceof net.minecraft.world.entity.player.Player player)) return;
            try {
                var world=services.worlds().bound(level);var source=placer(player);var held=snapshot(stack);
                var hand=player.getUsedItemHand();var before=hostInventory(player);
                if(hand==net.minecraft.world.InteractionHand.OFF_HAND)before.set(player.getInventory().selected,held);
                Object actor=exposeLegacyInventory(player,world,before,held);
                var result=level.isClientSide?itemUse.usingTickResult(world,source,held,remaining)
                        :logicTransaction(level,player.blockPosition(),()->LegacyItemDrops.capture(ModernContentBridge.this,player,()->itemUse.usingTickResult(world,source,held,remaining)),true);
                LegacyPlayerBody.push(actor);
                if(!level.isClientSide)collectLegacyInventory(player,actor,before,hand);
                if(result.scan()!=null&&level.isClientSide)ScanSyncChannel.sendToServer(result.scan(),"@");
                if(player instanceof net.minecraft.server.level.ServerPlayer server){
                    result.messages().forEach(packet->ScanSyncChannel.sendToPlayer(server,packet));
                    server.getPersistentData().putByteArray(LEGACY_ENTITY_DATA,itemUse.savePersisted(world,source));
                }
                if(!result.using())player.stopUsingItem();
            } catch (Exception failure) { LogUtils.getLogger().debug("MFCL legacy using tick failed for {}", entry.key(), failure); }
        }
        @Override public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity living) {
            // The server owns the outcome (effects, remaining bottle); the client receives it through inventory sync.
            if (level.isClientSide || !(living instanceof net.minecraft.world.entity.player.Player player)) return stack;
            try {
                var world = services.worlds().bound(level); var source = placer(player); var held = snapshot(stack);
                itemUse.loadPersisted(world,source,player.getPersistentData().getByteArray(LEGACY_ENTITY_DATA));
                var hand=player.getUsedItemHand();var before = hostInventory(player);
                if(hand==net.minecraft.world.InteractionHand.OFF_HAND)before.set(player.getInventory().selected,held);
                Object actor = exposeLegacyInventory(player, world, before, held);
                LegacyItemUse.Finished result;
                try { result = logicTransaction(level,player.blockPosition(),()->LegacyItemDrops.capture(ModernContentBridge.this,player,()->itemUse.finish(world,source,held)),true); }
                catch(Exception failure){services.playerInventory().write(actor,before);throw failure;}
                collectLegacyInventory(player,actor,before,hand);
                if(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                    result.messages().forEach(message->ScanSyncChannel.sendToPlayer(serverPlayer,message));
                    result.feedback().forEach(message->serverPlayer.displayClientMessage(Component.literal(message),false));
                    serverPlayer.getPersistentData().putByteArray(LEGACY_ENTITY_DATA,itemUse.savePersisted(world,source));
                }
                return player.getItemInHand(hand);
            } catch (Exception failure) {
                LogUtils.getLogger().error("MFCL legacy item completion failed for {}", entry.key(), failure);
                return stack;
            }
        }
        @Override public void releaseUsing(ItemStack stack, Level level, LivingEntity living, int timeLeft) {
            if (!(living instanceof net.minecraft.world.entity.player.Player player)) return;
            try {
                var world = services.worlds().bound(level); var source = placer(player); var held = snapshot(stack);
                var hand=player.getUsedItemHand();var before = hostInventory(player);
                if(hand==net.minecraft.world.InteractionHand.OFF_HAND)before.set(player.getInventory().selected,held);
                Object actor = exposeLegacyInventory(player, world, before, held);
                var result=level.isClientSide?itemUse.release(world,source,held,timeLeft)
                        :logicTransaction(level,player.blockPosition(),()->LegacyItemDrops.capture(ModernContentBridge.this,player,()->itemUse.release(world,source,held,timeLeft)),true);
                if (!level.isClientSide) {
                    collectLegacyInventory(player, actor, before,hand);
                    if(player instanceof net.minecraft.server.level.ServerPlayer server){
                        result.messages().forEach(message->ScanSyncChannel.sendToPlayer(server,message));
                        result.feedback().forEach(message->server.displayClientMessage(Component.literal(message),false));
                        server.getPersistentData().putByteArray(LEGACY_ENTITY_DATA,itemUse.savePersisted(world,source));
                    }
                }
            } catch (Exception failure) { LogUtils.getLogger().debug("MFCL legacy release failed for {}", entry.key(), failure); }
        }
        @Override public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientItemExtensions> consumer) {
            consumer.accept(customRenderer(() -> entry == null ? null : entry.key()));
        }
    }
    private net.minecraft.world.InteractionResultHolder<ItemStack> useLegacyHeldItem(Level level, net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand,String key) {
        ItemStack stack = player.getItemInHand(hand);
        try {
            var world = services.worlds().bound(level); var source = placer(player); var held = snapshot(stack);
            if(!level.isClientSide)itemUse.loadPersisted(world,source,player.getPersistentData().getByteArray(LEGACY_ENTITY_DATA));
            var before = hostInventory(player);
            if(hand==net.minecraft.world.InteractionHand.OFF_HAND)before.set(player.getInventory().selected,held);
            Object actor = exposeLegacyInventory(player, world, before, held);
            LegacyItemUse.Result result;
            try {
                result = level.isClientSide
                        ? github.com.gengyoubo.mfcl.client.ClientContainerBridge.useItem(()->itemUse.rightClick(world,source,held))
                        : logicTransaction(level,player.blockPosition(),()->{
                            var outcome=LegacyItemDrops.capture(this,player,()->itemUse.rightClick(world,source,held));
                            // Some original items place a block but return the unchanged stack in creative mode.
                            // The native interaction must still be consumed when the transaction changed the world.
                            var transaction=placement.get();
                            return !outcome.used()&&transaction!=null&&transaction.hasBlockChanges()
                                    ?new LegacyItemUse.Result(true,outcome.useDuration(),outcome.prompt(),outcome.messages(),outcome.feedback(),outcome.device()):outcome;
                        },true);
            } catch(Exception failure){services.playerInventory().write(actor,before);throw failure;}
            if(!level.isClientSide)collectLegacyInventory(player,actor,before,hand);
            if(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                result.messages().forEach(message->ScanSyncChannel.sendToPlayer(serverPlayer,message));
                result.feedback().forEach(message->serverPlayer.displayClientMessage(Component.literal(message),false));
                serverPlayer.getPersistentData().putByteArray(LEGACY_ENTITY_DATA,itemUse.savePersisted(world,source));
                if(result.device()!=null)LegacyDeviceMenu.open(serverPlayer,this,result.device());
            }
            ItemStack updated=player.getItemInHand(hand);
            if (!result.used()) return net.minecraft.world.InteractionResultHolder.pass(updated);
            if (result.useDuration() > 0) player.startUsingItem(hand);
            if (result.prompt() != null && level.isClientSide) player.displayClientMessage(Component.literal(result.prompt()), true);
            return net.minecraft.world.InteractionResultHolder.consume(updated);
        } catch (Exception failure) {
            LogUtils.getLogger().error("MFCL legacy item use failed for {}", key, failure);
            return net.minecraft.world.InteractionResultHolder.fail(stack);
        }
    }
    private final Set<String> itemTickFailures=java.util.concurrent.ConcurrentHashMap.newKeySet();
    private boolean mineLegacyItem(ItemStack stack,net.minecraft.world.entity.player.Player player,BlockState state,BlockPos pos,boolean start){
        var block=legacyBlock(state);if(block==null)return false;
        try {
            var world=services.worlds().bound(player.level());var source=placer(player);var held=snapshot(stack);var before=hostInventory(player);
            Object actor=exposeLegacyInventory(player,world,before,held);
            var result=logicTransaction(player.level(),pos,()->LegacyItemDrops.capture(this,player,()->itemUse.mine(world,source,held,block.legacyKey(),pos.getX(),pos.getY(),pos.getZ(),start)),true);
            collectLegacyInventory(player,actor,before);
            if(player instanceof net.minecraft.server.level.ServerPlayer server){result.messages().forEach(packet->ScanSyncChannel.sendToPlayer(server,packet));result.feedback().forEach(message->server.displayClientMessage(Component.literal(message),false));}
            return result.used();
        }catch(Exception failure){LogUtils.getLogger().error("MFCL original mining callback failed at {}",pos,failure);return false;}
    }
    private boolean interactLegacyEntity(ItemStack stack,net.minecraft.world.entity.player.Player player,LivingEntity target,net.minecraft.world.InteractionHand hand,boolean hit) throws Exception {
        return interactLegacyEntity(stack,player,target,hand,hit?1:0);
    }
    boolean interactHostedEntity(net.minecraft.world.entity.player.Player player,LegacyHostedEntity target,net.minecraft.world.InteractionHand hand) throws Exception {
        return interactLegacyEntity(player.getItemInHand(hand),player,target,hand,3);
    }
    private boolean interactLegacyEntity(ItemStack stack,net.minecraft.world.entity.player.Player player,net.minecraft.world.entity.Entity target,net.minecraft.world.InteractionHand hand,int action) throws Exception {
        var world=services.worlds().bound(player.level());var source=placer(player);var held=action==3?toLegacy(stack):snapshot(stack);var before=hostInventory(player);
        if(hand==net.minecraft.world.InteractionHand.OFF_HAND)before.set(player.getInventory().selected,held);
        itemUse.loadPersisted(world,source,player.getPersistentData().getByteArray(LEGACY_ENTITY_DATA));
        Object actor=exposeLegacyInventory(player,world,before,held);
        LegacyItemUse.Result result;
        try{result=logicTransaction(player.level(),target.blockPosition(),()->LegacyItemDrops.capture(this,player,()->action==3?itemUse.interactHosted(world,source,held,target.getUUID()):action==2?itemUse.leftClick(world,source,held,target.getUUID()):itemUse.interact(world,source,held,target.getUUID(),action==1)),true);}
        catch(Exception failure){services.playerInventory().write(actor,before);throw failure;}
        if(!player.level().isClientSide)collectLegacyInventory(player,actor,before,hand);
        if(player instanceof net.minecraft.server.level.ServerPlayer server){
            result.messages().forEach(packet->ScanSyncChannel.sendToPlayer(server,packet));result.feedback().forEach(message->server.displayClientMessage(Component.literal(message),false));
            server.getPersistentData().putByteArray(LEGACY_ENTITY_DATA,itemUse.savePersisted(world,source));
            if(result.device()!=null)LegacyDeviceMenu.open(server,this,result.device());
        }
        return result.used();
    }
    static LegacyTiles.Placer placer(net.minecraft.world.entity.player.Player player) {
        return new LegacyTiles.Placer(player.getUUID(), player.getName().getString(), player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot(),
                player.getAbilities().instabuild, player.isShiftKeyDown());
    }
    /** The host main inventory as old stacks, preserving occupied native-only slots. */
    List<LegacyClientData.Stack> hostInventory(net.minecraft.world.entity.player.Player player) {
        var inventory = player.getInventory(); var result = new ArrayList<LegacyClientData.Stack>(36);
        for (int slot = 0; slot < 36; slot++) { var stack = inventory.getItem(slot); result.add(stack.isEmpty() ? null : toLegacy(stack)); }
        return result;
    }
    LegacyClientData.Stack toLegacy(ItemStack stack) {
        try { return containerStack(stack); }
        catch (Exception failure) { throw new IllegalStateException("Cannot expose native inventory stack", failure); }
    }
    /** Inventory transport is lossless even when an item has no old gameplay implementation. */
    public LegacyClientData.Stack containerStack(ItemStack stack) throws Exception {
        if (stack.isEmpty()) return null;
        try { return gameplayStack(stack); }
        catch (Exception unmapped) { return hostInventoryStacks.toLegacy(stack); }
    }
    private Object exposeLegacyInventory(net.minecraft.world.entity.player.Player player, Object world, List<LegacyClientData.Stack> contents, LegacyClientData.Stack held) throws Exception {
        var actor = itemUse.clientActor(world, placer(player), held);
        services.playerInventory().write(actor, contents);
        services.playerInventory().select(actor, player.getInventory().selected);
        return actor;
    }
    private void collectLegacyInventory(net.minecraft.world.entity.player.Player player, Object actor, List<LegacyClientData.Stack> before) throws Exception {
        collectLegacyInventory(player,actor,before,net.minecraft.world.InteractionHand.MAIN_HAND);
    }
    private void collectLegacyInventory(net.minecraft.world.entity.player.Player player,Object actor,List<LegacyClientData.Stack> before,net.minecraft.world.InteractionHand hand) throws Exception {
        var after = services.playerInventory().read(actor); var inventory = player.getInventory();
        for (int slot = 0; slot < after.size() && slot < 36; slot++) {
            if (sameLegacy(before.get(slot), after.get(slot))) continue;
            writeBackSlot(player, slot, after.get(slot), hand);
        }
        inventory.setChanged();
    }
    private void writeBackSlot(net.minecraft.world.entity.player.Player player, int slot, LegacyClientData.Stack after, net.minecraft.world.InteractionHand hand) {
        var inventory = player.getInventory();
        try {
            var updated=after==null?ItemStack.EMPTY:gameplayStack(after);
            var current=hand==net.minecraft.world.InteractionHand.OFF_HAND&&slot==inventory.selected?player.getOffhandItem():inventory.getItem(slot);
            // Keep the live use-item reference while NBT/vis changes, so a held action can continue next tick.
            if(!current.isEmpty()&&!updated.isEmpty()&&current.getItem()==updated.getItem()) {
                keepHostTags(current,updated);
                // Original wear ignores host Unbreaking; replay it through the host so the enchantment applies.
                int wear=current.isDamageableItem()&&current.getEnchantmentLevel(net.minecraft.world.item.enchantment.Enchantments.UNBREAKING)>0?updated.getDamageValue()-current.getDamageValue():0;
                if(wear>0)updated.setDamageValue(current.getDamageValue());
                current.setCount(updated.getCount());current.setTag(updated.getTag()==null?null:updated.getTag().copy());
                if(wear>0)current.hurt(wear,player.getRandom(),player instanceof net.minecraft.server.level.ServerPlayer server?server:null);
            }
            else if(hand==net.minecraft.world.InteractionHand.OFF_HAND&&slot==inventory.selected)player.setItemInHand(hand,updated);else inventory.setItem(slot,updated);
        }
        catch (Exception unmapped) { }
    }
    /** Re-attaches the host-owned tags of a stack an original callback rewrote, while it is still the same item. */
    public static ItemStack keepHostTags(ItemStack current, ItemStack updated) {
        var kept = current.getTag(); if (kept == null || updated.isEmpty() || current.getItem() != updated.getItem()) return updated;
        for (String key : HOST_OWNED_TAGS) if (!key.equals("Enchantments")&&kept.contains(key)) updated.getOrCreateTag().put(key, kept.get(key).copy());
        if(current.getItem() instanceof MirroredBlockItem && kept.contains("BlockEntityTag"))updated.getOrCreateTag().put("BlockEntityTag",kept.get("BlockEntityTag").copy());
        if(active!=null)active.enchantments.keepNative(current,updated);
        return updated;
    }
    static boolean sameLegacy(LegacyClientData.Stack left, LegacyClientData.Stack right) {
        if (left == null || right == null) return left == right;
        return left.key().equals(right.key()) && left.metadata() == right.metadata() && left.count() == right.count() && left.tag().equals(right.tag());
    }
    /** Item's constructor invokes this before the subclass sets its entry, so the key is resolved lazily. */
    private net.minecraftforge.client.extensions.common.IClientItemExtensions customRenderer(java.util.function.Supplier<String> key) {
        return new net.minecraftforge.client.extensions.common.IClientItemExtensions() {
            private github.com.gengyoubo.mfcl.client.LegacyItemRenderer renderer;
            @Override public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() {
                String value = key.get();
                if (value == null || !declarations.customItems().contains(value)) return null;
                if (renderer == null) renderer = new github.com.gengyoubo.mfcl.client.LegacyItemRenderer(ModernContentBridge.this, value);
                return renderer;
            }
        };
    }
    private final class MirroredBlockItem extends BlockItem {
        private final LegacyContent.Item entry;
        MirroredBlockItem(MirroredBlock block, Properties properties, LegacyContent.Item entry) { super(block, properties); this.entry = entry; }
        @Override public net.minecraftforge.common.capabilities.ICapabilityProvider initCapabilities(ItemStack stack,CompoundTag nbt) {
            return entry!=null&&fluidRegistry.isContainerItem(entry.key())?new LegacyFluidItemHandler(ModernContentBridge.this,stack):super.initCapabilities(stack,nbt);
        }
        @Override public int getEntityLifespan(ItemStack stack, Level level) { return droppedItems.lifespan(stack, level); }
        @Override public int getMaxStackSize(ItemStack stack) { return entry==null?super.getMaxStackSize(stack):stackLimit(entry,stack); }
        @Override public Component getName(ItemStack stack) { return name(entry, stack); }
        @Override public void appendHoverText(ItemStack stack, Level level, List<Component> lines, TooltipFlag flag) { tooltip(entry, stack, lines); }
        @Override public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) { return useLegacyItem(context, true); }
        @Override public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientItemExtensions> consumer) {
            consumer.accept(customRenderer(() -> entry == null ? null : entry.key()));
        }
        @Override public InteractionResult useOn(UseOnContext context) {
            // The placed metadata is checked where it is known: modernBlock on the original write, getPlacementState below.
            if (!ModernContentBridge.canPlaceSomeForm(((MirroredBlock) getBlock()).entry)) {
                if (context.getPlayer() != null && context.getLevel().isClientSide) context.getPlayer().displayClientMessage(Component.translatable("mfcl.content.special_block_pending"), true);
                return InteractionResult.FAIL;
            }
            if (!context.getItemInHand().isDamageableItem() && (metadata(context.getItemInHand()) < 0 || metadata(context.getItemInHand()) > 15)) return InteractionResult.FAIL;
            // ItemBlock predicts placement on the client; authoritative world mutation runs on the server.
            if (context.getLevel().isClientSide) return InteractionResult.SUCCESS;
            return useLegacyItem(context);
        }
        @Override public InteractionResult place(BlockPlaceContext context) {
            if (!ModernContentBridge.canPlaceSomeForm(((MirroredBlock) getBlock()).entry)) return InteractionResult.FAIL;
            BlockPos position = context.getClickedPos(); var before = context.getLevel().getBlockState(position);
            try { return super.place(context); }
            catch (Exception failure) {
                context.getLevel().setBlock(position, before, 3);
                LogUtils.getLogger().error("MFCL legacy block placement failed for {} at {}", entry.key(), position, failure);
                if (context.getPlayer() != null) context.getPlayer().displayClientMessage(Component.translatable("mfcl.content.placement_failed"), true);
                return InteractionResult.FAIL;
            }
        }
        @Override protected BlockState getPlacementState(BlockPlaceContext context) {
            BlockState state = super.getPlacementState(context);
            if (state == null) return null;
            int meta = metadata(context.getItemInHand()) & 15;
            if (!context.getLevel().isClientSide) try {
                var position = context.getClickedPos(); var hit = context.getClickLocation().subtract(position.getX(), position.getY(), position.getZ());
                meta = blockShapes.placement(entry.blockKey(), services.worlds().bound(context.getLevel()), position.getX(),position.getY(),position.getZ(),
                        context.getClickedFace().get3DDataValue(), (float)hit.x, (float)hit.y, (float)hit.z, meta);
            } catch (Exception error) { throw new IllegalStateException("Cannot evaluate original placement metadata for " + entry.key(), error); }
            // A null state makes BlockItem.place refuse cleanly; only the server has computed the real metadata.
            if (!context.getLevel().isClientSide && !ModernContentBridge.canPlace(((MirroredBlock) getBlock()).entry, meta & 15)) return null;
            return state.setValue(METADATA, meta & 15);
        }
    }
    private class MirroredBlock extends Block {
        protected final LegacyContent.Block entry;
        private final boolean powerSource, comparatorSource, activation, forgeFluid;
        MirroredBlock(LegacyContent.Block entry) {
            super(properties(entry)); this.entry = entry; registerDefaultState(stateDefinition.any().setValue(METADATA, 0));
            try { powerSource = services.logic().providesPower(entry.key()); comparatorSource = services.logic().hasComparator(entry.key()); activation = services.logic().hasActivation(entry.key()); forgeFluid = services.logic().isForgeFluid(entry.key()); }
            catch (Exception failure) { throw new IllegalStateException("Cannot read original block interaction declarations",failure); }
        }
        @Override public void onPlace(BlockState state, Level level, BlockPos pos, BlockState previous, boolean moving) {
            super.onPlace(state,level,pos,previous,moving);
            if(!forgeFluid||level.isClientSide||state.getBlock()==previous.getBlock())return;
            try {logicTransaction(level,pos,()->{services.logic().fluidAdded(entry.key(),services.worlds().bound(level),pos.getX(),pos.getY(),pos.getZ());return null;},true);}
            catch(Exception failure){throw new IllegalStateException("Cannot schedule original fluid on placement: "+entry.key(),failure);}
        }
        @Override public void neighborChanged(BlockState state,Level level,BlockPos pos,Block neighbour,BlockPos from,boolean moving) {
            super.neighborChanged(state,level,pos,neighbour,from,moving);
            if(!forgeFluid||level.isClientSide)return;
            try {logicTransaction(level,pos,()->{services.logic().fluidNeighbourChanged(entry.key(),services.worlds().bound(level),pos.getX(),pos.getY(),pos.getZ(),from.getX(),from.getY(),from.getZ());return null;},true);}
            catch(Exception failure){throw new IllegalStateException("Cannot schedule original fluid after neighbour change: "+entry.key(),failure);}
        }
        @Override public net.minecraft.world.level.material.FluidState getFluidState(BlockState state) {
            return forgeFluid?fluidRegistry.state(entry.key(),state.getValue(METADATA)):super.getFluidState(state);
        }
        @Override public boolean isSignalSource(BlockState state) { return powerSource; }
        @Override public boolean hasAnalogOutputSignal(BlockState state) { return comparatorSource; }
        @Override public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
            try { return services.logic().comparator(entry.key(),services.worlds().bound(level),pos.getX(),pos.getY(),pos.getZ()); }
            catch (Exception failure) { github.com.gengyoubo.mfcl.bridge.LegacyDiagnostics.legacyFailure("comparator "+entry.key(),failure); LogUtils.getLogger().error("MFCL original comparator query failed at {}",pos,failure); return 0; }
        }
        @Override public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction side) { return power(state,level,pos,side,false); }
        @Override public int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction side) { return power(state,level,pos,side,true); }
        private int power(BlockState state, BlockGetter level, BlockPos pos, Direction side, boolean direct) {
            if (!powerSource) return 0;
            try { return services.logic().signal(entry.key(),renderView(level,pos,state),side.get3DDataValue(),direct); }
            catch (Exception failure) { github.com.gengyoubo.mfcl.bridge.LegacyDiagnostics.legacyFailure("redstone power "+entry.key(),failure); LogUtils.getLogger().error("MFCL original power query failed at {}",pos,failure); return 0; }
        }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(METADATA); }
        /** Native loot is evaluated after the tile is gone, so this is the only point where the original harvest callback can read it. */
        @Override public void playerWillDestroy(Level level,BlockPos pos,BlockState state,net.minecraft.world.entity.player.Player player){
            if(!level.isClientSide&&player instanceof net.minecraft.server.level.ServerPlayer server&&canPlace(entry,state.getValue(METADATA)))try{
                if(services.logic().hasHarvestCallback(entry.key())){
                    Object world=services.worlds().bound(level);
                    logicTransaction(level,pos,()->LegacyItemDrops.captureHarvest(ModernContentBridge.this,level,()->withServerActor(server,actor->
                            services.logic().harvested(entry.key(),world,pos.getX(),pos.getY(),pos.getZ(),state.getValue(METADATA),actor))),true);
                }
            }catch(Exception|LinkageError failure){
                github.com.gengyoubo.mfcl.bridge.LegacyDiagnostics.legacyFailure("block harvest "+entry.key(),failure);
                LogUtils.getLogger().error("MFCL original onBlockHarvested failed for {} at {}",entry.key(),pos,failure);
            }
            super.playerWillDestroy(level,pos,state,player);
        }
        @Override public void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moving){
            if(!level.isClientSide&&state.getBlock()!=next.getBlock())try{
                logicTransaction(level,pos,()->{services.logic().leavesRemoved(entry.key(),services.worlds().bound(level),pos.getX(),pos.getY(),pos.getZ());return null;},true);
            }catch(Exception failure){throw new IllegalStateException("Cannot notify original leaves after removal of "+entry.key(),failure);}
            super.onRemove(state,level,pos,next,moving);
        }
        @Override public InteractionResult use(BlockState state, Level level, BlockPos pos, net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand, net.minecraft.world.phys.BlockHitResult hit) {
            // Inert blocks must let the item use path continue (e.g. a wand charging a node inside a silverwood log).
            if (level.isClientSide) return activation ? InteractionResult.SUCCESS : InteractionResult.PASS;
            // Fluid containers (buckets, modded tanks/cells) exchange through the native fluid capability of original tanks.
            if (useFluidContainer(level,pos,player,hand,hit.getDirection())) return InteractionResult.CONSUME;
            try {
                var pendingInventory = new ArrayList<ItemStack>();
                var pendingMessages = new ArrayList<String>();
                var pendingMenus = new ArrayList<LegacyDeviceContainers.Open>();
                int selected = player.getInventory().selected;
                var outcome = logicTransaction(level,pos,() -> {
                    // Host items without an original counterpart become empty slots, and only slots the old logic changed
                    // are written back, so an unmapped item elsewhere in the inventory neither blocks nor loses anything.
                    var before = new ArrayList<LegacyClientData.Stack>();
                    for (int slot=0;slot<40;slot++) before.add(toLegacy(player.getInventory().getItem(slot)));
                    if (hand == net.minecraft.world.InteractionHand.OFF_HAND) before.set(selected,toLegacy(player.getOffhandItem()));
                    var point = hit.getLocation().subtract(pos.getX(),pos.getY(),pos.getZ());
                    var actor = new LegacyTiles.Placer(player.getUUID(),player.getName().getString(),player.getX(),player.getY(),player.getZ(),player.getYRot(),player.getXRot(),player.getAbilities().instabuild,player.isShiftKeyDown());
                    var result = services.logic().activate(entry.key(),services.worlds().bound(level),pos.getX(),pos.getY(),pos.getZ(),actor,new ArrayList<>(before),selected,hit.getDirection().get3DDataValue(),(float)point.x,(float)point.y,(float)point.z);
                    for (int slot=0;slot<result.inventory().size();slot++) {
                        var after = result.inventory().get(slot);
                        pendingInventory.add(slot < before.size() && sameLegacy(before.get(slot),after) ? null : gameplayStack(after));
                    }
                    pendingMessages.addAll(result.messages());
                    if(result.device()!=null)pendingMenus.add(result.device());
                    return result.handled() ? InteractionResult.CONSUME : InteractionResult.PASS;
                },true);
                if (!pendingInventory.isEmpty()) {
                    for (int slot=0;slot<40&&slot<pendingInventory.size();slot++) {
                        var replacement = pendingInventory.get(slot);
                        if (replacement == null) continue; // Unchanged by the original logic.
                        var current = slot == selected && hand == net.minecraft.world.InteractionHand.OFF_HAND ? player.getOffhandItem() : player.getInventory().getItem(slot);
                        if (ItemStack.isSameItemSameTags(current,replacement)) current.setCount(replacement.getCount());
                        else {
                            current.setCount(0); // Invalidate a native useOn caller's reference to the consumed item.
                            if (slot == selected && hand == net.minecraft.world.InteractionHand.OFF_HAND) player.setItemInHand(hand,replacement);
                            else player.getInventory().setItem(slot,replacement);
                        }
                    }
                    player.getInventory().setChanged(); player.inventoryMenu.broadcastChanges();
                }
                pendingMessages.forEach(message -> player.displayClientMessage(Component.literal(message),false));
                if(player instanceof net.minecraft.server.level.ServerPlayer server)for(var menu:pendingMenus)LegacyDeviceMenu.open(server,ModernContentBridge.this,menu);
                return outcome;
            } catch (Exception failure) {
                LegacyBlockEntity.logicFailed(entry.key(),state.getValue(METADATA));
                LogUtils.getLogger().error("MFCL original block interaction failed for {} at {}",entry.key(),pos,failure);
                player.displayClientMessage(Component.literal("此设备的部分交互尚未支持。"),true); return InteractionResult.FAIL;
            }
        }
        private boolean useFluidContainer(Level level, BlockPos pos, net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand, Direction side) {
            var held = player.getItemInHand(hand);
            if (held.isEmpty() || !held.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.FLUID_HANDLER_ITEM).isPresent()) return false;
            if (!(level.getBlockEntity(pos) instanceof LegacyBlockEntity tile)) return false;
            var handler = tile.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.FLUID_HANDLER,side).orElse(null);
            if (handler == null) return false;
            try {
                // Forge's generic creative exchange discards the filled container. Use vanilla bucket pickup
                // semantics instead: keep the empty bucket and give the player a filled bucket if needed.
                if (player.getAbilities().instabuild && held.is(net.minecraft.world.item.Items.BUCKET)) {
                    var filled = net.minecraftforge.fluids.FluidUtil.tryFillContainer(held,handler,Integer.MAX_VALUE,player,true);
                    if (!filled.isSuccess()) return false;
                    player.setItemInHand(hand,net.minecraft.world.item.ItemUtils.createFilledResult(held,player,filled.getResult()));
                    return true;
                }
                return net.minecraftforge.fluids.FluidUtil.interactWithFluidHandler(player,hand,handler);
            }
            catch (Exception failure) { LogUtils.getLogger().error("MFCL fluid container exchange failed for {} at {}",entry.key(),pos,failure); return false; }
        }
        @Override public boolean triggerEvent(BlockState state, Level level, BlockPos pos, int event, int data) {
            try { return services.logic().event(entry.key(),services.worlds().bound(level),pos.getX(),pos.getY(),pos.getZ(),event,data); }
            catch (Exception failure) { github.com.gengyoubo.mfcl.bridge.LegacyDiagnostics.legacyFailure("block event "+entry.key(),failure); LogUtils.getLogger().error("MFCL original block event failed for {} at {}",entry.key(),pos,failure); return false; }
        }
        @Override public void animateTick(BlockState state, Level level, BlockPos pos, net.minecraft.util.RandomSource random) {
            // Original randomDisplayTick (sparkles, smoke...), client-only like in 1.7.10.
            if (!level.isClientSide || displayFailures.contains(entry.key())) return;
            try { services.logic().displayTick(entry.key(), services.worlds().bound(level), pos.getX(), pos.getY(), pos.getZ(), random.nextLong()); }
            catch (Exception | LinkageError failure) {
                if (displayFailures.add(entry.key())) LogUtils.getLogger().warn("MFCL original randomDisplayTick disabled for {}", entry.key(), failure);
            }
        }
        @Override public void entityInside(BlockState state, Level level, BlockPos pos, net.minecraft.world.entity.Entity entity) {
            // Original onEntityCollidedWithBlock handles living hazards as well as item-consuming devices.
            if (level.isClientSide || entity.isRemoved()) return;
            try {
                if (!services.logic().collides(entry.key())) return;
                var snapshot = HostEntityQueries.snapshot(level, entity);
                if (snapshot == null) return;
                var result = logicTransaction(level, pos, () -> services.logic().collide(entry.key(), services.worlds().bound(level), pos.getX(), pos.getY(), pos.getZ(), snapshot), true);
                if (result.removed()) entity.discard();
                else if (entity instanceof net.minecraft.world.entity.item.ItemEntity drop && !sameLegacy(result.stack(), snapshot.item()))
                    drop.setItem(gameplayStack(result.stack()));
                if (level.getBlockEntity(pos) instanceof LegacyBlockEntity tile) tile.captureChanges(false);
            } catch (Exception | LinkageError failure) {
                if (collisionFailures.add(entry.key())) {
                    github.com.gengyoubo.mfcl.bridge.LegacyDiagnostics.legacyFailure("entity collision "+entry.key(),failure);
                    LogUtils.getLogger().error("MFCL original entity collision failed for {} at {}", entry.key(), pos, failure);
                }
            }
        }
        @Override public void tick(BlockState state, net.minecraft.server.level.ServerLevel level, BlockPos pos, net.minecraft.util.RandomSource random) {
            try { logicTransaction(level,pos,() -> { services.logic().scheduled(entry.key(),services.worlds().bound(level),pos.getX(),pos.getY(),pos.getZ()); return null; },true); }
            catch (Exception failure) { github.com.gengyoubo.mfcl.bridge.LegacyDiagnostics.legacyFailure("scheduled tick "+entry.key(),failure); LogUtils.getLogger().error("MFCL original scheduled tick failed for {} at {}",entry.key(),pos,failure); }
        }
        @Override public RenderShape getRenderShape(BlockState state) {
            return entry.renderType() < 0 && blockRendering.hasTileRenderer(entry.key(), state.getValue(METADATA)) ? RenderShape.INVISIBLE : RenderShape.MODEL;
        }
        @Override public net.minecraft.world.phys.shapes.VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, net.minecraft.world.phys.shapes.CollisionContext context) {
            return shape(state, level, pos, false, context);
        }
        @Override public net.minecraft.world.phys.shapes.VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, net.minecraft.world.phys.shapes.CollisionContext context) {
            return shape(state, level, pos, true, context);
        }
        private net.minecraft.world.phys.shapes.VoxelShape shape(BlockState state, BlockGetter level, BlockPos pos, boolean collision,net.minecraft.world.phys.shapes.CollisionContext context) {
            if (!canPlace(entry, state.getValue(METADATA))) return net.minecraft.world.phys.shapes.Shapes.block();
            try {
                HostWorldAccess.Entity collider=null;
                if(collision&&context instanceof net.minecraft.world.phys.shapes.EntityCollisionContext entities&&entities.getEntity()!=null) {
                    var entity=entities.getEntity();collider=HostEntityQueries.snapshot(entity.level(),entity);
                    if(collider!=null)collider=collider.relativeTo(pos.getX(),pos.getY(),pos.getZ());
                }
                var original = blockShapes.shapes(entry.key(), renderView(level, pos, state),collider);
                var boxes = collision ? original.collision() : original.outline();
                var result = net.minecraft.world.phys.shapes.Shapes.empty();
                for (var box : boxes) if (box.maxX() > box.minX() && box.maxY() > box.minY() && box.maxZ() > box.minZ())
                    result = net.minecraft.world.phys.shapes.Shapes.or(result, net.minecraft.world.phys.shapes.Shapes.box(box.minX(),box.minY(),box.minZ(),box.maxX(),box.maxY(),box.maxZ()));
                return result;
            } catch (Exception error) { throw new IllegalStateException("Cannot evaluate original shape " + entry.key() + " at " + pos, error); }
        }
        /** Original Block.isReplaceable (fluids, gases, plants): placing another block simply overwrites it. */
        @Override public boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
            if (!canPlace(entry, state.getValue(METADATA)) || context.getItemInHand().is(asItem())) return false;
            try { return blockShapes.shapes(entry.key(), renderView(context.getLevel(), context.getClickedPos(), state)).replaceable(); }
            catch (Exception error) { throw new IllegalStateException("Cannot evaluate original replaceability " + entry.key() + " at " + context.getClickedPos(), error); }
        }
        @Override public ItemStack getCloneItemStack(BlockGetter level, BlockPos pos, BlockState state) {
            return itemForBlock(entry.key(), state.getValue(METADATA));
        }
        @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
            if (level.isClientSide) return;
            try {
                if (state.hasBlockEntity() && entry.hasTile(state.getValue(METADATA))) {
                    if (!(level.getBlockEntity(pos) instanceof LegacyBlockEntity tile)) throw new IllegalStateException("Missing host tile envelope");
                    tile.legacy();
                }
                LegacyTiles.Placer actor = placer == null ? null : new LegacyTiles.Placer(placer.getUUID(), placer.getName().getString(), placer.getX(), placer.getY(), placer.getZ(), placer.getYRot(), placer.getXRot());
                services.tiles().placed(entry.key(), services.worlds().bound(level), pos.getX(), pos.getY(), pos.getZ(), actor, snapshot(stack));
                if (level.getBlockEntity(pos) instanceof LegacyBlockEntity tile) tile.synchronize();
            } catch (Exception error) { throw new IllegalStateException("Original onBlockPlacedBy failed for " + entry.key(), error); }
        }
        @Override public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
            if (!canPlace(entry, state.getValue(METADATA))) return List.of();
            try {
                var result = new ArrayList<ItemStack>();
                var origin=params.getParameter(LootContextParams.ORIGIN);
                var pos=BlockPos.containing(origin);
                var tool=params.getOptionalParameter(LootContextParams.TOOL);
                int fortune=tool==null?0:net.minecraft.world.item.enchantment.EnchantmentHelper.getItemEnchantmentLevel(net.minecraft.world.item.enchantment.Enchantments.BLOCK_FORTUNE,tool);
                for (var drop : declarations.drops(entry.key(),state.getValue(METADATA),services.worlds().bound(params.getLevel()),pos.getX(),pos.getY(),pos.getZ(),fortune)) {
                    ItemStack mapped = gameplayStack(drop);
                    if (mapped.isEmpty()) throw new UnsupportedOperationException("Unmapped original block drop: " + drop.key());
                    result.add(mapped);
                }
                if (params.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof LegacyBlockEntity tile) {
                    for (var drop : tile.inventoryDrops()) {
                        ItemStack mapped = mapStack(drop);
                        if (mapped.isEmpty()) throw new UnsupportedOperationException("Unmapped original inventory drop: " + drop.key());
                        result.add(mapped);
                    }
                }
                return result;
            } catch (Exception failure) { throw new IllegalStateException("Cannot execute original block drops for " + entry.key(), failure); }
        }
    }
    private final class MirroredTileBlock extends MirroredBlock implements EntityBlock {
        MirroredTileBlock(LegacyContent.Block entry) { super(entry); }
        @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return entry.hasTile(state.getValue(METADATA)) ? new LegacyBlockEntity(ModernContentBridge.this, pos, state) : null; }
        @Override public <T extends BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
            if (type != tileType || !entry.hasTile(state.getValue(METADATA))) return null;
            return level.isClientSide ? (world,pos,current,tile) -> ((LegacyBlockEntity)tile).tickClient() : (world,pos,current,tile) -> ((LegacyBlockEntity)tile).tickServer();
        }
    }
    public ItemStack itemForBlock(String key, int metadata) {
        for (var item : declarations.items()) if (key.equals(item.blockKey())) return stack(item.key(), metadata, 1, "");
        return ItemStack.EMPTY;
    }
    /** Read-only FE sides (bit per RF side) for render/shape views; the model needs connections, never transfers. */
    private record EnergyConnections(int receive, int extract) implements HostEnergyAccess {
        public boolean canReceive(int side) { return (receive >> side & 1) != 0; }
        public boolean canExtract(int side) { return (extract >> side & 1) != 0; }
        public int stored(int side) { return 0; }
        public int capacity(int side) { return 0; }
        public int receive(int side, int amount, boolean simulate) { return 0; }
        public int extract(int side, int amount, boolean simulate) { return 0; }
    }
    public LegacyBlockRendering.View renderView(BlockGetter level, BlockPos origin) {
        return renderView(level, origin, level.getBlockState(origin));
    }
    public LegacyBlockRendering.View renderView(BlockGetter level, BlockPos origin, BlockState center) {
        return new LegacyBlockRendering.View() {
            private boolean incomplete;
            private boolean available(int x,int z) {
                var pos=origin.offset(x,0,z);
                if (level instanceof net.minecraft.server.level.WorldGenRegion region && !region.hasChunk(pos.getX() >> 4,pos.getZ() >> 4)) {
                    incomplete=true; return false;
                }
                return true;
            }
            public boolean cacheable() { return !incomplete; }
            public String biome(int x,int z) {
                if (!available(x,z)) return "minecraft:plains";
                var pos=origin.offset(x,0,z);
                return level instanceof LevelReader reader?HostBiomeQueries.key(reader,pos):github.com.gengyoubo.mfcl.client.ClientBiomeQueries.key(pos);
            }
            public HostWorldAccess.Block block(int x, int y, int z) {
                if (!available(x,z)) return new HostWorldAccess.Block("minecraft:air",0);
                var state = x == 0 && y == 0 && z == 0 ? center : level.getBlockState(origin.offset(x,y,z));
                if (state.hasProperty(METADATA)) return legacyBlock(state);
                String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
                if (state.isAir()) id = "minecraft:air";
                // In 1.20 "grass" is the plant; in 1.7 it names the opaque grass block.
                if (id.equals("minecraft:grass")) return new HostWorldAccess.Block("minecraft:tallgrass", 1);
                if (id.equals("minecraft:grass_block")) id = "minecraft:grass";
                // New vanilla blocks have no old registry entry; preserve their occlusion for old neighbor queries.
                if (!legacyBlockKeys.contains(id)) id = state.isSolidRender(level, origin.offset(x,y,z)) ? "minecraft:stone" : "minecraft:air";
                return new HostWorldAccess.Block(id, 0);
            }
            public byte[] tileData(int x, int y, int z) {
                if (!available(x,z)) return null;
                if (x == 0 && y == 0 && z == 0 && level.getBlockState(origin).getBlock() != center.getBlock()) return null;
                return level.getBlockEntity(origin.offset(x,y,z)) instanceof LegacyBlockEntity tile ? tile.renderData() : null;
            }
            // Same neighbour tiles as the live old worlds: original tiles, and native FE tiles as RF endpoints.
            public boolean legacyTile(int x, int y, int z) {
                if (!available(x,z)) return false;
                if (x == 0 && y == 0 && z == 0 && level.getBlockState(origin).getBlock() != center.getBlock()) return true;
                return level.getBlockEntity(origin.offset(x,y,z)) instanceof LegacyBlockEntity;
            }
            public HostEnergyAccess energy(int x, int y, int z) {
                if (!available(x,z)) return null;
                var tile = level.getBlockEntity(origin.offset(x,y,z));
                if (tile == null || tile.isRemoved() || tile instanceof LegacyBlockEntity) return null;
                int receive = 0, extract = 0;
                for (int side = 0; side <= 6; side++) {
                    var energy = tile.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ENERGY, side == 6 ? null : Direction.from3DDataValue(side)).orElse(null);
                    if (energy == null) continue;
                    if (energy.canReceive()) receive |= 1 << side;
                    if (energy.canExtract()) extract |= 1 << side;
                }
                return receive == 0 && extract == 0 ? null : new EnergyConnections(receive, extract);
            }
            public long time() { return level instanceof Level world ? world.getGameTime() : 0; }
        };
    }
    private InteractionResult useLegacyItem(UseOnContext context) {
        return useLegacyItem(context, false);
    }
    private InteractionResult useLegacyItem(UseOnContext context, boolean first) {
        if (context.getPlayer() == null) return InteractionResult.PASS;
        // Some old first-use handlers write blocks even on a remote world (e.g. TC4 wooden tables).
        // The native use-on packet still runs this hook on the server; wait for its authoritative update.
        if (first && context.getLevel().isClientSide) return InteractionResult.PASS;
        var level = context.getLevel(); var player = context.getPlayer();
        var stack = context.getItemInHand(); var pos = context.getClickedPos(); var hit = context.getClickLocation().subtract(pos.getX(),pos.getY(),pos.getZ());
        try (var transaction = new PlacementTransaction(level)) {
            transaction.record(pos);
            var actor = new LegacyTiles.Placer(player.getUUID(),player.getName().getString(),player.getX(),player.getY(),player.getZ(),player.getYRot(),player.getXRot(),player.getAbilities().instabuild,player.isShiftKeyDown());
            // The whole main inventory is exposed: ItemEssence hands the filled phial back through addItemStackToInventory.
            int selected = player.getInventory().selected;
            var before = hostInventory(player);
            var held = snapshot(stack);
            if (context.getHand() == net.minecraft.world.InteractionHand.OFF_HAND) before.set(selected,held);
            var callback = new LegacyItemPlacement(services);
            // Use-on callbacks drop items too: TC4's wand turns a bookshelf into a dropped Thaumonomicon.
            var result = LegacyItemDrops.capture(this,player,()->first
                    ? callback.useFirst(services.worlds().bound(level),actor,held,before,selected,pos.getX(),pos.getY(),pos.getZ(),context.getClickedFace().get3DDataValue(),(float)hit.x,(float)hit.y,(float)hit.z)
                    : callback.use(services.worlds().bound(level),actor,held,before,selected,pos.getX(),pos.getY(),pos.getZ(),context.getClickedFace().get3DDataValue(),(float)hit.x,(float)hit.y,(float)hit.z));
            if (!first && result.used() && stack.getItem() instanceof MirroredBlockItem blockItem && BlockItem.getBlockEntityData(stack)!=null) {
                // Apply only to a block actually placed by this callback, through vanilla's permission checks.
                for (var placed : List.of(pos, pos.relative(context.getClickedFace()))) {
                    var previous=transaction.before.get(placed);
                    if(previous==null || previous.state().equals(level.getBlockState(placed)) || !level.getBlockState(placed).is(blockItem.getBlock()))continue;
                    var data=BlockItem.getBlockEntityData(stack);
                    var blockKey=((MirroredBlock)blockItem.getBlock()).entry.key();
                    if(data.contains("mfcl_legacy_block") && !data.getString("mfcl_legacy_block").equals(blockKey))throw new IllegalArgumentException("Block entity data belongs to another legacy block");
                    if(BlockItem.updateCustomBlockEntityTag(level,player,placed,stack) && level.getBlockEntity(placed) instanceof LegacyBlockEntity tile)tile.synchronize();
                    break;
                }
            }
            transaction.commit();
            if (level.isClientSide) return result.used() ? InteractionResult.SUCCESS : InteractionResult.PASS;
            for (int slot = 0; slot < result.inventory().size() && slot < 36; slot++) {
                if (slot == selected || sameLegacy(before.get(slot), result.inventory().get(slot))) continue; // The held stack is applied below.
                try { player.getInventory().setItem(slot, result.inventory().get(slot) == null ? ItemStack.EMPTY : keepHostTags(player.getInventory().getItem(slot), gameplayStack(result.inventory().get(slot)))); }
                catch (Exception unmapped) { LogUtils.getLogger().warn("MFCL cannot map original inventory change in slot {}: {}", slot, result.inventory().get(slot), unmapped); }
            }
            if (!sameLegacy(held,result.stack())) {
                if (result.stack() == null) { if (first || !player.getAbilities().instabuild) stack.setCount(0); }
                else {
                    var updated = keepHostTags(stack, gameplayStack(result.stack()));
                    if (!first && player.getAbilities().instabuild) updated.setCount(stack.getCount());
                    if (ItemStack.isSameItemSameTags(stack,updated)) stack.setCount(updated.getCount());
                    else { stack.setCount(0); player.setItemInHand(context.getHand(),updated); }
                }
            }
            if(player instanceof net.minecraft.server.level.ServerPlayer server){
                result.messages().forEach(message->ScanSyncChannel.sendToPlayer(server,message));
                result.feedback().forEach(message->server.displayClientMessage(Component.literal(message),false));
                if(result.device()!=null)LegacyDeviceMenu.open(server,ModernContentBridge.this,result.device());
            }
            return result.used() ? InteractionResult.CONSUME : InteractionResult.PASS;
        } catch (Exception failure) {
            // Old placement code (ItemArcaneDoor) writes blocks on the remote world too; the use-on packet still
            // runs it on the server, whose block and inventory updates replace the client prediction.
            if (level.isClientSide && github.com.gengyoubo.mfcl.bridge.LegacyClientMutationException.causes(failure)) return InteractionResult.SUCCESS;
            LogUtils.getLogger().error("MFCL original item placement failed for {} at {}", BuiltInRegistries.ITEM.getKey(stack.getItem()),pos,failure);
            player.displayClientMessage(Component.translatable("mfcl.content.placement_failed"),true);
            return InteractionResult.FAIL;
        }
    }
    public void beforeMutation(Level level, BlockPos pos) {
        var transaction = placement.get();
        if (transaction != null && transaction.level == level) transaction.record(pos);
    }
    public <T> T logicTransaction(Level level, BlockPos pos, java.util.concurrent.Callable<T> action, boolean immediate) throws Exception {
        if (placement.get() != null) { beforeMutation(level,pos); return action.call(); }
        try (var transaction = new PlacementTransaction(level)) {
            transaction.record(pos); var result = action.call(); transaction.commit(immediate); return result;
        }
    }
    public void afterCommit(Level level, Runnable action) {
        var transaction = placement.get();
        if (transaction != null && transaction.level == level) transaction.effects.add(action);
        else action.run();
    }
    Integer statistic(net.minecraft.server.level.ServerPlayer player,String id){var transaction=placement.get();if(transaction!=null&&transaction.level==player.level()){var values=transaction.statistics.get(player.getUUID());if(values!=null&&values.containsKey(id))return values.get(id);}return statistics.read(player,id);}
    void statistic(net.minecraft.server.level.ServerPlayer player,String id,int value){var transaction=placement.get();if(transaction!=null&&transaction.level==player.level())transaction.statistics.computeIfAbsent(player.getUUID(),key->new HashMap<>()).put(id,value);afterCommit(player.level(),()->statistics.write(player,id,value));}
    private final class PlacementTransaction implements AutoCloseable {
        private record Before(BlockState state, CompoundTag tile) {}
        private final Level level;
        private final Map<BlockPos,Before> before = new LinkedHashMap<>();
        private final List<Runnable> effects = new ArrayList<>();
        private final Map<java.util.UUID,Map<String,Integer>> statistics=new HashMap<>();
        private boolean committed;
        PlacementTransaction(Level level) {
            if (placement.get() != null) throw new IllegalStateException("Nested legacy placement");
            this.level = level; placement.set(this);
        }
        void record(BlockPos pos) {
            before.computeIfAbsent(pos.immutable(), key -> {
                var tile = level.getBlockEntity(key);
                return new Before(level.getBlockState(key),tile == null ? null : tile.saveWithFullMetadata());
            });
        }
        boolean hasBlockChanges() {
            for(var entry:before.entrySet())if(!level.getBlockState(entry.getKey()).equals(entry.getValue().state()))return true;
            return false;
        }
        void commit() {
            commit(true);
        }
        void commit(boolean immediate) {
            for (var pos : before.keySet()) if (level.getBlockEntity(pos) instanceof LegacyBlockEntity tile) tile.captureChanges(immediate);
            committed = true;
            placement.remove();
            effects.forEach(Runnable::run);
        }
        @Override public void close() {
            placement.remove();
            if (committed) return;
            var positions = new ArrayList<>(before.keySet()); Collections.reverse(positions);
            for (var pos : positions) {
                var saved = before.get(pos); level.setBlock(pos,saved.state(),3);
                if (saved.tile() != null && level.getBlockEntity(pos) != null) level.getBlockEntity(pos).load(saved.tile());
            }
        }
    }
    private BlockBehaviour.Properties properties(LegacyContent.Block entry) {
        var properties = BlockBehaviour.Properties.of().strength(entry.hardness(), entry.resistance()).lightLevel(state -> entry.light());
        // BlockBehaviour.randomTick delegates to our tick override, which runs the original updateTick transaction.
        if(entry.randomTicks())properties.randomTicks();
        var sound=entry.sound();
        var breaking=net.minecraft.sounds.SoundEvent.createVariableRangeEvent(ResourceLocation.parse(LegacySounds.modernId(sound.breaking())));
        var stepping=net.minecraft.sounds.SoundEvent.createVariableRangeEvent(ResourceLocation.parse(LegacySounds.modernId(sound.stepping())));
        var placing=net.minecraft.sounds.SoundEvent.createVariableRangeEvent(ResourceLocation.parse(LegacySounds.placementId(sound.placing())));
        String step=stepping.getLocation().toString(),base=step.endsWith(".step")?step.substring(0,step.length()-5):null;
        properties.sound(new SoundType(sound.volume(),sound.pitch(),breaking,stepping,placing,
                base==null?stepping:net.minecraft.sounds.SoundEvent.createVariableRangeEvent(ResourceLocation.parse(base+".hit")),
                base==null?stepping:net.minecraft.sounds.SoundEvent.createVariableRangeEvent(ResourceLocation.parse(base+".fall"))));
        if(declarations.requiresTool(entry.key()))properties.requiresCorrectToolForDrops();
        if (!entry.opaque()) properties.noOcclusion().isSuffocating((state,level,pos)->false).isViewBlocking((state,level,pos)->false);
        return properties;
    }
}
