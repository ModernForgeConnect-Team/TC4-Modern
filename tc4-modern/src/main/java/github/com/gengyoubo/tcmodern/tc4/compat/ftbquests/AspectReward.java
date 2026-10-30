package github.com.gengyoubo.tcmodern.tc4.compat.ftbquests;

import com.mojang.logging.LogUtils;
import dev.ftb.mods.ftblibrary.config.ConfigGroup;
import dev.ftb.mods.ftblibrary.icon.Icon;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.ServerQuestFile;
import dev.ftb.mods.ftbquests.quest.reward.Reward;
import dev.ftb.mods.ftbquests.quest.reward.RewardType;
import github.com.gengyoubo.tcmodern.api.TcModernApi;
import github.com.gengyoubo.tcmodern.api.aspect.TcPlayerAspects;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class AspectReward extends Reward {
    private final AspectAmount reward = new AspectAmount();

    public AspectReward(long id, Quest quest) { super(id, quest); }
    @Override public RewardType getType() { return Tc4FtbQuests.ASPECT_REWARD; }
    @Override public void claim(ServerPlayer player, boolean notify) {
        try {
            if (!reward.valid()) throw new IllegalArgumentException("Invalid aspect reward");
            var runtime = TcModernApi.runtime().orElseThrow(() -> new IllegalStateException("Thaumcraft is not ready"));
            TcPlayerAspects.give(runtime, player, Map.of(reward.aspect, reward.amount));
        } catch (RuntimeException failure) {
            // FTB marks a reward claimed before invoking claim(). Let a failed grant be retried.
            if (ServerQuestFile.INSTANCE != null)
                ServerQuestFile.INSTANCE.getOrCreateTeamData(player).resetReward(player.getUUID(), this);
            LogUtils.getLogger().error("Unable to grant TC4 aspect reward {}", id, failure);
            player.sendSystemMessage(Component.translatable("ftbquests.tc4modern.reward_failed"));
        }
    }
    @Override public Component getAltTitle() {
        return QuestChoices.aspectName(reward.aspect).copy().append(" × " + reward.amount);
    }
    @Override public Icon getAltIcon() { return QuestChoices.aspectIcon(reward.aspect); }
    @Override public String getButtonText() { return "+" + reward.amount; }
    @Override public void writeData(CompoundTag nbt) { super.writeData(nbt); reward.write(nbt); }
    @Override public void readData(CompoundTag nbt) { super.readData(nbt); reward.read(nbt); }
    @Override public void writeNetData(FriendlyByteBuf buf) { super.writeNetData(buf); reward.write(buf); }
    @Override public void readNetData(FriendlyByteBuf buf) { super.readNetData(buf); reward.read(buf); }
    @Override public void fillConfigGroup(ConfigGroup config) { super.fillConfigGroup(config); reward.configure(config); }
}
