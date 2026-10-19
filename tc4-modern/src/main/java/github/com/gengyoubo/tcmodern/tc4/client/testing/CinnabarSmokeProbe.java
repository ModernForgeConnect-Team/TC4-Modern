package github.com.gengyoubo.tcmodern.tc4.client.testing;

import com.mojang.logging.LogUtils;
import github.com.gengyoubo.mfcl.api.LegacyMappings;
import github.com.gengyoubo.tcmodern.api.TcModernApi;
import github.com.gengyoubo.tcmodern.tc4.content.Tc4ModernContent;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Opt-in regression check against real mining, furnace ticks and client recipe synchronization. */
@Mod.EventBusSubscriber(modid = "tc4modern", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class CinnabarSmokeProbe {
    private static final ResourceLocation RECIPE = ResourceLocation.fromNamespaceAndPath("tc4modern",
            "quicksilver_from_smelting_deepslate_cinnabar_ore");
    private final long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(4);
    private final Map<String, Object> proof = new LinkedHashMap<>();
    private int stage;
    private CompletableFuture<Void> checks;

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        if (Boolean.getBoolean("tc4modern.cinnabarSmokeTest"))
            event.enqueueWork(() -> MinecraftForge.EVENT_BUS.addListener(new CinnabarSmokeProbe()::tick));
    }

    private void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == 99) return;
        var mc = Minecraft.getInstance();
        try {
            require(System.nanoTime() < deadline, "Cinnabar acceptance timed out at " + stage);
            if (stage == 0 && mc.screen instanceof TitleScreen && mc.getOverlay() == null) {
                org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());
                mc.options.pauseOnLostFocus = false;
                mc.options.renderDistance().set(2);
                mc.createWorldOpenFlows().createFreshLevel("tc4modern-cinnabar-smoke-" + System.currentTimeMillis(),
                        new LevelSettings("TC4 Cinnabar", GameType.CREATIVE, false, Difficulty.PEACEFUL,
                                true, new GameRules(), WorldDataConfiguration.DEFAULT),
                        new WorldOptions(42, false, false), access -> access.registryOrThrow(Registries.WORLD_PRESET)
                                .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
                stage = 1;
            } else if (stage == 1 && mc.level != null && mc.player != null && mc.screen == null) {
                var pos = mc.player.blockPosition().offset(3, 3, 0);
                checks = mc.getSingleplayerServer().submit(() -> validateServer(mc.getSingleplayerServer().overworld(), pos));
                stage = 2;
            } else if (stage == 2 && checks.isDone()) {
                checks.join();
                var recipe = recipe(mc.level.getRecipeManager());
                require(recipe.getSerializer() == RecipeSerializer.SMELTING_RECIPE, "Recipe does not use vanilla synchronization");
                quicksilver(recipe.getResultItem(mc.level.registryAccess()));
                proof.put("clientRecipeMetadata", true);
                finish(mc, null);
            }
        } catch (Exception | AssertionError failure) { finish(mc, failure); }
    }

    private void validateServer(ServerLevel level, BlockPos pos) {
        mine(level, pos, new ItemStack(Items.IRON_PICKAXE), 1);
        proof.put("normalDropsSelf", true);
        var fortune = new ItemStack(Items.DIAMOND_PICKAXE);
        fortune.enchant(Enchantments.BLOCK_FORTUNE, 3);
        mine(level, pos, fortune, 1);
        proof.put("fortuneDropsOneSelf", true);
        var silk = new ItemStack(Items.DIAMOND_PICKAXE);
        silk.enchant(Enchantments.SILK_TOUCH, 1);
        mine(level, pos, silk, 1);
        proof.put("silkDropsSelf", true);
        mine(level, pos, new ItemStack(Items.WOODEN_PICKAXE), 0);
        mine(level, pos, new ItemStack(Items.STONE_PICKAXE), 0);
        proof.put("requiresIronTool", true);

        var recipe = recipe(level.getRecipeManager());
        require(recipe.getCookingTime() == 200 && recipe.getExperience() == 1.0F, "Original smelting time/XP differs");
        quicksilver(recipe.getResultItem(level.registryAccess()));
        level.setBlockAndUpdate(pos, Blocks.FURNACE.defaultBlockState());
        var furnace = (FurnaceBlockEntity) level.getBlockEntity(pos);
        furnace.setItem(0, new ItemStack(Tc4ModernContent.DEEPSLATE_CINNABAR_ORE.get(), 2));
        furnace.setItem(1, new ItemStack(Items.COAL));
        for (int tick = 0; tick < 200; tick++)
            AbstractFurnaceBlockEntity.serverTick(level, pos, level.getBlockState(pos), furnace);
        quicksilver(furnace.getItem(2));
        require(furnace.getItem(0).getCount() == 1, "Furnace did not consume exactly one ore");
        proof.put("furnaceOneOreToOneQuicksilver", true);
        level.removeBlock(pos, false);
    }

    private static void mine(ServerLevel level, BlockPos pos, ItemStack tool, int expectedCount) {
        var player = FakePlayerFactory.getMinecraft(level);
        player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, tool);
        level.setBlockAndUpdate(pos, Tc4ModernContent.DEEPSLATE_CINNABAR_ORE.get().defaultBlockState());
        require(player.gameMode.destroyBlock(pos), "Mining failed");
        var drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(1));
        int count = 0;
        for (var entity : drops) {
            require(entity.getItem().is(Tc4ModernContent.DEEPSLATE_CINNABAR_ORE.get().asItem()), "Mining dropped another item");
            count += entity.getItem().getCount();
            entity.discard();
        }
        require(count == expectedCount, "Unexpected mining count: " + count + ", expected " + expectedCount);
    }

    private static SmeltingRecipe recipe(RecipeManager manager) {
        return (SmeltingRecipe) manager.byKey(RECIPE).orElseThrow(() -> new IllegalStateException("Missing cinnabar smelting recipe"));
    }

    private static void quicksilver(ItemStack stack) {
        var api = TcModernApi.runtime().orElseThrow();
        var old = api.snapshot(api.mappings().toLegacy(LegacyMappings.ITEM_STACK, stack).orElseThrow());
        require(old.key().equals("Thaumcraft:ItemResource") && old.metadata() == 3 && old.count() == 1,
                "Expected one original Quicksilver, got " + old);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private void finish(Minecraft mc, Throwable failure) {
        stage = 99;
        proof.put("status", failure == null ? "PASS" : "FAIL");
        if (failure != null) {
            proof.put("failure", failure.toString());
            LogUtils.getLogger().error("TC4_CINNABAR_SMOKE_FAIL", failure);
        }
        try {
            var file = mc.gameDirectory.toPath().resolve("logs/tc4modern/cinnabar-smoke.json");
            Files.createDirectories(file.getParent());
            Files.writeString(file, new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(proof));
        } catch (java.io.IOException error) { throw new IllegalStateException(error); }
        LogUtils.getLogger().info("TC4_CINNABAR_SMOKE_RESULT: {}", proof);
        if (mc.level != null) { mc.level.disconnect(); mc.clearLevel(); }
        mc.stop();
    }
}
