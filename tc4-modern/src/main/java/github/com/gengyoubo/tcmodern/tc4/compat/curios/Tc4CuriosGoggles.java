package github.com.gengyoubo.tcmodern.tc4.compat.curios;

import com.mojang.logging.LogUtils;
import github.com.gengyoubo.mfcl.api.LegacyEquipment;
import github.com.gengyoubo.mfcl.api.LegacyRuntime;
import java.util.Set;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.loading.FMLEnvironment;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

/**
 * Hands Goggles of Revealing worn in the Curios head slot to the original code as the worn helmet,
 * so its own checks ({@code armorItemInSlot(3) instanceof IRevealer/IGoggles}: node rendering,
 * aspect popups, vis discount) see them. A real helmet in the vanilla slot wins: MFCL's own
 * source (priority 0) answers first and only defers on an empty helmet slot.
 */
final class Tc4CuriosGoggles implements LegacyEquipment.Source {
    private static final int PRIORITY = -10;

    private Tc4CuriosGoggles() { }

    static void install(LegacyRuntime runtime, Set<Item> goggles) {
        runtime.equipment().register(PRIORITY, new Tc4CuriosGoggles());
        // Curios draws nothing for an item without a renderer; the client class is only loaded on a client.
        if (FMLEnvironment.dist.isClient()) Tc4CuriosGogglesRenderer.register(goggles);
        LogUtils.getLogger().info("TC4 Curios: goggles in the Curios head slot count as the worn helmet.");
    }

    @Override
    public Object worn(Object hostPlayer, LegacyEquipment.Slot slot, boolean client) {
        if (slot != LegacyEquipment.Slot.ARMOR_HEAD) return null;
        var stacks = headStacks(hostPlayer);
        if (stacks == null) return null;
        int index = gogglesIndex(stacks);
        return index < 0 ? null : stacks.getStackInSlot(index);
    }

    @Override
    public void changed(Object hostPlayer, LegacyEquipment.Slot slot, Object newHostStack) {
        if (slot != LegacyEquipment.Slot.ARMOR_HEAD) return;
        var stacks = headStacks(hostPlayer);
        if (stacks == null) return;
        int index = gogglesIndex(stacks);
        if (index >= 0) stacks.setStackInSlot(index, (ItemStack) newHostStack);
    }

    private static IDynamicStackHandler headStacks(Object hostPlayer) {
        if (!(hostPlayer instanceof LivingEntity entity)) return null;
        return CuriosApi.getCuriosInventory(entity).resolve()
                .flatMap(inventory -> inventory.getStacksHandler(Tc4CuriosCompat.HEAD))
                .map(handler -> handler.getStacks())
                .orElse(null);
    }

    private static int gogglesIndex(IDynamicStackHandler stacks) {
        for (int index = 0; index < stacks.getSlots(); index++) {
            ItemStack stack = stacks.getStackInSlot(index);
            if (!stack.isEmpty() && Tc4CuriosCompat.HEAD.equals(Tc4CuriosCompat.slotOf(stack.getItem()))) return index;
        }
        return -1;
    }
}
