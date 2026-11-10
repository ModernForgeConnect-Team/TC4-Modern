package github.com.gengyoubo.mfcl.compat.thaumcraft;

import github.com.gengyoubo.mfcl.bridge.*;
import github.com.gengyoubo.mfcl.content.ModernContentBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

/** Dispatch TC4's original FillBucketEvent after a legacy liquid-aware ray trace. */
public final class ThaumcraftBucketInteraction {
    private static final java.util.concurrent.atomic.AtomicBoolean INSTALLED=new java.util.concurrent.atomic.AtomicBoolean();
    private ThaumcraftBucketInteraction() {}
    static void install(LegacyServices services){
        // Delay this sibling PlayerInteractEvent listener until Forge has finished parallel mod construction.
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.server.ServerAboutToStartEvent startup)->{
            if(INSTALLED.compareAndSet(false,true))MinecraftForge.EVENT_BUS.addListener((PlayerInteractEvent.RightClickItem event)->{
            if(!(event.getEntity() instanceof ServerPlayer player)||!event.getItemStack().is(Items.BUCKET))return;
            try {if(fill(services,player,event.getHand())){event.setCanceled(true);event.setCancellationResult(InteractionResult.SUCCESS);}}
            catch(Exception failure){com.mojang.logging.LogUtils.getLogger().error("MFCL TC4 bucket pickup failed",failure);}
            });
        });
    }
    public static boolean fill(LegacyServices services,ServerPlayer player,InteractionHand hand) throws Exception {
        var held=player.getItemInHand(hand);if(!held.is(Items.BUCKET))return false;
        var level=player.serverLevel();var content=ModernContentBridge.active();var eye=player.getEyePosition();
        // Same server-side block reach used by Forge, including reach modifiers.
        double reach=player.getAttributeValue(net.minecraftforge.common.ForgeMod.BLOCK_REACH.get());
        var end=eye.add(player.getViewVector(1).scale(reach));var world=services.worlds().bound(level);
        return content.logicTransaction(level,player.blockPosition(),()->services.items().inLegacy(()->{
            var loader=services.loader();var vec=loader.loadClass("net.minecraft.util.Vec3");
            var make=vec.getMethod("func_72443_a",double.class,double.class,double.class);
            Object start=make.invoke(null,eye.x,eye.y,eye.z),finish=make.invoke(null,end.x,end.y,end.z);
            Object hit=LegacyWorlds.rayTrace(world,start,finish,true,false,false);if(hit==null)return false;
            var type=loader.loadClass("net.minecraft.util.MovingObjectPosition");
            if(!String.valueOf(type.getField("field_72313_a").get(hit)).equals("BLOCK"))return false;
            int x=type.getField("field_72311_b").getInt(hit),y=type.getField("field_72312_c").getInt(hit),z=type.getField("field_72309_d").getInt(hit),side=type.getField("field_72310_e").getInt(hit);
            var pos=new BlockPos(x,y,z);if(!level.mayInteract(player,pos)||!player.mayUseItemAt(pos,Direction.from3DDataValue(side),held))return false;
            var state=level.getBlockState(pos);
            if(state.getBlock()!=content.blocks().get("Thaumcraft:blockFluidPure")&&state.getBlock()!=content.blocks().get("Thaumcraft:blockFluidDeath"))return false;
            var original=content.legacyBlock(state);
            if(original.metadata()!=(original.legacyKey().endsWith("Pure")?0:3))return false;
            var oldPlayer=services.tiles().actor(world,new LegacyTiles.Placer(player.getUUID(),player.getName().getString(),player.getX(),player.getY(),player.getZ(),player.getYRot(),player.getXRot(),player.getAbilities().instabuild,player.isShiftKeyDown()),null);
            var oldStack=services.items().materialize(content.gameplayStack(held));
            var eventType=loader.loadClass("net.minecraftforge.event.entity.player.FillBucketEvent");
            Object event=eventType.getConstructor(loader.loadClass("net.minecraft.entity.player.EntityPlayer"),loader.loadClass("net.minecraft.item.ItemStack"),loader.loadClass("net.minecraft.world.World"),type).newInstance(oldPlayer,oldStack,world,hit);
            Object bus=loader.loadClass("net.minecraftforge.common.MinecraftForge").getField("EVENT_BUS").get(null);
            boolean canceled=(boolean)bus.getClass().getMethod("post",loader.loadClass("cpw.mods.fml.common.eventhandler.Event")).invoke(bus,event);
            Object result=eventType.getField("result").get(event);
            if(canceled||result==null||!String.valueOf(eventType.getMethod("getResult").invoke(event)).equals("ALLOW"))return false;
            var filled=content.gameplayStack(services.items().snapshot(result));
            player.setItemInHand(hand,ItemUtils.createFilledResult(held,player,filled));player.getInventory().setChanged();player.inventoryMenu.broadcastChanges();
            return true;
        }),true);
    }
}
