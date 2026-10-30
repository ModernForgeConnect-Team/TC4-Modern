package github.com.gengyoubo.tcmodern.tc4.compat.ftbquests;

import dev.ftb.mods.ftblibrary.icon.Icon;
import dev.ftb.mods.ftbquests.quest.reward.RewardType;
import dev.ftb.mods.ftbquests.quest.reward.RewardTypes;
import dev.ftb.mods.ftbquests.quest.task.TaskType;
import dev.ftb.mods.ftbquests.quest.task.TaskTypes;
import github.com.gengyoubo.tcmodern.tc4.Tc4Modern;
import net.minecraft.resources.ResourceLocation;

/** Loaded only when FTB Quests is installed, before quest files are read. */
public final class Tc4FtbQuests {
    public static final TaskType RESEARCH = TaskTypes.register(id("research"), ResearchTask::new,
            () -> Icon.getIcon("thaumcraft:textures/items/researchnotes.png"));
    public static final TaskType ASPECT = TaskTypes.register(id("aspect"), AspectTask::new,
            () -> QuestChoices.aspectIcon("aer"));
    public static final RewardType ASPECT_REWARD = RewardTypes.register(id("aspect"), AspectReward::new,
            () -> QuestChoices.aspectIcon("aer"));

    private Tc4FtbQuests() { }
    public static void register() { /* Class initialization registers both registries. */ }
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(Tc4Modern.MOD_ID, path); }
}
