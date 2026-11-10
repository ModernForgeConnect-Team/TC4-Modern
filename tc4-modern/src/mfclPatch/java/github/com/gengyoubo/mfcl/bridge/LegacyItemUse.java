package github.com.gengyoubo.mfcl.bridge;

import com.mojang.logging.LogUtils;
import java.lang.reflect.*;
import java.util.*;

/**
 * Original right-click / held-use callbacks. The old player is cached per UUID so research, item-in-use and
 * other per-player state survive across the begin/tick/stop sequence the modern engine drives.
 */
public final class LegacyItemUse {
    public record Result(boolean used, int useDuration, String prompt, List<LegacyPackets.Message> messages,List<String> feedback,LegacyDeviceContainers.Open device) {
        public Result(boolean used,int useDuration,String prompt,List<LegacyPackets.Message> messages,List<String> feedback){this(used,useDuration,prompt,messages,feedback,null);}
        public Result(boolean used,int useDuration,String prompt) { this(used,useDuration,prompt,List.of(),List.of()); }
        public Result(boolean used,int useDuration,String prompt,List<LegacyPackets.Message> messages) {this(used,useDuration,prompt,messages,List.of());}
    }
    /** A finished legacy scan, ready to be replayed against the authoritative player. */
    public record Scan(byte type, int id, int meta, int entityId, String phenomena) {}
    public record AppliedScan(boolean accepted,byte[] data,List<LegacyPackets.Message> messages) {}
    public record UseTick(Scan scan,boolean using,List<LegacyPackets.Message> messages) {}
    private final LegacyServices services;
    // The same UUID exists on both the integrated server and the client; keep their actors apart.
    private final Map<String, Object> actors = new HashMap<>();
    private final Map<String,Boolean> inventoryUpdates = new HashMap<>();
    private final Map<String,Boolean> customArmorUses = new HashMap<>();
    /** An armor subclass may deliberately replace right-click equip with a portable GUI. */
    public boolean customArmorUse(LegacyClientData.Stack held) throws Exception {
        return services.items().inLegacy(()->{
            Boolean known=customArmorUses.get(held.key());if(known!=null)return known;
            var loader=services.loader();var stack=loader.loadClass("net.minecraft.item.ItemStack");
            Object item=stack.getMethod("func_77973_b").invoke(services.items().materialize(held));
            boolean custom=item.getClass().getMethod("func_77659_a",stack,loader.loadClass("net.minecraft.world.World"),loader.loadClass("net.minecraft.entity.player.EntityPlayer")).getDeclaringClass()!=loader.loadClass("net.minecraft.item.ItemArmor");
            customArmorUses.put(held.key(),custom);return custom;
        });
    }
    public LegacyItemUse(LegacyServices services) { this.services = services; }
    public record ItemAttribute(String name,HostWorldAccess.Modifier modifier){}
    public List<ItemAttribute> itemAttributes(LegacyClientData.Stack held) throws Exception {
        return services.items().inLegacy(()->{
            var loader=services.loader();var stack=loader.loadClass("net.minecraft.item.ItemStack");Object original=services.items().materialize(held);
            Object item=stack.getMethod("func_77973_b").invoke(original);
            Object map=loader.loadClass("net.minecraft.item.Item").getMethod("getAttributeModifiers",stack).invoke(item,original);
            var modifiers=loader.loadClass("com.google.common.collect.Multimap").getMethod("entries").invoke(map);
            var type=loader.loadClass("net.minecraft.entity.ai.attributes.AttributeModifier");var result=new ArrayList<ItemAttribute>();
            for(Object entry:(Collection<?>)modifiers){var pair=(Map.Entry<?,?>)entry;Object m=pair.getValue();result.add(new ItemAttribute((String)pair.getKey(),new HostWorldAccess.Modifier(
                    (UUID)type.getMethod("func_111167_a").invoke(m),(String)type.getMethod("func_111166_b").invoke(m),(double)type.getMethod("func_111164_d").invoke(m),(int)type.getMethod("func_111169_c").invoke(m),false)));}
            return List.copyOf(result);
        });
    }
    public float destroySpeed(LegacyClientData.Stack held,String block) throws Exception {
        return services.items().inLegacy(()->{
            var loader=services.loader();var stack=loader.loadClass("net.minecraft.item.ItemStack");Object original=services.items().materialize(held);
            Object registry=loader.loadClass("net.minecraft.block.Block").getField("field_149771_c").get(null);Object target=registry.getClass().getMethod("func_82594_a",String.class).invoke(registry,block);
            return (float)loader.loadClass("net.minecraft.item.Item").getMethod("func_150893_a",stack,loader.loadClass("net.minecraft.block.Block")).invoke(stack.getMethod("func_77973_b").invoke(original),original,target);
        });
    }
    public boolean harvest(LegacyClientData.Stack held,String block) throws Exception {
        return services.items().inLegacy(()->{
            var loader=services.loader();var stack=loader.loadClass("net.minecraft.item.ItemStack");Object original=services.items().materialize(held);
            Object registry=loader.loadClass("net.minecraft.block.Block").getField("field_149771_c").get(null);Object target=registry.getClass().getMethod("func_82594_a",String.class).invoke(registry,block);
            return (boolean)loader.loadClass("net.minecraft.item.Item").getMethod("canHarvestBlock",loader.loadClass("net.minecraft.block.Block"),stack).invoke(stack.getMethod("func_77973_b").invoke(original),target,original);
        });
    }
    public Result mine(Object world,LegacyTiles.Placer placer,LegacyClientData.Stack held,String key,int x,int y,int z,boolean start) throws Exception {
        return services.items().inLegacy(()->{
            try(var state=services.playerState().enter(world);var packets=LegacyPackets.capture(placer.id());var chat=new LegacyBlockLogic.ChatCapture()){
                var loader=services.loader();var stackType=loader.loadClass("net.minecraft.item.ItemStack");var itemType=loader.loadClass("net.minecraft.item.Item");var playerType=loader.loadClass("net.minecraft.entity.player.EntityPlayer");
                Object actor=actor(world,placer,held),stack=services.items().materialize(held);bindHeld(actor,stack);Object item=stackType.getMethod("func_77973_b").invoke(stack);
                boolean used;
                if(start)used=(boolean)itemType.getMethod("onBlockStartBreak",stackType,int.class,int.class,int.class,playerType).invoke(item,stack,x,y,z,actor);
                else {
                    Object registry=loader.loadClass("net.minecraft.block.Block").getField("field_149771_c").get(null);Object block=registry.getClass().getMethod("func_82594_a",String.class).invoke(registry,key);
                    used=(boolean)itemType.getMethod("func_150894_a",stackType,loader.loadClass("net.minecraft.world.World"),loader.loadClass("net.minecraft.block.Block"),int.class,int.class,int.class,loader.loadClass("net.minecraft.entity.EntityLivingBase")).invoke(item,stack,world,block,x,y,z,actor);
                }
                LegacyPlayerAttributes.push(actor);return new Result(used,0,null,packets.messages(),chat.messages());
            }
        });
    }
    private final Map<String,Boolean> entityUpdates=new HashMap<>();
    public boolean entityInteraction(LegacyClientData.Stack held) throws Exception {
        return services.items().inLegacy(()->{
            if(entityUpdates.containsKey(held.key()))return entityUpdates.get(held.key());
            var loader=services.loader();var stack=loader.loadClass("net.minecraft.item.ItemStack");var item=loader.loadClass("net.minecraft.item.Item");
            Object original=stack.getMethod("func_77973_b").invoke(services.items().materialize(held));
            boolean present=original.getClass().getMethod("func_111207_a",stack,loader.loadClass("net.minecraft.entity.player.EntityPlayer"),loader.loadClass("net.minecraft.entity.EntityLivingBase")).getDeclaringClass()!=item;
            entityUpdates.put(held.key(),present);return present;
        });
    }
    public Result interact(Object world,LegacyTiles.Placer placer,LegacyClientData.Stack held,UUID target,boolean hit) throws Exception {
        return interact(world,placer,held,target,hit?1:0);
    }
    public Result leftClick(Object world,LegacyTiles.Placer placer,LegacyClientData.Stack held,UUID target) throws Exception {
        return interact(world,placer,held,target,2);
    }
    /** Hosted entities need the old entity callback first, then the held item's living-target fallback. */
    public Result interactHosted(Object world,LegacyTiles.Placer placer,LegacyClientData.Stack held,UUID target) throws Exception {
        return interact(world,placer,held,target,3);
    }
    private Result interact(Object world,LegacyTiles.Placer placer,LegacyClientData.Stack held,UUID target,int action) throws Exception {
        return services.items().inLegacy(()->{
            try(var state=services.playerState().enter(world);var packets=LegacyPackets.capture(placer.id());var chat=new LegacyBlockLogic.ChatCapture();var menus=LegacyDeviceContainers.captureItem(services)){
                var loader=services.loader();var stackType=loader.loadClass("net.minecraft.item.ItemStack");var living=loader.loadClass("net.minecraft.entity.EntityLivingBase");
                Object actor=actor(world,placer,held),victim=LegacyWorlds.entity(world,target);if(victim==null||((action==0||action==1)&&!living.isInstance(victim)))return new Result(false,0,null);
                Object stack=held==null?null:services.items().materialize(held);bindHeld(actor,stack);
                Object item=stack==null?null:stackType.getMethod("func_77973_b").invoke(stack);
                boolean used;
                if(action==3) {
                    used=(boolean)loader.loadClass("net.minecraft.entity.Entity").getMethod("func_130002_c",loader.loadClass("net.minecraft.entity.player.EntityPlayer")).invoke(victim,actor);
                    if(!used&&item!=null&&living.isInstance(victim))used=(boolean)loader.loadClass("net.minecraft.item.Item").getMethod("func_111207_a",stackType,loader.loadClass("net.minecraft.entity.player.EntityPlayer"),living).invoke(item,stack,actor,victim);
                } else used=action==2?(boolean)loader.loadClass("net.minecraft.item.Item").getMethod("onLeftClickEntity",stackType,loader.loadClass("net.minecraft.entity.player.EntityPlayer"),loader.loadClass("net.minecraft.entity.Entity")).invoke(item,stack,actor,victim)
                        :(boolean)loader.loadClass("net.minecraft.item.Item").getMethod(action==1?"func_77644_a":"func_111207_a",stackType,action==1?living:loader.loadClass("net.minecraft.entity.player.EntityPlayer"),living)
                        .invoke(item,stack,action==1?victim:actor,action==1?actor:victim);
                LegacyPlayerAttributes.push(actor);
                return new Result(used||menus.opened()!=null,0,null,packets.messages(),chat.messages(),menus.opened());
            }
        });
    }
    public Result rightClick(Object world, LegacyTiles.Placer placer, LegacyClientData.Stack snapshot) throws Exception {
        return services.items().inLegacy(() -> {
            var loader = services.loader();
            try(var knowledge=services.playerState().enter(world)) {
            Class<?> stackType = loader.loadClass("net.minecraft.item.ItemStack");
            Class<?> itemType = loader.loadClass("net.minecraft.item.Item");
            Class<?> playerType = loader.loadClass("net.minecraft.entity.player.EntityPlayer");
            Class<?> worldType = loader.loadClass("net.minecraft.world.World");
            Object stack = services.items().materialize(snapshot);
            Object item = stackType.getMethod("func_77973_b").invoke(stack);
            Object player = actor(world, placer, snapshot);
            Object inventory=playerType.getField("field_71071_by").get(player);
            int selected=inventory.getClass().getField("field_70461_c").getInt(inventory);
            // The callback and inventory must share the same stack, just as in the original game.
            inventory.getClass().getMethod("func_70299_a",int.class,stackType).invoke(inventory,selected,stack);
            Object returned; List<LegacyPackets.Message> messages;List<String> feedback;LegacyDeviceContainers.Open device;
            try(var packets=LegacyPackets.capture(placer.id());var chat=new LegacyBlockLogic.ChatCapture();var menus=LegacyDeviceContainers.captureItem(services)) {
                returned = itemType.getMethod("func_77659_a", stackType, worldType, playerType).invoke(item, stack, world, player);
                LegacyPlayerAttributes.push(player);
                messages=packets.messages();feedback=chat.messages();device=menus.opened();
            }
            if(returned!=null&&stackType.getField("field_77994_a").getInt(returned)<=0)returned=null;
            inventory.getClass().getMethod("func_70299_a",int.class,stackType).invoke(inventory,selected,returned);
            int duration = (int) itemType.getMethod("func_77626_a", stackType).invoke(item, stack);
            var after=returned==null?null:services.items().snapshot(returned);
            boolean used = device!=null || duration > 0 || !messages.isEmpty() || !feedback.isEmpty() || after==null || !after.key().equals(snapshot.key())||after.metadata()!=snapshot.metadata()||after.count()!=snapshot.count()||!after.tag().equals(snapshot.tag());
            String prompt = prompt(snapshot.key(), item);
            boolean remote = loader.loadClass("net.minecraft.world.World").getField("field_72995_K").getBoolean(world);
            LogUtils.getLogger().info("MFCL legacy use {}@{}: remote={}, duration={}, target={}", snapshot.key(), snapshot.metadata(), remote, duration, prompt);
            return new Result(used, duration, prompt, messages,feedback,device);
            }
        });
    }
    /** A held scanner's current target, surfaced as text because no modern scanning overlay exists yet. */
    private String prompt(String key, Object item) {
        try {
            var scanner = services.scanners().forItem(key);
            return scanner.isPresent() ? scanner.get().prompt(item) : null;
        } catch (Throwable absent) { return null; }
    }
    /**
     * Names a scan target exactly as the original thaumometer lens does: a dropped item shows its stack's display
     * name rather than EntityItem's "item.item.&lt;name&gt;" entity name, which has no translation in 1.7.10.
     */
    public static String scanTargetName(ClassLoader loader, Object entity, int id, int meta) throws Exception {
        Class<?> itemType = loader.loadClass("net.minecraft.item.Item"), stackType = loader.loadClass("net.minecraft.item.ItemStack");
        Object stack = null;
        if (entity != null) {
            if (!loader.loadClass("net.minecraft.entity.item.EntityItem").isInstance(entity)) return (String) entity.getClass().getMethod("func_70005_c_").invoke(entity);
            stack = entity.getClass().getMethod("func_92059_d").invoke(entity);
        } else {
            Object scanned = itemType.getMethod("func_150899_d", int.class).invoke(null, id);
            if (scanned != null) stack = stackType.getConstructor(itemType, int.class, int.class).newInstance(scanned, 1, meta);
        }
        if (stack == null || stackType.getMethod("func_77973_b").invoke(stack) == null) return null;
        try { return (String) stackType.getMethod("func_82833_r").invoke(stack); }
        catch (InvocationTargetException failure) {
            return (String) itemType.getMethod("func_77653_i", stackType).invoke(stackType.getMethod("func_77973_b").invoke(stack), stack);
        }
    }
    public Scan usingTick(Object world, LegacyTiles.Placer placer, LegacyClientData.Stack snapshot, int remaining) throws Exception {
        return usingTickResult(world,placer,snapshot,remaining).scan();
    }
    public UseTick usingTickResult(Object world, LegacyTiles.Placer placer, LegacyClientData.Stack snapshot, int remaining) throws Exception {
        return services.items().inLegacy(() -> {
            try(var state=services.playerState().enter(world)) {
            var loader = services.loader();
            Class<?> stackType = loader.loadClass("net.minecraft.item.ItemStack");
            Class<?> itemType = loader.loadClass("net.minecraft.item.Item");
            Class<?> playerType = loader.loadClass("net.minecraft.entity.player.EntityPlayer");
            Object stack = services.items().materialize(snapshot);
            Object item = stackType.getMethod("func_77973_b").invoke(stack);
            Object player = actor(world, placer, snapshot);
            bindHeld(player,stack);
            playerType.getMethod("func_71008_a",stackType,int.class).invoke(player,stack,remaining);
            var scanner = services.scanners().forItem(snapshot.key()).orElse(null);
            Object request;List<LegacyPackets.Message> outgoing;
            LegacyWorlds.beginEntityQueries(world);
            try(var packets=LegacyPackets.captureScan(placer.id(),scanner==null?packet->false:scanner::completion)) {
                itemType.getMethod("onUsingTick", stackType, playerType, int.class).invoke(item, stack, player, remaining);
                LegacyWorlds.commitEntityMotion(world);
                request=packets.scanRequest();
                outgoing=packets.messages();
            }finally{LegacyWorlds.clearEntityQueries(world);}
            if (remaining <= 6) LogUtils.getLogger().info("MFCL legacy use tick {}@{}: remaining={}, target={}",
                    snapshot.key(), snapshot.metadata(), remaining, prompt(snapshot.key(), item));
            // Only the original successful completion branch emits this packet; clearing startScan also happens on cancellation.
            return new UseTick(request==null?null:scanner.read(request),(boolean)playerType.getMethod("func_71039_bw").invoke(player),outgoing);
            }
        });
    }
    private void bindHeld(Object player,Object stack) throws Exception {
        Object inventory=services.loader().loadClass("net.minecraft.entity.player.EntityPlayer").getField("field_71071_by").get(player);
        int selected=inventory.getClass().getField("field_70461_c").getInt(inventory);
        inventory.getClass().getMethod("func_70299_a",int.class,services.loader().loadClass("net.minecraft.item.ItemStack")).invoke(inventory,selected,stack);
    }
    public boolean inventoryUpdates(LegacyClientData.Stack snapshot) throws Exception {
        return services.items().inLegacy(()->{
            Boolean known=inventoryUpdates.get(snapshot.key());if(known!=null)return known;
            var loader=services.loader();var stackType=loader.loadClass("net.minecraft.item.ItemStack");var itemType=loader.loadClass("net.minecraft.item.Item");
            Object item=stackType.getMethod("func_77973_b").invoke(services.items().materialize(snapshot));
            boolean updates=item.getClass().getMethod("func_77663_a",stackType,loader.loadClass("net.minecraft.world.World"),loader.loadClass("net.minecraft.entity.Entity"),int.class,boolean.class).getDeclaringClass()!=itemType;
            inventoryUpdates.put(snapshot.key(),updates);return updates;
        });
    }
    /** What vanilla combat and enchanting need to know: original ItemSword, ItemTool tool classes, getItemEnchantability. */
    public record Equipment(boolean sword,boolean tool,Set<String> toolClasses,int enchantability){}
    private final Map<String,Equipment> equipment=new HashMap<>();
    public Equipment equipment(LegacyClientData.Stack snapshot) throws Exception {
        return services.items().inLegacy(()->{
            var known=equipment.get(snapshot.key());if(known!=null)return known;
            var loader=services.loader();var stackType=loader.loadClass("net.minecraft.item.ItemStack");var itemType=loader.loadClass("net.minecraft.item.Item");
            Object stack=services.items().materialize(snapshot);Object item=stackType.getMethod("func_77973_b").invoke(stack);
            boolean sword=loader.loadClass("net.minecraft.item.ItemSword").isInstance(item),tool=loader.loadClass("net.minecraft.item.ItemTool").isInstance(item);
            var classes=new HashSet<String>();
            if(tool)for(Object name:(Set<?>)itemType.getMethod("getToolClasses",stackType).invoke(item,stack))classes.add((String)name);
            var result=new Equipment(sword,tool,Set.copyOf(classes),(int)itemType.getMethod("func_77619_b").invoke(item));
            equipment.put(snapshot.key(),result);return result;
        });
    }
    /** Executes onUpdate on the actual inventory stack, including passive wand-rod charging. */
    public List<LegacyPackets.Message> inventoryTick(Object world,LegacyTiles.Placer placer,int slot,int ticks,boolean selected) throws Exception {
        return services.items().inLegacy(()->{try(var state=services.playerState().enter(world);var outgoing=LegacyPackets.capture(placer.id())) {
            var loader=services.loader();Object player=actor(world,placer,null);var stackType=loader.loadClass("net.minecraft.item.ItemStack");
            Object inventory=loader.loadClass("net.minecraft.entity.player.EntityPlayer").getField("field_71071_by").get(player);
            Object stack=inventory.getClass().getMethod("func_70301_a",int.class).invoke(inventory,slot);
            if(stack!=null){
                var entity=loader.loadClass("net.minecraft.entity.Entity");entity.getField("field_70173_aa").setInt(player,ticks);
                Object item=stackType.getMethod("func_77973_b").invoke(stack);
                loader.loadClass("net.minecraft.item.Item").getMethod("func_77663_a",stackType,loader.loadClass("net.minecraft.world.World"),entity,int.class,boolean.class).invoke(item,stack,world,player,slot,selected);
                LegacyPlayerAttributes.push(player);
            }
            return outgoing.messages();
        }});
    }
    public Object clientActor(Object world,LegacyTiles.Placer placer,LegacyClientData.Stack snapshot) throws Exception {return actor(world,placer,snapshot);}
    /** Restores previously persisted old player data into the cached actor before any legacy logic reads it. */
    public void loadPersisted(Object world, LegacyTiles.Placer placer, byte[] data) throws Exception {
        services.items().inLegacy(() -> {try(var state=services.playerState().enter(world)) {
            Object player=actor(world,placer,null);if(data!=null&&data.length!=0)services.playerData().restore(player,data);return null;
        }});
    }
    /** {@link #savePersisted}, or null while the player has no actor in that world. */
    public byte[] savePersistedIfPresent(Object world,LegacyTiles.Placer placer) throws Exception {
        synchronized(services.items()){if(!actors.containsKey(System.identityHashCode(world)+"|"+placer.id()))return null;}
        return savePersisted(world,placer);
    }
    public byte[] savePersisted(Object world,LegacyTiles.Placer placer) throws Exception {
        return services.items().inLegacy(()->{try(var state=services.playerState().enter(world)){return services.playerData().read(actor(world,placer,null));}});
    }
    /**
     * Lets the held item's scanner adapter re-evaluate a client-reported scan against the authoritative (server) old
     * player. The caller passes the player's previously persisted old entity data (or null) and gets the updated bytes
     * back, so research survives across sessions.
     */
    public AppliedScan applyScan(Object world, LegacyTiles.Placer placer, LegacyClientData.Stack held, Scan scan, String prefix, byte[] priorData) throws Exception {
        var scanner = services.scanners().forItem(held.key()).orElseThrow(() -> new IllegalArgumentException("Not a held scanner: " + held.key()));
        return services.items().inLegacy(() -> {
            try(var knowledge=services.playerState().enter(world);var packets=LegacyPackets.capture(placer.id())) {
            Object player = actor(world, placer, held);
            if (priorData != null && priorData.length != 0) services.playerData().restore(player, priorData);
            boolean accepted=scanner.complete(world,player,services.items().materialize(held),scan,prefix);
            services.playerState().synchronize(player);
            return new AppliedScan(accepted,services.playerData().read(player),packets.messages());
            }
        });
    }
    public List<LegacyPackets.Message> sync(Object world,LegacyTiles.Placer placer) throws Exception {
        return services.items().inLegacy(()->{try(var state=services.playerState().enter(world);var packets=LegacyPackets.capture(placer.id())) {
            services.playerState().synchronize(actor(world,placer,null));return packets.messages();
        }});
    }
    public Finished release(Object world, LegacyTiles.Placer placer, LegacyClientData.Stack snapshot, int remaining) throws Exception {
        return services.items().inLegacy(() -> {
            try(var state=services.playerState().enter(world);var packets=LegacyPackets.capture(placer.id());var chat=new LegacyBlockLogic.ChatCapture()) {
            var loader = services.loader();
            Class<?> stackType = loader.loadClass("net.minecraft.item.ItemStack");
            Class<?> itemType = loader.loadClass("net.minecraft.item.Item");
            Class<?> playerType = loader.loadClass("net.minecraft.entity.player.EntityPlayer");
            Class<?> worldType = loader.loadClass("net.minecraft.world.World");
            Object stack = services.items().materialize(snapshot);
            Object item = stackType.getMethod("func_77973_b").invoke(stack);
            Object player = actor(world, placer, snapshot);
            bindHeld(player,stack);
            itemType.getMethod("func_77615_a", stackType, worldType, playerType, int.class).invoke(item, stack, world, player, remaining);
            LegacyPlayerBody.push(player);LegacyPlayerAttributes.push(player);
            return new Finished(packets.messages(),chat.messages());
            }
        });
    }
    public record Finished(List<LegacyPackets.Message> messages,List<String> feedback) {}
    /** Old onEaten: the held-use timer ran out. The returned stack replaces the held one (empty bottle, bowl, nothing). */
    public Finished finish(Object world, LegacyTiles.Placer placer, LegacyClientData.Stack snapshot) throws Exception {
        return services.items().inLegacy(() -> {
            try(var state=services.playerState().enter(world)) {
            var loader = services.loader();
            Class<?> stackType = loader.loadClass("net.minecraft.item.ItemStack");
            Class<?> itemType = loader.loadClass("net.minecraft.item.Item");
            Class<?> playerType = loader.loadClass("net.minecraft.entity.player.EntityPlayer");
            Class<?> worldType = loader.loadClass("net.minecraft.world.World");
            Object stack = services.items().materialize(snapshot);
            Object item = stackType.getMethod("func_77973_b").invoke(stack);
            Object player = actor(world, placer, snapshot);
            Object inventory=playerType.getField("field_71071_by").get(player);
            int selected=inventory.getClass().getField("field_70461_c").getInt(inventory);
            inventory.getClass().getMethod("func_70299_a",int.class,stackType).invoke(inventory,selected,stack);
            Object returned;List<LegacyPackets.Message> messages;List<String> feedback;
            try(var packets=LegacyPackets.capture(placer.id());var chat=new LegacyBlockLogic.ChatCapture()) {
                returned = itemType.getMethod("func_77654_b", stackType, worldType, playerType).invoke(item, stack, world, player);
                LegacyPlayerAttributes.push(player);
                messages=packets.messages();feedback=chat.messages();
            }
            if(returned!=null&&stackType.getField("field_77994_a").getInt(returned)<=0)returned=null;
            // EntityPlayer.onItemUseFinish always stores the result in the current slot (null once used up).
            inventory.getClass().getMethod("func_70299_a",int.class,stackType).invoke(inventory,selected,returned);
            return new Finished(messages,feedback);
            }
        });
    }
    /** Outcome of an old ArrowNockEvent/ArrowLooseEvent: whether a mod took over the shot and whether it wore the bow. */
    public record BowEvent(boolean canceled, boolean bowDamaged) {}
    /**
     * Posts the old Forge bow event (charge &lt; 0: ArrowNockEvent, otherwise ArrowLooseEvent) on the old EVENT_BUS.
     * The bow sits in the held slot carrying the host's bow enchantments (old ids 48-51), and is restored afterwards so
     * inventory collection never rewrites the host bow; only its wear is reported.
     */
    public BowEvent bowEvent(Object world, LegacyTiles.Placer placer, LegacyClientData.Stack bow, Map<Integer,Integer> enchantments, int charge) throws Exception {
        return services.items().inLegacy(() -> {
            try(var state=services.playerState().enter(world)) {
            var loader = services.loader();
            Class<?> stackType = loader.loadClass("net.minecraft.item.ItemStack"), playerType = loader.loadClass("net.minecraft.entity.player.EntityPlayer");
            Class<?> enchantment = loader.loadClass("net.minecraft.enchantment.Enchantment");
            Object player = actor(world, placer, bow), stack = services.items().materialize(bow);
            Object[] known = (Object[]) enchantment.getField("field_77331_b").get(null);
            for (var entry : enchantments.entrySet()) if (entry.getKey() < known.length && known[entry.getKey()] != null) stackType.getMethod("func_77966_a", enchantment, int.class).invoke(stack, known[entry.getKey()], entry.getValue());
            bindHeld(player, stack);
            int damage = (int) stackType.getMethod("func_77960_j").invoke(stack);
            Object event = charge < 0 ? loader.loadClass("net.minecraftforge.event.entity.player.ArrowNockEvent").getConstructor(playerType, stackType).newInstance(player, stack)
                    : loader.loadClass("net.minecraftforge.event.entity.player.ArrowLooseEvent").getConstructor(playerType, stackType, int.class).newInstance(player, stack, charge);
            Object bus = loader.loadClass("net.minecraftforge.common.MinecraftForge").getField("EVENT_BUS").get(null);
            boolean canceled = (boolean) bus.getClass().getMethod("post", loader.loadClass("cpw.mods.fml.common.eventhandler.Event")).invoke(bus, event);
            boolean worn = stackType.getField("field_77994_a").getInt(stack) <= 0 || (int) stackType.getMethod("func_77960_j").invoke(stack) != damage;
            bindHeld(player, services.items().materialize(bow));
            return new BowEvent(canceled, worn);
            }
        });
    }
    public int useDuration(LegacyClientData.Stack snapshot) throws Exception {
        return services.items().inLegacy(() -> {
            var loader = services.loader();
            Class<?> stackType = loader.loadClass("net.minecraft.item.ItemStack");
            Class<?> itemType = loader.loadClass("net.minecraft.item.Item");
            Object stack = services.items().materialize(snapshot);
            return (int) itemType.getMethod("func_77626_a", stackType).invoke(stackType.getMethod("func_77973_b").invoke(stack), stack);
        });
    }
    /** Original Item.getIsRepairable: whether {@code material} repairs {@code target} in an anvil. */
    public boolean repairable(LegacyClientData.Stack target, LegacyClientData.Stack material) throws Exception {
        return services.items().inLegacy(() -> {
            Class<?> stackType = services.loader().loadClass("net.minecraft.item.ItemStack");
            Object stack = services.items().materialize(target);
            return (boolean) services.loader().loadClass("net.minecraft.item.Item").getMethod("func_82789_a", stackType, stackType)
                    .invoke(stackType.getMethod("func_77973_b").invoke(stack), stack, services.items().materialize(material));
        });
    }
    /** Original ItemArmor.getColor (dyed robes/leather), RGB. */
    public int armorColor(LegacyClientData.Stack worn) throws Exception {
        return services.items().inLegacy(() -> {
            Class<?> stackType = services.loader().loadClass("net.minecraft.item.ItemStack");
            Object stack = services.items().materialize(worn);
            return (int) services.loader().loadClass("net.minecraft.item.ItemArmor").getMethod("func_82814_b", stackType).invoke(stackType.getMethod("func_77973_b").invoke(stack), stack);
        });
    }
    /** Host movement state an original onArmorTick may read and change. */
    public record Body(double motionX, double motionY, double motionZ, float fallDistance, boolean onGround, boolean inWater,
                       float forward, float strafe, int ticksExisted) {}
    public record Effect(int id, int duration, int amplifier, boolean ambient) {}
    /** The worn stack, the movement state and the potion effects the original applied this tick. */
    public record ArmorTick(LegacyClientData.Stack stack, Body body, List<Effect> effects) {}
    public ArmorTick armorTick(Object world, LegacyTiles.Placer placer, LegacyClientData.Stack worn, Body body) throws Exception {
        return services.items().inLegacy(() -> {
            var loader = services.loader();
            Class<?> entity = loader.loadClass("net.minecraft.entity.Entity"), living = loader.loadClass("net.minecraft.entity.EntityLivingBase");
            Class<?> stackType = loader.loadClass("net.minecraft.item.ItemStack"), itemType = loader.loadClass("net.minecraft.item.Item");
            Object player = actor(world, placer, null);
            entity.getField("field_70159_w").setDouble(player, body.motionX()); entity.getField("field_70181_x").setDouble(player, body.motionY());
            entity.getField("field_70179_y").setDouble(player, body.motionZ()); entity.getField("field_70143_R").setFloat(player, body.fallDistance());
            entity.getField("field_70122_E").setBoolean(player, body.onGround());
            var water = entity.getDeclaredField("field_70171_ac"); water.setAccessible(true); water.setBoolean(player, body.inWater());
            living.getField("field_70701_bs").setFloat(player, body.forward()); living.getField("field_70702_br").setFloat(player, body.strafe());
            entity.getField("field_70173_aa").setInt(player, body.ticksExisted());
            // Only this tick's effects are reported back; the host owns the player's real effect list.
            var potions = living.getDeclaredField("field_70713_bf"); potions.setAccessible(true);
            @SuppressWarnings("unchecked") Map<Object, Object> active = (Map<Object, Object>) potions.get(player);
            if (active == null) { active = new HashMap<>(); potions.set(player, active); }
            var kept = new HashMap<>(active);
            active.clear();
            Object stack = services.items().materialize(worn);
            itemType.getMethod("onArmorTick", loader.loadClass("net.minecraft.world.World"), loader.loadClass("net.minecraft.entity.player.EntityPlayer"), stackType)
                    .invoke(stackType.getMethod("func_77973_b").invoke(stack), world, player, stack);
            var effects = new ArrayList<Effect>();
            Class<?> effect = loader.loadClass("net.minecraft.potion.PotionEffect");
            for (Object value : active.values()) effects.add(new Effect((int) effect.getMethod("func_76456_a").invoke(value), (int) effect.getMethod("func_76459_b").invoke(value),
                    (int) effect.getMethod("func_76458_c").invoke(value), (boolean) effect.getMethod("func_82720_e").invoke(value)));
            active.clear(); active.putAll(kept);
            var after = new Body(entity.getField("field_70159_w").getDouble(player), entity.getField("field_70181_x").getDouble(player), entity.getField("field_70179_y").getDouble(player),
                    entity.getField("field_70143_R").getFloat(player), body.onGround(), body.inWater(), body.forward(), body.strafe(), body.ticksExisted());
            int count = stackType.getField("field_77994_a").getInt(stack);
            return new ArmorTick(count <= 0 ? null : services.items().snapshot(stack), after, List.copyOf(effects));
        });
    }
    /** Original EnumAction name (none, eat, drink, block, bow) that drives the held-use pose. */
    public String useAction(LegacyClientData.Stack snapshot) throws Exception {
        return services.items().inLegacy(() -> {
            var loader = services.loader();
            Class<?> stackType = loader.loadClass("net.minecraft.item.ItemStack");
            Object stack = services.items().materialize(snapshot);
            Object action = loader.loadClass("net.minecraft.item.Item").getMethod("func_77661_b", stackType).invoke(stackType.getMethod("func_77973_b").invoke(stack), stack);
            return action == null ? "none" : ((Enum<?>) action).name().toLowerCase(java.util.Locale.ROOT);
        });
    }
    private Object actor(Object world, LegacyTiles.Placer placer, LegacyClientData.Stack snapshot) throws Exception {
        var loader = services.loader();
        // Thaumcraft's held-use tick compares player names with `==`, so on the client the actor must be the very
        // old client player that Minecraft.getMinecraft().thePlayer exposes, not an equal-but-distinct instance.
        if (loader.loadClass("net.minecraft.world.World").getField("field_72995_K").getBoolean(world)) {
            services.containers().preparePlayer();
            Class<?> mc = loader.loadClass("net.minecraft.client.Minecraft");
            Object instance = mc.getMethod("func_71410_x").invoke(null);
            Object client = mc.getField("field_71439_g").get(instance);
            if (client != null) { refresh(client, world, placer, snapshot);mc.getField("field_71451_h").set(instance,client);return client; }
        }
        String key = System.identityHashCode(world) + "|" + placer.id();
        Object actor = actors.get(key);
        if (actor == null) {
            actor = services.tiles().actor(world, placer, snapshot);
            services.playerState().initialize(actor);
            // A new server actor (first use, another dimension) starts from the host's persisted old entity data.
            if (!loader.loadClass("net.minecraft.world.World").getField("field_72995_K").getBoolean(world)) services.playerData().restore(actor, placer.id());
            actors.put(key, actor);
        }
        else refresh(actor, world, placer, snapshot);
        return actor;
    }
    private void refresh(Object actor, Object world, LegacyTiles.Placer placer, LegacyClientData.Stack snapshot) throws Exception {
        var playerType=services.loader().loadClass("net.minecraft.entity.player.EntityPlayer");
        var profile=playerType.getDeclaredField("field_146106_i");profile.setAccessible(true);
        profile.set(actor,services.loader().loadClass("com.mojang.authlib.GameProfile").getConstructor(UUID.class,String.class).newInstance(placer.id(),placer.name()));
        Class<?> entity = services.loader().loadClass("net.minecraft.entity.Entity");
        // Entity queries exclude the actor by UUID; the reused client player must carry the host player's identity,
        // otherwise its own host entity is returned as the pointed target.
        var uuid=entity.getDeclaredField("field_96093_i");uuid.setAccessible(true);uuid.set(actor,placer.id());
        boolean client=services.loader().loadClass("net.minecraft.world.World").getField("field_72995_K").getBoolean(world);
        float eyeHeight = LegacyPlayerBody.eyeHeight(placer.id(), client);
        playerType.getField("eyeHeight").setFloat(actor,client?.12f:eyeHeight);
        entity.getField("field_70129_M").setFloat(actor,client?1.62f:0);
        entity.getField("field_70170_p").set(actor, world);
        entity.getField("field_71093_bK").setInt(actor,services.loader().loadClass("net.minecraft.world.WorldProvider").getField("field_76574_g").getInt(services.loader().loadClass("net.minecraft.world.World").getField("field_73011_w").get(world)));
        double oldY=placer.y()+(client?eyeHeight:0);
        entity.getField("field_70165_t").setDouble(actor, placer.x()); entity.getField("field_70163_u").setDouble(actor, oldY); entity.getField("field_70161_v").setDouble(actor, placer.z());
        for(String field:new String[]{"field_70169_q","field_70142_S"})entity.getField(field).setDouble(actor,placer.x());
        for(String field:new String[]{"field_70167_r","field_70137_T"})entity.getField(field).setDouble(actor,oldY);
        for(String field:new String[]{"field_70166_s","field_70136_U"})entity.getField(field).setDouble(actor,placer.z());
        entity.getField("field_70177_z").setFloat(actor, placer.yaw()); entity.getField("field_70125_A").setFloat(actor, placer.pitch());
        entity.getField("field_70126_B").setFloat(actor,placer.yaw());entity.getField("field_70127_C").setFloat(actor,placer.pitch());
        Object capabilities=playerType.getField("field_71075_bZ").get(actor);
        capabilities.getClass().getField("field_75098_d").setBoolean(capabilities,placer.creative());
        LegacyPlayerFlight.refresh(actor);
        LegacyPlayerAttributes.pull(actor);
        LegacyPlayerBody.pull(actor, client);
        Object watcher=entity.getMethod("func_70096_w").invoke(actor);
        byte flags=(byte)watcher.getClass().getMethod("func_75683_a",int.class).invoke(watcher,0);
        watcher.getClass().getMethod("func_75692_b",int.class,Object.class).invoke(watcher,0,(byte)(placer.sneaking()?flags|2:flags&~2));
        LegacyPlayerArmor.apply(services,actor,placer.id(),client);
        Class<?> sp=services.loader().loadClass("net.minecraft.client.entity.EntityPlayerSP");
        if(sp.isInstance(actor)) {
            // A real client player always has MovementInput; original GUIs read it too (filing cabinet sorting).
            Object movement=sp.getField("field_71158_b").get(actor);
            if(movement==null){movement=services.loader().loadClass("net.minecraft.util.MovementInput").getConstructor().newInstance();sp.getField("field_71158_b").set(actor,movement);}
            movement.getClass().getField("field_78899_d").setBoolean(movement,placer.sneaking());
            for(String name:new String[]{"field_71155_g","field_71164_i"})sp.getField(name).setFloat(actor,placer.pitch());
            for(String name:new String[]{"field_71154_f","field_71163_h"})sp.getField(name).setFloat(actor,placer.yaw());
        }
        // Range/ray-trace helpers read the bounding box; the reused host player may never have had one built.
        Object box = services.loader().loadClass("net.minecraft.util.AxisAlignedBB").getMethod("func_72330_a", double.class, double.class, double.class, double.class, double.class, double.class)
                .invoke(null, placer.x() - 0.3, placer.y(), placer.z() - 0.3, placer.x() + 0.3, placer.y() + 1.8, placer.z() + 0.3);
        Field bbox = entity.getDeclaredField("field_70121_D"); bbox.setAccessible(true); bbox.set(actor, box);
        entity.getField("field_70130_N").setFloat(actor, 0.6f); entity.getField("field_70131_O").setFloat(actor, 1.8f);
        var inventory = services.loader().loadClass("net.minecraft.entity.player.EntityPlayer").getField("field_71071_by").get(actor);
        if (inventory != null && snapshot != null) {
            int selected = inventory.getClass().getField("field_70461_c").getInt(inventory);
            inventory.getClass().getMethod("func_70299_a", int.class, services.loader().loadClass("net.minecraft.item.ItemStack"))
                    .invoke(inventory, selected, services.items().materialize(snapshot));
        }
    }
}
