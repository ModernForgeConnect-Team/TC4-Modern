package github.com.gengyoubo.mfcl.bridge;

import org.objectweb.asm.*;
import java.lang.reflect.*;
import java.util.*;

/** Typed old container mirrors. Inventory writes remain owned by the host container. */
public final class LegacyContainers {
    public record Slot(int x, int y, LegacyClientData.Stack stack) {}
    public interface InputHooks {
        boolean mouse(Object screen, int x, int y, int button) throws Exception;
        boolean key(Object screen, char character, int key) throws Exception;
        boolean release(Object screen, int x, int y, int button) throws Exception;
        boolean scroll(Object screen, int x, int y, int delta) throws Exception;
    }
    private final LegacyServices services;
    private final ClassLoader loader;
    private Class<?> containerClass, guiClass;
    private final List<InputHooks> hooks = new ArrayList<>();
    public LegacyContainers(LegacyServices services) { this.services=services; loader=services.loader(); }
    public void addInputHooks(InputHooks hook) { hooks.add(Objects.requireNonNull(hook)); }
    public boolean hasInputHooks() { return !hooks.isEmpty(); }
    public void carried(LegacyClientData.Stack stack) throws Exception {
        Object client=loader.loadClass("net.minecraft.client.Minecraft").getMethod("func_71410_x").invoke(null);
        Object player=client.getClass().getField("field_71439_g").get(client);
        Object inventory=loader.loadClass("net.minecraft.entity.player.EntityPlayer").getField("field_71071_by").get(player);
        inventory.getClass().getMethod("func_70437_b",loader.loadClass("net.minecraft.item.ItemStack")).invoke(inventory,stack==null?null:services.items().materialize(stack));
    }
    public boolean sameLayout(Object gui,List<Slot> values) throws Exception {
        Object container=loader.loadClass("net.minecraft.client.gui.inventory.GuiContainer").getField("field_147002_h").get(gui);
        List<?> slots=(List<?>)container.getClass().getField("field_75151_b").get(container);
        if(slots.size()!=values.size())return false;
        for(int i=0;i<slots.size();i++) {
            Object slot=slots.get(i);
            if(slot.getClass().getField("field_75223_e").getInt(slot)!=values.get(i).x()
                    ||slot.getClass().getField("field_75221_f").getInt(slot)!=values.get(i).y())return false;
        }
        return true;
    }
    public boolean mouse(Object screen,int x,int y,int button) throws Exception { for(var hook:hooks)if(hook.mouse(screen,x,y,button))return true; return false; }
    public boolean key(Object screen,char character,int key) throws Exception { for(var hook:hooks)if(hook.key(screen,character,key))return true; return false; }
    public boolean release(Object screen,int x,int y,int button) throws Exception { for(var hook:hooks)if(hook.release(screen,x,y,button))return true;return false; }
    public boolean scroll(Object screen,int x,int y,int delta) throws Exception { for(var hook:hooks)if(hook.scroll(screen,x,y,delta))return true;return false; }
    public Object create(int width,int height,int left,int top,int imageWidth,int imageHeight,List<Slot> slots) throws Exception {
        preparePlayer();
        Class<?> mc=loader.loadClass("net.minecraft.client.Minecraft");Object client=mc.getMethod("func_71410_x").invoke(null);
        mc.getField("field_71443_c").setInt(client,width);mc.getField("field_71440_d").setInt(client,height);
        Object settings=mc.getField("field_71474_y").get(client);settings.getClass().getField("field_74335_Z").setInt(settings,1);
        if(containerClass==null) defineTypes();
        Object container=containerClass.getConstructor().newInstance();
        Class<?> inventoryType=loader.loadClass("net.minecraft.inventory.IInventory");
        Object inventory=loader.loadClass("net.minecraft.inventory.InventoryBasic").getConstructor(String.class,boolean.class,int.class).newInstance("MFCL",true,slots.size());
        Method add=loader.loadClass("net.minecraft.inventory.Container").getDeclaredMethod("func_75146_a",loader.loadClass("net.minecraft.inventory.Slot")); add.setAccessible(true);
        for(int i=0;i<slots.size();i++) {
            Slot slot=slots.get(i);
            Object oldSlot=loader.loadClass("net.minecraft.inventory.Slot").getConstructor(inventoryType,int.class,int.class,int.class).newInstance(inventory,i,slot.x,slot.y);
            add.invoke(container,oldSlot);
            if(slot.stack!=null) inventoryType.getMethod("func_70299_a",int.class,loader.loadClass("net.minecraft.item.ItemStack")).invoke(inventory,i,services.items().materialize(slot.stack));
        }
        Object gui=guiClass.getConstructor(loader.loadClass("net.minecraft.inventory.Container")).newInstance(container);
        setGui(gui,"field_146999_f",imageWidth);setGui(gui,"field_147000_g",imageHeight);
        return gui;
    }
    public void update(Object gui,List<Slot> values) throws Exception {
        var type=loader.loadClass("net.minecraft.client.gui.inventory.GuiContainer");Object container=type.getField("field_147002_h").get(gui);
        List<?> slots=(List<?>)container.getClass().getField("field_75151_b").get(container);
        if(slots.size()!=values.size())throw new IllegalStateException("Container slot count changed");
        // Like the old client's set-slot packets: only changed slots, through the container's own putStackInSlot hook.
        var stackType=loader.loadClass("net.minecraft.item.ItemStack");
        Method put=loader.loadClass("net.minecraft.inventory.Container").getMethod("func_75141_a",int.class,stackType);
        Method read=loader.loadClass("net.minecraft.inventory.Slot").getMethod("func_75211_c"),equal=stackType.getMethod("func_77989_b",stackType,stackType);
        for(int i=0;i<slots.size();i++) {
            Object next=values.get(i).stack==null?null:services.items().materialize(values.get(i).stack);
            if(!(Boolean)equal.invoke(null,read.invoke(slots.get(i)),next))put.invoke(container,i,next);
        }
    }
    public List<int[]> positions(Object gui) throws Exception {
        Object container=loader.loadClass("net.minecraft.client.gui.inventory.GuiContainer").getField("field_147002_h").get(gui);
        var slotType=loader.loadClass("net.minecraft.inventory.Slot");Field x=slotType.getField("field_75223_e"),y=slotType.getField("field_75221_f");
        var result=new ArrayList<int[]>();
        for(Object slot:(List<?>)container.getClass().getField("field_75151_b").get(container))result.add(new int[]{x.getInt(slot),y.getInt(slot)});
        return result;
    }
    public void position(Object gui,int left,int top) throws Exception { setGui(gui,"field_147003_i",left);setGui(gui,"field_147009_r",top); }
    private void setGui(Object target,String name,int value) throws Exception { Field field=loader.loadClass("net.minecraft.client.gui.inventory.GuiContainer").getDeclaredField(name);field.setAccessible(true);field.setInt(target,value); }
    public void preparePlayer() throws Exception {
        Class<?> mc=loader.loadClass("net.minecraft.client.Minecraft");Object client=mc.getMethod("func_71410_x").invoke(null);
        Object player=mc.getField("field_71439_g").get(client);
        if(player==null) {
            player=allocate(loader.loadClass("net.minecraft.client.entity.EntityClientPlayerMP"));
            var entityType=loader.loadClass("net.minecraft.entity.Entity");
            var uuid=entityType.getDeclaredField("field_96093_i");uuid.setAccessible(true);uuid.set(player,new java.util.UUID(0,0));
            var random=entityType.getDeclaredField("field_70146_Z");random.setAccessible(true);random.set(player,new java.util.Random());
            entityType.getField("capturedDrops").set(player,new java.util.ArrayList<>());
            var type=loader.loadClass("net.minecraft.entity.player.EntityPlayer");
            var profile=type.getDeclaredField("field_146106_i");profile.setAccessible(true);profile.set(player,
                    loader.loadClass("com.mojang.authlib.GameProfile").getConstructor(java.util.UUID.class,String.class).newInstance(new java.util.UUID(0,0),"MFCL"));
            type.getField("field_71071_by").set(player,loader.loadClass("net.minecraft.entity.player.InventoryPlayer").getConstructor(type).newInstance(player));
            type.getField("field_71075_bZ").set(player,loader.loadClass("net.minecraft.entity.player.PlayerCapabilities").getConstructor().newInstance());
            Object watcher=loader.loadClass("net.minecraft.entity.DataWatcher").getConstructor(loader.loadClass("net.minecraft.entity.Entity")).newInstance(player);
            var field=loader.loadClass("net.minecraft.entity.Entity").getDeclaredField("field_70180_af");field.setAccessible(true);field.set(player,watcher);
            watcher.getClass().getMethod("func_75682_a",int.class,Object.class).invoke(watcher,6,20f);
              watcher.getClass().getMethod("func_75682_a",int.class,Object.class).invoke(watcher,0,(byte)0);
            mc.getField("field_71439_g").set(client,player);
            mc.getField("field_71442_b").set(client,loader.loadClass("net.minecraft.client.multiplayer.PlayerControllerMP").getConstructor(mc,loader.loadClass("net.minecraft.client.network.NetHandlerPlayClient")).newInstance(client,null));
        }
        // EntityLivingBase fields that old item tooltips and wand logic read must be present.
        var potions=loader.loadClass("net.minecraft.entity.EntityLivingBase").getDeclaredField("field_70713_bf");potions.setAccessible(true);
        if(potions.get(player)==null)potions.set(player,new java.util.HashMap<Object,Object>());
        // Allocated without a constructor, the player has no bounding box. Old renderers read it anyway: TC4's wand
        // model asks Entity.getBrightnessForRender, which reads boundingBox.maxY, even while MFCL captures item models.
        var entityType=loader.loadClass("net.minecraft.entity.Entity");
        var bounds=entityType.getDeclaredField("field_70121_D");bounds.setAccessible(true);
        if(bounds.get(player)==null) {
            double x=entityType.getField("field_70165_t").getDouble(player),y=entityType.getField("field_70163_u").getDouble(player),z=entityType.getField("field_70161_v").getDouble(player);
            bounds.set(player,loader.loadClass("net.minecraft.util.AxisAlignedBB").getMethod("func_72330_a",double.class,double.class,double.class,double.class,double.class,double.class)
                    .invoke(null,x-0.3,y,z-0.3,x+0.3,y+1.8,z+0.3));
            entityType.getField("field_70130_N").setFloat(player,0.6f);entityType.getField("field_70131_O").setFloat(player,1.8f);
        }
        LegacyEntityAccess.forgeConstruct(loader,player,loader.loadClass("net.minecraft.entity.Entity").getField("field_70170_p").get(player));
        LegacyPlayerArmor.apply(services,player,null,true);
    }
    private Object allocate(Class<?> type) throws Exception { var field=Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");field.setAccessible(true);Object unsafe=field.get(null);return unsafe.getClass().getMethod("allocateInstance",Class.class).invoke(unsafe,type); }
    private void defineTypes() throws Exception {
        String container="github/com/gengyoubo/mfcl/legacy1710/HostContainer",gui="github/com/gengyoubo/mfcl/legacy1710/HostGuiContainer";
        ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);writer.visit(Opcodes.V1_8,Opcodes.ACC_PUBLIC,container,null,"net/minecraft/inventory/Container",null);
        var mv=writer.visitMethod(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);mv.visitCode();mv.visitVarInsn(Opcodes.ALOAD,0);mv.visitMethodInsn(Opcodes.INVOKESPECIAL,"net/minecraft/inventory/Container","<init>","()V",false);mv.visitInsn(Opcodes.RETURN);mv.visitMaxs(1,1);mv.visitEnd();
        mv=writer.visitMethod(Opcodes.ACC_PUBLIC,"func_75145_c","(Lnet/minecraft/entity/player/EntityPlayer;)Z",null,null);mv.visitCode();mv.visitInsn(Opcodes.ICONST_1);mv.visitInsn(Opcodes.IRETURN);mv.visitMaxs(1,2);mv.visitEnd();writer.visitEnd();containerClass=define(container,writer.toByteArray());
        writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);writer.visit(Opcodes.V1_8,Opcodes.ACC_PUBLIC,gui,null,"net/minecraft/client/gui/inventory/GuiContainer",null);
        mv=writer.visitMethod(Opcodes.ACC_PUBLIC,"<init>","(Lnet/minecraft/inventory/Container;)V",null,null);mv.visitCode();mv.visitVarInsn(Opcodes.ALOAD,0);mv.visitVarInsn(Opcodes.ALOAD,1);mv.visitMethodInsn(Opcodes.INVOKESPECIAL,"net/minecraft/client/gui/inventory/GuiContainer","<init>","(Lnet/minecraft/inventory/Container;)V",false);mv.visitInsn(Opcodes.RETURN);mv.visitMaxs(2,2);mv.visitEnd();
        for(String[] method:List.of(new String[]{"func_146976_a","(FII)V"},new String[]{"func_146979_b","(II)V"},new String[]{"func_146276_q_","()V"},new String[]{"func_146281_b","()V"})) {
            mv=writer.visitMethod(Opcodes.ACC_PUBLIC,method[0],method[1],null,null);mv.visitCode();mv.visitInsn(Opcodes.RETURN);mv.visitMaxs(0,4);mv.visitEnd();
        }
        writer.visitEnd();guiClass=define(gui,writer.toByteArray());
    }
    private Class<?> define(String name,byte[] bytes) throws Exception { return (Class<?>)loader.getClass().getMethod("defineLegacyClass",String.class,byte[].class,int.class,int.class).invoke(loader,name.replace('/','.'),bytes,0,bytes.length); }
}
