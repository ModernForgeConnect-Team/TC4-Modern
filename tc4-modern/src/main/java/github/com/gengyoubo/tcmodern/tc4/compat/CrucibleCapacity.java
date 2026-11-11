package github.com.gengyoubo.tcmodern.tc4.compat;

import github.com.gengyoubo.mfcl.api.LegacyRedirects;
import github.com.gengyoubo.mfcl.api.spi.LegacyRedirectProvider;

/**
 * TC4's crucible spills essentia whenever it holds more than 100 in total. Here it holds up to {@link #CAPACITY}
 * in total, so large recipes (such as ancient debris, 220 essentia) can be brewed; above that it spills as before.
 *
 * <p>The 100 is a literal that {@code TileCrucible} compares with its own {@code tagAmount()}: to spill one essentia
 * every 5 ticks, for the overflow bubbles, and as the liquid height ({@code tagAmount / 100}). Inside that class
 * {@code tagAmount()} therefore reports at most 100 while the real total fits, and 101 once it exceeds the capacity.
 * Overflow and its effects start only above the capacity, the liquid shows full from 100 essentia on, and the
 * {@code tagAmount() > 0} checks (boiling off) are unaffected.
 */
public final class CrucibleCapacity implements LegacyRedirectProvider {
    public static final int CAPACITY = 32767;
    private static final int ORIGINAL_CAPACITY = 100;
    private static final String CRUCIBLE = "thaumcraft.common.tiles.TileCrucible";

    @Override public void register(LegacyRedirects redirects) {
        redirects.redirect(CRUCIBLE, CRUCIBLE, "tagAmount", "()I", call -> reported((Integer) call.proceed(call.arguments())));
    }

    /** The total TileCrucible sees in place of {@code total}. */
    static int reported(int total) {
        return total > CAPACITY ? ORIGINAL_CAPACITY + 1 : Math.min(total, ORIGINAL_CAPACITY);
    }
}
