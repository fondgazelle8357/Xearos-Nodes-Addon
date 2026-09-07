package me.fond.nodesoverlay;

import me.fond.nodesoverlay.data.NodesOverlaySnapshot;
import me.fond.nodesoverlay.model.ChunkCoordinate;
import me.fond.nodesoverlay.war.WarEvent;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Batches pixel changes until the end of a client tick; readers retain immutable versions. */
public final class HighlightVersions {
    private volatile View current = View.global(0);
    private boolean pendingAll;
    private final Set<Long> pendingRegions = new HashSet<>();

    public View current() {
        return current;
    }

    public synchronized void invalidateAll() {
        pendingAll = true;
        pendingRegions.clear();
    }

    public synchronized void invalidateRegions(Set<Long> regions) {
        if (!pendingAll) {
            pendingRegions.addAll(regions);
        }
    }

    public void invalidateWarEvent(WarEvent event, NodesOverlaySnapshot snapshot) {
        Set<Long> regions = new HashSet<>();
        switch (event.kind()) {
            case ATTACK, ATTACK_DEFENDED -> { return; }
            case CHUNK_CAPTURE, CHUNK_DEFENDED, CHUNK_LIBERATED ->
                    regions.add(ChunkCoordinate.pack(event.chunkX() >> 5, event.chunkZ() >> 5));
            case TERRITORY_CAPTURE, TERRITORY_LIBERATED -> {
                var territory = snapshot.territory(event.territoryId());
                if (territory != null) {
                    int[] chunks = territory.territory().chunks();
                    for (int i = 0; i + 1 < chunks.length; i += 2) {
                        regions.add(ChunkCoordinate.pack(chunks[i] >> 5, chunks[i + 1] >> 5));
                    }
                }
            }
        }
        invalidateRegions(regions);
    }

    public synchronized Update flush() {
        if (!pendingAll && pendingRegions.isEmpty()) {
            return null;
        }
        long revision = current.revision() + 1;
        Set<Long> regions = Set.copyOf(pendingRegions);
        boolean all = pendingAll;
        if (all) {
            current = View.global(revision);
        } else {
            Map<Long, Long> versions = new HashMap<>(current.regions());
            regions.forEach(region -> versions.put(region, revision));
            current = new View(revision, current.globalRevision(), Map.copyOf(versions));
        }
        pendingAll = false;
        pendingRegions.clear();
        return new Update(current, all, regions);
    }

    public record View(long revision, long globalRevision, Map<Long, Long> regions) {
        public View {
            regions = Map.copyOf(regions);
        }

        public static View global(long revision) {
            return new View(revision, revision, Map.of());
        }

        public long region(long key) {
            return regions.getOrDefault(key, globalRevision);
        }
    }

    public record Update(View view, boolean all, Set<Long> regions) { }
}
