package me.fond.nodesoverlay.integration.xaero;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import me.fond.nodesoverlay.model.RgbColor;
import me.fond.nodesoverlay.model.TerritoryView;
import me.fond.nodesoverlay.war.WarEvent;
import net.minecraft.util.Identifier;
import xaero.common.minimap.waypoints.Waypoint;
import xaero.hud.minimap.BuiltInHudModules;
import xaero.hud.minimap.module.MinimapSession;
import xaero.hud.minimap.waypoint.WaypointColor;
import xaero.hud.minimap.waypoint.WaypointPurpose;

public final class TerritoryWaypointManager {

    private static final Identifier ORIGIN =
            Identifier.of("nodesoverlay", "selected_territories");

    private TerritoryWaypointManager() {
    }

    public static boolean has(int territoryId) {
        Int2ObjectMap<Waypoint> collection = collection();
        return collection != null && collection.get(territoryId) != null;
    }

    public static boolean create(TerritoryView territory, WarEvent attack) {
        Int2ObjectMap<Waypoint> collection = collection();
        if (collection == null || territory == null) {
            return false;
        }
        int x = attack == null ? territory.territory().coreX() : attack.blockX();
        int y = attack == null ? 128 : attack.blockY();
        int z = attack == null ? territory.territory().coreZ() : attack.blockZ();
        RgbColor color = attack == null ? territory.ownerColor() : attack.color();
        String target = attack == null
                ? nodeWaypointName(territory)
                : "Attack: " + attack.reportedTown();
        String symbol = attack == null
                ? "N"
                : attack.marker().name().contains("SHIELD") ? "D" : "W";
        Waypoint waypoint = new Waypoint(
                x,
                y,
                z,
                target,
                symbol,
                nearestColor(color),
                WaypointPurpose.NORMAL,
                true,
                true
        );
        waypoint.setTemporary(true);
        collection.put(territory.territory().id(), waypoint);
        return true;
    }

    public static void remove(int territoryId) {
        Int2ObjectMap<Waypoint> collection = collection();
        if (collection != null) {
            collection.remove(territoryId);
        }
    }

    public static void clear() {
        Int2ObjectMap<Waypoint> collection = collection();
        if (collection != null) {
            collection.clear();
        }
    }

    private static String nodeWaypointName(TerritoryView territory) {
        if (territory.territory().nodeIds().isEmpty()) {
            return "Territory " + territory.territory().id() + " core";
        }
        return "Node " + String.join(", ", territory.territory().nodeIds())
                + " (#" + territory.territory().id() + ")";
    }

    private static Int2ObjectMap<Waypoint> collection() {
        try {
            MinimapSession session = BuiltInHudModules.MINIMAP.getCurrentSession();
            if (session == null || session.getWorldManager() == null) {
                return null;
            }
            return session.getWorldManager().getCustomWaypoints(ORIGIN);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static WaypointColor nearestColor(RgbColor desired) {
        WaypointColor nearest = WaypointColor.RED;
        long nearestDistance = Long.MAX_VALUE;
        for (WaypointColor candidate : WaypointColor.values()) {
            int rgb = candidate.getHex();
            long red = (rgb >> 16 & 255) - desired.red();
            long green = (rgb >> 8 & 255) - desired.green();
            long blue = (rgb & 255) - desired.blue();
            long distance = red * red + green * green + blue * blue;
            if (distance < nearestDistance) {
                nearest = candidate;
                nearestDistance = distance;
            }
        }
        return nearest;
    }
}
