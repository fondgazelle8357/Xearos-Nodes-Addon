package me.fond.nodesoverlay.sync;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import me.fond.nodesoverlay.config.ServerConfigManager;
import me.fond.nodesoverlay.config.ServerSettings;
import me.fond.nodesoverlay.data.NodesOverlayDataParser;
import me.fond.nodesoverlay.data.NodesOverlaySnapshot;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Owns all cache I/O, HTTP and JSON parsing. Nothing in this class runs on the
 * render thread; {@link #tick()} only decides whether work should be queued.
 */
public final class DataSyncService implements AutoCloseable {

    private static final String USER_AGENT =
            "Mozilla/5.0 (compatible; NodesOverlay/1.1; +https://map.crusalis.net/)";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Logger logger;
    private final Supplier<ServerSettings> settingsSupplier;
    private final Path cacheDirectory;
    private final Consumer<NodesOverlaySnapshot> publisher;
    private final Supplier<NodesOverlaySnapshot> previousSnapshot;
    private final NodesOverlayDataParser parser = new NodesOverlayDataParser();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "Nodes Overlay data worker");
        thread.setDaemon(true);
        return thread;
    });
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final AtomicBoolean requestInFlight = new AtomicBoolean();
    private final Object scheduleMonitor = new Object();

    private volatile boolean closed;
    private volatile long nextCheckMillis = Long.MAX_VALUE;
    private volatile long nextForcedMillis = Long.MAX_VALUE;
    private volatile SyncStatus status = SyncStatus.idle();
    private long refreshSoonGeneration;
    private final long warRefreshCooldownMillis;
    private volatile long lastRefreshStartedMillis;


    public DataSyncService(
            Logger logger,
            Supplier<ServerSettings> settingsSupplier,
            Path cacheDirectory,
            Consumer<NodesOverlaySnapshot> publisher,
            Supplier<NodesOverlaySnapshot> previousSnapshot
    ) {
        this(logger, settingsSupplier, cacheDirectory, publisher, previousSnapshot, Duration.ofSeconds(15));
    }

    DataSyncService(
            Logger logger, Supplier<ServerSettings> settingsSupplier, Path cacheDirectory,
            Consumer<NodesOverlaySnapshot> publisher, Supplier<NodesOverlaySnapshot> previousSnapshot,
            Duration warRefreshCooldown
    ) {
        this.warRefreshCooldownMillis = Math.max(0L, warRefreshCooldown.toMillis());
        this.logger = Objects.requireNonNull(logger);
        this.settingsSupplier = Objects.requireNonNull(settingsSupplier);
        this.cacheDirectory = Objects.requireNonNull(cacheDirectory).toAbsolutePath().normalize();
        this.publisher = Objects.requireNonNull(publisher);
        this.previousSnapshot = Objects.requireNonNull(previousSnapshot);
    }

    public void start() {
        if (closed) {
            return;
        }
        status = new SyncStatus(SyncStatus.State.LOADING_CACHE, "Loading cached data", null, Instant.now());
        worker.execute(() -> {
            boolean cacheLoaded = loadCache(false);
            requestRefresh(!cacheLoaded);
        });
    }

    public void tick() {
        if (closed || requestInFlight.get()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now >= nextForcedMillis) {
            requestRefresh(true);
        } else if (now >= nextCheckMillis) {
            requestRefresh(false);
        }
    }

    public void requestRefresh(boolean forceDownload) {
        if (closed || !requestInFlight.compareAndSet(false, true)) {
            return;
        }
        worker.execute(() -> refresh(forceDownload));
    }

    public void requestRefreshSoon(Duration delay) {
        if (closed) {
            return;
        }
        long target = nextWarRefreshAt(System.currentTimeMillis(), delay.toMillis(),
                lastRefreshStartedMillis, warRefreshCooldownMillis);
        synchronized (scheduleMonitor) {
            refreshSoonGeneration++;
            nextCheckMillis = Math.min(nextCheckMillis, target);
        }
    }

    public void clearCacheAndRefresh() {
        if (closed) {
            return;
        }
        worker.execute(() -> {
            try {
                ServerConfigManager.clearKnownCacheFiles(cacheDirectory);
                if (closed) {
                    return;
                }
                publisher.accept(NodesOverlaySnapshot.empty());
                status = new SyncStatus(
                        SyncStatus.State.IDLE,
                        "Local cache cleared",
                        status.lastSuccess(),
                        Instant.now()
                );
            } catch (IOException exception) {
                warn("Could not clear the cache", exception);
            } finally {
                requestRefresh(true);
            }
        });
    }

    public SyncStatus status() {
        return status;
    }

    private void refresh(boolean forceDownload) {
        long scheduleGenerationAtStart;
        synchronized (scheduleMonitor) {
            scheduleGenerationAtStart = refreshSoonGeneration;
        }
        lastRefreshStartedMillis = System.currentTimeMillis();
        Instant attemptedAt = Instant.now();
        status = new SyncStatus(
                SyncStatus.State.CHECKING,
                forceDownload ? "Reconciling map data" : "Checking map data",
                status.lastSuccess(),
                attemptedAt
        );

        try {
            Files.createDirectories(cacheDirectory);
            HttpMetadata metadata = readMetadata();
            EndpointResult towns = download(
                    settingsSupplier.get().townsUrl,
                    cacheDirectory.resolve("towns.json"),
                    metadata.towns,
                    forceDownload
            );
            EndpointResult world = download(
                    settingsSupplier.get().worldUrl,
                    cacheDirectory.resolve("world.json"),
                    metadata.world,
                    forceDownload
            );
            EndpointResult ports = downloadOptional(
                    settingsSupplier.get().portsUrl,
                    cacheDirectory.resolve("ports.json"),
                    metadata.ports,
                    forceDownload
            );
            boolean changed = towns.body != null || world.body != null || ports.body != null;
            if (!changed && !previousSnapshot.get().isEmpty()) {
                metadata.towns = towns.metadata;
                metadata.world = world.metadata;
                metadata.ports = ports.metadata;
                writeAtomic(cacheDirectory.resolve("http-metadata.json"), GSON.toJson(metadata));
                scheduleSuccess(forceDownload, scheduleGenerationAtStart);
                status = new SyncStatus(
                        SyncStatus.State.UP_TO_DATE,
                        "Map data is up to date",
                        Instant.now(),
                        attemptedAt
                );
                return;
            }

            String townsJson = towns.body != null
                    ? towns.body
                    : readRequired(cacheDirectory.resolve("towns.json"), "towns");
            String worldJson = world.body != null
                    ? world.body
                    : readRequired(cacheDirectory.resolve("world.json"), "world");
            String portsJson = ports.body != null
                    ? ports.body
                    : readOptionalPorts(cacheDirectory.resolve("ports.json"));

            NodesOverlaySnapshot parsed = parser.parse(
                    townsJson,
                    worldJson,
                    portsJson,
                    previousSnapshot.get()
            );
            if (closed) {
                return;
            }

            if (towns.body != null) {
                writeAtomic(cacheDirectory.resolve("towns.json"), towns.body);
            }
            if (world.body != null) {
                writeAtomic(cacheDirectory.resolve("world.json"), world.body);
            }
            if (ports.body != null) {
                writeAtomic(cacheDirectory.resolve("ports.json"), ports.body);
            }
            metadata.towns = towns.metadata;
            metadata.world = world.metadata;
            metadata.ports = ports.metadata;
            writeAtomic(cacheDirectory.resolve("http-metadata.json"), GSON.toJson(metadata));
            publisher.accept(parsed);

            scheduleSuccess(forceDownload, scheduleGenerationAtStart);
            status = new SyncStatus(
                    changed ? SyncStatus.State.UPDATED : SyncStatus.State.UP_TO_DATE,
                    changed
                            ? "Map data updated ("
                            + parsed.nodes().size() + " nodes, "
                            + parsed.territories().size() + " territories)"
                            : "Map data is up to date",
                    Instant.now(),
                    attemptedAt
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            scheduleRetry(scheduleGenerationAtStart);
            warn("Map data refresh was interrupted", exception);
        } catch (Exception exception) {
            scheduleRetry(scheduleGenerationAtStart);
            warn("Using the last valid cache: " + conciseMessage(exception), exception);
        } finally {
            requestInFlight.set(false);
        }
    }

    private EndpointResult download(
            String url,
            Path cachedFile,
            EndpointMetadata metadata,
            boolean forceDownload
    ) throws IOException, InterruptedException {
        URI uri = URI.create(url);
        if (!"https".equalsIgnoreCase(uri.getScheme()) && !"http".equalsIgnoreCase(uri.getScheme())) {
            throw new IOException("Only HTTP(S) data endpoints are supported");
        }
        HttpRequest.Builder request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json,text/plain,*/*")
                .header("Referer", refererFor(uri))
                .GET();
        boolean metadataMatchesEndpoint = url.equals(metadata.url);
        if (!forceDownload
                && metadataMatchesEndpoint
                && Files.isRegularFile(cachedFile)) {
            if (metadata.etag != null && !metadata.etag.isBlank()) {
                request.header("If-None-Match", metadata.etag);
            }
            if (metadata.lastModified != null && !metadata.lastModified.isBlank()) {
                request.header("If-Modified-Since", metadata.lastModified);
            }
        }

        HttpResponse<String> response =
                httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() == 304
                && metadataMatchesEndpoint
                && Files.isRegularFile(cachedFile)) {
            return new EndpointResult(null, metadata);
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException(url + " returned HTTP " + response.statusCode());
        }
        String body = response.body();
        if (body == null || body.isBlank()) {
            throw new IOException(url + " returned an empty response");
        }
        EndpointMetadata updated = new EndpointMetadata();
        updated.url = url;
        updated.etag = response.headers().firstValue("ETag").orElse(null);
        updated.lastModified = response.headers().firstValue("Last-Modified").orElse(null);
        if (Files.isRegularFile(cachedFile)
                && body.equals(Files.readString(cachedFile, StandardCharsets.UTF_8))) {
            return new EndpointResult(null, updated);
        }
        return new EndpointResult(body, updated);
    }

    private EndpointResult downloadOptional(
            String url,
            Path cachedFile,
            EndpointMetadata metadata,
            boolean forceDownload
    ) {
        try {
            return download(url, cachedFile, metadata, forceDownload);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return new EndpointResult(null, metadata);
        } catch (Exception exception) {
            logger.warn(
                    "Could not refresh optional ports data; using saved/base port data: {}",
                    conciseMessage(exception)
            );
            return new EndpointResult(null, metadata);
        }
    }

    private boolean loadCache(boolean warnWhenMissing) {
        Path towns = cacheDirectory.resolve("towns.json");
        Path world = cacheDirectory.resolve("world.json");
        if (!Files.isRegularFile(towns) || !Files.isRegularFile(world)) {
            if (warnWhenMissing) {
                status = new SyncStatus(SyncStatus.State.IDLE, "No usable cache", null, Instant.now());
            }
            return false;
        }
        try {
            String townsJson = Files.readString(towns, StandardCharsets.UTF_8);
            String worldJson = Files.readString(world, StandardCharsets.UTF_8);
            String portsJson = readOptionalPorts(cacheDirectory.resolve("ports.json"));
            NodesOverlaySnapshot parsed = parser.parse(
                    townsJson,
                    worldJson,
                    portsJson,
                    previousSnapshot.get()
            );
            if (closed) {
                return false;
            }
            publisher.accept(parsed);
            status = new SyncStatus(
                    SyncStatus.State.UP_TO_DATE,
                    "Loaded cached data",
                    Instant.now(),
                    Instant.now()
            );
            return true;
        } catch (Exception exception) {
            warn("Cached map data is invalid; keeping the overlay empty until refresh", exception);
            return false;
        }
    }

    private HttpMetadata readMetadata() {
        Path path = cacheDirectory.resolve("http-metadata.json");
        if (!Files.isRegularFile(path)) {
            return new HttpMetadata();
        }
        try {
            HttpMetadata metadata = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), HttpMetadata.class);
            return metadata == null ? new HttpMetadata() : metadata.normalize();
        } catch (Exception ignored) {
            return new HttpMetadata();
        }
    }

    static long nextWarRefreshAt(long now, long delayMillis, long lastStarted, long cooldownMillis) {
        return Math.max(now + Math.max(0L, delayMillis), lastStarted + cooldownMillis);
    }

    private void scheduleRetry(long scheduleGenerationAtStart) {
        ServerSettings settings = settingsSupplier.get();
        long retryAt = System.currentTimeMillis()
                + Duration.ofSeconds(settings.requestFailureRetrySeconds).toMillis();
        synchronized (scheduleMonitor) {
            nextCheckMillis = refreshSoonGeneration == scheduleGenerationAtStart
                    ? retryAt
                    : Math.min(nextCheckMillis, retryAt);
            nextForcedMillis = Math.max(nextForcedMillis, retryAt);
        }
    }

    private void scheduleSuccess(boolean forced, long scheduleGenerationAtStart) {
        ServerSettings settings = settingsSupplier.get();
        long now = System.currentTimeMillis();
        long regularCheck =
                now + Duration.ofMinutes(settings.jsonRefreshIntervalMinutes).toMillis();
        synchronized (scheduleMonitor) {
            nextCheckMillis = refreshSoonGeneration == scheduleGenerationAtStart
                    ? regularCheck
                    : Math.min(Math.max(nextCheckMillis,
                            lastRefreshStartedMillis + warRefreshCooldownMillis), regularCheck);
            nextForcedMillis = forced || nextForcedMillis == Long.MAX_VALUE
                    ? now + Duration.ofMinutes(settings.forcedReconciliationMinutes).toMillis()
                    : nextForcedMillis;
        }
    }

    private void warn(String message, Throwable throwable) {
        logger.warn(message, throwable);
        status = new SyncStatus(
                SyncStatus.State.WARNING,
                message,
                status.lastSuccess(),
                Instant.now()
        );
    }

    private static String readRequired(Path path, String name) throws IOException {
        if (!Files.isRegularFile(path)) {
            throw new IOException("No cached " + name + ".json is available");
        }
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private static String readOptionalPorts(Path path) throws IOException {
        if (!Files.isRegularFile(path)) {
            return "{\"meta\":{\"type\":\"ports\"},\"ports\":{}}";
        }
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private static void writeAtomic(Path target, String contents) throws IOException {
        Files.createDirectories(target.getParent());
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        Files.writeString(temporary, contents, StandardCharsets.UTF_8);
        ServerConfigManager.moveReplace(temporary, target);
    }

    private static String conciseMessage(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.isBlank()
                ? throwable.getClass().getSimpleName()
                : message;
    }

    private static String refererFor(URI endpoint) {
        int port = endpoint.getPort();
        String authority = endpoint.getHost() == null ? endpoint.getAuthority() : endpoint.getHost();
        if (authority == null || authority.isBlank()) {
            return "https://map.crusalis.net/";
        }
        return endpoint.getScheme() + "://" + authority + (port < 0 ? "" : ":" + port) + "/";
    }

    @Override
    public void close() {
        closed = true;
        worker.shutdownNow();
    }

    private record EndpointResult(String body, EndpointMetadata metadata) {
    }

    private static final class EndpointMetadata {
        String url;
        String etag;
        String lastModified;
    }

    private static final class HttpMetadata {
        EndpointMetadata towns = new EndpointMetadata();
        EndpointMetadata world = new EndpointMetadata();
        EndpointMetadata ports = new EndpointMetadata();

        HttpMetadata normalize() {
            if (towns == null) {
                towns = new EndpointMetadata();
            }
            if (world == null) {
                world = new EndpointMetadata();
            }
            if (ports == null) {
                ports = new EndpointMetadata();
            }
            return this;
        }
    }
}
