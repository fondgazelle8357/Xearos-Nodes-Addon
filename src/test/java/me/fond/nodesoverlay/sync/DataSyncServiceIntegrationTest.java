package me.fond.nodesoverlay.sync;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import me.fond.nodesoverlay.config.ServerSettings;
import me.fond.nodesoverlay.data.NodesOverlaySnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.helpers.NOPLogger;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataSyncServiceIntegrationTest {

    private static final Duration WAIT_TIMEOUT = Duration.ofSeconds(10);
    private static final String TOWNS_ETAG = "\"towns-v1\"";
    private static final String WORLD_ETAG = "\"world-v1\"";
    private static final String PORTS_ETAG = "\"ports-v1\"";
    private static final String LAST_MODIFIED = "Mon, 27 Jul 2026 03:40:02 GMT";

    @TempDir
    Path temporaryDirectory;

    @Test
    void sendsBrowserHeadersAndPublishesInitialCache() throws Exception {
        try (TestServer server = new TestServer();
             ServiceFixture fixture = service(server)) {
            fixture.service.start();
            await(() -> fixture.publications.count() == 1
                    && fixture.service.status().state() == SyncStatus.State.UPDATED);

            NodesOverlaySnapshot snapshot = fixture.publications.current();
            assertNotNull(snapshot.territory(1));
            assertEquals("NodeTown", snapshot.territory(1).owner().name());
            assertEquals(TOWNS_JSON, Files.readString(fixture.cache.resolve("towns.json")));
            assertEquals(WORLD_JSON, Files.readString(fixture.cache.resolve("world.json")));
            assertEquals(PORTS_JSON, Files.readString(fixture.cache.resolve("ports.json")));
            assertEquals(-766, snapshot.port("rome").x());
            assertTrue(Files.readString(fixture.cache.resolve("http-metadata.json")).contains("towns-v1"));

            assertBrowserHeaders(server.towns.requests().getFirst(), server.url("/"));
            assertBrowserHeaders(server.world.requests().getFirst(), server.url("/"));
            assertBrowserHeaders(server.ports.requests().getFirst(), server.url("/"));
            assertEquals(1, server.towns.requests().size());
            assertEquals(1, server.world.requests().size());
            assertEquals(1, server.ports.requests().size());
        }
    }

    @Test
    void conditionalEtag304KeepsCachedBodiesUntouched() throws Exception {
        try (TestServer server = new TestServer();
             ServiceFixture fixture = service(server)) {
            fixture.service.start();
            await(() -> fixture.publications.count() == 1
                    && fixture.service.status().state() == SyncStatus.State.UPDATED);

            Path townsCache = fixture.cache.resolve("towns.json");
            Path worldCache = fixture.cache.resolve("world.json");
            String cachedTowns = Files.readString(townsCache);
            String cachedWorld = Files.readString(worldCache);
            FileTime townsModified = Files.getLastModifiedTime(townsCache);
            FileTime worldModified = Files.getLastModifiedTime(worldCache);

            fixture.service.requestRefresh(false);
            await(() -> server.towns.requests().size() == 2
                    && server.world.requests().size() == 2
                    && fixture.service.status().state() == SyncStatus.State.UP_TO_DATE);

            ReceivedRequest townsRequest = server.towns.requests().get(1);
            ReceivedRequest worldRequest = server.world.requests().get(1);
            assertEquals(TOWNS_ETAG, townsRequest.ifNoneMatch());
            assertEquals(WORLD_ETAG, worldRequest.ifNoneMatch());
            assertEquals(LAST_MODIFIED, townsRequest.ifModifiedSince());
            assertEquals(LAST_MODIFIED, worldRequest.ifModifiedSince());
            assertEquals(cachedTowns, Files.readString(townsCache));
            assertEquals(cachedWorld, Files.readString(worldCache));
            assertEquals(townsModified, Files.getLastModifiedTime(townsCache));
            assertEquals(worldModified, Files.getLastModifiedTime(worldCache));
            assertEquals(1, fixture.publications.count());
            assertNotNull(fixture.publications.current().territory(1));
        }
    }

    @Test
    void identicalForcedResponsesKeepSnapshotAndUpdateValidators() throws Exception {
        try (TestServer server = new TestServer(); ServiceFixture fixture = service(server)) {
            fixture.service.start();
            await(() -> fixture.service.status().state() == SyncStatus.State.UPDATED);
            NodesOverlaySnapshot original = fixture.publications.current();
            server.towns.response(TOWNS_JSON, "\"towns-new-validator\"", LAST_MODIFIED);
            fixture.service.requestRefresh(true);
            await(() -> server.ports.requests().size() == 2
                    && fixture.service.status().state() == SyncStatus.State.UP_TO_DATE);
            assertSame(original, fixture.publications.current());
            assertEquals(1, fixture.publications.count());
            assertNull(server.towns.requests().get(1).ifNoneMatch());
            assertTrue(Files.readString(fixture.cache.resolve("http-metadata.json"))
                    .contains("towns-new-validator"));
        }
    }

    @Test
    void townsOnlyRefreshRetainsGeometryButPublishesChangedOwnership() throws Exception {
        try (TestServer server = new TestServer(); ServiceFixture fixture = service(server)) {
            fixture.service.start();
            await(() -> fixture.service.status().state() == SyncStatus.State.UPDATED);
            NodesOverlaySnapshot original = fixture.publications.current();
            server.towns.response(ALT_TOWNS_JSON, "\"towns-v2\"", LAST_MODIFIED);
            fixture.service.requestRefresh(false);
            await(() -> fixture.publications.count() == 2);
            NodesOverlaySnapshot updated = fixture.publications.current();
            assertSame(original.territory(1).territory(), updated.territory(1).territory());
            assertEquals("MirrorTown", updated.territory(1).ownerTownName());
            assertEquals(updated.territory(1), updated.territoryAtChunk(0, 0));
        }
    }

    @Test
    void warRefreshCooldownCapsBurstsAndPreservesInitialDelay() {
        assertEquals(16_000L, DataSyncService.nextWarRefreshAt(2_000L, 2_000L, 1_000L, 15_000L));
        assertEquals(16_000L, DataSyncService.nextWarRefreshAt(3_000L, 2_000L, 1_000L, 15_000L));
        assertEquals(22_000L, DataSyncService.nextWarRefreshAt(20_000L, 2_000L, 1_000L, 15_000L));
    }

    @Test
    void changingAnEndpointDoesNotSendValidatorsFromThePreviousUrl() throws Exception {
        try (TestServer server = new TestServer();
             ServiceFixture fixture = service(server)) {
            fixture.service.start();
            await(() -> fixture.publications.count() == 1
                    && fixture.service.status().state() == SyncStatus.State.UPDATED);

            fixture.settings.townsUrl = server.url("/towns-alt.json");
            fixture.service.requestRefresh(false);
            await(() -> server.alternateTowns.requests().size() == 1
                    && fixture.publications.count() == 2);

            ReceivedRequest request = server.alternateTowns.requests().getFirst();
            assertNull(request.ifNoneMatch());
            assertNull(request.ifModifiedSince());
            assertEquals(
                    "MirrorTown",
                    fixture.publications.current().territory(1).owner().name()
            );
        }
    }

    @Test
    void refreshSoonRequestedDuringARequestIsNotLostOnCompletion() throws Exception {
        try (TestServer server = new TestServer();
             ServiceFixture fixture = service(server)) {
            fixture.service.start();
            await(() -> fixture.publications.count() == 1
                    && fixture.service.status().state() == SyncStatus.State.UPDATED);

            RequestGate gate = server.towns.blockNext();
            fixture.service.requestRefresh(false);
            try {
                assertTrue(gate.awaitStarted(), "The conditional request did not start");
                fixture.service.requestRefreshSoon(Duration.ZERO);
            } finally {
                gate.release();
            }

            await(() -> {
                fixture.service.tick();
                return server.towns.requests().size() >= 3;
            });
        }
    }

    @Test
    void malformedUpdateRetainsPriorSnapshotCacheAndMetadata() throws Exception {
        try (TestServer server = new TestServer();
             ServiceFixture fixture = service(server)) {
            fixture.service.start();
            await(() -> fixture.publications.count() == 1
                    && fixture.service.status().state() == SyncStatus.State.UPDATED);

            NodesOverlaySnapshot previous = fixture.publications.current();
            String cachedTowns = Files.readString(fixture.cache.resolve("towns.json"));
            String cachedWorld = Files.readString(fixture.cache.resolve("world.json"));
            String cachedMetadata = Files.readString(fixture.cache.resolve("http-metadata.json"));

            server.towns.response(
                    "{\"meta\":{\"type\":\"towns\"},\"towns\":",
                    "\"towns-v2\"",
                    "Mon, 27 Jul 2026 03:45:02 GMT"
            );
            fixture.service.requestRefresh(false);
            await(() -> server.towns.requests().size() == 2
                    && fixture.service.status().state() == SyncStatus.State.WARNING);

            assertEquals(1, fixture.publications.count());
            assertSame(previous, fixture.publications.current());
            assertEquals(cachedTowns, Files.readString(fixture.cache.resolve("towns.json")));
            assertEquals(cachedWorld, Files.readString(fixture.cache.resolve("world.json")));
            assertEquals(cachedMetadata, Files.readString(fixture.cache.resolve("http-metadata.json")));
            assertTrue(fixture.service.status().message().contains("last valid cache"));
        }
    }

    @Test
    void clearCachePublishesEmptySnapshotAndRemovesCacheFiles() throws Exception {
        try (TestServer server = new TestServer();
             ServiceFixture fixture = service(server)) {
            fixture.service.start();
            await(() -> fixture.publications.count() == 1
                    && fixture.service.status().state() == SyncStatus.State.UPDATED);

            server.towns.statusCode(503);
            server.world.statusCode(503);
            fixture.service.clearCacheAndRefresh();

            await(() -> fixture.publications.count() == 2
                    && fixture.publications.current().isEmpty());
            await(() -> fixture.service.status().state() == SyncStatus.State.WARNING);

            assertFalse(Files.exists(fixture.cache.resolve("towns.json")));
            assertFalse(Files.exists(fixture.cache.resolve("world.json")));
            assertFalse(Files.exists(fixture.cache.resolve("http-metadata.json")));
            assertEquals(2, fixture.publications.count());
        }
    }

    private ServiceFixture service(TestServer server) {
        Path cache = temporaryDirectory.resolve("server").resolve("cache");
        ServerSettings settings = new ServerSettings();
        settings.townsUrl = server.url("/towns.json");
        settings.worldUrl = server.url("/world.json");
        settings.portsUrl = server.url("/ports.json");
        settings.jsonRefreshIntervalMinutes = 5;
        settings.forcedReconciliationMinutes = 20;
        settings.requestFailureRetrySeconds = 90;

        PublicationProbe publications = new PublicationProbe();
        DataSyncService service = new DataSyncService(
                NOPLogger.NOP_LOGGER,
                () -> settings,
                cache,
                publications::publish,
                publications::current,
                Duration.ofMillis(200)
        );
        return new ServiceFixture(service, cache, publications, settings);
    }

    private static void assertBrowserHeaders(ReceivedRequest request, String expectedReferer) {
        assertTrue(request.userAgent().contains("NodesOverlay/1.1"));
        assertEquals("application/json,text/plain,*/*", request.accept());
        assertEquals(expectedReferer, request.referer());
    }

    private static void await(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + WAIT_TIMEOUT.toNanos();
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(10L);
        }
        assertTrue(condition.getAsBoolean(), "Timed out waiting for asynchronous sync work");
    }

    private record ServiceFixture(
            DataSyncService service,
            Path cache,
            PublicationProbe publications,
            ServerSettings settings
    ) implements AutoCloseable {
        @Override
        public void close() {
            service.close();
        }
    }

    private static final class PublicationProbe {
        private final AtomicReference<NodesOverlaySnapshot> current =
                new AtomicReference<>(NodesOverlaySnapshot.empty());
        private final AtomicInteger count = new AtomicInteger();

        void publish(NodesOverlaySnapshot snapshot) {
            current.set(snapshot);
            count.incrementAndGet();
        }

        NodesOverlaySnapshot current() {
            return current.get();
        }

        int count() {
            return count.get();
        }
    }

    private static final class TestServer implements AutoCloseable {
        private final HttpServer server;
        final Endpoint towns = new Endpoint(TOWNS_JSON, TOWNS_ETAG, LAST_MODIFIED);
        final Endpoint world = new Endpoint(WORLD_JSON, WORLD_ETAG, LAST_MODIFIED);
        final Endpoint ports = new Endpoint(PORTS_JSON, PORTS_ETAG, LAST_MODIFIED);
        final Endpoint alternateTowns = new Endpoint(
                ALT_TOWNS_JSON,
                "\"towns-v2\"",
                "Mon, 27 Jul 2026 03:50:02 GMT"
        );

        TestServer() throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/towns.json", towns);
            server.createContext("/world.json", world);
            server.createContext("/ports.json", ports);
            server.createContext("/towns-alt.json", alternateTowns);
            server.start();
        }

        String url(String path) {
            return "http://127.0.0.1:" + server.getAddress().getPort() + path;
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }

    private static final class Endpoint implements HttpHandler {
        private final List<ReceivedRequest> requests = new CopyOnWriteArrayList<>();
        private final AtomicReference<RequestGate> nextGate = new AtomicReference<>();
        private volatile String body;
        private volatile String etag;
        private volatile String lastModified;
        private volatile int statusCode = 200;

        Endpoint(String body, String etag, String lastModified) {
            response(body, etag, lastModified);
        }

        void response(String body, String etag, String lastModified) {
            this.body = body;
            this.etag = etag;
            this.lastModified = lastModified;
            this.statusCode = 200;
        }

        void statusCode(int statusCode) {
            this.statusCode = statusCode;
        }

        List<ReceivedRequest> requests() {
            return requests;
        }

        RequestGate blockNext() {
            RequestGate gate = new RequestGate();
            if (!nextGate.compareAndSet(null, gate)) {
                throw new IllegalStateException("An endpoint request is already gated");
            }
            return gate;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String ifNoneMatch = exchange.getRequestHeaders().getFirst("If-None-Match");
            String ifModifiedSince = exchange.getRequestHeaders().getFirst("If-Modified-Since");
            requests.add(new ReceivedRequest(
                    exchange.getRequestHeaders().getFirst("User-Agent"),
                    exchange.getRequestHeaders().getFirst("Accept"),
                    exchange.getRequestHeaders().getFirst("Referer"),
                    ifNoneMatch,
                    ifModifiedSince
            ));
            RequestGate gate = nextGate.getAndSet(null);
            if (gate != null) {
                gate.started.countDown();
                try {
                    if (!gate.release.await(
                            WAIT_TIMEOUT.toMillis(),
                            TimeUnit.MILLISECONDS
                    )) {
                        throw new IOException("Timed out waiting to release test endpoint");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted while gating test endpoint", exception);
                }
            }

            if (etag != null) {
                exchange.getResponseHeaders().set("ETag", etag);
            }
            if (lastModified != null) {
                exchange.getResponseHeaders().set("Last-Modified", lastModified);
            }
            if (statusCode != 200) {
                byte[] error = "unavailable".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(statusCode, error.length);
                exchange.getResponseBody().write(error);
                exchange.close();
                return;
            }

            boolean notModified = ifNoneMatch != null
                    ? ifNoneMatch.equals(etag)
                    : ifModifiedSince != null && ifModifiedSince.equals(lastModified);
            if (notModified) {
                exchange.sendResponseHeaders(304, -1);
                exchange.close();
                return;
            }

            byte[] response = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        }
    }

    private static final class RequestGate {
        private final CountDownLatch started = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);

        boolean awaitStarted() throws InterruptedException {
            return started.await(WAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        }

        void release() {
            release.countDown();
        }
    }

    private record ReceivedRequest(
            String userAgent,
            String accept,
            String referer,
            String ifNoneMatch,
            String ifModifiedSince
    ) {
    }

    private static final String TOWNS_JSON = """
            {
              "meta": {"type": "towns"},
              "residents": {},
              "towns": {
                "NodeTown": {
                  "color": [50, 100, 150],
                  "territories": [1],
                  "annexed": [],
                  "captured": [],
                  "claimed": [],
                  "cores": [],
                  "sphered": [],
                  "allies": [],
                  "enemies": []
                }
              },
              "nations": {},
              "plots": {}
            }
            """;

    private static final String ALT_TOWNS_JSON =
            TOWNS_JSON.replace("NodeTown", "MirrorTown");

    private static final String WORLD_JSON = """
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
                  "neighbors": [],
                  "isEdge": false,
                  "nodes": [],
                  "color": 0
                }
              }
            }
            """;

    private static final String PORTS_JSON = """
            {
              "meta": {"type": "ports"},
              "ports": {
                "rome": {"groups": ["1"], "x": -766, "z": 532}
              }
            }
            """;
}
