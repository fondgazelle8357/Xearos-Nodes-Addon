package me.fond.nodesoverlay.war;

import me.fond.nodesoverlay.model.RgbColor;

import java.time.Instant;

public record LiveCapture(
        String attackingPlayer,
        String occupierTown,
        String occupierNation,
        RgbColor occupierColor,
        Instant updatedAt
) {
}
