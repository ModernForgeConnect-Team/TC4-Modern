package github.com.gengyoubo.tcmodern.tc4.client.testing;

import com.mojang.logging.LogUtils;
import github.com.gengyoubo.mfcl.api.*;
import github.com.gengyoubo.mfcl.api.mla.*;
import github.com.gengyoubo.tcmodern.api.TcModernApi;
import github.com.gengyoubo.tcmodern.tc4.content.MagicalLeavesBlock;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.resources.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Real native leaf registration, legacy mapping, generation and harvesting in a disposable world. */
@Mod.EventBusSubscriber(modid = "tc4modern", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class NativeLeavesSmokeProbe {
    @LegacyType("net.minecraft.world.World") public interface OldWorld {
        @LegacyMethod(name="func_72805_g",descriptor="(III)I") int metadata(int x,int y,int z);
        @LegacyMethod(name="func_72921_c",descriptor="(IIIII)Z") boolean metadata(int x,int y,int z,int meta,int flags);
    }
    private final Map<String,Object> proof = new LinkedHashMap<>();
    private final long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(7);
    private CompletableFuture<?> pending;
    private int stage, frames;
    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        if (Boolean.getBoolean("tc4modern.nativeLeavesSmokeTest"))
            event.enqueueWork(() -> MinecraftForge.EVENT_BUS.addListener(new NativeLeavesSmokeProbe()::tick));
    }
    private void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == 99) return;
        var mc = Minecraft.getInstance();
        try {
            require(System.nanoTime() < deadline, "Native leaves acceptance timed out at " + stage);
            if (stage == 0 && mc.screen instanceof TitleScreen && mc.getOverlay() == null) {
                org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());
                mc.options.pauseOnLostFocus = false; mc.options.renderDistance().set(3);
                mc.createWorldOpenFlows().createFreshLevel("tc4modern-native-leaves-" + System.currentTimeMillis(),
                        new LevelSettings("Native leaves", GameType.CREATIVE, false, Difficulty.PEACEFUL, true,
                                new GameRules(), WorldDataConfiguration.DEFAULT), new WorldOptions(42,false,false),
                        access -> access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
                stage = 1;
            } else if (stage == 1 && mc.level != null && mc.player != null && mc.screen == null) {
                var id = mc.player.getUUID(); var server = mc.getSingleplayerServer();
                pending = server.submit(() -> validate(server.getPlayerList().getPlayer(id))); stage = 2;
            } else if (stage == 2 && pending.isDone()) {
                pending.join(); stage = 3; frames = 0;
            } else if (stage == 3 && ++frames > 20) {
                var path = mc.gameDirectory.toPath().resolve("screenshots/native-magical-leaves.png");
                Files.createDirectories(path.getParent());
                try (var image = net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())) { image.writeToFile(path); }
                finish(mc, null);
            }
        } catch (Throwable failure) { finish(mc, failure); }
    }
    private void validate(net.minecraft.server.level.ServerPlayer player) {
        var runtime = TcModernApi.runtime().orElseThrow(); var level = player.serverLevel();
        var loader = net.minecraftforge.fml.ModList.get().getModContainerById("mfcl").orElseThrow().getModInfo();
        proof.put("mfclVersion", loader.getVersion().toString());
        proof.put("mfclRuntime", loader.getOwningFile().getFile().getFilePath().toString());
        level.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(0, level.getServer()); level.setDayTime(6000);
        var origin = player.blockPosition().offset(0,30,8);
        var nativeState = runtime.hostBlock("Thaumcraft:blockMagicalLeaves",0,BlockState.class).orElseThrow();
        require(nativeState.getBlock() instanceof MagicalLeavesBlock, "Original leaf ID is not a native LeavesBlock");
        var leaves = (MagicalLeavesBlock) nativeState.getBlock();
        require(nativeState.is(BlockTags.LEAVES), "Native leaves tag missing");
        var log = runtime.hostBlock("Thaumcraft:blockMagicalLog",0,BlockState.class).orElseThrow();
        require(log.is(BlockTags.LOGS), "Original magical logs do not support native leaves");
        var world = runtime.accessor(OldWorld.class,runtime.mappings().toLegacy(LegacyMappings.WORLD,level).orElseThrow());
        for (int meta=0;meta<16;meta++) {
            var state = runtime.hostBlock("Thaumcraft:blockMagicalLeaves",meta,BlockState.class).orElseThrow();
            level.setBlock(origin,state,2);
            require(world.metadata(origin.getX(),origin.getY(),origin.getZ())==meta,"Legacy metadata round trip differs at "+meta);
        }
        proof.put("nativeLeavesSameIdentityAll16MetadataStates",true);

        level.setBlock(origin,log,3);
        for(int d=1;d<=6;d++) level.setBlock(origin.east(d),leaves.fromLegacy(d&1),3);
        for(int pass=0;pass<7;pass++) for(int d=1;d<=6;d++) leaves.tick(level.getBlockState(origin.east(d)),level,origin.east(d),level.random);
        for(int d=1;d<=6;d++) require(level.getBlockState(origin.east(d)).getValue(LeavesBlock.DISTANCE)==d,"Incorrect native support distance "+d);
        // Simulate an old saved canopy whose distance property has not yet been initialized.
        for(int d=1;d<=6;d++) level.setBlock(origin.east(d),leaves.fromLegacy(d&1),2);
        leaves.randomTick(level.getBlockState(origin.east(6)),level,origin.east(6),level.random);
        require(level.getBlockState(origin.east(6)).is(leaves),"Old supported canopy decayed before distance initialization");
        level.removeBlock(origin,false);
        for(int d=1;d<=6;d++) if(level.getBlockState(origin.east(d)).is(leaves))
            leaves.randomTick(level.getBlockState(origin.east(d)),level,origin.east(d),level.random);
        for(int d=1;d<=6;d++) require(!level.getBlockState(origin.east(d)).is(leaves),"Unsupported leaf did not decay");
        proof.put("nativeDistancesDecayAndExistingCanopySafety",true);

        var saved = new CompoundTag(); saved.putString("Name","thaumcraft:block_magical_leaves");
        var properties = new CompoundTag(); properties.putString("legacy_meta","5"); saved.put("Properties",properties);
        var oldPersistent = NbtUtils.readBlockState(level.holderLookup(Registries.BLOCK),saved);
        level.setBlock(origin,oldPersistent,2); leaves.randomTick(oldPersistent,level,origin,level.random);
        require(level.getBlockState(origin).is(leaves),"Old player-placed leaf decayed");
        leaves.tick(level.getBlockState(origin),level,origin,level.random);
        require(level.getBlockState(origin).getValue(LeavesBlock.PERSISTENT),"Old persistent metadata did not migrate");
        var wet = leaves.fromLegacy(1).setValue(LeavesBlock.WATERLOGGED,true).setValue(LeavesBlock.DISTANCE,1);
        level.setBlock(origin,wet,2);
        world.metadata(origin.getX(),origin.getY(),origin.getZ(),9,2);
        require(level.getBlockState(origin).getValue(LeavesBlock.WATERLOGGED)
                && level.getBlockState(origin).getValue(LeavesBlock.DISTANCE)==1,"Old metadata write erased host leaf properties");
        var roundTrip = NbtUtils.readBlockState(level.holderLookup(Registries.BLOCK),NbtUtils.writeBlockState(level.getBlockState(origin)));
        require(roundTrip.equals(level.getBlockState(origin)),"Native leaf NBT changed state");
        proof.put("oldPersistentSaveAndWaterloggedMetadataWrites",true);

        var silk = new ItemStack(Items.DIAMOND_AXE); silk.enchant(Enchantments.SILK_TOUCH,1);
        var counts = new ArrayList<Integer>();
        for(int species=0;species<2;species++) {
            var state = leaves.fromLegacy(species);
            for(var tool : List.of(new ItemStack(Items.SHEARS),silk)) {
                var drops = Block.getDrops(state,level,origin,null,player,tool);
                require(drops.size()==1,"Shears/silk did not drop exactly one leaf");
                identity(runtime,drops.get(0),"Thaumcraft:blockMagicalLeaves",species);
            }
            level.setBlock(origin,state,2);
            identity(runtime,leaves.onSheared(player,new ItemStack(Items.SHEARS),level,origin,0).get(0),"Thaumcraft:blockMagicalLeaves",species);
            int saplings=0;
            for(int i=0;i<8192;i++) for(var drop : Block.getDrops(state,level,origin,null,player,ItemStack.EMPTY)) {
                identity(runtime,drop,"Thaumcraft:blockCustomPlant",species); saplings+=drop.getCount();
            }
            require(saplings>0,"Unflagged native leaves dropped no saplings"); counts.add(saplings);
        }
        proof.put("shearsSilkAndBothOriginalSaplings",counts);
        player.setGameMode(GameType.SURVIVAL); player.getInventory().clearContent(); player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(Items.SHEARS));
        for(int species=0;species<2;species++) {
            for(var entity:level.getEntitiesOfClass(ItemEntity.class,new AABB(origin).inflate(10))) entity.discard();
            level.setBlock(origin,leaves.fromLegacy(species),3);
            require(player.gameMode.destroyBlock(origin),"Native shears harvest failed");
            var drops=level.getEntitiesOfClass(ItemEntity.class,new AABB(origin).inflate(2));
            require(drops.size()==1,"Native player harvest duplicated or lost leaf drops");
            identity(runtime,drops.get(0).getItem(),"Thaumcraft:blockMagicalLeaves",species); drops.get(0).discard();
        }
        proof.put("realPlayerHarvestExactlyOneCorrectLeaf",true);
        player.setGameMode(GameType.CREATIVE);

        for(int species=0;species<2;species++) {
            var at=origin.offset(species*32,0,24);
            for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++) level.setBlock(at.offset(x,-1,z),Blocks.DIRT.defaultBlockState(),2);
            var sapling=runtime.hostBlock("Thaumcraft:blockCustomPlant",species,BlockState.class).orElseThrow();
            level.setBlock(at,sapling,3);
            for(int tick=0;tick<256 && level.getBlockState(at).is(sapling.getBlock());tick++) {
                var state=level.getBlockState(at);state.getBlock().randomTick(state,level,at,RandomSource.create(42L+tick));
            }
            require(!level.getBlockState(at).is(sapling.getBlock()) && countLeaves(level,at,leaves)>0,"Original sapling did not grow native leaves: "+species);
        }
        proof.put("bothOriginalSaplingsGenerateNativeLeaves",true);
        for(String tree:List.of("greatwood","silverwood")) {
            var at=origin.offset(tree.equals("greatwood")?0:32,0,-32);
            for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++) level.setBlock(at.offset(x,-1,z),Blocks.DIRT.defaultBlockState(),2);
            var feature=level.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE)
                    .getHolderOrThrow(ResourceKey.create(Registries.CONFIGURED_FEATURE,ResourceLocation.fromNamespaceAndPath("tc4modern",tree))).value();
            require(feature.place(level,level.getChunkSource().getGenerator(),RandomSource.create(42),at),"Modern feature failed: "+tree);
            require(countLeaves(level,at,leaves)>0,"Modern feature omitted native leaves: "+tree);
        }
        proof.put("bothModernTreeFeaturesGenerateNativeLeaves",true);
        player.teleportTo(level,origin.getX()+12.5,origin.getY()+7,origin.getZ()+9,145,8);
    }
    private static int countLeaves(ServerLevel level,BlockPos origin,Block leaves) {
        int count=0;for(var pos:BlockPos.betweenClosed(origin.offset(-10,0,-10),origin.offset(10,35,10)))if(level.getBlockState(pos).is(leaves))count++;
        return count;
    }
    private static void identity(LegacyRuntime runtime,ItemStack stack,String key,int meta) {
        var old=runtime.snapshot(runtime.mappings().toLegacy(LegacyMappings.ITEM_STACK,stack).orElseThrow());
        require(old.key().equals(key)&&old.metadata()==meta&&old.count()==1,"Wrong original drop: "+old);
    }
    private static void require(boolean value,String message) { if(!value)throw new IllegalStateException(message); }
    private void finish(Minecraft mc,Throwable failure) {
        stage=99;proof.put("status",failure==null?"PASS":"FAIL");
        if(failure!=null){proof.put("failure",failure.toString());LogUtils.getLogger().error("TC4_NATIVE_LEAVES_FAIL",failure);}
        try {var path=mc.gameDirectory.toPath().resolve("logs/tc4modern/native-leaves-smoke.json");Files.createDirectories(path.getParent());
            Files.writeString(path,new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(proof));}
        catch(java.io.IOException error){throw new IllegalStateException(error);}
        LogUtils.getLogger().info("TC4_NATIVE_LEAVES_RESULT {}",proof);
        if(mc.level!=null){mc.level.disconnect();mc.clearLevel();}mc.stop();
    }
}
