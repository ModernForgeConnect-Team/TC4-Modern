package github.com.gengyoubo.tcmodern.tc4.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Locale;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;

/** One original TC4 tree at the placed position; worldgen/configured_feature composes them per biome. */
public final class Tc4TreeFeature extends Feature<Tc4TreeFeature.Config> {
    public enum Tree implements StringRepresentable {
        GREATWOOD, SILVERWOOD, BIG_MAGIC;
        static final Codec<Tree> CODEC = StringRepresentable.fromEnum(Tree::values);
        @Override public String getSerializedName() { return name().toLowerCase(Locale.ROOT); }
    }

    /** Silverwood height is {@code min_height + random(random_height)}: 8 + 5 in the Magical Forest, 7 + 4 elsewhere. */
    public record Config(Tree tree, int minHeight, int randomHeight) implements FeatureConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Tree.CODEC.fieldOf("tree").forGetter(Config::tree),
                Codec.intRange(1, 64).optionalFieldOf("min_height", 7).forGetter(Config::minHeight),
                Codec.intRange(1, 64).optionalFieldOf("random_height", 4).forGetter(Config::randomHeight)
        ).apply(instance, Config::new));
    }

    public Tc4TreeFeature() { super(Config.CODEC); }

    @Override public boolean place(FeaturePlaceContext<Config> context) {
        var region = new Tc4GenRegion(context.level(), context.origin(), Tc4ModernWorldgen.blocks);
        var random = context.random();
        var config = context.config();
        return switch (config.tree()) {
            case GREATWOOD -> Tc4BranchedTree.greatwood(region, random, context.origin(), random.nextInt(8) == 0);
            case SILVERWOOD -> Tc4SilverwoodTree.grow(region, random, context.origin(), config.minHeight(), config.randomHeight());
            case BIG_MAGIC -> Tc4BranchedTree.bigMagic(region, random, context.origin());
        };
    }
}
