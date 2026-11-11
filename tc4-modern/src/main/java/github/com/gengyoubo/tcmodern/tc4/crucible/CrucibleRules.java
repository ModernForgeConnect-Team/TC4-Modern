package github.com.gengyoubo.tcmodern.tc4.crucible;

/** The numbers of TC4 4.2.3.5's {@code TileCrucible}, which the intermediate crucible follows. */
public final class CrucibleRules {
    public static final int WATER_CAPACITY = 1000;
    public static final int MAX_HEAT = 200;
    /** Items dissolve and essentia boils off only above this heat. */
    public static final int BOILING = 150;
    /** Water one boil-off step evaporates, and what one synthesis uses. */
    public static final int BOIL_OFF_WATER = 2, SYNTHESIS_WATER = 50;
    /** The boil-off timer starts here and is set back after dissolving or synthesizing, delaying the next step. */
    public static final long START_COUNTER = -100, AFTER_DISSOLVE = -150, AFTER_SYNTHESIS = -250;
    /** A boil-off step happens once the timer passes this. */
    public static final long BOIL_OFF_INTERVAL = 100;
    /** Over capacity, one essentia spills every this many ticks. */
    public static final int OVERFLOW_INTERVAL = 5;
    /** Synthesis materials react on their own this long after the last one went in (one minute). */
    public static final int SYNTHESIS_TICKS = 20 * 60;
    public static final int MAX_MATERIALS = IntermediateRecipe.MAX_INGREDIENTS;
    /** Flux goo and gas stack up to this metadata in one block. */
    public static final int MAX_FLUX_META = 7;

    private CrucibleRules() { }

    /** Heat after one tick: rises by one over a heat source while there is water, otherwise falls by one. */
    public static int nextHeat(int heat, boolean hasWater, boolean heated) {
        if (hasWater && heated) return heat < MAX_HEAT ? heat + 1 : heat;
        return heat > 0 ? heat - 1 : 0;
    }

    public static boolean boiling(int heat, int water) { return heat > BOILING && water > 0; }

    /**
     * The liquid surface as a fraction of the block, as {@code TileCrucible.getFluidHeight}: water fills from 0.3 to
     * 0.8, essentia raises it further and shows full from 100 on.
     */
    public static float liquidHeight(int water, int essentia) {
        float base = 0.3F + 0.5F * Math.min(water, WATER_CAPACITY) / WATER_CAPACITY;
        float height = base + Math.min(essentia, 100) / 100F * (1 - base);
        return Math.min(height, 0.9999F);
    }
}
