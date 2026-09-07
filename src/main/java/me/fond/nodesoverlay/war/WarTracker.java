package me.fond.nodesoverlay.war;

import me.fond.nodesoverlay.data.NodesOverlaySnapshot;
import me.fond.nodesoverlay.model.ChunkCoordinate;
import me.fond.nodesoverlay.model.NationRecord;
import me.fond.nodesoverlay.model.ResidentRecord;
import me.fond.nodesoverlay.model.RgbColor;
import me.fond.nodesoverlay.model.TerritoryDefinition;
import me.fond.nodesoverlay.model.TerritoryView;
import me.fond.nodesoverlay.model.TownRecord;
import me.fond.nodesoverlay.model.TerritoryOwnershipOverride;
import net.minecraft.client.MinecraftClient;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class WarTracker {

    static final int ACTIVE_ATTACK_EXPIRATION_SECONDS = 15 * 60;

    private final Map<String, WarEvent> events = new ConcurrentHashMap<>();
    private final Map<Long, LiveCapture> capturedChunks = new ConcurrentHashMap<>();
    private final Map<Integer, LiveCapture> capturedTerritories = new ConcurrentHashMap<>();
    private final Map<Long, Instant> liberatedChunks = new ConcurrentHashMap<>();
    private final Map<Integer, Instant> liberatedTerritories = new ConcurrentHashMap<>();
    private final Map<Integer, TerritoryInfoState> territoryInfo = new ConcurrentHashMap<>();
    private final Set<Integer> suppressedWaypointTerritories = ConcurrentHashMap.newKeySet();
    private final AttackWaypointManager waypoints = new AttackWaypointManager();
    private volatile String localPlayerName = "";
    private volatile EventViews cachedEvents = new EventViews(List.of(), List.of());
    private long lastExpirySecond = Long.MIN_VALUE;

    private record EventViews(List<WarEvent> history, List<WarEvent> attacks) { }


    public synchronized WarEvent apply(WarMessage message, NodesOverlaySnapshot snapshot, String localPlayerName) {
        if (localPlayerName != null && !localPlayerName.isBlank()) {
            this.localPlayerName = localPlayerName;
        }
        Instant now = Instant.now();
        boolean storeEvent = true;
        ResidentRecord attacker = snapshot.resident(message.player());
        TownRecord attackerTown = attacker == null ? null : snapshot.town(attacker.town());
        String attackingTown = attackerTown == null
                ? attacker == null ? null : attacker.town()
                : attackerTown.name();
        String attackingNation = attackerTown != null
                ? attackerTown.nation()
                : attacker == null ? null : attacker.nation();

        WarEvent event;
        if (message instanceof WarMessage.Attack attack) {
            TerritoryView territory = snapshot.territoryAtBlock(attack.blockX(), attack.blockZ());
            int territoryId = territory == null ? -1 : territory.territory().id();
            int chunkX = Math.floorDiv(attack.blockX(), 16);
            int chunkZ = Math.floorDiv(attack.blockZ(), 16);
            WarRelation relation = relationToAttacker(
                    attacker,
                    attackerTown,
                    attackingTown,
                    attackingNation,
                    snapshot,
                    localPlayerName
            );
            WarMarkerType marker = markerFor(relation);
            RgbColor color = targetColor(attack.reportedTown(), territory, snapshot);
            String key = "attack:" + chunkX + ":" + chunkZ;
            event = new WarEvent(
                    key,
                    WarEvent.Kind.ATTACK,
                    attack.player(),
                    attackingTown,
                    attackingNation,
                    attack.reportedTown(),
                    territoryId,
                    attack.blockX(),
                    attack.blockY(),
                    attack.blockZ(),
                    chunkX,
                    chunkZ,
                    relation,
                    marker,
                    color,
                    now
            );
        } else if (message instanceof WarMessage.AttackDefended defended) {
            TerritoryView territory = snapshot.territoryAtBlock(
                    defended.blockX(),
                    defended.blockZ()
            );
            int territoryId = territory == null ? -1 : territory.territory().id();
            int chunkX = Math.floorDiv(defended.blockX(), 16);
            int chunkZ = Math.floorDiv(defended.blockZ(), 16);
            String reportedTown = territory == null || territory.owner() == null
                    ? ""
                    : territory.owner().name();
            events.entrySet().removeIf(entry ->
                    sameChunk(entry.getValue(), chunkX, chunkZ)
                            && entry.getValue().kind() == WarEvent.Kind.ATTACK);
            clearInactiveWaypointSuppressions();
            event = new WarEvent(
                    "attack-defended:" + chunkX + ":" + chunkZ,
                    WarEvent.Kind.ATTACK_DEFENDED,
                    defended.player(),
                    attackingTown,
                    attackingNation,
                    reportedTown,
                    territoryId,
                    defended.blockX(),
                    defended.blockY(),
                    defended.blockZ(),
                    chunkX,
                    chunkZ,
                    relationTo(reportedTown, snapshot, localPlayerName),
                    WarMarkerType.SHIELD,
                    participantColor(
                            attacker,
                            attackerTown,
                            attackingNation,
                            territory,
                            snapshot
                    ),
                    now
            );
        } else if (message instanceof WarMessage.ChunkCaptured capture) {
            TerritoryView territory = snapshot.territoryAtChunk(capture.chunkX(), capture.chunkZ());
            int territoryId = territory == null ? -1 : territory.territory().id();
            int blockX = capture.chunkX() * 16 + 8;
            int blockZ = capture.chunkZ() * 16 + 8;
            long chunkKey = ChunkCoordinate.pack(capture.chunkX(), capture.chunkZ());
            WarRelation attackerRelation = relationToAttacker(
                    attacker,
                    attackerTown,
                    attackingTown,
                    attackingNation,
                    snapshot,
                    localPlayerName
            );
            WarRelation existingOccupationRelation = occupationRelationAtChunk(
                    territory,
                    capture.chunkX(),
                    capture.chunkZ(),
                    territoryId,
                    snapshot,
                    localPlayerName
            );
            boolean preserveFriendlyOccupation =
                    isFriendlyOrAllied(attackerRelation)
                            && isFriendlyOrAllied(existingOccupationRelation);
            LiveCapture live = new LiveCapture(
                    capture.player(),
                    attackingTown,
                    attackingNation,
                    attackerColor(attackerTown, attackingNation, snapshot),
                    now
            );
            if (!preserveFriendlyOccupation) {
                liberatedChunks.remove(chunkKey);
                capturedChunks.put(chunkKey, live);
            }
            events.entrySet().removeIf(entry -> {
                WarEvent previous = entry.getValue();
                return sameChunk(previous, capture.chunkX(), capture.chunkZ())
                        && (preserveFriendlyOccupation
                        ? previous.kind() == WarEvent.Kind.ATTACK
                        : previous.kind() != WarEvent.Kind.TERRITORY_CAPTURE
                        && previous.kind() != WarEvent.Kind.TERRITORY_LIBERATED);
            });
            clearInactiveWaypointSuppressions();
            storeEvent = !preserveFriendlyOccupation;
            event = new WarEvent(
                    "chunk:" + capture.chunkX() + ":" + capture.chunkZ(),
                    WarEvent.Kind.CHUNK_CAPTURE,
                    capture.player(),
                    attackingTown,
                    attackingNation,
                    capture.reportedTown(),
                    territoryId,
                    blockX,
                    128,
                    blockZ,
                    capture.chunkX(),
                    capture.chunkZ(),
                    relationTo(capture.reportedTown(), snapshot, localPlayerName),
                    WarMarkerType.FLAG,
                    live.occupierColor(),
                    now
            );
        } else if (message instanceof WarMessage.ChunkDefended defended) {
            event = applyChunkResolution(
                    WarEvent.Kind.CHUNK_DEFENDED,
                    "chunk-defended:",
                    defended.player(),
                    attackingTown,
                    attackingNation,
                    defended.reportedTown(),
                    defended.chunkX(),
                    defended.chunkZ(),
                    attacker,
                    attackerTown,
                    snapshot,
                    localPlayerName,
                    now
            );
        } else if (message instanceof WarMessage.ChunkLiberated liberated) {
            event = applyChunkResolution(
                    WarEvent.Kind.CHUNK_LIBERATED,
                    "chunk-liberated:",
                    liberated.player(),
                    attackingTown,
                    attackingNation,
                    liberated.reportedTown(),
                    liberated.chunkX(),
                    liberated.chunkZ(),
                    attacker,
                    attackerTown,
                    snapshot,
                    localPlayerName,
                    now
            );
        } else if (message instanceof WarMessage.TerritoryCaptured capture) {
            TerritoryView territory = snapshot.territory(capture.territoryId());
            TerritoryDefinition definition = territory == null ? null : territory.territory();
            int blockX = definition == null ? 0 : definition.coreX();
            int blockZ = definition == null ? 0 : definition.coreZ();
            int chunkX = definition == null ? 0 : definition.coreChunkX();
            int chunkZ = definition == null ? 0 : definition.coreChunkZ();
            LiveCapture live = new LiveCapture(
                    capture.player(),
                    attackingTown,
                    attackingNation,
                    attackerColor(attackerTown, attackingNation, snapshot),
                    now
            );
            territoryInfo.remove(capture.territoryId());
            liberatedTerritories.remove(capture.territoryId());
            capturedTerritories.put(capture.territoryId(), live);
            if (definition != null) {
                int[] chunks = definition.chunks();
                for (int index = 0; index + 1 < chunks.length; index += 2) {
                    long chunkKey = ChunkCoordinate.pack(chunks[index], chunks[index + 1]);
                    capturedChunks.remove(chunkKey);
                    liberatedChunks.remove(chunkKey);
                }
            }
            events.entrySet().removeIf(entry ->
                    supersededByTerritoryOutcome(
                            entry.getValue(),
                            capture.territoryId(),
                            definition
                    ));
            suppressedWaypointTerritories.remove(capture.territoryId());
            event = new WarEvent(
                    "territory:" + capture.territoryId(),
                    WarEvent.Kind.TERRITORY_CAPTURE,
                    capture.player(),
                    attackingTown,
                    attackingNation,
                    capture.reportedTown(),
                    capture.territoryId(),
                    blockX,
                    128,
                    blockZ,
                    chunkX,
                    chunkZ,
                    relationTo(capture.reportedTown(), snapshot, localPlayerName),
                    WarMarkerType.FLAG,
                    live.occupierColor(),
                    now
            );
        } else if (message instanceof WarMessage.TerritoryLiberated liberated) {
            TerritoryView territory = snapshot.territory(liberated.territoryId());
            TerritoryDefinition definition = territory == null ? null : territory.territory();
            int blockX = definition == null ? 0 : definition.coreX();
            int blockZ = definition == null ? 0 : definition.coreZ();
            int chunkX = definition == null ? 0 : definition.coreChunkX();
            int chunkZ = definition == null ? 0 : definition.coreChunkZ();
            territoryInfo.remove(liberated.territoryId());
            liberatedTerritories.put(liberated.territoryId(), now);
            capturedTerritories.remove(liberated.territoryId());
            if (definition != null) {
                int[] chunks = definition.chunks();
                for (int index = 0; index + 1 < chunks.length; index += 2) {
                    long chunkKey = ChunkCoordinate.pack(chunks[index], chunks[index + 1]);
                    capturedChunks.remove(chunkKey);
                    liberatedChunks.remove(chunkKey);
                }
            }
            events.entrySet().removeIf(entry ->
                    supersededByTerritoryOutcome(
                            entry.getValue(),
                            liberated.territoryId(),
                            definition
                    ));
            suppressedWaypointTerritories.remove(liberated.territoryId());
            event = new WarEvent(
                    "territory-liberated:" + liberated.territoryId(),
                    WarEvent.Kind.TERRITORY_LIBERATED,
                    liberated.player(),
                    attackingTown,
                    attackingNation,
                    liberated.reportedTown(),
                    liberated.territoryId(),
                    blockX,
                    128,
                    blockZ,
                    chunkX,
                    chunkZ,
                    relationTo(liberated.reportedTown(), snapshot, localPlayerName),
                    WarMarkerType.SHIELD,
                    participantColor(
                            attacker,
                            attackerTown,
                            attackingNation,
                            territory,
                            snapshot
                    ),
                    now
            );
        } else {
            throw new IllegalArgumentException("Unsupported war message " + message);
        }
        if (storeEvent && event.kind() != WarEvent.Kind.ATTACK_DEFENDED) {
            events.put(event.key(), event);
        }
        refreshEventViews();
        return event;
    }

    public void tick(MinecraftClient client) {
        waypoints.tick(client, attackEvents().stream()
                .filter(event -> event.territoryId() < 0
                        || !suppressedWaypointTerritories.contains(event.territoryId()))
                .toList());
    }

    public synchronized boolean expire(int expirationSeconds) {
        Instant now = Instant.now();
        if (lastExpirySecond == now.getEpochSecond()) {
            return false;
        }
        lastExpirySecond = now.getEpochSecond();
        return expire(expirationSeconds, now);
    }

    synchronized boolean expire(int expirationSeconds, Instant now) {
        Instant historicalCutoff = now.minusSeconds(expirationSeconds);
        Instant attackCutoff = now.minusSeconds(ACTIVE_ATTACK_EXPIRATION_SECONDS);
        // Live war state is lifecycle-driven. The timer is only for historical
        // outcomes that no longer affect the map. Active attack flags have a
        // separate hard lifetime so a missed outcome cannot leave a permanent
        // icon or waypoint. In particular, towns.json does not immediately
        // expose an individual captured chunk, so expiring its provisional
        // stripe would make a real capture disappear until a later server
        // outcome or full-territory update arrives.
        boolean changed = events.entrySet().removeIf(entry -> {
            WarEvent event = entry.getValue();
            if (event.kind() == WarEvent.Kind.ATTACK) {
                return event.updatedAt().isBefore(attackCutoff);
            }
            return !isActiveOccupationEvent(event)
                    && !event.updatedAt().isAfter(historicalCutoff);
        });
        int suppressionsBeforeCleanup = suppressedWaypointTerritories.size();
        clearInactiveWaypointSuppressions();
        changed |= suppressionsBeforeCleanup != suppressedWaypointTerritories.size();
        if (changed) {
            refreshEventViews();
        }
        return changed;
    }

    private boolean isActiveOccupationEvent(WarEvent event) {
        return switch (event.kind()) {
            case CHUNK_CAPTURE -> capturedChunks.containsKey(
                    ChunkCoordinate.pack(event.chunkX(), event.chunkZ())
            );
            case TERRITORY_CAPTURE ->
                    capturedTerritories.containsKey(event.territoryId());
            case CHUNK_DEFENDED, CHUNK_LIBERATED -> liberatedChunks.containsKey(
                    ChunkCoordinate.pack(event.chunkX(), event.chunkZ())
            );
            case TERRITORY_LIBERATED ->
                    liberatedTerritories.containsKey(event.territoryId());
            case ATTACK, ATTACK_DEFENDED -> false;
        };
    }

    /**
     * Enriches live events with newly downloaded metadata without allowing
     * towns.json to replace newer in-game occupation outcomes. The map feed is
     * a baseline only: live captures, defenses, and liberations remain
     * authoritative until another in-game outcome supersedes them or the
     * tracker is cleared on disconnect.
     */
    public synchronized void reconcile(NodesOverlaySnapshot snapshot) {
        events.replaceAll((key, event) -> {
            WarEvent resolved = resolveEvent(event, snapshot, localPlayerName);
            return resolved.equals(event) ? event : resolved;
        });
        collapseSupersededEvents(snapshot);
        for (WarEvent event : events.values()) {
            LiveCapture resolved = liveCapture(event);
            if (event.kind() == WarEvent.Kind.CHUNK_CAPTURE) {
                capturedChunks.computeIfPresent(
                        ChunkCoordinate.pack(event.chunkX(), event.chunkZ()),
                        (key, previous) -> resolved
                );
            } else if (event.kind() == WarEvent.Kind.TERRITORY_CAPTURE) {
                capturedTerritories.computeIfPresent(
                        event.territoryId(),
                        (key, previous) -> resolved
                );
            }
        }
        territoryInfo.replaceAll((territoryId, previous) ->
                resolveTerritoryInfo(previous.observation(), snapshot));
        Set<Long> chunkCaptures = new java.util.HashSet<>();
        Set<Long> chunkLiberations = new java.util.HashSet<>();
        Set<Integer> territoryCaptures = new java.util.HashSet<>();
        Set<Integer> territoryLiberations = new java.util.HashSet<>();
        for (WarEvent event : events.values()) {
            switch (event.kind()) {
                case CHUNK_CAPTURE -> chunkCaptures.add(ChunkCoordinate.pack(event.chunkX(), event.chunkZ()));
                case CHUNK_DEFENDED, CHUNK_LIBERATED ->
                        chunkLiberations.add(ChunkCoordinate.pack(event.chunkX(), event.chunkZ()));
                case TERRITORY_CAPTURE -> territoryCaptures.add(event.territoryId());
                case TERRITORY_LIBERATED -> territoryLiberations.add(event.territoryId());
                default -> { }
            }
        }
        capturedChunks.keySet().retainAll(chunkCaptures);
        capturedTerritories.keySet().retainAll(territoryCaptures);
        liberatedChunks.keySet().retainAll(chunkLiberations);
        liberatedTerritories.keySet().retainAll(territoryLiberations);
        refreshEventViews();
    }

    /**
     * Applies the current owner/occupier reported by the in-game territory info
     * command. The response becomes the territory-wide live baseline and
     * replaces older territory-wide occupation outcomes. Exact chunk outcomes
     * remain layered on top because the command does not report per-chunk
     * occupation.
     */
    public synchronized boolean observeTerritory(
            TerritoryInfoObservation observation,
            NodesOverlaySnapshot snapshot
    ) {
        if (observation == null) {
            return false;
        }
        int territoryId = observation.territoryId();
        TerritoryView territory = snapshot.territory(territoryId);
        TerritoryInfoState previousInfo = territoryInfo.get(territoryId);
        String currentOwner = previousInfo == null
                ? territory == null || territory.owner() == null
                ? null
                : territory.owner().name()
                : previousInfo.observation().ownerTown();
        String currentOccupier = previousInfo == null
                ? territory == null || territory.occupier() == null
                ? null
                : territory.occupier().name()
                : previousInfo.observation().occupierTown();
        LiveCapture liveTerritory = capturedTerritories.get(territoryId);
        if (liveTerritory != null) {
            currentOccupier = liveTerritory.occupierTown();
        } else if (liberatedTerritories.containsKey(territoryId)) {
            currentOccupier = null;
        }
        boolean identityChanged =
                !sameNullableName(currentOwner, observation.ownerTown())
                        || !sameNullableName(currentOccupier, observation.occupierTown());

        capturedTerritories.remove(territoryId);
        liberatedTerritories.remove(territoryId);

        events.entrySet().removeIf(entry -> {
            WarEvent event = entry.getValue();
            return event.territoryId() == territoryId
                    && (event.kind() == WarEvent.Kind.TERRITORY_CAPTURE
                    || event.kind() == WarEvent.Kind.TERRITORY_LIBERATED);
        });

        TerritoryInfoState resolved = resolveTerritoryInfo(observation, snapshot);
        territoryInfo.put(territoryId, resolved);
        refreshEventViews();
        return identityChanged;
    }

    /**
     * Projects every in-game /territory owner observation onto a downloaded
     * snapshot. This keeps the command authoritative across later map refreshes
     * without changing the underlying geometry or exact chunk outcomes.
     */
    public NodesOverlaySnapshot applyTerritoryOwnership(NodesOverlaySnapshot snapshot) {
        if (snapshot == null || territoryInfo.isEmpty()) {
            return snapshot;
        }
        Map<Integer, TerritoryOwnershipOverride> overrides = new HashMap<>();
        territoryInfo.forEach((territoryId, state) -> overrides.put(
                territoryId,
                new TerritoryOwnershipOverride(
                        state.observation().ownerTown(),
                        state.observation().occupierTown()
                )
        ));
        return snapshot.withTerritoryOwnership(overrides);
    }

    public List<WarEvent> events() {
        return eventViews().history();
    }

    public List<WarEvent> attackEvents() {
        return eventViews().attacks();
    }

    private EventViews eventViews() {
        return cachedEvents;
    }

    // Called under the mutation monitor; readers retain the previous immutable
    // view until the new one is complete rather than blocking the render thread.
    private void refreshEventViews() {
        List<WarEvent> result = new ArrayList<>(events.values());
        result.sort(Comparator.comparing(WarEvent::updatedAt).reversed());
        List<WarEvent> history = List.copyOf(result);
        cachedEvents = new EventViews(history, history.stream()
                .filter(event -> event.kind() == WarEvent.Kind.ATTACK).toList());
    }

    /**
     * Returns live captures that override the downloaded map for this client
     * session. They remain active until superseded by a newer in-game outcome.
     */
    public List<WarEvent> activeCaptureEvents() {
        List<WarEvent> result = new ArrayList<>(events().stream()
                .filter(event -> switch (event.kind()) {
                    case CHUNK_CAPTURE -> capturedChunks.containsKey(
                            ChunkCoordinate.pack(event.chunkX(), event.chunkZ())
                    );
                    case TERRITORY_CAPTURE -> capturedTerritories.containsKey(event.territoryId());
                    case ATTACK, ATTACK_DEFENDED, CHUNK_DEFENDED, CHUNK_LIBERATED,
                             TERRITORY_LIBERATED -> false;
                })
                .toList());
        territoryInfo.values().stream()
                .filter(TerritoryInfoState::occupied)
                .map(TerritoryInfoState::event)
                .forEach(result::add);
        result.sort(Comparator.comparing(WarEvent::updatedAt).reversed());
        return List.copyOf(result);
    }

    /**
     * Includes provisional captures plus explicit defended/liberated chunk
     * overrides. An override must remain visible until the downloaded snapshot
     * stops reporting the former occupier, otherwise the stale stripe
     * immediately reappears after the chat message.
     */
    public List<WarEvent> activeOccupationEvents() {
        List<WarEvent> result = new ArrayList<>(events().stream()
                .filter(event -> switch (event.kind()) {
                    case CHUNK_CAPTURE -> capturedChunks.containsKey(
                            ChunkCoordinate.pack(event.chunkX(), event.chunkZ())
                    );
                    case TERRITORY_CAPTURE ->
                            capturedTerritories.containsKey(event.territoryId());
                    case CHUNK_DEFENDED, CHUNK_LIBERATED -> liberatedChunks.containsKey(
                            ChunkCoordinate.pack(event.chunkX(), event.chunkZ())
                    );
                    case TERRITORY_LIBERATED ->
                            liberatedTerritories.containsKey(event.territoryId());
                    case ATTACK, ATTACK_DEFENDED -> false;
                })
                .toList());
        territoryInfo.values().stream()
                .map(TerritoryInfoState::event)
                .forEach(result::add);
        result.sort(Comparator.comparing(WarEvent::updatedAt).reversed());
        return List.copyOf(result);
    }

    public List<WarEvent> eventsForTerritory(int territoryId) {
        return events().stream().filter(event -> event.territoryId() == territoryId).toList();
    }

    public WarEvent eventAtChunk(int chunkX, int chunkZ) {
        return events.values().stream()
                .filter(event -> event.chunkX() == chunkX && event.chunkZ() == chunkZ)
                .max(Comparator.comparing(WarEvent::updatedAt))
                .orElse(null);
    }

    public LiveCapture captureAtChunk(int chunkX, int chunkZ, int containingTerritoryId) {
        long chunkKey = ChunkCoordinate.pack(chunkX, chunkZ);
        LiveCapture chunk = capturedChunks.get(chunkKey);
        if (chunk != null) {
            return chunk;
        }
        if (liberatedChunks.containsKey(chunkKey)) {
            return null;
        }
        LiveCapture territory = capturedTerritories.get(containingTerritoryId);
        if (territory != null) {
            return territory;
        }
        if (liberatedTerritories.containsKey(containingTerritoryId)) {
            return null;
        }
        TerritoryInfoState observed = territoryInfo.get(containingTerritoryId);
        return observed != null && observed.occupied() ? observed.capture() : null;
    }

    public LiveCapture captureForTerritory(int territoryId) {
        LiveCapture captured = capturedTerritories.get(territoryId);
        if (captured != null) {
            return captured;
        }
        if (liberatedTerritories.containsKey(territoryId)) {
            return null;
        }
        TerritoryInfoState observed = territoryInfo.get(territoryId);
        return observed != null && observed.occupied() ? observed.capture() : null;
    }

    /**
     * Returns the faction currently controlling one exact territory chunk.
     * Live war outcomes and /territory observations take priority, followed by
     * the downloaded occupation and finally the owning/annexing town.
     */
    public TerritoryController controllerAtChunk(
            TerritoryView territory,
            int chunkX,
            int chunkZ
    ) {
        if (territory == null) {
            return null;
        }
        int territoryId = territory.territory().id();
        LiveCapture live = captureAtChunk(chunkX, chunkZ, territoryId);
        if (live != null) {
            return new TerritoryController(live.occupierTown(), live.occupierNation());
        }

        long chunkKey = ChunkCoordinate.pack(chunkX, chunkZ);
        TerritoryInfoState observed = territoryInfo.get(territoryId);
        boolean returnedToOwner = liberatedChunks.containsKey(chunkKey)
                || liberatedTerritories.containsKey(territoryId)
                || observed != null && !observed.occupied();
        TownRecord controller = !returnedToOwner && territory.occupied()
                ? territory.occupier()
                : territory.owner();
        NationRecord nation = !returnedToOwner && territory.occupied()
                ? territory.occupierNation()
                : territory.ownerNation();
        if (controller == null && nation == null) {
            return null;
        }
        return new TerritoryController(
                controller == null ? null : controller.name(),
                nation == null ? null : nation.name()
        );
    }

    public static WarRelation relationToController(
            TerritoryController controller,
            NodesOverlaySnapshot snapshot,
            String localPlayerName
    ) {
        if (controller == null || snapshot == null) {
            return WarRelation.UNKNOWN;
        }
        TownRecord town = snapshot.town(controller.town());
        return relationToFaction(
                town == null ? controller.town() : town.name(),
                town != null && town.nation() != null ? town.nation() : controller.nation(),
                town,
                snapshot,
                localPlayerName
        );
    }

    public boolean underAttack(int territoryId) {
        return events.values().stream()
                .anyMatch(event -> event.territoryId() == territoryId && event.kind() == WarEvent.Kind.ATTACK);
    }

    /**
     * Town conquest/plunder announcements are town-wide terminal outcomes and
     * do not include coordinates. Clear every active flag aimed at that town;
     * the subsequent towns.json refresh supplies the new ownership state.
     */
    public synchronized boolean completeAttacksForTown(String townName, NodesOverlaySnapshot snapshot) {
        if (townName == null || townName.isBlank()) {
            return false;
        }
        boolean changed = events.entrySet().removeIf(entry -> {
            WarEvent event = entry.getValue();
            if (event.kind() != WarEvent.Kind.ATTACK) {
                return false;
            }
            if (equalsIgnoreCase(event.reportedTown(), townName)) {
                return true;
            }
            TerritoryView territory = event.territoryId() < 0 || snapshot == null
                    ? null
                    : snapshot.territory(event.territoryId());
            return territory != null && (equalsIgnoreCase(territory.ownerTownName(), townName)
                    || equalsIgnoreCase(territory.occupierTownName(), townName));
        });
        if (changed) {
            refreshEventViews();
            clearInactiveWaypointSuppressions();
        }
        return changed;
    }

    public void removeWaypointForTerritory(int territoryId) {
        suppressWaypointForTerritory(territoryId);
        waypoints.clear();
    }

    void suppressWaypointForTerritory(int territoryId) {
        suppressedWaypointTerritories.add(territoryId);
    }

    public void restoreWaypointForTerritory(int territoryId) {
        suppressedWaypointTerritories.remove(territoryId);
    }

    public boolean hasAutomaticWaypointForTerritory(int territoryId) {
        return !suppressedWaypointTerritories.contains(territoryId)
                && events.values().stream().anyMatch(event ->
                event.territoryId() == territoryId && event.kind() == WarEvent.Kind.ATTACK);
    }

    public boolean hasActiveAttackForTerritory(int territoryId) {
        return events.values().stream().anyMatch(event ->
                event.territoryId() == territoryId && event.kind() == WarEvent.Kind.ATTACK);
    }

    public String debugSummary() {
        return events.size() + " active events, "
                + capturedChunks.size() + " captured chunks, "
                + capturedTerritories.size() + " captured territories, "
                + liberatedChunks.size() + " liberated chunks, "
                + liberatedTerritories.size() + " liberated territories, "
                + territoryInfo.size() + " territory info overrides";
    }

    public void clear() {
        clearTrackedState();
        waypoints.clear();
    }

    synchronized void clearTrackedState() {
        events.clear();
        capturedChunks.clear();
        capturedTerritories.clear();
        liberatedChunks.clear();
        liberatedTerritories.clear();
        territoryInfo.clear();
        suppressedWaypointTerritories.clear();
        localPlayerName = "";
        lastExpirySecond = Long.MIN_VALUE;
        refreshEventViews();
    }

    private void clearInactiveWaypointSuppressions() {
        suppressedWaypointTerritories.removeIf(territoryId ->
                !hasActiveAttackForTerritory(territoryId));
    }

    private WarEvent applyChunkResolution(
            WarEvent.Kind kind,
            String keyPrefix,
            String player,
            String actingTown,
            String actingNation,
            String reportedTown,
            int chunkX,
            int chunkZ,
            ResidentRecord actor,
            TownRecord actorTown,
            NodesOverlaySnapshot snapshot,
            String localPlayerName,
            Instant now
    ) {
        TerritoryView territory = snapshot.territoryAtChunk(chunkX, chunkZ);
        int territoryId = territory == null ? -1 : territory.territory().id();
        long chunkKey = ChunkCoordinate.pack(chunkX, chunkZ);
        capturedChunks.remove(chunkKey);
        if (kind == WarEvent.Kind.CHUNK_LIBERATED
                || kind == WarEvent.Kind.CHUNK_DEFENDED) {
            liberatedChunks.put(chunkKey, now);
        } else {
            liberatedChunks.remove(chunkKey);
        }
        events.entrySet().removeIf(entry ->
                (sameChunk(entry.getValue(), chunkX, chunkZ)
                        && entry.getValue().kind() != WarEvent.Kind.TERRITORY_CAPTURE
                        && entry.getValue().kind() != WarEvent.Kind.TERRITORY_LIBERATED));
        clearInactiveWaypointSuppressions();
        return new WarEvent(
                keyPrefix + chunkX + ":" + chunkZ,
                kind,
                player,
                actingTown,
                actingNation,
                reportedTown,
                territoryId,
                chunkX * 16 + 8,
                128,
                chunkZ * 16 + 8,
                chunkX,
                chunkZ,
                relationTo(reportedTown, snapshot, localPlayerName),
                WarMarkerType.SHIELD,
                participantColor(actor, actorTown, actingNation, territory, snapshot),
                now
        );
    }

    private void collapseSupersededEvents(NodesOverlaySnapshot snapshot) {
        List<WarEvent> resolvedEvents = List.copyOf(events.values());
        Map<Long, List<WarEvent>> chunkOutcomes = new HashMap<>();
        Map<Integer, List<WarEvent>> territoryOutcomes = new HashMap<>();
        for (WarEvent event : resolvedEvents) {
            if (event.kind() == WarEvent.Kind.TERRITORY_CAPTURE
                    || event.kind() == WarEvent.Kind.TERRITORY_LIBERATED) {
                territoryOutcomes.computeIfAbsent(event.territoryId(), ignored -> new ArrayList<>()).add(event);
            } else if (event.kind() != WarEvent.Kind.ATTACK) {
                chunkOutcomes.computeIfAbsent(ChunkCoordinate.pack(event.chunkX(), event.chunkZ()),
                        ignored -> new ArrayList<>()).add(event);
            }
        }
        events.entrySet().removeIf(entry -> {
            WarEvent previous = entry.getValue();
            if (chunkOutcomes.getOrDefault(ChunkCoordinate.pack(previous.chunkX(), previous.chunkZ()), List.of())
                    .stream().anyMatch(replacement -> supersedes(replacement, previous, snapshot))) {
                return true;
            }
            if (snapshot.duplicateChunkAssignments() > 0) {
                // Malformed overlapping definitions can match more than the
                // single territory returned by the chunk index. Preserve that fallback.
                return territoryOutcomes.values().stream().flatMap(List::stream)
                        .anyMatch(replacement -> supersedes(replacement, previous, snapshot));
            }
            // resolveEvent has already resolved positional events through the
            // current chunk index, so only this territory's outcomes can supersede them.
            return territoryOutcomes.getOrDefault(previous.territoryId(), List.of()).stream()
                    .anyMatch(replacement -> supersedes(replacement, previous, snapshot));
        });
        clearInactiveWaypointSuppressions();
    }

    private static boolean supersedes(
            WarEvent replacement,
            WarEvent previous,
            NodesOverlaySnapshot snapshot
    ) {
        if (replacement.key().equals(previous.key())
                || replacement.updatedAt().isBefore(previous.updatedAt())) {
            return false;
        }
        if (replacement.kind() == WarEvent.Kind.CHUNK_CAPTURE) {
            return previous.kind() == WarEvent.Kind.ATTACK
                    && sameChunk(previous, replacement.chunkX(), replacement.chunkZ());
        }
        if (replacement.kind() == WarEvent.Kind.ATTACK_DEFENDED
                || replacement.kind() == WarEvent.Kind.CHUNK_DEFENDED
                || replacement.kind() == WarEvent.Kind.CHUNK_LIBERATED) {
            return sameChunk(previous, replacement.chunkX(), replacement.chunkZ())
                    && previous.kind() != WarEvent.Kind.TERRITORY_CAPTURE
                    && previous.kind() != WarEvent.Kind.TERRITORY_LIBERATED;
        }
        if (replacement.kind() != WarEvent.Kind.TERRITORY_CAPTURE
                && replacement.kind() != WarEvent.Kind.TERRITORY_LIBERATED) {
            return false;
        }
        TerritoryView territory = snapshot.territory(replacement.territoryId());
        TerritoryDefinition definition = territory == null ? null : territory.territory();
        return supersededByTerritoryOutcome(
                previous,
                replacement.territoryId(),
                definition
        );
    }

    private static boolean supersededByTerritoryOutcome(
            WarEvent event,
            int territoryId,
            TerritoryDefinition definition
    ) {
        if (event.kind() == WarEvent.Kind.TERRITORY_CAPTURE
                || event.kind() == WarEvent.Kind.TERRITORY_LIBERATED) {
            return event.territoryId() == territoryId;
        }
        if (sameKnownTerritory(event, territoryId)) {
            return true;
        }
        return definition != null && definitionContainsChunk(
                definition,
                event.chunkX(),
                event.chunkZ()
        );
    }

    private static boolean sameKnownTerritory(WarEvent event, int territoryId) {
        return territoryId >= 0 && event.territoryId() == territoryId;
    }

    private static boolean sameChunk(WarEvent event, int chunkX, int chunkZ) {
        return event.chunkX() == chunkX && event.chunkZ() == chunkZ;
    }

    private static boolean isResolvedOutcome(WarEvent.Kind kind) {
        return kind == WarEvent.Kind.ATTACK_DEFENDED
                || kind == WarEvent.Kind.CHUNK_DEFENDED
                || kind == WarEvent.Kind.CHUNK_LIBERATED
                || kind == WarEvent.Kind.TERRITORY_LIBERATED;
    }

    private static boolean definitionContainsChunk(
            TerritoryDefinition definition,
            int chunkX,
            int chunkZ
    ) {
        int[] chunks = definition.chunks();
        for (int index = 0; index + 1 < chunks.length; index += 2) {
            if (chunks[index] == chunkX && chunks[index + 1] == chunkZ) {
                return true;
            }
        }
        return false;
    }

    private static WarMarkerType markerFor(WarRelation relation) {
        return WarMarkerType.SWORD;
    }

    private static WarEvent resolveEvent(
            WarEvent event,
            NodesOverlaySnapshot snapshot,
            String localPlayerName
    ) {
        ResidentRecord attacker = snapshot.resident(event.attackingPlayer());
        TownRecord attackerTown = attacker == null ? null : snapshot.town(attacker.town());
        String attackingTown = attackerTown != null
                ? attackerTown.name()
                : attacker != null && attacker.town() != null
                ? attacker.town()
                : event.attackingTown();
        String attackingNation = attackerTown != null && attackerTown.nation() != null
                ? attackerTown.nation()
                : attacker != null && attacker.nation() != null
                ? attacker.nation()
                : event.attackingNation();

        TerritoryView territory;
        int territoryId;
        int blockX = event.blockX();
        int blockY = event.blockY();
        int blockZ = event.blockZ();
        int chunkX = event.chunkX();
        int chunkZ = event.chunkZ();
        if (event.kind() == WarEvent.Kind.ATTACK
                || event.kind() == WarEvent.Kind.ATTACK_DEFENDED) {
            territory = snapshot.territoryAtBlock(blockX, blockZ);
            territoryId = territory == null ? -1 : territory.territory().id();
        } else if (event.kind() == WarEvent.Kind.CHUNK_CAPTURE
                || event.kind() == WarEvent.Kind.CHUNK_DEFENDED
                || event.kind() == WarEvent.Kind.CHUNK_LIBERATED) {
            territory = snapshot.territoryAtChunk(chunkX, chunkZ);
            territoryId = territory == null ? -1 : territory.territory().id();
        } else {
            territory = snapshot.territory(event.territoryId());
            territoryId = event.territoryId();
            if (territory != null) {
                TerritoryDefinition definition = territory.territory();
                blockX = definition.coreX();
                blockY = 128;
                blockZ = definition.coreZ();
                chunkX = definition.coreChunkX();
                chunkZ = definition.coreChunkZ();
            }
        }

        WarRelation relation = event.kind() == WarEvent.Kind.ATTACK
                ? relationToAttacker(
                        attacker,
                        attackerTown,
                        attackingTown,
                        attackingNation,
                        snapshot,
                        localPlayerName
                )
                : relationTo(
                        event.reportedTown(),
                        snapshot,
                        localPlayerName
                );
        WarMarkerType marker = switch (event.kind()) {
            case ATTACK -> markerFor(relation);
            case CHUNK_CAPTURE, TERRITORY_CAPTURE -> WarMarkerType.FLAG;
            case ATTACK_DEFENDED, CHUNK_DEFENDED, CHUNK_LIBERATED,
                    TERRITORY_LIBERATED -> WarMarkerType.SHIELD;
        };
        RgbColor color;
        if (event.kind() == WarEvent.Kind.ATTACK) {
            TownRecord targetTown = snapshot.town(event.reportedTown());
            color = territory != null || targetTown != null
                    ? targetColor(event.reportedTown(), territory, snapshot)
                    : event.color();
        } else {
            boolean attackerResolved = attacker != null
                    || attackerTown != null
                    || snapshot.nation(attackingNation) != null;
            color = attackerResolved
                    ? attackerColor(attackerTown, attackingNation, snapshot)
                    : event.color();
        }
        if (color == null) {
            color = RgbColor.NEUTRAL;
        }

        return new WarEvent(
                event.key(),
                event.kind(),
                event.attackingPlayer(),
                attackingTown,
                attackingNation,
                event.reportedTown(),
                territoryId,
                blockX,
                blockY,
                blockZ,
                chunkX,
                chunkZ,
                relation,
                marker,
                color,
                event.updatedAt()
        );
    }

    private static LiveCapture liveCapture(WarEvent event) {
        return new LiveCapture(
                event.attackingPlayer(),
                event.attackingTown(),
                event.attackingNation(),
                event.color(),
                event.updatedAt()
        );
    }

    private static TerritoryInfoState resolveTerritoryInfo(
            TerritoryInfoObservation observation,
            NodesOverlaySnapshot snapshot
    ) {
        TerritoryView territory = snapshot.territory(observation.territoryId());
        TerritoryDefinition definition = territory == null ? null : territory.territory();
        TownRecord occupier = snapshot.town(observation.occupierTown());
        String occupierNation = occupier == null ? null : occupier.nation();
        RgbColor color;
        if (!observation.occupied()) {
            color = territory == null ? RgbColor.NEUTRAL : territory.ownerColor();
        } else if (occupier != null) {
            color = attackerColor(occupier, occupierNation, snapshot);
        } else if (territory != null
                && territory.occupier() != null
                && territory.occupier().name().equalsIgnoreCase(observation.occupierTown())) {
            color = territory.occupierColor();
            occupierNation = territory.occupier().nation();
        } else {
            color = RgbColor.NEUTRAL;
        }
        int blockX = definition == null ? 0 : definition.coreX();
        int blockZ = definition == null ? 0 : definition.coreZ();
        int chunkX = definition == null ? 0 : definition.coreChunkX();
        int chunkZ = definition == null ? 0 : definition.coreChunkZ();
        return new TerritoryInfoState(
                observation,
                occupierNation,
                color,
                blockX,
                blockZ,
                chunkX,
                chunkZ
        );
    }

    private record TerritoryInfoState(
            TerritoryInfoObservation observation,
            String occupierNation,
            RgbColor color,
            int blockX,
            int blockZ,
            int chunkX,
            int chunkZ
    ) {

        private boolean occupied() {
            return observation.occupied();
        }

        private LiveCapture capture() {
            return new LiveCapture(
                    "",
                    observation.occupierTown(),
                    occupierNation,
                    color,
                    observation.observedAt()
            );
        }

        private WarEvent event() {
            return new WarEvent(
                    "territory-info:" + observation.territoryId(),
                    occupied()
                            ? WarEvent.Kind.TERRITORY_CAPTURE
                            : WarEvent.Kind.TERRITORY_LIBERATED,
                    "",
                    occupied() ? observation.occupierTown() : observation.ownerTown(),
                    occupierNation,
                    observation.ownerTown(),
                    observation.territoryId(),
                    blockX,
                    128,
                    blockZ,
                    chunkX,
                    chunkZ,
                    WarRelation.UNKNOWN,
                    occupied() ? WarMarkerType.FLAG : WarMarkerType.SHIELD,
                    color,
                    observation.observedAt()
            );
        }
    }

    private static WarRelation relationTo(
            String targetTownName,
            NodesOverlaySnapshot snapshot,
            String localPlayerName
    ) {
        TownRecord target = snapshot.town(targetTownName);
        if (target == null) {
            return WarRelation.UNKNOWN;
        }
        return relationToFaction(
                target.name(),
                target.nation(),
                target,
                snapshot,
                localPlayerName
        );
    }

    /**
     * Resolves a port owner against the local player's faction. Port info
     * normally reports a town, but accepting a nation name as well keeps the
     * marker useful if the server changes that field's format.
     */
    public static WarRelation relationToOwner(
            String ownerName,
            NodesOverlaySnapshot snapshot,
            String localPlayerName
    ) {
        if (ownerName == null || ownerName.isBlank() || snapshot == null) {
            return WarRelation.UNKNOWN;
        }
        TownRecord targetTown = snapshot.town(ownerName);
        if (targetTown != null) {
            return relationToFaction(
                    targetTown.name(),
                    targetTown.nation(),
                    targetTown,
                    snapshot,
                    localPlayerName
            );
        }
        NationRecord targetNation = snapshot.nation(ownerName);
        if (targetNation == null) {
            return WarRelation.UNKNOWN;
        }
        return relationToFaction(
                null,
                targetNation.name(),
                null,
                snapshot,
                localPlayerName
        );
    }

    /**
     * Active attack markers describe who placed the attack, not the faction
     * that currently owns or occupies the target. Allied attackers are treated
     * as friendly for attack-display purposes so both allied and same-nation
     * support use the green friendly badge and diamond sword.
     */
    private static WarRelation relationToAttacker(
            ResidentRecord attacker,
            TownRecord attackerTown,
            String attackingTown,
            String attackingNation,
            NodesOverlaySnapshot snapshot,
            String localPlayerName
    ) {
        TownRecord resolvedTown = attackerTown != null
                ? attackerTown
                : snapshot.town(attackingTown);
        String resolvedTownName = resolvedTown != null
                ? resolvedTown.name()
                : attacker != null && attacker.town() != null
                ? attacker.town()
                : attackingTown;
        String resolvedNation = resolvedTown != null && resolvedTown.nation() != null
                ? resolvedTown.nation()
                : attacker != null && attacker.nation() != null
                ? attacker.nation()
                : attackingNation;
        if (attacker == null
                && resolvedTown == null
                && snapshot.nation(resolvedNation) == null) {
            return WarRelation.UNKNOWN;
        }
        WarRelation relation = relationToFaction(
                resolvedTownName,
                resolvedNation,
                resolvedTown,
                snapshot,
                localPlayerName
        );
        return relation == WarRelation.ALLIED ? WarRelation.FRIENDLY : relation;
    }

    private static WarRelation relationToFaction(
            String targetTownName,
            String targetNationName,
            TownRecord target,
            NodesOverlaySnapshot snapshot,
            String localPlayerName
    ) {
        ResidentRecord local = snapshot.resident(localPlayerName);
        if (local == null) {
            return WarRelation.UNKNOWN;
        }
        TownRecord localTown = snapshot.town(local.town());
        String localNation = localTown == null ? local.nation() : localTown.nation();
        if (equalsIgnoreCase(local.town(), targetTownName)
                || equalsIgnoreCase(localNation, targetNationName)) {
            return WarRelation.FRIENDLY;
        }
        if (localTown != null && (containsIgnoreCase(localTown.allies(), targetTownName)
                || target != null
                && containsIgnoreCase(target.allies(), localTown.name()))) {
            return WarRelation.ALLIED;
        }
        NationRecord localNationRecord = snapshot.nation(localNation);
        NationRecord targetNation = snapshot.nation(targetNationName);
        if (localNationRecord != null && targetNation != null) {
            if (containsIgnoreCase(localNationRecord.allies(), targetNation.name())
                    || containsIgnoreCase(targetNation.allies(), localNationRecord.name())) {
                return WarRelation.ALLIED;
            }
            if (containsIgnoreCase(localNationRecord.enemies(), targetNation.name())
                    || containsIgnoreCase(targetNation.enemies(), localNationRecord.name())) {
                return WarRelation.HOSTILE;
            }
        }
        if (localTown != null && (containsIgnoreCase(localTown.enemies(), targetTownName)
                || target != null
                && containsIgnoreCase(target.enemies(), localTown.name()))) {
            return WarRelation.HOSTILE;
        }
        return WarRelation.UNKNOWN;
    }

    private static RgbColor targetColor(
            String targetTownName,
            TerritoryView territory,
            NodesOverlaySnapshot snapshot
    ) {
        if (territory != null) {
            return territory.ownerColor();
        }
        TownRecord town = snapshot.town(targetTownName);
        if (town == null) {
            return RgbColor.NEUTRAL;
        }
        NationRecord nation = snapshot.nation(town.nation());
        return nation == null ? town.color() : nation.color();
    }

    private static RgbColor attackerColor(
            TownRecord attackingTown,
            String attackingNation,
            NodesOverlaySnapshot snapshot
    ) {
        NationRecord nation = snapshot.nation(attackingNation);
        if (nation != null) {
            return nation.color();
        }
        return attackingTown == null ? RgbColor.NEUTRAL : attackingTown.color();
    }

    private static RgbColor participantColor(
            ResidentRecord actor,
            TownRecord actorTown,
            String actingNation,
            TerritoryView territory,
            NodesOverlaySnapshot snapshot
    ) {
        if (actor != null || actorTown != null || snapshot.nation(actingNation) != null) {
            return attackerColor(actorTown, actingNation, snapshot);
        }
        return territory == null ? RgbColor.NEUTRAL : territory.ownerColor();
    }

    private static boolean containsIgnoreCase(Iterable<String> values, String wanted) {
        if (wanted == null) {
            return false;
        }
        for (String value : values) {
            if (wanted.equalsIgnoreCase(value)) {
                return true;
            }
        }
        return false;
    }

    private static boolean equalsIgnoreCase(String first, String second) {
        return first != null && second != null && first.equalsIgnoreCase(second);
    }

    private WarRelation occupationRelationAtChunk(
            TerritoryView territory,
            int chunkX,
            int chunkZ,
            int territoryId,
            NodesOverlaySnapshot snapshot,
            String localPlayerName
    ) {
        long chunkKey = ChunkCoordinate.pack(chunkX, chunkZ);
        LiveCapture live = captureAtChunk(chunkX, chunkZ, territoryId);
        if (live != null) {
            TownRecord liveTown = snapshot.town(live.occupierTown());
            return relationToFaction(
                    live.occupierTown(),
                    live.occupierNation(),
                    liveTown,
                    snapshot,
                    localPlayerName
            );
        }
        if (liberatedChunks.containsKey(chunkKey)
                || liberatedTerritories.containsKey(territoryId)
                || territoryInfo.containsKey(territoryId)
                || territory == null
                || !territory.occupied()) {
            return WarRelation.UNKNOWN;
        }
        TownRecord occupier = territory.occupier();
        return relationToFaction(
                occupier.name(),
                territory.occupierNationName(),
                occupier,
                snapshot,
                localPlayerName
        );
    }

    private static boolean isFriendlyOrAllied(WarRelation relation) {
        return relation == WarRelation.FRIENDLY || relation == WarRelation.ALLIED;
    }

    private static boolean sameNullableName(String first, String second) {
        if (first == null || first.isBlank()) {
            return second == null || second.isBlank();
        }
        return second != null && first.equalsIgnoreCase(second);
    }

    public record TerritoryController(String town, String nation) {

        public TerritoryController {
            town = town == null || town.isBlank() ? null : town.trim();
            nation = nation == null || nation.isBlank() ? null : nation.trim();
        }
    }
}
