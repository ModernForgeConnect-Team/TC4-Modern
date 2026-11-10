package github.com.gengyoubo.mfcl.bridge;

import com.google.gson.*;
import java.util.*;

/** Old sound identifiers and resource definitions, independent of either audio engine. */
public final class LegacySounds {
    private static final Map<String,String> VANILLA=Map.ofEntries(
            Map.entry("random.orb","entity.experience_orb.pickup"),Map.entry("random.pop","entity.item.pickup"),
            Map.entry("random.click","block.lever.click"),Map.entry("gui.button.press","ui.button.click"),
            Map.entry("random.bow","entity.arrow.shoot"),Map.entry("random.break","entity.item.break"),
            Map.entry("random.fizz","block.fire.extinguish"),Map.entry("random.explode","entity.generic.explode"),
            Map.entry("random.drink","entity.generic.drink"),Map.entry("random.eat","entity.generic.eat"),
            Map.entry("fire.ignite","item.flintandsteel.use"),Map.entry("mob.endermen.portal","entity.enderman.teleport"),
            Map.entry("liquid.splash","entity.generic.splash"),Map.entry("liquid.swim","entity.generic.swim"),
            Map.entry("dig.wood","block.wood.break"),Map.entry("step.wood","block.wood.step"),
            Map.entry("random.chestopen","block.chest.open"),Map.entry("random.chestclosed","block.chest.close"),
            Map.entry("random.door_open","block.wooden_door.open"),Map.entry("random.door_close","block.wooden_door.close"),
            Map.entry("game.neutral.swim","entity.generic.swim"));
    private LegacySounds() {}
    public static String placementId(String name) {
        String id=name.startsWith("minecraft:")?name.substring(10):name;
        if(id.startsWith("dig.")||id.startsWith("step.")) {
            String material=id.substring(id.indexOf('.')+1);
            return switch(material) {
                case "wood","stone","grass","gravel","sand","snow","glass","metal","ladder","anvil" -> "minecraft:block."+material+".place";
                case "cloth" -> "minecraft:block.wool.place";
                default -> modernId(name);
            };
        }
        return modernId(name);
    }
    public static String modernId(String name) {
        String id=name.contains(":")?name:"minecraft:"+name;
        id=id.toLowerCase(Locale.ROOT);
        if(id.startsWith("minecraft:dig.")||id.startsWith("minecraft:step.")){
            String material=id.substring(id.indexOf('.')+1);if(material.equals("cloth"))material="wool";
            if(Set.of("wood","stone","grass","gravel","sand","snow","glass","metal","ladder","anvil","wool").contains(material))
                return "minecraft:block."+material+(id.startsWith("minecraft:dig.")?".break":".step");
        }
        return id.startsWith("minecraft:")?"minecraft:"+VANILLA.getOrDefault(id.substring(10),id.substring(10)):id;
    }
    public static JsonObject definitions(String namespace,String json) {
        var result=new JsonObject();
        for(var entry:JsonParser.parseString(json).getAsJsonObject().entrySet()) {
            var definition=entry.getValue().getAsJsonObject().deepCopy();definition.remove("category");
            var sounds=definition.getAsJsonArray("sounds");
            if(sounds!=null)for(int index=0;index<sounds.size();index++) {
                JsonElement sound=sounds.get(index);
                if(sound.isJsonPrimitive())sounds.set(index,new JsonPrimitive(qualified(namespace,sound.getAsString())));
                else if(sound.getAsJsonObject().has("name"))sound.getAsJsonObject().addProperty("name",qualified(namespace,sound.getAsJsonObject().get("name").getAsString()));
            }
            result.add(entry.getKey().toLowerCase(Locale.ROOT),definition);
        }
        return result;
    }
    private static String qualified(String namespace,String name) {return (name.contains(":")?name:namespace+":"+name).toLowerCase(Locale.ROOT);}
}
