package me.fond.nodesoverlay;

import me.fond.nodesoverlay.data.NodesOverlayDataParser;
import me.fond.nodesoverlay.data.NodesOverlaySnapshot;
import me.fond.nodesoverlay.model.ChunkCoordinate;
import me.fond.nodesoverlay.war.WarMessage;
import me.fond.nodesoverlay.war.WarTracker;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class HighlightVersionsTest {
    @Test
    void swordLifecycleDoesNotInvalidatePixels() {
        HighlightVersions versions = new HighlightVersions();
        WarTracker tracker = new WarTracker();
        NodesOverlaySnapshot snapshot = NodesOverlaySnapshot.empty();
        versions.invalidateWarEvent(tracker.apply(
                new WarMessage.Attack("Player", "Town", -16, 70, 512), snapshot, "Player"), snapshot);
        versions.invalidateWarEvent(tracker.apply(
                new WarMessage.AttackDefended("Player", -16, 70, 512), snapshot, "Player"), snapshot);
        assertNull(versions.flush());
        assertEquals(0, versions.current().revision());
    }

    @Test
    void batchesWarOutcomesAndPreservesDistantRegionVersions() {
        HighlightVersions versions = new HighlightVersions();
        WarTracker tracker = new WarTracker();
        NodesOverlaySnapshot snapshot = NodesOverlaySnapshot.empty();
        var original = versions.current();
        versions.invalidateWarEvent(tracker.apply(
                new WarMessage.ChunkCaptured("Player", "Town", -1, 32), snapshot, "Player"), snapshot);
        versions.invalidateWarEvent(tracker.apply(
                new WarMessage.ChunkLiberated("Player", "Town", -2, 33), snapshot, "Player"), snapshot);
        assertSame(original, versions.current());
        var update = versions.flush();
        assertFalse(update.all());
        assertEquals(Set.of(ChunkCoordinate.pack(-1, 1)), update.regions());
        assertEquals(1, update.view().region(ChunkCoordinate.pack(-1, 1)));
        assertEquals(0, update.view().region(ChunkCoordinate.pack(5, 5)));
        assertEquals(0, original.region(ChunkCoordinate.pack(-1, 1)));
        assertNull(versions.flush());
        versions.invalidateAll();
        versions.invalidateRegions(Set.of(ChunkCoordinate.pack(4, 4)));
        var full = versions.flush();
        assertTrue(full.all());
        assertTrue(full.view().regions().isEmpty());
        assertEquals(2, full.view().region(ChunkCoordinate.pack(5, 5)));
    }

    @Test
    void territoryOutcomesInvalidateEveryDisconnectedRegion() {
        NodesOverlaySnapshot snapshot = new NodesOverlayDataParser().parse(
                "{\"meta\":{\"type\":\"towns\"},\"towns\":{},\"nations\":{},\"residents\":{}}",
                """
                {"meta":{"type":"world"},"nodes":{},"territories":{
                  "1":{"core":[8,8],"coreChunk":[0,0],"chunks":[0,0,32,0,-1,-33],"nodes":[]}
                }}
                """);
        WarTracker tracker = new WarTracker();
        HighlightVersions versions = new HighlightVersions();
        versions.invalidateWarEvent(tracker.apply(
                new WarMessage.TerritoryCaptured("Player", "Town", 1), snapshot, "Player"), snapshot);
        Set<Long> expected = Set.of(ChunkCoordinate.pack(0, 0), ChunkCoordinate.pack(1, 0),
                ChunkCoordinate.pack(-1, -2));
        assertEquals(expected, versions.flush().regions());
        versions.invalidateWarEvent(tracker.apply(
                new WarMessage.TerritoryLiberated("Player", "Town", 1), snapshot, "Player"), snapshot);
        assertEquals(expected, versions.flush().regions());
    }
}
