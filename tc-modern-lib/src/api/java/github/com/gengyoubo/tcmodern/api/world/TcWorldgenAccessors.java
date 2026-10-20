package github.com.gengyoubo.tcmodern.api.world;

import github.com.gengyoubo.mfcl.api.mla.LegacyField;
import github.com.gengyoubo.mfcl.api.mla.LegacyType;

/** Exact public contracts of TC4 4.2.3.5 world generation. No old classes are linked. */
public final class TcWorldgenAccessors {
    private TcWorldgenAccessors() { }

    /** Generation toggles from Thaumcraft.cfg (world_generation). */
    @LegacyType("thaumcraft.common.config.Config")
    public interface Config {
        @LegacyField(name = "genCinnibar", descriptor = "Z", isStatic = true) boolean cinnabar();
        @LegacyField(name = "genAmber", descriptor = "Z", isStatic = true) boolean amber();
        @LegacyField(name = "genInfusedStone", descriptor = "Z", isStatic = true) boolean infused();
        /** generate_trees: greatwood, silverwood and cinderpearl outside the TC4 biomes' own decoration. */
        @LegacyField(name = "genTrees", descriptor = "Z", isStatic = true) boolean trees();
    }
}
