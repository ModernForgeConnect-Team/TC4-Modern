package github.com.gengyoubo.mfcl.bridge;

import java.lang.reflect.*;
import java.util.*;

/** Stable names around 1.7.10's configurable numeric potion array. The host owns duration and persistence. */
public final class LegacyPotions {
    private static final Map<ClassLoader,LegacyPotions> RUNTIMES=Collections.synchronizedMap(new WeakHashMap<>());
    private static final String[] VANILLA={"","speed","slowness","haste","mining_fatigue","strength","instant_health","instant_damage","jump_boost","nausea","regeneration","resistance","fire_resistance","water_breathing","invisibility","blindness","night_vision","hunger","weakness","poison","wither","health_boost","absorption","saturation"};
    public record Modifier(String attribute,UUID id,double amount,int operation) {}
    public record Entry(String key,int id,String name,boolean harmful,int color,boolean instant,String texture,int icon,List<Modifier> modifiers) {}
    private final LegacyServices services;
    private final Class<?> potion,living,effect;
    private final Field active;
    private final Map<Integer,String> names=new HashMap<>();
    private final Map<String,Integer> ids=new HashMap<>();
    private final Map<String,Entry> entries=new LinkedHashMap<>();
    private final Map<String,String> shaders=new LinkedHashMap<>();
    public LegacyPotions(LegacyServices services)throws Exception {
        this.services=services;var loader=services.loader();potion=loader.loadClass("net.minecraft.potion.Potion");living=loader.loadClass("net.minecraft.entity.EntityLivingBase");effect=loader.loadClass("net.minecraft.potion.PotionEffect");
        active=living.getDeclaredField("field_70713_bf");active.setAccessible(true);
        for(int id=1;id<VANILLA.length;id++){names.put(id,"minecraft:"+VANILLA[id]);ids.put("minecraft:"+VANILLA[id],id);}
        RUNTIMES.put(loader,this);
    }
    public void register(String key,Object original,String texture)throws Exception {
        if(original==null)return;
        services.items().inLegacy(()->{
            if(!potion.isInstance(original))throw new IllegalArgumentException("Foreign potion: "+key);
            int id=potion.getField("field_76415_H").getInt(original);
            if(names.containsKey(id)||ids.containsKey(key))throw new IllegalArgumentException("Duplicate potion mapping: "+key+" / "+id);
            // Read the base icon field: TC4's getter binds a client texture and must not run on a server.
            Field icon=null;for(Field field:potion.getDeclaredFields())if(field.getName().equals("field_76417_J")||field.getName().equals("statusIconIndex")){icon=field;break;}
            int index=-1;if(icon!=null){icon.setAccessible(true);index=icon.getInt(original);}
            var modifiers=new ArrayList<Modifier>();var attribute=services.loader().loadClass("net.minecraft.entity.ai.attributes.IAttribute");var modifier=services.loader().loadClass("net.minecraft.entity.ai.attributes.AttributeModifier");
            for(var entry:((Map<?,?>)potion.getMethod("func_111186_k").invoke(original)).entrySet())modifiers.add(new Modifier(
                    (String)attribute.getMethod("func_111108_a").invoke(entry.getKey()),(UUID)modifier.getMethod("func_111167_a").invoke(entry.getValue()),
                    (double)modifier.getMethod("func_111164_d").invoke(entry.getValue()),(int)modifier.getMethod("func_111169_c").invoke(entry.getValue())));
            var entry=new Entry(key,id,(String)potion.getMethod("func_76393_a").invoke(original),(boolean)potion.getMethod("func_76400_d").invoke(original),
                    (int)potion.getMethod("func_76401_j").invoke(original),(boolean)potion.getMethod("func_76403_b").invoke(original),texture,index,List.copyOf(modifiers));
            entries.put(key,entry);names.put(id,key);ids.put(key,id);return null;
        });
    }
    /** Other mods can be identified by their actual FML source JAR, never by their numeric potion ID. */
    public void discover()throws Exception {
        services.items().inLegacy(()->{
            var loader=services.loader();Object fml=loader.loadClass("cpw.mods.fml.common.Loader").getMethod("instance").invoke(null);
            var mods=(Map<?,?>)fml.getClass().getMethod("getIndexedModList").invoke(fml);
            var container=loader.loadClass("cpw.mods.fml.common.ModContainer");
            for(Object original:(Object[])potion.getField("field_76425_a").get(null)){
                if(original==null||names.containsKey(potion.getField("field_76415_H").getInt(original)))continue;
                var source=original.getClass().getProtectionDomain().getCodeSource();if(source==null)continue;
                String owner=null;for(var mod:mods.entrySet()){
                    var file=(java.io.File)container.getMethod("getSource").invoke(mod.getValue());
                    if(file!=null&&file.toURI().normalize().equals(source.getLocation().toURI().normalize())){owner=mod.getKey().toString().toLowerCase(Locale.ROOT);break;}
                }
                if(owner==null){LegacyDiagnostics.missingMapping("potion "+original.getClass().getName(),"No original mod source identity");continue;}
                String name=(String)potion.getMethod("func_76393_a").invoke(original);String path=name.replaceFirst("^potion\\.","").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_./-]","_");
                register(owner+":"+path,original,"minecraft:textures/gui/container/inventory.png");
            }
            return null;
        });
    }
    public List<Entry> entries(){return List.copyOf(entries.values());}
    public void registerScreenFilter(String effect,String resource){if(!entries.containsKey(effect))throw new IllegalArgumentException("Unknown potion "+effect);shaders.put(effect,resource);}
    public Map<String,String> screenFilters(){return Collections.unmodifiableMap(shaders);}
    public String name(int id){return names.get(id);}
    public Integer id(String name){return ids.get(name);}
    public static boolean mapped(ClassLoader loader,int id){var runtime=RUNTIMES.get(loader);return runtime!=null&&runtime.name(id)!=null;}
    public static LegacyClientData.Stack cureStack(LegacyClientData.Stack stack){return new LegacyClientData.Stack(stack.key(),stack.metadata(),stack.count(),"",stack.tag());}
    public static List<LegacyClientData.Stack> curatives(ClassLoader loader,Object effect)throws Exception {
        var runtime=RUNTIMES.get(loader);if(runtime==null)return null;var result=new ArrayList<LegacyClientData.Stack>();
        for(Object stack:(List<?>)runtime.effect.getMethod("getCurativeItems").invoke(effect))result.add(cureStack(runtime.services.items().snapshot(stack)));
        return List.copyOf(result);
    }
    public static void restoreCuratives(ClassLoader loader,Object effect,List<LegacyClientData.Stack> cures)throws Exception {
        var runtime=RUNTIMES.get(loader);if(runtime==null||cures==null)return;var values=new ArrayList<Object>();
        for(var cure:cures)values.add(runtime.services.items().materialize(cure));runtime.effect.getMethod("setCurativeItems",List.class).invoke(effect,values);
    }
    private Object original(Entry entry)throws Exception{return ((Object[])potion.getField("field_76425_a").get(null))[entry.id()];}
    public boolean ready(Entry entry,int duration,int amplifier)throws Exception{return services.items().inLegacy(()->(boolean)potion.getMethod("func_76397_a",int.class,int.class).invoke(original(entry),duration,amplifier));}
    public double modifierValue(Entry entry,UUID id,int amplifier)throws Exception {
        return services.items().inLegacy(()->{
            var type=services.loader().loadClass("net.minecraft.entity.ai.attributes.AttributeModifier");
            for(Object modifier:((Map<?,?>)potion.getMethod("func_111186_k").invoke(original(entry))).values())if(id.equals(type.getMethod("func_111167_a").invoke(modifier)))
                return (double)potion.getMethod("func_111183_a",int.class,type).invoke(original(entry),amplifier,modifier);
            throw new IllegalArgumentException("Missing original potion modifier: "+id);
        });
    }
    public void tick(Entry entry,Object target,int amplifier)throws Exception{potion.getMethod("func_76394_a",living,int.class).invoke(original(entry),target,amplifier);}
    public void instant(Entry entry,Object source,Object target,int amplifier,double strength)throws Exception{potion.getMethod("func_76402_a",living,living,int.class,double.class).invoke(original(entry),living.isInstance(source)?source:null,target,amplifier,strength);}
    public Object entity(Object world,UUID id)throws Exception{
        var host=LegacyWorlds.access(world);var snapshot=host.entity(id);if(snapshot==null)return null;
        return LegacyEntityAccess.mirror(services.items(),world,snapshot,host.existingEntity(id));
    }
    /** Query mirrors must answer hasPotion/getActivePotionEffect from the real entity's current effects. */
    public static void refresh(Object entity,Object world,UUID id)throws Exception {
        refreshChanged(entity,world,id);
    }
    /** Refresh durations too, but only additions, removals and amplifier changes affect container calculations. */
    public static boolean refreshChanged(Object entity,Object world,UUID id)throws Exception {
        var runtime=RUNTIMES.get(entity.getClass().getClassLoader());if(runtime==null||!runtime.living.isInstance(entity))return false;
        var values=new HashMap<Integer,Object>();
        for(var state:LegacyWorlds.access(world).effects(id)){
            Object old=runtime.effect.getConstructor(int.class,int.class,int.class,boolean.class).newInstance(state.id(),state.duration(),state.amplifier(),state.ambient());
            restoreCuratives(entity.getClass().getClassLoader(),old,state.curatives());values.put(state.id(),old);
        }
        var before=(Map<?,?>)runtime.active.get(entity);boolean changed=before==null?!values.isEmpty():!before.keySet().equals(values.keySet());
        if(!changed&&before!=null)for(var entry:values.entrySet())if(!runtime.effect.getMethod("func_76458_c").invoke(before.get(entry.getKey())).equals(runtime.effect.getMethod("func_76458_c").invoke(entry.getValue()))){changed=true;break;}
        runtime.active.set(entity,values);return changed;
    }
    /** Keep same-callback reads consistent after a mirrored addPotionEffect. */
    public static Object added(Object entity,Object incoming)throws Exception {
        var runtime=RUNTIMES.get(entity.getClass().getClassLoader());if(runtime==null)return incoming;
        @SuppressWarnings("unchecked") var values=(Map<Integer,Object>)runtime.active.get(entity);
        if(values==null){values=new HashMap<>();runtime.active.set(entity,values);}
        int id=(int)runtime.effect.getMethod("func_76456_a").invoke(incoming);Object before=values.get(id);
        if(before==null)values.put(id,incoming);else runtime.effect.getMethod("func_76452_a",runtime.effect).invoke(before,incoming);
        return values.get(id);
    }
    public static void removed(Object entity,int id)throws Exception {
        var runtime=RUNTIMES.get(entity.getClass().getClassLoader());if(runtime==null)return;
        var values=(Map<?,?>)runtime.active.get(entity);if(values!=null){if(id<0)values.clear();else values.remove(id);}
    }
}
