package github.com.gengyoubo.tcmodern.tc4.client.testing;

import github.com.gengyoubo.tcmodern.tc4.client.entity.PechScreen;
import github.com.gengyoubo.tcmodern.tc4.init.Tc4Entities;
import github.com.gengyoubo.tcmodern.tc4.menu.PechMenu;
import java.nio.file.Files;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
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

/** Isolated integration test for the synchronized original pech trade screen. */
@Mod.EventBusSubscriber(modid="tc4modern", value=Dist.CLIENT, bus=Mod.EventBusSubscriber.Bus.MOD)
public final class PechGuiSmokeProbe {
    private int stage, frames;
    private CompletableFuture<?> pending;
    private final long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.MINUTES.toNanos(5);
    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        if (Boolean.getBoolean("tc4modern.pechSmokeTest")) event.enqueueWork(() -> MinecraftForge.EVENT_BUS.addListener(new PechGuiSmokeProbe()::tick));
    }
    private void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == 99) return;
        var mc = Minecraft.getInstance();
        try {
            if (System.nanoTime() > deadline) throw new IllegalStateException("Pech screen timed out at " + stage);
            if (pending != null && pending.isCompletedExceptionally()) pending.join();
            if (stage == 0 && mc.screen instanceof TitleScreen && mc.getOverlay() == null) {
                org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());
                mc.options.pauseOnLostFocus = false;
                mc.createWorldOpenFlows().createFreshLevel("pech-smoke-" + System.currentTimeMillis(),
                        new LevelSettings("Pech smoke", GameType.CREATIVE, false, Difficulty.NORMAL, true, new GameRules(), WorldDataConfiguration.DEFAULT),
                        new WorldOptions(42, false, false), a -> a.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
                stage = 1;
            } else if (stage == 1 && mc.player != null && mc.level != null && mc.screen == null) {
                pending = mc.getSingleplayerServer().submit(() -> {
                    var player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                    var pech = Tc4Entities.PECH.get().create(player.serverLevel());
                    if (pech == null) throw new IllegalStateException("Missing pech");
                    pech.moveTo(player.getX() + 1, player.getY(), player.getZ(), 0, 0);
                    pech.setTamed(true); pech.setNoAi(true); player.serverLevel().addFreshEntity(pech);
                    pech.interact(player, InteractionHand.MAIN_HAND);
                    github.com.gengyoubo.tcmodern.tc4.network.DeskNetwork.guardianFog(player);
                    if (!(player.containerMenu instanceof PechMenu menu)) throw new IllegalStateException("Pech did not open menu");
                    menu.getSlot(0).set(new ItemStack(Items.GOLD_INGOT, 2)); menu.broadcastChanges();
                }); stage = 2;
            } else if (stage == 2 && pending.isDone() && mc.screen instanceof PechScreen && mc.player.containerMenu instanceof PechMenu menu && menu.canTrade()
                    && github.com.gengyoubo.tcmodern.tc4.client.entity.EldritchFog.isActive()) {
                if (++frames < 15) return;
                net.minecraft.client.Screenshot.grab(mc.gameDirectory, "tc4modern-pech-trade.png", mc.getMainRenderTarget(), message -> {});
                mc.gameMode.handleInventoryButtonClick(menu.containerId, 0);
                frames = 0; stage = 3;
            } else if (stage == 3 && ++frames >= 20) {
                if (mc.player.containerMenu instanceof PechMenu menu && menu.getSlot(0).getItem().getCount() != 1)
                    throw new IllegalStateException("Trade did not synchronize consumption");
                finish(mc, "PASS");
            }
        } catch (Throwable failure) { failure.printStackTrace(); finish(mc, "FAIL: " + failure); }
    }
    private void finish(Minecraft mc, String result) {
        stage = 99;
        try { Files.writeString(mc.gameDirectory.toPath().resolve("pech-gui-smoke.txt"), result); }
        catch (Exception failure) { throw new IllegalStateException(failure); }
        mc.stop();
    }
}
