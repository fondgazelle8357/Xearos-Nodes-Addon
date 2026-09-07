package me.fond.nodesoverlay.model;

import java.util.Set;

public record TownRecord(
        String name,
        String nation,
        RgbColor color,
        Set<Integer> territories,
        Set<Integer> directTerritories,
        Set<Integer> annexed,
        Set<Integer> captured,
        Set<Integer> claimed,
        Set<Integer> cores,
        Set<Integer> sphered,
        Set<String> allies,
        Set<String> enemies
) {
    public TownRecord {
        territories = Set.copyOf(territories);
        directTerritories = Set.copyOf(directTerritories);
        annexed = Set.copyOf(annexed);
        captured = Set.copyOf(captured);
        claimed = Set.copyOf(claimed);
        cores = Set.copyOf(cores);
        sphered = Set.copyOf(sphered);
        allies = Set.copyOf(allies);
        enemies = Set.copyOf(enemies);
    }

    /**
     * Compatibility constructor for callers that already provide a normalized
     * territory set and do not need to distinguish the raw exported list.
     */
    public TownRecord(
            String name,
            String nation,
            RgbColor color,
            Set<Integer> territories,
            Set<Integer> annexed,
            Set<Integer> captured,
            Set<Integer> claimed,
            Set<Integer> cores,
            Set<String> allies,
            Set<String> enemies
    ) {
        this(name, nation, color, territories, territories, annexed, captured, claimed,
                cores, Set.of(), allies, enemies);
    }
}
