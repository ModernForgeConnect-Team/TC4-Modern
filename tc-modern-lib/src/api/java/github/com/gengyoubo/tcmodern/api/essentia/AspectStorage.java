package github.com.gengyoubo.tcmodern.api.essentia;

/** A modern, per-aspect storage contract. Amounts are whole units, not research points or centivis.
 * Implementations must not mutate state when simulate is true. Calls belong on the owning game thread.
 * The full Lib mod bridges sided block-entity capabilities to TC4 tubes when MFCL redirects are available.
 */
public interface AspectStorage {
    int receive(String aspect, int maximum, boolean simulate);
    int extract(String aspect, int maximum, boolean simulate);
    int stored(String aspect);
    /** Maximum total units across all aspects, not capacity per aspect. */
    int capacity();
    /** Immutable snapshot of positive stored quantities. */
    java.util.Map<String, Integer> contents();
    default boolean canReceive() { return true; }
    default boolean canExtract() { return true; }
}
