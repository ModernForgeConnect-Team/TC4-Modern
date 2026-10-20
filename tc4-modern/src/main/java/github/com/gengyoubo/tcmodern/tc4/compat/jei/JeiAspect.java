package github.com.gengyoubo.tcmodern.tc4.compat.jei;

import github.com.gengyoubo.tcmodern.api.aspect.TcAspect;
import github.com.gengyoubo.tcmodern.api.recipe.TcRecipe;
import github.com.gengyoubo.tcmodern.tc4.client.AspectIcons;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.subtypes.UidContext;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.TooltipFlag;

/**
 * An original TC4 aspect as a JEI ingredient, so an aspect in a recipe or the ingredient list can be looked up:
 * its usages are the recipes that cost it and the compound aspects it is part of, its recipe is what it is made of.
 * The amount is a recipe cost and never part of the identity.
 */
record JeiAspect(TcAspect aspect, long amount) {
    static final IIngredientType<JeiAspect> TYPE = () -> JeiAspect.class;
    /** The original aspects by tag, filled when JEI registers ingredients. */
    private static volatile Map<String, TcAspect> catalog = Map.of();
    private static volatile List<TcAspect> ordered = List.of();

    static void catalog(List<TcAspect> aspects) {
        ordered = List.copyOf(aspects);
        catalog = aspects.stream().collect(Collectors.toUnmodifiableMap(TcAspect::tag, Function.identity(), (a, b) -> a));
    }

    /** In the original registration order, primals first. */
    static List<TcAspect> all() { return ordered; }

    /** A recipe cost; an aspect missing from the registry still shows, just without its components. */
    static JeiAspect of(TcRecipe.AspectCost cost) {
        var known = catalog.get(cost.tag());
        return new JeiAspect(known != null ? known : new TcAspect(cost.tag(), cost.color(), cost.texture(), List.of()), cost.amount());
    }

    static JeiAspect of(String tag) {
        var known = catalog.get(tag);
        return new JeiAspect(known != null ? known : new TcAspect(tag, 0xFFFFFF, "", List.of()), 0);
    }

    JeiAspect withAmount(long amount) { return new JeiAspect(aspect, amount); }

    static String name(String tag) { return AspectIcons.name(tag); }

    static void renderIcon(GuiGraphics graphics, TcAspect aspect, int x, int y) { AspectIcons.render(graphics, aspect, x, y); }

    static final class Helper implements IIngredientHelper<JeiAspect> {
        @Override public IIngredientType<JeiAspect> getIngredientType() { return TYPE; }
        @Override public String getDisplayName(JeiAspect value) { return name(value.aspect().tag()); }
        @Override public String getUniqueId(JeiAspect value, UidContext context) { return "tc4modern:aspect/" + value.aspect().tag(); }
        @Override public ResourceLocation getResourceLocation(JeiAspect value) {
            return ResourceLocation.fromNamespaceAndPath("tc4modern", "aspect/" + value.aspect().tag());
        }
        @Override public long getAmount(JeiAspect value) { return Math.max(1, value.amount()); }
        @Override public JeiAspect copyWithAmount(JeiAspect value, long amount) { return new JeiAspect(value.aspect(), amount); }
        @Override public JeiAspect copyIngredient(JeiAspect value) { return value; }
        @Override public JeiAspect normalizeIngredient(JeiAspect value) { return new JeiAspect(value.aspect(), 0); }
        @Override public Iterable<Integer> getColors(JeiAspect value) { return List.of(value.aspect().color()); }
        @Override public String getErrorInfo(JeiAspect value) { return value == null ? "null aspect" : "aspect " + value.aspect().tag(); }
    }

    static final class Renderer implements IIngredientRenderer<JeiAspect> {
        @Override public void render(GuiGraphics graphics, JeiAspect value) {
            renderIcon(graphics, value.aspect(), 0, 0);
            if (value.amount() <= 0) return;
            var font = Minecraft.getInstance().font;
            String amount = Long.toString(value.amount());
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 200);
            graphics.drawString(font, amount, 17 - font.width(amount), 9, 0xFFFFFFFF, true);
            graphics.pose().popPose();
        }

        @SuppressWarnings("removal")
        @Override public List<Component> getTooltip(JeiAspect value, TooltipFlag flag) {
            var aspect = value.aspect();
            var lines = new ArrayList<Component>();
            lines.add(Component.literal(name(aspect.tag()) + (value.amount() > 0 ? " × " + value.amount() : "")));
            lines.add(Component.literal(aspect.tag()).withStyle(ChatFormatting.GRAY));
            lines.add((aspect.primal() ? Component.translatable("jei.tc4modern.aspect.primal")
                    : Component.translatable("jei.tc4modern.aspect.components", aspect.components().stream().map(JeiAspect::name)
                    .collect(Collectors.joining(" + ")))).withStyle(ChatFormatting.DARK_AQUA));
            lines.add(Component.translatable("jei.tc4modern.aspect.lookup").withStyle(ChatFormatting.DARK_GRAY));
            return lines;
        }
    }
}
