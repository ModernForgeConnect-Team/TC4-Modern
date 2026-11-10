package github.com.gengyoubo.mfcl.compat.thaumcraft;

import github.com.gengyoubo.mfcl.bridge.*;
import static github.com.gengyoubo.mfcl.bridge.LegacyObjects.declared;
import static github.com.gengyoubo.mfcl.bridge.LegacyObjects.declaredField;

/** Validation for TC4's original wand-control, research-table and Thaumonomicon packets. */
final class ThaumcraftDevicePackets {
    private static final String MISC="thaumcraft.common.lib.network.misc.",PLAYER_DATA="thaumcraft.common.lib.network.playerdata.";
    private ThaumcraftDevicePackets() {}
    static boolean prepare(Object packet,Object player,LegacyDeviceContainers.Descriptor device) throws Exception {
        switch(packet.getClass().getName()) {
            case MISC+"PacketFocusChangeToServer"->validateWandControl(packet,player,true);
            case MISC+"PacketItemKeyToServer"->validateWandControl(packet,player,false);
            case PLAYER_DATA+"PacketAspectCombinationToServer"->validateCombination(packet,player,target(packet,player,device));
            case PLAYER_DATA+"PacketAspectPlaceToServer"->validatePlacement(packet,player,target(packet,player,device));
            case PLAYER_DATA+"PacketPlayerCompleteToServer"->validateBookCompletion(packet,player);
            default->{return false;}
        }
        return true;
    }
    private static void validateWandControl(Object packet,Object player,boolean focusChange) throws Exception {
        var loader=player.getClass().getClassLoader();
        Object held=LegacyObjects.heldStack(player),item=LegacyObjects.item(held);
        if(item==null||!loader.loadClass("thaumcraft.common.items.wands.ItemWandCasting").isInstance(item))throw new IllegalArgumentException("Wand control requires a held wand");
        LegacyPacketTargets.bindPlayer(packet,player,"dim","playerid","Wand control");
        if(focusChange) {
            if(!ThaumcraftWandFocus.supportsFocus(loader,held))throw new IllegalArgumentException("Sceptres cannot equip foci");
            String focus=(String)declared(packet,"focus");
            if(focus==null||focus.length()>256||!focus.equals("REMOVE")&&!ThaumcraftWandFocus.available(loader,player).containsKey(focus))throw new IllegalArgumentException("Focus is not in the player's inventory");
        }else if(declaredField(packet,"key").getByte(packet)!=1)throw new IllegalArgumentException("Unsupported wand key");
    }
    /** Bonus availability is authoritative; client booleans cannot grant free points. */
    private static void validateCombination(Object packet,Object player,Object tile) throws Exception {
        Object bonus=tile.getClass().getField("bonusAspects").get(tile);
        var amount=bonus.getClass().getMethod("getAmount",player.getClass().getClassLoader().loadClass("thaumcraft.api.aspects.Aspect"));
        for(int index=1;index<=2;index++) {
            Object aspect=declared(packet,"aspect"+index);
            if(aspect==null)throw new IllegalArgumentException("Unknown research aspect");
            declaredField(packet,"ab"+index).setBoolean(packet,(int)amount.invoke(bonus,aspect)>0);
        }
    }
    /** The Thaumonomicon's purchase/create-note request: bound to the authenticated player and a real research. */
    private static void validateBookCompletion(Object packet,Object player) throws Exception {
        int type=declaredField(packet,"type").getByte(packet);
        if(type!=0&&type!=1)throw new IllegalArgumentException("Unsupported research completion kind");
        LegacyPacketTargets.requirePlayerName(packet,player,"username","Research completion");
        LegacyPacketTargets.requireDimension(packet,player,"dim","Research completion");
        String key=(String)declared(packet,"key");
        var loader=player.getClass().getClassLoader();
        Object research=key==null?null:loader.loadClass("thaumcraft.api.research.ResearchCategories").getMethod("getResearch",String.class).invoke(null,key);
        if(research==null)throw new IllegalArgumentException("Research completion targets unknown research");
        if(type==0)requireResearchAspects(loader,player,research);
    }
    /**
     * TC4's server handler charges the research's aspects with addAspectPool, which ignores a shortfall, and only the
     * client GUI checks the pool. Without this check a forged packet unlocks any research whose prerequisites are met for free.
     */
    private static void requireResearchAspects(ClassLoader loader,Object player,Object research) throws Exception {
        Object tags=research.getClass().getField("tags").get(research);
        if(tags==null)return;
        Object proxy=ThaumcraftReflection.proxy(loader);
        Object knowledge=proxy.getClass().getMethod("getPlayerKnowledge").invoke(proxy);
        String name=LegacyObjects.playerName(player);
        Class<?> aspectType=loader.loadClass("thaumcraft.api.aspects.Aspect");
        var getAmount=tags.getClass().getMethod("getAmount",aspectType);
        var getPool=knowledge.getClass().getMethod("getAspectPoolFor",String.class,aspectType);
        for(Object aspect:(Object[])tags.getClass().getMethod("getAspects").invoke(tags)) {
            int cost=(int)getAmount.invoke(tags,aspect);
            if(cost>0&&((Number)getPool.invoke(knowledge,name,aspect)).intValue()<cost)
                throw new IllegalArgumentException("Research completion lacks the required aspects");
        }
    }
    private static Object target(Object packet,Object player,LegacyDeviceContainers.Descriptor device) throws Exception {
        LegacyPacketTargets.requireDevice(packet,device,"Thaumcraft",10,"x","y","z","Research action");
        LegacyPacketTargets.bindPlayer(packet,player,"dim","playerid","Research action");
        Object world=LegacyObjects.world(player);
        Object tile=world.getClass().getClassLoader().loadClass("net.minecraft.world.World").getMethod("func_147438_o",int.class,int.class,int.class).invoke(world,device.x(),device.y(),device.z());
        if(tile==null||!tile.getClass().getName().equals("thaumcraft.common.tiles.TileResearchTable"))throw new IllegalArgumentException("Research table no longer exists");
        return tile;
    }
    private static void validatePlacement(Object packet,Object player,Object tile) throws Exception {
        var loader=player.getClass().getClassLoader();
        Object note=((Object[])tile.getClass().getField("contents").get(tile))[1];
        Object item=LegacyObjects.item(note);
        if(item==null||!loader.loadClass("thaumcraft.common.items.ItemResearchNotes").isInstance(item))throw new IllegalArgumentException("Research table has no editable note");
        var stackType=loader.loadClass("net.minecraft.item.ItemStack");
        if((int)stackType.getMethod("func_77960_j").invoke(note)>=64)throw new IllegalArgumentException("Research note is already complete");
        Object data=loader.loadClass("thaumcraft.common.lib.research.ResearchManager").getMethod("getData",stackType).invoke(null,note);
        if(data==null)throw new IllegalArgumentException("Research note has no original data");
        int q=declaredField(packet,"q").getByte(packet),r=declaredField(packet,"r").getByte(packet);
        Object hex=loader.loadClass("thaumcraft.common.lib.utils.HexUtils$Hex").getConstructor(int.class,int.class).newInstance(q,r);
        var entries=(java.util.Map<?,?>)data.getClass().getField("hexEntries").get(data);
        if(!entries.containsKey(hex.toString()))throw new IllegalArgumentException("Research note has no such hex");
    }
}
