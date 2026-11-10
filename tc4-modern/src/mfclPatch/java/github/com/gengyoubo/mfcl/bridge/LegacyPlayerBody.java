package github.com.gengyoubo.mfcl.bridge;

import java.lang.reflect.Field;
import java.util.*;

/**
 * Player state original code reads and changes besides items: entity id, flight, walk/fly speed, fall distance, step
 * height, motion and mapped potion effects. Native entities own durations and saved effect identities.
 * {@link #pull} copies the host player onto an actor when it is refreshed; {@link #push} returns only what original
 * code changed since then.
 */
public final class LegacyPlayerBody {
    public record Effect(int id, int duration, int amplifier, boolean ambient, List<LegacyClientData.Stack> curatives) {
        public Effect(int id,int duration,int amplifier,boolean ambient){this(id,duration,amplifier,ambient,null);}
        public Effect{if(curatives!=null)curatives=List.copyOf(curatives);}
    }
    public record State(int entityId, boolean allowFlying, boolean flying, float walkSpeed, float flySpeed, float fallDistance,
                        float stepHeight, double motionX, double motionY, double motionZ, Map<Integer, Effect> effects) {}
    public interface Host {
        /** Null when that side has no such host player. */
        State read(UUID player, boolean client);
        /** Current host eye height, or the ordinary standing height for synthetic players. */
        default float eyeHeight(UUID player, boolean client) { return 1.62f; }
        void write(UUID player, boolean client, State before, State after);
    }
    public static final int VANILLA_POTIONS = 23;
    private record Pulled(UUID id, boolean client, State state) {}
    private record Access(Field entityId, Field abilities, Field allowFlying, Field flying, Field walkSpeed, Field flySpeed, Field fallDistance,
                          Field stepHeight, Field motionX, Field motionY, Field motionZ, Field potions, Field uuid, Class<?> effect,
                          java.lang.reflect.Constructor<?> newEffect, java.lang.reflect.Method potion, java.lang.reflect.Method duration,
                          java.lang.reflect.Method amplifier, java.lang.reflect.Method ambient) {
        static Access of(ClassLoader loader) {
            try {
                var entity = loader.loadClass("net.minecraft.entity.Entity"); var living = loader.loadClass("net.minecraft.entity.EntityLivingBase");
                var capabilities = loader.loadClass("net.minecraft.entity.player.PlayerCapabilities"); var effect = loader.loadClass("net.minecraft.potion.PotionEffect");
                return new Access(open(entity.getDeclaredField("field_145783_c")), loader.loadClass("net.minecraft.entity.player.EntityPlayer").getField("field_71075_bZ"),
                        capabilities.getField("field_75101_c"), capabilities.getField("field_75100_b"), open(capabilities.getDeclaredField("field_75097_g")),
                        open(capabilities.getDeclaredField("field_75096_f")), entity.getField("field_70143_R"), entity.getField("field_70138_W"),
                        entity.getField("field_70159_w"), entity.getField("field_70181_x"), entity.getField("field_70179_y"),
                        open(living.getDeclaredField("field_70713_bf")), open(entity.getDeclaredField("field_96093_i")), effect,
                        effect.getConstructor(int.class, int.class, int.class, boolean.class), effect.getMethod("func_76456_a"),
                        effect.getMethod("func_76459_b"), effect.getMethod("func_76458_c"), effect.getMethod("func_82720_e"));
            } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Old player state API is incomplete", failure); }
        }
        private static Field open(Field field) { field.setAccessible(true); return field; }
    }
    private static volatile Host host;
    private static final Map<Object, Pulled> PULLED = new com.google.common.collect.MapMaker().weakKeys().makeMap();
    private static final Map<ClassLoader, Access> ACCESS = Collections.synchronizedMap(new WeakHashMap<>());
    private LegacyPlayerBody() {}
    public static void host(Host value) { host = Objects.requireNonNull(value); }
    public static boolean available() { return host != null; }
    public static float eyeHeight(UUID id, boolean client) {
        var current = host;
        return current == null ? 1.62f : current.eyeHeight(id, client);
    }
    private static Access access(Object actor) { return ACCESS.computeIfAbsent(actor.getClass().getClassLoader(), Access::of); }

    public static void pull(Object actor, boolean client) throws Exception {
        var current = host; if (current == null) return;
        var access = access(actor); UUID id = (UUID) access.uuid.get(actor);
        State state = id == null ? null : current.read(id, client);
        if (state == null) { PULLED.remove(actor); return; }
        access.entityId.setInt(actor, state.entityId());
        Object abilities = access.abilities.get(actor);
        access.allowFlying.setBoolean(abilities, state.allowFlying()); access.flying.setBoolean(abilities, state.flying());
        access.walkSpeed.setFloat(abilities, state.walkSpeed()); access.flySpeed.setFloat(abilities, state.flySpeed());
        access.fallDistance.setFloat(actor, state.fallDistance()); access.stepHeight.setFloat(actor, state.stepHeight());
        access.motionX.setDouble(actor, state.motionX()); access.motionY.setDouble(actor, state.motionY()); access.motionZ.setDouble(actor, state.motionZ());
        Map<Object, Object> potions = potions(access, actor);
        potions.keySet().removeIf(key -> key instanceof Integer potion && LegacyPotions.mapped(actor.getClass().getClassLoader(),potion));
        for (var effect : state.effects().values()) {
            Object old=access.newEffect.newInstance(effect.id(), effect.duration(), effect.amplifier(), effect.ambient());
            LegacyPotions.restoreCuratives(actor.getClass().getClassLoader(),old,effect.curatives());potions.put(effect.id(),old);
        }
        PULLED.put(actor, new Pulled(id, client, read(access, actor, state.entityId())));
    }

    public static void push(Object actor) throws Exception {
        var current = host; var pulled = PULLED.get(actor);
        if (current == null || pulled == null) return;
        var after = read(access(actor), actor, pulled.state().entityId());
        if (after.equals(pulled.state())) return;
        current.write(pulled.id(), pulled.client(), pulled.state(), after);
        PULLED.put(actor, new Pulled(pulled.id(), pulled.client(), after));
    }

    private static State read(Access access, Object actor, int entityId) throws Exception {
        Object abilities = access.abilities.get(actor);
        var effects = new TreeMap<Integer, Effect>();
        for (Object value : potions(access, actor).values()) {
            int id = (int) access.potion.invoke(value);
            if (LegacyPotions.mapped(actor.getClass().getClassLoader(),id)) effects.put(id, new Effect(id, (int) access.duration.invoke(value), (int) access.amplifier.invoke(value), (boolean) access.ambient.invoke(value),LegacyPotions.curatives(actor.getClass().getClassLoader(),value)));
        }
        return new State(entityId, access.allowFlying.getBoolean(abilities), access.flying.getBoolean(abilities), access.walkSpeed.getFloat(abilities),
                access.flySpeed.getFloat(abilities), access.fallDistance.getFloat(actor), access.stepHeight.getFloat(actor),
                access.motionX.getDouble(actor), access.motionY.getDouble(actor), access.motionZ.getDouble(actor), Collections.unmodifiableMap(effects));
    }
    @SuppressWarnings("unchecked")
    private static Map<Object, Object> potions(Access access, Object actor) throws Exception {
        var potions = (Map<Object, Object>) access.potions.get(actor);
        if (potions == null) { potions = new HashMap<>(); access.potions.set(actor, potions); }
        return potions;
    }
}
