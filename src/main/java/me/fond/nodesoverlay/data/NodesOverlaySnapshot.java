package me.fond.nodesoverlay.data;

import me.fond.nodesoverlay.model.ChunkCoordinate;
import me.fond.nodesoverlay.model.NationRecord;
import me.fond.nodesoverlay.model.NodeDefinition;
import me.fond.nodesoverlay.model.PortRecord;
import me.fond.nodesoverlay.model.ResidentRecord;
import me.fond.nodesoverlay.model.TerritoryView;
import me.fond.nodesoverlay.model.TerritoryOwnershipOverride;
import me.fond.nodesoverlay.model.TownRecord;
import me.fond.nodesoverlay.model.OwnershipState;
import me.fond.nodesoverlay.model.RgbColor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Set;

public final class NodesOverlaySnapshot {

    private static final NodesOverlaySnapshot EMPTY = new NodesOverlaySnapshot(
            Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(),
            new ChunkTerritoryIndex(1), Instant.EPOCH, 0
    );

    private final Map<Integer, TerritoryView> territories;
    private final Map<String, NodeDefinition> nodes;
    private final Map<String, PortRecord> ports;
    private final Map<String, TownRecord> towns;
    private final Map<String, NationRecord> nations;
    private final Map<String, ResidentRecord> residentsByName;
    private final ChunkTerritoryIndex chunkIndex;
    private final SpatialTerritoryIndex spatialIndex;
    private final SpatialTerritoryIndex coreSpatialIndex;
    private final Set<Long> territoryRegionKeys;
    private final Set<Long> occupiedRegionKeys;
    private final Map<Long, List<Integer>> territoryIdsByRegion;
    private final Instant loadedAt;
    private final int duplicateChunkAssignments;

    NodesOverlaySnapshot(
            Map<Integer, TerritoryView> territories,
            Map<String, NodeDefinition> nodes,
            Map<String, PortRecord> ports,
            Map<String, TownRecord> towns,
            Map<String, NationRecord> nations,
            Map<String, ResidentRecord> residentsByName,
            ChunkTerritoryIndex chunkIndex,
            Instant loadedAt,
            int duplicateChunkAssignments
    ) {
        this(territories, nodes, ports, towns, nations, residentsByName,
                chunkIndex, loadedAt, duplicateChunkAssignments, null);
    }

    NodesOverlaySnapshot(
            Map<Integer, TerritoryView> territories,
            Map<String, NodeDefinition> nodes,
            Map<String, PortRecord> ports,
            Map<String, TownRecord> towns,
            Map<String, NationRecord> nations,
            Map<String, ResidentRecord> residentsByName,
            ChunkTerritoryIndex chunkIndex,
            Instant loadedAt,
            int duplicateChunkAssignments,
            NodesOverlaySnapshot geometrySource
    ) {
        this.territories = Map.copyOf(territories);
        this.nodes = Map.copyOf(nodes);
        this.ports = Map.copyOf(ports);
        this.towns = Map.copyOf(towns);
        this.nations = Map.copyOf(nations);
        this.residentsByName = Map.copyOf(residentsByName);
        this.chunkIndex = chunkIndex;
        boolean sameGeometry = geometrySource != null
                && geometrySource.chunkIndex == chunkIndex
                && geometrySource.territories.size() == territories.size()
                && territories.values().stream().allMatch(view -> {
                    TerritoryView old = geometrySource.territory(view.territory().id());
                    return old != null && old.territory() == view.territory();
                });
        if (sameGeometry) {
            this.spatialIndex = geometrySource.spatialIndex;
            this.coreSpatialIndex = geometrySource.coreSpatialIndex;
            this.territoryRegionKeys = geometrySource.territoryRegionKeys;
            this.territoryIdsByRegion = geometrySource.territoryIdsByRegion;
            Set<Long> occupied = new HashSet<>();
            territoryIdsByRegion.forEach((region, ids) -> {
                if (ids.stream().anyMatch(id -> this.territories.get(id).occupied())) {
                    occupied.add(region);
                }
            });
            this.occupiedRegionKeys = Set.copyOf(occupied);
        } else {
            this.spatialIndex = SpatialTerritoryIndex.build(this.territories.values());
            this.coreSpatialIndex = SpatialTerritoryIndex.buildForCores(this.territories.values());
            RegionIndex regionIndex = buildRegionIndex(this.territories.values());
            this.territoryRegionKeys = regionIndex.populatedRegions();
            this.occupiedRegionKeys = regionIndex.occupiedRegions();
            this.territoryIdsByRegion = regionIndex.territoryIdsByRegion();
        }
        this.loadedAt = loadedAt;
        this.duplicateChunkAssignments = duplicateChunkAssignments;
    }

    public static NodesOverlaySnapshot empty() {
        return EMPTY;
    }

    public boolean isEmpty() {
        return territories.isEmpty();
    }

    public TerritoryView territory(int id) {
        return territories.get(id);
    }

    public TerritoryView territoryAtChunk(int chunkX, int chunkZ) {
        int id = chunkIndex.get(ChunkCoordinate.pack(chunkX, chunkZ));
        return id < 0 ? null : territories.get(id);
    }

    public TerritoryView territoryAtBlock(int blockX, int blockZ) {
        ChunkCoordinate chunk = ChunkCoordinate.fromBlock(blockX, blockZ);
        return territoryAtChunk(chunk.x(), chunk.z());
    }

    public List<TerritoryView> visibleTerritories(
            double minBlockX,
            double minBlockZ,
            double maxBlockX,
            double maxBlockZ
    ) {
        List<TerritoryView> result = new ArrayList<>();
        for (int id : spatialIndex.query(minBlockX, minBlockZ, maxBlockX, maxBlockZ)) {
            TerritoryView view = territories.get(id);
            if (view != null && view.territory().intersects(minBlockX, minBlockZ, maxBlockX, maxBlockZ)) {
                result.add(view);
            }
        }
        return result;
    }

    /**
     * Returns only territories whose core marker lies inside the requested
     * viewport. This avoids feeding minimap rendering thousands of candidates
     * merely because a territory owns a distant or disconnected chunk.
     */
    public List<TerritoryView> visibleTerritoryCores(
            double minBlockX,
            double minBlockZ,
            double maxBlockX,
            double maxBlockZ
    ) {
        List<TerritoryView> result = new ArrayList<>();
        for (int id : coreSpatialIndex.query(minBlockX, minBlockZ, maxBlockX, maxBlockZ)) {
            TerritoryView view = territories.get(id);
            if (view == null) {
                continue;
            }
            int coreX = view.territory().coreX();
            int coreZ = view.territory().coreZ();
            if (coreX >= minBlockX
                    && coreX <= maxBlockX
                    && coreZ >= minBlockZ
                    && coreZ <= maxBlockZ) {
                result.add(view);
            }
        }
        return result;
    }

    /**
     * Returns whether a Xaero map region (32 by 32 chunks) contains at least
     * one mapped territory chunk.
     */
    public boolean regionHasTerritories(int regionX, int regionZ) {
        return territoryRegionKeys.contains(ChunkCoordinate.pack(regionX, regionZ));
    }

    /**
     * Alias matching the wording used by map highlighter integrations.
     */
    public boolean hasTerritoriesInRegion(int regionX, int regionZ) {
        return regionHasTerritories(regionX, regionZ);
    }

    /**
     * Returns precomputed Xaero region keys after applying the profile's
     * hidden-territory filter. The full chunk scan is performed once while the
     * snapshot is built on the data worker; filtered views inspect only the
     * much smaller region-to-territory index.
     */
    public Set<Long> visibleRegionKeys(Set<Integer> hiddenTerritories) {
        if (hiddenTerritories == null || hiddenTerritories.isEmpty()) {
            return territoryRegionKeys;
        }
        Set<Long> result = new HashSet<>();
        territoryIdsByRegion.forEach((region, territoryIds) -> {
            if (territoryIds.stream().anyMatch(id -> !hiddenTerritories.contains(id))) {
                result.add(region);
            }
        });
        return Set.copyOf(result);
    }

    public Set<Long> visibleOccupiedRegionKeys(Set<Integer> hiddenTerritories) {
        if (hiddenTerritories == null || hiddenTerritories.isEmpty()) {
            return occupiedRegionKeys;
        }
        Set<Long> result = new HashSet<>();
        territoryIdsByRegion.forEach((region, territoryIds) -> {
            boolean visibleOccupation = territoryIds.stream().anyMatch(id -> {
                TerritoryView territory = territories.get(id);
                return territory != null
                        && territory.occupied()
                        && !hiddenTerritories.contains(id);
            });
            if (visibleOccupation) {
                result.add(region);
            }
        });
        return Set.copyOf(result);
    }

    public NodeDefinition node(String id) {
        return id == null ? null : nodes.get(id.toLowerCase(Locale.ROOT));
    }

    public PortRecord port(String id) {
        return id == null ? null : ports.get(PortRecord.normalizeId(id));
    }

    public TownRecord town(String name) {
        return name == null ? null : towns.get(name.toLowerCase(Locale.ROOT));
    }

    public NationRecord nation(String name) {
        return name == null ? null : nations.get(name.toLowerCase(Locale.ROOT));
    }

    public ResidentRecord resident(String name) {
        return name == null ? null : residentsByName.get(name.toLowerCase(Locale.ROOT));
    }

    public Map<Integer, TerritoryView> territories() {
        return territories;
    }

    public Map<String, NodeDefinition> nodes() {
        return nodes;
    }

    public Map<String, PortRecord> ports() {
        return ports;
    }

    public Map<String, TownRecord> towns() {
        return towns;
    }

    public Map<String, NationRecord> nations() {
        return nations;
    }

    /**
     * Returns a snapshot view with owner/occupier identities learned directly
     * from the server. Geometry and lookup indexes are preserved, while every
     * owner-dependent label, color and status is rebuilt from the observed
     * town instead of stale towns.json territory membership.
     */
    public NodesOverlaySnapshot withTerritoryOwnership(
            Map<Integer, TerritoryOwnershipOverride> overrides
    ) {
        if (overrides == null || overrides.isEmpty() || territories.isEmpty()) {
            return this;
        }
        Map<Integer, TerritoryView> updated = new LinkedHashMap<>(territories);
        boolean changed = false;
        for (Map.Entry<Integer, TerritoryOwnershipOverride> entry : overrides.entrySet()) {
            TerritoryView previous = updated.get(entry.getKey());
            TerritoryOwnershipOverride override = entry.getValue();
            if (previous == null || override == null) {
                continue;
            }

            TownRecord owner = observedTown(override.ownerTown(), previous.owner());
            TownRecord occupier = observedTown(override.occupierTown(), previous.occupier());
            if (sameTown(owner, occupier)) {
                occupier = null;
            }
            NationRecord ownerNation = nationFor(owner);
            NationRecord occupierNation = nationFor(occupier);
            OwnershipState state = ownershipState(previous.territory().id(), owner, occupier);
            TerritoryView replacement = new TerritoryView(
                    previous.territory(),
                    owner,
                    owner == null ? List.of() : List.of(owner),
                    ownerNation,
                    occupier,
                    occupierNation,
                    state,
                    colorFor(owner, ownerNation),
                    colorFor(occupier, occupierNation),
                    owner != null && owner.cores().contains(previous.territory().id())
            );
            if (!replacement.equals(previous)) {
                updated.put(entry.getKey(), replacement);
                changed = true;
            }
        }
        return changed
                ? new NodesOverlaySnapshot(
                updated,
                nodes,
                ports,
                towns,
                nations,
                residentsByName,
                chunkIndex,
                loadedAt,
                duplicateChunkAssignments,
                this
        )
                : this;
    }

    private TownRecord observedTown(String observedName, TownRecord fallback) {
        if (observedName == null) {
            return null;
        }
        TownRecord known = town(observedName);
        if (known != null) {
            return known;
        }
        if (fallback != null && fallback.name().equalsIgnoreCase(observedName)) {
            return fallback;
        }
        return new TownRecord(
                observedName,
                null,
                RgbColor.NEUTRAL,
                Set.of(),
                Set.of(),
                Set.of(),
                Set.of(),
                Set.of(),
                Set.of(),
                Set.of(),
                Set.of(),
                Set.of()
        );
    }

    private NationRecord nationFor(TownRecord town) {
        return town == null ? null : nation(town.nation());
    }

    private static RgbColor colorFor(TownRecord town, NationRecord nation) {
        return nation != null
                ? nation.color()
                : town == null ? RgbColor.NEUTRAL : town.color();
    }

    private static OwnershipState ownershipState(
            int territoryId,
            TownRecord owner,
            TownRecord occupier
    ) {
        if (occupier != null && !sameTown(owner, occupier)) {
            return OwnershipState.CAPTURED;
        }
        if (owner == null) {
            return OwnershipState.UNOWNED;
        }
        if (owner.annexed().contains(territoryId)) {
            return OwnershipState.ANNEXED;
        }
        if (owner.claimed().contains(territoryId)) {
            return OwnershipState.CLAIMED;
        }
        return OwnershipState.OWNED;
    }

    private static boolean sameTown(TownRecord first, TownRecord second) {
        return first != null
                && second != null
                && first.name().equalsIgnoreCase(second.name());
    }

    public Instant loadedAt() {
        return loadedAt;
    }

    public int duplicateChunkAssignments() {
        return duplicateChunkAssignments;
    }

    private static RegionIndex buildRegionIndex(Iterable<TerritoryView> territories) {
        Set<Long> regions = new HashSet<>();
        Set<Long> occupiedRegions = new HashSet<>();
        Map<Long, Set<Integer>> mutableTerritoryIdsByRegion = new java.util.HashMap<>();
        for (TerritoryView view : territories) {
            Set<Long> territoryRegions = new HashSet<>();
            int[] chunks = view.territory().chunks();
            for (int index = 0; index + 1 < chunks.length; index += 2) {
                int regionX = Math.floorDiv(chunks[index], 32);
                int regionZ = Math.floorDiv(chunks[index + 1], 32);
                territoryRegions.add(ChunkCoordinate.pack(regionX, regionZ));
            }
            for (long region : territoryRegions) {
                regions.add(region);
                if (view.occupied()) {
                    occupiedRegions.add(region);
                }
                mutableTerritoryIdsByRegion
                        .computeIfAbsent(region, ignored -> new HashSet<>())
                        .add(view.territory().id());
            }
        }
        Map<Long, List<Integer>> territoryIdsByRegion = new java.util.HashMap<>();
        mutableTerritoryIdsByRegion.forEach((region, ids) ->
                territoryIdsByRegion.put(
                        region,
                        ids.stream().sorted().toList()
                ));
        return new RegionIndex(
                Set.copyOf(regions),
                Set.copyOf(occupiedRegions),
                Map.copyOf(territoryIdsByRegion)
        );
    }

    private record RegionIndex(
            Set<Long> populatedRegions,
            Set<Long> occupiedRegions,
            Map<Long, List<Integer>> territoryIdsByRegion
    ) {
    }
}
