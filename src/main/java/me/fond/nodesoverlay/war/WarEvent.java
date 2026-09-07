package me.fond.nodesoverlay.war;

import me.fond.nodesoverlay.model.RgbColor;

import java.time.Instant;

public record WarEvent(
        String key,
        Kind kind,
        String attackingPlayer,
        String attackingTown,
        String attackingNation,
        String reportedTown,
        int territoryId,
        int blockX,
        int blockY,
        int blockZ,
        int chunkX,
        int chunkZ,
        WarRelation relation,
        WarMarkerType marker,
        RgbColor color,
        Instant updatedAt
) {
    public enum Kind {
        ATTACK,
        ATTACK_DEFENDED,
        CHUNK_CAPTURE,
        CHUNK_DEFENDED,
        CHUNK_LIBERATED,
        TERRITORY_CAPTURE,
        TERRITORY_LIBERATED
    }
}
