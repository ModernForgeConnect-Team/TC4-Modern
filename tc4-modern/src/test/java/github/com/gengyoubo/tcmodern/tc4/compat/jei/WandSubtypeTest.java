package github.com.gengyoubo.tcmodern.tc4.compat.jei;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class WandSubtypeTest {
    @Test void storedVisAndFocusAreNotPartOfTheWand() {
        var empty = Tc4JeiPlugin.wandSubtype("{cap:\"iron\",rod:\"wood\"}");
        assertEquals("wand:iron/wood", empty);
        assertEquals(empty, Tc4JeiPlugin.wandSubtype("{aer:2500,cap:\"iron\",ignis:2500,rod:\"wood\",focus:{id:4s},}"));
    }

    @Test void splitItemsResolveToTheirOriginalHostItem() {
        var original = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("thaumcraft", "item_essence");
        assertEquals(original, Tc4JeiPlugin.original(original.withSuffix("/meta_1")));
        assertEquals(original, Tc4JeiPlugin.original(original));
    }

    @Test void capRodAndSceptreSeparateTheEntries() {
        var wand = Tc4JeiPlugin.wandSubtype("{cap:\"thaumium\",rod:\"silverwood\"}");
        var sceptre = Tc4JeiPlugin.wandSubtype("{cap:\"thaumium\",rod:\"silverwood\",sceptre:1b}");
        assertEquals("wand:thaumium/silverwood/sceptre", sceptre);
        assertNotEquals(wand, sceptre);
        assertNotEquals(wand, Tc4JeiPlugin.wandSubtype("{cap:\"gold\",rod:\"silverwood\"}"));
        assertEquals(wand, Tc4JeiPlugin.wandSubtype("{cap:thaumium,rod:silverwood,sceptre:0b}"));
    }
}
