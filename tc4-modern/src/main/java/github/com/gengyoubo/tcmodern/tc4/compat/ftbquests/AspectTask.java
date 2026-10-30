package github.com.gengyoubo.tcmodern.tc4.compat.ftbquests;

import dev.ftb.mods.ftblibrary.config.ConfigGroup;
import dev.ftb.mods.ftblibrary.icon.Icon;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.quest.task.Task;
import dev.ftb.mods.ftbquests.quest.task.TaskType;
import github.com.gengyoubo.tcmodern.api.TcModernApi;
import github.com.gengyoubo.tcmodern.api.aspect.TcPlayerAspects;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public final class AspectTask extends Task {
    private final AspectAmount requirement = new AspectAmount();
    private boolean consume;

    public AspectTask(long id, Quest quest) { super(id, quest); }
    @Override public TaskType getType() { return Tc4FtbQuests.ASPECT; }
    @Override public long getMaxProgress() { return requirement.valid() ? requirement.amount : 1; }
    @Override public boolean consumesResources() { return consume; }
    @Override public int autoSubmitOnPlayerTick() { return consume ? 0 : 20; }
    @Override public void submitTask(TeamData data, ServerPlayer player, ItemStack craftedItem) {
        if (!requirement.valid() || data.isLocked() || data.isCompleted(this)
                || !data.canStartTasks(getQuest()) || !checkTaskSequence(data)) return;
        TcModernApi.runtime().ifPresent(runtime -> {
            if (consume) {
                // Charge only the remainder, so existing progress never causes a second full payment.
                int remaining = (int) Math.max(0, requirement.amount - data.getProgress(this));
                if (remaining > 0 && TcPlayerAspects.consume(runtime, player, requirement.aspect, remaining))
                    data.setProgress(this, requirement.amount);
            } else if (TcPlayerAspects.pool(runtime, player, requirement.aspect) >= requirement.amount) {
                data.setProgress(this, requirement.amount);
            }
        });
    }
    @Override public Component getAltTitle() {
        return QuestChoices.aspectName(requirement.aspect).copy().append(" × " + requirement.amount);
    }
    @Override public Icon getAltIcon() { return QuestChoices.aspectIcon(requirement.aspect); }
    @Override public void writeData(CompoundTag nbt) {
        super.writeData(nbt); requirement.write(nbt); nbt.putBoolean("consume", consume);
    }
    @Override public void readData(CompoundTag nbt) {
        super.readData(nbt); requirement.read(nbt); consume = nbt.getBoolean("consume");
    }
    @Override public void writeNetData(FriendlyByteBuf buf) {
        super.writeNetData(buf); requirement.write(buf); buf.writeBoolean(consume);
    }
    @Override public void readNetData(FriendlyByteBuf buf) {
        super.readNetData(buf); requirement.read(buf); consume = buf.readBoolean();
    }
    @Override public void fillConfigGroup(ConfigGroup config) {
        super.fillConfigGroup(config); requirement.configure(config);
        config.addBool("consume", consume, v -> consume = v, false).setNameKey("ftbquests.tc4modern.consume");
    }
}
