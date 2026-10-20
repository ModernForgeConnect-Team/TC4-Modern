package github.com.gengyoubo.tcmodern.tc4.compat.jade;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * The essentia an original TC4 tile holds, read from the legacy NBT that MFCL's host block entity saves and
 * synchronises. Only the original public save format is relied on: {@code AspectList} ("Aspects": key/amount,
 * crucible, reservoir, buffer, furnace, node), jars ("Aspect"/"Amount"), alembics ("aspect"/"amount") and tubes
 * ("type"/"amount"). An empty aspect map means an empty container.
 */
record LegacyEssentia(Map<String, Integer> aspects, String filter) {
    /** The host block entity type MFCL registers for every original tile. */
    private static final String HOST_NAMESPACE = "mfcl";
    private static final String HOST_NBT = "mfcl_legacy_nbt";
    static final String SERVER_KEY = "tc4modern_essentia";

    LegacyEssentia {
        // In the original order; Map.copyOf would shuffle it.
        aspects = Collections.unmodifiableMap(new LinkedHashMap<>(aspects));
    }

    int total() { return aspects.values().stream().mapToInt(Integer::intValue).sum(); }

    static Optional<LegacyEssentia> read(BlockEntity blockEntity) {
        if (blockEntity == null) return Optional.empty();
        var type = ForgeRegistries.BLOCK_ENTITY_TYPES.getKey(blockEntity.getType());
        if (type == null || !type.getNamespace().equals(HOST_NAMESPACE)) return Optional.empty();
        try {
            var host = blockEntity.saveWithoutMetadata();
            if (!host.contains(HOST_NBT, Tag.TAG_BYTE_ARRAY)) return Optional.empty();
            // CompressedStreamTools.compress: gzip of the same binary NBT format the host still reads.
            return parse(NbtIo.readCompressed(new ByteArrayInputStream(host.getByteArray(HOST_NBT))));
        } catch (IOException | RuntimeException unreadable) {
            return Optional.empty();
        }
    }

    static Optional<LegacyEssentia> parse(CompoundTag legacy) {
        var aspects = new LinkedHashMap<String, Integer>();
        if (legacy.contains("Aspects", Tag.TAG_LIST)) {
            for (var entry : legacy.getList("Aspects", Tag.TAG_COMPOUND)) {
                var aspect = (CompoundTag) entry;
                add(aspects, aspect.getString("key"), aspect.getInt("amount"));
            }
            return Optional.of(new LegacyEssentia(aspects, ""));
        }
        if (legacy.contains("Amount", Tag.TAG_ANY_NUMERIC)) {
            add(aspects, legacy.getString("Aspect"), legacy.getInt("Amount"));
            return Optional.of(new LegacyEssentia(aspects, legacy.getString("AspectFilter")));
        }
        if (legacy.contains("aspect", Tag.TAG_STRING) && legacy.contains("amount", Tag.TAG_ANY_NUMERIC)) {
            add(aspects, legacy.getString("aspect"), legacy.getInt("amount"));
            return Optional.of(new LegacyEssentia(aspects, legacy.getString("AspectFilter")));
        }
        // "stype" (suction type) only exists on tubes, so "type" is not mistaken for anything else.
        if (legacy.contains("stype", Tag.TAG_STRING) && legacy.contains("amount", Tag.TAG_ANY_NUMERIC)) {
            add(aspects, legacy.getString("type"), legacy.getInt("amount"));
            return Optional.of(new LegacyEssentia(aspects, ""));
        }
        return Optional.empty();
    }

    private static void add(Map<String, Integer> aspects, String tag, int amount) {
        if (tag != null && !tag.isBlank() && amount > 0) aspects.merge(tag, amount, Integer::sum);
    }

    /** Ordered, unlike a compound keyed by aspect. */
    CompoundTag toTag() {
        var list = new ListTag();
        aspects.forEach((tag, amount) -> {
            var entry = new CompoundTag();
            entry.putString("key", tag);
            entry.putInt("amount", amount);
            list.add(entry);
        });
        var result = new CompoundTag();
        result.put("Aspects", list);
        if (!filter.isEmpty()) result.putString("AspectFilter", filter);
        return result;
    }

    static Optional<LegacyEssentia> fromTag(CompoundTag tag) {
        return parse(tag).map(read -> new LegacyEssentia(read.aspects(), tag.getString("AspectFilter")));
    }
}
