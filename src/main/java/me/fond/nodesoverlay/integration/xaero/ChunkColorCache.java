package me.fond.nodesoverlay.integration.xaero;

import java.util.LinkedHashMap;
import java.util.Map;

/** Bounded pixel cache with per-region versions: distant war updates retain cached chunks. */
final class ChunkColorCache {
    private static final int MAX_ENTRIES = 16_384;
    private final Map<Long, Entry> colors = new LinkedHashMap<>(256, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, Entry> eldest) {
            return size() > MAX_ENTRIES;
        }
    };

    synchronized int[] get(long expectedRevision, long chunkKey) {
        Entry entry = colors.get(chunkKey);
        return entry != null && entry.revision() == expectedRevision ? entry.pixels() : null;
    }

    synchronized int[] putIfAbsent(long expectedRevision, long chunkKey, int[] value) {
        Entry existing = colors.get(chunkKey);
        if (existing != null) {
            if (existing.revision() == expectedRevision) {
                return existing.pixels();
            }
            // An old writer may finish after a newer frame. Return its own
            // pixels without replacing the newer cache entry.
            if (existing.revision() > expectedRevision) {
                return value;
            }
        }
        colors.put(chunkKey, new Entry(expectedRevision, value));
        return value;
    }

    private record Entry(long revision, int[] pixels) { }
}
