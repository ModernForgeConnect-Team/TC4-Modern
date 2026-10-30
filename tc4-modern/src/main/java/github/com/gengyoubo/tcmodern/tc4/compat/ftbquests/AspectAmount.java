package github.com.gengyoubo.tcmodern.tc4.compat.ftbquests;

import dev.ftb.mods.ftblibrary.config.ConfigGroup;
import github.com.gengyoubo.tcmodern.api.aspect.TcPlayerAspects;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

/** One original research pool aspect, shared by task and reward serialization. */
final class AspectAmount {
    String aspect = "aer";
    int amount = 1;

    boolean valid() { return !aspect.isBlank() && amount >= 1 && amount <= TcPlayerAspects.MAX_POOL; }
    void write(CompoundTag nbt) { nbt.putString("aspect", aspect); nbt.putInt("amount", amount); }
    void read(CompoundTag nbt) { aspect = nbt.getString("aspect"); amount = nbt.getInt("amount"); }
    void write(FriendlyByteBuf buf) { buf.writeUtf(aspect); buf.writeVarInt(amount); }
    void read(FriendlyByteBuf buf) { aspect = buf.readUtf(); amount = buf.readVarInt(); }
    void configure(ConfigGroup config) {
        QuestChoices.add(config, "aspect", aspect, v -> aspect = v,
                QuestChoices.aspects(), "ftbquests.tc4modern.aspect_tag");
        config.addInt("amount", amount, v -> amount = v, 1, 1, TcPlayerAspects.MAX_POOL)
                .setNameKey("ftbquests.tc4modern.amount");
    }
}
