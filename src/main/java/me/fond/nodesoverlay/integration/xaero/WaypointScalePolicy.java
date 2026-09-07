package me.fond.nodesoverlay.integration.xaero;

import xaero.common.minimap.waypoints.Waypoint;

public final class WaypointScalePolicy {

    public static final double DISTANT_SCALE_DISTANCE = 20.0D;
    public static final float DISTANT_SCALE = 2.0F / 3.0F;
    private static final double DISTANT_SCALE_DISTANCE_SQUARED =
            DISTANT_SCALE_DISTANCE * DISTANT_SCALE_DISTANCE;

    private WaypointScalePolicy() {
    }

    public static boolean isDistant(
            Waypoint waypoint,
            double viewerX,
            double viewerZ
    ) {
        double deltaX = waypoint.getX() + 0.5D - viewerX;
        double deltaZ = waypoint.getZ() + 0.5D - viewerZ;
        return deltaX * deltaX + deltaZ * deltaZ
                > DISTANT_SCALE_DISTANCE_SQUARED;
    }

    public static int distantScale(int normalScale) {
        return Math.max(1, Math.round(normalScale * DISTANT_SCALE));
    }
}
