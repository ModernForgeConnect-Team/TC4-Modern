package github.com.gengyoubo.tcmodern.lib.wand;

import com.mojang.logging.LogUtils;
import github.com.gengyoubo.mfcl.api.LegacyRuntime;
import github.com.gengyoubo.tcmodern.api.TcModernApi;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.nbt.*;
import net.minecraftforge.event.level.ChunkDataEvent;

/** Opt-in recovery callbacks for custom wand definitions saved in MFCL-hosted TC4 block entities. */
public final class TcSavedWandRecovery {
    private static final CopyOnWriteArrayList<BiConsumer<LegacyRuntime, CompoundTag>> HANDLERS = new CopyOnWriteArrayList<>();
    private TcSavedWandRecovery() { }
    /** Register before chunks load; handler must recognize only its own wand tags and restore its own definitions. */
    public static AutoCloseable register(BiConsumer<LegacyRuntime, CompoundTag> handler) {
        HANDLERS.add(java.util.Objects.requireNonNull(handler));
        return () -> HANDLERS.remove(handler);
    }
    public static void chunkLoaded(ChunkDataEvent.Load event) {
        if (HANDLERS.isEmpty()) return;
        TcModernApi.runtime().ifPresent(runtime -> {
            var data = event.getData();
            read(runtime, data.getList("block_entities", Tag.TAG_COMPOUND));
            if (data.contains("Level", Tag.TAG_COMPOUND)) read(runtime, data.getCompound("Level").getList("TileEntities", Tag.TAG_COMPOUND));
        });
    }
    private static void read(LegacyRuntime runtime, ListTag tiles) {
        for (Tag value : tiles) if (value instanceof CompoundTag tile) restoreTile(runtime, tile);
    }
    /** Decompresses an MFCL TC4 tile snapshot and invokes handlers for each nested rod/cap compound. */
    public static void restoreTile(LegacyRuntime runtime, CompoundTag tile) {
        if (HANDLERS.isEmpty() || !tile.getString("mfcl_legacy_block").startsWith("Thaumcraft:")
                || !tile.contains("mfcl_legacy_nbt", Tag.TAG_BYTE_ARRAY)) return;
        byte[] bytes = tile.getByteArray("mfcl_legacy_nbt"); if (bytes.length == 0) return;
        try { scan(NbtIo.readCompressed(new ByteArrayInputStream(bytes)), wand -> {
            for (var handler : HANDLERS) try { handler.accept(runtime, wand.copy()); }
            catch (RuntimeException failure) { LogUtils.getLogger().warn("TC4 saved wand recovery handler failed", failure); }
        }); } catch (IOException | RuntimeException failure) { LogUtils.getLogger().warn("Cannot read saved TC4 wand data", failure); }
    }
    static void scan(Tag value, Consumer<CompoundTag> handler) {
        if (value instanceof CompoundTag compound) {
            if (compound.contains("rod", Tag.TAG_STRING) && compound.contains("cap", Tag.TAG_STRING)) handler.accept(compound);
            for (String key : compound.getAllKeys()) scan(compound.get(key), handler);
        } else if (value instanceof ListTag list) for (Tag element : list) scan(element, handler);
    }
}
