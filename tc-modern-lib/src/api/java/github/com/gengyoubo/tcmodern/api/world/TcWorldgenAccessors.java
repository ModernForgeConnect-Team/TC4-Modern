package github.com.gengyoubo.tcmodern.api.world;

import github.com.gengyoubo.mfcl.api.mla.LegacyField;
import github.com.gengyoubo.mfcl.api.mla.LegacyType;

/** Exact public contracts of TC4 4.2.3.5 world generation. No old classes are linked. */
public final class TcWorldgenAccessors {
    private TcWorldgenAccessors() { }

    /** Generation toggles from Thaumcraft.cfg (world_generation). */
    // The contract is itself named Config, so the original class is spelled out in full.
    @LegacyType(type = thaumcraft.common.config.Config.class)
    public interface Config {
        @LegacyField(name = "genCinnibar", isStatic = true) boolean cinnabar();
        @LegacyField(name = "genAmber", isStatic = true) boolean amber();
        @LegacyField(name = "genInfusedStone", isStatic = true) boolean infused();
        /** generate_trees: greatwood, silverwood and cinderpearl outside the TC4 biomes' own decoration. */
        @LegacyField(name = "genTrees", isStatic = true) boolean trees();
    }
}
