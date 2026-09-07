package me.fond.nodesoverlay.integration.xaero;

import org.junit.jupiter.api.Test;
import xaero.common.minimap.waypoints.Waypoint;
import xaero.hud.minimap.waypoint.WaypointColor;
import xaero.hud.minimap.waypoint.WaypointPurpose;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WaypointScalePolicyTest {

    @Test
    void usesTwoThirdsScaleBeyondTwentyBlocks() {
        Waypoint waypoint = new Waypoint(
                0,
                64,
                0,
                "Waypoint",
                "W",
                WaypointColor.RED,
                WaypointPurpose.NORMAL,
                true,
                true
        );

        assertFalse(WaypointScalePolicy.isDistant(waypoint, 20.5D, 0.5D));
        assertTrue(WaypointScalePolicy.isDistant(waypoint, 21.0D, 0.5D));
        assertEquals(2.0F / 3.0F, WaypointScalePolicy.DISTANT_SCALE);
        assertEquals(4, WaypointScalePolicy.distantScale(6));
        assertEquals(2, WaypointScalePolicy.distantScale(3));
        assertEquals(1, WaypointScalePolicy.distantScale(1));
    }
}
