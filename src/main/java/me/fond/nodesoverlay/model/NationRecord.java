package me.fond.nodesoverlay.model;

import java.util.Set;

public record NationRecord(
        String name,
        RgbColor color,
        String capital,
        Set<String> towns,
        Set<String> allies,
        Set<String> enemies
) {
    public NationRecord {
        capital = capital == null || capital.isBlank() ? null : capital.trim();
        towns = Set.copyOf(towns);
        allies = Set.copyOf(allies);
        enemies = Set.copyOf(enemies);
    }
}
