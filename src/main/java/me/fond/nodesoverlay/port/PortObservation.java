package me.fond.nodesoverlay.port;

import java.time.Instant;
import java.util.Set;

public record PortObservation(
        String name,
        Set<String> groups,
        int x,
        int z,
        String owner,
        Boolean allyAccess,
        Boolean neutralAccess,
        Boolean enemyAccess,
        String allyCost,
        Instant observedAt
) {
}
