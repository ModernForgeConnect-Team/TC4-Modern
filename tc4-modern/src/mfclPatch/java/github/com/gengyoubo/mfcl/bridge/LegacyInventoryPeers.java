package github.com.gengyoubo.mfcl.bridge;

import org.objectweb.asm.*;
import java.util.*;

/**
 * A typed old IInventory TileEntity for a native item handler (chests, barrels, modded machines), so old machines can
 * output into host storage. Old code mutates returned stacks in place ({@code stack.stackSize += n}) and then calls
 * markDirty, so each lent stack is remembered and its difference is written back to the host on the next access.
 * Native vanilla chests get a variant that extends the old TileEntityChest, because original generators (TC4 great trees,
 * dungeon code) cast the looked-up tile to that class before filling it with loot.
 */
public final class LegacyInventoryPeers {
    private static final String NAME = "github/com/gengyoubo/mfcl/legacy1710/HostInventoryTile";
    private static final String CHEST_NAME = "github/com/gengyoubo/mfcl/legacy1710/HostChestTile";
    private static final String TILE = "net/minecraft/tileentity/TileEntity", CHEST = "net/minecraft/tileentity/TileEntityChest";
    private static final String STACK = "Lnet/minecraft/item/ItemStack;";
    private LegacyInventoryPeers() {}
    /** The state behind one peer tile; slots hold the old stack handed out and the host contents it was made from. */
    public static final class Peer {
        private final LegacyServices services;
        private final HostInventoryAccess endpoint;
        private final Map<Integer,Object[]> lent = new HashMap<>();
        Peer(LegacyServices services, HostInventoryAccess endpoint) { this.services = services; this.endpoint = endpoint; }
    }
    public static Object create(LegacyServices services, Object world, int x, int y, int z, HostInventoryAccess endpoint) throws Exception {
        return create(services, world, x, y, z, endpoint, false);
    }
    /** {@code chest} selects the TileEntityChest-typed view; its endpoint must expose exactly that one chest's slots. */
    public static Object create(LegacyServices services, Object world, int x, int y, int z, HostInventoryAccess endpoint, boolean chest) throws Exception {
        String name = chest ? CHEST_NAME : NAME;
        return services.items().inLegacy(() -> {
            var loader = services.loader(); Class<?> type;
            synchronized (loader) {
                try { type = loader.loadClass(name.replace('/', '.')); }
                catch (ClassNotFoundException missing) { type = define(loader, name, chest); }
            }
            Object tile = type.getConstructor(Peer.class).newInstance(new Peer(services, endpoint));
            var base = loader.loadClass(TILE.replace('/', '.'));
            base.getMethod("func_145834_a", loader.loadClass("net.minecraft.world.World")).invoke(tile, world);
            base.getField("field_145851_c").setInt(tile, x); base.getField("field_145848_d").setInt(tile, y); base.getField("field_145849_e").setInt(tile, z);
            return tile;
        });
    }
    private static Class<?> define(ClassLoader loader, String name, boolean chest) throws Exception {
        String peer = Type.getInternalName(Peer.class), bridge = Type.getInternalName(LegacyInventoryPeers.class);
        String parent = chest ? CHEST : TILE;
        var writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, name, null, parent, new String[]{"net/minecraft/inventory/IInventory"});
        writer.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL, "peer", "L" + peer + ";", null, null).visitEnd();
        var init = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "(L" + peer + ";)V", null, null); init.visitCode();
        init.visitVarInsn(Opcodes.ALOAD, 0); init.visitMethodInsn(Opcodes.INVOKESPECIAL, parent, "<init>", "()V", false);
        init.visitVarInsn(Opcodes.ALOAD, 0); init.visitVarInsn(Opcodes.ALOAD, 1); init.visitFieldInsn(Opcodes.PUTFIELD, name, "peer", "L" + peer + ";");
        init.visitInsn(Opcodes.RETURN); init.visitMaxs(0, 0); init.visitEnd();
        delegate(writer, name, peer, bridge, "func_70302_i_", "()I", "size");
        delegate(writer, name, peer, bridge, "func_70301_a", "(I)" + STACK, "get");
        delegate(writer, name, peer, bridge, "func_70298_a", "(II)" + STACK, "decrease");
        delegate(writer, name, peer, bridge, "func_70304_b", "(I)" + STACK, "closing");
        delegate(writer, name, peer, bridge, "func_70299_a", "(I" + STACK + ")V", "set");
        delegate(writer, name, peer, bridge, "func_145825_b", "()Ljava/lang/String;", chest ? "chestName" : "name");
        delegate(writer, name, peer, bridge, "func_145818_k_", "()Z", "customName");
        delegate(writer, name, peer, bridge, "func_70297_j_", "()I", "limit");
        delegate(writer, name, peer, bridge, "func_70296_d", "()V", "flush");
        delegate(writer, name, peer, bridge, "func_70300_a", "(Lnet/minecraft/entity/player/EntityPlayer;)Z", "usable");
        delegate(writer, name, peer, bridge, "func_70295_k_", "()V", "open");
        delegate(writer, name, peer, bridge, "func_70305_f", "()V", "flush");
        delegate(writer, name, peer, bridge, "func_94041_b", "(I" + STACK + ")Z", "valid");
        if (chest) {
            // The native chest owns lid animation, saving and contents; the inherited private item array stays unused.
            var update = writer.visitMethod(Opcodes.ACC_PUBLIC, "func_145845_h", "()V", null, null); update.visitCode();
            update.visitInsn(Opcodes.RETURN); update.visitMaxs(0, 1); update.visitEnd();
            var load = writer.visitMethod(Opcodes.ACC_PUBLIC, "func_145839_a", "(Lnet/minecraft/nbt/NBTTagCompound;)V", null, null); load.visitCode();
            load.visitInsn(Opcodes.RETURN); load.visitMaxs(0, 2); load.visitEnd();
        }
        LegacyHostTileData.writer(writer);
        var tick = writer.visitMethod(Opcodes.ACC_PUBLIC, "canUpdate", "()Z", null, null); tick.visitCode();
        tick.visitInsn(Opcodes.ICONST_0); tick.visitInsn(Opcodes.IRETURN); tick.visitMaxs(1, 1); tick.visitEnd();
        writer.visitEnd(); byte[] bytes = writer.toByteArray();
        return (Class<?>) loader.getClass().getMethod("defineLegacyClass", String.class, byte[].class, int.class, int.class).invoke(loader, name.replace('/', '.'), bytes, 0, bytes.length);
    }
    /** Old-typed method forwarding to the static of the same purpose here, with references passed as Object. */
    private static void delegate(ClassWriter writer, String owner, String peer, String bridge, String method, String descriptor, String target) {
        var code = writer.visitMethod(Opcodes.ACC_PUBLIC, method, descriptor, null, null); code.visitCode();
        code.visitVarInsn(Opcodes.ALOAD, 0); code.visitFieldInsn(Opcodes.GETFIELD, owner, "peer", "L" + peer + ";");
        var arguments = new StringBuilder("(L" + peer + ";"); int slot = 1;
        for (Type argument : Type.getArgumentTypes(descriptor)) {
            code.visitVarInsn(argument.getOpcode(Opcodes.ILOAD), slot); slot += argument.getSize();
            arguments.append(argument.getSort() == Type.OBJECT ? "Ljava/lang/Object;" : argument.getDescriptor());
        }
        Type result = Type.getReturnType(descriptor);
        boolean reference = result.getSort() == Type.OBJECT && !result.getInternalName().equals("java/lang/String");
        code.visitMethodInsn(Opcodes.INVOKESTATIC, bridge, target, arguments + ")" + (reference ? "Ljava/lang/Object;" : result.getDescriptor()), false);
        if (reference) code.visitTypeInsn(Opcodes.CHECKCAST, result.getInternalName());
        code.visitInsn(result.getOpcode(Opcodes.IRETURN)); code.visitMaxs(0, 0); code.visitEnd();
    }
    private static boolean same(LegacyClientData.Stack left, LegacyClientData.Stack right) {
        return left.key().equals(right.key()) && left.metadata() == right.metadata() && left.tag().equals(right.tag());
    }
    private static LegacyClientData.Stack read(Peer peer, Object stack) throws Exception {
        if (stack == null || stack.getClass().getField("field_77994_a").getInt(stack) <= 0) return null;
        return peer.services.items().snapshot(stack);
    }
    private static LegacyClientData.Stack sized(LegacyClientData.Stack stack, int count) {
        return new LegacyClientData.Stack(stack.key(), stack.metadata(), count, stack.name(), stack.tag());
    }
    private static void put(Peer peer, int slot, LegacyClientData.Stack stack) {
        int moved = peer.endpoint.insert(slot, stack, false);
        if (moved < stack.count()) LegacyDiagnostics.missingMapping("host inventory insert", "host storage refused " + (stack.count() - moved) + " of " + stack.key() + " already committed by original code");
    }
    /**
     * Writes in-place changes of every lent stack back to the host. Lent stacks stay tracked against their new baseline:
     * callers check slot validity between reading a stack and growing it.
     */
    public static void flush(Peer peer) throws Exception {
        if (peer.lent.isEmpty()) return;
        if (!peer.endpoint.live()) { peer.lent.clear(); return; }
        for (var iterator = peer.lent.entrySet().iterator(); iterator.hasNext();) {
            var entry = iterator.next();
            int slot = entry.getKey(); var before = (LegacyClientData.Stack) entry.getValue()[1]; var after = read(peer, entry.getValue()[0]);
            if (after != null && same(before, after)) {
                int delta = after.count() - before.count();
                if (delta > 0) put(peer, slot, sized(after, delta));
                else if (delta < 0) peer.endpoint.extract(slot, -delta, false);
            } else {
                peer.endpoint.extract(slot, before.count(), false);
                if (after != null) put(peer, slot, after);
            }
            if (after == null) iterator.remove(); else entry.getValue()[1] = after;
        }
    }
    public static int size(Peer peer) throws Exception { flush(peer); return peer.endpoint.live() ? peer.endpoint.size() : 0; }
    public static Object get(Peer peer, int slot) throws Exception {
        flush(peer);
        if (!peer.endpoint.live() || slot < 0 || slot >= peer.endpoint.size()) return null;
        var contents = peer.endpoint.get(slot); if (contents == null) return null;
        Object stack = peer.services.items().materialize(contents);
        peer.lent.put(slot, new Object[]{stack, contents});
        return stack;
    }
    public static Object decrease(Peer peer, int slot, int amount) throws Exception {
        flush(peer);
        if (amount <= 0 || !peer.endpoint.live()) return null;
        var contents = peer.endpoint.get(slot); if (contents == null) return null;
        int moved = peer.endpoint.extract(slot, amount, false);
        return moved <= 0 ? null : peer.services.items().materialize(sized(contents, moved));
    }
    public static Object closing(Peer peer, int slot) { return null; }
    public static void set(Peer peer, int slot, Object stack) throws Exception {
        flush(peer);
        if (!peer.endpoint.live()) return;
        var current = peer.endpoint.get(slot); var target = read(peer, stack);
        if (current != null && target != null && same(current, target)) {
            int delta = target.count() - current.count();
            if (delta > 0) put(peer, slot, sized(target, delta)); else if (delta < 0) peer.endpoint.extract(slot, -delta, false);
            return;
        }
        if (current != null) peer.endpoint.extract(slot, current.count(), false);
        if (target != null) put(peer, slot, target);
    }
    public static String name(Peer peer) { return "container.mfcl.host"; }
    public static String chestName(Peer peer) { return "container.chest"; }
    public static boolean customName(Peer peer) { return false; }
    public static int limit(Peer peer) { return 64; }
    public static boolean usable(Peer peer, Object player) { return peer.endpoint.live(); }
    public static void open(Peer peer) {}
    /**
     * An empty slot is valid only for a stack the host takes completely, so a following set never loses items; an occupied
     * slot is valid when it takes at least one more, which in-place top-ups are bounded by.
     */
    public static boolean valid(Peer peer, int slot, Object stack) throws Exception {
        flush(peer);
        var offered = read(peer, stack);
        if (offered == null || !peer.endpoint.live()) return false;
        if (peer.endpoint.get(slot) != null) return peer.endpoint.insert(slot, sized(offered, 1), true) == 1;
        return peer.endpoint.insert(slot, offered, true) == offered.count();
    }
}
