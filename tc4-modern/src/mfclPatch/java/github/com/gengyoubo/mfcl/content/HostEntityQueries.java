package github.com.gengyoubo.mfcl.content;

import github.com.gengyoubo.mfcl.bridge.HostWorldAccess;
import github.com.gengyoubo.mfcl.bridge.LegacyClientData;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import java.util.*;

/** Snapshots host entities for legacy AABB queries without exposing native entities across class loaders. */
public final class HostEntityQueries {
    private static final java.lang.reflect.Field PICKUP_DELAY=net.minecraftforge.fml.util.ObfuscationReflectionHelper.findField(ItemEntity.class,"f_31986_");
    private static final ReentrantEntityQuery PUSHABLE_QUERY = new ReentrantEntityQuery();
    private HostEntityQueries() {}
    public static List<HostWorldAccess.Entity> query(Level level,double minX,double minY,double minZ,double maxX,double maxY,double maxZ) {
        return level.getEntities((Entity)null,new AABB(minX,minY,minZ,maxX,maxY,maxZ)).stream().map(entity->snapshot(level,entity)).filter(Objects::nonNull).toList();
    }
    /** One host entity as the legacy world sees it; null for a dropped item whose stack has no original form. */
    public static HostWorldAccess.Entity snapshot(Level level,Entity entity) {
        String type=entity instanceof Player?(level.isClientSide?"net.minecraft.client.entity.EntityClientPlayerMP":"net.minecraft.entity.player.EntityPlayerMP"):
                entity instanceof ItemEntity?"net.minecraft.entity.item.EntityItem":entity instanceof AbstractHorse?"net.minecraft.entity.passive.EntityHorse":
                entity instanceof LivingEntity?"net.minecraft.entity.EntityLiving":"net.minecraft.entity.Entity";
        if (!(entity instanceof Player) && ModernContentBridge.active() != null) {
            String represented = ModernContentBridge.active().hostRepresentations().entityClass(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString());
            // Vanilla animals resolve to their old class (EntityCow...) so EntityAnimal queries such as the lamp of fertility find them.
            if (represented == null && entity instanceof LivingEntity && net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getNamespace().equals("minecraft")) {
                ModernContentBridge.active().hostRepresentations().entity(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString());
                represented = ModernContentBridge.active().hostRepresentations().entityClass(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString());
            }
            if (represented != null) type = represented;
        }
        LegacyClientData.Stack item=null;int pickupDelay=0;
        if(entity instanceof ItemEntity drop) {
            String original=drop.getPersistentData().getString(LegacyItemDrops.ORIGINAL_CLASS);
            if(!original.isEmpty())type=original;
            try {if(ModernContentBridge.active()!=null)item=ModernContentBridge.active().gameplayStack(drop.getItem());}
            catch(UnsupportedOperationException unmapped) { }
            catch(Exception failure) {throw new IllegalStateException("Cannot snapshot dropped item for the legacy world",failure);}
            // An unsupported stack must not become EntityItem's vanilla fallback stone stack.
            if(item==null)return null;
            try {pickupDelay=PICKUP_DELAY.getInt(drop);}
            catch(IllegalAccessException failure) {throw new IllegalStateException(failure);}
        }
        var motion=entity.getDeltaMovement();
        return new HostWorldAccess.Entity(entity.getUUID(),type,entity instanceof Player?entity.getName().getString():entity.hasCustomName()?entity.getCustomName().getString():"",entity.getX(),entity.getY(),entity.getZ(),motion.x,motion.y,motion.z,entity.fallDistance,entity.getBbWidth(),entity.getBbHeight(),entity.isShiftKeyDown(),PUSHABLE_QUERY.get(entity,entity::isPushable),entity.getId(),item,
                entity instanceof AgeableMob ageable?ageable.getAge():0,entity instanceof net.minecraft.world.entity.animal.Animal animal?animal.getInLoveTime():0,
                entity instanceof Player player?new HostWorldAccess.Abilities(player.getAbilities().instabuild,player.getAbilities().invulnerable):null,
                entity instanceof LivingEntity living?living.getHealth():null,entityData(entity),pickupDelay);
    }
    private static byte[] entityData(Entity entity) {
        if(entity.getPersistentData().isEmpty())return null;
        try(var output=new java.io.ByteArrayOutputStream()) {
            net.minecraft.nbt.NbtIo.writeCompressed(entity.getPersistentData(),output);return output.toByteArray();
        } catch(java.io.IOException failure){throw new IllegalStateException("Cannot snapshot Forge entity data",failure);}
    }
    public static void apply(Level level,HostWorldAccess.Motion motion) {
        if(!(level instanceof net.minecraft.server.level.ServerLevel server))return;
        Entity entity=server.getEntity(motion.id());if(entity==null||entity.isRemoved())return;
        entity.setDeltaMovement(motion.x(),motion.y(),motion.z());entity.fallDistance=motion.fallDistance();entity.hurtMarked=true;
        // Host setInLove also broadcasts the heart particles the old setEntityState(18) would have sent.
        if(motion.love()>0&&entity instanceof net.minecraft.world.entity.animal.Animal animal&&!animal.isInLove())animal.setInLove(null);
    }
}
