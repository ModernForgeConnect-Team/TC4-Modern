package github.com.gengyoubo.mfcl.bridge;

import com.mojang.logging.LogUtils;
import java.lang.reflect.*;
import java.util.*;

/**
 * Called from original Entity code rewritten by EntityHostTransformer. A mirror is only a snapshot of a host entity,
 * so damage, burning and potion effects old code applies to it are forwarded to the authoritative host entity.
 * Client mirrors and every non-mirror entity keep running their original implementation.
 */
public final class LegacyEntityHooks {
    // Weak identity keys: old Entity.equals compares entity ids, and mirrors are short-lived query snapshots.
    private static final Map<Object, Boolean> MIRRORS = new com.google.common.collect.MapMaker().weakKeys().makeMap();
    private static final Map<ClassLoader, Access> ACCESS = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Set<String> FAILURES = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private LegacyEntityHooks() {}
    private record Access(Class<?> entity, Field world, Method id, Class<?> source, Field type, Method fire, Method projectile, Method magic, Method explosion,
                          Method bypassArmor, Method direct, Method attacker, Method potion, Method duration, Method amplifier, Method ambient) {
        static Access of(ClassLoader loader) {
            try {
                var entity = loader.loadClass("net.minecraft.entity.Entity"); var source = loader.loadClass("net.minecraft.util.DamageSource");
                var effect = loader.loadClass("net.minecraft.potion.PotionEffect");
                return new Access(entity, entity.getField("field_70170_p"), entity.getMethod("func_110124_au"), source, source.getField("field_76373_n"),
                        source.getMethod("func_76347_k"), source.getMethod("func_76352_a"), source.getMethod("func_82725_o"), source.getMethod("func_94541_c"),
                        source.getMethod("func_76363_c"), source.getMethod("func_76364_f"), source.getMethod("func_76346_g"),
                        effect.getMethod("func_76456_a"), effect.getMethod("func_76459_b"), effect.getMethod("func_76458_c"), effect.getMethod("func_82720_e"));
            } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Old Entity API is incomplete", failure); }
        }
        UUID idOf(Object value) throws ReflectiveOperationException { return value != null && entity.isInstance(value) ? (UUID) id.invoke(value) : null; }
    }
    static void mirrored(Object entity) { MIRRORS.put(entity, Boolean.TRUE); }
    static boolean isMirror(Object entity) { return MIRRORS.containsKey(entity); }
    /** Inspection uses the real host's NBT; Unsafe query mirrors do not own full potion/AI/attribute state. */
    public static boolean inspect(Object entity,Object tag) throws Exception {
        if(isMirror(entity)) {
            var host=host(entity);byte[] data=host==null?null:host.nativeEntityData(access(entity).idOf(entity));
            if(data==null)return false;LegacyHostTileData.copy(data,tag);return true;
        }
        return (boolean)access(entity).entity.getMethod("func_70039_c",entity.getClass().getClassLoader().loadClass("net.minecraft.nbt.NBTTagCompound")).invoke(entity,tag);
    }
    public static void sheared(Object entity,boolean value) throws ReflectiveOperationException {
        var loader=entity.getClass().getClassLoader();Object watcher=loader.loadClass("net.minecraft.entity.Entity").getMethod("func_70096_w").invoke(entity);
        byte flags=(byte)watcher.getClass().getMethod("func_75683_a",int.class).invoke(watcher,16);
        watcher.getClass().getMethod("func_75692_b",int.class,Object.class).invoke(watcher,16,(byte)(value?flags|16:flags&~16));
        host(entity).sheared(access(entity).idOf(entity),value);
    }
    /** Actors and query mirrors both lack the old MinecraftServer used by EntityPlayerMP. */
    public static boolean hasServerHost(Object entity) {
        try {var host=host(entity);return host!=null&&!host.clientSide();}
        catch(ReflectiveOperationException failure){throw new IllegalStateException(failure);}
    }
    public static boolean canAttackPlayer(Object attacker,Object target) throws ReflectiveOperationException {
        return host(attacker).canAttackPlayer(access(attacker).idOf(attacker),access(attacker).idOf(target));
    }
    /** Keep the original swing state/callback, but deliver its animation through the host tracker. */
    public static void animation(Object tracker,Object entity,Object packet) throws ReflectiveOperationException {
        var host=host(entity);
        if(host!=null)host.swing(access(entity).idOf(entity));
        else tracker.getClass().getMethod("func_151247_a",access(entity).entity,entity.getClass().getClassLoader().loadClass("net.minecraft.network.Packet")).invoke(tracker,entity,packet);
    }
    private static Access access(Object entity) { return ACCESS.computeIfAbsent(entity.getClass().getClassLoader(), Access::of); }
    private static HostWorldAccess host(Object entity) throws ReflectiveOperationException {
        return LegacyWorlds.hostOrNull(access(entity).world.get(entity));
    }
    public static boolean intercepts(Object entity) {
        if (MIRRORS.isEmpty() || !MIRRORS.containsKey(entity)) return false;
        try { var host = host(entity); return host != null && !host.clientSide(); }
        catch (Throwable failure) { report("mirror lookup", failure); return false; }
    }
    /** EntityItem.getEntityItem and isEntityAlive: old AI keeps an item mirror for many ticks and changes it long after the query. */
    public static void droppedItem(Object entity) {
        if (MIRRORS.isEmpty() || !MIRRORS.containsKey(entity)) return;
        try { LegacyWorlds.retainedDroppedItem(access(entity).world.get(entity), entity); }
        catch (Throwable failure) { report("dropped item refresh", failure); }
    }
    public static boolean attack(Object entity, Object source, float amount) {
        try {
            var access = access(entity); var host = host(entity);
            if (host == null || source == null) return false;
            return host.damage(new HostWorldAccess.Damage(access.idOf(entity), (String) access.type.get(source), amount,
                    (boolean) access.fire.invoke(source), (boolean) access.projectile.invoke(source), (boolean) access.magic.invoke(source),
                    (boolean) access.explosion.invoke(source), (boolean) access.bypassArmor.invoke(source),
                    access.idOf(access.direct.invoke(source)), access.idOf(access.attacker.invoke(source))));
        } catch (Throwable failure) { report("attackEntityFrom", failure); return false; }
    }
    public static void ignite(Object entity, int seconds) {
        try { var host = host(entity); if (host != null) host.ignite(access(entity).idOf(entity), seconds); }
        catch (Throwable failure) { report("setFire", failure); }
    }
    public static void effect(Object entity, Object effect) {
        try {
            var access = access(entity); var host = host(entity);
            if (host == null || effect == null) return;
            effect=LegacyPotions.added(entity,effect);
            host.addEffect(access.idOf(entity), new LegacyPlayerBody.Effect((int) access.potion.invoke(effect), (int) access.duration.invoke(effect), (int) access.amplifier.invoke(effect), (boolean) access.ambient.invoke(effect),LegacyPotions.curatives(entity.getClass().getClassLoader(),effect)));
        } catch (Throwable failure) { report("addPotionEffect", failure); }
    }
    public static void heal(Object entity,float amount) {
        try{var host=host(entity);if(host!=null)host.heal(access(entity).idOf(entity),amount);}
        catch(Throwable failure){report("heal",failure);}
    }
    public static void removeEffect(Object entity,int potion) {
        try{LegacyPotions.removed(entity,potion);var host=host(entity);if(host!=null){if(potion<0)host.clearEffects(access(entity).idOf(entity));else host.removeEffect(access(entity).idOf(entity),potion);}}
        catch(Throwable failure){report("removePotionEffect",failure);}
    }
    // Old callers cannot handle a bridge exception; a forwarding failure must not abort their logic.
    private static void report(String operation, Throwable failure) {
        while (failure instanceof InvocationTargetException && failure.getCause() != null) failure = failure.getCause();
        if (FAILURES.add(operation + failure)) LogUtils.getLogger().error("MFCL cannot forward old {} to its host entity", operation, failure);
    }
}
