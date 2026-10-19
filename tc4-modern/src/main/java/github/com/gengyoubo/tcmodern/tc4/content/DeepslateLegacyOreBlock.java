package github.com.gengyoubo.tcmodern.tc4.content;

import github.com.gengyoubo.mfcl.api.LegacyMappings;
import github.com.gengyoubo.mfcl.api.LegacyObject;
import github.com.gengyoubo.mfcl.api.LegacyStack;
import github.com.gengyoubo.tcmodern.api.TcModernApi;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.stats.Stats;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

/**
 * A modern deepslate variant of an original Thaumcraft ore or infused stone.
 *
 * <p>Cinnabar drops the deepslate ore itself. Shard and amber drops are resolved at runtime
 * through MFCL's public item mapping. Silk Touch preserves the deepslate block itself. No original TC4 behavior
 * such as the special cluster mining result of special TC4 tools is reproduced here.</p>
 */
public final class DeepslateLegacyOreBlock extends Block {
    /** Cinnabar drops itself; infused stone and amber use original TC4 resources. */
    public enum Drop {
        CINNABAR(null),
        INFUSED("Thaumcraft:ItemShard"),
        AMBER("Thaumcraft:ItemResource");

        private final String legacyKey;

        Drop(String legacyKey) {
            this.legacyKey = legacyKey;
        }
    }

    private final Drop drop;
    private final int legacyMetadata;

    public DeepslateLegacyOreBlock(Drop drop, int legacyMetadata) {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.DEEPSLATE)
                .strength(1.5F, 5.0F)
                .requiresCorrectToolForDrops()
                .sound(SoundType.DEEPSLATE));
        this.drop = drop;
        this.legacyMetadata = legacyMetadata;
    }

    @Override
    public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state,
                              @Nullable BlockEntity blockEntity, ItemStack tool) {
        if (level instanceof ServerLevel server) {
            Block.popResource(server, pos, resolveDrop(state, tool, server.getRandom()));
        }
        player.awardStat(Stats.BLOCK_MINED.get(this));
        player.causeFoodExhaustion(0.005F);
    }

    @Override
    public int getExpDrop(BlockState state, LevelReader level, RandomSource random, BlockPos pos, int fortune, int silktouch) {
        if (silktouch != 0) return 0;
        return switch (drop) {
            case CINNABAR -> 0;
            case INFUSED -> random.nextInt(4);
            case AMBER -> 1 + random.nextInt(4);
        };
    }

    private ItemStack resolveDrop(BlockState state, ItemStack tool, RandomSource random) {
        if (!tool.isCorrectToolForDrops(state)) return ItemStack.EMPTY;
        if (drop == Drop.CINNABAR || tool.getEnchantmentLevel(Enchantments.SILK_TOUCH) > 0) {
            return new ItemStack(this);
        }
        int fortune = tool.getEnchantmentLevel(Enchantments.BLOCK_FORTUNE);
        int count = switch (drop) {
            case CINNABAR -> 1;
            case INFUSED -> 1 + random.nextInt(2 + fortune);
            case AMBER -> 1 + random.nextInt(fortune + 1);
        };
        return legacyStack(count).orElseGet(() -> new ItemStack(this));
    }

    private Optional<ItemStack> legacyStack(int count) {
        return TcModernApi.runtime().flatMap(runtime -> {
            try {
                LegacyObject original = runtime.stack(new LegacyStack(drop.legacyKey, legacyMetadata, count, ""));
                return runtime.mappings().toHost(LegacyMappings.ITEM_STACK, original, ItemStack.class);
            } catch (RuntimeException unsupported) {
                return Optional.empty();
            }
        });
    }
}
