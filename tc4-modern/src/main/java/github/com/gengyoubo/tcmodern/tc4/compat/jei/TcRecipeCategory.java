package github.com.gengyoubo.tcmodern.tc4.compat.jei;

import com.mojang.blaze3d.systems.RenderSystem;
import github.com.gengyoubo.tcmodern.api.recipe.TcRecipe;
import java.util.ArrayList;
import java.util.List;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * The original Thaumonomicon recipe pages (GuiResearchRecipe) on a research book page, drawn from the installed
 * Thaumcraft's own GUI textures at runtime, so none are redistributed. Positions are the original page coordinates
 * scaled by {@link #SCALE}; items keep their centres, since JEI slots stay 16 px. Without the textures the page
 * falls back to plain slot frames.
 */
final class TcRecipeCategory implements IRecipeCategory<JeiTcRecipe> {
    static final int MAX_ASPECTS = 16;
    static final int MAX_COMPONENTS = 16;
    private static final ResourceLocation BOOK = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/gui/gui_researchbook.png");
    private static final ResourceLocation OVERLAY = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/gui/gui_researchbook_overlay.png");
    private static final int WIDTH = 160, HEIGHT = 140;
    /**
     * Original page size relative to this one; the original page content is 112 px wide. 9/16 turns the arcane
     * grid's 32 px cells into vanilla's 18 px slot spacing and keeps the page shorter than JEI's usual limit.
     */
    private static final float SCALE = 0.5625F;
    private static final int LEFT = Math.round((WIDTH - 112 * SCALE) / 2), TOP = 4;
    private static final int RESEARCH_Y = HEIGHT - 12, INSTABILITY_Y = RESEARCH_Y - 11, ASPECT = 18;
    /** Original page text colour. */
    private static final int INK = 0xFF505050;
    private final RecipeType<JeiTcRecipe> type;
    private final String title;
    private final IDrawable icon;

    TcRecipeCategory(RecipeType<JeiTcRecipe> type, String title, IDrawable icon) {
        this.type = type;
        this.title = title;
        this.icon = icon;
    }

    @Override public RecipeType<JeiTcRecipe> getRecipeType() { return type; }
    @Override public Component getTitle() { return Component.translatable(title); }
    @Override public int getWidth() { return WIDTH; }
    @Override public int getHeight() { return HEIGHT; }
    @Override public IDrawable getIcon() { return icon; }
    @Override public ResourceLocation getRegistryName(JeiTcRecipe recipe) {
        return ResourceLocation.fromNamespaceAndPath("tc4modern", recipe.source().id());
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, JeiTcRecipe recipe, IFocusGroup focuses) {
        for (int i = 0; i < recipe.inputs().size(); i++) {
            if (recipe.inputs().get(i).isEmpty()) continue;
            int[] position = position(recipe.source(), i);
            builder.addSlot(RecipeIngredientRole.INPUT, position[0], position[1])
                    .addItemStacks(recipe.inputs().get(i));
        }
        int[] output = output(recipe.source());
        builder.addSlot(RecipeIngredientRole.OUTPUT, output[0], output[1]).addItemStack(recipe.output());
        if (recipe.source().kind() == TcRecipe.Kind.ARCANE_SHAPELESS) builder.setShapeless();
    }

    @Override
    public void draw(JeiTcRecipe recipe, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX, double mouseY) {
        var source = recipe.source();
        var font = Minecraft.getInstance().font;
        boolean art = available(BOOK) && available(OVERLAY);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        if (art) {
            // The right-hand page of the open book, without the cover and spine.
            graphics.blit(BOOK, 0, 0, WIDTH, HEIGHT, 258, 4, 234, 348, 512, 512);
            switch (source.kind()) {
                case ARCANE_SHAPED, ARCANE_SHAPELESS -> {
                    overlay(graphics, 0, 0, 2, 27, 112, 15, 52, 52);
                    overlay(graphics, 0, 0, 20, 7, 20, 3, 16, 16);
                    graphics.setColor(1, 1, 1, 0.4F);
                    overlay(graphics, 0, 164, 0, 0, 68, 76, 12, 12);
                    graphics.setColor(1, 1, 1, 1);
                }
                case CRUCIBLE -> {
                    overlay(graphics, 0, 28, 0, 0, 0, 3, 56, 17);
                    overlay(graphics, 0, 92, 0, 0, 0, 20, 56, 48);
                    overlay(graphics, 42, 76, 0, 0, 100, 84, 11, 13);
                }
                case INFUSION -> {
                    overlay(graphics, 0, 20, 0, 0, 0, 3, 56, 17);
                    overlay(graphics, 0, 58, 0, 0, 200, 77, 60, 44);
                }
            }
        } else {
            for (int i = 0; i < recipe.inputs().size(); i++) {
                var position = position(source, i);
                frame(graphics, position[0], position[1]);
            }
            int[] output = output(source);
            frame(graphics, output[0], output[1]);
        }
        if (source.kind() == TcRecipe.Kind.INFUSION) {
            int level = Math.min(5, source.instability() / 2);
            String text = I18n.exists("tc.inst") && I18n.exists("tc.inst." + level) ? I18n.get("tc.inst") + " " + I18n.get("tc.inst." + level)
                    : Component.translatable("jei.tc4modern.instability", source.instability()).getString();
            centered(graphics, text, INSTABILITY_Y);
        }
        centered(graphics, research(source).getString(), RESEARCH_Y);
        var aspects = source.aspects();
        for (int i = 0; i < aspects.size(); i++) {
            var cost = aspects.get(i);
            int[] at = aspect(source, i);
            int x = at[0], y = at[1];
            var texture = ResourceLocation.tryParse(cost.texture());
            if (texture != null && available(texture)) {
                graphics.setColor((cost.color() >> 16 & 255) / 255F, (cost.color() >> 8 & 255) / 255F,
                        (cost.color() & 255) / 255F, 1);
                graphics.blit(texture, x, y, 16, 16, 0F, 0F, 32, 32, 32, 32);
                graphics.setColor(1, 1, 1, 1);
            } else {
                graphics.fill(x, y, x + 16, y + 16, 0xFF000000 | cost.color());
                graphics.drawString(font, cost.tag().substring(0, 1), x + 4, y + 2, 0xFFFFFFFF, true);
            }
            String amount = Integer.toString(cost.amount());
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 200);
            graphics.drawString(font, amount, x + 17 - font.width(amount), y + 9, 0xFFFFFFFF, true);
            graphics.pose().popPose();
        }
        RenderSystem.disableBlend();
    }

    @Override
    public void getTooltip(ITooltipBuilder tooltip, JeiTcRecipe recipe, IRecipeSlotsView slots, double x, double y) {
        var source = recipe.source();
        if (y >= RESEARCH_Y - 2 && y < RESEARCH_Y + 10) {
            tooltip.addAll(List.of(research(source), Component.translatable("jei.tc4modern.view_only")));
            return;
        }
        var result = new ArrayList<Component>();
        for (int i = 0; i < source.aspects().size(); i++) {
            int[] at = aspect(source, i);
            if (x >= at[0] && x < at[0] + 16 && y >= at[1] && y < at[1] + 16) {
                var cost = source.aspects().get(i);
                String key = "tc.aspect." + cost.tag();
                String name = I18n.exists(key) ? I18n.get(key) : cost.tag();
                result.add(Component.literal(name + " (" + cost.tag() + ") × " + cost.amount()));
                result.add(Component.translatable(isArcane(source) ? "jei.tc4modern.vis" : "jei.tc4modern.essentia"));
            }
        }
        tooltip.addAll(result);
    }

    /** The research's own name where Thaumcraft's language files have one, else its key. */
    private static Component research(TcRecipe recipe) {
        String key = recipe.research();
        if (key.isEmpty()) return Component.translatable("jei.tc4modern.no_research");
        String name = "tc.research_name." + key;
        return Component.translatable("jei.tc4modern.research", I18n.exists(name) ? I18n.get(name) : key);
    }

    /** Original page coordinate to this page, for a 16 px item whose centre keeps its place. */
    private static int itemX(float x) { return Math.round(LEFT + (x + 8) * SCALE) - 8; }
    private static int itemY(float y) { return Math.round(TOP + (y + 8) * SCALE) - 8; }

    private static int[] position(TcRecipe recipe, int index) {
        return switch (recipe.kind()) {
            case ARCANE_SHAPED -> new int[]{itemX(16 + index % recipe.width() * 32), itemY(66 + index / recipe.width() * 32)};
            case ARCANE_SHAPELESS -> new int[]{itemX(16 + index % 3 * 32), itemY(66 + index / 3 * 32)};
            case CRUCIBLE -> new int[]{itemX(26), itemY(72)};
            case INFUSION -> {
                if (index == 0) yield new int[]{itemX(48), itemY(94)};
                // Components on a circle around the central item, starting at the top.
                int count = recipe.inputs().size() - 1;
                float angle = (-90F + 360F / count * (index - 1)) * Mth.DEG_TO_RAD;
                yield new int[]{itemX(56 + Mth.cos(angle) * 40 - 8), itemY(102 + Mth.sin(angle) * 40 - 8)};
            }
        };
    }

    private static int[] output(TcRecipe recipe) {
        return switch (recipe.kind()) {
            case ARCANE_SHAPED, ARCANE_SHAPELESS -> new int[]{itemX(48), itemY(22)};
            case CRUCIBLE -> new int[]{itemX(48), itemY(36)};
            case INFUSION -> new int[]{itemX(48), itemY(28)};
        };
    }

    /** Aspects in centred rows: beside the wand for vis, inside the cauldron for crucible essentia. */
    private static int[] aspect(TcRecipe recipe, int index) {
        int count = recipe.aspects().size();
        int perRow = recipe.kind() == TcRecipe.Kind.CRUCIBLE ? 5 : 8;
        int rows = (count + perRow - 1) / perRow, row = index / perRow;
        int inRow = Math.min(perRow, count - row * perRow);
        int top = switch (recipe.kind()) {
            case ARCANE_SHAPED, ARCANE_SHAPELESS -> itemY(172);
            case CRUCIBLE -> itemY(128) - ASPECT / 2 * (rows - 1);
            case INFUSION -> Math.min(itemY(164), INSTABILITY_Y - 2 - ASPECT * rows);
        };
        int left = WIDTH / 2 - (inRow * ASPECT - 2) / 2;
        return new int[]{left + index % perRow * ASPECT, top + row * ASPECT};
    }

    /**
     * One original overlay blit: the page translated to {@code (pageX, pageY)} and scaled by two, then a 256-space
     * rectangle at {@code (x, y)}, exactly as GuiResearchRecipe drew it.
     */
    private static void overlay(GuiGraphics graphics, int pageX, int pageY, int x, int y, int u, int v, int width, int height) {
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(LEFT + pageX * SCALE, TOP + pageY * SCALE, 0);
        pose.scale(2 * SCALE, 2 * SCALE, 1);
        graphics.blit(OVERLAY, x, y, u, v, width, height);
        pose.popPose();
    }

    private static void centered(GuiGraphics graphics, String text, int y) {
        var font = Minecraft.getInstance().font;
        text = font.plainSubstrByWidth(text, WIDTH - 8);
        graphics.drawString(font, text, (WIDTH - font.width(text)) / 2, y, INK, false);
    }

    private static boolean available(ResourceLocation texture) {
        return Minecraft.getInstance().getResourceManager().getResource(texture).isPresent();
    }

    private static boolean isArcane(TcRecipe recipe) {
        return recipe.kind() == TcRecipe.Kind.ARCANE_SHAPED || recipe.kind() == TcRecipe.Kind.ARCANE_SHAPELESS;
    }

    private static void frame(GuiGraphics graphics, int x, int y) {
        graphics.fill(x - 1, y - 1, x + 17, y + 17, 0xFF777777);
        graphics.fill(x, y, x + 16, y + 16, 0xFFE8E8E8);
    }
}
