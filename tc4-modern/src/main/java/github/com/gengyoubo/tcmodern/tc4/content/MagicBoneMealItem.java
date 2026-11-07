package github.com.gengyoubo.tcmodern.tc4.content;

import com.mojang.logging.LogUtils;
import github.com.gengyoubo.mfcl.api.LegacyMappings;
import github.com.gengyoubo.mfcl.api.LegacyObject;
import github.com.gengyoubo.mfcl.api.LegacyRuntime;
import github.com.gengyoubo.mfcl.api.mla.*;
import github.com.gengyoubo.tcmodern.api.TcModernApi;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;

/** Bone meal for the original greatwood (metadata 0) and silverwood (metadata 1) saplings, which vanilla bone meal ignores. */
public final class MagicBoneMealItem extends Item {
    static final String SAPLING = "Thaumcraft:blockCustomPlant";
    /** Vanilla bone meal's chance to advance a sapling. */
    static final float GROWTH_CHANCE = 0.45F;

    @LegacyType("thaumcraft.common.blocks.BlockCustomPlant") public interface Sapling {
        /** Restores the sapling itself when the tree does not fit. */
        @LegacyMethod(name="growGreatTree",descriptor="(Lnet/minecraft/world/World;IIILjava/util/Random;)V") void greatwood(LegacyObject world,int x,int y,int z,LegacyObject random);
        @LegacyMethod(name="growSilverTree",descriptor="(Lnet/minecraft/world/World;IIILjava/util/Random;)V") void silverwood(LegacyObject world,int x,int y,int z,LegacyObject random);
    }
    @LegacyType("net.minecraft.world.World") public interface OldWorld {
        @LegacyField(name="field_73012_v",descriptor="Ljava/util/Random;") LegacyObject random();
    }

    public MagicBoneMealItem() { super(new Properties()); }

    @Override public InteractionResult useOn(UseOnContext context) {
        var level = context.getLevel();
        var pos = context.getClickedPos();
        var clicked = level.getBlockState(pos);
        if (clicked.getBlock() instanceof GiantSaplingBlock sapling) {
            if (level instanceof ServerLevel server) {
                if (server.random.nextFloat() < GROWTH_CHANCE) sapling.performBonemeal(server, server.random, pos, clicked);
                var player = context.getPlayer();
                if (player == null || !player.getAbilities().instabuild) context.getItemInHand().shrink(1);
                server.levelEvent(LevelEvent.PARTICLES_AND_SOUND_PLANT_GROWTH, pos, 0);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        var runtime = TcModernApi.runtime().orElse(null);
        if (runtime == null) return InteractionResult.PASS;
        int species = species(runtime, level.getBlockState(pos));
        if (species < 0) return InteractionResult.PASS;
        if (level instanceof ServerLevel server) {
            if (server.random.nextFloat() < GROWTH_CHANCE) {
                try {
                    var world = runtime.mappings().toLegacy(LegacyMappings.WORLD, server).orElseThrow();
                    var block = runtime.find(LegacyRuntime.Registry.BLOCK, SAPLING).orElseThrow();
                    var sapling = runtime.accessor(Sapling.class, block);
                    var random = runtime.accessor(OldWorld.class, world).random();
                    if (species == 0) sapling.greatwood(world, pos.getX(), pos.getY(), pos.getZ(), random);
                    else sapling.silverwood(world, pos.getX(), pos.getY(), pos.getZ(), random);
                } catch (RuntimeException failure) {
                    LogUtils.getLogger().warn("Magic bone meal could not grow the sapling at {}", pos, failure);
                    return InteractionResult.FAIL;
                }
            }
            var player = context.getPlayer();
            if (player == null || !player.getAbilities().instabuild) context.getItemInHand().shrink(1);
            server.levelEvent(LevelEvent.PARTICLES_AND_SOUND_PLANT_GROWTH, pos, 0);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** 0 greatwood, 1 silverwood, -1 anything else. */
    private static int species(LegacyRuntime runtime, BlockState state) {
        for (int species = 0; species < 2; species++)
            if (runtime.hostBlock(SAPLING, species, BlockState.class).filter(state::equals).isPresent()) return species;
        return -1;
    }
}
