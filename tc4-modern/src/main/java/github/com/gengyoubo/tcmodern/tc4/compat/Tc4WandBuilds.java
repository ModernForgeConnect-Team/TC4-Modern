package github.com.gengyoubo.tcmodern.tc4.compat;

import java.util.List;

/**
 * The fixed TC4 4.2.3.5 wand builds (ConfigItems), shared by {@link Tc4ItemSplits} and {@link Tc4WandStacks}.
 */
public final class Tc4WandBuilds {
    public static final String WAND = "Thaumcraft:WandCasting";
    public static final List<String> CAPS = List.of("iron", "gold", "thaumium", "void", "copper", "silver");
    public static final List<String> RODS = List.of("wood", "greatwood", "obsidian", "blaze", "ice", "quartz", "bone", "reed", "silverwood",
            "greatwood_staff", "obsidian_staff", "blaze_staff", "ice_staff", "quartz_staff", "bone_staff", "reed_staff",
            "silverwood_staff", "primal_staff");

    private Tc4WandBuilds() { }
}
