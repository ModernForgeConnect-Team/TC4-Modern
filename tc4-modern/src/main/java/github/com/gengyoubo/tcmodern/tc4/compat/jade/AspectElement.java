package github.com.gengyoubo.tcmodern.tc4.compat.jade;

import github.com.gengyoubo.tcmodern.api.aspect.TcAspect;
import github.com.gengyoubo.tcmodern.lib.client.AspectIcons;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.phys.Vec2;
import snownee.jade.api.ui.Element;

/** An aspect icon in a Jade tooltip, optionally with its amount in the corner like an item stack count. */
final class AspectElement extends Element {
    private static final Vec2 SIZE = new Vec2(18, 18);
    private final TcAspect aspect;
    private final int amount;

    AspectElement(TcAspect aspect, int amount) {
        this.aspect = aspect;
        this.amount = amount;
    }

    @Override public Vec2 getSize() { return SIZE; }

    @Override public void render(GuiGraphics graphics, float x, float y, float maxX, float maxY) {
        AspectIcons.render(graphics, aspect, Math.round(x + 1), Math.round(y + 1));
        if (amount <= 0) return;
        var font = Minecraft.getInstance().font;
        String text = Integer.toString(amount);
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 200);
        graphics.drawString(font, text, Math.round(x + 18 - font.width(text)), Math.round(y + 10), 0xFFFFFFFF, true);
        graphics.pose().popPose();
    }

    @Override public String getMessage() { return AspectIcons.name(aspect.tag()) + " " + amount; }
}
