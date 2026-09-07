package me.fond.nodesoverlay.model;

import java.util.List;

public record GeometryLod(
        int chunkFactor,
        List<BlockSpan> fillSpans,
        List<BlockSegment> borders
) {
    public GeometryLod {
        fillSpans = List.copyOf(fillSpans);
        borders = List.copyOf(borders);
    }

    public record BlockSpan(int minX, int minZ, int maxX, int maxZ) {
    }

    public record BlockSegment(int x1, int z1, int x2, int z2) {
    }
}
