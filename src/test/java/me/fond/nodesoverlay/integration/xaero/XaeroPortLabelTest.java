package me.fond.nodesoverlay.integration.xaero;

import me.fond.nodesoverlay.model.PortRecord;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class XaeroPortLabelTest {

    private static final PortRecord ROME = new PortRecord(
            "rome",
            "Rome",
            Set.of(),
            -766,
            532,
            null,
            null,
            null,
            null,
            null,
            false,
            Instant.EPOCH
    );

    @Test
    void includesNodeControllerWhenOwnerLabelsAreEnabled() {
        assertEquals(
                "Rome | Roma",
                XaeroMarkerRenderContext.formatPortLabel(ROME, true, true, "Roma")
        );
    }

    @Test
    void omitsNodeControllerWhenOwnerLabelsAreDisabled() {
        assertEquals(
                "Rome",
                XaeroMarkerRenderContext.formatPortLabel(ROME, true, false, "Roma")
        );
    }

    @Test
    void hidesEntireLabelWhenPortNamesAreDisabled() {
        assertEquals(
                "",
                XaeroMarkerRenderContext.formatPortLabel(ROME, false, true, "Roma")
        );
    }
}
