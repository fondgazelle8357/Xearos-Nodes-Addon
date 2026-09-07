package me.fond.nodesoverlay;

import me.fond.nodesoverlay.access.AccessBlacklist;
import me.fond.nodesoverlay.config.ServerConfigManager;
import me.fond.nodesoverlay.config.ServerSettings;
import me.fond.nodesoverlay.data.NodesOverlaySnapshot;
import me.fond.nodesoverlay.integration.xaero.TerritoryWaypointManager;
import me.fond.nodesoverlay.model.PortRecord;
import me.fond.nodesoverlay.port.PortObservation;
import me.fond.nodesoverlay.port.PortTracker;
import me.fond.nodesoverlay.sync.DataSyncService;
import me.fond.nodesoverlay.sync.SyncStatus;
import me.fond.nodesoverlay.war.TerritoryInfoObservation;
import me.fond.nodesoverlay.war.WarEvent;
import me.fond.nodesoverlay.war.WarTracker;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import org.slf4j.Logger;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-safe bridge shared by Fabric callbacks and Xaero render/highlight
 * hooks. Snapshots are immutable and published atomically.
 */
public final class NodesOverlayRuntime {

    private static final AtomicReference<NodesOverlaySnapshot> SNAPSHOT =
            new AtomicReference<>(NodesOverlaySnapshot.empty());
    private static final AtomicLong REVISION = new AtomicLong();
    private static final HighlightVersions HIGHLIGHTS = new HighlightVersions();
    private static final ServerConfigManager CONFIG = new ServerConfigManager();
    private static final WarTracker WAR_TRACKER = new WarTracker();
    private static final PortTracker PORT_TRACKER = new PortTracker();
    private static final RuntimeSessionGuard SESSION_GUARD = new RuntimeSessionGuard();

    private static volatile Logger logger;
    private static volatile String serverAddress;
    private static volatile boolean active;
    private static volatile DataSyncService sync;
    private static volatile AccessBlacklist.Decision accessDecision =
            AccessBlacklist.Decision.pending();

    private NodesOverlayRuntime() {
    }

    public static synchronized void initialize(Logger value) {
        logger = value;
    }

    public static synchronized void activate(String address) {
        deactivate();
        serverAddress = address == null || address.isBlank() ? "singleplayer" : address.trim();
        accessDecision = AccessBlacklist.Decision.pending();
        ServerSettings settings = CONFIG.activate(serverAddress);
        PORT_TRACKER.activate(CONFIG.serverDirectory(), logger);
        active = settings.enabled;
        if (!active) {
            return;
        }
        long sessionGeneration = SESSION_GUARD.current();
        sync = new DataSyncService(
                logger,
                () -> synchronizedSessionSettings(settings),
                CONFIG.cacheDirectory(),
                snapshot -> publish(sessionGeneration, snapshot),
                SNAPSHOT::get
        );
        sync.start();
    }

    public static synchronized void deactivate() {
        SESSION_GUARD.advance();
        DataSyncService current = sync;
        sync = null;
        if (current != null) {
            current.close();
        }
        active = false;
        accessDecision = AccessBlacklist.Decision.pending();
        serverAddress = null;
        SNAPSHOT.set(NodesOverlaySnapshot.empty());
        WAR_TRACKER.clear();
        PORT_TRACKER.clear();
        TerritoryWaypointManager.clear();
        changed();
    }

    public static void tick(MinecraftClient client) {
        DataSyncService current = sync;
        if (active && current != null) {
            updateAccess(client);
            current.tick();
            if (WAR_TRACKER.expire(CONFIG.settings().warEventExpirationSeconds)) {
                markersChanged();
            }
            if (accessDecision.allowed()) {
                WAR_TRACKER.tick(client);
            }
        }
    }

    public static void publish(NodesOverlaySnapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        WAR_TRACKER.reconcile(snapshot);
        snapshot = WAR_TRACKER.applyTerritoryOwnership(snapshot);
        PORT_TRACKER.setBase(snapshot.ports());
        SNAPSHOT.set(snapshot);
        changed();
    }

    private static void publish(long sessionGeneration, NodesOverlaySnapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        SESSION_GUARD.runIfCurrent(
                sessionGeneration,
                () -> publish(snapshot)
        );
    }

    public static void changed() {
        REVISION.incrementAndGet();
        HIGHLIGHTS.invalidateAll();
    }

    public static void markersChanged() {
        REVISION.incrementAndGet();
    }

    public static HighlightVersions.View highlightVersions() {
        return HIGHLIGHTS.current();
    }

    public static void flushHighlights() {
        HighlightVersions.Update update = HIGHLIGHTS.flush();
        if (update != null) {
            XaeroCacheInvalidation.invalidate(update);
        }
    }

    public static void warChanged(WarEvent event) {
        markersChanged();
        HIGHLIGHTS.invalidateWarEvent(event, snapshot());
    }

    public static NodesOverlaySnapshot snapshot() {
        return SNAPSHOT.get();
    }

    public static long revision() {
        return REVISION.get();
    }

    public static ServerSettings settings() {
        return CONFIG.settings();
    }

    private static ServerSettings synchronizedSessionSettings(ServerSettings sessionSettings) {
        synchronized (CONFIG) {
            return sessionSettings;
        }
    }

    public static WarTracker warTracker() {
        return WAR_TRACKER;
    }

    public static Map<String, PortRecord> ports() {
        return PORT_TRACKER.ports();
    }

    public static PortRecord observePort(PortObservation observation) {
        PortRecord port = PORT_TRACKER.apply(observation);
        markersChanged();
        return port;
    }

    public static boolean observeTerritory(TerritoryInfoObservation observation) {
        NodesOverlaySnapshot current = SNAPSHOT.get();
        boolean changedState = WAR_TRACKER.observeTerritory(observation, current);
        if (changedState) {
            SNAPSHOT.set(WAR_TRACKER.applyTerritoryOwnership(current));
            changed();
        }
        return changedState;
    }

    public static boolean isActive() {
        return active && CONFIG.settings().enabled && accessDecision.allowed();
    }

    public static boolean sessionEnabled() {
        return active && CONFIG.settings().enabled;
    }

    public static boolean accessAllowed() {
        return accessDecision.allowed();
    }

    public static boolean accessDenied() {
        return accessDecision.denied();
    }

    public static boolean configurationAccessAllowed() {
        return AccessBlacklist.canConfigure(
                CONFIG.settings().enabled,
                accessDecision
        );
    }

    public static AccessBlacklist.Decision accessDecision() {
        return accessDecision;
    }

    public static String accessMessage() {
        return accessDecision.message();
    }

    public static String serverAddress() {
        return serverAddress;
    }

    public static SyncStatus syncStatus() {
        DataSyncService current = sync;
        return current == null ? SyncStatus.idle() : current.status();
    }

    public static void refreshNow() {
        DataSyncService current = sync;
        if (current != null) {
            current.requestRefresh(true);
        }
    }

    public static void refreshSoon() {
        DataSyncService current = sync;
        if (current != null) {
            current.requestRefreshSoon(Duration.ofSeconds(2));
        }
    }

    public static void clearCache() {
        DataSyncService current = sync;
        if (current != null) {
            current.clearCacheAndRefresh();
            return;
        }
        try {
            CONFIG.clearCacheFiles();
        } catch (IOException exception) {
            if (logger != null) {
                logger.warn("Could not clear the cache", exception);
            }
        }
    }

    public static void saveSettings() {
        try {
            CONFIG.save();
        } catch (IOException exception) {
            if (logger != null) {
                logger.warn("Could not save Nodes Overlay settings", exception);
            }
        }
        boolean shouldBeActive = CONFIG.settings().enabled;
        if (shouldBeActive != active && serverAddress != null) {
            activate(serverAddress);
        } else {
            changed();
        }
    }

    private static void updateAccess(MinecraftClient client) {
        UUID playerUuid = client == null || client.getSession() == null
                ? null
                : client.getSession().getUuidOrNull();
        String playerName = client == null || client.player == null
                ? ""
                : client.player.getName().getString();
        AccessBlacklist.Decision updated = AccessBlacklist.evaluate(
                playerUuid,
                playerName,
                CONFIG.settings()
        );
        AccessBlacklist.Decision previous = accessDecision;
        if (updated.equals(previous)) {
            return;
        }
        accessDecision = updated;
        if (updated.denied()) {
            WAR_TRACKER.clear();
            TerritoryWaypointManager.clear();
            if (client != null && client.player != null) {
                client.player.sendMessage(
                        Text.literal("[Nodes Overlay] " + updated.message()),
                        false
                );
            }
            if (logger != null) {
                logger.warn(
                        "Nodes Overlay access denied for {} ({}) by UUID blacklist entry {}",
                        updated.playerName(),
                        updated.playerUuid(),
                        updated.matchedValue()
                );
            }
        }
        changed();
    }
}
