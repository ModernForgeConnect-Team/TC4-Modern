package github.com.gengyoubo.tcmodern.tc4.compat.ftbquests;

import dev.ftb.mods.ftblibrary.config.ConfigGroup;
import dev.ftb.mods.ftblibrary.config.NameMap;
import dev.ftb.mods.ftblibrary.icon.Color4I;
import dev.ftb.mods.ftblibrary.icon.Icon;
import dev.ftb.mods.ftblibrary.icon.ItemIcon;
import github.com.gengyoubo.mfcl.api.LegacyMappings;
import github.com.gengyoubo.mfcl.api.LegacyObject;
import github.com.gengyoubo.mfcl.api.LegacyRuntime;
import github.com.gengyoubo.mfcl.api.mla.*;
import github.com.gengyoubo.tcmodern.api.TcModernApi;
import github.com.gengyoubo.tcmodern.api.aspect.TcAspect;
import github.com.gengyoubo.tcmodern.api.aspect.TcAspects;
import github.com.gengyoubo.tcmodern.api.research.TcResearch;
import github.com.gengyoubo.tcmodern.api.research.TcResearchAccessors;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

/** Client presentation reads the installed registries, including addon research and aspects. */
public final class QuestChoices {
    @LegacyType("thaumcraft.api.research.ResearchItem")
    public interface ResearchDisplay {
        @LegacyMethod(name = "getName", descriptor = "()Ljava/lang/String;") String name();
        @LegacyField(name = "icon_item", descriptor = "Lnet/minecraft/item/ItemStack;") LegacyObject item();
        @LegacyField(name = "icon_resource", descriptor = "Lnet/minecraft/util/ResourceLocation;") LegacyObject texture();
    }
    @LegacyType("net.minecraft.util.ResourceLocation")
    public interface Resource {
        @LegacyMethod(name = "func_110624_b", descriptor = "()Ljava/lang/String;") String namespace();
        @LegacyMethod(name = "func_110623_a", descriptor = "()Ljava/lang/String;") String path();
    }
    public record Choice(String id, Component name, Icon icon) { }
    private static LegacyRuntime cachedRuntime;
    private static String cachedLanguage;
    private static List<Choice> aspectChoices, researchChoices;
    private QuestChoices() { }

    private static LegacyRuntime catalogRuntime() {
        var runtime = TcModernApi.runtime().orElse(null);
        String language = Minecraft.getInstance().getLanguageManager().getSelected();
        if (runtime != cachedRuntime || !Objects.equals(language, cachedLanguage)) {
            cachedRuntime = runtime; cachedLanguage = language;
            aspectChoices = null; researchChoices = null;
        }
        return runtime;
    }

    public static List<Choice> aspects() {
        var runtime = catalogRuntime();
        if (runtime == null) return List.of();
        if (aspectChoices == null) aspectChoices = TcAspects.snapshot(runtime).stream()
                .map(a -> new Choice(a.tag(), aspectName(a.tag()), aspectIcon(a))).toList();
        return aspectChoices;
    }
    public static Component aspectName(String tag) { return Component.translatable("tc.aspect." + tag); }
    private static Icon aspectIcon(TcAspect aspect) {
        return aspect.texture().isBlank() ? Icon.empty() : Icon.getIcon(aspect.texture()).withTint(Color4I.rgb(aspect.color()));
    }
    public static Icon aspectIcon(String tag) {
        return aspects().stream().filter(c -> c.id().equals(tag)).map(Choice::icon).findFirst().orElse(Icon.empty());
    }
    public static List<Choice> research() {
        var api = catalogRuntime();
        if (api == null) return List.of();
        if (researchChoices != null) return researchChoices;
        var choices = new ArrayList<Choice>();
        for (String key : TcResearch.keys(api)) {
            var display = api.accessor(ResearchDisplay.class, api.accessor(TcResearchAccessors.Categories.class).find(key));
            Icon icon = Icon.empty();
            if (display.item() != null) {
                var stack = api.mappings().toHost(LegacyMappings.ITEM_STACK, display.item(), ItemStack.class).orElse(ItemStack.EMPTY);
                if (!stack.isEmpty()) icon = ItemIcon.getItemIcon(stack);
            } else if (display.texture() != null) {
                var texture = api.accessor(Resource.class, display.texture());
                icon = Icon.getIcon(texture.namespace() + ":" + texture.path());
            }
            choices.add(new Choice(key, Component.literal(display.name()), icon));
        }
        researchChoices = List.copyOf(choices);
        return researchChoices;
    }
    public static Choice research(String key) {
        return research().stream().filter(c -> c.id().equals(key)).findFirst()
                .orElse(new Choice(key, Component.literal(key), Icon.empty()));
    }
    public static void add(ConfigGroup config, String field, String selected, Consumer<String> setter,
                           List<Choice> choices, String nameKey) {
        var byId = new LinkedHashMap<String, Choice>();
        for (var choice : choices) byId.put(choice.id(), choice);
        selected = Objects.requireNonNullElse(selected, "");
        var unset = new Choice("", Component.translatable("ftbquests.tc4modern.select"), Icon.empty());
        // Preserve missing addon identifiers when editing an existing quest instead of silently changing its target.
        byId.putIfAbsent(selected, selected.isEmpty() ? unset : new Choice(selected, Component.literal(selected), Icon.empty()));
        String uiSelected = selected;
        // NameMap rejects empty IDs and IDs beginning with '-'. Using either as its default produces null,
        // which FTB passes to the display provider when building the editor's default-value tooltip.
        if (uiSelected.isEmpty() || uiSelected.startsWith("-")) {
            String placeholder = "tc4modern:unselected";
            while (byId.containsKey(placeholder)) placeholder += "_";
            byId.put(placeholder, byId.remove(uiSelected));
            uiSelected = placeholder;
        }
        config.addEnum(field, uiSelected, id -> setter.accept(byId.getOrDefault(id, unset).id()),
                NameMap.of(uiSelected, new ArrayList<>(byId.keySet())).id(id -> id)
                        .name(id -> byId.getOrDefault(id, unset).name())
                        .icon(id -> byId.getOrDefault(id, unset).icon()).create()).setNameKey(nameKey);
    }
}
