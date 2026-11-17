package github.com.gengyoubo.tcmodern.tc4.client.testing;

import github.com.gengyoubo.tcmodern.api.TcModernApi;
import github.com.gengyoubo.tcmodern.api.aspect.TcPlayerAspects;
import github.com.gengyoubo.tcmodern.api.research.TcResearch;
import github.com.gengyoubo.tcmodern.tc4.client.deconstruction.ModernDeconstructionScreen;
import github.com.gengyoubo.tcmodern.tc4.content.ModernDeconstructionBlockEntity;
import github.com.gengyoubo.tcmodern.tc4.init.Tc4Blocks;
import github.com.gengyoubo.tcmodern.tc4.menu.ModernDeconstructionMenu;
import java.nio.file.Files;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.*;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Opt-in integrated-server check for the native deconstruction menu. */
@Mod.EventBusSubscriber(modid="tc4modern", value=Dist.CLIENT, bus=Mod.EventBusSubscriber.Bus.MOD)
public final class DeconstructionSmokeProbe {
    private int stage;
    private final long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.MINUTES.toNanos(4);
    private BlockPos pos;
    private CompletableFuture<?> pending;
    private int terraBefore;
    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        if (Boolean.getBoolean("tc4modern.deconstructionSmokeTest"))
            event.enqueueWork(() -> MinecraftForge.EVENT_BUS.addListener(new DeconstructionSmokeProbe()::tick));
    }
    private void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == 99) return;
        var mc=Minecraft.getInstance();
        try {
            require(System.nanoTime()<deadline,"Timed out at stage "+stage);
            if (pending != null && pending.isCompletedExceptionally()) pending.join();
            if (stage == 0 && mc.screen instanceof TitleScreen && mc.getOverlay() == null) {
                org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow()); mc.options.pauseOnLostFocus=false; mc.options.renderDistance().set(4);
                mc.createWorldOpenFlows().createFreshLevel("deconstruction-smoke-"+System.currentTimeMillis(),
                        new LevelSettings("Deconstruction smoke",GameType.SURVIVAL,false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),
                        new WorldOptions(42,false,false), access -> access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
                stage=1;
            } else if (stage == 1 && mc.player != null && mc.level != null && mc.screen == null) {
                pending=mc.getSingleplayerServer().submit(() -> {
                    var server=mc.getSingleplayerServer(); var level=server.overworld(); var player=server.getPlayerList().getPlayers().get(0);
                    pos=player.blockPosition().above(2); player.teleportTo(pos.getX()+0.5,pos.getY(),pos.getZ()+2.5);
                    level.setBlockAndUpdate(pos,Tc4Blocks.DECONSTRUCTION_Y.get().defaultBlockState());
                    var table=(ModernDeconstructionBlockEntity)level.getBlockEntity(pos);
                    net.minecraftforge.network.NetworkHooks.openScreen(player,table,pos);
                }); stage=2;
            } else if (stage == 2 && pending.isDone() && mc.screen instanceof ModernDeconstructionScreen) {
                pending.join(); pending=mc.getSingleplayerServer().submit(() -> {
                    var server=mc.getSingleplayerServer(); var player=server.getPlayerList().getPlayers().get(0); var runtime=TcModernApi.runtime().orElseThrow();
                    var table=(ModernDeconstructionBlockEntity)server.overworld().getBlockEntity(pos);
                    require(player.containerMenu instanceof ModernDeconstructionMenu,"Wrong server menu");
                    var menu=(ModernDeconstructionMenu)player.containerMenu;
                    require(!TcResearch.isComplete(runtime,player,"deconstruction_y"),"Fixture unexpectedly has the research");
                    terraBefore=TcPlayerAspects.pool(runtime,player,"terra");
                    table.items().setStackInSlot(0,new ItemStack(Items.STONE,2));
                    require(table.items().getStackInSlot(0).getCount()==2 && table.pendingCount()==0,"Input was consumed without processing time");
                    ModernDeconstructionBlockEntity.tick(server.overworld(),pos,table.getBlockState(),table);
                    require(table.progress()==1 && table.items().getStackInSlot(0).getCount()==2,"Progress did not start before consumption");
                    var unfinished=new ModernDeconstructionBlockEntity(pos,table.getBlockState()); unfinished.load(table.saveWithoutMetadata());
                    require(unfinished.progress()==1 && unfinished.items().getStackInSlot(0).getCount()==2,"Unfinished work did not persist");
                    table.items().setStackInSlot(0,ItemStack.EMPTY);
                    require(table.progress()==0,"Removing input did not reset progress");
                    table.items().setStackInSlot(0,new ItemStack(Items.STONE,2));
                }); stage=3;
            } else if (stage == 3 && pending.isDone() && mc.screen instanceof ModernDeconstructionScreen screen && screen.getMenu().hasPending()) {
                pending.join(); require(!screen.getMenu().results().isEmpty(),"Pending aspects were not shown to the client");
                pending=mc.getSingleplayerServer().submit(() -> {
                    var server=mc.getSingleplayerServer(); var player=server.getPlayerList().getPlayers().get(0); var runtime=TcModernApi.runtime().orElseThrow();
                    var table=(ModernDeconstructionBlockEntity)server.overworld().getBlockEntity(pos);
                    require(table.items().getStackInSlot(0).getCount()==1 && table.pendingCount()>0,"Unresearched player could not use the table");
                    var copy=new ModernDeconstructionBlockEntity(pos,table.getBlockState()); copy.load(table.saveWithoutMetadata());
                    require(copy.items().getStackInSlot(0).getCount()==1 && copy.pendingCount()==table.pendingCount(),"Input or result did not persist");
                    ((ModernDeconstructionMenu)player.containerMenu).clickMenuButton(player,1);
                    require(table.pendingCount()==0 && TcPlayerAspects.pool(runtime,player,"terra")>terraBefore,"Claim did not credit research");
                    require(!TcResearch.isComplete(runtime,player,"deconstruction_y"),"Using the table unlocked its research");
                }); stage=4;
            } else if (stage == 4 && pending.isDone()) {
                pending.join();
                pending=mc.getSingleplayerServer().submit(() -> {
                    var server=mc.getSingleplayerServer(); var player=server.getPlayerList().getPlayers().get(0);
                    var runtime=TcModernApi.runtime().orElseThrow();
                    player.closeContainer();
                    checkThaumicSponge(runtime, server.overworld(), player.blockPosition().offset(20, 4, 0));
                    checkFluxIngot(runtime, server.overworld(), player, player.blockPosition().offset(40, 4, 0));
                    TcResearch.completeAll(runtime,player);
                    var at=player.blockPosition().above(1);
                    server.overworld().setBlockAndUpdate(at,runtime.hostBlock("Thaumcraft:blockTable",15,net.minecraft.world.level.block.state.BlockState.class).orElseThrow());
                    player.gameMode.useItemOn(player,server.overworld(),ItemStack.EMPTY,InteractionHand.MAIN_HAND,
                            new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(at),net.minecraft.core.Direction.UP,at,false));
                    var menu=player.containerMenu;
                    require(menu != player.inventoryMenu,"Arcane workbench did not open");
                    var oldWand=runtime.stack(new github.com.gengyoubo.mfcl.api.LegacyStack("Thaumcraft:WandCasting",0,1,
                            "{rod:\"silverwood\",cap:\"thaumium\",aer:10000,ignis:10000,aqua:10000,terra:10000,ordo:10000,perditio:10000}"));
                    var wand=runtime.mappings().toHost(github.com.gengyoubo.mfcl.api.LegacyMappings.ITEM_STACK,oldWand,ItemStack.class).orElseThrow();
                    var oldTable=runtime.stack(new github.com.gengyoubo.mfcl.api.LegacyStack("Thaumcraft:blockTable",14,1,""));
                    var base=runtime.mappings().toHost(github.com.gengyoubo.mfcl.api.LegacyMappings.ITEM_STACK,oldTable,ItemStack.class).orElseThrow();
                    placeInMenu(menu,player,1,wand);
                    for (int variant=0; variant<2; variant++) {
                        for (int cell=0; cell<9; cell++) {
                            var item=cell<3?new ItemStack(variant==0?Items.GOLD_INGOT:Items.BLAZE_POWDER)
                                    :cell==4?base.copy():new ItemStack(Items.OAK_PLANKS);
                            placeInMenu(menu,player,cell+2,item);
                        }
                        menu.broadcastChanges();
                        var expected=variant==0?Tc4Blocks.DECONSTRUCTION_X.get().asItem():Tc4Blocks.DECONSTRUCTION_Y.get().asItem();
                        require(menu.getSlot(0).getItem().is(expected),"Arcane recipe did not produce the expected deconstruction table");
                        menu.clicked(0,0,net.minecraft.world.inventory.ClickType.PICKUP,player);
                        require(menu.getCarried().is(expected),"Could not take arcane crafting result");
                        menu.setCarried(ItemStack.EMPTY);
                        var id=net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("tc4modern",variant==0?"whole_aspects":"many_aspects");
                        var advancement=server.getAdvancements().getAdvancement(id);
                        require(advancement!=null && player.getAdvancements().getOrStartProgress(advancement).isDone(),"Arcane crafting advancement did not unlock: "+id);
                    }
                    player.closeContainer();
                }); stage=5;
            } else if (stage == 5 && pending.isDone()) { pending.join(); finish(mc,"PASS"); }
        } catch(Throwable failure) { failure.printStackTrace(); finish(mc,"FAIL: "+failure); }
    }
    private static void placeInMenu(net.minecraft.world.inventory.AbstractContainerMenu menu,net.minecraft.server.level.ServerPlayer player,int slot,ItemStack item) {
        menu.setCarried(item);
        menu.clicked(slot,0,net.minecraft.world.inventory.ClickType.PICKUP,player);
        require(menu.getCarried().isEmpty(),"Could not insert arcane ingredient into slot "+slot);
    }
    private static void checkThaumicSponge(github.com.gengyoubo.mfcl.api.LegacyRuntime runtime, net.minecraft.server.level.ServerLevel level, BlockPos pos) {
        var sponge = Tc4Blocks.THAUMIC_SPONGE.get();
        var goo = runtime.hostBlock("Thaumcraft:blockFluxGoo", 3, net.minecraft.world.level.block.state.BlockState.class).orElseThrow();
        var gas = runtime.hostBlock("Thaumcraft:blockFluxGas", 7, net.minecraft.world.level.block.state.BlockState.class).orElseThrow();
        level.setBlockAndUpdate(pos, sponge.defaultBlockState());
        require(level.getBlockTicks().hasScheduledTick(pos, sponge), "Placed thaumic sponge did not schedule automatic absorption");
        level.setBlockAndUpdate(pos.east(2), goo);
        level.setBlockAndUpdate(pos.west(), gas);
        sponge.tick(level.getBlockState(pos), level, pos, level.random);
        require(level.getBlockState(pos).is(Tc4Blocks.SATURATED_THAUMIC_SPONGE.get()), "Thaumic sponge did not saturate");
        require(level.isEmptyBlock(pos.east(2)), "Thaumic sponge did not prioritize flux goo");
        require(level.getBlockState(pos.west()).equals(runtime.hostBlock("Thaumcraft:blockFluxGas", 3, net.minecraft.world.level.block.state.BlockState.class).orElseThrow()), "Thaumic sponge removed the wrong amount of flux gas");
        var partial = pos.east(10);
        level.setBlockAndUpdate(partial, sponge.defaultBlockState());
        level.setBlockAndUpdate(partial.east(), runtime.hostBlock("Thaumcraft:blockFluxGoo", 1, net.minecraft.world.level.block.state.BlockState.class).orElseThrow());
        sponge.tick(level.getBlockState(partial), level, partial, level.random);
        var state = level.getBlockState(partial);
        require(state.getValue(github.com.gengyoubo.tcmodern.tc4.content.ThaumicSpongeBlock.ABSORBED) == 2, "Partial flux absorption was not retained");
        var drops = net.minecraft.world.level.block.Block.getDrops(state, level, partial, null);
        require(drops.size() == 1 && drops.get(0).getOrCreateTag().getCompound("BlockStateTag").getString("absorbed").equals("2"), "Partial absorption was lost when broken");
        var furnace = new net.minecraft.world.SimpleContainer(new ItemStack(Tc4Blocks.SATURATED_THAUMIC_SPONGE.get()));
        var drying = level.getRecipeManager().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.SMELTING, furnace, level).orElseThrow();
        require(drying.getResultItem(level.registryAccess()).is(Tc4Blocks.THAUMIC_SPONGE.get().asItem()), "Drying did not restore the thaumic sponge");
    }
    private static void checkFluxIngot(github.com.gengyoubo.mfcl.api.LegacyRuntime runtime, net.minecraft.server.level.ServerLevel level, net.minecraft.server.level.ServerPlayer player, BlockPos pos) {
        require(!TcResearch.isComplete(runtime, player, "flux_recycling"), "Flux research unexpectedly unlocked");
        var locked = fluxCrucible(level, pos, false);
        locked.synthesize(player);
        var area = new net.minecraft.world.phys.AABB(pos).inflate(2);
        require(level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, area, item -> item.getItem().is(github.com.gengyoubo.tcmodern.tc4.init.Tc4Items.FLUX_INGOT.get())).isEmpty(), "Flux ingot bypassed its research");
        TcResearch.complete(runtime, player, "flux_recycling");
        var ready = fluxCrucible(level, pos.east(8), true);
        ready.synthesize(player);
        var result = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(pos.east(8)).inflate(2), item -> item.getItem().is(github.com.gengyoubo.tcmodern.tc4.init.Tc4Items.FLUX_INGOT.get()));
        require(result.size() == 1 && result.get(0).getItem().getCount() == 1, "Flux ingot synthesis failed");
        require(ready.materials().isEmpty() && ready.essentia().getOrDefault("praecantatio", 0) == 0 && ready.water() == 950, "Flux ingot synthesis did not consume the required materials and essentia");
    }
    private static github.com.gengyoubo.tcmodern.tc4.content.IntermediateCrucibleBlockEntity fluxCrucible(net.minecraft.server.level.ServerLevel level, BlockPos pos, boolean reverse) {
        level.setBlockAndUpdate(pos, Tc4Blocks.INTERMEDIATE_CRUCIBLE.get().defaultBlockState());
        var crucible = (github.com.gengyoubo.tcmodern.tc4.content.IntermediateCrucibleBlockEntity) level.getBlockEntity(pos);
        require(crucible != null && crucible.fill(new ItemStack(Items.WATER_BUCKET)), "Could not prepare crucible water");
        var tag = crucible.saveWithoutMetadata();
        tag.putString("Mode", "SYNTHESIS");
        tag.putShort("Heat", (short) 200);
        var aspects = new net.minecraft.nbt.CompoundTag(); aspects.putInt("praecantatio", 10); tag.put("Aspects", aspects);
        var materials = new net.minecraft.nbt.ListTag();
        var iron = new ItemStack(Items.IRON_INGOT); var sponge = new ItemStack(Tc4Blocks.SATURATED_THAUMIC_SPONGE.get());
        materials.add((reverse ? iron : sponge).save(new net.minecraft.nbt.CompoundTag()));
        materials.add((reverse ? sponge : iron).save(new net.minecraft.nbt.CompoundTag()));
        tag.put("Materials", materials); crucible.load(tag);
        return crucible;
    }
    private static void require(boolean value,String message) { if (!value) throw new IllegalStateException(message); }
    private void finish(Minecraft mc,String result) {
        stage=99; try { Files.writeString(mc.gameDirectory.toPath().resolve("deconstruction-smoke.txt"),result); } catch(Exception ignored) { }
        mc.stop();
    }
}
