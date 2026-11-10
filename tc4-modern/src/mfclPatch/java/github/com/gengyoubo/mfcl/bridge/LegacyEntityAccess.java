package github.com.gengyoubo.mfcl.bridge;

import org.objectweb.asm.*;
import java.lang.reflect.Method;
import java.util.*;

/**
 * Entity query snapshots. Motion/fall, breeding (inLove) and dropped-item stack or removal changes are committed back to the owning host world;
 * damage, burning and potion effects are forwarded immediately by LegacyEntityHooks.
 */
final class LegacyEntityAccess {
    private static final Map<ClassLoader,Map<String,Class<?>>> FACADES=Collections.synchronizedMap(new WeakHashMap<>());
    private LegacyEntityAccess() {}
    static Object mirror(LegacyClientData items,Object world,HostWorldAccess.Entity snapshot,Object existing) throws Exception {
        ClassLoader loader=items.loader();
        boolean generic=snapshot.legacyClass().equals("net.minecraft.entity.Entity")||snapshot.legacyClass().equals("net.minecraft.entity.EntityLiving");
        Class<?> type=generic?facade(loader,snapshot.legacyClass()):loader.loadClass(snapshot.legacyClass());
        var unsafeField=Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");unsafeField.setAccessible(true);Object unsafe=unsafeField.get(null);
        Object entity=existing==null?unsafe.getClass().getMethod("allocateInstance",Class.class).invoke(unsafe,type):existing;
        var base=loader.loadClass("net.minecraft.entity.Entity");
        base.getMethod("func_145769_d",int.class).invoke(entity,snapshot.entityId());
        base.getField("field_70170_p").set(entity,world);
        if(snapshot.entityData()!=null) {
            Object nbt=loader.loadClass("net.minecraft.nbt.CompressedStreamTools").getMethod("func_74796_a",java.io.InputStream.class)
                    .invoke(null,new java.io.ByteArrayInputStream(snapshot.entityData()));
            var field=base.getDeclaredField("customEntityData");field.setAccessible(true);field.set(entity,nbt);
        }
        base.getField("field_71093_bK").setInt(entity,loader.loadClass("net.minecraft.world.WorldProvider").getField("field_76574_g").getInt(loader.loadClass("net.minecraft.world.World").getField("field_73011_w").get(world)));
        base.getField("field_70165_t").setDouble(entity,snapshot.x());base.getField("field_70163_u").setDouble(entity,snapshot.y());base.getField("field_70161_v").setDouble(entity,snapshot.z());
        base.getField("field_70159_w").setDouble(entity,snapshot.motionX());base.getField("field_70181_x").setDouble(entity,snapshot.motionY());base.getField("field_70179_y").setDouble(entity,snapshot.motionZ());
        base.getField("field_70143_R").setFloat(entity,snapshot.fallDistance());base.getField("field_70130_N").setFloat(entity,snapshot.width());base.getField("field_70131_O").setFloat(entity,snapshot.height());
        if(existing==null) {
            for(var entry:Map.of("field_96093_i",(Object)snapshot.id(),"field_70146_Z",new Random()).entrySet()){var field=base.getDeclaredField(entry.getKey());field.setAccessible(true);field.set(entity,entry.getValue());}
            Object watcher=loader.loadClass("net.minecraft.entity.DataWatcher").getConstructor(base).newInstance(entity);
            var add=watcher.getClass().getMethod("func_75682_a",int.class,Object.class);
            var exists=watcher.getClass().getDeclaredMethod("func_75691_i",int.class);exists.setAccessible(true);
            // Entity mirrors bypass constructors, so reproduce the vanilla DataWatcher bootstrap. The subclass hook adds
            // per-type slots (name, age, profession, ...) that old rendering/scanning code reads directly.
            add.invoke(watcher,0,(byte)(snapshot.sneaking()?2:0));
            add.invoke(watcher,1,(short)300);
            var watched=base.getDeclaredField("field_70180_af");watched.setAccessible(true);watched.set(entity,watcher);
            try { initializeDataWatcher(type,entity); } catch (Throwable ignored) { }
            Class<?> livingBase=loader.loadClass("net.minecraft.entity.EntityLivingBase");
            if(livingBase.isAssignableFrom(type)) {
                // Constructor-made equipment arrays: hit callbacks (EnchantmentHelper thorns/arthropod checks) read them.
                Class<?> stack=loader.loadClass("net.minecraft.item.ItemStack");
                equipment(livingBase,"field_82180_bT",entity,java.lang.reflect.Array.newInstance(stack,5));
                if(loader.loadClass("net.minecraft.entity.EntityLiving").isAssignableFrom(type)) {
                    equipment(loader.loadClass("net.minecraft.entity.EntityLiving"),"field_82182_bS",entity,java.lang.reflect.Array.newInstance(stack,5));
                    equipment(loader.loadClass("net.minecraft.entity.EntityLiving"),"field_82174_bp",entity,new float[5]);
                }
                watch(watcher,add,exists,6,1f);watch(watcher,add,exists,7,0);watch(watcher,add,exists,8,(byte)0);watch(watcher,add,exists,9,(byte)0);
                if(loader.loadClass("net.minecraft.entity.EntityLiving").isAssignableFrom(type)) {watch(watcher,add,exists,10,"");watch(watcher,add,exists,11,(byte)0);}
                if(loader.loadClass("net.minecraft.entity.EntityAgeable").isAssignableFrom(type)) watch(watcher,add,exists,12,0);
                else if(loader.loadClass("net.minecraft.entity.monster.EntityZombie").isAssignableFrom(type)) {watch(watcher,add,exists,12,(byte)0);watch(watcher,add,exists,13,(byte)0);watch(watcher,add,exists,14,(byte)0);}
            }
            Class<?> playerType=loader.loadClass("net.minecraft.entity.player.EntityPlayer");
            if(playerType.isAssignableFrom(type)) {
                watch(watcher,add,exists,16,(byte)0);watch(watcher,add,exists,17,0f);watch(watcher,add,exists,18,0);
                var profile=playerType.getDeclaredField("field_146106_i");profile.setAccessible(true);
                profile.set(entity,loader.loadClass("com.mojang.authlib.GameProfile").getConstructor(UUID.class,String.class).newInstance(snapshot.id(),snapshot.name()));
                LegacyPlayerFood.initialize(entity);
            }
            forgeConstruct(loader,entity,world);
        }
        // Age and breeding state are what old animal logic (lamp of fertility) checks before setInLove.
        Class<?> playerType=loader.loadClass("net.minecraft.entity.player.EntityPlayer");
        if(playerType.isInstance(entity)) {
            Class<?> abilitiesType=loader.loadClass("net.minecraft.entity.player.PlayerCapabilities");
            Object abilities=abilitiesType.getConstructor().newInstance();
            if(snapshot.abilities()!=null) {
                abilitiesType.getField("field_75098_d").setBoolean(abilities,snapshot.abilities().creative());
                abilitiesType.getField("field_75102_a").setBoolean(abilities,snapshot.abilities().invulnerable());
            }
            playerType.getField("field_71075_bZ").set(entity,abilities);
            var clientPlayer=loader.loadClass("net.minecraft.client.entity.EntityPlayerSP");
            if(clientPlayer.isInstance(entity)) {
                // Client isSneaking reads MovementInput rather than Entity's watched flags.
                var inputType=loader.loadClass("net.minecraft.util.MovementInput");Object input=inputType.getConstructor().newInstance();
                inputType.getField("field_78899_d").setBoolean(input,snapshot.sneaking());
                clientPlayer.getField("field_71158_b").set(entity,input);
            }
        }
        if(snapshot.health()!=null&&loader.loadClass("net.minecraft.entity.EntityLivingBase").isInstance(entity)) {
            // Hazard callbacks choose lethal/nonlethal branches from getHealth. Do not leave the constructor
            // substitute of 1 HP, and avoid setHealth's access to attributes absent from an Unsafe mirror.
            Object watcher=base.getMethod("func_70096_w").invoke(entity);
            watcher.getClass().getMethod("func_75692_b",int.class,Object.class).invoke(watcher,6,snapshot.health());
        }
        if(loader.loadClass("net.minecraft.entity.EntityAgeable").isInstance(entity)) {
            var watched=base.getDeclaredField("field_70180_af");watched.setAccessible(true);Object watcher=watched.get(entity);
            watcher.getClass().getMethod("func_75692_b",int.class,Object.class).invoke(watcher,12,snapshot.age());
        }
        Class<?> animal=loader.loadClass("net.minecraft.entity.passive.EntityAnimal");
        if(animal.isInstance(entity)){var love=animal.getDeclaredField("field_70881_d");love.setAccessible(true);love.setInt(entity,snapshot.love());}
        if(loader.loadClass("net.minecraft.entity.item.EntityItem").isInstance(entity)) {
            if(snapshot.item()==null)throw new IllegalArgumentException("Dropped item mirror requires its actual stack");
            entity.getClass().getMethod("func_92058_a",loader.loadClass("net.minecraft.item.ItemStack")).invoke(entity,items.materialize(snapshot.item()));
            // Old selection logic skips items that players could not pick up yet either (delayBeforeCanPickup >= 5, never-pickup display items).
            loader.loadClass("net.minecraft.entity.item.EntityItem").getField("field_145804_b").setInt(entity,snapshot.pickupDelay());
            if(existing!=null) {
                // A reused item mirror becomes the host's current state again: nothing old code left on it outlives a commit.
                base.getField("field_70128_L").setBoolean(entity,false);
                if(snapshot.entityData()==null){var field=base.getDeclaredField("customEntityData");field.setAccessible(true);field.set(entity,null);}
            }
        }
        double half=snapshot.width()/2;
        Object box=loader.loadClass("net.minecraft.util.AxisAlignedBB").getMethod("func_72330_a",double.class,double.class,double.class,double.class,double.class,double.class).invoke(null,snapshot.x()-half,snapshot.y(),snapshot.z()-half,snapshot.x()+half,snapshot.y()+snapshot.height(),snapshot.z()+half);
        var bounds=base.getField("field_70121_D");bounds.setAccessible(true);bounds.set(entity,box);
        if(generic&&existing==null)type.getField("mfclPushable").setBoolean(entity,snapshot.pushable());
        var living=loader.loadClass("net.minecraft.entity.EntityLiving");
        if(living.isInstance(entity)&&!snapshot.name().isEmpty())living.getMethod("func_94058_c",String.class).invoke(entity,snapshot.name());
        if(loader.loadClass("net.minecraft.entity.passive.EntitySheep").isInstance(entity)){
            var sheep=LegacyWorlds.access(world).sheep(snapshot.id());
            if(sheep!=null){Object watcher=base.getMethod("func_70096_w").invoke(entity);watcher.getClass().getMethod("func_75692_b",int.class,Object.class).invoke(watcher,16,(byte)(sheep.color()|(sheep.sheared()?16:0)));}
        }
        LegacyPotions.refresh(entity,world,snapshot.id());
        LegacyEntityHooks.mirrored(entity);
        return entity;
    }
    /** Forge's Entity constructor part that Unsafe allocation skips: the extended-properties map plus the
     * EntityConstructing event, so mods find their registered properties (getExtendedProperties) on mirrors. */
    static void forgeConstruct(ClassLoader loader,Object entity,Object world) throws Exception {
        var base=loader.loadClass("net.minecraft.entity.Entity");
        var field=base.getDeclaredField("extendedProperties");field.setAccessible(true);
        if(field.get(entity)!=null)return;
        var properties=new HashMap<Object,Object>();field.set(entity,properties);
        Object bus=loader.loadClass("net.minecraftforge.common.MinecraftForge").getField("EVENT_BUS").get(null);
        Class<?> event=loader.loadClass("net.minecraftforge.event.entity.EntityEvent$EntityConstructing");
        bus.getClass().getMethod("post",loader.loadClass("cpw.mods.fml.common.eventhandler.Event")).invoke(bus,event.getConstructor(base).newInstance(entity));
        var init=loader.loadClass("net.minecraftforge.common.IExtendedEntityProperties").getMethod("init",base,loader.loadClass("net.minecraft.world.World"));
        for(Object value:List.copyOf(properties.values()))init.invoke(value,entity,world);
    }
    private static void initializeDataWatcher(Class<?> type,Object entity) throws ReflectiveOperationException {
        for(Class<?> current=type;current!=null;current=current.getSuperclass()) {
            try {
                var method=current.getDeclaredMethod("func_70088_a");method.setAccessible(true);method.invoke(entity);return;
            } catch(NoSuchMethodException ignored) { }
        }
    }
    private static void equipment(Class<?> owner,String name,Object entity,Object value) throws Exception {
        var field=owner.getDeclaredField(name);field.setAccessible(true);if(field.get(entity)==null)field.set(entity,value);
    }
    private static void watch(Object watcher,Method add,Method exists,int index,Object value) throws Exception {
        if(exists.invoke(watcher,index)==null) add.invoke(watcher,index,value);
    }
    static HostWorldAccess.Motion motion(ClassLoader loader,Object entity,UUID id) throws Exception {
        var type=loader.loadClass("net.minecraft.entity.Entity");Class<?> animal=loader.loadClass("net.minecraft.entity.passive.EntityAnimal");int love=0;
        if(animal.isInstance(entity)){var field=animal.getDeclaredField("field_70881_d");field.setAccessible(true);love=field.getInt(entity);}
        return new HostWorldAccess.Motion(id,type.getField("field_70159_w").getDouble(entity),type.getField("field_70181_x").getDouble(entity),type.getField("field_70179_y").getDouble(entity),type.getField("field_70143_R").getFloat(entity),love);
    }
    private static Class<?> facade(ClassLoader loader,String parent) throws Exception {
        var types=FACADES.computeIfAbsent(loader,ignored->new HashMap<>());Class<?> existing=types.get(parent);if(existing!=null)return existing;
        String name="github/com/gengyoubo/mfcl/legacy1710/Host"+parent.substring(parent.lastIndexOf('.')+1)+"Snapshot";
        var writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);writer.visit(Opcodes.V1_8,Opcodes.ACC_PUBLIC,name,null,parent.replace('.','/'),null);
        writer.visitField(Opcodes.ACC_PUBLIC,"mfclPushable","Z",null,null).visitEnd();
        for(String method:List.of("func_70088_a","func_70037_a","func_70014_b")) {
            var mv=writer.visitMethod(Opcodes.ACC_PUBLIC,method,method.equals("func_70088_a")?"()V":"(Lnet/minecraft/nbt/NBTTagCompound;)V",null,null);mv.visitCode();mv.visitInsn(Opcodes.RETURN);mv.visitMaxs(0,2);mv.visitEnd();
        }
        var mv=writer.visitMethod(Opcodes.ACC_PUBLIC,"func_70104_M","()Z",null,null);mv.visitCode();mv.visitVarInsn(Opcodes.ALOAD,0);mv.visitFieldInsn(Opcodes.GETFIELD,name,"mfclPushable","Z");mv.visitInsn(Opcodes.IRETURN);mv.visitMaxs(1,1);mv.visitEnd();writer.visitEnd();
        byte[] bytes=writer.toByteArray();var type=(Class<?>)loader.getClass().getMethod("defineLegacyClass",String.class,byte[].class,int.class,int.class).invoke(loader,name.replace('/','.'),bytes,0,bytes.length);types.put(parent,type);return type;
    }
}
