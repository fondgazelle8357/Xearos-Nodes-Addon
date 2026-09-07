package me.fond.nodesoverlay.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class ServerConfigManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServerConfigManager.class);
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();
    private static final List<String> KNOWN_CACHE_FILES = List.of(
            "towns.json",
            "world.json",
            "ports.json",
            "http-metadata.json",
            "towns.json.tmp",
            "world.json.tmp",
            "ports.json.tmp",
            "http-metadata.json.tmp"
    );

    private final Path root;
    private Path serverDirectory;
    private ServerSettings settings = ServerSettings.defaultsFor("");

    public ServerConfigManager() {
        this(FabricLoader.getInstance().getConfigDir().resolve("nodesoverlay").resolve("servers"));
    }

    ServerConfigManager(Path root) {
        this.root = Objects.requireNonNull(root).toAbsolutePath().normalize();
    }

    public synchronized ServerSettings activate(String serverAddress) {
        serverDirectory = root.resolve(folderName(serverAddress));
        Path path = serverDirectory.resolve("settings.json");
        try {
            Files.createDirectories(serverDirectory);
            if (Files.isRegularFile(path)) {
                settings = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), ServerSettings.class);
                if (settings == null) {
                    settings = ServerSettings.defaultsFor(serverAddress);
                }
            } else {
                settings = ServerSettings.defaultsFor(serverAddress);
            }
            settings.normalize();
        } catch (RuntimeException | IOException exception) {
            LOGGER.warn("Could not load Nodes Overlay settings from {}; using defaults", path, exception);
            settings = ServerSettings.defaultsFor(serverAddress);
            settings.normalize();
        }
        try {
            save();
        } catch (RuntimeException | IOException exception) {
            LOGGER.warn(
                    "Loaded Nodes Overlay settings from {}, but could not save the normalized profile",
                    path,
                    exception
            );
        }
        return settings;
    }

    public synchronized void save() throws IOException {
        if (serverDirectory == null) {
            return;
        }
        settings.normalize();
        Files.createDirectories(serverDirectory);
        Path target = serverDirectory.resolve("settings.json");
        Path temporary = serverDirectory.resolve("settings.json.tmp");
        Files.writeString(temporary, GSON.toJson(settings), StandardCharsets.UTF_8);
        moveReplace(temporary, target);
    }

    public synchronized ServerSettings settings() {
        return settings;
    }

    public synchronized Path serverDirectory() {
        return serverDirectory;
    }

    public synchronized Path cacheDirectory() {
        if (serverDirectory == null) {
            throw new IllegalStateException("No server has been activated");
        }
        return serverDirectory.resolve("cache");
    }

    public synchronized void clearCacheFiles() throws IOException {
        if (serverDirectory == null) {
            throw new IOException("No server profile has been activated");
        }
        clearKnownCacheFiles(serverDirectory.resolve("cache"));
    }

    public static void clearKnownCacheFiles(Path cacheDirectory) throws IOException {
        Path normalized = Objects.requireNonNull(cacheDirectory).toAbsolutePath().normalize();
        if (normalized.getNameCount() < 3 || !"cache".equals(normalized.getFileName().toString())) {
            throw new IOException("Refusing to clear unexpected cache path " + normalized);
        }
        for (String fileName : KNOWN_CACHE_FILES) {
            Path target = normalized.resolve(fileName).normalize();
            if (!normalized.equals(target.getParent())) {
                throw new IOException("Refusing to clear cache path outside " + normalized);
            }
            Files.deleteIfExists(target);
        }
    }

    public static void moveReplace(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException atomicFailure) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String folderName(String address) {
        String normalized = address == null ? "unknown" : address.trim().toLowerCase(Locale.ROOT);
        String slug = normalized.replaceAll("[^a-z0-9._-]+", "_");
        if (slug.isBlank()) {
            slug = "unknown";
        }
        if (slug.length() > 48) {
            slug = slug.substring(0, 48);
        }
        return slug + "-" + shortHash(normalized);
    }

    private static String shortHash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 6);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }
}
