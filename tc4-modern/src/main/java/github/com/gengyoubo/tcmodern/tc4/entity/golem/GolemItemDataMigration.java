package github.com.gengyoubo.tcmodern.tc4.entity.golem;

import github.com.gengyoubo.tcmodern.tc4.Tc4Modern;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingEquipmentChangeEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

/** Repairs golem items saved by the earlier top-level host-NBT implementation. */
@Mod.EventBusSubscriber(modid = Tc4Modern.MOD_ID)
public final class GolemItemDataMigration {
    private GolemItemDataMigration() { }
    private static void repair(ItemStack stack) {
        if (!stack.isEmpty() && stack.hasTag()) GolemItemData.migrate(stack.getTag());
    }
    private static void repairPlayer(Player player) {
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) repair(inventory.getItem(slot));
        repair(player.containerMenu.getCarried());
        if (ModList.get().isLoaded("curios")) CuriosMigration.repair(player);
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof Player player) repairPlayer(player);
        else if (event.getEntity() instanceof ItemEntity item) repair(item.getItem());
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) { repairPlayer(event.getEntity()); }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.START) repairPlayer(event.player);
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void onEquipment(LivingEquipmentChangeEvent event) {
        repair(event.getFrom()); repair(event.getTo());
        if (event.getEntity() instanceof Player player) repairPlayer(player);
    }

    /** Loaded only when Curios is present. */
    private static final class CuriosMigration {
        private static void repair(LivingEntity entity) {
            top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(entity).ifPresent(inventory -> {
                for (var group : inventory.getCurios().values()) {
                    var stacks = group.getStacks();
                    for (int slot = 0; slot < stacks.getSlots(); slot++) GolemItemDataMigration.repair(stacks.getStackInSlot(slot));
                    var cosmetics = group.getCosmeticStacks();
                    for (int slot = 0; slot < cosmetics.getSlots(); slot++) GolemItemDataMigration.repair(cosmetics.getStackInSlot(slot));
                }
            });
        }
    }
}
