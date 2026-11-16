package github.com.gengyoubo.tcmodern.tc4.client.testing;

import com.mojang.logging.LogUtils;
import github.com.gengyoubo.tcmodern.tc4.init.Tc4Entities;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Opt-in client acceptance ({@code -PentityRenderSmokeTest}): renders every new TC4 entity in batches in front of the
 * camera and saves one screenshot per batch, so renderer crashes surface and the models can be looked at.
 */
@Mod.EventBusSubscriber(modid = "tc4modern", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class EntityRenderSmokeProbe {
    private final long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(5);
    private final Map<String, Object> proof = new LinkedHashMap<>();
    private final List<List<Supplier<EntityType<?>>>> batches = List.of(
            List.of(Tc4Entities.TRAVELING_TRUNK::get, Tc4Entities.FIRE_BAT::get, Tc4Entities.ELDRITCH_CRAB::get, Tc4Entities.MIND_SPIDER::get, Tc4Entities.THAUMIC_SLIME::get),
            List.of(Tc4Entities.CULTIST_CLERIC::get, Tc4Entities.CULTIST_KNIGHT::get, Tc4Entities.PECH::get, Tc4Entities.WISP::get, Tc4Entities.ELDRITCH_GUARDIAN::get),
            List.of(Tc4Entities.CULTIST_LEADER::get, Tc4Entities.ELDRITCH_GOLEM::get, Tc4Entities.ELDRITCH_WARDEN::get, Tc4Entities.CULTIST_PORTAL::get),
            List.of(Tc4Entities.FROST_SHARD::get, Tc4Entities.EXPLOSIVE_ORB::get, Tc4Entities.SHOCK_ORB::get, Tc4Entities.GOLEM_ORB::get, Tc4Entities.ELDRITCH_ORB::get,
                    Tc4Entities.PRIMAL_ORB::get, Tc4Entities.EMBER::get, Tc4Entities.BOTTLE_TAINT::get),
            List.of(Tc4Entities.DART::get, Tc4Entities.PRIMAL_ARROW::get, Tc4Entities.ASPECT_ORB::get, Tc4Entities.SPECIAL_ITEM::get, Tc4Entities.PERMANENT_ITEM::get,
                    Tc4Entities.FOLLOWING_ITEM::get, Tc4Entities.ITEM_GRATE::get));
    private final List<Entity> current = new ArrayList<>();
    private int stage, batch, wait;

    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        if (Boolean.getBoolean("tc4modern.entityRenderSmokeTest"))
            event.enqueueWork(() -> MinecraftForge.EVENT_BUS.addListener(new EntityRenderSmokeProbe()::tick));
    }

    private void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == 99) return;
        var mc = Minecraft.getInstance();
        try {
            if (System.nanoTime() > deadline) throw new IllegalStateException("Entity render smoke timed out at stage " + stage + " batch " + batch);
            if (stage == 0 && mc.screen instanceof TitleScreen && mc.getOverlay() == null) {
                mc.options.pauseOnLostFocus = false;
                mc.options.renderDistance().set(4);
                mc.options.hideGui = true;
                mc.createWorldOpenFlows().createFreshLevel("tc4modern-entity-render-smoke-" + System.currentTimeMillis(),
                        new LevelSettings("TC4 Entities", GameType.CREATIVE, false, Difficulty.NORMAL, true, new GameRules(), WorldDataConfiguration.DEFAULT),
                        new WorldOptions(42, false, false), access -> access.registryOrThrow(Registries.WORLD_PRESET)
                                .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
                stage = 1;
            } else if (stage == 1 && mc.level != null && mc.player != null && mc.screen == null) {
                if (++wait < 60) return;
                wait = 0;
                stage = 2;
            } else if (stage == 2) {
                var server = mc.getSingleplayerServer();
                var types = batches.get(batch);
                server.execute(() -> place(server.overworld(), types));
                stage = 3;
            } else if (stage == 3) {
                if (++wait < 40) return;
                wait = 0;
                String name = "tc4modern-entities-" + batch + ".png";
                Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), message -> { });
                proof.put("batch" + batch, name);
                batch++;
                stage = batch < batches.size() ? 2 : 4;
            } else if (stage == 4) {
                if (++wait < 10) return;
                finish(mc, null);
            }
        } catch (Throwable failure) {
            finish(mc, failure);
        }
    }

    /** Clears the last batch and lines this one up six blocks in front of the player, frozen in place. */
    private void place(ServerLevel level, List<Supplier<EntityType<?>>> types) {
        current.forEach(Entity::discard);
        current.clear();
        var player = level.players().get(0);
        player.teleportTo(level, 0.5, -60, -6.5, 0.0F, 10.0F);
        level.setDayTime(6000);
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, level.getServer());
        double spacing = 3.2, start = -(types.size() - 1) * spacing / 2;
        for (int i = 0; i < types.size(); i++) {
            var entity = types.get(i).get().create(level);
            if (entity == null) continue;
            double x = 0.5 + start + i * spacing;
            entity.moveTo(x, -60 + (entity instanceof Mob ? 0 : 1.2), 0.5, 180.0F, 0.0F);
            if (entity instanceof Mob mob) {
                mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()), MobSpawnType.COMMAND, null, null);
                mob.setNoAi(true);
                mob.setYBodyRot(180.0F); mob.setYHeadRot(180.0F);
            } else {
                entity.setNoGravity(true);
                entity.setDeltaMovement(0, 0, 0);
            }
            if (entity instanceof net.minecraft.world.entity.item.ItemEntity item) { item.setItem(new ItemStack(Items.DIAMOND)); item.setNeverPickUp(); }
            level.addFreshEntity(entity);
            current.add(entity);
        }
    }

    private void finish(Minecraft mc, Throwable failure) {
        stage = 99;
        proof.put("status", failure == null ? "PASS" : "FAIL");
        if (failure != null) proof.put("failure", failure.toString());
        try {
            var file = mc.gameDirectory.toPath().resolve("tc4modern/entity-render-smoke.json");
            Files.createDirectories(file.getParent());
            Files.writeString(file, new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(proof));
        } catch (Exception ignored) { }
        LogUtils.getLogger().info("TC4_ENTITY_RENDER_SMOKE_RESULT: {}", proof);
        if (failure != null) LogUtils.getLogger().error("Entity render smoke failed", failure);
        mc.stop();
    }
}
