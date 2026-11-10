package github.com.gengyoubo.mfcl.bridge;

import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Real old TileEntity construction, lifecycle and binary NBT. The host owns positions and scheduling. */
public final class LegacyTiles {
    public record Placer(UUID id, String name, double x, double y, double z, float yaw, float pitch, boolean creative, boolean sneaking) {
        public Placer(UUID id, String name, double x, double y, double z, float yaw, float pitch) { this(id,name,x,y,z,yaw,pitch,false,false); }
    }
    private final LegacyServices services;
    private final ClassLoader loader;
    private final Class<?> blockType, tileType, worldType, nbtType;
    private final Object registry;
    private static final Map<ClassLoader,LegacyTiles> RUNTIMES = Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<Object,Map<UUID,Object>> fakePlayers = Collections.synchronizedMap(new WeakHashMap<>());
    public LegacyTiles(LegacyServices services) throws Exception {
        this.services = services; loader = services.loader(); RUNTIMES.put(loader, this);
        blockType = loader.loadClass("net.minecraft.block.Block"); tileType = loader.loadClass("net.minecraft.tileentity.TileEntity");
        worldType = loader.loadClass("net.minecraft.world.World"); nbtType = loader.loadClass("net.minecraft.nbt.NBTTagCompound");
        registry = blockType.getField("field_149771_c").get(null);
    }
    private Object block(String key) throws Exception {
        Object block = registry.getClass().getMethod("func_82594_a", String.class).invoke(registry, key);
        if (!key.equals(registry.getClass().getMethod("func_148750_c", Object.class).invoke(registry, block))) throw new IllegalArgumentException("Unknown old block " + key);
        return block;
    }
    public Object create(String key, int metadata, Object world, int x, int y, int z) throws Exception {
        return services.items().inLegacy(() -> {
            Object block=block(key);
            if(!(boolean)blockType.getMethod("hasTileEntity",int.class).invoke(block,metadata))return null;
            Object tile = blockType.getMethod("createTileEntity", worldType, int.class).invoke(block, world, metadata);
            if (tile == null) return null; // A technical block may report hasTileEntity but expose no tile for this metadata.
            attach(tile, key, metadata, world, x, y, z); return tile;
        });
    }
    public void attach(Object tile, String key, int metadata, Object world, int x, int y, int z) throws Exception {
        if (!tileType.isInstance(tile) || !worldType.isInstance(world)) throw new IllegalArgumentException("Tile/world belongs to another runtime");
        boolean validate=tileType.getMethod("func_145831_w").invoke(tile)!=world
                ||tileType.getField("field_145851_c").getInt(tile)!=x||tileType.getField("field_145848_d").getInt(tile)!=y||tileType.getField("field_145849_e").getInt(tile)!=z
                ||(boolean)tileType.getMethod("func_145837_r").invoke(tile);
        tileType.getMethod("func_145834_a", worldType).invoke(tile, world);
        tileType.getField("field_145851_c").setInt(tile, x); tileType.getField("field_145848_d").setInt(tile, y); tileType.getField("field_145849_e").setInt(tile, z);
        tileType.getField("field_145847_g").setInt(tile, metadata); tileType.getField("field_145854_h").set(tile, block(key));
        if(validate)tileType.getMethod("func_145829_t").invoke(tile);
    }
    /** The original client getRenderBoundingBox in world coordinates; null for INFINITE_EXTENT_AABB. */
    public double[] renderBounds(Object tile) throws Exception {
        return services.items().inLegacy(() -> {
            Object box = tileType.getMethod("getRenderBoundingBox").invoke(tile);
            if (box == null || box == tileType.getField("INFINITE_EXTENT_AABB").get(null)) return null;
            Class<?> type = box.getClass(); double[] bounds = new double[6]; int i = 0;
            for (String name : new String[]{"field_72340_a","field_72338_b","field_72339_c","field_72336_d","field_72337_e","field_72334_f"}) bounds[i++] = type.getField(name).getDouble(box);
            return bounds;
        });
    }
    public byte[] save(Object tile) throws Exception {
        return services.items().inLegacy(() -> {
            Object nbt = nbtType.getConstructor().newInstance(); tileType.getMethod("func_145841_b", nbtType).invoke(tile, nbt);
            return (byte[]) loader.loadClass("net.minecraft.nbt.CompressedStreamTools").getMethod("func_74798_a", nbtType).invoke(null, nbt);
        });
    }
    public void load(Object tile, byte[] bytes) throws Exception {
        load(tile,bytes,null);
    }
    /** Rebase the snapshot before readFromNBT, whose inventory callbacks may already query the world. */
    public void loadInScene(Object tile, byte[] bytes, int x, int y, int z) throws Exception {
        load(tile,bytes,new int[]{x,y,z});
    }
    private void load(Object tile, byte[] bytes, int[] position) throws Exception {
        services.items().inLegacy(() -> {
            Class<?> tracker = loader.loadClass("net.minecraft.nbt.NBTSizeTracker");
            Object nbt = loader.loadClass("net.minecraft.nbt.CompressedStreamTools").getMethod("func_152457_a", byte[].class, tracker)
                    .invoke(null, bytes, tracker.getConstructor(long.class).newInstance(8L * 1024 * 1024));
            if(position!=null) {
                var setInt=nbtType.getMethod("func_74768_a",String.class,int.class);
                setInt.invoke(nbt,"x",position[0]);setInt.invoke(nbt,"y",position[1]);setInt.invoke(nbt,"z",position[2]);
            }
            tileType.getMethod("func_145839_a", nbtType).invoke(tile, nbt); return null;
        });
    }
    /** NBT identifies the authoritative tile subtype, including replacements within one block ID. */
    public Object snapshotInstance(Object current, byte[] bytes) throws Exception {
        return services.items().inLegacy(() -> {
            Class<?> tracker = loader.loadClass("net.minecraft.nbt.NBTSizeTracker");
            Object nbt = loader.loadClass("net.minecraft.nbt.CompressedStreamTools").getMethod("func_152457_a", byte[].class, tracker)
                    .invoke(null, bytes, tracker.getConstructor(long.class).newInstance(8L * 1024 * 1024));
            String id = (String) nbtType.getMethod("func_74779_i", String.class).invoke(nbt, "id");
            Field mappings = tileType.getDeclaredField("field_145855_i"); mappings.setAccessible(true);
            Class<?> type = (Class<?>) ((Map<?, ?>) mappings.get(null)).get(id);
            if (type == null) throw new IllegalArgumentException("Unknown saved legacy tile " + id);
            return current != null && current.getClass() == type ? current : type.getConstructor().newInstance();
        });
    }
    public void invalidate(Object tile) throws Exception { services.items().inLegacy(() -> { tileType.getMethod("func_145843_s").invoke(tile); return null; }); }
    public void metadata(Object tile, int metadata) throws Exception { tileType.getField("field_145847_g").setInt(tile, metadata); }
    public void placed(String key, Object world, int x, int y, int z, Placer placer, LegacyClientData.Stack stack) throws Exception {
        services.items().inLegacy(() -> {
            Object actor = actor(world, placer, stack);
            blockType.getMethod("func_149689_a", worldType, int.class, int.class, int.class, loader.loadClass("net.minecraft.entity.EntityLivingBase"), loader.loadClass("net.minecraft.item.ItemStack"))
                    .invoke(block(key), world, x, y, z, actor, services.items().materialize(stack));
            return null;
        });
    }
    /**
     * FakePlayerFactory.get(WorldServer, GameProfile): Forge's FakePlayer constructor needs the absent old server, so the
     * fake player is a server actor of the exact FakePlayer type, one per world and profile like the original cache.
     */
    public static Object fakePlayer(Object world, Object profile) throws Exception {
        var tiles = RUNTIMES.get(world.getClass().getClassLoader());
        if (tiles == null) throw new IllegalStateException("No legacy runtime owns this world");
        UUID id = (UUID) profile.getClass().getMethod("getId").invoke(profile); String name = (String) profile.getClass().getMethod("getName").invoke(profile);
        // TC4 and other old mods use name-only profiles. Give each one a stable identity before any host lookup.
        if (id == null) id = UUID.nameUUIDFromBytes(("MFCL:FakePlayer:" + name).getBytes(StandardCharsets.UTF_8));
        var players = tiles.fakePlayers.computeIfAbsent(world, key -> Collections.synchronizedMap(new HashMap<>()));
        Object player = players.get(id);
        if (player == null) {
            // Like Forge's FakePlayer, it stands at the origin; callers that need a position set it themselves.
            player = tiles.actor(world, new Placer(id, name, 0, 0, 0, 0, 0), null, "net.minecraftforge.common.util.FakePlayer");
            players.put(id, player);
        }
        return player;
    }
    public Object actor(Object world, Placer placer, LegacyClientData.Stack stack) throws Exception {
        return actor(world, placer, stack, "net.minecraft.entity.player.EntityPlayerMP");
    }
    private Object actor(Object world, Placer placer, LegacyClientData.Stack stack, String serverType) throws Exception {
        if (placer == null) return null;
        boolean client = worldType.getField("field_72995_K").getBoolean(world);
        Class<?> actorType = loader.loadClass(client ? "net.minecraft.client.entity.EntityClientPlayerMP" : serverType);
        Field field = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe"); field.setAccessible(true); Object unsafe = field.get(null);
        Object actor = unsafe.getClass().getMethod("allocateInstance", Class.class).invoke(unsafe, actorType);
        if(client) {
            Object movement = loader.loadClass("net.minecraft.util.MovementInput").getConstructor().newInstance();
            movement.getClass().getField("field_78899_d").setBoolean(movement,placer.sneaking());
            actorType.getField("field_71158_b").set(actor,movement);
        } else {
            Class<?> manager=loader.loadClass("net.minecraft.server.management.ItemInWorldManager");
            Object access=manager.getConstructor(worldType).newInstance(world);manager.getField("field_73090_b").set(access,actor);
            var interaction=actorType.getField("field_71134_c");interaction.setAccessible(true);interaction.set(actor,access);
            var statistics=loader.loadClass("net.minecraft.entity.player.EntityPlayerMP").getDeclaredField("field_147103_bO");statistics.setAccessible(true);
            statistics.set(actor,LegacyPlayerStatistics.create(loader,services.gameDirectory().resolve("mfcl/player-stats/"+placer.id()+".json").toFile(),actor));
            // Effect/inventory updates sent to the actor's connection are dropped (it has none); the host syncs the real player.
            Class<?> handler=loader.loadClass("net.minecraft.network.NetHandlerPlayServer");
            Object connection=unsafe.getClass().getMethod("allocateInstance",Class.class).invoke(unsafe,handler);
            handler.getField("field_147369_b").set(connection,actor);actorType.getField("field_71135_a").set(actor,connection);
        }
        Class<?> player = loader.loadClass("net.minecraft.entity.player.EntityPlayer"), entity = loader.loadClass("net.minecraft.entity.Entity");
        var uuid=entity.getDeclaredField("field_96093_i");uuid.setAccessible(true);uuid.set(actor,placer.id());
        var random=entity.getDeclaredField("field_70146_Z");random.setAccessible(true);random.set(actor,new Random());
        entity.getField("capturedDrops").set(actor,new ArrayList<>());
        float eyeHeight = LegacyPlayerBody.eyeHeight(placer.id(), client);
        player.getField("eyeHeight").setFloat(actor,client?.12f:eyeHeight);
        entity.getField("field_70129_M").setFloat(actor,client?1.62f:0);
        var profile = player.getDeclaredField("field_146106_i"); profile.setAccessible(true);
        profile.set(actor, loader.loadClass("com.mojang.authlib.GameProfile").getConstructor(UUID.class, String.class).newInstance(placer.id(), placer.name()));
        Object inventory = loader.loadClass("net.minecraft.entity.player.InventoryPlayer").getConstructor(player).newInstance(actor);
        player.getField("field_71071_by").set(actor, inventory);
        LegacyPlayerArmor.apply(services, actor, placer.id(), client);
        if (stack != null) inventory.getClass().getMethod("func_70299_a",int.class,loader.loadClass("net.minecraft.item.ItemStack")).invoke(inventory,0,services.items().materialize(stack));
        Object capabilities = loader.loadClass("net.minecraft.entity.player.PlayerCapabilities").getConstructor().newInstance();
        capabilities.getClass().getField("field_75098_d").setBoolean(capabilities, placer.creative());
        player.getField("field_71075_bZ").set(actor, capabilities);
        Object watcher = loader.loadClass("net.minecraft.entity.DataWatcher").getConstructor(entity).newInstance(actor);
        var data = entity.getDeclaredField("field_70180_af"); data.setAccessible(true); data.set(actor,watcher);
        watcher.getClass().getMethod("func_75682_a",int.class,Object.class).invoke(watcher,0,(byte)(placer.sneaking()?2:0));
        watcher.getClass().getMethod("func_75682_a",int.class,Object.class).invoke(watcher,6,20f);
        entity.getField("field_70170_p").set(actor, world);
        LegacyPlayerAttributes.initialize(actor);
        LegacyPlayerFood.initialize(actor);
        LegacyPlayerFlight.refresh(actor);
        // Entity.dimension: old code compares it with dimension ids (TC4 eldritch portals pick the travel direction by it).
        entity.getField("field_71093_bK").setInt(actor, loader.loadClass("net.minecraft.world.WorldProvider").getField("field_76574_g").getInt(worldType.getField("field_73011_w").get(world)));
        entity.getField("field_70165_t").setDouble(actor, placer.x()); entity.getField("field_70163_u").setDouble(actor, placer.y()+(client?eyeHeight:0)); entity.getField("field_70161_v").setDouble(actor, placer.z());
        entity.getField("field_70177_z").setFloat(actor, placer.yaw()); entity.getField("field_70125_A").setFloat(actor, placer.pitch());
        // Range/ray-trace helpers read the player's bounding box; the allocated actor has none, so build one directly.
        Object box = loader.loadClass("net.minecraft.util.AxisAlignedBB").getMethod("func_72330_a", double.class, double.class, double.class, double.class, double.class, double.class)
                .invoke(null, placer.x() - 0.3, placer.y(), placer.z() - 0.3, placer.x() + 0.3, placer.y() + 1.8, placer.z() + 0.3);
        var bbox = entity.getDeclaredField("field_70121_D"); bbox.setAccessible(true); bbox.set(actor, box);
        entity.getField("field_70130_N").setFloat(actor, 0.6f); entity.getField("field_70131_O").setFloat(actor, 1.8f);
        var potions = loader.loadClass("net.minecraft.entity.EntityLivingBase").getDeclaredField("field_70713_bf"); potions.setAccessible(true); potions.set(actor,new HashMap<>());
        LegacyEntityAccess.forgeConstruct(loader,actor,world);
        Object container=loader.loadClass("net.minecraft.inventory.ContainerPlayer").getConstructor(loader.loadClass("net.minecraft.entity.player.InventoryPlayer"),boolean.class,player).newInstance(inventory,false,actor);
        player.getField("field_71069_bz").set(actor,container);
        player.getField("field_71070_bA").set(actor,container);
        // A fake player has no host entity to mirror; host-backed hooks must not look one up.
        if (!actorType.getName().equals("net.minecraftforge.common.util.FakePlayer")) LegacyEntityHooks.mirrored(actor);
        return actor;
    }
    public List<LegacyClientData.Stack> inventory(Object tile) throws Exception {
        return services.items().inLegacy(() -> {
            Class<?> inventory = loader.loadClass("net.minecraft.inventory.IInventory");
            if (!inventory.isInstance(tile)) return List.of();
            var result = new ArrayList<LegacyClientData.Stack>();
            int size = (int) inventory.getMethod("func_70302_i_").invoke(tile);
            for (int slot = 0; slot < size; slot++) {
                Object value = inventory.getMethod("func_70301_a", int.class).invoke(tile, slot);
                if (value != null) result.add(services.items().snapshot(value));
            }
            return List.copyOf(result);
        });
    }
}
