package github.com.gengyoubo.tcmodern.tc4.compat.jei;

import static org.junit.jupiter.api.Assertions.assertEquals;

import github.com.gengyoubo.tcmodern.api.aspect.TcAspect;
import java.util.List;
import mezz.jei.api.ingredients.subtypes.UidContext;
import org.junit.jupiter.api.Test;

class JeiAspectTest {
    private final JeiAspect.Helper helper = new JeiAspect.Helper();

    @Test void originalAspectsDisplayThaumcraft() {
        var aspect = aspect("aer", "thaumcraft:textures/aspects/aer.png");
        assertEquals("thaumcraft", helper.getDisplayModId(aspect));
        assertEquals("tc4modern", helper.getResourceLocation(aspect).getNamespace());
        assertEquals("tc4modern:aspect/aer", helper.getUniqueId(aspect, UidContext.Ingredient));
    }

    @Test void addedAspectsDisplayTheirSourceMod() {
        assertEquals("tc4modern", helper.getDisplayModId(aspect("modernitas", "tc4modern:textures/aspects/modernitas.png")));
        assertEquals("addon", helper.getDisplayModId(aspect("custom", "addon:textures/aspects/custom.png")));
    }

    @Test void missingOrInvalidIconsDefaultToThaumcraft() {
        assertEquals("thaumcraft", helper.getDisplayModId(aspect("aer", "")));
        assertEquals("thaumcraft", helper.getDisplayModId(aspect("aer", "invalid texture")));
    }

    private static JeiAspect aspect(String tag, String texture) {
        return new JeiAspect(new TcAspect(tag, 0xFFFFFF, texture, List.of()), 0);
    }
}
