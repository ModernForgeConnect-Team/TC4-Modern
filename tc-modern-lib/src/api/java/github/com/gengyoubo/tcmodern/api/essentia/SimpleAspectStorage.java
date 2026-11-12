package github.com.gengyoubo.tcmodern.api.essentia;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** In-memory implementation with a shared capacity and independent receive/extract limits. */
public class SimpleAspectStorage implements AspectStorage {
    private final int capacity, maxReceive, maxExtract;
    private final Map<String, Integer> amounts = new LinkedHashMap<>();
    private int total;

    public SimpleAspectStorage(int capacity, int maxReceive, int maxExtract) {
        if (capacity < 0 || maxReceive < 0 || maxExtract < 0) throw new IllegalArgumentException("Negative storage limit");
        this.capacity = capacity; this.maxReceive = maxReceive; this.maxExtract = maxExtract;
    }
    public SimpleAspectStorage(int capacity) { this(capacity, capacity, capacity); }
    private static void check(String aspect, int maximum) {
        Objects.requireNonNull(aspect, "aspect");
        if (!aspect.matches("[a-z][a-z0-9_]*") || maximum < 0) throw new IllegalArgumentException("Invalid aspect or amount");
    }
    @Override public int receive(String aspect, int maximum, boolean simulate) {
        check(aspect, maximum);
        int accepted = Math.min(Math.min(maximum, maxReceive), capacity - total);
        if (!simulate && accepted > 0) { amounts.merge(aspect, accepted, Integer::sum); total += accepted; onChanged(); }
        return accepted;
    }
    @Override public int extract(String aspect, int maximum, boolean simulate) {
        check(aspect, maximum);
        int taken = Math.min(Math.min(maximum, maxExtract), amounts.getOrDefault(aspect, 0));
        if (!simulate && taken > 0) {
            int remaining = amounts.get(aspect) - taken;
            if (remaining == 0) amounts.remove(aspect); else amounts.put(aspect, remaining);
            total -= taken; onChanged();
        }
        return taken;
    }
    /** Restores a complete snapshot without transfer limits; rejects invalid or over-capacity data atomically. */
    public void load(Map<String, Integer> snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        var copy = new LinkedHashMap<String, Integer>();
        long sum = 0;
        for (var entry : snapshot.entrySet()) {
            if (entry.getValue() == null || entry.getValue() <= 0) throw new IllegalArgumentException("Invalid stored amount");
            check(entry.getKey(), entry.getValue());
            sum += entry.getValue();
            if (sum > capacity) throw new IllegalArgumentException("Storage exceeds capacity");
            copy.put(entry.getKey(), entry.getValue());
        }
        if (!amounts.equals(copy)) { amounts.clear(); amounts.putAll(copy); total = (int) sum; onChanged(); }
    }
    @Override public int stored(String aspect) { Objects.requireNonNull(aspect); return amounts.getOrDefault(aspect, 0); }
    @Override public int capacity() { return capacity; }
    @Override public Map<String, Integer> contents() { return Collections.unmodifiableMap(new LinkedHashMap<>(amounts)); }
    @Override public boolean canReceive() { return maxReceive > 0; }
    @Override public boolean canExtract() { return maxExtract > 0; }
    /** Override to mark a block entity dirty or schedule synchronization after real changes. */
    protected void onChanged() { }
}
