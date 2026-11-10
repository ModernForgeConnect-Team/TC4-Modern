package github.com.gengyoubo.mfcl.content;

import github.com.gengyoubo.mfcl.bridge.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.effect.MobEffect;
import java.util.*;

/** Explicit old vanilla loot NBT conversion. The source tag also preserves old-only enchantment data for round trips. */
final class LegacyVanillaStacks {
    private static final String SOURCE="mfcl_legacy_vanilla";
    private LegacyVanillaStacks() {}
    static ItemStack toHost(LegacyServices services,LegacyClientData.Stack old) {
        if(!old.key().startsWith("minecraft:")||(old.tag().isEmpty()&&!old.key().equals("minecraft:potion")))return ItemStack.EMPTY;
        try {
            var tag=originalTag(services,old);
            boolean potion=old.key().equals("minecraft:potion");
            var result=potion?new ItemStack((old.metadata()&0x4000)!=0?Items.SPLASH_POTION:Items.POTION,old.count()):HostStackMapping.renderStack(old);
            if(result.isEmpty())return result;
            for(String name:tag.getAllKeys()) {
                switch(name) {
                    case "ench","StoredEnchantments" -> result.getOrCreateTag().put(name.equals("ench")?"Enchantments":name,enchantments(services,tag.getList(name,Tag.TAG_COMPOUND),true));
                    case "RepairCost","Unbreakable","HideFlags" -> result.getOrCreateTag().put(name,tag.get(name).copy());
                    case "display" -> {
                        var display=tag.getCompound(name).copy();
                        if(display.contains("Name",Tag.TAG_STRING))display.putString("Name",Component.Serializer.toJson(Component.literal(display.getString("Name"))));
                        if(display.contains("Lore",Tag.TAG_LIST)) {
                            var lore=new ListTag();for(Tag line:display.getList("Lore",Tag.TAG_STRING))lore.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal(line.getAsString()))));display.put("Lore",lore);
                        }
                        result.getOrCreateTag().put(name,display);
                    }
                    case "CustomPotionEffects" -> result.getOrCreateTag().put(name,potionEffects(services,tag.getList(name,Tag.TAG_COMPOUND),true));
                    default -> throw new UnsupportedOperationException("Unmapped old vanilla loot NBT: "+old.key()+"."+name);
                }
            }
            if(potion&&!tag.contains("CustomPotionEffects")) {
                var effects=services.items().inLegacy(()->{
                    var loader=services.loader();Object stack=services.items().materialize(old);
                    Object item=loader.loadClass("net.minecraft.item.ItemStack").getMethod("func_77973_b").invoke(stack);
                    var list=(List<?>)item.getClass().getMethod("func_77832_l",loader.loadClass("net.minecraft.item.ItemStack")).invoke(item,stack);
                    var output=new ListTag();if(list==null)return output;
                    for(Object effect:list) {
                        var type=loader.loadClass("net.minecraft.potion.PotionEffect");int id=(int)type.getMethod("func_76456_a").invoke(effect);
                        if(ModernContentBridge.active().potions().effect(id)==null)throw new UnsupportedOperationException("Unmapped old potion effect: "+id);
                        var entry=new CompoundTag();entry.putByte("Id",(byte)id);entry.putInt("Duration",(int)type.getMethod("func_76459_b").invoke(effect));
                        entry.putByte("Amplifier",((Integer)type.getMethod("func_76458_c").invoke(effect)).byteValue());output.add(entry);
                    }
                    return potionEffects(services,output,true);
                });
                result.getOrCreateTag().put("CustomPotionEffects",effects);
            }
            var source=new CompoundTag();source.putString("key",old.key());source.putInt("metadata",old.metadata());source.put("tag",tag.copy());
            if(potion&&!tag.contains("CustomPotionEffects"))source.put("generated_potion_effects",result.getTag().getList("CustomPotionEffects",Tag.TAG_COMPOUND).copy());
            source.putBoolean("enchantments_mapped",true);
            result.getOrCreateTag().put(SOURCE,source);
            return result;
        }catch(RuntimeException failure){throw failure;}
        catch(Exception failure){throw new IllegalStateException("Cannot convert original vanilla loot: "+old.key(),failure);}
    }
    static LegacyClientData.Stack toLegacy(LegacyServices services,ItemStack stack) throws Exception {
        migrate(services,stack);
        var root=stack.getTag();if(root==null)return null;
        boolean original=root.contains(SOURCE,Tag.TAG_COMPOUND);
        if(!original&&(!net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace().equals("minecraft")||!root.contains("Enchantments")&&!root.contains("StoredEnchantments")))return null;
        if(!original)for(String name:root.getAllKeys())if(!Set.of("Damage","Enchantments","StoredEnchantments","RepairCost","Unbreakable","HideFlags","display").contains(name))throw new UnsupportedOperationException("Unmapped enchanted host NBT: "+name);
        var source=original?root.getCompound(SOURCE):new CompoundTag();String key=original?source.getString("key"):net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        if(!key.startsWith("minecraft:"))throw new IllegalArgumentException("Invalid original loot identity: "+key);
        var tag=source.getCompound("tag").copy();
        if(root.contains("CustomPotionEffects")&&!(source.contains("generated_potion_effects",Tag.TAG_LIST)&&source.getList("generated_potion_effects",Tag.TAG_COMPOUND).equals(root.getList("CustomPotionEffects",Tag.TAG_COMPOUND))))tag.put("CustomPotionEffects",potionEffects(services,root.getList("CustomPotionEffects",Tag.TAG_COMPOUND),false));
        else if(tag.contains("CustomPotionEffects"))tag.remove("CustomPotionEffects");
        // Native enchanting/repairing can change supported enchantments after the item was awarded.
        for(String name:List.of("ench","StoredEnchantments")) {
            String modern=name.equals("ench")?"Enchantments":name;
            var pending=new LinkedHashMap<Short,CompoundTag>();
            for(Tag entry:enchantments(services,root.getList(modern,Tag.TAG_COMPOUND),false))pending.put(((CompoundTag)entry).getShort("id"),(CompoundTag)entry);
            var list=new ListTag();
            for(Tag entry:tag.getList(name,Tag.TAG_COMPOUND)) {
                short id=((CompoundTag)entry).getShort("id");
                if(services.enchantments().name(id)==null)list.add(entry.copy());
                else if(pending.containsKey(id))list.add(pending.remove(id));
            }
            pending.values().forEach(list::add);
            if(list.isEmpty())tag.remove(name);else tag.put(name,list);
        }
        if(root.contains("RepairCost"))tag.putInt("RepairCost",root.getInt("RepairCost"));
        for(String name:List.of("Unbreakable","HideFlags"))if(root.contains(name))tag.put(name,root.get(name).copy());else tag.remove(name);
        if(root.contains("display",Tag.TAG_COMPOUND)) {
            var display=root.getCompound("display").copy();
            if(display.contains("Name",Tag.TAG_STRING)) {
                var name=Component.Serializer.fromJson(display.getString("Name"));display.putString("Name",name==null?"":name.getString());
            }
            if(display.contains("Lore",Tag.TAG_LIST)) {
                var lore=new ListTag();for(Tag line:display.getList("Lore",Tag.TAG_STRING)) {
                    var text=Component.Serializer.fromJson(line.getAsString());lore.add(StringTag.valueOf(text==null?"":text.getString()));
                }
                display.put("Lore",lore);
            }
            tag.put("display",display);
        }else tag.remove("display");
        var old=new LegacyClientData.Stack(key,stack.isDamageableItem()?stack.getDamageValue():source.getInt("metadata"),stack.getCount(),stack.getHoverName().getString(),"");
        if(tag.isEmpty())return old;
        var bytes=new java.io.ByteArrayOutputStream();NbtIo.writeCompressed(tag,bytes);return services.items().withTag(old,bytes.toByteArray());
    }
    private static ListTag potionEffects(LegacyServices services,ListTag effects,boolean toHost) {
        var result=new ListTag();var bridge=ModernContentBridge.active().potions();
        for(Tag value:effects){
            var tag=(CompoundTag)value;
            if(toHost){
                int id=tag.getInt("Id")&255;var effect=bridge.effect(id);
                if(effect==null)throw new UnsupportedOperationException("Unmapped old potion effect: "+id);
                result.add(new net.minecraft.world.effect.MobEffectInstance(effect,tag.getInt("Duration"),tag.getInt("Amplifier")&255,tag.getBoolean("Ambient"),!tag.contains("ShowParticles")||tag.getBoolean("ShowParticles")).save(new CompoundTag()));
            }else{
                var effect=net.minecraft.world.effect.MobEffectInstance.load(tag);Integer id=effect==null?null:bridge.oldId(effect.getEffect());
                if(id==null)throw new UnsupportedOperationException("Unmapped native potion effect: "+tag);
                var old=new CompoundTag();old.putByte("Id",id.byteValue());old.putInt("Duration",effect.getDuration());old.putByte("Amplifier",(byte)effect.getAmplifier());old.putBoolean("Ambient",effect.isAmbient());result.add(old);
            }
        }return result;
    }
    static CompoundTag originalTag(LegacyServices services,LegacyClientData.Stack old) throws Exception {
        if(old.tag().isEmpty())return new CompoundTag();
        // Old list SNBT includes indexes (e.g. [0:{id:1s}]); binary NBT has the same tag types in both versions.
        byte[] bytes=services.items().inLegacy(()->{
            var loader=services.loader();Object stack=services.items().materialize(old);
            Object tag=loader.loadClass("net.minecraft.item.ItemStack").getField("field_77990_d").get(stack);
            var output=new java.io.ByteArrayOutputStream();
            loader.loadClass("net.minecraft.nbt.CompressedStreamTools").getMethod("func_74799_a",loader.loadClass("net.minecraft.nbt.NBTTagCompound"),java.io.OutputStream.class).invoke(null,tag,output);
            return output.toByteArray();
        });
        return NbtIo.readCompressed(new java.io.ByteArrayInputStream(bytes));
    }
    static void migrate(LegacyServices services,ItemStack stack) {
        var source=stack.getTagElement(SOURCE);if(source==null||source.getBoolean("enchantments_mapped"))return;
        for(String name:List.of("ench","StoredEnchantments")) {
            var added=new ListTag();
            for(Tag value:source.getCompound("tag").getList(name,Tag.TAG_COMPOUND))
                if(!LegacyEnchantments.VANILLA.containsKey((int)((CompoundTag)value).getShort("id")))added.add(value.copy());
            var merged=new LinkedHashMap<String,Tag>();
            for(Tag value:enchantments(services,added,true))merged.put(((CompoundTag)value).getString("id"),value);
            String modern=name.equals("ench")?"Enchantments":name;
            for(Tag value:stack.getOrCreateTag().getList(modern,Tag.TAG_COMPOUND))merged.put(((CompoundTag)value).getString("id"),value.copy());
            var list=new ListTag();merged.values().forEach(list::add);if(!list.isEmpty())stack.getOrCreateTag().put(modern,list);
        }
        source.putBoolean("enchantments_mapped",true);
    }
    static ListTag enchantments(LegacyServices services,ListTag input,boolean modern) {
        var output=new ListTag();
        for(Tag value:input) {
            var entry=(CompoundTag)value;var mapped=new CompoundTag();
            if(modern) {
                int id=entry.getShort("id");String name=services.enchantments().name(id);
                if(name==null){LegacyDiagnostics.missingMapping("enchantment "+id,"old-only enchantment retained in original loot NBT");continue;}
                mapped.putString("id",LegacyRegistryIds.id(name).toString());
            }else {
                String id=entry.getString("id");Integer found=services.enchantments().id(id);
                if(found==null)continue; // native-only enchantments stay host-owned during original callbacks
                mapped.putShort("id",found.shortValue());
            }
            mapped.putShort("lvl",entry.getShort("lvl"));output.add(mapped);
        }
        return output;
    }
}
