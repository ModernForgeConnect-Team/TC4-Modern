package github.com.gengyoubo.tcmodern.tc4.content;

import java.util.*;

/** Bounded per-aspect storage. A decomposition is accepted in full or not at all. */
public final class PrimalFurnaceBuffer {
    public static final int CAPACITY_PER_ASPECT = 256;
    private final Map<String, Integer> contents = new TreeMap<>();
    public Map<String, Integer> snapshot() { return Map.copyOf(contents); }
    public boolean canAdd(Map<String, Integer> yield) {
        if (yield.isEmpty()) return false;
        for (var entry : yield.entrySet()) {
            int n=entry.getValue();
            if (n<=0 || n>CAPACITY_PER_ASPECT || contents.getOrDefault(entry.getKey(),0)>CAPACITY_PER_ASPECT-n) return false;
        }
        return true;
    }
    public boolean add(Map<String, Integer> yield) {
        if (!canAdd(yield)) return false;
        yield.forEach((tag,n)->contents.merge(tag,n,Integer::sum));
        return true;
    }
    public boolean takeOne(String tag) {
        int n=contents.getOrDefault(tag,0);
        if (n<=0) return false;
        if (n==1) contents.remove(tag); else contents.put(tag,n-1);
        return true;
    }
    public void clear() { contents.clear(); }
}
