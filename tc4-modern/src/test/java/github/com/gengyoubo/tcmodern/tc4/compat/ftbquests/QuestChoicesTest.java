package github.com.gengyoubo.tcmodern.tc4.compat.ftbquests;

import dev.ftb.mods.ftblibrary.config.ConfigGroup;
import dev.ftb.mods.ftblibrary.config.EnumConfig;
import dev.ftb.mods.ftblibrary.icon.Icon;
import dev.ftb.mods.ftblibrary.util.TooltipList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class QuestChoicesTest {
    private static final List<QuestChoices.Choice> RESEARCH = List.of(
            new QuestChoices.Choice("NITOR", Component.literal("Nitor"), Icon.empty()),
            new QuestChoices.Choice("ALCHEMY", Component.literal("Alchemy"), Icon.empty()));

    @Test
    void newResearchCanShowDefaultTooltipAndResetWithoutSelectingResearch() {
        var stored = new AtomicReference<>("");
        var selector = selector(stored, RESEARCH);
        assertDoesNotThrow(() -> selector.addInfo(new TooltipList()));
        assertNotNull(selector.getDefaultValue());
        assertNotNull(selector.nameMap.defaultValue);
        assertEquals("", stored.get());
        selector.setCurrentValue(selector.getDefaultValue());
        selector.applyValue();
        assertEquals("", stored.get());
        selector.setCurrentValue("NITOR"); selector.applyValue();
        assertEquals("NITOR", stored.get());
    }

    @Test
    void emptyRegistryAndUnknownAddonKeysRenderWithoutLosingTheSavedTarget() {
        for (String selected : List.of("", "MISSING_ADDON_RESEARCH", "-ADDON_KEY")) {
            var stored = new AtomicReference<>(selected);
            var selector = selector(stored, List.of());
            assertDoesNotThrow(() -> selector.addInfo(new TooltipList()));
            assertNotNull(selector.nameMap.defaultValue);
            selector.setCurrentValue(selector.getDefaultValue()); selector.applyValue();
            assertEquals(selected, stored.get());
        }
    }

    @Test
    void absentDisplayValuesHaveSafeNamesAndIcons() {
        var selector = selector(new AtomicReference<>("NITOR"), RESEARCH);
        assertDoesNotThrow(() -> selector.getStringForGUI(null));
        assertDoesNotThrow(() -> selector.getStringForGUI("NOT_IN_CHOICES"));
        assertDoesNotThrow(() -> selector.nameMap.getIcon(null));
        assertDoesNotThrow(() -> selector.nameMap.getIcon("NOT_IN_CHOICES"));
    }

    @SuppressWarnings("unchecked")
    private static EnumConfig<String> selector(AtomicReference<String> value, List<QuestChoices.Choice> choices) {
        var group = new ConfigGroup("test");
        QuestChoices.add(group, "research", value.get(), value::set, choices, "test.research");
        return (EnumConfig<String>) group.getValues().iterator().next();
    }
}
