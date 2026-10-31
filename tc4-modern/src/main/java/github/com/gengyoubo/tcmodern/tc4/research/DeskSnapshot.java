package github.com.gengyoubo.tcmodern.tc4.research;

import java.util.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

/**
 * Server-authoritative view of one desk for its viewer: their aspect points, the note on the desk and the notes
 * filed in linked cabinets. Plain values only, so it can be compared and sent.
 */
public record DeskSnapshot(Map<String, Integer> pools, Sheet sheet, boolean researcher, boolean expert, boolean duplicate,
                           int cabinets, int capacity, List<Filed> archive) {
    /** {@code type}: 0 blank, 1 fixed, 2 written. */
    public record Cell(int q, int r, int type, String aspect) {
        public String key() { return q + ":" + r; }
    }
    public record Sheet(String key, boolean complete, int copies, List<Cell> cells, Map<String, Integer> copyCost) {}
    /** A note in the {@code cabinet}-th linked cabinet, at {@code slot}. */
    public record Filed(int cabinet, int slot, ItemStack stack) {}

    public static final DeskSnapshot EMPTY = new DeskSnapshot(Map.of(), null, false, false, false, 0, 0, List.of());

    public int pool(String tag) { return tag == null ? 0 : pools.getOrDefault(tag, 0); }

    public void write(FriendlyByteBuf out) {
        out.writeVarInt(pools.size());
        pools.forEach((tag, amount) -> { out.writeUtf(tag, 64); out.writeVarInt(amount); });
        out.writeBoolean(sheet != null);
        if (sheet != null) {
            out.writeUtf(sheet.key(), 256); out.writeBoolean(sheet.complete()); out.writeVarInt(sheet.copies());
            out.writeVarInt(sheet.cells().size());
            for (var cell : sheet.cells()) {
                out.writeVarInt(cell.q()); out.writeVarInt(cell.r()); out.writeByte(cell.type());
                out.writeUtf(cell.aspect() == null ? "" : cell.aspect(), 64);
            }
            out.writeVarInt(sheet.copyCost().size());
            sheet.copyCost().forEach((tag, amount) -> { out.writeUtf(tag, 64); out.writeVarInt(amount); });
        }
        out.writeBoolean(researcher); out.writeBoolean(expert); out.writeBoolean(duplicate);
        out.writeVarInt(cabinets); out.writeVarInt(capacity);
        out.writeVarInt(archive.size());
        for (var filed : archive) { out.writeVarInt(filed.cabinet()); out.writeVarInt(filed.slot()); out.writeItem(filed.stack()); }
    }

    public static DeskSnapshot read(FriendlyByteBuf in) {
        var pools = new LinkedHashMap<String, Integer>();
        for (int i = bounded(in.readVarInt(), 4096); i > 0; i--) pools.put(in.readUtf(64), in.readVarInt());
        Sheet sheet = null;
        if (in.readBoolean()) {
            String key = in.readUtf(256); boolean complete = in.readBoolean(); int copies = in.readVarInt();
            var cells = new ArrayList<Cell>();
            for (int i = bounded(in.readVarInt(), 1024); i > 0; i--) {
                int q = in.readVarInt(), r = in.readVarInt(), type = in.readByte(); String aspect = in.readUtf(64);
                cells.add(new Cell(q, r, type, aspect.isEmpty() ? null : aspect));
            }
            var cost = new LinkedHashMap<String, Integer>();
            for (int i = bounded(in.readVarInt(), 256); i > 0; i--) cost.put(in.readUtf(64), in.readVarInt());
            sheet = new Sheet(key, complete, copies, List.copyOf(cells), Collections.unmodifiableMap(cost));
        }
        boolean researcher = in.readBoolean(), expert = in.readBoolean(), duplicate = in.readBoolean();
        int cabinets = in.readVarInt(), capacity = in.readVarInt();
        var archive = new ArrayList<Filed>();
        for (int i = bounded(in.readVarInt(), 4096); i > 0; i--) archive.add(new Filed(in.readVarInt(), in.readVarInt(), in.readItem()));
        return new DeskSnapshot(Collections.unmodifiableMap(pools), sheet, researcher, expert, duplicate, cabinets, capacity, List.copyOf(archive));
    }

    private static int bounded(int value, int max) {
        if (value < 0 || value > max) throw new IllegalArgumentException("Invalid research desk payload size");
        return value;
    }
}
