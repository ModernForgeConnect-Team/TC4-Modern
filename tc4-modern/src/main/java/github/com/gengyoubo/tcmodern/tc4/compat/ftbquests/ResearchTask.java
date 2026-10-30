package github.com.gengyoubo.tcmodern.tc4.compat.ftbquests;

import dev.ftb.mods.ftblibrary.config.ConfigGroup;
import dev.ftb.mods.ftblibrary.icon.Icon;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.quest.task.AbstractBooleanTask;
import dev.ftb.mods.ftbquests.quest.task.TaskType;
import github.com.gengyoubo.tcmodern.api.TcModernApi;
import github.com.gengyoubo.tcmodern.api.research.TcResearch;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Requires actually unlocking the research; holding a research note does not count. */
public final class ResearchTask extends AbstractBooleanTask {
    private String research = "";

    public ResearchTask(long id, Quest quest) { super(id, quest); }
    @Override public TaskType getType() { return Tc4FtbQuests.RESEARCH; }
    @Override public int autoSubmitOnPlayerTick() { return 20; }
    @Override public boolean canSubmit(TeamData data, ServerPlayer player) {
        return !data.isLocked() && data.canStartTasks(getQuest()) && TcModernApi.runtime()
                .map(runtime -> TcResearch.isComplete(runtime, player, research)).orElse(false);
    }
    @Override public Component getAltTitle() {
        return QuestChoices.research(research).name();
    }
    @Override public Icon getAltIcon() { return QuestChoices.research(research).icon(); }
    @Override public void writeData(CompoundTag nbt) { super.writeData(nbt); nbt.putString("research", research); }
    @Override public void readData(CompoundTag nbt) { super.readData(nbt); research = nbt.getString("research"); }
    @Override public void writeNetData(FriendlyByteBuf buf) { super.writeNetData(buf); buf.writeUtf(research); }
    @Override public void readNetData(FriendlyByteBuf buf) { super.readNetData(buf); research = buf.readUtf(); }
    @Override public void fillConfigGroup(ConfigGroup config) {
        super.fillConfigGroup(config);
        QuestChoices.add(config, "research", research, v -> research = v,
                QuestChoices.research(), "ftbquests.tc4modern.research_key");
    }
}
