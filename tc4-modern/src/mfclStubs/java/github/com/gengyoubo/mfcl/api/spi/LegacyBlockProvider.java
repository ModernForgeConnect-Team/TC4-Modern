package github.com.gengyoubo.mfcl.api.spi;

/**
 * Optional native implementation of an original non-tile block. Register through LegacyBlockProviders or services.
 * Called during host block registration, after original declarations are available and before items register.
 * All objects are host objects; no original runtime objects cross this interface. Unhandled keys return null.
 */
public interface LegacyBlockProvider {
    NativeBlock create(String legacyKey, Object hostProperties, DisplayTicks display);

    /** Original ambient presentation, called on the client thread with host Level and BlockPos values. */
    interface DisplayTicks { void animate(Object hostLevel, Object hostPosition, long seed); }

    interface NativeBlock {
        /** The native host Block to register under the original host registry ID. */
        Object block();
        /** Converts original metadata to a host BlockState of {@link #block()}. */
        Object state(int metadata);
        /** Converts a host BlockState back to original metadata, in 0..15. */
        int metadata(Object hostState);
        /** An original world write can retain host-only properties from the previous state at that position. */
        default Object update(Object previousState, int metadata) { return state(metadata); }
        /** Placement may preserve host properties such as waterlogging and player persistence. */
        default Object placement(Object placementState, int metadata) { return state(metadata); }
    }
}
