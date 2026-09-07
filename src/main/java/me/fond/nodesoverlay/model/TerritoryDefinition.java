package me.fond.nodesoverlay.model;

import java.util.List;

public record TerritoryDefinition(
        int id,
        String name,
        int coreX,
        int coreZ,
        int coreChunkX,
        int coreChunkZ,
        int[] chunks,
        int size,
        List<Integer> neighbors,
        boolean edge,
        List<String> nodeIds,
        int sourceColor,
        int minBlockX,
        int minBlockZ,
        int maxBlockX,
        int maxBlockZ,
        GeometryLod exactGeometry,
        GeometryLod mediumGeometry,
        GeometryLod coarseGeometry,
        List<String> dataIssues
) {
    public TerritoryDefinition {
        chunks = chunks.clone();
        neighbors = List.copyOf(neighbors);
        nodeIds = List.copyOf(nodeIds);
        dataIssues = List.copyOf(dataIssues);
    }

    public boolean intersects(double minX, double minZ, double maxX, double maxZ) {
        return maxBlockX >= minX && minBlockX <= maxX && maxBlockZ >= minZ && minBlockZ <= maxZ;
    }

    public GeometryLod geometryForPixelsPerBlock(double pixelsPerBlock) {
        double pixelsPerChunk = pixelsPerBlock * 16.0;
        if (pixelsPerChunk >= 1.5) {
            return exactGeometry;
        }
        if (pixelsPerChunk >= 0.28) {
            return mediumGeometry;
        }
        return coarseGeometry;
    }

    public boolean hasDataIssues() {
        return !dataIssues.isEmpty();
    }

    public TerritoryDefinition withDataIssues(List<String> additionalIssues) {
        java.util.LinkedHashSet<String> merged = new java.util.LinkedHashSet<>(dataIssues);
        merged.addAll(additionalIssues);
        return new TerritoryDefinition(
                id,
                name,
                coreX,
                coreZ,
                coreChunkX,
                coreChunkZ,
                chunks,
                size,
                neighbors,
                edge,
                nodeIds,
                sourceColor,
                minBlockX,
                minBlockZ,
                maxBlockX,
                maxBlockZ,
                exactGeometry,
                mediumGeometry,
                coarseGeometry,
                List.copyOf(merged)
        );
    }
}
