package me.fond.nodesoverlay.data;

import java.util.Arrays;

public final class ChunkTerritoryIndex {

    private static final int EMPTY = -1;

    private final long[] keys;
    private final int[] values;
    private final int mask;
    private int size;

    public ChunkTerritoryIndex(int expectedEntries) {
        int capacity = 16;
        int needed = Math.max(1, expectedEntries);
        while (capacity < needed / 0.62) {
            capacity <<= 1;
        }
        keys = new long[capacity];
        values = new int[capacity];
        Arrays.fill(values, EMPTY);
        mask = capacity - 1;
    }

    /**
     * @return the previous territory ID, or {@code -1} when the chunk was unused.
     */
    public int put(long key, int territoryId) {
        int slot = slot(key);
        while (values[slot] != EMPTY) {
            if (keys[slot] == key) {
                int previous = values[slot];
                values[slot] = territoryId;
                return previous;
            }
            slot = (slot + 1) & mask;
        }
        keys[slot] = key;
        values[slot] = territoryId;
        size++;
        return EMPTY;
    }

    public int get(long key) {
        int slot = slot(key);
        while (values[slot] != EMPTY) {
            if (keys[slot] == key) {
                return values[slot];
            }
            slot = (slot + 1) & mask;
        }
        return EMPTY;
    }

    public int size() {
        return size;
    }

    private int slot(long value) {
        long z = value;
        z = (z ^ (z >>> 33)) * 0xff51afd7ed558ccdl;
        z = (z ^ (z >>> 33)) * 0xc4ceb9fe1a85ec53l;
        z ^= z >>> 33;
        return (int) z & mask;
    }
}
