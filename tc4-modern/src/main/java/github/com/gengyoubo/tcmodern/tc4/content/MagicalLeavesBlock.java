package github.com.gengyoubo.tcmodern.tc4.content;

import github.com.gengyoubo.mfcl.api.LegacyMappings;
import github.com.gengyoubo.mfcl.api.LegacyStack;
import github.com.gengyoubo.mfcl.api.spi.LegacyBlockProvider;
import github.com.gengyoubo.tcmodern.api.TcModernApi;
import java.util.*;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

/** Original greatwood/silverwood identity and artwork, with native leaf state and decay. */
public final class MagicalLeavesBlock extends LeavesBlock {
    public static final IntegerProperty LEGACY_META = IntegerProperty.create("legacy_meta", 0, 15);
    private final LegacyBlockProvider.DisplayTicks originalDisplay;

    public MagicalLeavesBlock(BlockBehaviour.Properties properties, LegacyBlockProvider.DisplayTicks originalDisplay) {
        super(properties.randomTicks().noOcclusion()
                .isSuffocating((state, level, pos) -> false).isViewBlocking((state, level, pos) -> false));
        this.originalDisplay = originalDisplay;
        registerDefaultState(defaultBlockState().setValue(LEGACY_META, 0));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder); builder.add(LEGACY_META);
    }
    public BlockState fromLegacy(int metadata) {
        if (metadata < 0 || metadata > 15) throw new IllegalArgumentException("Leaf metadata outside 0..15");
        return defaultBlockState().setValue(LEGACY_META, metadata).setValue(PERSISTENT, (metadata & 4) != 0);
    }
    public static int legacyMetadata(BlockState state) {
        return state.getValue(LEGACY_META) & ~4 | (persistent(state) ? 4 : 0);
    }
    private static boolean persistent(BlockState state) {
        // Old saved states only have legacy_meta; their newly introduced persistent property defaults to false.
        return state.getValue(PERSISTENT) || (state.getValue(LEGACY_META) & 4) != 0;
    }
    @Override public boolean isRandomlyTicking(BlockState state) { return !persistent(state) && state.getValue(DISTANCE) == DECAY_DISTANCE; }
    @Override protected boolean decaying(BlockState state) { return !persistent(state) && super.decaying(state); }
    @Override public void onPlace(BlockState state, Level level, BlockPos pos, BlockState before, boolean moving) {
        super.onPlace(state, level, pos, before, moving);
        if (!level.isClientSide && state.getBlock() != before.getBlock()) level.scheduleTick(pos, this, 1);
    }
    @Override public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        super.tick(state.setValue(PERSISTENT, persistent(state)), level, pos, random);
        var current = level.getBlockState(pos);
        if (current.is(this)) {
            int metadata = legacyMetadata(current) & ~8 | (current.getValue(DISTANCE) == DECAY_DISTANCE ? 8 : 0);
            if (current.getValue(LEGACY_META) != metadata) level.setBlock(pos, current.setValue(LEGACY_META, metadata), 2);
        }
    }
    @Override public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (persistent(state)) return;
        // A pre-existing canopy has no cached distances. Check support through real leaves before its first decay,
        // rather than letting the random-tick order destroy it while scheduled distance updates are propagating.
        int distance = supportDistance(level, pos);
        if (distance < DECAY_DISTANCE) {
            level.setBlock(pos, state.setValue(DISTANCE, distance).setValue(LEGACY_META, legacyMetadata(state) & ~8), 3);
        } else super.randomTick(state.setValue(DISTANCE, DECAY_DISTANCE), level, pos, random);
    }
    private static int supportDistance(LevelReader level, BlockPos origin) {
        var queue = new ArrayDeque<BlockPos>(); var distances = new HashMap<BlockPos, Integer>();
        queue.add(origin); distances.put(origin, 0);
        while (!queue.isEmpty()) {
            var at = queue.remove(); int distance = distances.get(at) + 1;
            for (var side : Direction.values()) {
                var next = at.relative(side);
                if (!level.hasChunkAt(next)) continue;
                var state = level.getBlockState(next);
                if (state.is(BlockTags.LOGS)) return distance;
                if (distance < DECAY_DISTANCE - 1 && state.getBlock() instanceof LeavesBlock && distances.putIfAbsent(next, distance) == null)
                    queue.add(next);
            }
        }
        return DECAY_DISTANCE;
    }
    @Override public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        var tool = params.getOptionalParameter(LootContextParams.TOOL);
        int species = state.getValue(LEGACY_META) & 1;
        if (tool != null && (tool.is(Items.SHEARS) || tool.getEnchantmentLevel(Enchantments.SILK_TOUCH) > 0))
            return List.of(originalStack("Thaumcraft:blockMagicalLeaves", species));
        // Original TC4 probabilities and sapling items; modern harvesting no longer needs an old decay flag.
        if (params.getLevel().random.nextInt(species == 0 ? 200 : 250) == 0)
            return List.of(originalStack("Thaumcraft:blockCustomPlant", species));
        return List.of();
    }
    @Override public List<ItemStack> onSheared(@Nullable Player player, ItemStack tool, Level level, BlockPos pos, int fortune) {
        return List.of(originalStack("Thaumcraft:blockMagicalLeaves", level.getBlockState(pos).getValue(LEGACY_META) & 1));
    }
    @Override public ItemStack getCloneItemStack(BlockGetter level, BlockPos pos, BlockState state) {
        return originalStack("Thaumcraft:blockMagicalLeaves", state.getValue(LEGACY_META) & 1);
    }
    @Override public int getFlammability(BlockState state, BlockGetter level, BlockPos pos, Direction face) { return state.getValue(WATERLOGGED) ? 0 : 60; }
    @Override public int getFireSpreadSpeed(BlockState state, BlockGetter level, BlockPos pos, Direction face) { return state.getValue(WATERLOGGED) ? 0 : 30; }
    @Override public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        originalDisplay.animate(level, pos, random.nextLong());
    }
    private static ItemStack originalStack(String key, int metadata) {
        var runtime = TcModernApi.runtime().orElseThrow();
        return runtime.mappings().toHost(LegacyMappings.ITEM_STACK,
                runtime.stack(new LegacyStack(key, metadata, 1, "")), ItemStack.class).orElseThrow();
    }
}
