package github.com.gengyoubo.tcmodern.tc4.content;

import github.com.gengyoubo.tcmodern.tc4.menu.WritingToolMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.util.RandomSource;
import github.com.gengyoubo.tcmodern.tc4.init.Tc4Items;
import net.minecraftforge.network.NetworkHooks;

/** Writing capacity is stored as damage, including for empty refillable tools. */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = "tc4modern")
public final class WritingToolItem extends Item {
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void crafted(net.minecraftforge.event.entity.player.PlayerEvent.ItemCraftedEvent event) {
        ItemStack stack = event.getCrafting();
        if (stack.getItem() instanceof WritingToolItem tool && tool.refillAmount > 0 && !stack.hasTag())
            tool.store(stack, 1, 0);
    }
    private final int refillAmount;
    public WritingToolItem(int capacity, int refillAmount) {
        super(new Item.Properties().durability(capacity));
        this.refillAmount = refillAmount;
    }
    public int refillAmount() { return refillAmount; }
    public int refillCapacity() { return refillAmount == 0 ? 0 : getMaxDamage() / refillAmount; }
    public Item refillItem() { return this == Tc4Items.MECHANICAL_PENCIL.get() ? Tc4Items.PENCIL_LEAD.get() : Tc4Items.PEN_REFILL.get(); }
    private static final String COUNT = "StoredRefills", WEAR = "RefillWear";

    /** Before the storage update, refillable tools used a single durability value. Preserve that capacity. */
    public int stored(ItemStack tool) {
        if (refillAmount == 0) return 0;
        if (tool.hasTag() && tool.getTag().contains(COUNT)) return Math.max(0, Math.min(refillCapacity(), tool.getTag().getInt(COUNT)));
        int remaining = Math.max(0, tool.getMaxDamage() - tool.getDamageValue());
        return (remaining + refillAmount - 1) / refillAmount;
    }
    public int wear(ItemStack tool) {
        if (refillAmount == 0) return 0;
        if (tool.hasTag() && tool.getTag().contains(COUNT)) return Math.max(0, Math.min(refillAmount - 1, tool.getTag().getInt(WEAR)));
        return (refillAmount - (Math.max(0, tool.getMaxDamage() - tool.getDamageValue()) % refillAmount)) % refillAmount;
    }
    public int remaining(ItemStack tool) {
        return refillAmount == 0 ? Math.max(0, tool.getMaxDamage() - tool.getDamageValue()) : Math.max(0, stored(tool) * refillAmount - wear(tool));
    }
    public void store(ItemStack tool, int count, int wear) {
        count = Math.max(0, Math.min(refillCapacity(), count));
        wear = Math.max(0, Math.min(refillAmount - 1, wear));
        var tag = tool.getOrCreateTag(); tag.putInt(COUNT, count); tag.putInt(WEAR, wear);
        tool.setDamageValue(tool.getMaxDamage() - Math.max(0, count * refillAmount - wear));
    }
    public void consume(ItemStack tool, RandomSource random) {
        if (refillAmount == 0) {
            tool.setDamageValue(tool.getDamageValue() + 1);
            if (tool.getDamageValue() >= tool.getMaxDamage()) tool.shrink(1);
            return;
        }
        int count = stored(tool), used = wear(tool) + 1;
        if (count == 0) return;
        if (this == Tc4Items.MECHANICAL_PENCIL.get() && random.nextInt(32) == 0) used = refillAmount;
        if (used >= refillAmount) { count--; used = 0; }
        store(tool, count, used);
    }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (refillAmount == 0) return InteractionResultHolder.pass(stack);
        if (player instanceof ServerPlayer server) {
            int slot = hand == InteractionHand.OFF_HAND ? 40 : player.getInventory().selected;
            NetworkHooks.openScreen(server, new SimpleMenuProvider(
                    (id, inventory, p) -> new WritingToolMenu(id, inventory, slot),
                    Component.translatable("writing.tc4modern.title", stack.getHoverName())), buf -> buf.writeVarInt(slot));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
