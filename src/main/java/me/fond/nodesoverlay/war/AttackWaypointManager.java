package me.fond.nodesoverlay.war;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import me.fond.nodesoverlay.NodesOverlayRuntime;
import me.fond.nodesoverlay.config.ServerSettings;
import me.fond.nodesoverlay.model.RgbColor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import xaero.common.minimap.waypoints.Waypoint;
import xaero.hud.minimap.BuiltInHudModules;
import xaero.hud.minimap.module.MinimapSession;
import xaero.hud.minimap.waypoint.WaypointColor;
import xaero.hud.minimap.waypoint.WaypointPurpose;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Uses Xaero's custom waypoint collection. Entries are explicitly marked
 * temporary and remain isolated under this mod's origin ID.
 */
public final class AttackWaypointManager {

    private static final Identifier ORIGIN = Identifier.of("nodesoverlay", "war_events");

    public void tick(MinecraftClient client, List<WarEvent> events) {
        Int2ObjectMap<Waypoint> waypoints = waypoints();
        if (waypoints == null) {
            return;
        }
        if (!NodesOverlayRuntime.settings().showWorldWaypoints || client.player == null) {
            waypoints.clear();
            return;
        }

        int maximumDistance = NodesOverlayRuntime.settings().waypointMaximumDistance;
        double maximumDistanceSquared = (double) maximumDistance * maximumDistance;
        Set<Integer> wanted = new HashSet<>();
        for (WarEvent event : events) {
            double dx = event.blockX() + 0.5D - client.player.getX();
            double dz = event.blockZ() + 0.5D - client.player.getZ();
            if (dx * dx + dz * dz > maximumDistanceSquared) {
                continue;
            }
            if (!NodesOverlayRuntime.settings().waypointsThroughTerrain
                    && !hasLineOfSight(client, event)) {
                continue;
            }
            int id = event.key().hashCode();
            wanted.add(id);
            Waypoint existing = waypoints.get(id);
            String label = waypointLabel(event);
            String symbol = symbol(event);
            WaypointColor color = nearestColor(waypointColor(
                    event,
                    NodesOverlayRuntime.settings()
            ));
            if (existing == null
                    || existing.getX() != event.blockX()
                    || existing.getY() != event.blockY()
                    || existing.getZ() != event.blockZ()
                    || !label.equals(existing.getName())
                    || !symbol.equals(existing.getSymbol())
                    || color != existing.getWaypointColor()) {
                Waypoint waypoint = new Waypoint(
                        event.blockX(),
                        event.blockY(),
                        event.blockZ(),
                        label,
                        symbol,
                        color,
                        WaypointPurpose.NORMAL,
                        true,
                        true
                );
                waypoint.setTemporary(true);
                waypoints.put(id, waypoint);
            }
        }

        /*
         * This origin is owned exclusively by the addon, so reconcile the
         * complete Xaero collection instead of only IDs observed during the
         * current session. Xaero can restore the custom collection after a
         * disconnect while the tracker has already been cleared. Tracking
         * only the previous tick's IDs left those restored entries behind as
         * world-space waypoints with no corresponding map attack icon.
         */
        retainOnly(waypoints, wanted);
    }

    public void clear() {
        Int2ObjectMap<Waypoint> waypoints = waypoints();
        if (waypoints != null) {
            waypoints.clear();
        }
    }

    private Int2ObjectMap<Waypoint> waypoints() {
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

    static <T> void retainOnly(Int2ObjectMap<T> entries, Set<Integer> wanted) {
        for (int id : Set.copyOf(entries.keySet())) {
            if (!wanted.contains(id)) {
                entries.remove(id);
            }
        }
    }

    static RgbColor waypointColor(WarEvent event, ServerSettings settings) {
        RgbColor eventColor = event.color() == null ? RgbColor.NEUTRAL : event.color();
        if (settings == null || !settings.relationshipAwareWarMarkers) {
            return eventColor;
        }
        return switch (event.relation()) {
            case FRIENDLY -> settings.friendlyMarkerColor;
            case ALLIED -> settings.alliedMarkerColor;
            case HOSTILE -> settings.hostileMarkerColor;
            case UNKNOWN -> settings.unknownMarkerColor;
        };
    }

    private static String waypointLabel(WarEvent event) {
        String target = event.reportedTown() == null || event.reportedTown().isBlank()
                ? (event.territoryId() >= 0 ? "Territory " + event.territoryId() : "War")
                : event.reportedTown();
        return switch (event.kind()) {
            case ATTACK -> "Attack: " + target;
            case ATTACK_DEFENDED -> "Defended attack: " + target;
            case CHUNK_CAPTURE -> "Captured chunk: " + target;
            case CHUNK_DEFENDED -> "Defended chunk: " + target;
            case CHUNK_LIBERATED -> "Liberated chunk: " + target;
            case TERRITORY_CAPTURE -> "Captured territory: " + target;
            case TERRITORY_LIBERATED -> "Liberated territory: " + target;
        };
    }

    private static boolean hasLineOfSight(MinecraftClient client, WarEvent event) {
        if (client.world == null || client.player == null
                || !client.world.isChunkLoaded(event.chunkX(), event.chunkZ())) {
            return false;
        }
        Vec3d start = client.player.getEyePos();
        Vec3d end = new Vec3d(event.blockX() + 0.5D, event.blockY() + 1.5D, event.blockZ() + 0.5D);
        HitResult hit = client.world.raycast(new RaycastContext(
                start,
                end,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                client.player
        ));
        return hit.getType() == HitResult.Type.MISS
                || hit.getPos().squaredDistanceTo(end) <= 2.25D;
    }

    private static String symbol(WarEvent event) {
        if (!NodesOverlayRuntime.settings().relationshipAwareWarMarkers
                && event.kind() == WarEvent.Kind.ATTACK) {
            return "⚔";
        }
        return event.marker() == WarMarkerType.SHIELD ? "⬟" : event.marker() == WarMarkerType.FLAG ? "⚑" : "⚔";
    }

    private static WaypointColor nearestColor(RgbColor desired) {
        WaypointColor nearest = WaypointColor.RED;
        long nearestDistance = Long.MAX_VALUE;
        for (WaypointColor candidate : WaypointColor.values()) {
            int rgb = candidate.getHex();
            int red = rgb >> 16 & 0xFF;
            int green = rgb >> 8 & 0xFF;
            int blue = rgb & 0xFF;
            long dr = red - desired.red();
            long dg = green - desired.green();
            long db = blue - desired.blue();
            long distance = dr * dr + dg * dg + db * db;
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = candidate;
            }
        }
        return nearest;
    }
}
