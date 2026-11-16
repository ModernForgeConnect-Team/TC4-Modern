package github.com.gengyoubo.tcmodern.tc4.entity;

import github.com.gengyoubo.tcmodern.tc4.Tc4Modern;
import github.com.gengyoubo.tcmodern.tc4.init.Tc4Entities;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** ItemGolemBell.onLeftClickEntity for modern trunks, and placing a trunk the bell packed up. */
@Mod.EventBusSubscriber(modid = Tc4Modern.MOD_ID)
public final class TrunkInteraction {
    private TrunkInteraction() { }

    @SubscribeEvent public static void ring(AttackEntityEvent event) {
        if (!(event.getTarget() instanceof TravelingTrunk trunk) || !Tc4Legacy.isNamed(event.getEntity().getMainHandItem(), "golem_bell")) return;
        event.setCanceled(true);
        if (event.getEntity().level().isClientSide) trunk.spawnAnim();
        else trunk.ringAway(event.getEntity());
    }

    @SubscribeEvent(priority = EventPriority.HIGH) public static void place(PlayerInteractEvent.RightClickBlock event) {
        var stack = event.getItemStack();
        if (!stack.hasTag() || !stack.getTag().contains(TravelingTrunk.PORTABLE, 10) || !Tc4Legacy.isNamed(stack, "trunk_spawner")) return;
        // The packed trunk's contents live only in the modern payload, so the original placer must never see it.
        event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide));
        event.setCanceled(true);
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        var trunk = Tc4Entities.TRAVELING_TRUNK.get().create(level);
        if (trunk == null) return;
        var target = event.getPos().relative(event.getFace());
        trunk.moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, level.random.nextFloat() * 360.0F, 0.0F);
        trunk.setOwner(event.getEntity().getUUID());
        trunk.unpack(stack.getTag().getCompound(TravelingTrunk.PORTABLE));
        if (stack.hasCustomHoverName()) trunk.setCustomName(stack.getHoverName());
        if (!level.noCollision(trunk) || !level.addFreshEntity(trunk)) return;
        if (!event.getEntity().getAbilities().instabuild) stack.shrink(1);
        level.gameEvent(event.getEntity(), GameEvent.ENTITY_PLACE, target);
    }
}
