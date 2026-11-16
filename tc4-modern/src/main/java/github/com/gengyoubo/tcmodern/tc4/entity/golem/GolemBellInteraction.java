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
    static final String PORTABLE = GolemItemData.PORTABLE;
    private static final String SELECTED = GolemItemData.SELECTED, SELECTED_DIMENSION = GolemItemData.SELECTED_DIMENSION;
    private GolemBellInteraction() { }
    private static boolean isBell(ItemStack stack) {
        var key = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return key != null && key.toString().equals("thaumcraft:golem_bell");
    }
    private static ModernGolem selected(net.minecraft.world.entity.player.Player player, ItemStack stack) {
        if (!(player.level() instanceof net.minecraft.server.level.ServerLevel level) || !isBell(stack)) return null;
        var tag = GolemItemData.get(stack);
        if (tag == null || !tag.hasUUID(SELECTED)) return null;
        if (!level.dimension().location().toString().equals(tag.getString(SELECTED_DIMENSION))) return null;
        if (!(level.getEntity(tag.getUUID(SELECTED)) instanceof ModernGolem golem) || !golem.isAlive()
                || (golem.ownerId() != null && !golem.ownerId().equals(player.getUUID()))) return null;
        return golem;
    }
    private static net.minecraft.world.phys.BlockHitResult fluidTarget(net.minecraft.world.entity.player.Player player) {
        var start = player.getEyePosition();
        var end = start.add(player.getViewVector(1.0F).scale(5.0));
        return player.level().clip(new net.minecraft.world.level.ClipContext(start, end,
                net.minecraft.world.level.ClipContext.Block.OUTLINE, net.minecraft.world.level.ClipContext.Fluid.SOURCE_ONLY, player));
    }
    private static boolean markFluid(ModernGolem golem, net.minecraft.world.phys.BlockHitResult hit) {
        if (!golem.hasLiquidCore() || hit.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK) return false;
        var state = golem.level().getBlockState(hit.getBlockPos());
        if (!(state.getBlock() instanceof net.minecraft.world.level.block.BucketPickup) || !state.getFluidState().isSource()) return false;
        golem.setMarkedContainer(hit.getBlockPos(), hit.getDirection());
        return true;
    }
    @SubscribeEvent public static void select(net.minecraftforge.event.entity.player.PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof ModernGolem golem) || !isBell(event.getItemStack())) return;
        var player = event.getEntity();
        event.setCancellationResult(net.minecraft.world.InteractionResult.sidedSuccess(player.level().isClientSide));
        event.setCanceled(true);
        if (player.level().isClientSide || (golem.ownerId() != null && !golem.ownerId().equals(player.getUUID()))) return;
        var tag = GolemItemData.getOrCreate(event.getItemStack());
        tag.putUUID(SELECTED, golem.getUUID());
        tag.putString(SELECTED_DIMENSION, player.level().dimension().location().toString());
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.tc4modern.golem_selected"), true);
    }
    @SubscribeEvent public static void mark(net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock event) {
        var stack = event.getItemStack();
        var data = isBell(stack) ? GolemItemData.get(stack) : null;
        if (data == null || !data.hasUUID(SELECTED)) return;
        var player = event.getEntity();
        event.setCancellationResult(net.minecraft.world.InteractionResult.sidedSuccess(player.level().isClientSide));
        event.setCanceled(true);
        if (!(player.level() instanceof net.minecraft.server.level.ServerLevel level)) return;
        var golem = selected(player, stack);
        if (golem == null) return;
        var block = level.getBlockEntity(event.getPos());
        if (golem.hasEssentiaCore()) {
            if (!GolemEssentia.isContainer(level, event.getPos())) return;
            golem.setMarkedContainer(event.getPos(), event.getFace());
        } else if (golem.hasSortCore()) {
            if (block == null || !block.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER, event.getFace()).isPresent()) return;
            boolean added = golem.toggleSortMarker(event.getPos(), event.getFace());
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(added ? "message.tc4modern.golem_sort_added" : "message.tc4modern.golem_sort_removed"), true);
            return;
        } else if (golem.hasUseCore()) {
            golem.setMarkedContainer(event.getPos(), event.getFace());
        } else if (block != null && (block.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER, event.getFace()).isPresent()
                || block.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.FLUID_HANDLER, event.getFace()).isPresent())) {
            golem.setMarkedContainer(event.getPos(), event.getFace());
        } else if (!markFluid(golem, fluidTarget(player))) return;
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.tc4modern.golem_container_marked"), true);
    }
    @SubscribeEvent public static void markFluidInAir(net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickItem event) {
        var stack = event.getItemStack();
        var data = isBell(stack) ? GolemItemData.get(stack) : null;
        if (data == null || !data.hasUUID(SELECTED)) return;
        var player = event.getEntity();
        if (player.level().isClientSide) return;
        var golem = selected(player, stack);
        if (golem == null || !markFluid(golem, fluidTarget(player))) return;
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.tc4modern.golem_container_marked"), true);
        event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
        event.setCanceled(true);
    }
    @SubscribeEvent public static void onAttack(AttackEntityEvent event) {
        if (!(event.getTarget() instanceof ModernGolem golem)) return;
        var player = event.getEntity();
        if (!isBell(player.getMainHandItem())) return;
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
            if (golem.hasCarriedFluid() || golem.hasCarriedEssentia()) {
                var data = new CompoundTag();
                golem.addAdditionalSaveData(data);
                data.putInt("Tc4Core", -1);
                data.remove("Tc4Carried");
                GolemItemData.getOrCreate(item).put(PORTABLE, data);
            }
            golem.discard();
            give(player, item);
            if (!core.isEmpty()) give(player, core);
            if (!carried.isEmpty()) give(player, carried);
        } else {
            var data = new CompoundTag();
            golem.addAdditionalSaveData(data);
            GolemItemData.getOrCreate(item).put(PORTABLE, data);
            if (golem.hasCustomName()) item.setHoverName(golem.getCustomName());
            golem.discard();
            give(player, item);
        }
    }
    private static void give(net.minecraft.world.entity.player.Player player, ItemStack stack) {
        if (!player.getInventory().add(stack)) player.drop(stack, false);
    }
}
