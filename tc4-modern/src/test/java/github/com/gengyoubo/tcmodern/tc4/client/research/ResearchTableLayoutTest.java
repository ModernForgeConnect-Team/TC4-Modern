package github.com.gengyoubo.tcmodern.tc4.client.research;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ResearchTableLayoutTest {
    @Test void paletteWindowIsColumnMajorAndSlidesOneColumn(){
        assertEquals(0,ResearchTableLayout.paletteIndex(10,40,0,70));
        assertEquals(4,ResearchTableLayout.paletteIndex(10,119,0,70));
        assertEquals(5,ResearchTableLayout.paletteIndex(26,40,0,70));
        assertEquals(24,ResearchTableLayout.paletteIndex(89,119,0,70));
        assertEquals(5,ResearchTableLayout.paletteIndex(10,40,1,70));
        assertEquals(-1,ResearchTableLayout.paletteIndex(90,40,0,70));
        assertEquals(-1,ResearchTableLayout.paletteIndex(10,120,0,70));
        assertEquals(-1,ResearchTableLayout.paletteIndex(26,40,0,5));
    }
    @Test void lastPageRetainsTheOriginalColumnWindowIncludingExactMultiplesOfFive(){
        assertEquals(-4,ResearchTableLayout.lastPage(0));
        assertEquals(0,ResearchTableLayout.lastPage(20));assertEquals(0,ResearchTableLayout.lastPage(24));
        assertEquals(1,ResearchTableLayout.lastPage(25));assertEquals(1,ResearchTableLayout.lastPage(26));
        assertEquals(2,ResearchTableLayout.lastPage(30));assertEquals(2,ResearchTableLayout.lastPage(31));
        assertEquals(56,ResearchTableLayout.lastPage(300));
    }
    @Test void hexHitTestAgreesWithAxialCentersWithoutSelectingHoles(){
        for(int q=-4;q<=4;q++)for(int r=-4;r<=4;r++){
            var cell=new ResearchTableState.Cell(q,r,0,null);
            assertTrue(ResearchTableLayout.contains(cell,cell.x(),cell.y()));
            assertTrue(ResearchTableLayout.contains(cell,cell.x()+8.9,cell.y()));
            assertFalse(ResearchTableLayout.contains(cell,cell.x()+9.1,cell.y()));
            assertFalse(ResearchTableLayout.contains(cell,cell.x()+8,cell.y()+6));
            assertFalse(ResearchTableLayout.contains(cell,cell.x(),cell.y()+8));
        }
    }
    @Test void noSelectionAndEmptyPoolAreUnavailable(){
        assertFalse(ResearchTableState.empty().available(null));
        assertFalse(ResearchTableState.empty().available("aer"));
        assertFalse(new ResearchTableState.Pool("aer",0,0).available());
        assertTrue(new ResearchTableState.Pool("aer",0,1).available());
    }
    @Test void tooltipHasOnlyTheOriginalNameAndDescription(){
        var tooltip=new ResearchTableTooltip("Aer","Air, wind, weather");
        assertEquals(java.util.List.of("Aer","Air, wind, weather"),tooltip.lines());
    }
    @Test void pixelToHexUsesTheOriginalCubeRoundingAtEveryCellCenter(){
        for(int q=-6;q<=6;q++)for(int r=-6;r<=6;r++){
            var cell=new ResearchTableState.Cell(q,r,0,null);
            assertEquals(new ResearchTableLayout.Hex(q,r),ResearchTableLayout.hexAt(cell.x(),cell.y()));
        }
    }
}
