package github.com.gengyoubo.mfcl.content;

import github.com.gengyoubo.mfcl.bridge.LegacyDeviceContainers;
import net.minecraft.world.entity.player.Player;
import java.util.concurrent.Callable;

/** Original item/container drops join the enclosing host transaction. */
final class LegacyItemDrops {
    static final String ORIGINAL_CLASS="mfcl:original_item_entity";
    static net.minecraft.world.entity.item.ItemEntity create(ModernContentBridge content,net.minecraft.world.level.Level level,Object entity) throws Exception {
        return content.services().items().inLegacy(()->{
            var loader=content.services().loader();var type=loader.loadClass("net.minecraft.entity.Entity");
            if(type.getField("field_70128_L").getBoolean(entity))return null;
            var snapshot=content.services().items().snapshot(entity.getClass().getMethod("func_92059_d").invoke(entity));if(snapshot.count()<=0)return null;
            var result=new net.minecraft.world.entity.item.ItemEntity(level,type.getField("field_70165_t").getDouble(entity),type.getField("field_70163_u").getDouble(entity),type.getField("field_70161_v").getDouble(entity),content.gameplayStack(snapshot));
            result.setDeltaMovement(type.getField("field_70159_w").getDouble(entity),type.getField("field_70181_x").getDouble(entity),type.getField("field_70179_y").getDouble(entity));
            Object data=type.getMethod("getEntityData").invoke(entity);
            byte[] bytes=(byte[])loader.loadClass("net.minecraft.nbt.CompressedStreamTools").getMethod("func_74798_a",loader.loadClass("net.minecraft.nbt.NBTTagCompound")).invoke(null,data);
            result.getPersistentData().merge(net.minecraft.nbt.NbtIo.readCompressed(new java.io.ByteArrayInputStream(bytes)));
            // Original callbacks distinguish special ejected items from ingredients via instanceof.
            result.getPersistentData().putString(ORIGINAL_CLASS,entity.getClass().getName());
            result.setDefaultPickUpDelay();return result;
        });
    }
    /** Original block-harvest drops: nobody threw them, and the host rule applies because the old world keeps its own default rules. */
    static <T> T captureHarvest(ModernContentBridge content,net.minecraft.world.level.Level level,Callable<T> action) throws Exception {
        return content.services().items().inLegacy(()->LegacyDeviceContainers.withDrops(entity->{
            if(!level.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_DOBLOCKDROPS))return;
            var drop=create(content,level,entity);
            if(drop!=null)content.afterCommit(level,()->level.addFreshEntity(drop));
        },action));
    }
    static <T> T capture(ModernContentBridge content,Player player,Callable<T> action) throws Exception {
        return content.services().items().inLegacy(()->LegacyDeviceContainers.withDrops(entity->{
            content.afterCommit(player.level(),()->{
                try {
                    var drop=create(content,player.level(),entity);
                    if(drop!=null){drop.setThrower(player.getUUID());player.level().addFreshEntity(drop);}
                }catch(Exception failure){throw new IllegalStateException("Cannot commit original item drop",failure);}
            });
        },action));
    }
}
