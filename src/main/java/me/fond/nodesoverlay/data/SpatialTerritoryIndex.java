package me.fond.nodesoverlay.data;

import me.fond.nodesoverlay.model.ChunkCoordinate;
import me.fond.nodesoverlay.model.TerritoryView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class SpatialTerritoryIndex {

    private static final int CELL_BLOCKS = 2048;
    private static final int MAX_INDEX_CELLS_PER_TERRITORY = 4096;

    private final Map<Long, int[]> cells;
    private final int[] globalTerritories;

    private SpatialTerritoryIndex(Map<Long, int[]> cells, int[] globalTerritories) {
        this.cells = cells;
        this.globalTerritories = globalTerritories;
    }

    static SpatialTerritoryIndex build(Iterable<TerritoryView> territories) {
        Map<Long, IntAccumulator> mutable = new HashMap<>();
        IntAccumulator global = new IntAccumulator();

        for (TerritoryView view : territories) {
            var territory = view.territory();
            int minX = Math.floorDiv(territory.minBlockX(), CELL_BLOCKS);
            int maxX = Math.floorDiv(territory.maxBlockX() - 1, CELL_BLOCKS);
            int minZ = Math.floorDiv(territory.minBlockZ(), CELL_BLOCKS);
            int maxZ = Math.floorDiv(territory.maxBlockZ() - 1, CELL_BLOCKS);
            long count = (long) (maxX - minX + 1) * (maxZ - minZ + 1);
            if (count > MAX_INDEX_CELLS_PER_TERRITORY) {
                global.add(territory.id());
                continue;
            }
            for (int z = minZ; z <= maxZ; z++) {
                for (int x = minX; x <= maxX; x++) {
                    mutable.computeIfAbsent(ChunkCoordinate.pack(x, z), ignored -> new IntAccumulator())
                            .add(territory.id());
                }
            }
        }

        Map<Long, int[]> frozen = new HashMap<>(mutable.size());
        mutable.forEach((key, value) -> frozen.put(key, value.toArray()));
        return new SpatialTerritoryIndex(Map.copyOf(frozen), global.toArray());
    }

    /**
     * Builds a compact point index for core/node markers. Marker visibility is
     * based on the core point, not the potentially enormous and disconnected
     * territory footprint.
     */
    static SpatialTerritoryIndex buildForCores(Iterable<TerritoryView> territories) {
        Map<Long, IntAccumulator> mutable = new HashMap<>();
        for (TerritoryView view : territories) {
            var territory = view.territory();
            int cellX = Math.floorDiv(territory.coreX(), CELL_BLOCKS);
            int cellZ = Math.floorDiv(territory.coreZ(), CELL_BLOCKS);
            mutable.computeIfAbsent(
                    ChunkCoordinate.pack(cellX, cellZ),
                    ignored -> new IntAccumulator()
            ).add(territory.id());
        }

        Map<Long, int[]> frozen = new HashMap<>(mutable.size());
        mutable.forEach((key, value) -> frozen.put(key, value.toArray()));
        return new SpatialTerritoryIndex(Map.copyOf(frozen), new int[0]);
    }

    List<Integer> query(double minBlockX, double minBlockZ, double maxBlockX, double maxBlockZ) {
        int minX = (int) Math.floor(minBlockX / CELL_BLOCKS);
        int maxX = (int) Math.floor(maxBlockX / CELL_BLOCKS);
        int minZ = (int) Math.floor(minBlockZ / CELL_BLOCKS);
        int maxZ = (int) Math.floor(maxBlockZ / CELL_BLOCKS);

        Set<Integer> unique = new HashSet<>();
        for (int id : globalTerritories) {
            unique.add(id);
        }
        for (int z = minZ; z <= maxZ; z++) {
            for (int x = minX; x <= maxX; x++) {
                int[] ids = cells.get(ChunkCoordinate.pack(x, z));
                if (ids == null) {
                    continue;
                }
                for (int id : ids) {
                    unique.add(id);
                }
            }
        }
        List<Integer> result = new ArrayList<>(unique);
        result.sort(Integer::compareTo);
        return result;
    }

    private static final class IntAccumulator {
        private int[] values = new int[8];
        private int size;

        void add(int value) {
            if (size == values.length) {
                values = Arrays.copyOf(values, values.length * 2);
            }
            values[size++] = value;
        }

        int[] toArray() {
            return Arrays.copyOf(values, size);
        }
    }
}
