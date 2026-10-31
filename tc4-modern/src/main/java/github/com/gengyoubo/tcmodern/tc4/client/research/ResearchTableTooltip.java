package github.com.gengyoubo.tcmodern.tc4.client.research;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import java.util.List;

/** The two original aspect tooltip lines. Amounts, bonuses and shortcut hints are not tooltip content. */
public record ResearchTableTooltip(String name,String description) {
    List<String> lines(){return List.of(name,description);}
    /** UtilsFX.drawCustomTooltip with the aqua title colour the original device GUIs pass. */
    public static void draw(GuiGraphics graphics,List<String> lines,int x,int y){
        var font=Minecraft.getInstance().font;int width=lines.stream().mapToInt(font::width).max().orElse(0),height=lines.size()>1?lines.size()*10:8;
        // The original tooltip is anchored at x+12/y-12 and colours its first and remaining lines aqua/grey.
        int tx=x+12,ty=y-12;
        net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil.renderTooltipBackground(graphics,tx,ty,width,height,400,0xf0100010,0xf0100010,0x505000ff,0x5028007f);
        graphics.pose().pushPose();graphics.pose().translate(0,0,400);
        for(int i=0;i<lines.size();i++)font.drawInBatch(lines.get(i),tx,ty+(i==0?0:2+i*10),i==0?0xff55ffff:0xffaaaaaa,true,graphics.pose().last().pose(),graphics.bufferSource(),Font.DisplayMode.SEE_THROUGH,0,15728880,font.isBidirectional());
        graphics.pose().popPose();graphics.flush();
    }
}
