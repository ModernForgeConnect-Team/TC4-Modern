package github.com.gengyoubo.tcmodern.lib.client;

import com.mojang.blaze3d.systems.RenderSystem;
import github.com.gengyoubo.tcmodern.api.aspect.TcAspect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.resources.ResourceLocation;

/** Aspect names and icons shared by the JEI and Jade integrations; neither mod is linked here. */
public final class AspectIcons {
    private AspectIcons() { }

    public static String name(String tag) {
        String key = "tc.aspect." + tag;
        return I18n.exists(key) ? I18n.get(key) : tag;
    }

    /** The original icon tinted with the aspect colour, or a coloured square with its initial. */
    public static void render(GuiGraphics graphics, TcAspect aspect, int x, int y) {
        var texture = ResourceLocation.tryParse(aspect.texture());
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        if (texture != null && !aspect.texture().isEmpty() && Minecraft.getInstance().getResourceManager().getResource(texture).isPresent()) {
            int color = aspect.color();
            graphics.setColor((color >> 16 & 255) / 255F, (color >> 8 & 255) / 255F, (color & 255) / 255F, 1);
            graphics.blit(texture, x, y, 16, 16, 0F, 0F, 32, 32, 32, 32);
            graphics.setColor(1, 1, 1, 1);
        } else {
            graphics.fill(x, y, x + 16, y + 16, 0xFF000000 | aspect.color());
            graphics.drawString(Minecraft.getInstance().font, aspect.tag().substring(0, 1), x + 4, y + 2, 0xFFFFFFFF, true);
        }
        RenderSystem.disableBlend();
    }
}
