package me.fond.nodesoverlay;

import me.fond.nodesoverlay.model.ChunkCoordinate;
import xaero.hud.minimap.BuiltInHudModules;
import xaero.map.MapProcessor;
import xaero.map.WorldMapSession;
import xaero.map.region.LeveledRegion;
import xaero.map.region.MapRegion;
import xaero.map.world.MapDimension;
import xaero.map.world.MapWorld;

import java.util.List;

/**
 * Cache refreshes must happen on the client thread because Xaero owns these
 * processors. A missing Xaero session is normal while connecting/disconnecting.
 */
final class XaeroCacheInvalidation {

    private XaeroCacheInvalidation() {
    }

    // Called once at the end of the client tick, after queued war messages.
    static void invalidate(HighlightVersions.Update update) {
        try {
            WorldMapSession worldMap = WorldMapSession.getCurrentSession();
            if (worldMap != null
                    && worldMap.getMapProcessor() != null
                    && worldMap.getMapProcessor().getMapWorld() != null) {
                MapProcessor processor = worldMap.getMapProcessor();
                MapWorld mapWorld = processor.getMapWorld();
                if (update.all()) {
                    mapWorld.clearAllCachedHighlightHashes();
                    invalidateLoadedWorldMapRegions(mapWorld);
                } else {
                    // Xaero propagates each cleared hash into its parent branches
                    // and minimap bridge without forcing a terrain-region reload.
                    for (MapDimension dimension : mapWorld.getDimensionsList()) {
                        for (long region : update.regions()) {
                            dimension.getHighlightHandler().clearCachedHash(
                                    ChunkCoordinate.unpackX(region), ChunkCoordinate.unpackZ(region));
                        }
                        dimension.getLayeredMapRegions().applyToEachLoadedLayer((layerId, layer) -> {
                            for (long region : update.regions()) {
                                invalidateLoadedRegion(layer.getMapRegions().getLeaf(
                                        ChunkCoordinate.unpackX(region), ChunkCoordinate.unpackZ(region)));
                            }
                        });
                    }
                }
            }
        } catch (RuntimeException ignored) {
            // Xaero can be between sessions during a server transition.
        }
        try {
            var minimap = BuiltInHudModules.MINIMAP.getCurrentSession();
            if (minimap != null
                    && minimap.getProcessor() != null
                    && minimap.getProcessor().getMinimapWriter() != null) {
                var handler = minimap.getProcessor().getMinimapWriter().getDimensionHighlightHandler();
                if (handler != null) {
                    if (update.all()) {
                        handler.requestRefresh();
                    } else {
                        for (long region : update.regions()) {
                            handler.requestRefresh(ChunkCoordinate.unpackX(region), ChunkCoordinate.unpackZ(region));
                        }
                    }
                }
            }
        } catch (RuntimeException ignored) {
            // Xaero can be between sessions during a server transition.
        }
    }

    /**
     * Xaero clearing its region-hash cache does not always enqueue terrain
     * regions that were already loaded before map data became available.
     * Those regions can retain an empty highlight texture while marker labels
     * use the new snapshot. Refresh only loaded leaf regions whose rendered
     * hash differs from the newly calculated target hash.
     *
     * Do not call {@link MapRegion#requestRefresh(MapProcessor, boolean)} here.
     * That marks the leaf region as refreshing immediately. During a proxy
     * shard transfer, Xaero can unload the old dimension before its map worker
     * consumes that request, which leaves a non-loaded region marked as
     * refreshing and causes Xaero to crash while finalizing the dimension.
     * Invalidating the hash and its parent branch lets Xaero schedule the work
     * through its normal load-state-aware update path instead.
     */
    private static void invalidateLoadedWorldMapRegions(MapWorld mapWorld) {
        MapDimension dimension = mapWorld.getCurrentDimension();
        if (dimension == null) {
            return;
        }

        List<LeveledRegion<?>> loadedRegions = List.copyOf(
                dimension.getLayeredMapRegions().getLoadedListUnsynced()
        );
        for (LeveledRegion<?> loadedRegion : loadedRegions) {
            if (!(loadedRegion instanceof MapRegion region)) {
                continue;
            }
            invalidateLoadedRegion(region);
        }
    }


    private static void invalidateLoadedRegion(MapRegion region) {
        if (region == null || !region.isLoaded()) {
            return;
        }
        region.updateTargetHighlightsHash();
        if (region.getHighlightsHash() != region.getTargetHighlightsHash()) {
            region.setHighlightsHash(0);
            if (region.getParent() != null) {
                region.getParent().setShouldCheckForUpdatesRecursive(true);
            }
        }
    }
}
