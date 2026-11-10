package github.com.gengyoubo.mfcl.bridge;

import java.lang.reflect.*;

/** Original gameplay callbacks. Scheduling, authority and rollback belong to the host level. */
public final class LegacyBlockLogic {
    private static final ThreadLocal<java.util.List<String>> MESSAGES = new ThreadLocal<>();
    public static final class ChatCapture implements AutoCloseable {
        private final java.util.List<String> previous=MESSAGES.get(), messages=new java.util.ArrayList<>();
        public ChatCapture(){MESSAGES.set(messages);}
        public java.util.List<String> messages(){return java.util.List.copyOf(messages);}
        @Override public void close(){if(previous==null)MESSAGES.remove();else MESSAGES.set(previous);}
    }
    public record Activation(boolean handled, java.util.List<LegacyClientData.Stack> inventory, java.util.List<String> messages, LegacyDeviceContainers.Open device) {}
    /** Chat line addressed to one old ICommandSender (a player actor, or the command console stand-in). */
    public record AddressedChat(Object recipient, String text) {}
    private static final ThreadLocal<java.util.List<AddressedChat>> ADDRESSED = new ThreadLocal<>();
    /** Like {@link ChatCapture}, but keeps who each line was sent to; used where several old players are live at once. */
    public static final class AddressedChatCapture implements AutoCloseable {
        private final java.util.List<AddressedChat> previous=ADDRESSED.get(), lines=new java.util.ArrayList<>();
        public AddressedChatCapture(){ADDRESSED.set(lines);}
        public java.util.List<AddressedChat> lines(){return java.util.List.copyOf(lines);}
        @Override public void close(){if(previous==null)ADDRESSED.remove();else ADDRESSED.set(previous);}
    }
    public static boolean chat(Object recipient, Object component) throws Exception {
        var lines = ADDRESSED.get(); if (lines == null) return chat(component);
        var type = component.getClass().getClassLoader().loadClass("net.minecraft.util.IChatComponent");
        lines.add(new AddressedChat(recipient,(String)type.getMethod("func_150260_c").invoke(component))); return true;
    }
    public static boolean chat(Object component) throws Exception {
        var messages = MESSAGES.get(); if (messages == null) return false;
        var type = component.getClass().getClassLoader().loadClass("net.minecraft.util.IChatComponent");
        messages.add((String)type.getMethod("func_150260_c").invoke(component)); return true;
    }
    private final LegacyServices services;
    private final Class<?> blockType, worldType, tileType;
    private final Object registry;
    @FunctionalInterface public interface TickPolicy { boolean scheduled(Object tile) throws Exception; }
    private final java.util.List<TickPolicy> tickPolicies=new java.util.concurrent.CopyOnWriteArrayList<>();
    /** Original mods can maintain their own tick list despite canUpdate=false. */
    public void tickPolicy(TickPolicy policy) { tickPolicies.add(java.util.Objects.requireNonNull(policy)); }
    private boolean scheduled(Object tile) throws Exception {
        if((boolean)tileType.getMethod("canUpdate").invoke(tile))return true;
        for(var policy:tickPolicies)if(policy.scheduled(tile))return true;
        return false;
    }
    public LegacyBlockLogic(LegacyServices services) throws Exception {
        this.services = services; var loader = services.loader();
        blockType = loader.loadClass("net.minecraft.block.Block"); worldType = loader.loadClass("net.minecraft.world.World");
        tileType = loader.loadClass("net.minecraft.tileentity.TileEntity"); registry = blockType.getField("field_149771_c").get(null);
    }
    private Object block(String key) throws Exception { return registry.getClass().getMethod("func_82594_a",String.class).invoke(registry,key); }
    public boolean tick(Object tile) throws Exception {
        return services.items().inLegacy(() -> {
            if (tile == null || (boolean)tileType.getMethod("func_145837_r").invoke(tile) || !scheduled(tile)) return false;
            Object world=tileType.getMethod("func_145831_w").invoke(tile);LegacyWorlds.beginEntityQueries(world);
            try {tileType.getMethod("func_145845_h").invoke(tile);LegacyWorlds.commitEntityMotion(world);return true;}
            finally {LegacyWorlds.clearEntityQueries(world);}
        });
    }
    /** Original removal flag, plus the possibly shrunk stack for a dropped item (null for other entities). */
    public record Collision(boolean removed, LegacyClientData.Stack stack) {}
    private final java.util.Map<String,Boolean> collides = new java.util.concurrent.ConcurrentHashMap<>();
    /** Whether the original block overrides onEntityCollidedWithBlock (crucible, cactus-like hazards...). */
    public boolean collides(String key) throws Exception {
        Boolean known = collides.get(key);
        if (known != null) return known;
        boolean value = services.items().inLegacy(() -> block(key).getClass().getMethod("func_149670_a", worldType, int.class, int.class, int.class,
                services.loader().loadClass("net.minecraft.entity.Entity")).getDeclaringClass() != blockType);
        collides.put(key, value); return value;
    }
    /** Runs original collision logic against a host entity mirror; damage/effects use the authoritative host hooks. */
    public Collision collide(String key, Object world, int x, int y, int z, HostWorldAccess.Entity snapshot) throws Exception {
        return services.items().inLegacy(() -> {try(var state=services.playerState().enter(world)) {
            var loader = services.loader(); Class<?> entityType = loader.loadClass("net.minecraft.entity.Entity");
            Object entity = LegacyEntityAccess.mirror(services.items(), world, snapshot, null);
            blockType.getMethod("func_149670_a", worldType, int.class, int.class, int.class, entityType).invoke(block(key), world, x, y, z, entity);
            var motion=LegacyEntityAccess.motion(loader,entity,snapshot.id());
            if(motion.x()!=snapshot.motionX()||motion.y()!=snapshot.motionY()||motion.z()!=snapshot.motionZ()||motion.fallDistance()!=snapshot.fallDistance()||motion.love()!=snapshot.love())
                LegacyWorlds.hostOrNull(world).applyMotion(motion);
            boolean removed=entityType.getField("field_70128_L").getBoolean(entity);
            if(!loader.loadClass("net.minecraft.entity.item.EntityItem").isInstance(entity))return new Collision(removed,null);
            Object stack = entity.getClass().getMethod("func_92059_d").invoke(entity);
            removed = removed || stack == null
                    || loader.loadClass("net.minecraft.item.ItemStack").getField("field_77994_a").getInt(stack) <= 0;
            return new Collision(removed, removed ? null : services.items().snapshot(stack));
        }});
    }
    /** Original Block.randomDisplayTick on the client world. */
    public void displayTick(String key, Object world, int x, int y, int z, long seed) throws Exception {
        services.items().inLegacy(() -> {
            blockType.getMethod("func_149734_b", worldType, int.class, int.class, int.class, java.util.Random.class).invoke(block(key), world, x, y, z, new java.util.Random(seed));
            return null;
        });
    }
    public boolean event(String key, Object world, int x, int y, int z, int event, int data) throws Exception {
        return services.items().inLegacy(() -> (boolean)blockType.getMethod("func_149696_a",worldType,int.class,int.class,int.class,int.class,int.class)
                .invoke(block(key),world,x,y,z,event,data));
    }
    /** Only old Forge fluids need their placement and neighbour callbacks to seed flow updates. */
    public boolean isForgeFluid(String key) throws Exception {
        return services.items().inLegacy(() -> services.loader().loadClass("net.minecraftforge.fluids.BlockFluidBase").isInstance(block(key)));
    }
    public void fluidAdded(String key,Object world,int x,int y,int z) throws Exception {
        services.items().inLegacy(() -> {blockType.getMethod("func_149726_b",worldType,int.class,int.class,int.class).invoke(block(key),world,x,y,z);return null;});
    }
    public void fluidNeighbourChanged(String key,Object world,int x,int y,int z,int nx,int ny,int nz) throws Exception {
        services.items().inLegacy(() -> {
            Object neighbour=worldType.getMethod("func_147439_a",int.class,int.class,int.class).invoke(world,nx,ny,nz);
            blockType.getMethod("func_149695_a",worldType,int.class,int.class,int.class,blockType).invoke(block(key),world,x,y,z,neighbour);return null;
        });
    }
    public boolean providesPower(String key) throws Exception { return services.items().inLegacy(() -> (boolean)blockType.getMethod("func_149744_f").invoke(block(key))); }
    public boolean hasComparator(String key) throws Exception { return services.items().inLegacy(() -> (boolean)blockType.getMethod("func_149740_M").invoke(block(key))); }
    /** The inherited Block.onBlockActivated always returns false and must not consume a predicted client click. */
    public boolean hasActivation(String key) throws Exception {
        return services.items().inLegacy(() -> block(key).getClass().getMethod("func_149727_a",worldType,int.class,int.class,int.class,
                services.loader().loadClass("net.minecraft.entity.player.EntityPlayer"),int.class,float.class,float.class,float.class).getDeclaringClass()!=blockType);
    }
    /** Old leaf-support notifications, including persistent/decay metadata, without replaying inventory-breaking callbacks. */
    public void leavesRemoved(String key,Object world,int x,int y,int z)throws Exception {
        services.items().inLegacy(()->{
            Class<?> access=services.loader().loadClass("net.minecraft.world.IBlockAccess");
            var support=blockType.getMethod("canSustainLeaves",access,int.class,int.class,int.class);
            var leaves=blockType.getMethod("isLeaves",access,int.class,int.class,int.class);
            Object removed=block(key);int radius=(boolean)support.invoke(removed,world,x,y,z)?4:(boolean)leaves.invoke(removed,world,x,y,z)?1:0;
            if(radius==0)return null;
            var decay=blockType.getMethod("beginLeavesDecay",worldType,int.class,int.class,int.class);
            var lookup=worldType.getMethod("func_147439_a",int.class,int.class,int.class);
            for(int dx=-radius;dx<=radius;dx++)for(int dy=-radius;dy<=radius;dy++)for(int dz=-radius;dz<=radius;dz++){
                int xx=x+dx,yy=y+dy,zz=z+dz;if(!LegacyWorlds.validPosition(world,xx,yy,zz))continue;
                Object neighbour=lookup.invoke(world,xx,yy,zz);
                if((boolean)leaves.invoke(neighbour,world,xx,yy,zz))decay.invoke(neighbour,world,xx,yy,zz);
            }
            return null;
        });
    }
    private final java.util.Map<String,Boolean> harvestCallbacks = new java.util.concurrent.ConcurrentHashMap<>();
    /** Whether the original block overrides onBlockHarvested; the inherited one does nothing. */
    public boolean hasHarvestCallback(String key) throws Exception {
        Boolean known = harvestCallbacks.get(key);
        if (known != null) return known;
        boolean value = services.items().inLegacy(() -> block(key).getClass().getMethod("func_149681_a", worldType, int.class, int.class, int.class, int.class,
                services.loader().loadClass("net.minecraft.entity.player.EntityPlayer")).getDeclaringClass() != blockType);
        harvestCallbacks.put(key, value); return value;
    }
    /**
     * Original onBlockHarvested. Like 1.7.10's ItemInWorldManager, this runs for every game mode while the block and
     * its tile still exist. Blocks such as jars compute their drops here because their getDrops needs that tile.
     */
    public void harvested(String key, Object world, int x, int y, int z, int metadata, Object player) throws Exception {
        services.items().inLegacy(() -> {
            blockType.getMethod("func_149681_a", worldType, int.class, int.class, int.class, int.class, services.loader().loadClass("net.minecraft.entity.player.EntityPlayer"))
                    .invoke(block(key), world, x, y, z, metadata, player);
            return null;
        });
    }
    public int comparator(String key, Object world, int x, int y, int z) throws Exception {
        return services.items().inLegacy(() -> (int)blockType.getMethod("func_149736_g",worldType,int.class,int.class,int.class,int.class).invoke(block(key),world,x,y,z,0));
    }
    public int signal(String key, LegacyBlockRendering.View view, int side, boolean direct) throws Exception {
        return services.items().inLegacy(() -> {
            try (var scene = new LegacyBlockScene(services,view)) {
                return (int)blockType.getMethod(direct ? "func_149748_c" : "func_149709_b",services.loader().loadClass("net.minecraft.world.IBlockAccess"),int.class,int.class,int.class,int.class)
                        .invoke(block(key),scene.world(),0,0,0,side);
            }
        });
    }
    public Activation activate(String key, Object world, int x, int y, int z, LegacyTiles.Placer actor, java.util.List<LegacyClientData.Stack> contents, int selected, int side, float hitX, float hitY, float hitZ) throws Exception {
        return services.items().inLegacy(() -> {
            try(var state=services.playerState().enter(world)) {
            var loader = services.loader(); var playerType = loader.loadClass("net.minecraft.entity.player.EntityPlayer");
            var inventoryType = loader.loadClass("net.minecraft.entity.player.InventoryPlayer"); var stackType = loader.loadClass("net.minecraft.item.ItemStack");
            Object player = services.tiles().actor(world,actor,null), inventory = playerType.getField("field_71071_by").get(player);
            for (int slot = 0; slot < contents.size(); slot++) inventoryType.getMethod("func_70299_a",int.class,stackType).invoke(inventory,slot,contents.get(slot) == null ? null : services.items().materialize(contents.get(slot)));
            inventoryType.getField("field_70461_c").setInt(inventory,selected);
            Object container = loader.loadClass("net.minecraft.inventory.ContainerPlayer").getConstructor(inventoryType,boolean.class,playerType).newInstance(inventory,false,player);
            playerType.getField("field_71069_bz").set(player,container); playerType.getField("field_71070_bA").set(player,container);
            var messages = new java.util.ArrayList<String>(); MESSAGES.set(messages);
            boolean handled;
            LegacyDeviceContainers.Open opened;
            try(var device=LegacyDeviceContainers.capture(services)) {
                handled = (boolean)blockType.getMethod("func_149727_a",worldType,int.class,int.class,int.class,playerType,int.class,float.class,float.class,float.class)
                    .invoke(block(key),world,x,y,z,player,side,hitX,hitY,hitZ);
                opened=device.opened();
            }
            finally { MESSAGES.remove(); }
            var result = new java.util.ArrayList<LegacyClientData.Stack>();
            for (int slot = 0; slot < contents.size(); slot++) {
                Object value = inventoryType.getMethod("func_70301_a",int.class).invoke(inventory,slot);
                result.add(value == null || stackType.getField("field_77994_a").getInt(value) <= 0 ? null : services.items().snapshot(value));
            }
            return new Activation(handled,result,java.util.List.copyOf(messages),opened);
            }
        });
    }
    public void scheduled(String key, Object world, int x, int y, int z) throws Exception {
        services.items().inLegacy(() -> {
            blockType.getMethod("func_149674_a",worldType,int.class,int.class,int.class,java.util.Random.class)
                    .invoke(block(key),world,x,y,z,worldType.getField("field_73012_v").get(world)); return null;
        });
    }
}
