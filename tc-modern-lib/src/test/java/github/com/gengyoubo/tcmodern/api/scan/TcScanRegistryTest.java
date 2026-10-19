package github.com.gengyoubo.tcmodern.api.scan;

import github.com.gengyoubo.mfcl.api.LegacyStack;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TcScanRegistryTest {
    @Test void variantsFollowMainBlockOverridesAndKeepTheirOwnDiscoveryIdentity() {
        var registry = new TcScanRegistry(); var block = ScanKey.block("minecraft:copper_block");
        var stairs = ScanKey.block("minecraft:cut_copper_stairs");
        registry.register(block,AspectValues.of("metallum",4)); registry.inherit(stairs,block);
        assertEquals(4,registry.resolve(stairs, source -> Optional.empty()).orElseThrow().amounts().get("metallum"));
        registry.register(block,AspectValues.of("metallum",6));
        assertEquals(6,registry.resolve(stairs, source -> Optional.empty()).orElseThrow().amounts().get("metallum"));
        assertNotEquals(stairs.researchKey(),block.researchKey());
    }
    @Test void defaultsCannotOverwriteAddonRules() {
        var registry = new TcScanRegistry(); var key = ScanKey.item("addon:crystal");
        registry.register(key,AspectValues.of("praecantatio",3));
        registry.registerDefault(key,TcScanRegistry.Definition.aspects(AspectValues.of("terra",1)));
        registry.replaceDefault(key,TcScanRegistry.Definition.aspects(AspectValues.of("aqua",2)));
        assertEquals(Map.of("praecantatio",3),registry.resolve(key, source -> Optional.empty()).orElseThrow().amounts());
    }
    @Test void originalSourcesPreserveMetadataAndMissingSourcesStayMissing() {
        var registry = new TcScanRegistry(); var key = ScanKey.block("addon:deepslate_infused");
        var original = new LegacyStack("Thaumcraft:blockCustomOre",4,1,"");
        registry.inheritOriginal(key,original);
        assertTrue(registry.resolve(key, source -> Optional.empty()).isEmpty());
        assertEquals(AspectValues.of("terra",2),registry.resolve(key, source -> {
            assertEquals(original,source); return Optional.of(AspectValues.of("terra",2));
        }).orElseThrow());
    }
    @Test void cyclesAreRejectedAndUnknownParentsDoNotInventValues() {
        var registry = new TcScanRegistry(); var a = ScanKey.block("test:a"); var b = ScanKey.block("test:b");
        registry.inherit(a,b); assertTrue(registry.resolve(a, source -> Optional.empty()).isEmpty());
        registry.inherit(b,a); assertThrows(IllegalStateException.class,() -> registry.resolve(a, source -> Optional.empty()));
    }
    @Test void publishedValuesAndDefinitionsAreImmutable() {
        var input = new HashMap<>(Map.of("aer",2)); var value = new AspectValues(input); input.put("aer",10);
        assertEquals(2,value.amounts().get("aer")); assertThrows(UnsupportedOperationException.class,() -> value.amounts().put("aer",4));
        var registry = new TcScanRegistry(); registry.register(ScanKey.entity("test:mob"),value);
        assertThrows(UnsupportedOperationException.class,() -> registry.definitions().clear());
    }
    @Test void rejectsInvalidIdsAmountsAndAmbiguousSources() {
        assertThrows(IllegalArgumentException.class,() -> ScanKey.block("Thaumcraft:Uppercase"));
        assertThrows(IllegalArgumentException.class,() -> AspectValues.of("aer",0));
        assertThrows(IllegalArgumentException.class,() -> AspectValues.of("aer",32768));
        assertThrows(IllegalArgumentException.class,() -> new TcScanRegistry.Definition(null,null,null));
    }
}
