package github.com.gengyoubo.tcmodern.api.scan;

import java.util.Objects;

/** Stable registry identity, independent of either version's numeric IDs or Java classes. */
public record ScanKey(Kind kind, String id) {
    public enum Kind { ITEM, BLOCK, ENTITY }
    public ScanKey {
        Objects.requireNonNull(kind);
        if (id == null || !id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))
            throw new IllegalArgumentException("Expected namespaced registry ID: " + id);
    }
    public static ScanKey item(String id) { return new ScanKey(Kind.ITEM, id); }
    public static ScanKey block(String id) { return new ScanKey(Kind.BLOCK, id); }
    public static ScanKey entity(String id) { return new ScanKey(Kind.ENTITY, id); }
    public String researchKey() { return "tcmodern/" + kind.name().toLowerCase(java.util.Locale.ROOT) + "/" + id; }
}
