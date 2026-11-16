package github.com.gengyoubo.tcmodern.tc4.testing;

import com.mojang.logging.LogUtils;
import github.com.gengyoubo.tcmodern.tc4.Tc4Modern;
import github.com.gengyoubo.tcmodern.tc4.entity.golem.*;
import github.com.gengyoubo.tcmodern.tc4.init.Tc4Entities;
import github.com.gengyoubo.tcmodern.api.TcModernApi;
import github.com.gengyoubo.mfcl.api.LegacyMappings;
import github.com.gengyoubo.mfcl.api.LegacyObject;
import github.com.gengyoubo.mfcl.api.mla.*;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

/** Disposable dedicated-server scenarios; results are observations, including unfinished features. */
@Mod.EventBusSubscriber(modid = Tc4Modern.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class GolemServerProbe {
    private MinecraftServer server;
    private int tick;
    private final Map<String, Object> result = new LinkedHashMap<>();
    private BlockPos crop, trunk, branch, source, sink, water;
    private BlockPos jarSource, jarSink, tube, tank, usedBlock, pipeSource, placement, useSource;
    private ModernGolem fisher, harvester, pipeCarrier, user;
    private final java.util.Set<String> fishingDrops = new java.util.LinkedHashSet<>();

    @SubscribeEvent public static void setup(FMLCommonSetupEvent event) {
        if (!Boolean.getBoolean("tc4modern.golemSmokeTest")) return;
        var probe = new GolemServerProbe();
        MinecraftForge.EVENT_BUS.addGenericListener(net.minecraft.world.level.block.entity.BlockEntity.class, GolemServerProbe::attachTank);
        MinecraftForge.EVENT_BUS.addListener(probe::started);
        MinecraftForge.EVENT_BUS.addListener(probe::tick);
    }
    private void started(ServerStartedEvent event) { server = event.getServer(); }
    private void tick(TickEvent.ServerTickEvent event) {
        if (server == null || tick < 0 || event.phase != TickEvent.Phase.END) return;
        try {
            tick++;
            var level = server.overworld();
            if (tick == 30) setupWorld(level);
            if (tick > 30 && water != null) level.getEntitiesOfClass(ItemEntity.class, new AABB(water).inflate(10)).forEach(e -> fishingDrops.add(e.getItem().toString()));
            if (tick == 230) {
                result.put("originalGolemItemSpawnedModernAfterTicks", !level.getEntitiesOfClass(ModernGolem.class, new AABB(placement).inflate(10)).isEmpty());
                var chest = (ChestBlockEntity) level.getBlockEntity(source);
                result.put("sortSourceWhileSinkFull", chest.getItem(0).getCount());
                var target = (ChestBlockEntity) level.getBlockEntity(sink);
                target.setItem(0, new ItemStack(Items.DIAMOND, 60));
            }
            if (tick == 430) {
                result.put("sortSourceAfterSpace", ((ChestBlockEntity) level.getBlockEntity(source)).getItem(0).getCount());
                result.put("sortSinkAfterSpace", ((ChestBlockEntity) level.getBlockEntity(sink)).getItem(0).getCount());
                level.removeBlock(sink, false);
            }
            if (tick == 830) {
                result.put("harvestBlock", level.getBlockState(crop).toString());
                result.put("harvesterPosition", harvester.blockPosition().toShortString());
                result.put("trunkRemaining", level.getBlockState(trunk).is(Blocks.OAK_LOG));
                result.put("branchRemaining", level.getBlockState(branch).is(Blocks.OAK_LOG));
                result.put("sortSourceAfterContainerRemoval", ((ChestBlockEntity) level.getBlockEntity(source)).getItem(0).getCount());
                result.put("fishingPosition", fisher.fishingPosition().map(BlockPos::toShortString).orElse("none"));
                result.put("bobbers", level.getEntitiesOfClass(GolemBobber.class, new AABB(water).inflate(10)).size());
                result.put("fishingDropsObserved", fishingDrops);
                result.put("useCoreWaterPlaced", level.getFluidState(usedBlock).is(net.minecraft.tags.FluidTags.WATER));
                result.put("useCoreSourceItem", ((ChestBlockEntity) level.getBlockEntity(useSource)).getItem(0).toString());
                result.put("useCoreCarried", user.carriedForDisplay().toString());
                result.put("liquidTankAmount", tank(level, tank).getFluidAmount());
                result.put("essentiaSourceAmount", jarAmount(level, jarSource));
                result.put("essentiaSinkAmount", jarAmount(level, jarSink));
                var runtime = TcModernApi.runtime().orElseThrow();
                var world = runtime.mappings().toLegacy(LegacyMappings.WORLD, level).orElseThrow();
                var tubeTile = runtime.accessor(OldWorld.class, world).tile(tube.getX(), tube.getY(), tube.getZ());
                result.put("tubeImplementsAspectContainer", tubeTile != null && runtime.values().isInstance(tubeTile, "thaumcraft.api.aspects.IAspectContainer"));
                result.put("pipeSourceAmount", jarAmount(level, pipeSource));
                result.put("pipeCarrierHasEssentia", pipeCarrier.hasCarriedEssentia());
                finish(null);
            }
        } catch (Throwable error) { finish(error); }
    }
    private void platform(ServerLevel level, BlockPos center) {
        for (int x = -5; x <= 5; x++) for (int z = -5; z <= 5; z++) {
            BlockPos p = center.offset(x, 0, z);
            level.setChunkForced(p.getX() >> 4, p.getZ() >> 4, true);
            level.setBlockAndUpdate(p, Blocks.STONE.defaultBlockState());
            for (int y = 1; y <= 5; y++) level.setBlockAndUpdate(p.above(y), Blocks.AIR.defaultBlockState());
        }
    }
    private ModernGolem golem(ServerLevel level, BlockPos position, int core) {
        var golem = Tc4Entities.GOLEMS.get(GolemMaterial.WOOD).get().create(level);
        if (golem == null) throw new IllegalStateException("Cannot create golem");
        golem.moveTo(position.getX() + .5, position.getY(), position.getZ() + .5, 0, 0);
        var tag = new CompoundTag(); tag.putInt("Tc4Core", core);
        golem.readAdditionalSaveData(tag);
        golem.setHome(position);
        if (!level.addFreshEntity(golem)) throw new IllegalStateException("Cannot spawn golem");
        return golem;
    }
    private void setupWorld(ServerLevel level) {
        BlockPos base = new BlockPos(1000, 120, 1000);
        for (int i = 0; i < 9; i++) platform(level, base.offset(i * 32, 0, 0));
        crop = base.offset(1, 1, 0);
        level.setBlockAndUpdate(crop.below(), Blocks.FARMLAND.defaultBlockState());
        level.setBlockAndUpdate(crop, Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7));
        harvester = golem(level, base.above(), 3);
        var tree = base.offset(32, 0, 0);
        trunk = tree.offset(1, 1, 0); branch = trunk.above(3).east();
        for (int y = 0; y < 4; y++) level.setBlockAndUpdate(trunk.above(y), Blocks.OAK_LOG.defaultBlockState());
        level.setBlockAndUpdate(branch, Blocks.OAK_LOG.defaultBlockState());
        level.setBlockAndUpdate(trunk.above(4), Blocks.OAK_LEAVES.defaultBlockState());
        golem(level, tree.above(), 7);
        var sorting = base.offset(64, 0, 0);
        source = sorting.offset(-1, 1, 0); sink = sorting.offset(1, 1, 0);
        level.setBlockAndUpdate(source, Blocks.CHEST.defaultBlockState());
        level.setBlockAndUpdate(sink, Blocks.CHEST.defaultBlockState());
        ((ChestBlockEntity) level.getBlockEntity(source)).setItem(0, new ItemStack(Items.DIAMOND, 16));
        var target = (ChestBlockEntity) level.getBlockEntity(sink);
        for (int i = 0; i < target.getContainerSize(); i++) target.setItem(i, new ItemStack(Items.DIAMOND, 64));
        var sorter = golem(level, sorting.above(), 10);
        sorter.setDestination(source, Direction.UP); sorter.toggleSortMarker(sink, Direction.UP);
        var fishing = base.offset(96, 0, 0);
        water = fishing.east();
        level.setBlockAndUpdate(water, Blocks.WATER.defaultBlockState());
        fisher = golem(level, fishing.above(), 11);
        var liquid = base.offset(128, 0, 0);
        tank = liquid.offset(-1, 1, 0);
        var liquidSource = liquid.offset(1, 0, 0);
        level.setBlockAndUpdate(tank, Blocks.CHEST.defaultBlockState());
        level.setBlockAndUpdate(liquidSource, Blocks.WATER.defaultBlockState());
        var carrier = golem(level, liquid.above(), 5);
        carrier.setDestination(tank, Direction.UP); carrier.setMarkedContainer(liquidSource, Direction.UP);
        var essentia = base.offset(160, 0, 0);
        jarSource = essentia.offset(-1, 1, 0); jarSink = essentia.offset(1, 1, 0);
        var runtime = TcModernApi.runtime().orElseThrow();
        var jar = runtime.hostBlock("Thaumcraft:blockJar", 0, net.minecraft.world.level.block.state.BlockState.class).orElseThrow();
        level.setBlockAndUpdate(jarSource, jar); level.setBlockAndUpdate(jarSink, jar);
        var world = runtime.mappings().toLegacy(LegacyMappings.WORLD, level).orElseThrow();
        var oldWorld = runtime.accessor(OldWorld.class, world);
        var aspect = runtime.accessor(OldAspect.class).get("ignis");
        runtime.accessor(OldContainer.class, oldWorld.tile(jarSource.getX(), jarSource.getY(), jarSource.getZ())).add(aspect, 5);
        var alchemist = golem(level, essentia.above(), 6);
        alchemist.setDestination(jarSink, Direction.UP); alchemist.setMarkedContainer(jarSource, Direction.UP);
        var use = base.offset(192, 0, 0);
        useSource = use.offset(-1, 1, 0);
        usedBlock = use.offset(1, 1, 0);
        level.setBlockAndUpdate(useSource, Blocks.CHEST.defaultBlockState());
        ((ChestBlockEntity) level.getBlockEntity(useSource)).setItem(0, new ItemStack(Items.WATER_BUCKET));
        user = golem(level, use.above(), 8);
        user.setDestination(useSource, Direction.UP); user.setMarkedContainer(usedBlock.below(), Direction.UP);
        placement = base.offset(224, 0, 0);
        var player = new net.minecraftforge.common.util.FakePlayer(level, new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "[GolemProbe]"));
        player.moveTo(placement.getX() + .5, placement.getY() + 1, placement.getZ() - 1.5, 0, 0);
        var item = runtime.mappings().toHost(LegacyMappings.ITEM_STACK, runtime.stack(new github.com.gengyoubo.mfcl.api.LegacyStack("Thaumcraft:ItemGolemPlacer", 0, 1, "")), ItemStack.class).orElseThrow();
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, item);
        var hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(placement).add(0, .5, 0), Direction.UP, placement, false);
        var interaction = player.gameMode.useItemOn(player, level, item, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
        result.put("originalGolemItemUseResult", interaction.toString());
        var pipeTest = base.offset(256, 0, 0);
        pipeSource = pipeTest.offset(-1, 1, 0); tube = pipeTest.offset(1, 1, 0);
        level.setBlockAndUpdate(pipeSource, jar);
        level.setBlockAndUpdate(tube, runtime.hostBlock("Thaumcraft:blockTube", 0, net.minecraft.world.level.block.state.BlockState.class).orElseThrow());
        runtime.accessor(OldContainer.class, oldWorld.tile(pipeSource.getX(), pipeSource.getY(), pipeSource.getZ())).add(aspect, 5);
        pipeCarrier = golem(level, pipeTest.above(), 6);
        pipeCarrier.setDestination(tube, Direction.UP); pipeCarrier.setMarkedContainer(pipeSource, Direction.UP);
        result.put("setup", "complete");
    }
    private static int jarAmount(ServerLevel level, BlockPos pos) {
        var runtime = TcModernApi.runtime().orElseThrow();
        var world = runtime.mappings().toLegacy(LegacyMappings.WORLD, level).orElseThrow();
        var tile = runtime.accessor(OldWorld.class, world).tile(pos.getX(), pos.getY(), pos.getZ());
        return runtime.accessor(OldJar.class, tile).amount();
    }
    private static void attachTank(net.minecraftforge.event.AttachCapabilitiesEvent<net.minecraft.world.level.block.entity.BlockEntity> event) {
        if (!(event.getObject() instanceof ChestBlockEntity)) return;
        var storage = new net.minecraftforge.fluids.capability.templates.FluidTank(8000);
        var optional = net.minecraftforge.common.util.LazyOptional.<net.minecraftforge.fluids.capability.IFluidHandler>of(() -> storage);
        event.addCapability(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("tc4modern", "golem_probe_tank"), new net.minecraftforge.common.capabilities.ICapabilityProvider() {
            @Override public <T> net.minecraftforge.common.util.LazyOptional<T> getCapability(net.minecraftforge.common.capabilities.Capability<T> cap, Direction side) {
                return cap == net.minecraftforge.common.capabilities.ForgeCapabilities.FLUID_HANDLER ? optional.cast() : net.minecraftforge.common.util.LazyOptional.empty();
            }
        });
        event.addListener(optional::invalidate);
    }
    private static net.minecraftforge.fluids.capability.templates.FluidTank tank(ServerLevel level, BlockPos pos) {
        return (net.minecraftforge.fluids.capability.templates.FluidTank) level.getBlockEntity(pos).getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.FLUID_HANDLER).orElseThrow(IllegalStateException::new);
    }
    @LegacyType("net.minecraft.world.World") public interface OldWorld {
        @LegacyMethod(name="func_147438_o", descriptor="(III)Lnet/minecraft/tileentity/TileEntity;") LegacyObject tile(int x, int y, int z);
    }
    @LegacyType("thaumcraft.api.aspects.Aspect") public interface OldAspect {
        @LegacyMethod(name="getAspect", descriptor="(Ljava/lang/String;)Lthaumcraft/api/aspects/Aspect;", isStatic=true) LegacyObject get(String tag);
    }
    @LegacyType("thaumcraft.api.aspects.IAspectContainer") public interface OldContainer {
        @LegacyMethod(name="addToContainer", descriptor="(Lthaumcraft/api/aspects/Aspect;I)I") int add(LegacyObject aspect, int amount);
    }
    @LegacyType("thaumcraft.common.tiles.TileJarFillable") public interface OldJar {
        @LegacyField(name="amount", descriptor="I") int amount();
    }
    private void finish(Throwable error) {
        tick = -1;
        if (error != null) { result.put("error", error.toString()); LogUtils.getLogger().error("Golem server probe failed", error); }
        result.put("status", error == null ? "OBSERVED" : "ERROR");
        try {
            var path = server.getServerDirectory().toPath().resolve("tc4modern/golem-server-probe.json");
            Files.createDirectories(path.getParent());
            Files.writeString(path, new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(result));
        } catch (Exception e) { LogUtils.getLogger().error("Could not write golem report", e); }
        LogUtils.getLogger().info("TC4_GOLEM_SERVER_RESULT: {}", result);
        server.halt(false);
    }
}
