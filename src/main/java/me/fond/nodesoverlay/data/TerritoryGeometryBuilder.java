package me.fond.nodesoverlay.data;

import me.fond.nodesoverlay.model.ChunkCoordinate;
import me.fond.nodesoverlay.model.GeometryLod;
import me.fond.nodesoverlay.model.GeometryLod.BlockSegment;
import me.fond.nodesoverlay.model.GeometryLod.BlockSpan;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class TerritoryGeometryBuilder {

    private TerritoryGeometryBuilder() {
    }

    static GeometryLod build(int[] chunks, int factor) {
        int cellBlocks = factor * 16;
        Set<Long> cells = new HashSet<>(Math.max(16, chunks.length / 2));
        for (int i = 0; i + 1 < chunks.length; i += 2) {
            int cellX = Math.floorDiv(chunks[i], factor);
            int cellZ = Math.floorDiv(chunks[i + 1], factor);
            cells.add(ChunkCoordinate.pack(cellX, cellZ));
        }

        Map<Integer, List<Integer>> rows = new HashMap<>();
        Map<Integer, List<Integer>> horizontalEdges = new HashMap<>();
        Map<Integer, List<Integer>> verticalEdges = new HashMap<>();

        for (long packed : cells) {
            int x = ChunkCoordinate.unpackX(packed);
            int z = ChunkCoordinate.unpackZ(packed);
            rows.computeIfAbsent(z, ignored -> new ArrayList<>()).add(x);

            if (!cells.contains(ChunkCoordinate.pack(x, z - 1))) {
                horizontalEdges.computeIfAbsent(z, ignored -> new ArrayList<>()).add(x);
            }
            if (!cells.contains(ChunkCoordinate.pack(x, z + 1))) {
                horizontalEdges.computeIfAbsent(z + 1, ignored -> new ArrayList<>()).add(x);
            }
            if (!cells.contains(ChunkCoordinate.pack(x - 1, z))) {
                verticalEdges.computeIfAbsent(x, ignored -> new ArrayList<>()).add(z);
            }
            if (!cells.contains(ChunkCoordinate.pack(x + 1, z))) {
                verticalEdges.computeIfAbsent(x + 1, ignored -> new ArrayList<>()).add(z);
            }
        }

        List<BlockSpan> spans = new ArrayList<>();
        rows.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            List<Integer> xs = entry.getValue();
            xs.sort(Integer::compareTo);
            int runStart = xs.getFirst();
            int previous = runStart;
            for (int i = 1; i < xs.size(); i++) {
                int x = xs.get(i);
                if (x == previous || x == previous + 1) {
                    previous = x;
                    continue;
                }
                spans.add(span(runStart, previous, entry.getKey(), cellBlocks));
                runStart = previous = x;
            }
            spans.add(span(runStart, previous, entry.getKey(), cellBlocks));
        });

        List<BlockSegment> borders = new ArrayList<>();
        horizontalEdges.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry ->
                mergeHorizontal(entry.getKey(), entry.getValue(), cellBlocks, borders));
        verticalEdges.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry ->
                mergeVertical(entry.getKey(), entry.getValue(), cellBlocks, borders));
        borders.sort(Comparator.comparingInt(BlockSegment::z1)
                .thenComparingInt(BlockSegment::x1)
                .thenComparingInt(BlockSegment::z2)
                .thenComparingInt(BlockSegment::x2));

        return new GeometryLod(factor, spans, borders);
    }

    private static BlockSpan span(int startCellX, int endCellX, int cellZ, int cellBlocks) {
        return new BlockSpan(
                startCellX * cellBlocks,
                cellZ * cellBlocks,
                (endCellX + 1) * cellBlocks,
                (cellZ + 1) * cellBlocks
        );
    }

    private static void mergeHorizontal(int lineCellZ, List<Integer> starts, int cellBlocks,
                                        List<BlockSegment> output) {
        starts.sort(Integer::compareTo);
        int runStart = starts.getFirst();
        int previous = runStart;
        for (int i = 1; i < starts.size(); i++) {
            int value = starts.get(i);
            if (value == previous || value == previous + 1) {
                previous = value;
                continue;
            }
            output.add(new BlockSegment(runStart * cellBlocks, lineCellZ * cellBlocks,
                    (previous + 1) * cellBlocks, lineCellZ * cellBlocks));
            runStart = previous = value;
        }
        output.add(new BlockSegment(runStart * cellBlocks, lineCellZ * cellBlocks,
                (previous + 1) * cellBlocks, lineCellZ * cellBlocks));
    }

    private static void mergeVertical(int lineCellX, List<Integer> starts, int cellBlocks,
                                      List<BlockSegment> output) {
        starts.sort(Integer::compareTo);
        int runStart = starts.getFirst();
        int previous = runStart;
        for (int i = 1; i < starts.size(); i++) {
            int value = starts.get(i);
            if (value == previous || value == previous + 1) {
                previous = value;
                continue;
            }
            output.add(new BlockSegment(lineCellX * cellBlocks, runStart * cellBlocks,
                    lineCellX * cellBlocks, (previous + 1) * cellBlocks));
            runStart = previous = value;
        }
        output.add(new BlockSegment(lineCellX * cellBlocks, runStart * cellBlocks,
                lineCellX * cellBlocks, (previous + 1) * cellBlocks));
    }
}
