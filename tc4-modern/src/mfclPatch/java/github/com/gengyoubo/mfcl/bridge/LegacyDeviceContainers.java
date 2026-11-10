package github.com.gengyoubo.mfcl.bridge;

import java.util.*;

/** Captures original FML/vanilla container requests without entering the old network stack. */
public final class LegacyDeviceContainers {
    public record Descriptor(String mod,int id,int x,int y,int z,boolean inventory,boolean item,int itemSlot) {
        public Descriptor(String mod,int id,int x,int y,int z,boolean inventory){this(mod,id,x,y,z,inventory,false);}
        public Descriptor(String mod,int id,int x,int y,int z,boolean inventory,boolean item){this(mod,id,x,y,z,inventory,item,-1);}
    }
    public record Open(Descriptor descriptor,Object container,Object player) {}
    private static final ThreadLocal<Capture> CURRENT=new ThreadLocal<>();
    public interface GuiProvider {
        Object container(Object player,Object source) throws Exception;
        Object screen(Object player,Object source) throws Exception;
    }
    private static final Map<ClassLoader,Map<String,GuiProvider>> PROVIDERS=new WeakHashMap<>();
    private static final Map<ClassLoader,Set<String>> PREDICTED=new WeakHashMap<>();
    @FunctionalInterface public interface PlayerEffectsListener {void changed(Object container,Object player)throws Exception;}
    private static final Map<ClassLoader,Map<String,PlayerEffectsListener>> EFFECT_LISTENERS=new WeakHashMap<>();
    /** Original containers whose cached results depend on the viewer's current status effects. */
    public static synchronized void registerPlayerEffectsListener(ClassLoader loader,String type,PlayerEffectsListener listener){
        EFFECT_LISTENERS.computeIfAbsent(loader,ignored->new HashMap<>()).put(type,listener);
    }
    public static void playerEffectsChanged(Open opened)throws Exception {
        PlayerEffectsListener listener;
        synchronized(LegacyDeviceContainers.class){listener=EFFECT_LISTENERS.getOrDefault(opened.container().getClass().getClassLoader(),Map.of()).get(opened.container().getClass().getName());}
        if(listener!=null)listener.changed(opened.container(),opened.player());
    }
    /** Opt in only containers whose ordinary client slot-click logic is safe to preview and reconcile. */
    public static synchronized void registerPredictedContainer(ClassLoader loader,String type) {
        PREDICTED.computeIfAbsent(loader,ignored->new HashSet<>()).add(type);
    }
    public static synchronized boolean predicts(Object container) {
        return PREDICTED.getOrDefault(container.getClass().getClassLoader(),Set.of()).contains(container.getClass().getName());
    }
    public static synchronized void registerProvider(ClassLoader loader,String id,GuiProvider provider) {
        PROVIDERS.computeIfAbsent(loader,ignored->new HashMap<>()).put(id,provider);
    }
    private static synchronized GuiProvider provider(ClassLoader loader,String id) {
        var providers=PROVIDERS.get(loader);
        if(providers==null||!providers.containsKey(id))throw new IllegalArgumentException("Unknown original GUI provider: "+id);
        return providers.get(id);
    }
    /** Optional adapters use this for positioned devices with their own GUI protocol instead of FML openGui. */
    public static boolean openProvider(String id,Object player,Object source) throws Exception {
        var capture=CURRENT.get();if(capture==null)return false;
        var loader=capture.services.loader();var tile=loader.loadClass("net.minecraft.tileentity.TileEntity");
        if(!tile.isInstance(source))return false;
        Object container=provider(loader,id).container(player,source);
        if(container!=null)capture.opened=new Open(new Descriptor("mfcl:provider/"+id,0,
                tile.getField("field_145851_c").getInt(source),tile.getField("field_145848_d").getInt(source),
                tile.getField("field_145849_e").getInt(source),false),container,player);
        return true;
    }
    @FunctionalInterface public interface DropSink {void accept(Object entity) throws Exception;}
    private static final ThreadLocal<DropSink> DROPS=new ThreadLocal<>();
    private static final ThreadLocal<List<Object>> PENDING_DROPS=new ThreadLocal<>();
    static List<Object> pendingDrops(){var drops=PENDING_DROPS.get();return drops==null?List.of():List.copyOf(drops);}
    public static <T> T withDrops(DropSink sink,java.util.concurrent.Callable<T> action) throws Exception {
        DropSink previous=DROPS.get();var pending=PENDING_DROPS.get();DROPS.set(sink);PENDING_DROPS.set(new ArrayList<>());
        try{return action.call();}finally{if(previous==null)DROPS.remove();else DROPS.set(previous);if(pending==null)PENDING_DROPS.remove();else PENDING_DROPS.set(pending);}
    }
    public static boolean drop(Object entity) throws Exception {var sink=DROPS.get();if(sink==null)return false;PENDING_DROPS.get().add(entity);sink.accept(entity);return true;}
    public static final class Capture implements AutoCloseable {
        final LegacyServices services;final boolean item;Open opened;
        private Capture(LegacyServices services,boolean item){this.services=services;this.item=item;}
        public Open opened(){return opened;}
        @Override public void close(){CURRENT.remove();}
    }
    private LegacyDeviceContainers() {}
    public static Capture capture(LegacyServices services) {
        return capture(services,false);
    }
    public static Capture captureItem(LegacyServices services) {
        return capture(services,true);
    }
    private static Capture capture(LegacyServices services,boolean item) {
        if(CURRENT.get()!=null)throw new IllegalStateException("Nested device activation");
        var capture=new Capture(services,item);CURRENT.set(capture);return capture;
    }
    private static Object registry(ClassLoader loader) throws Exception {
        return loader.loadClass("cpw.mods.fml.common.network.NetworkRegistry").getField("INSTANCE").get(null);
    }
    public static boolean openGui(Object player,Object mod,int id,Object world,int x,int y,int z) throws Exception {
        var capture=CURRENT.get();if(capture==null)return false;
        var loader=capture.services.loader();
        boolean client=loader.loadClass("net.minecraft.world.World").getField("field_72995_K").getBoolean(world);
        Object common=loader.loadClass("cpw.mods.fml.common.FMLCommonHandler").getMethod("instance").invoke(null);
        Object owner=common.getClass().getMethod("findContainerFor",Object.class).invoke(common,mod);
        if(owner==null)throw new IllegalArgumentException("No original mod owns the GUI request");
        var ownerType=loader.loadClass("cpw.mods.fml.common.ModContainer");
        if(client) {
            Object screen=registry(loader).getClass().getMethod("getLocalGuiContainer",ownerType,loader.loadClass("net.minecraft.entity.player.EntityPlayer"),int.class,loader.loadClass("net.minecraft.world.World"),int.class,int.class,int.class)
                    .invoke(registry(loader),owner,player,id,world,x,y,z);
            // Portable containers open through the authoritative server menu. Client-only books have no container.
            if(screen!=null&&!loader.loadClass("net.minecraft.client.gui.inventory.GuiContainer").isInstance(screen))LegacyGuiGraphics.showScreen(screen);
            return true;
        }
        Object container=registry(loader).getClass().getMethod("getRemoteGuiContainer",ownerType,loader.loadClass("net.minecraft.entity.player.EntityPlayerMP"),int.class,loader.loadClass("net.minecraft.world.World"),int.class,int.class,int.class)
                .invoke(registry(loader),owner,player,id,world,x,y,z);
        Object inventory=loader.loadClass("net.minecraft.entity.player.EntityPlayer").getField("field_71071_by").get(player);
        int itemSlot=capture.item?inventory.getClass().getField("field_70461_c").getInt(inventory):-1;
        if(container!=null)capture.opened=new Open(new Descriptor((String)ownerType.getMethod("getModId").invoke(owner),id,x,y,z,false,capture.item,itemSlot),container,player);
        return true;
    }
    public static boolean openInventory(Object player,Object inventory) throws Exception {
        var capture=CURRENT.get();if(capture==null)return false;
        var loader=capture.services.loader();var inv=loader.loadClass("net.minecraft.inventory.IInventory");
        Object playerInventory=loader.loadClass("net.minecraft.entity.player.EntityPlayer").getField("field_71071_by").get(player);
        Object container=loader.loadClass("net.minecraft.inventory.ContainerChest").getConstructor(inv,inv).newInstance(playerInventory,inventory);
        var tile=loader.loadClass("net.minecraft.tileentity.TileEntity");
        if(!tile.isInstance(inventory))throw new UnsupportedOperationException("Inventory GUI needs a positioned original tile");
        capture.opened=new Open(new Descriptor("",0,tile.getField("field_145851_c").getInt(inventory),tile.getField("field_145848_d").getInt(inventory),tile.getField("field_145849_e").getInt(inventory),true),container,player);
        return true;
    }
    public static Object clientGui(LegacyServices services,Descriptor descriptor,Object player,Object world) throws Exception {
        var loader=services.loader();
        if(descriptor.mod().startsWith("mfcl:provider/")) {
            Object source=loader.loadClass("net.minecraft.world.World").getMethod("func_147438_o",int.class,int.class,int.class)
                    .invoke(world,descriptor.x(),descriptor.y(),descriptor.z());
            if(source==null)throw new IllegalStateException("Original GUI tile is missing");
            return provider(loader,descriptor.mod().substring("mfcl:provider/".length())).screen(player,source);
        }
        if(descriptor.inventory()) {
            Object inventory=loader.loadClass("net.minecraft.world.World").getMethod("func_147438_o",int.class,int.class,int.class).invoke(world,descriptor.x(),descriptor.y(),descriptor.z());
            Object playerInventory=loader.loadClass("net.minecraft.entity.player.EntityPlayer").getField("field_71071_by").get(player);
            var inv=loader.loadClass("net.minecraft.inventory.IInventory");
            return loader.loadClass("net.minecraft.client.gui.inventory.GuiChest").getConstructor(inv,inv).newInstance(playerInventory,inventory);
        }
        Object fml=loader.loadClass("cpw.mods.fml.common.Loader").getMethod("instance").invoke(null);
        Object owner=((Map<?,?>)fml.getClass().getMethod("getIndexedModList").invoke(fml)).get(descriptor.mod());
        return registry(loader).getClass().getMethod("getLocalGuiContainer",loader.loadClass("cpw.mods.fml.common.ModContainer"),loader.loadClass("net.minecraft.entity.player.EntityPlayer"),int.class,loader.loadClass("net.minecraft.world.World"),int.class,int.class,int.class)
                .invoke(registry(loader),owner,player,descriptor.id(),world,descriptor.x(),descriptor.y(),descriptor.z());
    }
    public static List<?> slots(Object container) throws Exception {return (List<?>)container.getClass().getField("field_75151_b").get(container);}
    /** Original machine containers publish their live fields to registered ICrafting viewers. */
    public static void attachViewer(Open opened,int windowId) throws Exception {
        var loader=opened.player().getClass().getClassLoader();
        var container=loader.loadClass("net.minecraft.inventory.Container");
        container.getField("field_75152_c").setInt(opened.container(),windowId);
        loader.loadClass("net.minecraft.entity.player.EntityPlayer").getField("field_71070_bA").set(opened.player(),opened.container());
        container.getMethod("func_75132_a",loader.loadClass("net.minecraft.inventory.ICrafting")).invoke(opened.container(),opened.player());
    }
}
