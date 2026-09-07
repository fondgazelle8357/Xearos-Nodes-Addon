package me.fond.nodesoverlay.model;

import java.time.Instant;
import java.util.Locale;
import java.util.Set;

/**
 * A NodesOverlay port location. Coordinates from {@code ports.json} provide the
 * baseline while an in-game {@code /port info} response can supply newer
 * coordinates, ownership and access details.
 */
public record PortRecord(
        String id,
        String name,
        Set<String> groups,
        int x,
        int z,
        String owner,
        Boolean allyAccess,
        Boolean neutralAccess,
        Boolean enemyAccess,
        String allyCost,
        boolean observedInGame,
        Instant updatedAt
) {

    public PortRecord {
        id = normalizeId(id == null || id.isBlank() ? name : id);
        name = name == null || name.isBlank() ? id : name.trim();
        groups = groups == null ? Set.of() : Set.copyOf(groups);
        owner = owner == null || owner.isBlank() ? null : owner.trim();
        allyCost = allyCost == null || allyCost.isBlank() ? null : allyCost.trim();
        updatedAt = updatedAt == null ? Instant.EPOCH : updatedAt;
    }

    public int chunkX() {
        return Math.floorDiv(x, 16);
    }

    public int chunkZ() {
        return Math.floorDiv(z, 16);
    }

    public int chunkCenterX() {
        return chunkX() * 16 + 8;
    }

    public int chunkCenterZ() {
        return chunkZ() * 16 + 8;
    }

    public static String normalizeId(String value) {
        return value == null
                ? ""
                : value.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9._-]+", "_");
    }
}
