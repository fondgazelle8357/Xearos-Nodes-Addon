package me.fond.nodesoverlay.integration.xaero;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class ChunkColorCacheTest {

    @Test
    void updatingOneRegionDoesNotDiscardOtherRegionsOrAllowAnOldWriterToReplaceNewPixels() {
        ChunkColorCache cache = new ChunkColorCache();
        int[] nearby = new int[256];
        int[] distant = new int[256];
        int[] updated = new int[256];
        cache.putIfAbsent(1, 10, nearby);
        cache.putIfAbsent(1, 20, distant);
        cache.putIfAbsent(2, 10, updated);
        assertSame(distant, cache.get(1, 20));
        assertSame(updated, cache.get(2, 10));
        assertSame(nearby, cache.putIfAbsent(1, 10, nearby));
        assertSame(updated, cache.get(2, 10));
    }

    @Test
    void returnsCachedPixelsUntilRuntimeRevisionChanges() {
        ChunkColorCache cache = new ChunkColorCache();
        int[] first = new int[256];

        assertNull(cache.get(10L, 25L));
        assertSame(first, cache.putIfAbsent(10L, 25L, first));
        assertSame(first, cache.get(10L, 25L));
        assertNull(cache.get(11L, 25L));
    }
}
