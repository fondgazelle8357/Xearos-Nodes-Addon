package me.fond.nodesoverlay.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.fond.nodesoverlay.model.NationRecord;
import me.fond.nodesoverlay.model.NodeDefinition;
import me.fond.nodesoverlay.model.PortRecord;
import me.fond.nodesoverlay.model.OwnershipState;
import me.fond.nodesoverlay.model.ResidentRecord;
import me.fond.nodesoverlay.model.RgbColor;
import me.fond.nodesoverlay.model.TerritoryDefinition;
import me.fond.nodesoverlay.model.TerritoryView;
import me.fond.nodesoverlay.model.TownRecord;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class NodesOverlayDataParser {
    // A service owns one parser. Preserve geometry across towns/ports-only updates.
    private String cachedWorldJson;
    private WorldData cachedWorld;

    private record WorldData(Map<String, NodeDefinition> nodes,
                             Map<Integer, TerritoryDefinition> definitions,
                             ChunkTerritoryIndex chunkIndex, int duplicates) { }


    public NodesOverlaySnapshot parse(String townsJson, String worldJson) {
        return parse(townsJson, worldJson, null);
    }

    /**
     * Parses a new data snapshot while retaining a previous owner when the new
     * source still contains that town among multiple valid owner candidates.
     * This prevents randomized JSON object order from making ambiguous
     * territories flicker between owners on each refresh.
     */
    public NodesOverlaySnapshot parse(
            String townsJson,
            String worldJson,
            NodesOverlaySnapshot previousSnapshot
    ) {
        return parse(townsJson, worldJson, emptyPortsJson(), previousSnapshot);
    }

    public synchronized NodesOverlaySnapshot parse(
            String townsJson,
            String worldJson,
            String portsJson,
            NodesOverlaySnapshot previousSnapshot
    ) {
        JsonObject townsRoot = requireRoot(townsJson, "towns");
        JsonObject portsRoot = requireRoot(portsJson, "ports");

        Map<String, NationRecord> nations = parseNations(townsRoot.getAsJsonObject("nations"));
        Map<String, String> townNations = new HashMap<>();
        for (NationRecord nation : nations.values()) {
            for (String town : nation.towns()) {
                townNations.put(town.toLowerCase(Locale.ROOT), nation.name());
            }
        }
        Map<String, TownRecord> towns = parseTowns(townsRoot.getAsJsonObject("towns"), townNations);
        Map<String, ResidentRecord> residents = parseResidents(townsRoot.getAsJsonObject("residents"));
        WorldData world = worldData(worldJson);
        Map<String, NodeDefinition> nodes = world.nodes();
        Map<String, PortRecord> ports = parsePorts(portsRoot.getAsJsonObject("ports"));
        Map<Integer, TerritoryDefinition> definitions = world.definitions();
        ChunkTerritoryIndex chunkIndex = world.chunkIndex();
        int duplicates = world.duplicates();

        Map<Integer, List<TownRecord>> memberships = new HashMap<>();
        Map<Integer, TownRecord> capturedBy = new HashMap<>();
        List<TownRecord> sortedTowns = new ArrayList<>(towns.values());
        sortedTowns.sort(Comparator.comparing(TownRecord::name, String.CASE_INSENSITIVE_ORDER));
        for (TownRecord town : sortedTowns) {
            for (int id : town.directTerritories()) {
                addOwnerCandidate(memberships, id, town);
            }
            for (int id : town.annexed()) {
                addOwnerCandidate(memberships, id, town);
            }
            for (int id : town.claimed()) {
                addOwnerCandidate(memberships, id, town);
            }
            for (int id : town.cores()) {
                addOwnerCandidate(memberships, id, town);
            }
            for (int id : town.captured()) {
                capturedBy.putIfAbsent(id, town);
            }
        }

        Map<Integer, TerritoryView> views = new LinkedHashMap<>();
        definitions.values().stream().sorted(Comparator.comparingInt(TerritoryDefinition::id)).forEach(territory -> {
            TownRecord occupier = capturedBy.get(territory.id());
            TerritoryView previousView = previousSnapshot == null
                    ? null
                    : previousSnapshot.territory(territory.id());
            TownRecord previousOwner = previousView == null ? null : previousView.owner();
            OwnerResolution resolution = chooseOwner(
                    territory.id(),
                    memberships.getOrDefault(territory.id(), List.of()),
                    previousOwner
            );
            TownRecord owner = resolution.owner();
            if (owner == null && occupier != null) {
                // When the feed lists only a captured holder, there is no
                // separate owner to stripe against. Treat the holder as the
                // effective owner until authoritative ownership appears.
                owner = occupier;
            }
            if (sameTown(owner, occupier)) {
                occupier = null;
            }
            NationRecord ownerNation = nationFor(owner, nations);
            NationRecord occupierNation = nationFor(occupier, nations);
            OwnershipState state = ownershipState(territory.id(), owner, occupier);
            RgbColor ownerColor = colorFor(owner, ownerNation);
            RgbColor occupierColor = colorFor(occupier, occupierNation);
            boolean townCore = owner != null && owner.cores().contains(territory.id());

            views.put(territory.id(), new TerritoryView(
                    territory,
                    owner,
                    resolution.candidates(),
                    ownerNation,
                    occupier,
                    occupierNation,
                    state,
                    ownerColor,
                    occupierColor,
                    townCore
            ));
        });

        return new NodesOverlaySnapshot(views, nodes, ports, towns, nations, residents,
                chunkIndex, Instant.now(), duplicates, previousSnapshot);
    }

    private WorldData worldData(String worldJson) {
        if (cachedWorld != null && java.util.Objects.equals(worldJson, cachedWorldJson)) {
            return cachedWorld;
        }
        JsonObject worldRoot = requireRoot(worldJson, "world");
        Map<String, NodeDefinition> nodes = parseNodes(worldRoot.getAsJsonObject("nodes"));
        Map<Integer, TerritoryDefinition> definitions =
                parseTerritories(worldRoot.getAsJsonObject("territories"));
        if (definitions.isEmpty()) {
            throw new IllegalArgumentException("world.json contains no territories");
        }
        definitions = validateWorldReferences(definitions, nodes);

        int expectedChunks = definitions.values().stream().mapToInt(value -> value.chunks().length / 2).sum();
        ChunkTerritoryIndex chunkIndex = new ChunkTerritoryIndex(expectedChunks);
        int duplicates = 0;
        Set<Integer> duplicateTerritories = new HashSet<>();
        for (TerritoryDefinition territory : definitions.values()) {
            int[] chunks = territory.chunks();
            for (int i = 0; i + 1 < chunks.length; i += 2) {
                long key = me.fond.nodesoverlay.model.ChunkCoordinate.pack(chunks[i], chunks[i + 1]);
                int previous = chunkIndex.put(key, territory.id());
                if (previous >= 0 && previous != territory.id()) {
                    duplicates++;
                    duplicateTerritories.add(previous);
                    duplicateTerritories.add(territory.id());
                }
            }
        }
        if (duplicates > 0) {
            Map<Integer, TerritoryDefinition> marked = new LinkedHashMap<>();
            for (TerritoryDefinition territory : definitions.values()) {
                marked.put(
                        territory.id(),
                        duplicateTerritories.contains(territory.id())
                                ? territory.withDataIssues(List.of("duplicate chunk ownership"))
                                : territory
                );
            }
            definitions = marked;
        }

        WorldData result = new WorldData(Map.copyOf(nodes), Map.copyOf(definitions), chunkIndex, duplicates);
        cachedWorldJson = worldJson;
        cachedWorld = result;
        return result;
    }

    public void validate(String json, String expectedType) {
        requireRoot(json, expectedType);
    }

    private static JsonObject requireRoot(String json, String expectedType) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException(expectedType + ".json is empty");
        }
        JsonElement parsed = JsonParser.parseString(json);
        if (!parsed.isJsonObject()) {
            throw new IllegalArgumentException(expectedType + ".json root must be an object");
        }
        JsonObject root = parsed.getAsJsonObject();
        JsonObject meta = object(root, "meta");
        String type = string(meta, "type", "");
        if (!expectedType.equalsIgnoreCase(type)) {
            throw new IllegalArgumentException("Expected meta.type=" + expectedType + " but got " + type);
        }
        List<String> requiredCollections = switch (expectedType.toLowerCase(Locale.ROOT)) {
            case "towns" -> List.of("residents", "towns", "nations");
            case "world" -> List.of("nodes", "territories");
            case "ports" -> List.of("ports");
            default -> List.of();
        };
        for (String requiredCollection : requiredCollections) {
            if (!root.has(requiredCollection)
                    || !root.get(requiredCollection).isJsonObject()) {
                throw new IllegalArgumentException(
                        expectedType + ".json is missing " + requiredCollection
                );
            }
        }
        return root;
    }

    private static Map<String, NodeDefinition> parseNodes(JsonObject source) {
        Map<String, NodeDefinition> result = new LinkedHashMap<>();
        if (source == null) {
            return result;
        }
        for (Map.Entry<String, JsonElement> entry : source.entrySet()) {
            if (!entry.getValue().isJsonObject()) {
                continue;
            }
            String id = entry.getKey().toLowerCase(Locale.ROOT);
            JsonObject node = entry.getValue().getAsJsonObject();
            Set<String> known = Set.of("name", "icon", "cost", "priority", "income", "ore", "crops", "animals");
            Map<String, Double> modifiers = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> field : node.entrySet()) {
                if (!known.contains(field.getKey()) && field.getValue().isJsonPrimitive()
                        && field.getValue().getAsJsonPrimitive().isNumber()) {
                    modifiers.put(field.getKey(), field.getValue().getAsDouble());
                }
            }
            result.put(id, new NodeDefinition(
                    id,
                    string(node, "name", entry.getKey()),
                    string(node, "icon", "barrier"),
                    doubleMap(node.get("cost")),
                    integer(node, "priority", 0),
                    doubleMap(node.get("income")),
                    doubleMap(node.get("ore")),
                    doubleMap(node.get("crops")),
                    doubleMap(node.get("animals")),
                    modifiers
            ));
        }
        return result;
    }

    private static Map<String, PortRecord> parsePorts(JsonObject source) {
        Map<String, PortRecord> result = new LinkedHashMap<>();
        if (source == null) {
            return result;
        }
        for (Map.Entry<String, JsonElement> entry : source.entrySet()) {
            if (!entry.getValue().isJsonObject()) {
                continue;
            }
            JsonObject value = entry.getValue().getAsJsonObject();
            String id = PortRecord.normalizeId(entry.getKey());
            result.put(id, new PortRecord(
                    id,
                    displayIdentifier(entry.getKey()),
                    new HashSet<>(stringList(value.get("groups"))),
                    integer(value, "x", 0),
                    integer(value, "z", 0),
                    null,
                    null,
                    null,
                    null,
                    null,
                    false,
                    Instant.EPOCH
            ));
        }
        return result;
    }

    private static Map<Integer, TerritoryDefinition> parseTerritories(JsonObject source) {
        Map<Integer, TerritoryDefinition> result = new LinkedHashMap<>();
        if (source == null) {
            return result;
        }
        for (Map.Entry<String, JsonElement> entry : source.entrySet()) {
            if (!entry.getValue().isJsonObject()) {
                continue;
            }
            int id;
            try {
                id = Integer.parseInt(entry.getKey());
            } catch (NumberFormatException ignored) {
                continue;
            }
            JsonObject territory = entry.getValue().getAsJsonObject();
            Set<String> issues = new java.util.LinkedHashSet<>();
            int[] core = safeIntArray(territory.get("core"));
            int[] coreChunk = safeIntArray(territory.get("coreChunk"));
            int[] rawChunks = safeIntArray(territory.get("chunks"));
            if (core.length < 2) {
                issues.add("core");
            }
            if (coreChunk.length < 2) {
                issues.add("core chunk");
            }
            if (rawChunks.length == 0) {
                issues.add("chunks");
                continue;
            }
            if ((rawChunks.length & 1) != 0) {
                issues.add("chunks");
            }

            int usableLength = rawChunks.length & ~1;
            int[] chunks = java.util.Arrays.copyOf(rawChunks, usableLength);
            if (chunks.length == 0) {
                issues.add("chunks");
                continue;
            }
            Set<Long> uniqueChunks = new HashSet<>(chunks.length / 2);
            int[] compactChunks = new int[chunks.length];
            int compactLength = 0;
            for (int index = 0; index + 1 < chunks.length; index += 2) {
                long packed = me.fond.nodesoverlay.model.ChunkCoordinate.pack(
                        chunks[index],
                        chunks[index + 1]
                );
                if (!uniqueChunks.add(packed)) {
                    issues.add("duplicate chunks");
                    continue;
                }
                compactChunks[compactLength++] = chunks[index];
                compactChunks[compactLength++] = chunks[index + 1];
            }
            chunks = java.util.Arrays.copyOf(compactChunks, compactLength);
            if (chunks.length == 0) {
                issues.add("chunks");
                continue;
            }

            int actualSize = chunks.length / 2;
            int declaredSize = integer(territory, "size", actualSize);
            if (declaredSize != actualSize) {
                issues.add("size");
            }

            int firstChunkX = chunks[0];
            int firstChunkZ = chunks[1];
            int minChunkX = firstChunkX;
            int maxChunkX = firstChunkX;
            int minChunkZ = firstChunkZ;
            int maxChunkZ = firstChunkZ;
            for (int i = 0; i + 1 < chunks.length; i += 2) {
                minChunkX = Math.min(minChunkX, chunks[i]);
                maxChunkX = Math.max(maxChunkX, chunks[i]);
                minChunkZ = Math.min(minChunkZ, chunks[i + 1]);
                maxChunkZ = Math.max(maxChunkZ, chunks[i + 1]);
            }

            int resolvedCoreX = core.length > 0 ? core[0] : firstChunkX * 16 + 8;
            int resolvedCoreZ = core.length > 1 ? core[1] : firstChunkZ * 16 + 8;
            int resolvedCoreChunkX = coreChunk.length > 0
                    ? coreChunk[0]
                    : Math.floorDiv(resolvedCoreX, 16);
            int resolvedCoreChunkZ = coreChunk.length > 1
                    ? coreChunk[1]
                    : Math.floorDiv(resolvedCoreZ, 16);
            boolean coreValid = resolvedCoreChunkX == Math.floorDiv(resolvedCoreX, 16)
                    && resolvedCoreChunkZ == Math.floorDiv(resolvedCoreZ, 16)
                    && uniqueChunks.contains(
                    me.fond.nodesoverlay.model.ChunkCoordinate.pack(
                            resolvedCoreChunkX,
                            resolvedCoreChunkZ
                    ));
            if (!coreValid) {
                issues.add("core chunk");
                int candidateCoreChunkX = Math.floorDiv(resolvedCoreX, 16);
                int candidateCoreChunkZ = Math.floorDiv(resolvedCoreZ, 16);
                if (!uniqueChunks.contains(
                        me.fond.nodesoverlay.model.ChunkCoordinate.pack(
                                candidateCoreChunkX,
                                candidateCoreChunkZ
                        ))) {
                    candidateCoreChunkX = firstChunkX;
                    candidateCoreChunkZ = firstChunkZ;
                    resolvedCoreX = candidateCoreChunkX * 16 + 8;
                    resolvedCoreZ = candidateCoreChunkZ * 16 + 8;
                }
                resolvedCoreChunkX = candidateCoreChunkX;
                resolvedCoreChunkZ = candidateCoreChunkZ;
            }

            result.put(id, new TerritoryDefinition(
                    id,
                    string(territory, "name", ""),
                    resolvedCoreX,
                    resolvedCoreZ,
                    resolvedCoreChunkX,
                    resolvedCoreChunkZ,
                    chunks,
                    actualSize,
                    safeIntegerList(territory.get("neighbors")),
                    bool(territory, "isEdge", false),
                    safeStringList(territory.get("nodes")),
                    integer(territory, "color", 0),
                    minChunkX * 16,
                    minChunkZ * 16,
                    (maxChunkX + 1) * 16,
                    (maxChunkZ + 1) * 16,
                    TerritoryGeometryBuilder.build(chunks, 1),
                    TerritoryGeometryBuilder.build(chunks, 4),
                    TerritoryGeometryBuilder.build(chunks, 16),
                    List.copyOf(issues)
            ));
        }
        return result;
    }

    private static Map<Integer, TerritoryDefinition> validateWorldReferences(
            Map<Integer, TerritoryDefinition> territories,
            Map<String, NodeDefinition> nodes
    ) {
        Map<Integer, TerritoryDefinition> result = new LinkedHashMap<>();
        for (TerritoryDefinition territory : territories.values()) {
            Set<String> issues = new java.util.LinkedHashSet<>();
            for (int neighbor : territory.neighbors()) {
                TerritoryDefinition neighborTerritory = territories.get(neighbor);
                if (neighborTerritory == null) {
                    issues.add("neighbor " + neighbor);
                }
                if (neighborTerritory != null && !neighborTerritory.neighbors().contains(territory.id())) {
                    issues.add("neighbor " + neighbor + " link");
                }
            }
            for (String node : territory.nodeIds()) {
                if (!nodes.containsKey(node.toLowerCase(Locale.ROOT))) {
                    issues.add("node " + node);
                }
            }
            result.put(
                    territory.id(),
                    issues.isEmpty() ? territory : territory.withDataIssues(List.copyOf(issues))
            );
        }
        return result;
    }

    private static Map<String, NationRecord> parseNations(JsonObject source) {
        Map<String, NationRecord> result = new LinkedHashMap<>();
        if (source == null) {
            return result;
        }
        for (Map.Entry<String, JsonElement> entry : source.entrySet()) {
            if (!entry.getValue().isJsonObject()) {
                continue;
            }
            JsonObject value = entry.getValue().getAsJsonObject();
            NationRecord nation = new NationRecord(
                    entry.getKey(),
                    color(value.get("color")),
                    nullableString(value, "capital"),
                    stringSet(value.get("towns")),
                    stringSet(value.get("allies")),
                    stringSet(value.get("enemies"))
            );
            result.put(entry.getKey().toLowerCase(Locale.ROOT), nation);
        }
        return result;
    }

    private static Map<String, TownRecord> parseTowns(JsonObject source, Map<String, String> townNations) {
        Map<String, TownRecord> result = new LinkedHashMap<>();
        if (source == null) {
            return result;
        }
        for (Map.Entry<String, JsonElement> entry : source.entrySet()) {
            if (!entry.getValue().isJsonObject()) {
                continue;
            }
            JsonObject value = entry.getValue().getAsJsonObject();
            String key = entry.getKey().toLowerCase(Locale.ROOT);
            List<Integer> territoryEntries = integerList(value.get("territories"));
            List<Integer> annexedEntries = integerList(value.get("annexed"));
            List<Integer> capturedEntries = integerList(value.get("captured"));
            List<Integer> claimedEntries = integerList(value.get("claimed"));
            List<Integer> coreEntries = integerList(value.get("cores"));
            List<Integer> spheredEntries = integerList(value.get("sphered"));
            TownRecord town = new TownRecord(
                    entry.getKey(),
                    townNations.get(key),
                    color(value.get("color")),
                    integerSet(territoryEntries),
                    countedDifference(
                            territoryEntries,
                            annexedEntries,
                            capturedEntries,
                            claimedEntries,
                            coreEntries,
                            spheredEntries
                    ),
                    integerSet(annexedEntries),
                    integerSet(capturedEntries),
                    integerSet(claimedEntries),
                    integerSet(coreEntries),
                    integerSet(spheredEntries),
                    stringSet(value.get("allies")),
                    stringSet(value.get("enemies"))
            );
            result.put(key, town);
        }
        return result;
    }

    private static Map<String, ResidentRecord> parseResidents(JsonObject source) {
        Map<String, ResidentRecord> result = new LinkedHashMap<>();
        if (source == null) {
            return result;
        }
        for (JsonElement element : source.asMap().values()) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject value = element.getAsJsonObject();
            String name = string(value, "name", "");
            if (!name.isBlank()) {
                result.put(name.toLowerCase(Locale.ROOT), new ResidentRecord(
                        name,
                        nullableString(value, "town"),
                        nullableString(value, "nation")
                ));
            }
        }
        return result;
    }

    private static void addOwnerCandidate(
            Map<Integer, List<TownRecord>> memberships,
            int territoryId,
            TownRecord town
    ) {
        List<TownRecord> candidates = memberships.computeIfAbsent(territoryId, ignored -> new ArrayList<>());
        boolean alreadyPresent = candidates.stream()
                .anyMatch(candidate -> candidate.name().equalsIgnoreCase(town.name()));
        if (!alreadyPresent) {
            candidates.add(town);
        }
    }

    private static OwnerResolution chooseOwner(
            int territoryId,
            List<TownRecord> memberships,
            TownRecord previousOwner
    ) {
        List<TownRecord> candidates = memberships.stream()
                .sorted(Comparator
                        .comparingInt((TownRecord town) -> ownerScore(town, territoryId))
                        .reversed()
                        .thenComparing(TownRecord::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
        TownRecord owner = null;
        if (!candidates.isEmpty()) {
            int bestScore = ownerScore(candidates.getFirst(), territoryId);
            List<TownRecord> strongest = candidates.stream()
                    .filter(candidate -> ownerScore(candidate, territoryId) == bestScore)
                    .toList();
            if (strongest.size() == 1) {
                owner = strongest.getFirst();
            } else if (previousOwner != null) {
                owner = strongest.stream()
                        .filter(candidate -> candidate.name().equalsIgnoreCase(previousOwner.name()))
                        .findFirst()
                        .orElse(null);
            }
        }
        if (owner == null && previousOwner != null) {
            owner = candidates.stream()
                    .filter(candidate -> candidate.name().equalsIgnoreCase(previousOwner.name()))
                    .findFirst()
                    .orElse(null);
        }
        return new OwnerResolution(owner, candidates);
    }

    private static int ownerScore(TownRecord town, int territoryId) {
        if (town.cores().contains(territoryId)) {
            return 100;
        }
        if (town.annexed().contains(territoryId)) {
            return 80;
        }
        if (town.claimed().contains(territoryId)) {
            return 60;
        }
        return 10;
    }

    private static NationRecord nationFor(TownRecord town, Map<String, NationRecord> nations) {
        return town == null || town.nation() == null
                ? null
                : nations.get(town.nation().toLowerCase(Locale.ROOT));
    }

    private static RgbColor colorFor(TownRecord town, NationRecord nation) {
        if (nation != null) {
            return nation.color();
        }
        return town == null ? RgbColor.NEUTRAL : town.color();
    }

    private static OwnershipState ownershipState(int id, TownRecord owner, TownRecord occupier) {
        if (occupier != null && !sameTown(owner, occupier)) {
            return OwnershipState.CAPTURED;
        }
        if (owner == null) {
            return OwnershipState.UNOWNED;
        }
        if (owner.annexed().contains(id)) {
            return OwnershipState.ANNEXED;
        }
        if (owner.claimed().contains(id)) {
            return OwnershipState.CLAIMED;
        }
        return OwnershipState.OWNED;
    }

    private static boolean sameTown(TownRecord first, TownRecord second) {
        return first != null
                && second != null
                && first.name().equalsIgnoreCase(second.name());
    }

    private static String displayIdentifier(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        StringBuilder result = new StringBuilder(value.length());
        boolean capitalize = true;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character == '_' || character == '-') {
                result.append(' ');
                capitalize = true;
            } else if (capitalize) {
                result.append(Character.toUpperCase(character));
                capitalize = false;
            } else {
                result.append(character);
            }
        }
        return result.toString();
    }

    private static String emptyPortsJson() {
        return "{\"meta\":{\"type\":\"ports\"},\"ports\":{}}";
    }

    private static JsonObject object(JsonObject object, String name) {
        JsonElement element = object.get(name);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
    }

    private static String string(JsonObject object, String name, String fallback) {
        JsonElement value = object.get(name);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : fallback;
    }

    private static String nullableString(JsonObject object, String name) {
        JsonElement value = object.get(name);
        return value == null || value.isJsonNull() ? null : value.getAsString();
    }

    private static int integer(JsonObject object, String name, int fallback) {
        JsonElement value = object.get(name);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()
                ? value.getAsInt()
                : fallback;
    }

    private static boolean bool(JsonObject object, String name, boolean fallback) {
        JsonElement value = object.get(name);
        return value != null && value.isJsonPrimitive() ? value.getAsBoolean() : fallback;
    }

    private static RgbColor color(JsonElement element) {
        int[] values = intArray(element);
        return values.length >= 3 ? new RgbColor(values[0], values[1], values[2]) : RgbColor.NEUTRAL;
    }

    private static int[] intArray(JsonElement element) {
        if (element == null || !element.isJsonArray()) {
            return new int[0];
        }
        int[] result = new int[element.getAsJsonArray().size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = element.getAsJsonArray().get(i).getAsInt();
        }
        return result;
    }

    private static int[] safeIntArray(JsonElement element) {
        try {
            return intArray(element);
        } catch (RuntimeException ignored) {
            return new int[0];
        }
    }

    private static List<Integer> integerList(JsonElement element) {
        int[] values = intArray(element);
        List<Integer> result = new ArrayList<>(values.length);
        for (int value : values) {
            result.add(value);
        }
        return result;
    }

    private static List<Integer> safeIntegerList(JsonElement element) {
        try {
            return integerList(element);
        } catch (RuntimeException ignored) {
            return List.of();
        }
    }

    private static Set<Integer> integerSet(JsonElement element) {
        return integerSet(integerList(element));
    }

    private static Set<Integer> integerSet(List<Integer> values) {
        return new HashSet<>(values);
    }

    @SafeVarargs
    private static Set<Integer> countedDifference(
            List<Integer> territories,
            List<Integer>... specializedStates
    ) {
        Map<Integer, Integer> counts = new HashMap<>();
        for (int territory : territories) {
            counts.merge(territory, 1, Integer::sum);
        }
        for (List<Integer> state : specializedStates) {
            for (int territory : state) {
                int remaining = counts.getOrDefault(territory, 0);
                if (remaining <= 1) {
                    counts.remove(territory);
                } else {
                    counts.put(territory, remaining - 1);
                }
            }
        }
        return Set.copyOf(counts.keySet());
    }

    private static List<String> stringList(JsonElement element) {
        if (element == null || !element.isJsonArray()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (JsonElement value : element.getAsJsonArray()) {
            if (!value.isJsonNull()) {
                result.add(value.getAsString());
            }
        }
        return result;
    }

    private static List<String> safeStringList(JsonElement element) {
        try {
            return stringList(element);
        } catch (RuntimeException ignored) {
            return List.of();
        }
    }

    private static Set<String> stringSet(JsonElement element) {
        return new HashSet<>(stringList(element));
    }

    private static Map<String, Double> doubleMap(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return Map.of();
        }
        Map<String, Double> result = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
            if (entry.getValue().isJsonPrimitive() && entry.getValue().getAsJsonPrimitive().isNumber()) {
                result.put(entry.getKey(), entry.getValue().getAsDouble());
            }
        }
        return result;
    }

    private record OwnerResolution(TownRecord owner, List<TownRecord> candidates) {
        private OwnerResolution {
            candidates = List.copyOf(candidates);
        }
    }
}
