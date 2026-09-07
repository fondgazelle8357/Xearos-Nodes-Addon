package me.fond.nodesoverlay.integration.xaero;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

final class NodesOverlayWorldMapMarkerRendererTest {

    @Test
    void tooltipLinesUseXaeroSafeLineFeeds() {
        String tooltip = NodesOverlayWorldMapMarkerRenderer.xaeroTooltipText(List.of(
                "Territory #2129",
                "State: Captured",
                "Under attack"
        ));

        assertEquals(
                "Territory #2129 \nState: Captured \nUnder attack",
                tooltip
        );
        assertFalse(tooltip.contains("#2129\n"));
        assertFalse(tooltip.contains("Captured\n"));
    }
}
