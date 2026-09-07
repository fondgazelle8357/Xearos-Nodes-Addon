package me.fond.nodesoverlay.config;

import me.fond.nodesoverlay.model.CoreMarkerMode;
import me.fond.nodesoverlay.model.RgbColor;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-server settings. Fields intentionally remain simple Gson-friendly values
 * so older configuration files can be upgraded without custom adapters.
 */
public final class ServerSettings {

    private static final int CURRENT_CONFIGURATION_VERSION = 6;
    public int configurationVersion;
    public static final String DEFAULT_TOWNS_URL = "https://map.crusalis.net/nodes/towns.json";
    public static final String DEFAULT_WORLD_URL = "https://map.crusalis.net/nodes/world.json";
    public static final String DEFAULT_PORTS_URL = "https://map.crusalis.net/nodes/ports.json";
    public static final double DEFAULT_WAR_ICON_SCALE = 1.0D;
    public static final double MINIMUM_WAR_ICON_SCALE = 0.5D;
    public static final double MAXIMUM_WAR_ICON_SCALE = 2.0D;

    public boolean enabled = true;
    public boolean worldMapOverlay = true;
    public boolean minimapOverlay = true;
    public int territoryFillOpacity = 145;
    public int borderOpacity = 235;
    public int internalBorderOpacity = 155;
    public int borderThickness = 1;
    public int occupationStripeOpacity = 185;
    public int occupationStripePeriod = 7;
    public int occupationStripeWidth = 2;

    public boolean showTerritoryIds = true;
    public boolean showTownNames = true;
    public boolean showNationNames = true;
    public boolean showOverviewTownNames;
    public boolean showNodeIcons = true;
    public boolean showNodeLabels = true;
    public boolean showPorts = true;
    public boolean showPortNames = true;
    public boolean showPortOwnerInLabels = true;
    public double portExactZoomThreshold = 1.25D;
    public CoreMarkerMode coreMarkerMode = CoreMarkerMode.ZOOMED_IN;
    public double coreMarkerZoomThreshold = 0.55D;

    public boolean showWarIcons = true;
    public double warIconScale = DEFAULT_WAR_ICON_SCALE;
    public boolean relationshipAwareWarMarkers = true;
    public boolean showWorldWaypoints = true;
    public boolean waypointsThroughTerrain = true;
    public int waypointMaximumDistance = 8_000;
    public int warEventExpirationSeconds = 300;

    public volatile int jsonRefreshIntervalMinutes = 5;
    public volatile int forcedReconciliationMinutes = 20;
    public volatile int requestFailureRetrySeconds = 90;

    public RgbColor neutralTerritoryColor = new RgbColor(112, 112, 112);
    public RgbColor territoryBorderColor = new RgbColor(24, 16, 16);
    public RgbColor friendlyMarkerColor = new RgbColor(80, 190, 110);
    public RgbColor alliedMarkerColor = new RgbColor(75, 160, 235);
    public RgbColor hostileMarkerColor = new RgbColor(225, 70, 62);
    public RgbColor unknownMarkerColor = new RgbColor(240, 180, 55);

    public volatile String townsUrl = DEFAULT_TOWNS_URL;
    public volatile String worldUrl = DEFAULT_WORLD_URL;
    public volatile String portsUrl = DEFAULT_PORTS_URL;
    public Set<Integer> hiddenTerritories = new HashSet<>();
    public Set<Integer> trackedTerritories = new HashSet<>();
    public Set<String> blacklistedUuids = new HashSet<>();

    public static ServerSettings defaultsFor(String serverAddress) {
        ServerSettings settings = new ServerSettings();
        settings.enabled = isNodesOverlayAddress(serverAddress);
        return settings;
    }

    public static boolean isNodesOverlayAddress(String address) {
        if (address == null) {
            return false;
        }
        String normalized = address.trim().toLowerCase();
        int colon = normalized.lastIndexOf(':');
        if (colon > 0 && normalized.indexOf(':') == colon) {
            normalized = normalized.substring(0, colon);
        }
        return normalized.equals("crusalis.net")
                || normalized.endsWith(".crusalis.net");
    }

    public void normalize() {
        blacklistedUuids = concurrentUuidCopy(blacklistedUuids);
        if (configurationVersion < 5) {
            // Preserve the existing "Port | Owner" label for upgraded
            // profiles until the user explicitly disables the suffix.
            showPortOwnerInLabels = true;
        }
        if (configurationVersion < 2
                && borderOpacity == 0
                && internalBorderOpacity == 0) {
            // Early builds exposed alpha sliders before the web-map-like dark
            // outline color existed. Preserve custom nonzero values, but
            // migrate the old all-zero outline profile to the new readable
            // style instead of leaving every territory visually merged.
            borderOpacity = 235;
            internalBorderOpacity = 155;
        }
        configurationVersion = CURRENT_CONFIGURATION_VERSION;
        territoryFillOpacity = clampByte(territoryFillOpacity);
        borderOpacity = clampByte(borderOpacity);
        internalBorderOpacity = clampByte(internalBorderOpacity);
        occupationStripeOpacity = clampByte(occupationStripeOpacity);
        borderThickness = clamp(borderThickness, 1, 4);
        occupationStripePeriod = clamp(occupationStripePeriod, 3, 24);
        occupationStripeWidth = clamp(occupationStripeWidth, 1, occupationStripePeriod - 1);
        coreMarkerZoomThreshold = Math.max(0.05D, Math.min(8.0D, coreMarkerZoomThreshold));
        portExactZoomThreshold = Math.max(0.05D, Math.min(8.0D, portExactZoomThreshold));
        waypointMaximumDistance = clamp(waypointMaximumDistance, 128, 100_000);
        warEventExpirationSeconds = clamp(warEventExpirationSeconds, 30, 3_600);
        warIconScale = Double.isFinite(warIconScale)
                ? Math.max(
                        MINIMUM_WAR_ICON_SCALE,
                        Math.min(MAXIMUM_WAR_ICON_SCALE, warIconScale)
                )
                : DEFAULT_WAR_ICON_SCALE;
        jsonRefreshIntervalMinutes = clamp(jsonRefreshIntervalMinutes, 1, 120);
        forcedReconciliationMinutes = clamp(
                forcedReconciliationMinutes,
                Math.max(2, jsonRefreshIntervalMinutes),
                360
        );
        requestFailureRetrySeconds = clamp(requestFailureRetrySeconds, 30, 900);
        coreMarkerMode = coreMarkerMode == null ? CoreMarkerMode.ZOOMED_IN : coreMarkerMode;
        neutralTerritoryColor = colorOr(neutralTerritoryColor, new RgbColor(112, 112, 112));
        territoryBorderColor = colorOr(territoryBorderColor, new RgbColor(24, 16, 16));
        friendlyMarkerColor = colorOr(friendlyMarkerColor, new RgbColor(80, 190, 110));
        alliedMarkerColor = colorOr(alliedMarkerColor, new RgbColor(75, 160, 235));
        hostileMarkerColor = colorOr(hostileMarkerColor, new RgbColor(225, 70, 62));
        unknownMarkerColor = colorOr(unknownMarkerColor, new RgbColor(240, 180, 55));
        townsUrl = urlOr(townsUrl, DEFAULT_TOWNS_URL);
        worldUrl = urlOr(worldUrl, DEFAULT_WORLD_URL);
        portsUrl = urlOr(portsUrl, DEFAULT_PORTS_URL);
        hiddenTerritories = concurrentCopy(hiddenTerritories);
        trackedTerritories = concurrentCopy(trackedTerritories);
    }

    public void applyNodesOverlayMapStyle() {
        territoryFillOpacity = 145;
        borderOpacity = 235;
        internalBorderOpacity = 155;
        borderThickness = 1;
        territoryBorderColor = new RgbColor(24, 16, 16);
        showTerritoryIds = false;
        showTownNames = true;
        showNationNames = false;
        showOverviewTownNames = true;
        coreMarkerZoomThreshold = 0.8D;
    }

    private static RgbColor colorOr(RgbColor value, RgbColor fallback) {
        return value == null ? fallback : value;
    }

    private static Set<Integer> concurrentCopy(Set<Integer> source) {
        Set<Integer> result = ConcurrentHashMap.newKeySet();
        if (source != null) {
            result.addAll(source);
        }
        return result;
    }

    private static Set<String> concurrentUuidCopy(Set<String> source) {
        Set<String> result = ConcurrentHashMap.newKeySet();
        if (source == null) {
            return result;
        }
        for (String value : source) {
            String canonical = canonicalUuid(value);
            if (canonical != null) {
                result.add(canonical);
            }
        }
        return result;
    }

    private static String canonicalUuid(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.trim();
        if (cleaned.length() == 32) {
            cleaned = cleaned.substring(0, 8) + "-"
                    + cleaned.substring(8, 12) + "-"
                    + cleaned.substring(12, 16) + "-"
                    + cleaned.substring(16, 20) + "-"
                    + cleaned.substring(20);
        }
        try {
            return UUID.fromString(cleaned).toString();
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static String urlOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static int clampByte(int value) {
        return clamp(value, 0, 255);
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
