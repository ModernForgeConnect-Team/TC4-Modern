package github.com.gengyoubo.mfcl.bridge;

import org.objectweb.asm.*;
import org.objectweb.asm.Type;
import java.lang.reflect.*;
import java.util.*;

/** Maintains typed old World/WorldProvider objects, stable identity, direct fields and synchronous forwarding. */
public final class LegacyWorlds implements AutoCloseable {
    public static java.util.List<Object> serverWorlds(ClassLoader loader){synchronized(DISPATCH){return DISPATCH.values().stream().filter(binding->binding.loader==loader&&binding.owner==Thread.currentThread()&&!binding.host.clientSide()&&binding.host.dimensionWorld()).map(binding->binding.world).toList();}}
    private static final Map<Object, Binding> DISPATCH = Collections.synchronizedMap(new IdentityHashMap<>());
    private final Map<Object, Binding> worlds = new IdentityHashMap<>();
    private final ClassLoader loader;
    private final LegacyClientData items;
    private final LegacyBiomes biomes;
    private Class<?> clientFacade, serverFacade;
    private static final class Binding {
        final Object world; final HostWorldAccess host; final Thread owner; final ClassLoader loader; final LegacyClientData items; final LegacyBiomes biomes;
        final Map<java.util.UUID,Object> queried=new LinkedHashMap<>();
        final Map<java.util.UUID,HostWorldAccess.Entity> snapshots=new LinkedHashMap<>();
        /** One mirror per live host item, so what old AI retained and what a later query returns are the same object. */
        final Map<java.util.UUID,Object> droppedItems=new com.google.common.collect.MapMaker().weakValues().makeMap();
        boolean motionCapture;
        long playersAt = Long.MIN_VALUE; List<Object> players = List.of();
        Binding(Object world, HostWorldAccess host, LegacyClientData items,LegacyBiomes biomes) { this.world = world; this.host = host; this.loader = items.loader(); this.items=items;this.biomes=biomes; owner = Thread.currentThread(); }
        void check() { if (Thread.currentThread() != owner) throw new IllegalStateException("Legacy world access must run on its host owner thread"); }
    }
    public LegacyWorlds(LegacyClientData items,LegacyBiomes biomes) { this.loader = items.loader(); this.items=items;this.biomes=biomes; }
    public synchronized Object bound(Object hostIdentity) {
        Binding binding = worlds.get(hostIdentity);
        if (binding == null) throw new IllegalStateException("Host world has no legacy binding");
        binding.check(); refreshDifficulty(binding); return binding.world;
    }
    /** Read-only API lookup; never creates a world on an arbitrary addon thread. */
    public synchronized Object findBound(Object hostIdentity) {
        Binding binding = worlds.get(hostIdentity);
        if (binding == null) return null;
        binding.check(); refreshDifficulty(binding); return binding.world;
    }
    public synchronized Object host(Object legacyWorld) {
        for (var entry : worlds.entrySet()) if (entry.getValue().world == legacyWorld) {
            entry.getValue().check(); return entry.getKey();
        }
        return null;
    }
    public synchronized Object bind(Object hostIdentity, HostWorldAccess host) throws Exception {
        Binding existing = worlds.get(hostIdentity);
        if (existing != null) { existing.check(); return existing.world; }
        Objects.requireNonNull(host, "Host world access");
        Class<?> facade;
        if (host.clientSide()) {
            if (clientFacade == null) clientFacade = defineFacade(true);
            facade = clientFacade;
        } else {
            if (serverFacade == null) serverFacade = defineFacade(false);
            facade = serverFacade;
        }
        Object world = allocate(facade);
        Class<?> providerType = loader.loadClass("net.minecraft.world.WorldProvider");
        int dimension = host.legacyDimensionId();
        // Dimensions an old mod registered itself (TC4's Outer Lands) keep their original provider: sky, spawn and chunk provider rules.
        boolean original = dimension != 0 && dimension != -1 && dimension != 1
                && (boolean) loader.loadClass("net.minecraftforge.common.DimensionManager").getMethod("isDimensionRegistered", int.class).invoke(null, dimension);
        Object provider = original ? loader.loadClass("net.minecraftforge.common.DimensionManager").getMethod("createProviderFor", int.class).invoke(null, dimension)
                : loader.loadClass(dimension==-1?"net.minecraft.world.WorldProviderHell":dimension==1?"net.minecraft.world.WorldProviderEnd":"net.minecraft.world.WorldProviderSurface").getConstructor().newInstance();
        set(loader.loadClass("net.minecraft.world.World"), world, "field_73011_w", provider);
        set(loader.loadClass("net.minecraft.world.World"), world, "field_72995_K", host.clientSide());
        set(loader.loadClass("net.minecraft.world.World"), world, "field_73012_v", new Random());
        // worldAccesses: allocated worlds skip field initializers, and spawnParticle/playSound iterate this list.
        set(loader.loadClass("net.minecraft.world.World"), world, "field_73021_x", new ArrayList<>());
        // capturedBlockSnapshots: ForgeHooks.onPlaceItemIntoWorld clones it after every ItemStack.tryPlaceItemIntoWorld (builder's wand...).
        set(loader.loadClass("net.minecraft.world.World"), world, "capturedBlockSnapshots", new ArrayList<>());
        // Entity.moveEntity uses World's reusable collision list, even in a façade allocated without its constructor.
        set(loader.loadClass("net.minecraft.world.World"), world, "field_72998_d", new ArrayList<>());
        // loadedEntityList: packet handlers (Witchery entity-size sync...) scan it for an id; host entities are not listed here.
        set(loader.loadClass("net.minecraft.world.World"), world, "field_72996_f", new ArrayList<>());
        // Some mods explicitly register active tiles here instead of returning canUpdate=true.
        set(loader.loadClass("net.minecraft.world.World"), world, "field_147482_g", new ArrayList<>());
        // theProfiler: Entity.onEntityUpdate and other original update loops open profiler sections unconditionally.
        set(loader.loadClass("net.minecraft.world.World"), world, "field_72984_F", loader.loadClass("net.minecraft.profiler.Profiler").getConstructor().newInstance());
        // playerEntities: old code (eldritch lock messages...) reads it directly; it mirrors the host players, refreshed once per tick.
        set(loader.loadClass("net.minecraft.world.World"), world, "field_73010_i", new AbstractList<Object>() {
            @Override public Object get(int index) { return players(world).get(index); }
            @Override public int size() { return players(world).size(); }
        });
        Object infoData = loader.loadClass("net.minecraft.nbt.NBTTagCompound").getConstructor().newInstance();
        infoData.getClass().getMethod("func_74772_a", String.class, long.class).invoke(infoData, "RandomSeed", host.seed());
        Object info = loader.loadClass("net.minecraft.world.storage.WorldInfo").getConstructor(loader.loadClass("net.minecraft.nbt.NBTTagCompound")).newInstance(infoData);
        set(loader.loadClass("net.minecraft.world.World"), world, "field_72986_A", info);
        set(loader.loadClass("net.minecraft.world.World"), world, "field_96442_D", loader.loadClass("net.minecraft.scoreboard.Scoreboard").getConstructor().newInstance());
        // getEntityTracker: setItemInUse(eat) and similar old code broadcast animations through it. With no tracked entries
        // those broadcasts are no-ops; the host tracks and animates the real entities itself.
        if (!host.clientSide()) {
            Class<?> trackerType = loader.loadClass("net.minecraft.entity.EntityTracker"); Object tracker = allocate(trackerType);
            set(trackerType, tracker, "field_72795_a", world);
            set(trackerType, tracker, "field_72793_b", new HashSet<>());
            set(trackerType, tracker, "field_72794_c", loader.loadClass("net.minecraft.util.IntHashMap").getConstructor().newInstance());
            set(loader.loadClass("net.minecraft.world.WorldServer"), world, "field_73062_L", tracker);
        }
        if (host.legacySaveDirectory() != null) storage(world, host.legacySaveDirectory(), dimension);
        set(providerType, provider, "field_76574_g", dimension);
        set(providerType, provider, "field_76579_a", world);
        // WorldChunkManager(World) calls getSeed/getWorldInfo through the facade during provider registration.
        var binding = new Binding(world, host, items,biomes); worlds.put(hostIdentity, binding); DISPATCH.put(world, binding);
        try {
        if (original) providerType.getMethod("func_76558_a", loader.loadClass("net.minecraft.world.World")).invoke(provider, world);
        else {
            // Host worlds skip registerWorld's chunk-manager setup, but mods still query terrain type/sea level.
            set(providerType, provider, "field_76577_b", info.getClass().getMethod("func_76067_t").invoke(info));
            set(providerType, provider, "field_82913_c", info.getClass().getMethod("func_82571_y").invoke(info));
        }
        if(!host.clientSide()&&host.dimensionWorld())dimensionWorlds().put(dimension,world);
        refreshDifficulty(binding);
        set(loader.loadClass("net.minecraft.world.World"),world,"field_73020_y",LegacyChunkProvider.create(loader,world));
        return world;
        }catch(Exception | LinkageError failure){unbind(hostIdentity);throw failure;}
    }
    private static void refreshDifficulty(Binding binding) {
        try {
            var type=binding.loader.loadClass("net.minecraft.world.EnumDifficulty");
            Object difficulty=type.getEnumConstants()[Math.max(0,Math.min(3,binding.host.difficulty()))];
            binding.loader.loadClass("net.minecraft.world.World").getField("field_73013_u").set(binding.world,difficulty);
        }catch(ReflectiveOperationException failure){throw new IllegalStateException("Cannot refresh old world difficulty",failure);}
    }
    public synchronized void unbind(Object hostIdentity) {
        Binding binding = worlds.get(hostIdentity);
        if (binding != null) {
            binding.check(); LegacyChunkTickets.unload(binding.world);worlds.remove(hostIdentity); DISPATCH.remove(binding.world);
            if(!binding.host.clientSide())dimensionWorlds().remove(binding.host.legacyDimensionId(),binding.world);
            var directory = binding.host.legacySaveDirectory();
            if (directory != null && worlds.values().stream().noneMatch(other -> directory.equals(other.host.legacySaveDirectory()))) storages.remove(directory);
        }
    }
    /** Old world lifecycle (WorldEvent.Load/Save) for adapters whose mods keep their own world files. */
    public interface Lifecycle {
        default void loaded(Object world, int dimension) throws Exception {}
        default void saving(Object world, int dimension) throws Exception {}
        default void tick(Object world, boolean start) throws Exception {}
        default void unloaded(Object world) throws Exception {}
        interface WorldData { void run(Object world) throws Exception; }
        /** Mod data an old mod loads and saves with the overworld (WorldEvent.Load/Save on dimension 0), such as global maze or network files. */
        static Lifecycle overworldData(WorldData load, WorldData save) {
            return new Lifecycle() {
                @Override public void loaded(Object world, int dimension) throws Exception { if (dimension == 0) load.run(world); }
                @Override public void saving(Object world, int dimension) throws Exception { if (dimension == 0) save.run(world); }
            };
        }
    }
    private final List<Lifecycle> lifecycles = new java.util.concurrent.CopyOnWriteArrayList<>();
    public void lifecycle(Lifecycle hooks) { lifecycles.add(Objects.requireNonNull(hooks)); }
    public void tick(Object hostIdentity,boolean start) throws Exception {
        Object world=bound(hostIdentity);
        items.inLegacy(()->{for(var hooks:lifecycles)hooks.tick(world,start);return null;});
    }
    public void unloaded(Object hostIdentity) throws Exception {
        Object world=bound(hostIdentity);
        items.inLegacy(()->{for(var hooks:lifecycles)hooks.unloaded(world);return null;});
    }
    // Not synchronized: inLegacy takes the runtime lock, which other threads hold while they look up bound worlds.
    public void loaded(Object hostIdentity) throws Exception {
        Object world = bound(hostIdentity); int dimension = binding(world).host.legacyDimensionId();
        if (!binding(world).host.dimensionWorld()) return;
        items.inLegacy(() -> { for (var hooks : lifecycles) hooks.loaded(world, dimension); return null; });
    }
    /** Writes old WorldSavedData (MapStorage, per-world storage) and runs adapter save hooks, as WorldServer.saveAllChunks did. */
    public void save(Object hostIdentity) throws Exception {
        Object world = bound(hostIdentity); int dimension = binding(world).host.legacyDimensionId();
        if (!binding(world).host.dimensionWorld()) return;
        items.inLegacy(() -> {
            var type = loader.loadClass("net.minecraft.world.World"); var storage = loader.loadClass("net.minecraft.world.storage.MapStorage");
            for (String field : List.of("field_72988_C", "perWorldStorage")) { Object data = type.getField(field).get(world); if (data != null) storage.getMethod("func_75744_a").invoke(data); }
            for (var hooks : lifecycles) hooks.saving(world, dimension);
            return null;
        });
    }
    /** One shared MapStorage per save (1.7.10 WorldServerMulti reuses the overworld's), plus a per-dimension storage. */
    private final Map<java.io.File, Object> storages = new HashMap<>();
    private void storage(Object world, java.io.File directory, int dimension) throws Exception {
        var storage = loader.loadClass("net.minecraft.world.storage.MapStorage"); var handler = loader.loadClass("net.minecraft.world.storage.ISaveHandler");
        Object shared = storages.get(directory);
        if (shared == null) storages.put(directory, shared = storage.getConstructor(handler).newInstance(saveHandler(directory, directory)));
        var worldType = loader.loadClass("net.minecraft.world.World");
        set(worldType, world, "field_73019_z", saveHandler(directory, directory));
        set(worldType, world, "field_72988_C", shared);
        set(worldType, world, "perWorldStorage", storage.getConstructor(handler).newInstance(saveHandler(directory, dimension == 0 ? directory : new java.io.File(directory, "DIM" + dimension))));
    }
    /** ISaveHandler answering only file locations; chunks and player files stay with the host. */
    private Object saveHandler(java.io.File root, java.io.File data) throws Exception {
        var type = loader.loadClass("net.minecraft.world.storage.ISaveHandler");
        return Proxy.newProxyInstance(loader, new Class<?>[]{type}, (proxy, method, args) -> switch (method.getName()) {
            case "func_75765_b" -> root;
            case "func_75760_g" -> root.getName();
            case "func_75758_b" -> { var folder = new java.io.File(data, "data"); folder.mkdirs(); yield new java.io.File(folder, args[0] + ".dat"); }
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == args[0];
            case "toString" -> "MFCL legacy save " + root;
            default -> null;
        });
    }
    /** Host contract of a bound old world, for bridges invoked from rewritten original code. */
    static HostWorldAccess access(Object world) { return binding(world).host; }
    /** Host of a live binding on its owner thread, else null: old entities may outlive or wander from their world. */
    static HostWorldAccess hostOrNull(Object world) {
        Binding binding = world == null ? null : DISPATCH.get(world);
        return binding == null || binding.owner != Thread.currentThread() ? null : binding.host;
    }
    /** The bound server world of a legacy dimension on the calling thread (MinecraftServer.worldServerForDimension). */
    static Object serverWorld(int dimension) {
        synchronized (DISPATCH) {
            for (var binding : DISPATCH.values())
                if (!binding.host.clientSide() && binding.host.dimensionWorld() && binding.owner == Thread.currentThread() && binding.host.legacyDimensionId() == dimension) return binding.world;
        }
        return null;
    }
    /** Old Chunk queries share the world's host state, including heights outside the old 0..255 storage array. */
    public static Object chunk(Object world, int x, int z) {
        var binding = binding(world);
        try { return LegacyChunkFacades.create(binding.loader, world, x, z, binding.host.legacyTiles(x, z)); }
        catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
    }
    @SuppressWarnings("unchecked") private Map<Integer,Object> dimensionWorlds() {
        try {
            var field=loader.loadClass("net.minecraftforge.common.DimensionManager").getDeclaredField("worlds");field.setAccessible(true);
            return (Map<Integer,Object>)field.get(null);
        }catch(ReflectiveOperationException failure){throw new IllegalStateException("Cannot expose host dimensions to original mods",failure);}
    }
    @Override public synchronized void close() {
        for (Binding binding : worlds.values()) {
            if(binding.owner==Thread.currentThread())LegacyChunkTickets.unload(binding.world);
            DISPATCH.remove(binding.world);
            if(!binding.host.clientSide())dimensionWorlds().remove(binding.host.legacyDimensionId(),binding.world);
        }
        worlds.clear();
    }
    private static Binding binding(Object world) {
        Binding binding = DISPATCH.get(world); if (binding == null) throw new IllegalStateException("Legacy world binding has expired"); binding.check(); return binding;
    }
    /** World.getCelestialAngle reads worldInfo's own time, which the host never advances; use the host's time of day. */
    public static float celestialAngle(Object world, float partial) throws ReflectiveOperationException {
        var bound = binding(world); Object provider = bound.loader.loadClass("net.minecraft.world.World").getField("field_73011_w").get(world);
        return (float) provider.getClass().getMethod("func_76563_a", long.class, float.class).invoke(provider, bound.host.dayTime(), partial);
    }
    public static long time(Object world, boolean total) { var binding = binding(world); return total ? binding.host.gameTime() : binding.host.dayTime(); }
    public static int viewDistance(Object world) { return binding(world).host.viewDistance(); }
    public static void sound(Object world,double x,double y,double z,String id,float volume,float pitch) {
        binding(world).host.playSound(new HostWorldAccess.Sound(x,y,z,id,volume,pitch,false,null));
    }
    public static void placementSound(Object world,Object block,int x,int y,int z) throws ReflectiveOperationException {
        var bound=binding(world);if(bound.host.clientSide())return;
        Class<?> blockType=bound.loader.loadClass("net.minecraft.block.Block");
        Object sound=blockType.getField("field_149762_H").get(block);
        Class<?> type=bound.loader.loadClass("net.minecraft.block.Block$SoundType");
        String id=LegacySounds.placementId((String)type.getMethod("func_150496_b").invoke(sound));
        // Old metal borrowed the stone sample at a higher pitch; modern Minecraft gives it its own event.
        if(sound==blockType.getField("field_149777_j").get(null))id="minecraft:block.metal.place";
        float volume=(float)type.getMethod("func_150497_c").invoke(sound),pitch=(float)type.getMethod("func_150494_d").invoke(sound);
        bound.host.playSound(new HostWorldAccess.Sound(x+.5,y+.5,z+.5,id,(volume+1)/2,pitch*.8f,false,null));
    }
    public static void auxiliaryEffect(Object world,Object excluded,int event,int x,int y,int z,int data) throws ReflectiveOperationException {
        var bound=binding(world);
        UUID id=excluded==null?null:(UUID)bound.loader.loadClass("net.minecraft.entity.Entity").getMethod("func_110124_au").invoke(excluded);
        if(event==1003) {
            // 1.7.10's single door event randomly chose either wooden door sample for both transitions.
            Random random=(Random)bound.loader.loadClass("net.minecraft.world.World").getField("field_73012_v").get(world);
            bound.host.playSound(new HostWorldAccess.Sound(x+.5,y+.5,z+.5,random.nextBoolean()?"random.door_open":"random.door_close",1,random.nextFloat()*.1f+.9f,false,id));
        } else if(event==2004) bound.host.levelEvent(id,2004,x,y,z,data); // mob-spawner flames/smoke
        else LegacyDiagnostics.missingMapping("world event "+event,"legacy auxiliary event has no host translation");
    }
    public static void particle(Object world,String name,double x,double y,double z,double velocityX,double velocityY,double velocityZ) {
        var host=binding(world).host;
        // Like RenderGlobal, only the client world draws named particles; the server's world accesses ignore them.
        if(host.clientSide())host.particle(name,x,y,z,velocityX,velocityY,velocityZ);
    }
    public static void localSound(Object world,double x,double y,double z,String id,float volume,float pitch,boolean delay) {
        var host=binding(world).host;
        // World.playSound is a client-only effect in 1.7.10; the server implementation is intentionally empty.
        if(host.clientSide())host.playSound(new HostWorldAccess.Sound(x,y,z,id,volume,pitch,delay,null));
    }
    public static void entitySound(Object world,Object entity,String id,float volume,float pitch,boolean exclude) throws ReflectiveOperationException {
        var bound=binding(world);var type=bound.loader.loadClass("net.minecraft.entity.Entity");java.util.UUID except=null;
        if(exclude) {
            Object profile=entity.getClass().getMethod("func_146103_bH").invoke(entity);
            except=(java.util.UUID)profile.getClass().getMethod("getId").invoke(profile);
        }
        bound.host.playSound(new HostWorldAccess.Sound(type.getField("field_70165_t").getDouble(entity),
                type.getField("field_70163_u").getDouble(entity)-type.getField("field_70129_M").getFloat(entity),
                type.getField("field_70161_v").getDouble(entity),id,volume,pitch,false,except));
    }
    public static void setTime(Object world, long time) { binding(world).host.setDayTime(time); }
    public static boolean explosionEffect(Object world,double x,double y,double z) {
        var host=hostOrNull(world);if(host==null)return false;
        host.explosionEffect(x,y,z);return true;
    }
    /** The plain arrow becomes a host arrow and drops become host items; every other old entity is hosted with its original logic. */
    public static boolean spawn(Object world, Object entity) {
        var bound = binding(world);
        if (bound.host.clientSide()) return false;
        try {
            Boolean captured=LegacyEntityCapture.spawn(world,entity);if(captured!=null)return captured;
            ClassLoader loader = bound.loader;
            Class<?> arrowType = loader.loadClass("net.minecraft.entity.projectile.EntityArrow");
            if(loader.loadClass("net.minecraft.entity.item.EntityItem").isInstance(entity))
                return LegacyDeviceContainers.drop(entity)||bound.host.spawnLegacyEntity(entity);
            // Mod arrows (TC4 primal arrows...) keep their own impact logic and renderer; only the plain arrow is translated.
            if (entity.getClass() != arrowType) return bound.host.spawnLegacyEntity(entity);
            Class<?> entityType = loader.loadClass("net.minecraft.entity.Entity");
            double x = entityType.getField("field_70165_t").getDouble(entity), y = entityType.getField("field_70163_u").getDouble(entity), z = entityType.getField("field_70161_v").getDouble(entity);
            double mx = entityType.getField("field_70159_w").getDouble(entity), my = entityType.getField("field_70181_x").getDouble(entity), mz = entityType.getField("field_70179_y").getDouble(entity);
            double damage = (double) arrowType.getMethod("func_70242_d").invoke(entity);
            boolean burning = (boolean) entityType.getMethod("func_70027_ad").invoke(entity);
            Object shooter = arrowType.getField("field_70250_c").get(entity);
            java.util.UUID owner = shooter == null ? null : (java.util.UUID) entityType.getMethod("func_110124_au").invoke(shooter);
            bound.host.spawnProjectile(new HostWorldAccess.Projectile(x, y, z, mx, my, mz, damage, burning, owner));
            return true;
        } catch (Exception error) { throw new IllegalStateException("Cannot translate legacy entity spawn", error); }
    }
    public static Object block(Object world, int x, int y, int z) {
        Binding binding = binding(world); var block = binding.host.block(x, y, z);
        Object represented = LegacyHostRegistry.block(binding.loader, block.legacyKey());
        if (represented != null) return represented;
        try {
            Object registry = binding.loader.loadClass("net.minecraft.block.Block").getField("field_149771_c").get(null);
            var keys = (Set<?>) registry.getClass().getMethod("func_148742_b").invoke(registry);
            if (!keys.contains(block.legacyKey())) throw new UnmappedLegacyBlockException("No old Block representation: " + block.legacyKey());
            return registry.getClass().getMethod("func_82594_a", String.class).invoke(registry, block.legacyKey());
        } catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
    }
    public static int metadata(Object world, int x, int y, int z) { return binding(world).host.block(x, y, z).metadata(); }
    public static boolean metadata(Object world, int x, int y, int z, int metadata, int flags) {
        var host = binding(world).host; var old = host.block(x, y, z);
        // 1.7.10 stored metadata on air invisibly (orphan door halves write to the missing half); the host has no such state.
        if (old.legacyKey().equals("minecraft:air")) return false;
        return host.setBlock(x, y, z, new HostWorldAccess.Block(old.legacyKey(), metadata), flags);
    }
    public static Object rayTrace(Object world, Object start, Object end, boolean stopOnLiquid, boolean ignoreBlockWithoutBoundingBox, boolean returnLastUncollidableBlock) {
        var binding = binding(world);
        try {
            var vecType = binding.loader.loadClass("net.minecraft.util.Vec3");
            if(binding.host.originalBlockRayTrace())return world.getClass().getMethod("mfcl$originalRayTrace",vecType,vecType,boolean.class,boolean.class,boolean.class)
                    .invoke(world,start,end,stopOnLiquid,ignoreBlockWithoutBoundingBox,returnLastUncollidableBlock);
            double sx = vecType.getField("field_72450_a").getDouble(start), sy = vecType.getField("field_72448_b").getDouble(start), sz = vecType.getField("field_72449_c").getDouble(start);
            double ex = vecType.getField("field_72450_a").getDouble(end), ey = vecType.getField("field_72448_b").getDouble(end), ez = vecType.getField("field_72449_c").getDouble(end);
            var hit = binding.host.rayTrace(sx, sy, sz, ex, ey, ez);
            if (hit == null) return null;
            Object point = vecType.getMethod("func_72443_a", double.class, double.class, double.class).invoke(null, hit.hitX(), hit.hitY(), hit.hitZ());
            return binding.loader.loadClass("net.minecraft.util.MovingObjectPosition")
                    .getConstructor(int.class, int.class, int.class, int.class, vecType).newInstance(hit.x(), hit.y(), hit.z(), hit.side(), point);
        } catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
    }
    public static Object tile(Object world, int x, int y, int z) { return binding(world).host.tileEntity(x, y, z); }
    public static void spawnerType(Object logic,String entity) {
        try {
            var type=logic.getClass().getClassLoader().loadClass("net.minecraft.tileentity.MobSpawnerBaseLogic");
            var host=hostOrNull(type.getMethod("func_98271_a").invoke(logic));if(host==null)return;
            host.spawnerType((int)type.getMethod("func_98275_b").invoke(logic),(int)type.getMethod("func_98274_c").invoke(logic),(int)type.getMethod("func_98266_d").invoke(logic),logic,entity);
        }catch(ReflectiveOperationException failure){throw new IllegalStateException("Cannot mirror original spawner type",failure);}
    }
    public static void tile(Object world, int x, int y, int z, Object tile) { binding(world).host.setTileEntity(x, y, z, tile); }
    public static void removeTile(Object world, int x, int y, int z) { binding(world).host.removeTileEntity(x, y, z); }
    public static void dirty(Object world, int x, int y, int z, Object tile) { binding(world).host.markTileDirty(x, y, z, tile); }
    public static void updated(Object world, int x, int y, int z) { binding(world).host.blockUpdated(x, y, z); }
    public static boolean powered(Object world, int x, int y, int z) { return binding(world).host.powered(x,y,z); }
    public static int power(Object world, int x, int y, int z, int side) { return binding(world).host.power(x,y,z,side); }
    public static boolean loaded(Object world, int x, int y, int z) { return binding(world).host.loaded(x,y,z); }
    /** Original library height guards use native bounds for bound worlds; unrelated old worlds retain 1.7 limits. */
    public static boolean validPosition(Object world,int x,int y,int z) {
        var host=hostOrNull(world);return host==null?y>=0&&y<256:host.loaded(x,y,z);
    }
    public static List<Object> entities(Object world,Class<?> type,Object box) {return selectedEntities(world,type,box,null);}
    /** Old AI can retain a snapshot for many ticks; keep the target's position and removal state authoritative. */
    public static void refreshEntity(Object world,Object entity) throws Exception {
        if(entity==null||!LegacyEntityHooks.isMirror(entity))return;
        var bound=binding(world);var type=bound.loader.loadClass("net.minecraft.entity.Entity");
        UUID id=(UUID)type.getMethod("func_110124_au").invoke(entity);var snapshot=bound.host.entity(id);
        type.getField("field_70128_L").setBoolean(entity,snapshot==null);
        if(snapshot!=null) {
            LegacyEntityAccess.mirror(bound.items,world,snapshot,entity);
            if(bound.motionCapture){bound.queried.put(id,entity);bound.snapshots.put(id,snapshot);}
        }
    }
    /**
     * First access this tick to an item mirror retained from an earlier query: the host item may have moved, shrunk or
     * gone, and changes made from now on must be committed like those to a mirror queried in this tick. A gone item reads
     * as dead and empty, so taking from it yields nothing.
     */
    static void retainedDroppedItem(Object world,Object entity) throws Exception {
        Binding bound=world==null?null:DISPATCH.get(world);
        if(bound==null||bound.owner!=Thread.currentThread()||!bound.motionCapture||bound.host.clientSide())return;
        var type=bound.loader.loadClass("net.minecraft.entity.Entity");var dead=type.getField("field_70128_L");
        if(dead.getBoolean(entity)||bound.queried.get(type.getMethod("func_110124_au").invoke(entity))==entity)return;
        refreshEntity(world,entity);
        if(!dead.getBoolean(entity))return;
        Object stack=entity.getClass().getMethod("func_92059_d").invoke(entity);
        if(stack!=null)stack.getClass().getField("field_77994_a").setInt(stack,0);
    }
    private static Object mirrored(Binding binding,Object world,HostWorldAccess.Entity snapshot) throws Exception {
        // A host entity that hosts an old entity is that old entity; it must not be replaced by a snapshot.
        Object hosted=binding.host.hostedLegacyEntity(snapshot.id());
        if(hosted!=null){if(binding.motionCapture){binding.queried.putIfAbsent(snapshot.id(),hosted);binding.snapshots.putIfAbsent(snapshot.id(),snapshot);}return hosted;}
        Object entity=binding.motionCapture?binding.queried.get(snapshot.id()):null;
        if(entity==null){
            boolean drop=snapshot.item()!=null&&!binding.host.clientSide();
            entity=LegacyEntityAccess.mirror(binding.items,world,snapshot,drop?binding.droppedItems.get(snapshot.id()):binding.host.existingEntity(snapshot.id()));
            if(drop)binding.droppedItems.put(snapshot.id(),entity);
            if(binding.motionCapture){binding.queried.put(snapshot.id(),entity);binding.snapshots.put(snapshot.id(),snapshot);}
        }
        return entity;
    }
    static LegacyClientData items(Object world){return binding(world).items;}
    public static Object entity(Object world,UUID id) throws Exception {
        var binding=binding(world);var snapshot=binding.host.entity(id);return snapshot==null?null:mirrored(binding,world,snapshot);
    }
    private static List<Object> players(Object world) {
        var binding=binding(world);long now=binding.host.gameTime();
        if(binding.playersAt!=now||binding.motionCapture) try {
            var result=new ArrayList<Object>();for(var snapshot:binding.host.players())result.add(mirrored(binding,world,snapshot));
            binding.players=List.copyOf(result);binding.playersAt=now;
        } catch(Exception failure){throw new IllegalStateException("Cannot mirror host players",failure);}
        return binding.players;
    }
    /** Old World.newExplosion: the explosion happens in the host; the returned object only carries its parameters. */
    public static Object explode(Object world,Object source,double x,double y,double z,float power,boolean fire,boolean smoking) {
        var binding=binding(world);
        try {
            var type=binding.loader.loadClass("net.minecraft.world.Explosion");
            Object explosion=type.getConstructor(binding.loader.loadClass("net.minecraft.world.World"),binding.loader.loadClass("net.minecraft.entity.Entity"),double.class,double.class,double.class,float.class).newInstance(world,source,x,y,z,power);
            type.getField("field_77286_a").setBoolean(explosion,fire);type.getField("field_82755_b").setBoolean(explosion,smoking);
            if(!binding.host.clientSide())binding.host.explode(x,y,z,power,fire,smoking);
            return explosion;
        } catch(ReflectiveOperationException failure){throw new IllegalStateException("Cannot forward original explosion",failure);}
    }
    public static List<Object> selectedEntities(Object world,Class<?> type,Object box,Object selector) {
        return selectedEntities(world,type,box,selector,null);
    }
    private static List<Object> selectedEntities(Object world,Class<?> type,Object box,Object selector,UUID excluded) {
        var binding=binding(world);
        try {
            var aabb=binding.loader.loadClass("net.minecraft.util.AxisAlignedBB");
            var snapshots=binding.host.entities(aabb.getField("field_72340_a").getDouble(box),aabb.getField("field_72338_b").getDouble(box),aabb.getField("field_72339_c").getDouble(box),aabb.getField("field_72336_d").getDouble(box),aabb.getField("field_72337_e").getDouble(box),aabb.getField("field_72334_f").getDouble(box));
            var result=new ArrayList<Object>();
            for(var snapshot:snapshots) {
                // Exclusion must precede mirroring: resolving the active player's actor refreshes its body and
                // would erase changes already made by the callback issuing this query (e.g. TC4 sword lift).
                if(snapshot.id().equals(excluded))continue;
                Object hosted=binding.host.hostedLegacyEntity(snapshot.id());
                if(hosted!=null?!type.isInstance(hosted):!type.isAssignableFrom(binding.loader.loadClass(snapshot.legacyClass())))continue;
                Object entity=mirrored(binding,world,snapshot);
                if(type.isInstance(entity)&&(selector==null||(boolean)binding.loader.loadClass("net.minecraft.command.IEntitySelector").getMethod("func_82704_a",binding.loader.loadClass("net.minecraft.entity.Entity")).invoke(selector,entity)))result.add(entity);
            }
            var entityType=binding.loader.loadClass("net.minecraft.entity.Entity");
            for(Object pending:LegacyDeviceContainers.pendingDrops()){
                if(!type.isInstance(pending)||entityType.getField("field_70170_p").get(pending)!=world||entityType.getField("field_70128_L").getBoolean(pending))continue;
                if(excluded!=null&&excluded.equals(entityType.getMethod("func_110124_au").invoke(pending)))continue;
                Object bounds=entityType.getField("field_70121_D").get(pending);
                if(!(boolean)aabb.getMethod("func_72326_a",aabb).invoke(box,bounds))continue;
                if(selector==null||(boolean)binding.loader.loadClass("net.minecraft.command.IEntitySelector").getMethod("func_82704_a",entityType).invoke(selector,pending))result.add(pending);
            }
            return result;
        }catch(Exception failure){throw new IllegalStateException("Cannot query host entities",failure);}
    }
    public static List<Object> excludingEntity(Object world,Object excluded,Object box) {
        var binding=binding(world);
        try {
            var entity=binding.loader.loadClass("net.minecraft.entity.Entity");
            UUID id=excluded==null?null:(UUID)entity.getMethod("func_110124_au").invoke(excluded);
            return selectedEntities(world,entity,box,null,id);
        }catch(Exception failure){throw new IllegalStateException(failure);}
    }
    public static void beginEntityQueries(Object world){clearEntityQueries(world);binding(world).motionCapture=true;}
    public static void clearEntityQueries(Object world){var binding=binding(world);binding.queried.clear();binding.snapshots.clear();binding.motionCapture=false;}
    public static void commitEntityMotion(Object world) throws Exception {
        var binding=binding(world);
        try {
            for(var entry:binding.queried.entrySet()) {
                var next=LegacyEntityAccess.motion(binding.loader,entry.getValue(),entry.getKey());var previous=binding.snapshots.get(entry.getKey());
                if(previous.item()!=null&&LegacyEntityHooks.isMirror(entry.getValue()))commitDroppedItem(binding,entry.getValue(),entry.getKey(),previous.item());
                if(next.x()!=previous.motionX()||next.y()!=previous.motionY()||next.z()!=previous.motionZ()||next.fallDistance()!=previous.fallDistance()||next.love()!=previous.love())binding.host.applyMotion(next);
            }
        }finally{clearEntityQueries(world);}
    }
    /** EntityItem.onUpdate would have killed an emptied item; a mirror never ticks, so emptied and dead both remove the host item. */
    private static void commitDroppedItem(Binding binding,Object mirror,UUID id,LegacyClientData.Stack before) throws Exception {
        Object stack=mirror.getClass().getMethod("func_92059_d").invoke(mirror);
        if(binding.loader.loadClass("net.minecraft.entity.Entity").getField("field_70128_L").getBoolean(mirror)||stack==null||stack.getClass().getField("field_77994_a").getInt(stack)<=0){binding.host.droppedItem(id,null);return;}
        var now=binding.items.snapshot(stack);
        // The trip through the old form may normalize a stack: only what old code changed afterwards is a change.
        if(sameStack(before,now)||sameStack(binding.items.snapshot(binding.items.materialize(before)),now))return;
        binding.host.droppedItem(id,now);
    }
    private static boolean sameStack(LegacyClientData.Stack left,LegacyClientData.Stack right) {
        return left.key().equals(right.key())&&left.metadata()==right.metadata()&&left.count()==right.count()&&left.tag().equals(right.tag());
    }
    public static int light(Object world, int x, int y, int z) { return binding(world).host.light(x,y,z); }
    public static int height(Object world, int x, int z) { return binding(world).host.height(x,z); }
    public static int minBuildHeight(Object world) { var host=hostOrNull(world);return host==null?0:host.minBuildHeight(); }
    public static int maxBuildHeight(Object world) { var host=hostOrNull(world);return host==null?256:host.maxBuildHeight(); }
    public static boolean regionLoaded(Object world,int x1,int y1,int z1,int x2,int y2,int z2){
        var host=binding(world).host;if(y2<host.minBuildHeight()||y1>=host.maxBuildHeight())return false;
        int y=Math.max(y1,host.minBuildHeight());
        for(int x=x1>>4;x<=x2>>4;x++)for(int z=z1>>4;z<=z2>>4;z++)if(!host.loaded(x<<4,y,z<<4))return false;
        return true;
    }
    public static boolean canSeeSky(Object world, int x, int y, int z) { return binding(world).host.canSeeSky(x,y,z); }
    /** getClosestPlayer: a negative range is unlimited in 1.7.10; host player queries are bounded to 256 blocks. */
    public static Object closestPlayer(Object world, double x, double y, double z, double range) {
        var binding = binding(world);
        try {
            double reach = range < 0 ? 256 : range;
            Object box = binding.loader.loadClass("net.minecraft.util.AxisAlignedBB").getMethod("func_72330_a",double.class,double.class,double.class,double.class,double.class,double.class)
                    .invoke(null,x-reach,y-reach,z-reach,x+reach,y+reach,z+reach);
            var entity = binding.loader.loadClass("net.minecraft.entity.Entity"); var distance = entity.getMethod("func_70092_e",double.class,double.class,double.class);
            Object closest = null; double best = range < 0 ? Double.MAX_VALUE : range * range;
            for (Object player : entities(world,binding.loader.loadClass("net.minecraft.entity.player.EntityPlayer"),box)) {
                double squared = (double) distance.invoke(player,x,y,z);
                if (squared < best) { best = squared; closest = player; }
            }
            return closest;
        } catch (ReflectiveOperationException error) { throw new IllegalStateException("Cannot query the closest host player", error); }
    }
    public static Object closestPlayerTo(Object world, Object entity, double range) {
        try {
            var type = binding(world).loader.loadClass("net.minecraft.entity.Entity");
            return closestPlayer(world,type.getField("field_70165_t").getDouble(entity),type.getField("field_70163_u").getDouble(entity),type.getField("field_70161_v").getDouble(entity),range);
        } catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
    }
    /** notifyBlocksOfNeighborChange: original neighbours run their old onNeighborBlockChange, host neighbours are updated by the host. */
    public static void neighbors(Object world, int x, int y, int z, Object source) {
        var binding = binding(world);
        try {
            var type = binding.loader.loadClass("net.minecraft.block.Block");
            var changed = type.getMethod("func_149695_a",binding.loader.loadClass("net.minecraft.world.World"),int.class,int.class,int.class,type);
            for (int[] side : new int[][]{{-1,0,0},{1,0,0},{0,-1,0},{0,1,0},{0,0,-1},{0,0,1}}) {
                int nx = x+side[0], ny = y+side[1], nz = z+side[2];
                var cell = binding.host.block(nx,ny,nz);
                if (!cell.legacyKey().startsWith("minecraft:") && !cell.legacyKey().startsWith("mfcl_host:")) changed.invoke(block(world,nx,ny,nz),world,nx,ny,nz,source);
            }
            binding.host.neighborsChanged(x,y,z);
        } catch (ReflectiveOperationException error) { throw new IllegalStateException("Cannot notify original neighbours", error); }
    }
    /** Entity status bytes (hearts, hurt flashes) on mirrors: the host entity broadcasts its own when changes are committed. */
    public static void entityState(Object world, Object entity, byte state) { }
    public static void comparators(Object world, int x, int y, int z, Object block) { binding(world).host.updateComparators(x,y,z); }
    private static String blockKey(Binding binding, Object block) {
        try {
            Object registry = binding.loader.loadClass("net.minecraft.block.Block").getField("field_149771_c").get(null);
            return (String)registry.getClass().getMethod("func_148750_c",Object.class).invoke(registry,block);
        } catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
    }
    public static void blockEvent(Object world, int x, int y, int z, Object block, int event, int data) {
        var binding = binding(world); binding.host.blockEvent(x,y,z,blockKey(binding,block),event,data);
    }
    public static void schedule(Object world, int x, int y, int z, Object block, int delay) {
        var binding = binding(world); binding.host.schedule(x,y,z,blockKey(binding,block),delay);
    }
    public static boolean air(Object world, int x, int y, int z) { return binding(world).host.block(x, y, z).legacyKey().equals("minecraft:air"); }
    public static Object biome(Object world, int x, int z) {
        var binding = binding(world);
        return binding.biomes.resolve(binding.host.biome(x,z));
    }
    /** Used by adapters for old mods that mutate the obsolete byte[] chunk biome array. */
    public static void setBiome(Object world,int x,int z,Object biome) {
        if(biome==null)return;
        var binding=binding(world);
        if(binding.host.clientSide())throw new UnsupportedOperationException("Legacy client cannot mutate authoritative biomes");
        binding.host.setBiome(x,z,binding.biomes.key(biome));
    }
    public static boolean canPlace(Object world, Object target, int x, int y, int z, boolean ignoreCollision, int side, Object entity, Object stack) {
        var binding = binding(world);
        try {
            var type = binding.loader.loadClass("net.minecraft.block.Block");
            Object registry = type.getField("field_149771_c").get(null);
            String key = (String)registry.getClass().getMethod("func_148750_c",Object.class).invoke(registry,target);
            Object existing = block(world,x,y,z);
            return (boolean)type.getMethod("isReplaceable",binding.loader.loadClass("net.minecraft.world.IBlockAccess"),int.class,int.class,int.class).invoke(existing,world,x,y,z)
                    && binding.host.canPlaceBlock(new HostWorldAccess.Block(key,0),x,y,z)
                    && (boolean)type.getMethod("func_149707_d",binding.loader.loadClass("net.minecraft.world.World"),int.class,int.class,int.class,int.class).invoke(target,world,x,y,z,side);
        } catch (Exception error) { throw new IllegalStateException("Cannot evaluate old placement conditions",error); }
    }
    /** isBlockNormalCubeDefault: the facade has no chunk provider, so ask the old block directly (ItemDoor hinge checks). */
    public static boolean normalCube(Object world, int x, int y, int z, boolean fallback) {
        var binding = binding(world);
        if (!binding.host.loaded(x,y,z)) return fallback;
        try { return (boolean)binding.loader.loadClass("net.minecraft.block.Block").getMethod("isNormalCube",binding.loader.loadClass("net.minecraft.world.IBlockAccess"),int.class,int.class,int.class)
                .invoke(block(world,x,y,z),world,x,y,z); }
        catch (Exception error) { throw new IllegalStateException(error); }
    }
    /** chunkExists(chunkX, chunkZ) for checkChunksExist and friends. */
    public static boolean chunkLoaded(Object world, int chunkX, int chunkZ) { return binding(world).host.loaded(chunkX<<4,64,chunkZ<<4); }
    public static boolean removeBlock(Object world, int x, int y, int z) { return binding(world).host.setBlock(x,y,z,new HostWorldAccess.Block("minecraft:air",0),3); }
    public static boolean solid(Object world, int x, int y, int z, Object direction, boolean fallback) {
        var binding = binding(world);
        try { return (boolean)binding.loader.loadClass("net.minecraft.block.Block").getMethod("isSideSolid",binding.loader.loadClass("net.minecraft.world.IBlockAccess"),int.class,int.class,int.class,binding.loader.loadClass("net.minecraftforge.common.util.ForgeDirection"))
                .invoke(block(world,x,y,z),world,x,y,z,direction); }
        catch (Exception error) { throw new IllegalStateException(error); }
    }
    public static boolean setBlock(Object world, int x, int y, int z, Object oldBlock, int metadata, int flags) {
        Binding binding = binding(world);
        try {
            Class<?> type = binding.loader.loadClass("net.minecraft.block.Block");
            if (!type.isInstance(oldBlock)) throw new IllegalArgumentException("Block belongs to another runtime");
            Object registry = type.getField("field_149771_c").get(null);
            String key = (String) registry.getClass().getMethod("func_148750_c", Object.class).invoke(registry, oldBlock);
            if (key == null) throw new UnsupportedOperationException("Unregistered legacy block");
            return binding.host.setBlock(x, y, z, new HostWorldAccess.Block(key, metadata), flags);
        } catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
    }
    private Object allocate(Class<?> type) throws Exception {
        Class<?> unsafe = Class.forName("sun.misc.Unsafe"); Field field = unsafe.getDeclaredField("theUnsafe"); field.setAccessible(true);
        return unsafe.getMethod("allocateInstance", Class.class).invoke(field.get(null), type);
    }
    private void set(Class<?> owner, Object target, String name, Object value) throws Exception {
        loader.loadClass("github.com.gengyoubo.mfcl.legacy1710.ModernEnumSupport").getMethod("setFailsafeFieldValue", Field.class, Object.class, Object.class)
                .invoke(null, owner.getDeclaredField(name), target, value);
    }
    private Class<?> defineFacade(boolean client) throws Exception {
        String name = "github/com/gengyoubo/mfcl/legacy1710/"+(client ? "HostWorldFacade" : "HostServerWorldFacade"), bridge = Type.getInternalName(LegacyWorlds.class);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, name, null, client ? "net/minecraft/client/multiplayer/WorldClient" : "net/minecraft/world/WorldServer", null);
        for (String method : List.of("func_82737_E", "func_72820_D")) {
            var mv = writer.visitMethod(Opcodes.ACC_PUBLIC, method, "()J", null, null); mv.visitCode(); mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitInsn(method.equals("func_82737_E") ? Opcodes.ICONST_1 : Opcodes.ICONST_0);
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, bridge, "time", "(Ljava/lang/Object;Z)J", false); mv.visitInsn(Opcodes.LRETURN); mv.visitMaxs(2, 1); mv.visitEnd();
        }
        forward(writer, bridge, "func_72877_b", "(J)V", "setTime", "(Ljava/lang/Object;J)V", null);
        forward(writer, bridge, "func_72826_c", "(F)F", "celestialAngle", "(Ljava/lang/Object;F)F", null);
        forward(writer, bridge, "func_147439_a", "(III)Lnet/minecraft/block/Block;", "block", "(Ljava/lang/Object;III)Ljava/lang/Object;", "net/minecraft/block/Block");
        forward(writer, bridge, "func_72805_g", "(III)I", "metadata", "(Ljava/lang/Object;III)I", null);
        forward(writer, bridge, "func_147465_d", "(IIILnet/minecraft/block/Block;II)Z", "setBlock", "(Ljava/lang/Object;IIILjava/lang/Object;II)Z", null);
        forward(writer, bridge, "func_72921_c", "(IIIII)Z", "metadata", "(Ljava/lang/Object;IIIII)Z", null);
        forward(writer, bridge, "func_147438_o", "(III)Lnet/minecraft/tileentity/TileEntity;", "tile", "(Ljava/lang/Object;III)Ljava/lang/Object;", "net/minecraft/tileentity/TileEntity");
        forward(writer, Type.getInternalName(LegacyPackets.class), "func_73045_a", "(I)Lnet/minecraft/entity/Entity;", "serverEntity", "(Ljava/lang/Object;I)Ljava/lang/Object;", "net/minecraft/entity/Entity");
        forward(writer, Type.getInternalName(LegacyPackets.class), "func_72924_a", "(Ljava/lang/String;)Lnet/minecraft/entity/player/EntityPlayer;", "serverPlayer", "(Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/Object;", "net/minecraft/entity/player/EntityPlayer");
        forward(writer, bridge, "func_147455_a", "(IIILnet/minecraft/tileentity/TileEntity;)V", "tile", "(Ljava/lang/Object;IIILjava/lang/Object;)V", null);
        forward(writer, bridge, "func_147475_p", "(III)V", "removeTile", "(Ljava/lang/Object;III)V", null);
        forward(writer, bridge, "func_147476_b", "(IIILnet/minecraft/tileentity/TileEntity;)V", "dirty", "(Ljava/lang/Object;IIILjava/lang/Object;)V", null);
        for (String method : List.of("func_147471_g", "func_147479_m")) forward(writer, bridge, method, "(III)V", "updated", "(Ljava/lang/Object;III)V", null);
        forward(writer, bridge, "func_147437_c", "(III)Z", "air", "(Ljava/lang/Object;III)Z", null);
        forward(writer, bridge, "func_147468_f", "(III)Z", "removeBlock", "(Ljava/lang/Object;III)Z", null);
        forward(writer, bridge, "func_147472_a", "(Lnet/minecraft/block/Block;IIIZILnet/minecraft/entity/Entity;Lnet/minecraft/item/ItemStack;)Z", "canPlace", "(Ljava/lang/Object;Ljava/lang/Object;IIIZILjava/lang/Object;Ljava/lang/Object;)Z", null);
        forward(writer, bridge, "func_147447_a", "(Lnet/minecraft/util/Vec3;Lnet/minecraft/util/Vec3;ZZZ)Lnet/minecraft/util/MovingObjectPosition;", "rayTrace", "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;ZZZ)Ljava/lang/Object;", "net/minecraft/util/MovingObjectPosition");
        // Selection boxes are not ray-test bounds: TC nodes deliberately expose an empty selection box.
        // Let the old World traversal preserve canCollideCheck, collisionRayTrace and all three flags.
        String rayDescriptor="(Lnet/minecraft/util/Vec3;Lnet/minecraft/util/Vec3;ZZZ)Lnet/minecraft/util/MovingObjectPosition;";
        var ray=writer.visitMethod(Opcodes.ACC_PUBLIC,"mfcl$originalRayTrace",rayDescriptor,null,null);ray.visitCode();
        for(int slot=0;slot<3;slot++)ray.visitVarInsn(Opcodes.ALOAD,slot);
        for(int slot=3;slot<6;slot++)ray.visitVarInsn(Opcodes.ILOAD,slot);
        ray.visitMethodInsn(Opcodes.INVOKESPECIAL,client?"net/minecraft/client/multiplayer/WorldClient":"net/minecraft/world/WorldServer","func_147447_a",rayDescriptor,false);
        ray.visitInsn(Opcodes.ARETURN);ray.visitMaxs(6,6);ray.visitEnd();
        forward(writer, bridge, "isSideSolid", "(IIILnet/minecraftforge/common/util/ForgeDirection;Z)Z", "solid", "(Ljava/lang/Object;IIILjava/lang/Object;Z)Z", null);
        forward(writer, bridge, "func_147445_c", "(IIIZ)Z", "normalCube", "(Ljava/lang/Object;IIIZ)Z", null);
        forward(writer, bridge, "func_72916_c", "(II)Z", "chunkLoaded", "(Ljava/lang/Object;II)Z", null);
        forward(writer, bridge, "func_72807_a", "(II)Lnet/minecraft/world/biome/BiomeGenBase;", "biome", "(Ljava/lang/Object;II)Ljava/lang/Object;", "net/minecraft/world/biome/BiomeGenBase");
        constant(writer, "func_72802_i", "(IIII)I", 0x00F000F0);
        forward(writer,bridge,"func_72800_K","()I","maxBuildHeight","(Ljava/lang/Object;)I",null);
        forward(writer,bridge,"func_72940_L","()I","maxBuildHeight","(Ljava/lang/Object;)I",null);
        constant(writer, "func_72806_N", "()Z", 0);
        forward(writer,bridge,"func_72879_k","(IIII)I","power","(Ljava/lang/Object;IIII)I",null);
        constant(writer, "func_72849_a", "(IIIZ)I", 15);
        forward(writer,bridge,"func_72864_z","(III)Z","powered","(Ljava/lang/Object;III)Z",null);
        forward(writer,bridge,"func_72899_e","(III)Z","loaded","(Ljava/lang/Object;III)Z",null);
        forward(writer,bridge,"func_147452_c","(IIILnet/minecraft/block/Block;II)V","blockEvent","(Ljava/lang/Object;IIILjava/lang/Object;II)V",null);
        forward(writer,bridge,"func_147464_a","(IIILnet/minecraft/block/Block;I)V","schedule","(Ljava/lang/Object;IIILjava/lang/Object;I)V",null);
        forward(writer,bridge,"func_147453_f","(IIILnet/minecraft/block/Block;)V","comparators","(Ljava/lang/Object;IIILjava/lang/Object;)V",null);
        forward(writer,bridge,"func_72904_c","(IIIIII)Z","regionLoaded","(Ljava/lang/Object;IIIIII)Z",null);
        forward(writer,bridge,"func_72908_a","(DDDLjava/lang/String;FF)V","sound","(Ljava/lang/Object;DDDLjava/lang/String;FF)V",null);
        forward(writer,bridge,"func_72889_a","(Lnet/minecraft/entity/player/EntityPlayer;IIIII)V","auxiliaryEffect","(Ljava/lang/Object;Ljava/lang/Object;IIIII)V",null);
        forward(writer,bridge,"func_72869_a","(Ljava/lang/String;DDDDDD)V","particle","(Ljava/lang/Object;Ljava/lang/String;DDDDDD)V",null);
        forward(writer,bridge,"func_72980_b","(DDDLjava/lang/String;FFZ)V","localSound","(Ljava/lang/Object;DDDLjava/lang/String;FFZ)V",null);
        for(String method:List.of("func_72956_a","func_85173_a")) {
            var sound=writer.visitMethod(Opcodes.ACC_PUBLIC,method,"("+(method.equals("func_72956_a")?"Lnet/minecraft/entity/Entity;":"Lnet/minecraft/entity/player/EntityPlayer;")+"Ljava/lang/String;FF)V",null,null);
            sound.visitCode();sound.visitVarInsn(Opcodes.ALOAD,0);sound.visitVarInsn(Opcodes.ALOAD,1);sound.visitVarInsn(Opcodes.ALOAD,2);sound.visitVarInsn(Opcodes.FLOAD,3);sound.visitVarInsn(Opcodes.FLOAD,4);
            sound.visitInsn(method.equals("func_85173_a")?Opcodes.ICONST_1:Opcodes.ICONST_0);
            sound.visitMethodInsn(Opcodes.INVOKESTATIC,bridge,"entitySound","(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/String;FFZ)V",false);sound.visitInsn(Opcodes.RETURN);sound.visitMaxs(6,5);sound.visitEnd();
        }
        forward(writer,bridge,"func_72872_a","(Ljava/lang/Class;Lnet/minecraft/util/AxisAlignedBB;)Ljava/util/List;","entities","(Ljava/lang/Object;Ljava/lang/Class;Ljava/lang/Object;)Ljava/util/List;",null);
        forward(writer,bridge,"func_82733_a","(Ljava/lang/Class;Lnet/minecraft/util/AxisAlignedBB;Lnet/minecraft/command/IEntitySelector;)Ljava/util/List;","selectedEntities","(Ljava/lang/Object;Ljava/lang/Class;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/List;",null);
        forward(writer,bridge,"func_72839_b","(Lnet/minecraft/entity/Entity;Lnet/minecraft/util/AxisAlignedBB;)Ljava/util/List;","excludingEntity","(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/List;",null);
        forward(writer,bridge,"func_72957_l","(III)I","light","(Ljava/lang/Object;III)I",null);
        forward(writer,bridge,"func_72976_f","(II)I","height","(Ljava/lang/Object;II)I",null);
        forward(writer,bridge,"func_72937_j","(III)Z","canSeeSky","(Ljava/lang/Object;III)Z",null);
        forward(writer,bridge,"func_72977_a","(DDDD)Lnet/minecraft/entity/player/EntityPlayer;","closestPlayer","(Ljava/lang/Object;DDDD)Ljava/lang/Object;","net/minecraft/entity/player/EntityPlayer");
        forward(writer,bridge,"func_72890_a","(Lnet/minecraft/entity/Entity;D)Lnet/minecraft/entity/player/EntityPlayer;","closestPlayerTo","(Ljava/lang/Object;Ljava/lang/Object;D)Ljava/lang/Object;","net/minecraft/entity/player/EntityPlayer");
        for (String method : List.of("func_147459_d", "func_147444_c")) forward(writer,bridge,method,"(IIILnet/minecraft/block/Block;)V","neighbors","(Ljava/lang/Object;IIILjava/lang/Object;)V",null);
        // Light is recomputed by the host from the block's light value; old relight requests have nothing to do.
        constant(writer, "func_147463_c", "(Lnet/minecraft/world/EnumSkyBlock;III)Z", 1);
        forward(writer,bridge,"func_72885_a","(Lnet/minecraft/entity/Entity;DDDFZZ)Lnet/minecraft/world/Explosion;","explode","(Ljava/lang/Object;Ljava/lang/Object;DDDFZZ)Ljava/lang/Object;","net/minecraft/world/Explosion");
        forward(writer,bridge,"func_72960_a","(Lnet/minecraft/entity/Entity;B)V","entityState","(Ljava/lang/Object;Ljava/lang/Object;B)V",null);
        forward(writer, bridge, "func_72838_d", "(Lnet/minecraft/entity/Entity;)Z", "spawn", "(Ljava/lang/Object;Ljava/lang/Object;)Z", null);
        forward(writer,bridge,"func_72964_e","(II)Lnet/minecraft/world/chunk/Chunk;","chunk","(Ljava/lang/Object;II)Ljava/lang/Object;","net/minecraft/world/chunk/Chunk");
        writer.visitEnd(); byte[] bytes = writer.toByteArray();
        return (Class<?>) loader.getClass().getMethod("defineLegacyClass", String.class, byte[].class, int.class, int.class).invoke(loader, name.replace('/', '.'), bytes, 0, bytes.length);
    }
    private static void constant(ClassWriter writer, String name, String descriptor, int value) {
        var mv = writer.visitMethod(Opcodes.ACC_PUBLIC, name, descriptor, null, null); mv.visitCode();
        mv.visitLdcInsn(value); mv.visitInsn(Opcodes.IRETURN); mv.visitMaxs(1, 6); mv.visitEnd();
    }
    private static void forward(ClassWriter writer, String bridge, String method, String descriptor, String callback, String targetDescriptor, String cast) {
        var mv = writer.visitMethod(Opcodes.ACC_PUBLIC, method, descriptor, null, null); mv.visitCode(); mv.visitVarInsn(Opcodes.ALOAD, 0);
        int slot = 1; for (Type type : Type.getArgumentTypes(descriptor)) { mv.visitVarInsn(type.getOpcode(Opcodes.ILOAD), slot); slot += type.getSize(); }
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, bridge, callback, targetDescriptor, false);
        if (cast != null) mv.visitTypeInsn(Opcodes.CHECKCAST, cast);
        mv.visitInsn(Type.getReturnType(descriptor).getOpcode(Opcodes.IRETURN)); mv.visitMaxs(8, slot); mv.visitEnd();
    }
}
