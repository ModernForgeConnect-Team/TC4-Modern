package github.com.gengyoubo.tcmodern.tc4.research;

import com.mojang.logging.LogUtils;
import github.com.gengyoubo.mfcl.api.LegacyMappings;
import github.com.gengyoubo.mfcl.api.LegacyObject;
import github.com.gengyoubo.mfcl.api.LegacyRuntime;
import github.com.gengyoubo.tcmodern.api.TcModernApi;
import github.com.gengyoubo.tcmodern.api.aspect.TcAspectAccessors;
import github.com.gengyoubo.tcmodern.api.recipe.TcRecipeAccessors;
import github.com.gengyoubo.tcmodern.tc4.content.ArchiveCabinetBlockEntity;
import github.com.gengyoubo.tcmodern.tc4.content.ResearchDeskBlockEntity;
import github.com.gengyoubo.tcmodern.tc4.menu.ResearchDeskMenu;
import github.com.gengyoubo.tcmodern.tc4.network.DeskNetwork;
import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import static github.com.gengyoubo.tcmodern.tc4.research.DeskAccessors.*;

/**
 * Server side of the research desk. Placement, erasing, combining and copying follow the original
 * {@code TileResearchTable} and its packets in TC4 4.2.3.5, including the researcher chances, but on a modern
 * block entity: notes and ink are read and written through MFCL, and every fifth written hex costs no ink.
 * The desk keeps no table bonus aspects, so only the player's own points are spent.
 */
public final class DeskLogic {
    private DeskLogic() {}

    public static DeskSnapshot snapshot(ServerPlayer player, ResearchDeskBlockEntity desk) {
        var archive = new ArrayList<DeskSnapshot.Filed>();
        var cabinets = desk.cabinets();
        for (int c = 0; c < cabinets.size(); c++) {
            var items = cabinets.get(c).items();
            for (int slot = 0; slot < items.getSlots(); slot++)
                if (!items.getStackInSlot(slot).isEmpty()) archive.add(new DeskSnapshot.Filed(c, slot, items.getStackInSlot(slot).copy()));
        }
        int capacity = cabinets.size() * ArchiveCabinetBlockEntity.SIZE;
        var runtime = TcModernApi.runtime().orElse(null);
        if (runtime == null) return new DeskSnapshot(Map.of(), null, false, false, false, cabinets.size(), capacity, List.copyOf(archive));
        try {
            return runtime.withPlayer(player, actor -> {
                String name = name(runtime, actor);
                var pools = new LinkedHashMap<String, Integer>();
                var discovered = runtime.accessor(Discovered.class, knowledgeObject(runtime)).of(name);
                if (discovered != null) {
                    var list = runtime.accessor(TcAspectAccessors.Aspects.class, discovered);
                    for (var aspect : list.values()) if (aspect != null) pools.put(tag(runtime, aspect), list.amount(aspect));
                }
                var notes = runtime.accessor(Notes.class);
                return new DeskSnapshot(Collections.unmodifiableMap(pools), sheet(runtime, desk.note()),
                        notes.known(name, "RESEARCHER1"), notes.known(name, "RESEARCHER2"), notes.known(name, "RESEARCHDUPE"),
                        cabinets.size(), capacity, List.copyOf(archive));
            });
        } catch (RuntimeException failure) {
            LogUtils.getLogger().debug("Research desk snapshot unavailable: {}", failure.toString());
            return new DeskSnapshot(Map.of(), null, false, false, false, cabinets.size(), capacity, List.copyOf(archive));
        }
    }

    public static void handle(ServerPlayer player, ResearchDeskMenu menu, DeskNetwork.Action action) {
        var desk = menu.desk();
        var runtime = TcModernApi.runtime().orElse(null);
        if (desk == null) return;
        try {
            switch (action.kind()) {
                case PLACE -> { if (runtime != null) write(runtime, player, desk, action.q(), action.r(), action.first()); }
                case ERASE -> { if (runtime != null) write(runtime, player, desk, action.q(), action.r(), null); }
                case COMBINE -> { if (runtime != null) combine(runtime, player, action.first(), action.second()); }
                case COPY -> { if (runtime != null) copy(runtime, player, desk); }
                case TAKE -> take(desk, action.q(), action.r());
                case FILE -> file(desk);
            }
        } catch (RuntimeException failure) {
            LogUtils.getLogger().warn("Research desk action {} failed: {}", action.kind(), failure.toString());
        }
        menu.invalidate();
    }

    private static DeskSnapshot.Sheet sheet(LegacyRuntime runtime, ItemStack host) {
        if (!ResearchItems.isNote(host)) return null;
        var stack = runtime.mappings().toLegacy(LegacyMappings.ITEM_STACK, host).orElse(null);
        if (stack == null) return null;
        var value = runtime.accessor(Notes.class).read(stack);
        if (value == null) return null;
        var note = runtime.accessor(Note.class, value);
        var cells = new ArrayList<DeskSnapshot.Cell>();
        for (var item : runtime.values().map(note.entries()).entrySet()) {
            String[] qr = ((String) item.getKey()).split(":");
            var entry = runtime.accessor(Entry.class, (LegacyObject) item.getValue());
            var aspect = entry.aspect();
            cells.add(new DeskSnapshot.Cell(Integer.parseInt(qr[0]), Integer.parseInt(qr[1]), entry.type(), aspect == null ? null : tag(runtime, aspect)));
        }
        cells.sort(Comparator.comparingInt(DeskSnapshot.Cell::q).thenComparingInt(DeskSnapshot.Cell::r));
        var cost = new LinkedHashMap<String, Integer>();
        var research = runtime.accessor(TcRecipeAccessors.ResearchLookup.class).find(note.key());
        if (research != null) {
            var tags = runtime.accessor(ResearchItem.class, research).tags();
            if (tags != null) {
                var list = runtime.accessor(TcAspectAccessors.Aspects.class, tags);
                for (var aspect : list.values()) if (aspect != null) cost.put(tag(runtime, aspect), list.amount(aspect) + note.copies());
            }
        }
        boolean complete = note.complete() || runtime.snapshot(stack).metadata() >= DISCOVERY;
        return new DeskSnapshot.Sheet(note.key(), complete, note.copies(), List.copyOf(cells), Collections.unmodifiableMap(cost));
    }

    /** {@code tag} null erases a hex the player wrote. */
    private static void write(LegacyRuntime runtime, ServerPlayer player, ResearchDeskBlockEntity desk, int q, int r, String tag) {
        var toolsHost = desk.tools();
        var noteHost = desk.note();
        if (!ResearchItems.isScribeTools(toolsHost) || !ResearchItems.isNote(noteHost)) return;
        var random = player.getRandom();
        Boolean finished = runtime.withPlayer(player, actor -> {
            var tools = runtime.mappings().toLegacy(LegacyMappings.ITEM_STACK, toolsHost).orElse(null);
            var note = runtime.mappings().toLegacy(LegacyMappings.ITEM_STACK, noteHost).orElse(null);
            if (tools == null || note == null) return null;
            var ink = runtime.accessor(StackView.class, tools);
            if (ink.damage() >= ink.capacity() || runtime.accessor(StackView.class, note).damage() >= DISCOVERY) return null;
            var notes = runtime.accessor(Notes.class);
            var data = notes.read(note);
            if (data == null) return null;
            var sheet = runtime.accessor(Note.class, data);
            if (sheet.complete()) return null;
            String key = q + ":" + r;
            var existing = (LegacyObject) runtime.values().map(sheet.entries()).get(key);
            if (existing == null) return null;
            var current = runtime.accessor(Entry.class, existing);
            String name = name(runtime, actor);
            var knowledge = runtime.accessor(TcAspectAccessors.Knowledge.class, knowledgeObject(runtime));
            boolean researcher = notes.known(name, "RESEARCHER1"), expert = notes.known(name, "RESEARCHER2");
            LegacyObject written;
            if (tag != null) {
                if (current.type() != 0) return null;
                var aspect = runtime.accessor(TcAspectAccessors.AspectLookup.class).find(tag);
                if (aspect == null || knowledge.pool(name, aspect) <= 0) return null;
                if (expert && random.nextFloat() < 0.1F) orb(player);
                else knowledge.add(name, aspect, (short) -1);
                written = runtime.accessor(EntryFactory.class).create(aspect, 2);
            } else {
                if (current.type() != 2) return null;
                float roll = random.nextFloat();
                if (current.aspect() != null && (researcher && roll < 0.25F || expert && roll < 0.5F)) {
                    knowledge.add(name, current.aspect(), (short) 1);
                    orb(player);
                }
                written = runtime.accessor(EntryFactory.class).create(null, 0);
            }
            runtime.values().put(sheet.entries(), key, written);
            runtime.values().put(sheet.hexes(), key, runtime.accessor(HexFactory.class).create(q, r));
            notes.write(note, data);
            if (!desk.inkFree()) ink.setDamage(ink.damage() + 1);
            boolean done = notes.finish(note, data, name);
            if (done) runtime.accessor(StackView.class, note).setDamage(DISCOVERY);
            desk.items().setStackInSlot(ResearchDeskBlockEntity.NOTE, host(runtime, noteHost, note));
            desk.items().setStackInSlot(ResearchDeskBlockEntity.TOOLS, runtime.mappings().applyTo(LegacyMappings.ITEM_STACK, toolsHost, tools));
            return done;
        });
        // The writer hears write/erase from their own screen, as in the original GUI; finishing is heard by all.
        if (Boolean.TRUE.equals(finished)) learn(player, desk);
    }

    /** Spends one point of each and gains one of their compound, as the original combination packet does. */
    private static void combine(LegacyRuntime runtime, ServerPlayer player, String first, String second) {
        if (first.isEmpty() || second.isEmpty()) return;
        runtime.withPlayer(player, actor -> {
            var lookup = runtime.accessor(TcAspectAccessors.AspectLookup.class);
            var a = lookup.find(first);
            var b = lookup.find(second);
            if (a == null || b == null) return false;
            var result = runtime.accessor(Notes.class).combine(a, b);
            if (result == null) return false;
            String name = name(runtime, actor);
            var knowledge = runtime.accessor(TcAspectAccessors.Knowledge.class, knowledgeObject(runtime));
            if (knowledge.pool(name, a) <= 0 || knowledge.pool(name, b) <= 0) return false;
            knowledge.add(name, a, (short) -1);
            knowledge.add(name, b, (short) -1);
            knowledge.add(name, result, (short) 1);
            return true;
        });
    }

    /** A finished note gains one copy for its research aspects plus one per earlier copy, a paper and an ink sac. */
    private static void copy(LegacyRuntime runtime, ServerPlayer player, ResearchDeskBlockEntity desk) {
        var noteHost = desk.note();
        if (!ResearchItems.isNote(noteHost)) return;
        var inventory = player.getInventory();
        int paper = inventory.findSlotMatchingItem(new ItemStack(Items.PAPER)), dye = inventory.findSlotMatchingItem(new ItemStack(Items.INK_SAC));
        if (paper < 0 || dye < 0) return;
        boolean copied = runtime.withPlayer(player, actor -> {
            var note = runtime.mappings().toLegacy(LegacyMappings.ITEM_STACK, noteHost).orElse(null);
            if (note == null || runtime.accessor(StackView.class, note).damage() != DISCOVERY) return false;
            String name = name(runtime, actor);
            var notes = runtime.accessor(Notes.class);
            if (!notes.known(name, "RESEARCHDUPE")) return false;
            var data = notes.read(note);
            if (data == null) return false;
            var sheet = runtime.accessor(Note.class, data);
            var research = runtime.accessor(TcRecipeAccessors.ResearchLookup.class).find(sheet.key());
            var tags = research == null ? null : runtime.accessor(ResearchItem.class, research).tags();
            if (tags == null) return false;
            var list = runtime.accessor(TcAspectAccessors.Aspects.class, tags);
            var knowledge = runtime.accessor(TcAspectAccessors.Knowledge.class, knowledgeObject(runtime));
            for (var aspect : list.values()) if (aspect != null && knowledge.pool(name, aspect) < list.amount(aspect) + sheet.copies()) return false;
            for (var aspect : list.values()) if (aspect != null) knowledge.add(name, aspect, (short) -(list.amount(aspect) + sheet.copies()));
            sheet.copies(sheet.copies() + 1);
            notes.write(note, data);
            var updated = host(runtime, noteHost, note);
            updated.setCount(noteHost.getCount() + 1);
            desk.items().setStackInSlot(ResearchDeskBlockEntity.NOTE, updated);
            return true;
        });
        if (!copied) return;
        inventory.removeItem(paper, 1);
        inventory.removeItem(dye, 1);
        learn(player, desk);
    }

    /** Swaps the desk's note with a filed one; the desk note takes the drawer slot it came from. */
    private static void take(ResearchDeskBlockEntity desk, int cabinet, int slot) {
        var cabinets = desk.cabinets();
        if (cabinet < 0 || cabinet >= cabinets.size()) return;
        var drawer = cabinets.get(cabinet).items();
        if (slot < 0 || slot >= drawer.getSlots()) return;
        var filed = drawer.getStackInSlot(slot);
        var current = desk.note();
        if (!ResearchItems.isNote(filed) || !current.isEmpty() && !ResearchItems.isNote(current)) return;
        drawer.setStackInSlot(slot, current.copy());
        desk.items().setStackInSlot(ResearchDeskBlockEntity.NOTE, filed.copy());
    }

    /** Files the desk's note in the first free drawer slot of the nearest cabinet. */
    private static void file(ResearchDeskBlockEntity desk) {
        var current = desk.note();
        if (!ResearchItems.isNote(current)) return;
        for (var cabinet : desk.cabinets()) {
            int free = cabinet.freeSlot();
            if (free < 0) continue;
            cabinet.items().setStackInSlot(free, current.copy());
            desk.items().setStackInSlot(ResearchDeskBlockEntity.NOTE, ItemStack.EMPTY);
            return;
        }
    }

    /**
     * The host form of a changed note. A note that became a discovery may be a different host item when MFCL splits
     * subtypes, so it is converted afresh; merging into the old stack is only the fallback.
     */
    private static ItemStack host(LegacyRuntime runtime, ItemStack previous, LegacyObject note) {
        return runtime.mappings().toHost(LegacyMappings.ITEM_STACK, note, ItemStack.class)
                .orElseGet(() -> runtime.mappings().applyTo(LegacyMappings.ITEM_STACK, previous, note));
    }

    /** The original table's block event 1: {@code thaumcraft:learn}, served by MFCL under its original id. */
    private static void learn(ServerPlayer player, ResearchDeskBlockEntity desk) {
        player.serverLevel().playSound(null, desk.getBlockPos(),
                SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("thaumcraft", "learn")), SoundSource.BLOCKS, 1.0F, 1.0F);
    }

    private static void orb(ServerPlayer player) {
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.2F,
                0.9F + player.getRandom().nextFloat() * 0.2F);
    }

    private static String name(LegacyRuntime runtime, LegacyObject player) {
        return runtime.accessor(TcAspectAccessors.Player.class, player).name();
    }

    private static String tag(LegacyRuntime runtime, LegacyObject aspect) {
        return runtime.accessor(TcAspectAccessors.Aspect.class, aspect).tag();
    }

    private static LegacyObject knowledgeObject(LegacyRuntime runtime) {
        return runtime.accessor(TcAspectAccessors.Proxy.class, runtime.accessor(TcAspectAccessors.Thaumcraft.class).proxy()).knowledge();
    }
}
