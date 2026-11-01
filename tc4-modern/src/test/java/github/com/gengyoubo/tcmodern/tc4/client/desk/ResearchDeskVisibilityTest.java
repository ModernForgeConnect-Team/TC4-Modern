package github.com.gengyoubo.tcmodern.tc4.client.desk;

import github.com.gengyoubo.tcmodern.tc4.research.DeskSnapshot;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ResearchDeskVisibilityTest {
    @Test void discoveredAspectsRemainVisibleWhenTheirPointsReachZero() {
        var snapshot = new DeskSnapshot(Map.of("aer", 0, "ignis", 3), null, false, false, false, 0, 0, List.of());
        assertTrue(ResearchDeskScreen.discovered(snapshot, "aer"));
        assertTrue(ResearchDeskScreen.discovered(snapshot, "ignis"));
        assertFalse(ResearchDeskScreen.discovered(snapshot, "lux"));
        assertFalse(ResearchDeskScreen.discovered(snapshot, null));
    }
}
