package me.fond.nodesoverlay.model;

import java.util.Map;

public record NodeDefinition(
        String id,
        String name,
        String icon,
        Map<String, Double> cost,
        int priority,
        Map<String, Double> income,
        Map<String, Double> ores,
        Map<String, Double> crops,
        Map<String, Double> animals,
        Map<String, Double> modifiers
) {
    public NodeDefinition {
        cost = Map.copyOf(cost);
        income = Map.copyOf(income);
        ores = Map.copyOf(ores);
        crops = Map.copyOf(crops);
        animals = Map.copyOf(animals);
        modifiers = Map.copyOf(modifiers);
    }
}
