package me.fond.nodesoverlay.data;

import me.fond.nodesoverlay.model.OwnershipState;
import me.fond.nodesoverlay.model.RgbColor;
import me.fond.nodesoverlay.model.TerritoryView;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class NodesOverlayDataParserTest {

    private final NodesOverlayDataParser parser = new NodesOverlayDataParser();

    @Test
    void reusesWorldGeometryAndIndexesUntilWorldContentChanges() {
        String firstTown = townsJson(town("First", "[1]"), "");
        String secondTown = townsJson(town("Second", "[1]"), "");
        String world = worldJson(territory(1, 0, 0));
        NodesOverlaySnapshot first = parser.parse(firstTown, world);
        NodesOverlaySnapshot second = parser.parse(secondTown, world, first);
        assertSame(first.territory(1).territory(), second.territory(1).territory());
        assertSame(first.visibleRegionKeys(java.util.Set.of()), second.visibleRegionKeys(java.util.Set.of()));
        assertEquals("Second", second.territoryAtChunk(0, 0).ownerTownName());
        NodesOverlaySnapshot moved = parser.parse(secondTown, worldJson(territory(1, 32, -32)), second);
        assertNotSame(second.territory(1).territory(), moved.territory(1).territory());
        assertNull(moved.territoryAtChunk(0, 0));
        assertNotNull(moved.territoryAtChunk(32, -32));
        assertTrue(moved.visibleTerritoryCores(0, 0, 16, 16).isEmpty());
        assertEquals(1, moved.visibleTerritoryCores(512, -512, 528, -496).size());
    }

    @Test
    void subtractsSpecializedStateOccurrencesBeforeResolvingOwners() {
        NodesOverlaySnapshot snapshot = parser.parse(
                townsJson("""
                        "OccupierOnly": {
                          "color": [200, 0, 0],
                          "territories": [1],
                          "annexed": [],
                          "captured": [1],
                          "claimed": [],
                          "cores": [],
                          "sphered": []
                        },
                        "Annexer": {
                          "color": [0, 200, 0],
                          "territories": [2, 2],
                          "annexed": [2],
                          "captured": [],
                          "claimed": [],
                          "cores": [],
                          "sphered": []
                        },
                        "Capital": {
                          "color": [0, 0, 200],
                          "territories": [3, 3],
                          "annexed": [],
                          "captured": [],
                          "claimed": [],
                          "cores": [3],
                          "sphered": []
                        }
                        """, ""),
                worldJson(
                        territory(1, 0, 0),
                        territory(2, 1, 0),
                        territory(3, 2, 0)
                )
        );

        assertTrue(snapshot.town("OccupierOnly").directTerritories().isEmpty());
        assertEquals(java.util.Set.of(2), snapshot.town("Annexer").directTerritories());
        assertEquals(java.util.Set.of(3), snapshot.town("Capital").directTerritories());

        TerritoryView occupied = snapshot.territory(1);
        assertEquals("OccupierOnly", occupied.owner().name());
        assertNull(occupied.occupier());
        assertEquals(OwnershipState.OWNED, occupied.state());

        TerritoryView annexed = snapshot.territory(2);
        assertEquals("Annexer", annexed.owner().name());
        assertEquals(OwnershipState.ANNEXED, annexed.state());
        assertTrue(snapshot.territory(3).townCore());
    }

    @Test
    void treatsCapturedHolderAsOwnerWhenItAlsoHasDirectOwnership() {
        NodesOverlaySnapshot snapshot = parser.parse(
                townsJson("""
                        "PreviousOwner": {
                          "color": [0, 120, 200],
                          "territories": [4],
                          "annexed": [],
                          "captured": [],
                          "claimed": [],
                          "cores": [],
                          "sphered": []
                        },
                        "NewOwner": {
                          "color": [200, 80, 0],
                          "territories": [4, 4],
                          "annexed": [],
                          "captured": [4],
                          "claimed": [],
                          "cores": [],
                          "sphered": []
                        }
                        """, ""),
                worldJson(territory(4, 0, 0))
        );

        TerritoryView view = snapshot.territory(4);
        assertEquals("NewOwner", view.owner().name());
        assertNull(view.occupier());
        assertEquals(OwnershipState.OWNED, view.state());
    }

    @Test
    void parsesDownloadedPortsIntoSnapshot() {
        String ports = """
                {
                  "meta": {"type": "ports"},
                  "ports": {
                    "rome": {"groups": ["1"], "x": -766, "z": 532}
                  }
                }
                """;
        NodesOverlaySnapshot snapshot = parser.parse(
                townsJson("", ""),
                worldJson(territory(5, 0, 0)),
                ports,
                NodesOverlaySnapshot.empty()
        );

        assertEquals("Rome", snapshot.port("ROME").name());
        assertEquals(-766, snapshot.port("rome").x());
        assertEquals(532, snapshot.port("rome").z());
        assertEquals(java.util.Set.of("1"), snapshot.port("rome").groups());
    }

    @Test
    void retainsPreviousOwnerWhenNewSnapshotRemainsAmbiguous() {
        String world = worldJson(territory(10, 0, 0));
        NodesOverlaySnapshot previous = parser.parse(
                townsJson(town("Beta", "[10]"), ""),
                world
        );

        NodesOverlaySnapshot refreshed = parser.parse(
                townsJson(town("Alpha", "[10]") + "," + town("Beta", "[10]"), ""),
                world,
                previous
        );

        TerritoryView view = refreshed.territory(10);
        assertEquals("Beta", view.owner().name());
        assertTrue(view.ownershipAmbiguous());
        assertEquals(List.of("Alpha", "Beta"), view.ownerCandidates().stream()
                .map(candidate -> candidate.name())
                .toList());

        NodesOverlaySnapshot withoutPrevious = parser.parse(
                townsJson(town("Beta", "[10]") + "," + town("Alpha", "[10]"), ""),
                world
        );
        assertNull(withoutPrevious.territory(10).owner());
        assertTrue(withoutPrevious.territory(10).ownershipAmbiguous());
    }

    @Test
    void indexesXaeroRegionsUsingFloorDivisionForNegativeChunks() {
        NodesOverlaySnapshot snapshot = parser.parse(
                townsJson("", ""),
                worldJson("""
                        "20": {
                          "name": "",
                          "core": [-1, -1],
                          "coreChunk": [-1, -1],
                          "chunks": [-1, -1, 31, 31, 32, 32, -33, 0],
                          "size": 4,
                          "neighbors": [],
                          "isEdge": false,
                          "nodes": [],
                          "color": 0
                        }
                        """)
        );

        assertTrue(snapshot.regionHasTerritories(-1, -1));
        assertTrue(snapshot.regionHasTerritories(0, 0));
        assertTrue(snapshot.hasTerritoriesInRegion(1, 1));
        assertTrue(snapshot.regionHasTerritories(-2, 0));
        assertFalse(snapshot.regionHasTerritories(0, -1));
        assertFalse(snapshot.regionHasTerritories(2, 2));
    }

    @Test
    void markerQueryUsesCorePositionInsteadOfDisconnectedTerritoryFootprint() {
        NodesOverlaySnapshot snapshot = parser.parse(
                townsJson("", ""),
                worldJson("""
                        "21": {
                          "name": "",
                          "core": [16008, 16008],
                          "coreChunk": [1000, 1000],
                          "chunks": [0, 0, 1000, 1000],
                          "size": 2,
                          "neighbors": [],
                          "isEdge": false,
                          "nodes": [],
                          "color": 0
                        }
                        """)
        );

        assertEquals(1, snapshot.visibleTerritories(-64, -64, 64, 64).size());
        assertTrue(snapshot.visibleTerritoryCores(-64, -64, 64, 64).isEmpty());
        assertEquals(
                List.of(snapshot.territory(21)),
                snapshot.visibleTerritoryCores(15900, 15900, 16100, 16100)
        );
    }

    @Test
    void keepsOtherTerritoriesWhenOneNodeHasAnInvalidCoreChunk() {
        NodesOverlaySnapshot snapshot = parser.parse(
                townsJson("", ""),
                worldJson("""
                        "20": {
                          "name": "Broken node",
                          "core": [1608, 1608],
                          "coreChunk": [99, 99],
                          "chunks": [100, 100],
                          "size": 1,
                          "neighbors": [],
                          "isEdge": false,
                          "nodes": [],
                          "color": 0
                        },
                        "21": {
                          "name": "Valid node",
                          "core": [8, 8],
                          "coreChunk": [0, 0],
                          "chunks": [0, 0],
                          "size": 1,
                          "neighbors": [],
                          "isEdge": false,
                          "nodes": [],
                          "color": 0
                        }
                        """)
        );

        assertNotNull(snapshot.territory(20));
        assertTrue(snapshot.territory(20).territory().hasDataIssues());
        assertTrue(snapshot.territory(20).territory().dataIssues().contains("core chunk"));
        assertNotNull(snapshot.territory(21));
        assertFalse(snapshot.territory(21).territory().hasDataIssues());
    }

    @Test
    void resolvesReferenceTerritory4759FromLiveCachedFilesWhenAvailable() throws IOException {
        Path probe = Path.of(System.getProperty("java.io.tmpdir"), "nodesoverlay-schema-probe-20260726");
        Path towns = probe.resolve("towns-latest.json");
        Path world = probe.resolve("world.json");
        assumeTrue(Files.isRegularFile(towns) && Files.isRegularFile(world));

        NodesOverlaySnapshot snapshot = parser.parse(Files.readString(towns), Files.readString(world));
        TerritoryView view = snapshot.territory(4759);

        assertNotNull(view);
        assertEquals(-1140, view.territory().coreX());
        assertEquals(-231, view.territory().coreZ());
        assertEquals(-72, view.territory().coreChunkX());
        assertEquals(-15, view.territory().coreChunkZ());
        assertEquals(List.of("wheat"), view.territory().nodeIds());
        assertNotNull(view.owner());
        assertNotNull(view.ownerColor());
        assertEquals(view, snapshot.territoryAtChunk(-72, -15));
    }

    private static String townsJson(String towns, String nations) {
        return """
                {
                  "meta": {"type": "towns"},
                  "residents": {},
                  "towns": {%s},
                  "nations": {%s},
                  "plots": {}
                }
                """.formatted(towns, nations);
    }

    private static String town(String name, String territories) {
        return """
                "%s": {
                  "color": [100, 180, 100],
                  "territories": %s,
                  "annexed": [],
                  "captured": [],
                  "claimed": [],
                  "cores": [],
                  "sphered": []
                }
                """.formatted(name, territories);
    }

    private static String worldJson(String... territories) {
        return """
                {
                  "meta": {"type": "world"},
                  "nodes": {},
                  "territories": {%s}
                }
                """.formatted(String.join(",", territories));
    }

    private static String territory(int id, int chunkX, int chunkZ) {
        return """
                "%d": {
                  "name": "",
                  "core": [%d, %d],
                  "coreChunk": [%d, %d],
                  "chunks": [%d, %d],
                  "size": 1,
                  "neighbors": [],
                  "isEdge": false,
                  "nodes": [],
                  "color": 0
                }
                """.formatted(
                id,
                chunkX * 16 + 8,
                chunkZ * 16 + 8,
                chunkX,
                chunkZ,
                chunkX,
                chunkZ
        );
    }
}
