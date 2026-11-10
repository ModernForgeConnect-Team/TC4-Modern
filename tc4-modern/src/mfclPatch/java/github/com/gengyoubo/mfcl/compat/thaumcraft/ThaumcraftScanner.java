package github.com.gengyoubo.mfcl.compat.thaumcraft;

import github.com.gengyoubo.mfcl.bridge.*;
import java.lang.reflect.Field;
import java.util.Objects;
import static github.com.gengyoubo.mfcl.bridge.LegacyObjects.declared;

/**
 * TC4's thaumometer. Its use tick sends {@code PacketScannedToServer} when the original scan completes; the server
 * re-runs the original {@code doScan} with its own player and world and only then calls {@code ScanManager.completeScan}.
 */
final class ThaumcraftScanner implements LegacyHeldScanners.Scanner {
    private static final String ITEM = "Thaumcraft:ItemThaumometer";
    private static final String REQUEST = "thaumcraft.common.lib.network.playerdata.PacketScannedToServer";
    private final ClassLoader loader;
    ThaumcraftScanner(ClassLoader loader) { this.loader = loader; }

    @Override public String id() { return "thaumcraft4:thaumometer"; }
    @Override public boolean supports(String item) { return ITEM.equals(item); }
    @Override public boolean completion(Object packet) { return packet.getClass().getName().equals(REQUEST); }

    @Override public LegacyItemUse.Scan read(Object packet) throws Exception {
        return new LegacyItemUse.Scan((byte) declared(packet, "type"), (int) declared(packet, "id"), (int) declared(packet, "md"),
                (int) declared(packet, "entityid"), (String) declared(packet, "phenomena"));
    }

    /** The package-private {@code startScan} the thaumometer records while a scan is in progress. */
    @Override public String prompt(Object item) throws Exception {
        Object scan = null;
        for (Class<?> type = item.getClass(); type != null && scan == null; type = type.getSuperclass()) {
            try { Field field = type.getDeclaredField("startScan"); field.setAccessible(true); scan = field.get(item); }
            catch (NoSuchFieldException absent) { }
        }
        if (scan == null) return null;
        byte type = scan.getClass().getField("type").getByte(scan);
        if (type == 3) {
            String phenomenon=(String)scan.getClass().getField("phenomena").get(scan);
            if(phenomenon!=null&&phenomenon.startsWith("NODE"))
                return (String)loader.loadClass("net.minecraft.util.StatCollector").getMethod("func_74838_a",String.class).invoke(null,"tile.blockAiry.0.name");
            return null;
        }
        return LegacyItemUse.scanTargetName(loader, type == 2 ? scan.getClass().getField("entity").get(scan) : null,
                scan.getClass().getField("id").getInt(scan), scan.getClass().getField("meta").getInt(scan));
    }

    @Override public boolean complete(Object world, Object player, Object heldStack, LegacyItemUse.Scan reported, String prefix) throws Exception {
        Class<?> stackType = loader.loadClass("net.minecraft.item.ItemStack"), playerType = loader.loadClass("net.minecraft.entity.player.EntityPlayer");
        Object item = stackType.getMethod("func_77973_b").invoke(heldStack);
        var doScan = item.getClass().getDeclaredMethod("doScan", stackType, loader.loadClass("net.minecraft.world.World"), playerType, int.class);
        doScan.setAccessible(true);
        Object scan = doScan.invoke(item, heldStack, world, player, 0);
        if (scan == null) return false;
        var type = scan.getClass();
        Object entity = type.getField("entity").get(scan);
        boolean same = type.getField("type").getByte(scan) == reported.type() && type.getField("id").getInt(scan) == reported.id()
                && type.getField("meta").getInt(scan) == reported.meta() && Objects.equals(type.getField("phenomena").get(scan), reported.phenomena())
                && (reported.type() != 2 || entity != null && LegacyObjects.entityId(entity) == reported.entityId());
        if (!same) return false;
        // The client-supplied prefix is not trusted; the thaumometer itself always completes with "@".
        return (boolean) loader.loadClass("thaumcraft.common.lib.research.ScanManager")
                .getMethod("completeScan", playerType, loader.loadClass("thaumcraft.api.research.ScanResult"), String.class)
                .invoke(null, player, scan, "@");
    }

    /** Original research, aspect and scanned-state packets for one player. */
    static void synchronize(ClassLoader loader, Object player) throws Exception {
        Object wrapper = ThaumcraftReflection.packetHandler(loader);
        var send = wrapper.getClass().getMethod("sendTo", loader.loadClass("cpw.mods.fml.common.network.simpleimpl.IMessage"), loader.loadClass("net.minecraft.entity.player.EntityPlayerMP"));
        var playerType = loader.loadClass("net.minecraft.entity.player.EntityPlayer");
        for (String name : new String[]{"PacketSyncResearch", "PacketSyncAspects", "PacketSyncScannedItems", "PacketSyncScannedEntities", "PacketSyncScannedPhenomena"})
            send.invoke(wrapper, loader.loadClass("thaumcraft.common.lib.network.playerdata." + name).getConstructor(playerType).newInstance(player), player);
        var warp=loader.loadClass("thaumcraft.common.lib.network.playerdata.PacketSyncWarp").getConstructor(playerType,byte.class);
        for(byte kind=0;kind<3;kind++)send.invoke(wrapper,warp.newInstance(player,kind),player);
    }
}
