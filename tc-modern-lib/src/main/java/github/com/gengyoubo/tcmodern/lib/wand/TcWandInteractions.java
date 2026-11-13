package github.com.gengyoubo.tcmodern.lib.wand;

import java.util.Objects;
import java.util.function.Function;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/** Forwards modern wand item actions to a temporary original-TC4 host stack. */
public final class TcWandInteractions {
    private TcWandInteractions() { }
    /** The addon owns its item identity, data format, proxy creation and state conversion. */
    public interface Adapter {
        ItemStack proxy(ItemStack modern);
        boolean isProxy(ItemStack stack);
        void sync(ItemStack proxy, ItemStack modern);
    }
    /** Temporarily replaces the held item and synchronizes its final proxy state even if action throws. */
    public static <T> T withProxy(@Nullable Player player, InteractionHand hand, ItemStack modern,
                                  Adapter adapter, Function<ItemStack,T> action) {
        Objects.requireNonNull(modern); Objects.requireNonNull(adapter); Objects.requireNonNull(action);
        ItemStack proxy = Objects.requireNonNull(adapter.proxy(modern));
        if (player != null) player.setItemInHand(hand, proxy);
        try { return action.apply(proxy); }
        finally {
            ItemStack after = proxy;
            if (player != null) {
                ItemStack held = player.getItemInHand(hand);
                if (held.isEmpty()) { adapter.sync(after, modern); modern.setCount(0); }
                else if (adapter.isProxy(held)) { after = held; player.setItemInHand(hand, modern); }
            }
            if (!modern.isEmpty()) adapter.sync(after, modern);
        }
    }
    private static UseOnContext context(UseOnContext old, ItemStack proxy) {
        var hit = new BlockHitResult(old.getClickLocation(), old.getClickedFace(), old.getClickedPos(), old.isInside());
        return new UseOnContext(old.getLevel(), old.getPlayer(), old.getHand(), proxy, hit) { };
    }
    private static void resume(@Nullable Player player, InteractionHand hand, Adapter adapter) {
        if (player != null && player.isUsingItem() && player.getUsedItemHand() == hand && adapter.isProxy(player.getUseItem())) {
            player.stopUsingItem(); player.startUsingItem(hand);
        }
    }
    public static InteractionResult onItemUseFirst(ItemStack wand, UseOnContext context, Adapter adapter) {
        var result = withProxy(context.getPlayer(), context.getHand(), wand, adapter,
                proxy -> proxy.getItem().onItemUseFirst(proxy, context(context, proxy)));
        resume(context.getPlayer(), context.getHand(), adapter); return result;
    }
    public static InteractionResult useOn(ItemStack wand, UseOnContext context, Adapter adapter) {
        var result = withProxy(context.getPlayer(), context.getHand(), wand, adapter,
                proxy -> proxy.getItem().useOn(context(context, proxy)));
        resume(context.getPlayer(), context.getHand(), adapter); return result;
    }
    public static InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand, Adapter adapter) {
        var wand = player.getItemInHand(hand);
        var result = withProxy(player, hand, wand, adapter, proxy -> {
            var holder = proxy.getItem().use(level, player, hand);
            if (adapter.isProxy(holder.getObject())) player.setItemInHand(hand, holder.getObject());
            return holder.getResult();
        });
        resume(player, hand, adapter); return new InteractionResultHolder<>(result, wand);
    }
    public static void onUseTick(Level level, LivingEntity entity, ItemStack wand, int remaining, Adapter adapter) {
        if (entity instanceof Player player) withProxy(player, player.getUsedItemHand(), wand, adapter, proxy -> {
            proxy.getItem().onUseTick(level, player, proxy, remaining); return null;
        });
    }
    public static void releaseUsing(ItemStack wand, Level level, LivingEntity entity, int remaining, Adapter adapter) {
        if (entity instanceof Player player) withProxy(player, player.getUsedItemHand(), wand, adapter, proxy -> {
            proxy.getItem().releaseUsing(proxy, level, player, remaining); return null;
        });
    }
    public static int useDuration(ItemStack wand, Adapter adapter) { var proxy = adapter.proxy(wand); return proxy.getItem().getUseDuration(proxy); }
    public static UseAnim useAnimation(ItemStack wand, Adapter adapter) { var proxy = adapter.proxy(wand); return proxy.getItem().getUseAnimation(proxy); }
}
