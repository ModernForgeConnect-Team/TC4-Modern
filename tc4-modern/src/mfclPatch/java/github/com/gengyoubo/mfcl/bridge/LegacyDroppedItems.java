package github.com.gengyoubo.mfcl.bridge;

/** Original dropped-item lifetime and Forge expiration callbacks; native entities retain their own physics. */
public final class LegacyDroppedItems {
    private final LegacyServices services;
    public LegacyDroppedItems(LegacyServices services) { this.services = services; }

    public int lifespan(Object world, LegacyClientData.Stack value) throws Exception {
        return services.items().inLegacy(() -> {
            var loader = services.loader(); var stack = loader.loadClass("net.minecraft.item.ItemStack");
            Object original = services.items().materialize(value);
            Object item = stack.getMethod("func_77973_b").invoke(original);
            return (int) loader.loadClass("net.minecraft.item.Item").getMethod("getEntityLifespan", stack, loader.loadClass("net.minecraft.world.World"))
                    .invoke(item, original, world);
        });
    }

    public record Expiration(boolean canceled, int extraLife, boolean removed, LegacyClientData.Stack stack) {}
    public record Toss(boolean canceled,boolean removed,LegacyClientData.Stack stack,byte[] entityData) {}
    /** Let original Forge subscribers attach provenance and apply their normal toss restrictions. */
    public Toss toss(Object world,HostWorldAccess.Entity snapshot,LegacyTiles.Placer placer) throws Exception {
        return services.items().inLegacy(()->{
            try(var state=services.playerState().enter(world)) {
                var loader=services.loader();var type=loader.loadClass("net.minecraft.entity.item.EntityItem");
                Object entity=LegacyEntityAccess.mirror(services.items(),world,snapshot,null);
                Object player=services.tiles().actor(world,placer,null);
                var eventType=loader.loadClass("net.minecraftforge.event.entity.item.ItemTossEvent");
                Object event=eventType.getConstructor(type,loader.loadClass("net.minecraft.entity.player.EntityPlayer")).newInstance(entity,player);
                Object bus=loader.loadClass("net.minecraftforge.common.MinecraftForge").getField("EVENT_BUS").get(null);
                boolean canceled=(boolean)bus.getClass().getMethod("post",loader.loadClass("cpw.mods.fml.common.eventhandler.Event")).invoke(bus,event);
                Object stack=type.getMethod("func_92059_d").invoke(entity);
                boolean removed=loader.loadClass("net.minecraft.entity.Entity").getField("field_70128_L").getBoolean(entity)
                        ||stack==null||stack.getClass().getField("field_77994_a").getInt(stack)<=0;
                Object data=type.getMethod("getEntityData").invoke(entity);
                byte[] bytes=(byte[])loader.loadClass("net.minecraft.nbt.CompressedStreamTools").getMethod("func_74798_a",loader.loadClass("net.minecraft.nbt.NBTTagCompound")).invoke(null,data);
                return new Toss(canceled,removed,removed?null:services.items().snapshot(stack),bytes);
            }
        });
    }
    public Expiration expire(Object world, HostWorldAccess.Entity snapshot, int age, int lifespan, int extraLife) throws Exception {
        return services.items().inLegacy(() -> {
            var loader = services.loader(); var type = loader.loadClass("net.minecraft.entity.item.EntityItem");
            Object entity = LegacyEntityAccess.mirror(services.items(), world, snapshot, null);
            type.getField("field_70292_b").setInt(entity, age); type.getField("lifespan").setInt(entity, lifespan);
            var eventType = loader.loadClass("net.minecraftforge.event.entity.item.ItemExpireEvent");
            Object event = eventType.getConstructor(type, int.class).newInstance(entity, extraLife);
            Object bus = loader.loadClass("net.minecraftforge.common.MinecraftForge").getField("EVENT_BUS").get(null);
            boolean canceled = (boolean) bus.getClass().getMethod("post", loader.loadClass("cpw.mods.fml.common.eventhandler.Event")).invoke(bus, event);
            var motion = LegacyEntityAccess.motion(loader, entity, snapshot.id());
            if (motion.x() != snapshot.motionX() || motion.y() != snapshot.motionY() || motion.z() != snapshot.motionZ() || motion.fallDistance() != snapshot.fallDistance())
                LegacyWorlds.hostOrNull(world).applyMotion(motion);
            Object stack = type.getMethod("func_92059_d").invoke(entity);
            boolean removed = loader.loadClass("net.minecraft.entity.Entity").getField("field_70128_L").getBoolean(entity)
                    || stack == null || stack.getClass().getField("field_77994_a").getInt(stack) <= 0;
            return new Expiration(canceled, eventType.getField("extraLife").getInt(event), removed, removed ? null : services.items().snapshot(stack));
        });
    }
}
