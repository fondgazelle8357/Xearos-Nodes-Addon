package me.fond.nodesoverlay.integration.xaero;

import me.fond.nodesoverlay.config.ServerSettings;
import me.fond.nodesoverlay.data.NodesOverlayDataParser;
import me.fond.nodesoverlay.data.NodesOverlaySnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XaeroOverviewLabelTest {

    @Test
    void overviewLabelsRemainVisibleUntilDetailedLabelsTakeOver() {
        ServerSettings settings = new ServerSettings();
        settings.showNationNames = true;
        settings.showTownNames = true;
        settings.coreMarkerZoomThreshold = 0.55D;
        XaeroMarkerRenderContext.MarkerStyle style =
                XaeroMarkerRenderContext.MarkerStyle.from(settings, true);

        assertTrue(XaeroMarkerRenderContext.overviewLabelsVisible(false, 0.10D, style));
        assertTrue(XaeroMarkerRenderContext.overviewLabelsVisible(false, 0.54D, style));
        assertFalse(XaeroMarkerRenderContext.overviewLabelsVisible(false, 0.55D, style));
        assertFalse(XaeroMarkerRenderContext.overviewLabelsVisible(true, 0.10D, style));
    }

    @Test
    void overviewTownNamesUseTheirOwnSetting() {
        ServerSettings settings = new ServerSettings();
        settings.showNationNames = false;
        settings.showTownNames = true;
        settings.showOverviewTownNames = false;
        settings.coreMarkerZoomThreshold = 0.55D;

        XaeroMarkerRenderContext.MarkerStyle detailedOnly =
                XaeroMarkerRenderContext.MarkerStyle.from(settings, true);
        assertFalse(XaeroMarkerRenderContext.overviewLabelsVisible(
                false,
                0.10D,
                detailedOnly
        ));

        settings.showOverviewTownNames = true;
        XaeroMarkerRenderContext.MarkerStyle withTownOverview =
                XaeroMarkerRenderContext.MarkerStyle.from(settings, true);
        assertTrue(XaeroMarkerRenderContext.overviewLabelsVisible(
                false,
                0.10D,
                withTownOverview
        ));
    }

    @Test
    void anchorsNationAtCapitalCoreAndTownsAtTheirOwnCores() {
        NodesOverlaySnapshot snapshot = new NodesOverlayDataParser().parse(
                """
                {
                  "meta": {"type": "towns"},
                  "residents": {},
                  "towns": {
                    "TownA": {
                      "color": [120, 20, 20],
                      "territories": [1],
                      "annexed": [],
                      "captured": [],
                      "claimed": [],
                      "cores": [1],
                      "sphered": []
                    },
                    "TownB": {
                      "color": [120, 20, 20],
                      "territories": [2],
                      "annexed": [],
                      "captured": [],
                      "claimed": [],
                      "cores": [2],
                      "sphered": []
                    },
                    "Independent": {
                      "color": [20, 120, 20],
                      "territories": [3],
                      "annexed": [],
                      "captured": [],
                      "claimed": [],
                      "cores": [3],
                      "sphered": []
                    }
                  },
                  "nations": {
                    "Empire": {
                      "capital": "TownA",
                      "color": [180, 40, 40],
                      "towns": ["TownA", "TownB"],
                      "allies": [],
                      "enemies": []
                    }
                  }
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
                      "core": [168, 8],
                      "coreChunk": [10, 0],
                      "chunks": [10, 0, 11, 0, 12, 0],
                      "size": 3,
                      "neighbors": [1],
                      "isEdge": false,
                      "nodes": [],
                      "color": 0
                    },
                    "3": {
                      "name": "",
                      "core": [8, 168],
                      "coreChunk": [0, 10],
                      "chunks": [0, 10],
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
        ServerSettings settings = new ServerSettings();
        settings.showNationNames = true;
        settings.showTownNames = true;
        settings.showOverviewTownNames = true;
        XaeroMarkerRenderContext.MarkerStyle style =
                XaeroMarkerRenderContext.MarkerStyle.from(settings, true);

        List<XaeroMapMarker> markers =
                XaeroMarkerRenderContext.buildOverviewMarkers(
                        snapshot,
                        style,
                        Set.of()
                );

        XaeroMarkerRenderContext.OverviewCache cache = new XaeroMarkerRenderContext.OverviewCache();
        assertTrue(cache.get(true, snapshot, style, Set.of()).isEmpty());
        List<XaeroMapMarker> cached = cache.get(false, snapshot, style, Set.of());
        assertSame(cached, cache.get(false, snapshot, style, Set.of()));
        settings.trackedTerritories.add(1);
        assertSame(cached, cache.get(false, snapshot,
                XaeroMarkerRenderContext.MarkerStyle.from(settings, true), Set.of()));
        assertTrue(cache.get(true, snapshot, style, Set.of()).isEmpty());
        assertSame(cached, cache.get(false, snapshot, style, Set.of()));
        assertNotSame(cached, cache.get(false, snapshot, style, Set.of(1)));
        settings.showNationNames = false;
        assertTrue(cache.get(false, snapshot,
                XaeroMarkerRenderContext.MarkerStyle.from(settings, true), Set.of())
                .stream().noneMatch(marker -> marker.overviewLabel().equals("Empire")));

        assertEquals(List.of("Empire", "TownB", "Independent"), markers.stream()
                .map(XaeroMapMarker::overviewLabel)
                .toList());
        XaeroMapMarker empire = markers.getFirst();
        assertEquals(4, empire.overviewWeight());
        assertEquals(8.0D, empire.blockX());
        assertEquals(8.0D, empire.blockZ());
        XaeroMapMarker townB = markers.stream()
                .filter(marker -> marker.overviewLabel().equals("TownB"))
                .findFirst()
                .orElseThrow();
        assertEquals(168.0D, townB.blockX());
        assertEquals(8.0D, townB.blockZ());
        XaeroMapMarker independent = markers.stream()
                .filter(marker -> marker.overviewLabel().equals("Independent"))
                .findFirst()
                .orElseThrow();
        assertEquals(8.0D, independent.blockX());
        assertEquals(168.0D, independent.blockZ());

        XaeroMapMarker visibleRemainder =
                XaeroMarkerRenderContext.buildOverviewMarkers(
                                snapshot,
                                style,
                                Set.of(1)
                        ).stream()
                        .filter(marker -> marker.overviewLabel().equals("Empire"))
                        .findFirst()
                        .orElseThrow();
        assertEquals(3, visibleRemainder.overviewWeight());
        assertEquals(168.0D, visibleRemainder.blockX());
    }
}
