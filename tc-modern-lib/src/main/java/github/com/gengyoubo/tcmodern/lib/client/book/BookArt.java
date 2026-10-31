package github.com.gengyoubo.tcmodern.lib.client.book;

import com.mojang.blaze3d.systems.RenderSystem;
import github.com.gengyoubo.mfcl.api.LegacyMappings;
import github.com.gengyoubo.mfcl.api.LegacyStack;
import github.com.gengyoubo.tcmodern.api.TcModernApi;
import github.com.gengyoubo.tcmodern.api.aspect.TcAspect;
import github.com.gengyoubo.tcmodern.api.aspect.TcAspects;
import github.com.gengyoubo.tcmodern.api.research.TcResearchEntry;
import github.com.gengyoubo.tcmodern.api.research.TcThaumonomicon;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.ItemStack;

/** The original Thaumonomicon's textures and drawing routines (UtilsFX), shared by the browser and the page book. */
final class BookArt {
    static final ResourceLocation RESEARCH = tc("textures/gui/gui_research.png"), BOOK = tc("textures/gui/gui_researchbook.png"),
            OVERLAY = tc("textures/gui/gui_researchbook_overlay.png"), NODES = tc("textures/misc/nodes.png"),
            ASPECT_BACK = tc("textures/aspects/_back.png"), UNKNOWN = tc("textures/aspects/_unknown.png");
    private static final Map<LegacyStack, ItemStack> ITEMS = new ConcurrentHashMap<>();
    private static Map<String, TcAspect> aspects;

    private BookArt() {}

    static ResourceLocation tc(String path) { return ResourceLocation.fromNamespaceAndPath("thaumcraft", path); }

    /** Forgets converted items and aspects after a reload. */
    static void invalidate() {
        ITEMS.clear();
        aspects = null;
    }

    /** An original translation (StatCollector), so Thaumcraft's own language files apply. */
    static String translate(String key) {
        return TcModernApi.runtime().map(runtime -> TcThaumonomicon.translate(runtime, key)).orElse(key);
    }

    /** {@code translate} with {@code %s} / {@code %1$s} filled in, like ChatComponentTranslation. */
    static String format(String key, Object... args) {
        String text = translate(key);
        for (int i = 0; i < args.length; i++) text = text.replace("%" + (i + 1) + "$s", String.valueOf(args[i]));
        for (Object arg : args) text = text.replaceFirst("%s", java.util.regex.Matcher.quoteReplacement(String.valueOf(arg)));
        return text;
    }

    static String name(String key) { return translate("tc.research_name." + key); }
    static String text(String key) { return translate("tc.research_text." + key); }

    static ItemStack item(LegacyStack stack) {
        if (stack == null) return ItemStack.EMPTY;
        return ITEMS.computeIfAbsent(stack, key -> TcModernApi.runtime().map(runtime -> {
            try {
                var fixed = key.metadata() == TcThaumonomicon.WILDCARD ? new LegacyStack(key.key(), 0, Math.max(1, key.count()), key.snbt()) : key;
                return runtime.mappings().toHost(LegacyMappings.ITEM_STACK, runtime.stack(fixed), ItemStack.class).orElse(ItemStack.EMPTY);
            } catch (RuntimeException unmapped) {
                return ItemStack.EMPTY;
            }
        }).orElse(ItemStack.EMPTY));
    }

    /** One of several alternatives, changing once a second as InventoryUtils.cycleItemStack does. */
    static LegacyStack cycle(List<LegacyStack> choices) {
        if (choices == null || choices.isEmpty()) return null;
        return choices.get((int) (System.currentTimeMillis() / 1000 % choices.size()));
    }

    static synchronized TcAspect aspect(String tag) {
        if (aspects == null) {
            var map = new HashMap<String, TcAspect>();
            TcModernApi.runtime().ifPresent(runtime -> TcAspects.snapshot(runtime).forEach(aspect -> map.put(aspect.tag(), aspect)));
            aspects = map;
        }
        return aspects.get(tag);
    }

    /** Aspect.getName(): the tag, capitalised. */
    static String aspectName(String tag) {
        return tag.isEmpty() ? tag : tag.substring(0, 1).toUpperCase(Locale.ROOT) + tag.substring(1).toLowerCase(Locale.ROOT);
    }

    /** Aspect.getLocalizedDescription(). */
    static String aspectDescription(String tag) { return translate("tc.aspect." + tag); }

    /** UtilsFX.drawTag: the icon in the aspect's colour, the amount in half-size white with a black outline. */
    static void drawTag(GuiGraphics g, String tag, float x, float y, int amount, float alpha) {
        var aspect = aspect(tag);
        if (aspect == null) return;
        var texture = ResourceLocation.tryParse(aspect.texture());
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        int color = aspect.color();
        g.setColor((color >> 16 & 255) / 255F, (color >> 8 & 255) / 255F, (color & 255) / 255F, alpha);
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        if (texture != null) g.blit(texture, 0, 0, 16, 16, 0F, 0F, 1, 1, 1, 1);
        g.pose().popPose();
        g.setColor(1, 1, 1, 1);
        if (amount > 0) {
            var font = Minecraft.getInstance().font;
            String text = String.valueOf(amount);
            int w = font.width(text);
            g.pose().pushPose();
            g.pose().translate(0, 0, 1);
            g.pose().scale(0.5F, 0.5F, 1);
            int tx = 32 - w + (int) x * 2, ty = 32 - font.lineHeight + (int) y * 2;
            for (int a = -1; a <= 1; a++)
                for (int b = -1; b <= 1; b++)
                    if ((a == 0 || b == 0) && (a != 0 || b != 0)) g.drawString(font, text, tx + a, ty + b, 0xFF000000, false);
            g.drawString(font, text, tx, ty, 0xFFFFFFFF, false);
            g.pose().popPose();
        }
    }

    /** A research icon, item or texture, 16 px at {@code x, y}; the current shader colour tints it. */
    static void icon(GuiGraphics g, TcResearchEntry entry, int x, int y) {
        if (entry.iconItem() != null) {
            var stack = item(entry.iconItem());
            if (!stack.isEmpty()) g.renderItem(stack, x, y);
        } else {
            var texture = ResourceLocation.tryParse(entry.iconTexture());
            if (texture != null) {
                RenderSystem.enableBlend();
                g.blit(texture, x, y, 16, 16, 0F, 0F, 1, 1, 1, 1);
            }
        }
    }

    /** A 256-space rectangle grown about its centre by {@code 1 + scale} (drawTexturedModalRectScaled). */
    static void blitScaled(GuiGraphics g, ResourceLocation texture, int x, int y, int u, int v, int w, int h, float scale) {
        g.pose().pushPose();
        g.pose().translate(x + w / 2F, y + h / 2F, 0);
        g.pose().scale(1 + scale, 1 + scale, 1);
        RenderSystem.enableBlend();
        g.blit(texture, -w / 2, -h / 2, u, v, w, h);
        g.pose().popPose();
    }

    static void sound(String id, float volume, float pitch) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvent.createVariableRangeEvent(tc(id)), pitch, volume));
    }
}
