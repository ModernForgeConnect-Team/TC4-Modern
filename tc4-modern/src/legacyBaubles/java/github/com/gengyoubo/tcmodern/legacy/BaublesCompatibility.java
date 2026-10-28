package github.com.gengyoubo.tcmodern.legacy;

import baubles.api.IBauble;
import baubles.common.lib.PlayerHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingEvent.LivingUpdateEvent;

/** Independent compatibility provider, not a copy or redistribution of original Baubles. */
@Mod(modid = "Baubles", name = "TC4 Modern: Baubles API compatibility", version = "1.0.1.10",
        acceptedMinecraftVersions = "[1.7.10]")
public final class BaublesCompatibility {
    @Mod.EventHandler public void init(FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(this);
    }
    // With LIVING_UPDATE forwarding MFCL delegates bauble ticking to this event; otherwise
    // MFCL calls onWornTick itself. Both paths operate on the same supplied stackList.
    @SubscribeEvent public void update(LivingUpdateEvent event) {
        if (!(event.entityLiving instanceof EntityPlayer)) return;
        EntityPlayer player = (EntityPlayer) event.entityLiving;
        for (ItemStack stack : PlayerHandler.getPlayerBaubles(player).stackList) {
            if (stack != null && stack.func_77973_b() instanceof IBauble)
                ((IBauble) stack.func_77973_b()).onWornTick(stack, player);
        }
    }
}
