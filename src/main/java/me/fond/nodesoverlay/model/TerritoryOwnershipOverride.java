package me.fond.nodesoverlay.model;

/**
 * Authoritative owner and occupier names learned from an in-game territory
 * information response.
 */
public record TerritoryOwnershipOverride(
        String ownerTown,
        String occupierTown
) {

    public TerritoryOwnershipOverride {
        ownerTown = clean(ownerTown);
        occupierTown = clean(occupierTown);
    }

    private static String clean(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.trim();
        return cleaned.isEmpty() ? null : cleaned;
    }
}
