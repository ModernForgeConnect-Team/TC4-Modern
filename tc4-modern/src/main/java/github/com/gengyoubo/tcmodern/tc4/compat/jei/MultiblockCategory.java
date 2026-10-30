package github.com.gengyoubo.tcmodern.tc4.compat.jei;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import github.com.gengyoubo.tcmodern.tc4.client.FormedStructures;
import java.util.List;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.gui.inputs.IJeiInputHandler;
import mezz.jei.api.gui.inputs.IJeiUserInput;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.gui.widgets.IRecipeWidget;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenPosition;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;

/**
 * A TC4 multiblock structure the wand (or scribing tools) turns into a device, as WandManager and BlockTable check
 * it: the research the check requires, a rotatable 3D preview that shows all layers or one at a time, the blocks it
 * takes, and the vis it costs. The preview can also show the structure as the original forms it.
 * Drag rotates, scrolling or clicking the layer label steps through the layers, clicking the form label switches
 * between the blocks to place and the formed structure.
 */
final class MultiblockCategory implements IRecipeCategory<MultiblockCategory.Recipe> {
    /**
     * One block of a structure. Nothing to render or list is allowed: the preview falls back to a marker.
     *
     * @param state     the host state MFCL gives the block, or null
     * @param items     what the material slot shows, the first also renders the block when its state cannot
     * @param trigger   whether this is a block to right-click
     * @param legacyKey the original block, drawn by its own renderers when MFCL offers preview scenes; null for host blocks
     */
    record Part(BlockState state, List<ItemStack> items, boolean trigger, String legacyKey, int metadata) {
        Part { items = items.stream().map(stack -> stack.copyWithCount(1)).toList(); }
        Part(BlockState state, List<ItemStack> items, boolean trigger) { this(state, items, trigger, null, 0); }
        Part asTrigger() { return new Part(state, items, true, legacyKey, metadata); }
    }

    record Placed(int x, int y, int z, Part part) { }

    record Material(Part part, int count) { }

    /**
     * @param research the original research key, empty when the structure needs none
     * @param tools    the wands, or the scribing tools, that build it
     */
    record Recipe(String id, String research, List<Placed> blocks, int sizeX, int sizeY, int sizeZ, List<Material> materials,
                  List<ItemStack> tools, List<JeiAspect> cost, List<ItemStack> results) {
        Recipe { blocks = List.copyOf(blocks); materials = List.copyOf(materials); tools = List.copyOf(tools); cost = List.copyOf(cost); results = results.stream().map(ItemStack::copy).toList(); }
    }

    static final RecipeType<Recipe> TYPE = RecipeType.create("tc4modern", "multiblock", Recipe.class);
    private static final int WIDTH = 150, HEIGHT = 142;
    private static final int RESEARCH_Y = 12;
    private static final int VIEW_X = 0, VIEW_Y = 22, VIEW_W = 96, VIEW_H = 78;
    private static final int SIDE_X = 102, TOOL_Y = 22, COST_Y = 46, MATERIAL_Y = 102, NOTE_Y = 122;
    private static final int LAYER_H = 10;
    private static final int INK = 0xFF505050, REQUIRED = 0xFF8B1A1A;
    private final IDrawable icon;

    MultiblockCategory(IDrawable icon) { this.icon = icon; }

    @Override public RecipeType<Recipe> getRecipeType() { return TYPE; }
    @Override public Component getTitle() { return Component.translatable("jei.tc4modern.multiblock"); }
    @Override public int getWidth() { return WIDTH; }
    @Override public int getHeight() { return HEIGHT; }
    @Override public IDrawable getIcon() { return icon; }
    @Override public ResourceLocation getRegistryName(Recipe recipe) {
        return ResourceLocation.fromNamespaceAndPath("tc4modern", "multiblock/" + recipe.id());
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, Recipe recipe, IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.CATALYST, SIDE_X, TOOL_Y).addItemStacks(recipe.tools());
        // These are representatives of the formed device, not items granted by forming it.
        builder.addSlot(RecipeIngredientRole.OUTPUT, SIDE_X + 24, TOOL_Y).addItemStacks(recipe.results())
                .addRichTooltipCallback((slot, tooltip) -> tooltip.add(Component.translatable("jei.tc4modern.multiblock.result")));
        var vis = Component.translatable("jei.tc4modern.vis");
        for (int i = 0; i < recipe.cost().size(); i++) {
            builder.addSlot(RecipeIngredientRole.INPUT, SIDE_X + i % 2 * 24, COST_Y + i / 2 * 18)
                    .addIngredient(JeiAspect.TYPE, recipe.cost().get(i))
                    .addRichTooltipCallback((slot, tooltip) -> tooltip.add(vis));
        }
        int column = 0;
        for (var material : recipe.materials()) {
            if (material.part().items().isEmpty()) continue;
            var stacks = material.part().items().stream().map(stack -> stack.copyWithCount(material.count())).toList();
            var slot = builder.addSlot(RecipeIngredientRole.INPUT, 1 + column++ * 18, MATERIAL_Y).addItemStacks(stacks);
            if (material.part().trigger()) slot.addRichTooltipCallback((view, tooltip) ->
                    tooltip.add(Component.translatable(recipe.id().equals("research_table")
                            ? "jei.tc4modern.multiblock.trigger.ink" : "jei.tc4modern.multiblock.trigger").withStyle(ChatFormatting.GOLD)));
        }
    }

    @Override
    public void createRecipeExtras(IRecipeExtrasBuilder builder, Recipe recipe, IFocusGroup focuses) {
        var view = new StructureView(recipe);
        builder.addWidget(view);
        builder.addInputHandler(view);
    }

    @Override
    public void draw(Recipe recipe, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX, double mouseY) {
        var font = Minecraft.getInstance().font;
        frame(graphics, SIDE_X, TOOL_Y);
        frame(graphics, SIDE_X + 24, TOOL_Y);
        for (int i = 0; i < recipe.materials().stream().filter(material -> !material.part().items().isEmpty()).count(); i++)
            frame(graphics, 1 + i * 18, MATERIAL_Y);
        graphics.drawString(font, font.plainSubstrByWidth(Component.translatable(name(recipe)).getString(), VIEW_W), 0, 2, INK, false);
        graphics.drawString(font, font.plainSubstrByWidth(research(recipe).getString(), WIDTH), 0, RESEARCH_Y,
                recipe.research().isEmpty() ? INK : REQUIRED, false);
        if (recipe.cost().isEmpty()) graphics.drawString(font, Component.translatable("jei.tc4modern.multiblock.free"), SIDE_X, COST_Y + 4, INK, false);
        var lines = font.split(Component.translatable(name(recipe) + ".note"), WIDTH);
        for (int i = 0; i < Math.min(2, lines.size()); i++) graphics.drawString(font, lines.get(i), 0, NOTE_Y + i * 10, INK, false);
    }

    @Override
    public void getTooltip(ITooltipBuilder tooltip, Recipe recipe, IRecipeSlotsView slots, double x, double y) {
        if (y >= RESEARCH_Y && y < VIEW_Y) {
            tooltip.add(research(recipe));
            if (!recipe.research().isEmpty())
                tooltip.add(Component.translatable("jei.tc4modern.multiblock.research.hint").withStyle(ChatFormatting.GRAY));
        }
        else if (y >= NOTE_Y) tooltip.add(Component.translatable(name(recipe) + ".note"));
    }

    private static String name(Recipe recipe) { return "jei.tc4modern.multiblock." + recipe.id(); }

    /** The research's own name where Thaumcraft's language files have one, else its key. */
    private static Component research(Recipe recipe) {
        if (recipe.research().isEmpty()) return Component.translatable("jei.tc4modern.no_research");
        String name = "tc.research_name." + recipe.research();
        return Component.translatable("jei.tc4modern.multiblock.research", I18n.exists(name) ? I18n.get(name) : recipe.research());
    }

    private static void frame(GuiGraphics graphics, int x, int y) {
        graphics.fill(x - 1, y - 1, x + 17, y + 17, 0xFF777777);
        graphics.fill(x, y, x + 16, y + 16, 0xFFE8E8E8);
    }

    /** The 3D preview of one displayed recipe; JEI makes one per layout, so its rotation and layer are its own. */
    private static final class StructureView implements IRecipeWidget, IJeiInputHandler {
        private static final int FULL_BRIGHT = 0xF000F0;
        private final Recipe recipe;
        /** -1 shows every layer. */
        private int layer = -1;
        /** Shows the structure as the original forms it instead of the blocks to place. */
        private boolean formed;
        private float yaw = 45, pitch = 30;
        /** Spins slowly until the player first turns it. */
        private boolean turned;
        private final long start = Util.getMillis();

        StructureView(Recipe recipe) { this.recipe = recipe; }

        @Override public ScreenPosition getPosition() { return new ScreenPosition(VIEW_X, VIEW_Y); }
        @Override public ScreenRectangle getArea() { return new ScreenRectangle(VIEW_X, VIEW_Y, VIEW_W, VIEW_H); }

        @Override
        public void drawWidget(GuiGraphics graphics, double mouseX, double mouseY) {
            var font = Minecraft.getInstance().font;
            boolean shown = !formed || FormedStructures.render(graphics, recipe.id(), VIEW_W / 2F, (VIEW_H - LAYER_H) / 2F,
                    Math.min(VIEW_W, VIEW_H - LAYER_H), 20, currentYaw(), pitch);
            if (!formed) renderStructure(graphics);
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 400);
            if (!shown) {
                var lines = font.split(Component.translatable("jei.tc4modern.multiblock.formed.unavailable"), VIEW_W - 8);
                for (int i = 0; i < lines.size(); i++)
                    graphics.drawString(font, lines.get(i), 4, (VIEW_H - LAYER_H) / 2 - lines.size() * 5 + i * 10, INK, false);
            }
            if (!formed) {
                var label = layerLabel();
                boolean hover = mouseX >= 0 && mouseX < font.width(label) + 4 && mouseY >= VIEW_H - LAYER_H && mouseY < VIEW_H;
                graphics.drawString(font, label, 0, VIEW_H - LAYER_H + 1, hover ? 0xFF2050C0 : INK, false);
            }
            var toggle = formLabel();
            int toggleX = VIEW_W - font.width(toggle);
            boolean hover = mouseX >= toggleX - 4 && mouseX < VIEW_W && mouseY >= VIEW_H - LAYER_H && mouseY < VIEW_H;
            graphics.drawString(font, toggle, toggleX, VIEW_H - LAYER_H + 1, hover ? 0xFF2050C0 : INK, false);
            graphics.pose().popPose();
        }

        /** Names the view a click switches to. */
        private Component formLabel() {
            return Component.translatable(formed ? "jei.tc4modern.multiblock.show_unformed" : "jei.tc4modern.multiblock.show_formed");
        }

        private Component layerLabel() {
            return layer < 0 ? Component.translatable("jei.tc4modern.multiblock.layers.all")
                    : Component.translatable("jei.tc4modern.multiblock.layers.one", layer + 1, recipe.sizeY());
        }

        @Override
        public void getTooltip(ITooltipBuilder tooltip, double mouseX, double mouseY) {
            tooltip.add(Component.translatable("jei.tc4modern.multiblock.controls").withStyle(ChatFormatting.GRAY));
        }

        @Override
        public boolean handleInput(double mouseX, double mouseY, IJeiUserInput input) {
            var key = input.getKey();
            if (key.getType() != InputConstants.Type.MOUSE || key.getValue() != InputConstants.MOUSE_BUTTON_LEFT) return false;
            // JEI passes positions relative to getArea().
            var font = Minecraft.getInstance().font;
            double x = mouseX, y = mouseY;
            if (y < VIEW_H - LAYER_H || y >= VIEW_H) return false;
            if (x >= VIEW_W - font.width(formLabel()) - 4 && x < VIEW_W) {
                if (!input.isSimulate()) formed = !formed;
                return true;
            }
            if (formed || x < 0 || x >= font.width(layerLabel()) + 4) return false;
            if (!input.isSimulate()) stepLayer(1);
            return true;
        }

        @Override
        public boolean handleMouseScrolled(double mouseX, double mouseY, double delta) {
            if (formed || recipe.sizeY() < 2 || delta == 0) return false;
            stepLayer(delta > 0 ? 1 : -1);
            return true;
        }

        @Override
        public boolean handleMouseDragged(double mouseX, double mouseY, InputConstants.Key key, double dragX, double dragY) {
            if (!turned) { yaw = currentYaw(); turned = true; }
            yaw += (float) dragX * 2;
            pitch = Mth.clamp(pitch + (float) dragY * 2, -90, 90);
            return true;
        }

        /** Cycles all layers, then each from the bottom up. */
        private void stepLayer(int step) {
            int states = recipe.sizeY() + 1;
            layer = Math.floorMod(layer + 1 + step, states) - 1;
        }

        private float currentYaw() { return turned ? yaw : yaw + (Util.getMillis() - start) / 60F; }

        private void renderStructure(GuiGraphics graphics) {
            var mc = Minecraft.getInstance();
            float diagonal = Mth.sqrt(recipe.sizeX() * recipe.sizeX() + recipe.sizeY() * recipe.sizeY() + recipe.sizeZ() * recipe.sizeZ());
            float scale = Math.min(20, Math.min(VIEW_W, VIEW_H - LAYER_H) / diagonal);
            var pose = graphics.pose();
            pose.pushPose();
            pose.translate(VIEW_W / 2F, (VIEW_H - LAYER_H) / 2F, 200);
            pose.scale(scale, -scale, scale);
            pose.mulPose(Axis.XP.rotationDegrees(pitch));
            pose.mulPose(Axis.YP.rotationDegrees(currentYaw()));
            pose.translate(-recipe.sizeX() / 2F, -recipe.sizeY() / 2F, -recipe.sizeZ() / 2F);
            Lighting.setupForEntityInInventory();
            RenderSystem.enableDepthTest();
            var buffers = graphics.bufferSource();
            for (var block : recipe.blocks()) {
                if (layer >= 0 && block.y() != layer) continue;
                pose.pushPose();
                pose.translate(block.x(), block.y(), block.z());
                render(mc, block.part(), pose, buffers, currentYaw(), pitch);
                pose.popPose();
            }
            graphics.flush();
            Lighting.setupFor3DItems();
            pose.popPose();
        }

        /** The original block's own renderers, else the block's model, else its item, else a cube of its texture, else a marker. */
        private static void render(Minecraft mc, Part part, PoseStack pose, MultiBufferSource buffers, float yaw, float pitch) {
            // Tile-rendered originals (rune matrix, table) have no host model; the preview scene draws them as placed.
            if (part.legacyKey() != null && FormedStructures.renderBlock(part.legacyKey(), part.metadata(), pose, buffers, yaw + 180, pitch)) return;
            var state = part.state();
            var blocks = mc.getBlockRenderer();
            try {
                if (state != null && state.getFluidState().isEmpty() && state.getRenderShape() == RenderShape.MODEL
                        && blocks.getBlockModel(state) != mc.getModelManager().getMissingModel()) {
                    blocks.renderSingleBlock(state, pose, buffers, FULL_BRIGHT, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
                    return;
                }
                if (!part.items().isEmpty() && (state == null || state.getFluidState().isEmpty())) {
                    pose.pushPose();
                    pose.translate(0.5, 0.5, 0.5);
                    mc.getItemRenderer().renderStatic(part.items().get(0), ItemDisplayContext.NONE, FULL_BRIGHT,
                            OverlayTexture.NO_OVERLAY, pose, buffers, mc.level, 0);
                    pose.popPose();
                    return;
                }
                if (state != null) {
                    var sprite = blocks.getBlockModelShaper().getParticleIcon(state);
                    if (!sprite.contents().name().equals(MissingTextureAtlasSprite.getLocation())) {
                        // No culling, so the face winding below needs no care.
                        cube(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS)), sprite);
                        return;
                    }
                }
            } catch (RuntimeException unrenderable) {
                // Original blocks without a usable host model still get the marker below.
            }
            LevelRenderer.addChainedFilledBoxVertices(pose, buffers.getBuffer(RenderType.debugFilledBox()),
                    0.3, 0.3, 0.3, 0.7, 0.7, 0.7, 0.85F, 0.6F, 1F, 0.8F);
        }

        private static void cube(PoseStack pose, com.mojang.blaze3d.vertex.VertexConsumer consumer,
                                 net.minecraft.client.renderer.texture.TextureAtlasSprite sprite) {
            var matrix = pose.last().pose();
            var normals = pose.last().normal();
            float u0 = sprite.getU0(), u1 = sprite.getU1(), v0 = sprite.getV0(), v1 = sprite.getV1();
            for (var face : Direction.values()) {
                var n = face.getNormal();
                float[][] corners = corners(face);
                float[][] uv = {{u0, v1}, {u1, v1}, {u1, v0}, {u0, v0}};
                for (int i = 0; i < 4; i++) {
                    consumer.vertex(matrix, corners[i][0], corners[i][1], corners[i][2]).color(255, 255, 255, 255)
                            .uv(uv[i][0], uv[i][1]).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(FULL_BRIGHT)
                            .normal(normals, n.getX(), n.getY(), n.getZ()).endVertex();
                }
            }
        }

        /** The four corners of a unit cube face, in texture order. */
        private static float[][] corners(Direction face) {
            return switch (face) {
                case DOWN -> new float[][]{{0, 0, 1}, {0, 0, 0}, {1, 0, 0}, {1, 0, 1}};
                case UP -> new float[][]{{0, 1, 0}, {0, 1, 1}, {1, 1, 1}, {1, 1, 0}};
                case NORTH -> new float[][]{{1, 0, 0}, {0, 0, 0}, {0, 1, 0}, {1, 1, 0}};
                case SOUTH -> new float[][]{{0, 0, 1}, {1, 0, 1}, {1, 1, 1}, {0, 1, 1}};
                case WEST -> new float[][]{{0, 0, 0}, {0, 0, 1}, {0, 1, 1}, {0, 1, 0}};
                case EAST -> new float[][]{{1, 0, 1}, {1, 0, 0}, {1, 1, 0}, {1, 1, 1}};
            };
        }
    }
}
