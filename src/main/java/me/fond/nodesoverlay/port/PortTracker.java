package me.fond.nodesoverlay.port;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.fond.nodesoverlay.config.ServerConfigManager;
import me.fond.nodesoverlay.model.PortRecord;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Merges downloaded port locations with newer in-game observations. Live
 * observations are persisted separately from the HTTP cache so clearing or
 * refreshing map data never discards coordinates learned from /port info.
 */
public final class PortTracker {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Map<String, PortRecord> base = new LinkedHashMap<>();
    private final Map<String, PortRecord> overrides = new LinkedHashMap<>();
    private volatile Map<String, PortRecord> merged = Map.of();
    private Path storageFile;
    private Logger logger;

    public synchronized void activate(Path serverDirectory, Logger logger) {
        clear();
        this.logger = logger;
        this.storageFile = serverDirectory.resolve("ports-live.json")
                .toAbsolutePath()
                .normalize();
        load();
    }

    public synchronized void setBase(Map<String, PortRecord> ports) {
        base.clear();
        if (ports != null) {
            base.putAll(ports);
        }
        rebuild();
    }

    public synchronized PortRecord apply(PortObservation observation) {
        String id = PortRecord.normalizeId(observation.name());
        PortRecord previous = overrides.get(id);
        if (previous == null) {
            previous = base.get(id);
        }
        Set<String> groups = observation.groups() == null || observation.groups().isEmpty()
                ? previous == null ? Set.of() : previous.groups()
                : observation.groups();
        PortRecord updated = new PortRecord(
                id,
                observation.name(),
                groups,
                observation.x(),
                observation.z(),
                observation.owner(),
                observation.allyAccess(),
                observation.neutralAccess(),
                observation.enemyAccess(),
                observation.allyCost(),
                true,
                observation.observedAt()
        );
        overrides.put(id, updated);
        rebuild();
        save();
        return updated;
    }

    public Map<String, PortRecord> ports() {
        return merged;
    }

    public synchronized void clear() {
        base.clear();
        overrides.clear();
        merged = Map.of();
        storageFile = null;
        logger = null;
    }

    private void rebuild() {
        Map<String, PortRecord> result = new LinkedHashMap<>(base);
        result.putAll(overrides);
        merged = Map.copyOf(result);
    }

    private void load() {
        if (storageFile == null || !Files.isRegularFile(storageFile)) {
            return;
        }
        try {
            JsonElement parsed = JsonParser.parseString(
                    Files.readString(storageFile, StandardCharsets.UTF_8)
            );
            if (!parsed.isJsonObject()) {
                return;
            }
            for (Map.Entry<String, JsonElement> entry : parsed.getAsJsonObject().entrySet()) {
                if (!entry.getValue().isJsonObject()) {
                    continue;
                }
                JsonObject value = entry.getValue().getAsJsonObject();
                String id = PortRecord.normalizeId(entry.getKey());
                String name = string(value, "name", entry.getKey());
                Set<String> groups = new LinkedHashSet<>();
                JsonElement groupsValue = value.get("groups");
                if (groupsValue != null && groupsValue.isJsonArray()) {
                    groupsValue.getAsJsonArray().forEach(group -> groups.add(group.getAsString()));
                }
                overrides.put(id, new PortRecord(
                        id,
                        name,
                        groups,
                        integer(value, "x", 0),
                        integer(value, "z", 0),
                        nullableString(value, "owner"),
                        nullableBoolean(value, "allyAccess"),
                        nullableBoolean(value, "neutralAccess"),
                        nullableBoolean(value, "enemyAccess"),
                        nullableString(value, "allyCost"),
                        true,
                        Instant.ofEpochMilli(longValue(value, "updatedAt", 0L))
                ));
            }
            rebuild();
        } catch (Exception exception) {
            warn("Could not load saved /port info observations", exception);
        }
    }

    private void save() {
        if (storageFile == null) {
            return;
        }
        JsonObject root = new JsonObject();
        for (PortRecord port : overrides.values()) {
            JsonObject value = new JsonObject();
            value.addProperty("name", port.name());
            value.add("groups", GSON.toJsonTree(port.groups()));
            value.addProperty("x", port.x());
            value.addProperty("z", port.z());
            addNullable(value, "owner", port.owner());
            addNullable(value, "allyAccess", port.allyAccess());
            addNullable(value, "neutralAccess", port.neutralAccess());
            addNullable(value, "enemyAccess", port.enemyAccess());
            addNullable(value, "allyCost", port.allyCost());
            value.addProperty("updatedAt", port.updatedAt().toEpochMilli());
            root.add(port.id(), value);
        }
        try {
            Files.createDirectories(storageFile.getParent());
            Path temporary = storageFile.resolveSibling(storageFile.getFileName() + ".tmp");
            Files.writeString(temporary, GSON.toJson(root), StandardCharsets.UTF_8);
            ServerConfigManager.moveReplace(temporary, storageFile);
        } catch (IOException exception) {
            warn("Could not save /port info observations", exception);
        }
    }

    private void warn(String message, Throwable throwable) {
        if (logger != null) {
            logger.warn(message, throwable);
        }
    }

    private static int integer(JsonObject value, String name, int fallback) {
        JsonElement element = value.get(name);
        return element == null || !element.isJsonPrimitive()
                ? fallback
                : element.getAsInt();
    }

    private static long longValue(JsonObject value, String name, long fallback) {
        JsonElement element = value.get(name);
        return element == null || !element.isJsonPrimitive()
                ? fallback
                : element.getAsLong();
    }

    private static String string(JsonObject value, String name, String fallback) {
        String result = nullableString(value, name);
        return result == null ? fallback : result;
    }

    private static String nullableString(JsonObject value, String name) {
        JsonElement element = value.get(name);
        return element == null || element.isJsonNull() ? null : element.getAsString();
    }

    private static Boolean nullableBoolean(JsonObject value, String name) {
        JsonElement element = value.get(name);
        return element == null || element.isJsonNull() ? null : element.getAsBoolean();
    }

    private static void addNullable(JsonObject object, String name, String value) {
        if (value == null) {
            object.add(name, null);
        } else {
            object.addProperty(name, value);
        }
    }

    private static void addNullable(JsonObject object, String name, Boolean value) {
        if (value == null) {
            object.add(name, null);
        } else {
            object.addProperty(name, value);
        }
    }
}
