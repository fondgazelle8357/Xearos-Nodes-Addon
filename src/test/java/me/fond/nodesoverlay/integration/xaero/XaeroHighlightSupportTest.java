package me.fond.nodesoverlay.integration.xaero;

import me.fond.nodesoverlay.data.NodesOverlayDataParser;
import me.fond.nodesoverlay.data.NodesOverlaySnapshot;
import me.fond.nodesoverlay.model.ChunkCoordinate;
import me.fond.nodesoverlay.model.RgbColor;
import me.fond.nodesoverlay.war.WarEvent;
import me.fond.nodesoverlay.war.WarMarkerType;
import me.fond.nodesoverlay.war.WarRelation;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XaeroHighlightSupportTest {

    @Test
    void regionalInvalidationUpdatesLiberatedPixelsAndRetainsDistantCacheEntries() {
        NodesOverlaySnapshot snapshot = snapshot();
        var style = XaeroOverlayRuntime.OverlayStyle.from(new me.fond.nodesoverlay.config.ServerSettings(), true);
        var versions = new me.fond.nodesoverlay.HighlightVersions();
        versions.invalidateAll();
        var initial = versions.flush().view();
        var before = XaeroOverlayRuntime.State.create(snapshot, style, initial.revision(), List.of(), null, initial);
        ChunkColorCache cache = new ChunkColorCache();
        long localChunk = ChunkCoordinate.pack(0, 0);
        long distantChunk = ChunkCoordinate.pack(320, 320);
        int[] oldPixels = XaeroHighlightSupport.territoryOverlayColors(before, 0, 0, new int[256]);
        int[] distantPixels = new int[256];
        cache.putIfAbsent(before.regionRevision(0, 0), localChunk, oldPixels);
        cache.putIfAbsent(before.regionRevision(10, 10), distantChunk, distantPixels);
        WarEvent liberation = occupationEvent(WarEvent.Kind.CHUNK_LIBERATED, "liberated:0:0",
                0, 0, RgbColor.NEUTRAL, Instant.now());
        versions.invalidateWarEvent(liberation, snapshot);
        var updated = versions.flush().view();
        var after = XaeroOverlayRuntime.State.create(snapshot, style, updated.revision(),
                List.of(liberation), before, updated);
        assertNull(cache.get(after.regionRevision(0, 0), localChunk));
        assertSame(distantPixels, cache.get(after.regionRevision(10, 10), distantChunk));
        assertEquals(before.regionHash(10, 10), after.regionHash(10, 10));
        org.junit.jupiter.api.Assertions.assertNotEquals(before.regionHash(0, 0), after.regionHash(0, 0));
        int[] liberatedPixels = XaeroHighlightSupport.territoryOverlayColors(after, 0, 0, new int[256]);
        int[] basePixels = XaeroHighlightSupport.territoryBaseColors(after, 0, 0, new int[256]);
        org.junit.jupiter.api.Assertions.assertArrayEquals(basePixels, liberatedPixels);
        org.junit.jupiter.api.Assertions.assertNotEquals(oldPixels[index(15, 6)], liberatedPixels[index(15, 6)]);
        assertTrue(after.isOccupiedChunk(1, 0), "Liberation must not remove the neighboring chunk's stripe");
    }

    @Test
    void matchesOwnerBaseAndOccupierStripeColorsAcrossChunkSeam() {
        NodesOverlaySnapshot snapshot = snapshot();
        XaeroOverlayRuntime.OverlayStyle style = new XaeroOverlayRuntime.OverlayStyle(
                true,
                true,
                58,
                210,
                105,
                2,
                185,
                7,
                2,
                RgbColor.NEUTRAL,
                new RgbColor(24, 16, 16),
                Set.of()
        );
        XaeroOverlayRuntime.State state = XaeroOverlayRuntime.State.create(snapshot, style, 1L);

        int[] base = new int[256];
        XaeroHighlightSupport.territoryBaseColors(state, 0, 0, base);
        assertEquals(xaero(24, 16, 16, 210), base[index(0, 6)]);
        assertEquals(xaero(42, 118, 129, 58), base[index(15, 6)]);

        int[] leftChunk = new int[256];
        int[] rightChunk = new int[256];
        XaeroHighlightSupport.occupationStripeColors(state, 0, 0, leftChunk);
        XaeroHighlightSupport.occupationStripeColors(state, 1, 0, rightChunk);

        int romeStripe = xaero(153, 0, 0, 185);
        // Global X+Z values 21 and 22 are the two-pixel "/" band on
        // either side of the chunk seam. The pattern must not restart.
        assertEquals(romeStripe, leftChunk[index(15, 6)]);
        assertEquals(romeStripe, rightChunk[index(0, 6)]);
        assertEquals(0, leftChunk[index(14, 6)]);
        assertEquals(0, rightChunk[index(1, 6)]);

        // Configured two-block outer borders remain unobscured.
        assertEquals(0, leftChunk[index(0, 6)]);
        assertEquals(0, leftChunk[index(1, 6)]);
        assertEquals(0, rightChunk[index(14, 6)]);
        assertEquals(0, rightChunk[index(15, 6)]);

        int[] combined = new int[256];
        XaeroHighlightSupport.territoryOverlayColors(state, 0, 0, combined);
        assertEquals(base[index(14, 6)], combined[index(14, 6)]);
        assertEquals(
                composite(base[index(15, 6)], romeStripe),
                combined[index(15, 6)]
        );
    }

    @Test
    void liveChunkLiberationSuppressesOnlyThatChunksStaleOccupierStripe() {
        NodesOverlaySnapshot snapshot = snapshot();
        WarEvent liberation = new WarEvent(
                "chunk-liberated:0:0",
                WarEvent.Kind.CHUNK_LIBERATED,
                "Liberator",
                "NorthEntea",
                "Entea",
                "Roma",
                4759,
                8,
                128,
                8,
                0,
                0,
                WarRelation.HOSTILE,
                WarMarkerType.SHIELD,
                new RgbColor(42, 118, 129),
                Instant.now()
        );
        XaeroOverlayRuntime.State state = XaeroOverlayRuntime.State.create(
                snapshot,
                style(58, Set.of()),
                2L,
                List.of(liberation)
        );

        assertNull(state.occupationColor(snapshot.territory(4759), 0, 0));
        assertEquals(
                new RgbColor(153, 0, 0),
                state.occupationColor(snapshot.territory(4759), 1, 0)
        );

        int[] liberatedChunk = new int[256];
        int[] stillOccupiedChunk = new int[256];
        assertNull(XaeroHighlightSupport.occupationStripeColors(
                state,
                0,
                0,
                liberatedChunk
        ));
        XaeroHighlightSupport.occupationStripeColors(
                state,
                1,
                0,
                stillOccupiedChunk
        );
        assertTrue(java.util.Arrays.stream(stillOccupiedChunk)
                .anyMatch(color -> color != 0));
    }

    @Test
    void exactEnemyChunkCapturePreservesDifferentDownloadedOccupation() {
        NodesOverlaySnapshot snapshot = snapshot();
        RgbColor liveColor = new RgbColor(20, 200, 20);
        WarEvent capture = new WarEvent(
                "chunk:0:0",
                WarEvent.Kind.CHUNK_CAPTURE,
                "Attacker",
                "AttackTown",
                "AttackNation",
                "NorthEntea",
                4759,
                8,
                128,
                8,
                0,
                0,
                WarRelation.HOSTILE,
                WarMarkerType.FLAG,
                liveColor,
                Instant.now()
        );
        XaeroOverlayRuntime.State state = XaeroOverlayRuntime.State.create(
                snapshot,
                style(58, Set.of()),
                3L,
                List.of(capture)
        );

        assertTrue(snapshot.territory(4759).occupied());
        assertEquals(liveColor, state.occupationColor(snapshot.territory(4759), 0, 0));
        assertEquals(
                new RgbColor(153, 0, 0),
                state.occupationColor(snapshot.territory(4759), 1, 0)
        );

        int[] capturedChunk = new int[256];
        int[] previouslyOccupiedChunk = new int[256];
        assertNotNull(XaeroHighlightSupport.occupationStripeColors(
                state,
                0,
                0,
                capturedChunk
        ));
        assertNotNull(XaeroHighlightSupport.occupationStripeColors(
                state,
                1,
                0,
                previouslyOccupiedChunk
        ));
    }

    @Test
    void exactEnemyChunkCaptureStaysAboveLaterTerritoryInfoBaseline() {
        NodesOverlaySnapshot snapshot = snapshot();
        RgbColor existingOccupierColor = new RgbColor(20, 200, 20);
        RgbColor enemyColor = new RgbColor(200, 20, 20);
        Instant now = Instant.now();
        WarEvent existingOccupation = occupationEvent(
                WarEvent.Kind.TERRITORY_CAPTURE,
                "territory:4759",
                0,
                0,
                existingOccupierColor,
                now
        );
        WarEvent enemyChunkCapture = occupationEvent(
                WarEvent.Kind.CHUNK_CAPTURE,
                "chunk:0:0",
                0,
                0,
                enemyColor,
                now.minusSeconds(1)
        );

        XaeroOverlayRuntime.State state = XaeroOverlayRuntime.State.create(
                snapshot,
                style(58, Set.of()),
                4L,
                List.of(existingOccupation, enemyChunkCapture)
        );

        assertEquals(enemyColor, state.occupationColor(snapshot.territory(4759), 0, 0));
        assertEquals(
                existingOccupierColor,
                state.occupationColor(snapshot.territory(4759), 1, 0)
        );
    }

    @Test
    void liveTerritoryOutcomeOverridesDifferentDownloadedOccupation() {
        NodesOverlaySnapshot snapshot = snapshot();
        RgbColor liveColor = new RgbColor(20, 200, 20);
        WarEvent capture = occupationEvent(
                WarEvent.Kind.TERRITORY_CAPTURE,
                "territory:4759",
                0,
                0,
                liveColor,
                Instant.now()
        );
        XaeroOverlayRuntime.State captured = XaeroOverlayRuntime.State.create(
                snapshot,
                style(58, Set.of()),
                4L,
                List.of(capture)
        );

        assertEquals(liveColor, captured.occupationColor(snapshot.territory(4759), 0, 0));
        assertEquals(liveColor, captured.occupationColor(snapshot.territory(4759), 1, 0));

        WarEvent liberation = occupationEvent(
                WarEvent.Kind.TERRITORY_LIBERATED,
                "territory-liberated:4759",
                0,
                0,
                liveColor,
                Instant.now()
        );
        XaeroOverlayRuntime.State liberated = XaeroOverlayRuntime.State.create(
                snapshot,
                style(58, Set.of()),
                5L,
                List.of(liberation)
        );

        assertNull(liberated.occupationColor(snapshot.territory(4759), 0, 0));
        assertNull(liberated.occupationColor(snapshot.territory(4759), 1, 0));
    }

    @Test
    void exactChunkOutcomeOverridesEarlierLiveTerritoryOutcome() {
        NodesOverlaySnapshot snapshot = snapshot();
        RgbColor liveColor = new RgbColor(20, 200, 20);
        Instant now = Instant.now();
        WarEvent territoryCapture = occupationEvent(
                WarEvent.Kind.TERRITORY_CAPTURE,
                "territory:4759",
                0,
                0,
                liveColor,
                now.minusSeconds(1)
        );
        WarEvent chunkLiberation = occupationEvent(
                WarEvent.Kind.CHUNK_LIBERATED,
                "chunk-liberated:0:0",
                0,
                0,
                liveColor,
                now
        );
        XaeroOverlayRuntime.State mostlyCaptured = XaeroOverlayRuntime.State.create(
                snapshot,
                style(58, Set.of()),
                6L,
                List.of(chunkLiberation, territoryCapture)
        );

        assertNull(mostlyCaptured.occupationColor(snapshot.territory(4759), 0, 0));
        assertEquals(liveColor, mostlyCaptured.occupationColor(snapshot.territory(4759), 1, 0));

        WarEvent territoryLiberation = occupationEvent(
                WarEvent.Kind.TERRITORY_LIBERATED,
                "territory-liberated:4759",
                0,
                0,
                liveColor,
                now.minusSeconds(1)
        );
        WarEvent chunkCapture = occupationEvent(
                WarEvent.Kind.CHUNK_CAPTURE,
                "chunk:0:0",
                0,
                0,
                liveColor,
                now
        );
        XaeroOverlayRuntime.State mostlyLiberated = XaeroOverlayRuntime.State.create(
                snapshot,
                style(58, Set.of()),
                7L,
                List.of(chunkCapture, territoryLiberation)
        );

        assertEquals(liveColor, mostlyLiberated.occupationColor(snapshot.territory(4759), 0, 0));
        assertNull(mostlyLiberated.occupationColor(snapshot.territory(4759), 1, 0));
    }

    @Test
    void reusesSnapshotRegionIndexesAcrossNonVisibilityRevisions() {
        NodesOverlaySnapshot snapshot = snapshot();
        XaeroOverlayRuntime.OverlayStyle originalStyle = style(58, Set.of());
        XaeroOverlayRuntime.State first = XaeroOverlayRuntime.State.create(
                snapshot,
                originalStyle,
                1L
        );

        XaeroOverlayRuntime.State warOrOpacityRevision = XaeroOverlayRuntime.State.create(
                snapshot,
                style(90, Set.of()),
                2L,
                List.of(),
                first
        );

        assertSame(first.populatedRegions(), warOrOpacityRevision.populatedRegions());
        assertSame(first.snapshotOccupiedRegions(), warOrOpacityRevision.snapshotOccupiedRegions());

        XaeroOverlayRuntime.State hiddenTerritoryRevision = XaeroOverlayRuntime.State.create(
                snapshot,
                style(90, Set.of(4759)),
                3L,
                List.of(),
                warOrOpacityRevision
        );

        assertNotSame(warOrOpacityRevision.populatedRegions(), hiddenTerritoryRevision.populatedRegions());
        assertEquals(Set.of(), hiddenTerritoryRevision.populatedRegions());
    }

    @Test
    void findsTerritoriesInAsymmetricXaeroRegionCoordinates() {
        NodesOverlaySnapshot snapshot = new NodesOverlayDataParser().parse(
                """
                {
                  "meta": {"type": "towns"},
                  "residents": {},
                  "towns": {},
                  "nations": {}
                }
                """,
                """
                {
                  "meta": {"type": "world"},
                  "nodes": {},
                  "territories": {
                    "99": {
                      "name": "",
                      "core": [5640, 3080],
                      "coreChunk": [352, 192],
                      "chunks": [352, 192],
                      "size": 1,
                      "neighbors": [],
                      "isEdge": false,
                      "nodes": [],
                      "color": 0
                    }
                  }
                }
                """
        );
        XaeroOverlayRuntime.State state = XaeroOverlayRuntime.State.create(
                snapshot,
                style(90, Set.of()),
                7L
        );

        // Chunk (352,192) is Xaero region (11,6). A swapped x/z packed key
        // incorrectly reports this valid region as empty.
        assertEquals(ChunkCoordinate.pack(11, 6), XaeroOverlayRuntime.packRegion(11, 6));
        assertTrue(state.hasTerritoriesInRegion(11, 6));
        assertFalse(state.hasTerritoriesInRegion(6, 11));
    }

    @Test
    void makesTownCoreOutlinesOneAndAHalfTimesMoreOpaque() {
        NodesOverlaySnapshot snapshot = new NodesOverlayDataParser().parse(
                """
                {
                  "meta": {"type": "towns"},
                  "residents": {},
                  "towns": {
                    "TestTown": {
                      "color": [60, 70, 80],
                      "territories": [1, 2],
                      "annexed": [],
                      "captured": [],
                      "claimed": [],
                      "cores": [1],
                      "sphered": []
                    }
                  },
                  "nations": {}
                }
                """,
                """
                {
                  "meta": {"type": "world"},
                  "nodes": {},
                  "territories": {
                    "1": {
                      "name": "",
                      "core": [8, 8],
                      "coreChunk": [0, 0],
                      "chunks": [0, 0],
                      "size": 1,
                      "neighbors": [2],
                      "isEdge": false,
                      "nodes": [],
                      "color": 0
                    },
                    "2": {
                      "name": "",
                      "core": [24, 8],
                      "coreChunk": [1, 0],
                      "chunks": [1, 0],
                      "size": 1,
                      "neighbors": [1],
                      "isEdge": false,
                      "nodes": [],
                      "color": 0
                    }
                  }
                }
                """
        );
        XaeroOverlayRuntime.OverlayStyle style = new XaeroOverlayRuntime.OverlayStyle(
                true,
                true,
                40,
                100,
                80,
                2,
                185,
                7,
                2,
                RgbColor.NEUTRAL,
                new RgbColor(24, 16, 16),
                Set.of()
        );
        XaeroOverlayRuntime.State state = XaeroOverlayRuntime.State.create(snapshot, style, 8L);

        int[] coreTerritory = new int[256];
        int[] normalTerritory = new int[256];
        XaeroHighlightSupport.territoryBaseColors(state, 0, 0, coreTerritory);
        XaeroHighlightSupport.territoryBaseColors(state, 1, 0, normalTerritory);

        assertEquals(150, coreTerritory[index(0, 8)] & 255);
        assertEquals(120, coreTerritory[index(15, 8)] & 255);
        assertEquals(80, normalTerritory[index(0, 8)] & 255);
        assertEquals(100, normalTerritory[index(15, 8)] & 255);
        assertEquals(40, coreTerritory[index(8, 8)] & 255);
    }

    private static XaeroOverlayRuntime.OverlayStyle style(int fillAlpha, Set<Integer> hiddenTerritories) {
        return new XaeroOverlayRuntime.OverlayStyle(
                true,
                true,
                fillAlpha,
                210,
                105,
                2,
                185,
                7,
                2,
                RgbColor.NEUTRAL,
                new RgbColor(24, 16, 16),
                hiddenTerritories
        );
    }

    private static WarEvent occupationEvent(
            WarEvent.Kind kind,
            String key,
            int chunkX,
            int chunkZ,
            RgbColor color,
            Instant updatedAt
    ) {
        WarMarkerType marker = kind == WarEvent.Kind.CHUNK_CAPTURE
                || kind == WarEvent.Kind.TERRITORY_CAPTURE
                ? WarMarkerType.FLAG
                : WarMarkerType.SHIELD;
        return new WarEvent(
                key,
                kind,
                "Attacker",
                "AttackTown",
                "AttackNation",
                "NorthEntea",
                4759,
                chunkX * 16 + 8,
                128,
                chunkZ * 16 + 8,
                chunkX,
                chunkZ,
                WarRelation.HOSTILE,
                marker,
                color,
                updatedAt
        );
    }

    private static int index(int localX, int localZ) {
        return localZ * 16 + localX;
    }

    private static int xaero(int red, int green, int blue, int alpha) {
        return blue << 24 | green << 16 | red << 8 | alpha;
    }

    private static int composite(int under, int over) {
        int underAlpha = under & 255;
        int overAlpha = over & 255;
        int inverseOver = 255 - overAlpha;
        int outAlpha = overAlpha + (underAlpha * inverseOver + 127) / 255;
        int denominator = outAlpha * 255;
        int red = channel(
                under >>> 8 & 255,
                underAlpha,
                over >>> 8 & 255,
                overAlpha,
                inverseOver,
                denominator
        );
        int green = channel(
                under >>> 16 & 255,
                underAlpha,
                over >>> 16 & 255,
                overAlpha,
                inverseOver,
                denominator
        );
        int blue = channel(
                under >>> 24 & 255,
                underAlpha,
                over >>> 24 & 255,
                overAlpha,
                inverseOver,
                denominator
        );
        return blue << 24 | green << 16 | red << 8 | outAlpha;
    }

    private static int channel(
            int under,
            int underAlpha,
            int over,
            int overAlpha,
            int inverseOver,
            int denominator
    ) {
        int numerator = over * overAlpha * 255
                + under * underAlpha * inverseOver;
        return (numerator + denominator / 2) / denominator;
    }

    private static NodesOverlaySnapshot snapshot() {
        String towns = """
                {
                  "meta": {"type": "towns"},
                  "residents": {},
                  "towns": {
                    "NorthEntea": {
                      "color": [1, 2, 3],
                      "territories": [4759],
                      "annexed": [],
                      "captured": [],
                      "claimed": [],
                      "cores": [],
                      "sphered": []
                    },
                    "Roma": {
                      "color": [4, 5, 6],
                      "territories": [4759],
                      "annexed": [],
                      "captured": [4759],
                      "claimed": [],
                      "cores": [],
                      "sphered": []
                    }
                  },
                  "nations": {
                    "Entea": {
                      "color": [42, 118, 129],
                      "towns": ["NorthEntea"],
                      "allies": [],
                      "enemies": []
                    },
                    "Rome": {
                      "color": [153, 0, 0],
                      "towns": ["Roma"],
                      "allies": [],
                      "enemies": []
                    }
                  }
                }
                """;
        String world = """
                {
                  "meta": {"type": "world"},
                  "nodes": {},
                  "territories": {
                    "4759": {
                      "name": "",
                      "core": [8, 8],
                      "coreChunk": [0, 0],
                      "chunks": [0, 0, 1, 0],
                      "size": 2,
                      "neighbors": [],
                      "isEdge": false,
                      "nodes": [],
                      "color": 0
                    }
                  }
                }
                """;
        return new NodesOverlayDataParser().parse(towns, world);
    }
}
