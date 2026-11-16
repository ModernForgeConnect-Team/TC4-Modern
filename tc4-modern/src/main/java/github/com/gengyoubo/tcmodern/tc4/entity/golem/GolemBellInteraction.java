package github.com.gengyoubo.tcmodern.tc4.entity.golem;

import github.com.gengyoubo.mfcl.api.LegacyMappings;
import github.com.gengyoubo.mfcl.api.LegacyStack;
import github.com.gengyoubo.tcmodern.api.TcModernApi;
import github.com.gengyoubo.tcmodern.tc4.Tc4Modern;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/** The TC4 golem bell returns the original TC4 golem item. */
@Mod.EventBusSubscriber(modid = Tc4Modern.MOD_ID)
public final class GolemBellInteraction {
    static final String PORTABLE = "tc4modern_golem";
    private GolemBellInteraction() { }
    @SubscribeEvent public static void onAttack(AttackEntityEvent event) {
        if (!(event.getTarget() instanceof ModernGolem golem)) return;
        var player = event.getEntity();
        var key = ForgeRegistries.ITEMS.getKey(player.getMainHandItem().getItem());
        if (key == null || !key.toString().equals("thaumcraft:golem_bell")) return;
        event.setCanceled(true);
        if (player.level().isClientSide || !golem.isAlive()) return;
        if (golem.ownerId() != null && !golem.ownerId().equals(player.getUUID())) return;
        var runtime = TcModernApi.runtime().orElse(null);
        if (runtime == null) return;
        var item = runtime.mappings().toHost(LegacyMappings.ITEM_STACK,
                runtime.stack(new LegacyStack("Thaumcraft:ItemGolemPlacer", golem.material().ordinal(), 1, "")), ItemStack.class).orElse(ItemStack.EMPTY);
        if (item.isEmpty()) return;
        if (player.isShiftKeyDown()) {
            var core = golem.core() >= 0 ? runtime.mappings().toHost(LegacyMappings.ITEM_STACK,
                    runtime.stack(new LegacyStack("Thaumcraft:ItemGolemCore", golem.core(), 1, "")), ItemStack.class).orElse(ItemStack.EMPTY) : ItemStack.EMPTY;
            if (golem.core() >= 0 && core.isEmpty()) return;
            var carried = golem.carriedForDisplay();
            golem.discard();
            give(player, item);
            if (!core.isEmpty()) give(player, core);
            if (!carried.isEmpty()) give(player, carried);
        } else {
            var data = new CompoundTag();
            golem.addAdditionalSaveData(data);
            item.getOrCreateTag().put(PORTABLE, data);
            if (golem.core() >= 0) item.getOrCreateTag().putByte("core", (byte)golem.core());
            if (golem.hasCustomName()) item.setHoverName(golem.getCustomName());
            golem.discard();
            give(player, item);
        }
    }
    private static void give(net.minecraft.world.entity.player.Player player, ItemStack stack) {
        if (!player.getInventory().add(stack)) player.drop(stack, false);
    }
}
