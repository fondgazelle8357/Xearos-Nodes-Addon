package me.fond.nodesoverlay.war;

import java.time.Instant;

/**
 * Current territory ownership reported directly by the server's territory
 * information command.
 */
public record TerritoryInfoObservation(
        int territoryId,
        String ownerTown,
        String occupierTown,
        Instant observedAt
) {

    public TerritoryInfoObservation {
        if (territoryId < 0) {
            throw new IllegalArgumentException("territoryId must be non-negative");
        }
        ownerTown = clean(ownerTown);
        occupierTown = clean(occupierTown);
        observedAt = observedAt == null ? Instant.now() : observedAt;
    }

    public boolean occupied() {
        return occupierTown != null
                && (ownerTown == null || !occupierTown.equalsIgnoreCase(ownerTown));
    }

    private static String clean(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.trim();
        return cleaned.isEmpty() ? null : cleaned;
    }
}
