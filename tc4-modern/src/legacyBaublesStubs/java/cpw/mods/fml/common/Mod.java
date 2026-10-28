package cpw.mods.fml.common;
import java.lang.annotation.*;
/** Compile-only Forge signature; never packaged. */
@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.TYPE)
public @interface Mod {
    String modid(); String name(); String version(); String acceptedMinecraftVersions();
    @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.METHOD) @interface EventHandler { }
}
