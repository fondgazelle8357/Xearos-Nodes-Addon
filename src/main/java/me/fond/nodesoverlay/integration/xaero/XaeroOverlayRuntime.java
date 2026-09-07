package me.fond.nodesoverlay.integration.xaero;

import me.fond.nodesoverlay.NodesOverlayRuntime;
import me.fond.nodesoverlay.HighlightVersions;
import me.fond.nodesoverlay.config.ServerSettings;
import me.fond.nodesoverlay.data.NodesOverlaySnapshot;
import me.fond.nodesoverlay.model.ChunkCoordinate;
import me.fond.nodesoverlay.model.RgbColor;
import me.fond.nodesoverlay.model.TerritoryView;
import me.fond.nodesoverlay.war.WarEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Lazily derives the immutable, Xaero-specific indexes for the snapshot
 * published by {@link NodesOverlayRuntime}. The derivation happens at most once per
 * highlight revision and is then safe for Xaero's render and writer threads.
 */
final class XaeroOverlayRuntime {

    private static final AtomicReference<State> STATE =
            new AtomicReference<>(State.empty());

    private XaeroOverlayRuntime() {
    }

    static State state() {
        NodesOverlaySnapshot snapshot = NodesOverlayRuntime.snapshot();
        HighlightVersions.View versions = NodesOverlayRuntime.highlightVersions();
        long revision = versions.revision();
        State current = STATE.get();
        if (current.sourceRevision() == revision && current.snapshot() == snapshot) {
            return current;
        }

        while (true) {
            current = STATE.get();
            if (current.sourceRevision() == revision && current.snapshot() == snapshot) {
                return current;
            }

            OverlayStyle style = OverlayStyle.from(
                    NodesOverlayRuntime.settings(),
                    NodesOverlayRuntime.isActive()
            );
            State updated = State.create(
                    snapshot,
                    style,
                    revision,
                    NodesOverlayRuntime.warTracker().activeOccupationEvents(),
                    current,
                    versions
            );
            if (STATE.compareAndSet(current, updated)) {
                return updated;
            }

            snapshot = NodesOverlayRuntime.snapshot();
            versions = NodesOverlayRuntime.highlightVersions();
            revision = versions.revision();
        }
    }

    static long packRegion(int regionX, int regionZ) {
        // Snapshot region indexes use the same x-in-low/z-in-high layout as
        // ChunkCoordinate. Keeping this helper identical is essential:
        // swapping the halves made highlights appear only in coincident
        // regions while marker elements continued to render normally.
        return ChunkCoordinate.pack(regionX, regionZ);
    }

    record OverlayStyle(
            boolean worldMapEnabled,
            boolean minimapEnabled,
            int fillAlpha,
            int strongBorderAlpha,
            int internalBorderAlpha,
            int borderThickness,
            int occupationStripeAlpha,
            int occupationStripePeriod,
            int occupationStripeWidth,
            RgbColor neutralColor,
            RgbColor borderColor,
            Set<Integer> hiddenTerritories
    ) {

        OverlayStyle {
            fillAlpha = clampAlpha(fillAlpha);
            strongBorderAlpha = clampAlpha(strongBorderAlpha);
            internalBorderAlpha = clampAlpha(internalBorderAlpha);
            borderThickness = Math.max(1, Math.min(8, borderThickness));
            occupationStripeAlpha = clampAlpha(occupationStripeAlpha);
            occupationStripePeriod = Math.max(2, occupationStripePeriod);
            occupationStripeWidth = Math.max(
                    1,
                    Math.min(occupationStripePeriod - 1, occupationStripeWidth)
            );
            neutralColor = neutralColor == null ? RgbColor.NEUTRAL : neutralColor;
            borderColor = borderColor == null ? new RgbColor(24, 16, 16) : borderColor;
            hiddenTerritories = hiddenTerritories == null
                    ? Set.of()
                    : Set.copyOf(hiddenTerritories);
        }

        static OverlayStyle from(ServerSettings settings, boolean active) {
            return new OverlayStyle(
                    active && settings.worldMapOverlay,
                    active && settings.minimapOverlay,
                    settings.territoryFillOpacity,
                    settings.borderOpacity,
                    settings.internalBorderOpacity,
                    settings.borderThickness,
                    settings.occupationStripeOpacity,
                    settings.occupationStripePeriod,
                    settings.occupationStripeWidth,
                    settings.neutralTerritoryColor,
                    settings.territoryBorderColor,
                    settings.hiddenTerritories
            );
        }

        private static int clampAlpha(int alpha) {
            return Math.max(0, Math.min(255, alpha));
        }
    }

    record State(
            NodesOverlaySnapshot snapshot,
            OverlayStyle style,
            Set<Long> populatedRegions,
            Set<Long> snapshotOccupiedRegions,
            Set<Long> occupiedRegions,
            Map<Long, RgbColor> liveOccupationColors,
            Set<Long> liveLiberatedChunks,
            Set<Integer> liveLiberatedTerritories,
            long sourceRevision,
            HighlightVersions.View versions
    ) {

        static State empty() {
            return new State(
                    NodesOverlaySnapshot.empty(),
                    OverlayStyle.from(new ServerSettings(), false),
                    Set.of(),
                    Set.of(),
                    Set.of(),
                    Map.of(),
                    Set.of(),
                    Set.of(),
                    Long.MIN_VALUE,
                    HighlightVersions.View.global(Long.MIN_VALUE)
            );
        }

        static State create(NodesOverlaySnapshot snapshot, OverlayStyle style, long sourceRevision) {
            return create(snapshot, style, sourceRevision, List.of(), null);
        }

        static State create(
                NodesOverlaySnapshot snapshot,
                OverlayStyle style,
                long sourceRevision,
                List<WarEvent> liveEvents
        ) {
            return create(snapshot, style, sourceRevision, liveEvents, null);
        }

        static State create(
                NodesOverlaySnapshot snapshot,
                OverlayStyle style,
                long sourceRevision,
                List<WarEvent> liveEvents,
                State previous
        ) {
            return create(snapshot, style, sourceRevision, liveEvents, previous,
                    HighlightVersions.View.global(sourceRevision));
        }

        static State create(
                NodesOverlaySnapshot snapshot, OverlayStyle style, long sourceRevision,
                List<WarEvent> liveEvents, State previous, HighlightVersions.View versions
        ) {
            Set<Long> populated;
            Set<Long> snapshotOccupied;
            Map<Long, RgbColor> liveOccupationColors = new HashMap<>();
            Set<Long> liveLiberatedChunks = new HashSet<>();
            Set<Integer> liveLiberatedTerritories = new HashSet<>();

            if (canReuseSnapshotRegions(previous, snapshot, style)) {
                populated = previous.populatedRegions();
                snapshotOccupied = previous.snapshotOccupiedRegions();
            } else {
                populated = snapshot.visibleRegionKeys(style.hiddenTerritories());
                snapshotOccupied = snapshot.visibleOccupiedRegionKeys(
                        style.hiddenTerritories()
                );
            }

            Set<Long> occupied = new HashSet<>(snapshotOccupied);
            applyLiveOccupationEvents(
                    snapshot,
                    style,
                    occupied,
                    liveOccupationColors,
                    liveLiberatedChunks,
                    liveLiberatedTerritories,
                    liveEvents
            );

            return new State(
                    snapshot,
                    style,
                    populated,
                    snapshotOccupied,
                    Set.copyOf(occupied),
                    Map.copyOf(liveOccupationColors),
                    Set.copyOf(liveLiberatedChunks),
                    Set.copyOf(liveLiberatedTerritories),
                    sourceRevision,
                    versions
            );
        }

        private static boolean canReuseSnapshotRegions(
                State previous,
                NodesOverlaySnapshot snapshot,
                OverlayStyle style
        ) {
            return previous != null
                    && previous.snapshot() == snapshot
                    && previous.style().hiddenTerritories().equals(style.hiddenTerritories());
        }

        private static void applyLiveOccupationEvents(
                NodesOverlaySnapshot snapshot,
                OverlayStyle style,
                Set<Long> occupied,
                Map<Long, RgbColor> liveOccupationColors,
                Set<Long> liveLiberatedChunks,
                Set<Integer> liveLiberatedTerritories,
                List<WarEvent> liveEvents
        ) {
            // Exact chunk outcomes are always the top layer. A later
            // /territory observation only refreshes the territory-wide
            // baseline and must not hide chunk captures that happened before
            // the command was run.
            for (WarEvent event : liveEvents) {
                if (event.kind() == WarEvent.Kind.CHUNK_DEFENDED
                        || event.kind() == WarEvent.Kind.CHUNK_LIBERATED) {
                    TerritoryView territory = snapshot.territoryAtChunk(
                            event.chunkX(),
                            event.chunkZ()
                    );
                    if (territory != null
                            && !style.hiddenTerritories().contains(territory.territory().id())) {
                        liveLiberatedChunks.add(
                                ChunkCoordinate.pack(event.chunkX(), event.chunkZ())
                        );
                    }
                } else if (event.kind() == WarEvent.Kind.CHUNK_CAPTURE) {
                    TerritoryView territory = snapshot.territoryAtChunk(
                            event.chunkX(),
                            event.chunkZ()
                    );
                    if (territory != null
                            && !style.hiddenTerritories().contains(territory.territory().id())) {
                        occupied.add(packRegion(event.chunkX() >> 5, event.chunkZ() >> 5));
                        liveOccupationColors.putIfAbsent(
                                ChunkCoordinate.pack(event.chunkX(), event.chunkZ()),
                                event.color()
                        );
                    }
                }
            }

            // Territory outcomes form the baseline under those exact chunk
            // results. WarTracker removes stale exact results when an actual
            // full-territory war outcome supersedes them.
            for (WarEvent event : liveEvents) {
                if (event.kind() == WarEvent.Kind.TERRITORY_LIBERATED) {
                    TerritoryView territory = snapshot.territory(event.territoryId());
                    if (territory != null
                            && !style.hiddenTerritories().contains(territory.territory().id())) {
                        liveLiberatedTerritories.add(event.territoryId());
                    }
                } else if (event.kind() == WarEvent.Kind.TERRITORY_CAPTURE) {
                    TerritoryView territory = snapshot.territory(event.territoryId());
                    if (territory == null
                            || style.hiddenTerritories().contains(territory.territory().id())) {
                        continue;
                    }
                    int[] chunks = territory.territory().chunks();
                    for (int index = 0; index + 1 < chunks.length; index += 2) {
                        int chunkX = chunks[index];
                        int chunkZ = chunks[index + 1];
                        occupied.add(packRegion(chunkX >> 5, chunkZ >> 5));
                        liveOccupationColors.putIfAbsent(
                                ChunkCoordinate.pack(chunkX, chunkZ),
                                event.color()
                        );
                    }
                }
            }
        }

        long regionRevision(int regionX, int regionZ) {
            return versions.region(packRegion(regionX, regionZ));
        }

        int regionHash(int regionX, int regionZ) {
            long revision = regionRevision(regionX, regionZ);
            return (int) (revision ^ revision >>> 32);
        }

        boolean hasTerritoriesInRegion(int regionX, int regionZ) {
            return populatedRegions.contains(packRegion(regionX, regionZ));
        }

        boolean hasOccupiedTerritoriesInRegion(int regionX, int regionZ) {
            return occupiedRegions.contains(packRegion(regionX, regionZ));
        }

        TerritoryView territoryAtChunk(int chunkX, int chunkZ) {
            TerritoryView territory = snapshot.territoryAtChunk(chunkX, chunkZ);
            return territory == null
                    || style.hiddenTerritories().contains(territory.territory().id())
                    ? null
                    : territory;
        }

        TerritoryView territoryAtBlock(int blockX, int blockZ) {
            return territoryAtChunk(Math.floorDiv(blockX, 16), Math.floorDiv(blockZ, 16));
        }

        RgbColor baseColor(TerritoryView territory) {
            return territory.owner() == null ? style.neutralColor() : territory.ownerColor();
        }

        RgbColor occupationColor(TerritoryView territory, int chunkX, int chunkZ) {
            long chunkKey = ChunkCoordinate.pack(chunkX, chunkZ);
            if (liveLiberatedChunks.contains(chunkKey)) {
                return null;
            }
            RgbColor liveColor = liveOccupationColors.get(chunkKey);
            if (liveColor != null) {
                return liveColor;
            }
            if (liveLiberatedTerritories.contains(territory.territory().id())) {
                return null;
            }
            return territory.occupied() ? territory.occupierColor() : null;
        }

        boolean isOccupiedChunk(int chunkX, int chunkZ) {
            TerritoryView territory = territoryAtChunk(chunkX, chunkZ);
            return territory != null && occupationColor(territory, chunkX, chunkZ) != null;
        }
    }
}
