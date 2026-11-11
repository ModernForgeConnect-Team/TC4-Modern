package github.com.gengyoubo.tcmodern.tc4.client.testing;

import com.mojang.logging.LogUtils;
import github.com.gengyoubo.mfcl.api.LegacyMappings;
import github.com.gengyoubo.mfcl.api.LegacyObject;
import github.com.gengyoubo.mfcl.api.mla.LegacyField;
import github.com.gengyoubo.mfcl.api.mla.LegacyType;
import github.com.gengyoubo.tcmodern.api.TcModernApi;
import github.com.gengyoubo.tcmodern.tc4.init.Tc4CreativeTabs;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
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

/** Opt-in check of the wand creative tab: every build the registered caps and rods allow, once, fully charged. */
@Mod.EventBusSubscriber(modid = "tc4modern", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class WandTabSmokeProbe {
    private static final List<String> PRIMALS = List.of("aer", "terra", "ignis", "aqua", "ordo", "perditio");
    private final long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(4);
    private final Map<String, Object> proof = new LinkedHashMap<>();
    private int stage;

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        if (Boolean.getBoolean("tc4modern.wandTabSmokeTest"))
            event.enqueueWork(() -> MinecraftForge.EVENT_BUS.addListener(new WandTabSmokeProbe()::tick));
    }

    private void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == 99) return;
        var mc = Minecraft.getInstance();
        try {
            require(System.nanoTime() < deadline, "Wand tab check timed out at stage " + stage);
            if (stage == 0 && mc.screen instanceof TitleScreen && mc.getOverlay() == null) {
                org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());
                mc.options.pauseOnLostFocus = false;
                mc.options.renderDistance().set(2);
                mc.createWorldOpenFlows().createFreshLevel("tc4modern-wand-tab-smoke-" + System.currentTimeMillis(),
                        new LevelSettings("TC4 Wand Tab", GameType.CREATIVE, false, Difficulty.PEACEFUL,
                                true, new GameRules(), WorldDataConfiguration.DEFAULT),
                        new WorldOptions(42, false, false), access -> access.registryOrThrow(Registries.WORLD_PRESET)
                                .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
                stage = 1;
            } else if (stage == 1 && mc.level != null && mc.player != null && mc.screen == null) {
                CreativeModeTabs.tryRebuildTabContents(mc.level.enabledFeatures(), true, mc.level.registryAccess());
                check(List.copyOf(Tc4CreativeTabs.WANDS.get().getDisplayItems()));
                finish(mc, null);
            }
        } catch (Exception | AssertionError failure) { finish(mc, failure); }
    }

    @LegacyType("thaumcraft.api.wands.WandCap")
    public interface Caps { @LegacyField(name = "caps", descriptor = "Ljava/util/LinkedHashMap;", isStatic = true) LegacyObject all(); }

    @LegacyType("thaumcraft.api.wands.WandRod")
    public interface Rods { @LegacyField(name = "rods", descriptor = "Ljava/util/LinkedHashMap;", isStatic = true) LegacyObject all(); }

    private void check(List<ItemStack> shown) {
        var api = TcModernApi.runtime().orElseThrow();
        // What the game can craft: every registered cap on every registered rod, and sceptres on non-staff rods.
        var caps = new java.util.ArrayList<String>();
        for (Object tag : api.values().map(api.accessor(Caps.class).all()).keySet()) caps.add((String) tag);
        var wandRods = new java.util.ArrayList<String>();
        var staffRods = new java.util.ArrayList<String>();
        for (var rod : api.values().map(api.accessor(Rods.class).all()).entrySet())
            (api.values().isInstance((LegacyObject) rod.getValue(), "thaumcraft.api.wands.StaffRod") ? staffRods : wandRods).add((String) rod.getKey());
        proof.put("caps", caps);
        proof.put("wandRods", wandRods);
        proof.put("staffRods", staffRods);
        require(caps.containsAll(List.of("iron", "gold", "thaumium", "void")), "TC4's own caps are not registered: " + caps);
        require(wandRods.size() >= 9 && staffRods.size() >= 9, "TC4's own rods are not registered");
        var expected = new java.util.LinkedHashSet<String>();
        for (String rod : wandRods) for (String cap : caps) expected.add(rod + "/" + cap + "/wand");
        for (String rod : staffRods) for (String cap : caps) expected.add(rod + "/" + cap + "/staff");
        for (String rod : wandRods) for (String cap : caps) expected.add(rod + "/" + cap + "/sceptre");

        proof.put("shown", shown.size());
        var builds = new java.util.ArrayList<String>();
        String previousKind = "wand";
        var order = List.of("wand", "staff", "sceptre");
        for (var stack : shown) {
            var old = api.snapshot(api.mappings().toLegacy(LegacyMappings.ITEM_STACK, stack).orElseThrow(
                    () -> new IllegalStateException("Not an original stack: " + stack)));
            require(old.key().equals("Thaumcraft:WandCasting"), "Not an original wand: " + old);
            String snbt = old.snbt(), rod = string(snbt, "rod");
            String kind = snbt.matches(".*sceptre:1b?[,}].*") ? "sceptre" : staffRods.contains(rod) ? "staff" : "wand";
            String build = rod + "/" + string(snbt, "cap") + "/" + kind;
            require(order.indexOf(kind) >= order.indexOf(previousKind), "Wands, staves and sceptres are not grouped at " + build);
            previousKind = kind;
            require(!builds.contains(build), "Duplicate build " + build);
            builds.add(build);
            int first = -1;
            for (String primal : PRIMALS) {
                int vis = number(snbt, primal);
                require(vis > 0, build + " is not charged with " + primal);
                if (first < 0) first = vis;
                require(vis == first, build + " is not charged evenly");
            }
            if (build.equals("wood/iron/wand")) proof.put("ironWoodWandVis", first);
            if (build.equals("wood/iron/sceptre")) proof.put("ironWoodSceptreVis", first);
        }
        var missing = new java.util.ArrayList<>(expected); missing.removeAll(builds);
        var extra = new java.util.ArrayList<>(builds); extra.removeAll(expected);
        if (!missing.isEmpty()) proof.put("missing", missing);
        if (!extra.isEmpty()) proof.put("extra", extra);
        require(missing.isEmpty() && extra.isEmpty(), "Tab differs from the craftable builds: " + missing.size() + " missing, " + extra.size() + " extra");
        require((int) proof.get("ironWoodSceptreVis") > (int) proof.get("ironWoodWandVis"), "Sceptre bonus missing");
        proof.put("everyCraftableBuildOnce", true);
        proof.put("allFullyCharged", true);
    }

    private static String string(String snbt, String key) {
        var matcher = Pattern.compile("[{,]\\s*" + key + "\\s*:\\s*\"([^\"]*)\"").matcher(snbt);
        require(matcher.find(), "No " + key + " in " + snbt);
        return matcher.group(1);
    }

    private static int number(String snbt, String key) {
        var matcher = Pattern.compile("[{,]\\s*" + key + "\\s*:\\s*(-?\\d+)").matcher(snbt);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : 0;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private void finish(Minecraft mc, Throwable failure) {
        stage = 99;
        proof.put("status", failure == null ? "PASS" : "FAIL");
        if (failure != null) {
            proof.put("failure", failure.toString());
            LogUtils.getLogger().error("TC4_WAND_TAB_SMOKE_FAIL", failure);
        }
        try {
            var file = mc.gameDirectory.toPath().resolve("logs/tc4modern/wand-tab-smoke.json");
            Files.createDirectories(file.getParent());
            Files.writeString(file, new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(proof));
        } catch (java.io.IOException error) { throw new IllegalStateException(error); }
        LogUtils.getLogger().info("TC4_WAND_TAB_SMOKE_RESULT: {}", proof);
        if (mc.level != null) { mc.level.disconnect(); mc.clearLevel(); }
        mc.stop();
    }
}
