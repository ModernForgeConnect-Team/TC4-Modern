package github.com.gengyoubo.tcmodern.tc4.compat.jade;

import github.com.gengyoubo.tcmodern.tc4.client.FormedStructures;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.world.phys.Vec2;
import snownee.jade.api.ui.Element;

/** A formed TC4 multiblock as the Jade icon, turning slowly. */
final class FormedElement extends Element {
    private static final Vec2 SIZE = new Vec2(24, 24);
    private final String id;

    FormedElement(String id) { this.id = id; }

    @Override public Vec2 getSize() { return SIZE; }

    @Override public void render(GuiGraphics graphics, float x, float y, float maxX, float maxY) {
        FormedStructures.render(graphics, id, x + SIZE.x / 2, y + SIZE.y / 2, SIZE.x, 12, Util.getMillis() / 40F % 360, 25);
    }

    @Override public String getMessage() { return I18n.get("jei.tc4modern.multiblock." + id); }
}
