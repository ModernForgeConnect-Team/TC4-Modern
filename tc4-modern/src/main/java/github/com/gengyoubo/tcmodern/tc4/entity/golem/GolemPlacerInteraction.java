package github.com.gengyoubo.tcmodern.tc4.entity.golem;

import com.mojang.logging.LogUtils;
import github.com.gengyoubo.mfcl.api.LegacyMappings;
import github.com.gengyoubo.tcmodern.api.TcModernApi;
import github.com.gengyoubo.tcmodern.tc4.Tc4Modern;
import github.com.gengyoubo.tcmodern.tc4.init.Tc4Entities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/** Uses TC4's actual golem placer for supported modern golems. */
@Mod.EventBusSubscriber(modid = Tc4Modern.MOD_ID)
public final class GolemPlacerInteraction {
    private GolemPlacerInteraction() { }
    @SubscribeEvent(priority = EventPriority.HIGH) public static void place(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        var stack = event.getItemStack();
        var id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id == null || !id.getNamespace().equals("thaumcraft") || !id.getPath().startsWith("item_golem_placer")) return;
        // A portable modern golem must never fall through to TC4's old placer: it cannot read our carried state.
        boolean portable = stack.getTag() != null && stack.getTag().contains(GolemBellInteraction.PORTABLE, 10);
        if (portable) { event.setCancellationResult(InteractionResult.FAIL); event.setCanceled(true); }
        var runtime = TcModernApi.runtime().orElse(null);
        if (runtime == null) return;
        try {
            // Remove our host-only payload before identifying the TC4 item; all other original NBT is checked below.
            var probe = stack.copy();
            var tag = probe.getTag();
            CompoundTag saved = tag != null && tag.contains(GolemBellInteraction.PORTABLE, 10) ? tag.getCompound(GolemBellInteraction.PORTABLE).copy() : null;
            if (tag != null) { tag.remove(GolemBellInteraction.PORTABLE); if (saved != null) tag.remove("core"); }
            var legacy = runtime.mappings().toLegacy(LegacyMappings.ITEM_STACK, probe).map(runtime::snapshot).orElse(null);
            if (legacy == null || !legacy.key().equals("Thaumcraft:ItemGolemPlacer") || legacy.metadata() >= GolemMaterial.values().length) return;
            if (!legacy.snbt().isBlank()) {
                var original = TagParser.parseTag(legacy.snbt());
                // Unimplemented TC4 upgrades, cores and markers remain owned by the original placer.
                if (original.getAllKeys().stream().anyMatch(key -> !key.equals("display"))) return;
            }
            var material = GolemMaterial.values()[legacy.metadata()];
            var face = event.getFace(); var clicked = event.getPos(); var target = clicked.relative(face);
            var entity = Tc4Entities.GOLEMS.get(material).get().create(level);
            if (entity == null) return;
            entity.moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, level.random.nextFloat() * 360, 0);
            entity.finalizeSpawn(level, level.getCurrentDifficultyAt(target), MobSpawnType.MOB_SUMMONED, null, null);
            if (saved != null) entity.readAdditionalSaveData(saved);
            entity.resetPlacement(target);
            entity.setOwner(event.getEntity());
            if (stack.hasCustomHoverName()) entity.setCustomName(stack.getHoverName());
            var tile = level.getBlockEntity(clicked);
            if (tile != null && (tile.getCapability(ForgeCapabilities.ITEM_HANDLER, face).isPresent()
                    || tile.getCapability(ForgeCapabilities.FLUID_HANDLER, face).isPresent() || GolemEssentia.isContainer(level, clicked))) entity.setDestination(clicked, face);
            if (!level.noCollision(entity) || !level.addFreshEntity(entity)) return;
            if (!event.getEntity().getAbilities().instabuild) stack.shrink(1);
            level.gameEvent(event.getEntity(), GameEvent.ENTITY_PLACE, clicked);
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException | RuntimeException failure) {
            LogUtils.getLogger().warn("Could not use TC4 golem item with modern golem; leaving it to the original placer", failure);
        }
    }
}
