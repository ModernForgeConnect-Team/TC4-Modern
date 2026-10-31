package github.com.gengyoubo.tcmodern.tc4.compat.jade;

import github.com.gengyoubo.tcmodern.api.TcModernApi;
import github.com.gengyoubo.tcmodern.api.aspect.TcAspect;
import github.com.gengyoubo.tcmodern.api.aspect.TcAspects;
import github.com.gengyoubo.tcmodern.tc4.Tc4Modern;
import github.com.gengyoubo.tcmodern.lib.client.AspectIcons;
import github.com.gengyoubo.tcmodern.tc4.client.FormedStructures;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec2;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ui.IElement;
import snownee.jade.api.ui.IElementHelper;

/**
 * Shows the essentia held by original TC4 tiles: jars, alembics, the crucible, reservoirs, tubes, the alchemical
 * furnace and nodes. Loaded only by Jade. The server reads the tile when Jade is installed there; otherwise the
 * client reads the copy MFCL synchronises for rendering. Blocks that only exist in a formed multiblock show the whole
 * formed structure as their icon and name it.
 */
@WailaPlugin(Tc4Modern.MOD_ID)
public final class Tc4JadePlugin implements IWailaPlugin {
    static final ResourceLocation ESSENTIA = ResourceLocation.fromNamespaceAndPath(Tc4Modern.MOD_ID, "essentia");
    static final ResourceLocation FORMED = ResourceLocation.fromNamespaceAndPath(Tc4Modern.MOD_ID, "formed_structure");
    /** Up to this many aspects are listed by name; more become a grid of icons until details are shown. */
    private static final int NAMED_LIMIT = 4;
    private static final int ICONS_PER_ROW = 8;

    @Override public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(Server.INSTANCE, BlockEntity.class);
    }

    @Override public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(Client.INSTANCE, Block.class);
        registration.registerBlockIcon(Formed.INSTANCE, Block.class);
        registration.registerBlockComponent(Formed.INSTANCE, Block.class);
    }

    private enum Formed implements IBlockComponentProvider {
        INSTANCE;

        @Override public IElement getIcon(BlockAccessor accessor, IPluginConfig config, IElement currentIcon) {
            return FormedStructures.partOf(accessor.getBlockState()).filter(id -> FormedStructures.scene(id).isPresent())
                    .<IElement>map(FormedElement::new).orElse(currentIcon);
        }

        @Override public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            FormedStructures.partOf(accessor.getBlockState()).ifPresent(id -> tooltip.add(Component.translatable("jade.tc4modern.formed",
                    Component.translatable("jei.tc4modern.multiblock." + id)).withStyle(ChatFormatting.GRAY)));
        }

        @Override public ResourceLocation getUid() { return FORMED; }
    }

    private enum Server implements IServerDataProvider<BlockAccessor> {
        INSTANCE;

        @Override public void appendServerData(CompoundTag data, BlockAccessor accessor) {
            LegacyEssentia.read(accessor.getBlockEntity()).ifPresent(essentia -> data.put(LegacyEssentia.SERVER_KEY, essentia.toTag()));
        }

        @Override public ResourceLocation getUid() { return ESSENTIA; }
    }

    private enum Client implements IBlockComponentProvider {
        INSTANCE;
        /** The original aspects by tag, read once the runtime is up; unknown tags still show as a coloured square. */
        private volatile Map<String, TcAspect> catalog;

        @Override public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            var server = accessor.getServerData();
            var essentia = server.contains(LegacyEssentia.SERVER_KEY, Tag.TAG_COMPOUND)
                    ? LegacyEssentia.fromTag(server.getCompound(LegacyEssentia.SERVER_KEY))
                    : LegacyEssentia.read(accessor.getBlockEntity());
            essentia.ifPresent(contents -> append(tooltip, contents, accessor.showDetails()));
        }

        private void append(ITooltip tooltip, LegacyEssentia contents, boolean details) {
            if (!contents.filter().isEmpty())
                tooltip.add(Component.translatable("jade.tc4modern.essentia.filter", AspectIcons.name(contents.filter())).withStyle(ChatFormatting.GRAY));
            if (contents.aspects().isEmpty()) {
                tooltip.add(Component.translatable("jade.tc4modern.essentia.empty").withStyle(ChatFormatting.GRAY));
                return;
            }
            if (contents.aspects().size() > 1)
                tooltip.add(Component.translatable("jade.tc4modern.essentia.total", contents.total()));
            var helper = IElementHelper.get();
            if (details || contents.aspects().size() <= NAMED_LIMIT) {
                contents.aspects().forEach((tag, amount) -> {
                    tooltip.add(new AspectElement(aspect(tag), 0));
                    tooltip.append(helper.text(Component.literal(AspectIcons.name(tag) + " ")
                            .append(Component.literal(Integer.toString(amount)).withStyle(ChatFormatting.WHITE))).translate(new Vec2(2, 5)));
                });
                return;
            }
            var row = new ArrayList<IElement>();
            contents.aspects().forEach((tag, amount) -> {
                row.add(new AspectElement(aspect(tag), amount));
                if (row.size() == ICONS_PER_ROW) { tooltip.add(List.copyOf(row)); row.clear(); }
            });
            if (!row.isEmpty()) tooltip.add(row);
        }

        private TcAspect aspect(String tag) {
            var known = catalog;
            if (known == null || known.isEmpty()) {
                known = TcModernApi.runtime().map(TcAspects::snapshot).orElse(List.of()).stream()
                        .collect(Collectors.toUnmodifiableMap(TcAspect::tag, Function.identity(), (a, b) -> a));
                catalog = known;
            }
            var aspect = known.get(tag);
            return aspect != null ? aspect : new TcAspect(tag, 0xFFFFFF, "", List.of());
        }

        @Override public ResourceLocation getUid() { return ESSENTIA; }
    }
}
