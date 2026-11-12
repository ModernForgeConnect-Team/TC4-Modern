package github.com.gengyoubo.tcmodern.api.essentia;

/** Optional TC4 tube settings for a side-specific AspectStorage capability.
 * A higher suction draws from lower suction; equal suction does not transfer.
 */
public interface AspectTubePort extends AspectStorage {
    /** 0..128. Zero disables pulling into this side. */
    default int suction() { return canReceive() ? 64 : 0; }
    /** Null accepts any aspect; otherwise only the named aspect is pulled. */
    default String suctionAspect() { return null; }
    /** Minimum external suction required to extract from this side. */
    default int minimumSuction() { return 0; }
}
