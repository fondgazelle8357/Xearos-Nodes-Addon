package me.fond.nodesoverlay.integration.xaero;

import me.fond.nodesoverlay.NodesOverlayRuntime;
import me.fond.nodesoverlay.config.ServerSettings;
import me.fond.nodesoverlay.data.NodesOverlaySnapshot;
import me.fond.nodesoverlay.model.CoreMarkerMode;
import me.fond.nodesoverlay.model.NodeDefinition;
import me.fond.nodesoverlay.model.OwnershipState;
import me.fond.nodesoverlay.model.PortRecord;
import me.fond.nodesoverlay.model.RgbColor;
import me.fond.nodesoverlay.model.TerritoryView;
import me.fond.nodesoverlay.war.WarEvent;
import me.fond.nodesoverlay.war.WarRelation;
import me.fond.nodesoverlay.war.WarTracker;
import net.minecraft.client.MinecraftClient;
import xaero.hud.minimap.element.render.MinimapElementRenderInfo;
import xaero.map.element.render.ElementRenderInfo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Per-renderer, reusable frame context. Visible-territory queries use the
 * snapshot's spatial index; event and overview views are cached independently
 * from the frame's visible marker and label-collision collections.
 */
final class XaeroMarkerRenderContext {

    private static final double QUERY_MARGIN_BLOCKS = 48.0D;
    private static final int OVERVIEW_LABEL_SCREEN_MARGIN = 160;
    private static final int SCREEN_MARGIN_PIXELS = 18;
    private static final RgbColor DEFAULT_PORT_COLOR = new RgbColor(255, 195, 77);
    private final ArrayList<XaeroMapMarker> markers = new ArrayList<>();
    private final ArrayList<XaeroMapMarker> overviewMarkers = new ArrayList<>();
    private final ArrayList<XaeroMapMarker> territoryMarkers = new ArrayList<>();
    private final ArrayList<XaeroMapMarker> warMarkers = new ArrayList<>();
    private final ArrayList<XaeroMapMarker> portMarkers = new ArrayList<>();
    private final HashSet<Integer> attackedTerritories = new HashSet<>();
    private final LabelCollisionIndex labelCollisions = new LabelCollisionIndex();
    private final LabelCollisionIndex overviewLabelCollisions = new LabelCollisionIndex();

    private XaeroOverlayRuntime.State overlayState = XaeroOverlayRuntime.State.empty();
    private MarkerStyle style = MarkerStyle.inactive();
    private List<XaeroMapMarker> overviewIndex = List.of();
    private final OverviewCache overviewCache = new OverviewCache();
    private long styleRevision = Long.MIN_VALUE;
    private int cursor;
    private double centerX;
    private double centerZ;
    private double pixelsPerBlock;
    private double rotationSin;
    private double rotationCos = 1.0D;
    private int halfViewWidth;
    private int halfViewHeight;
    private boolean circular;
    private boolean minimap;
    private String localPlayerName = "";

    void prepareWorldMap(ElementRenderInfo info) {
        minimap = false;
        prepareBase(info.renderPos.x, info.renderPos.z, info.scale);
        if (!style.worldMapEnabled()
                || !XaeroHighlightSupport.shouldRender(info.mapDimension, true)) {
            clearFrame();
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        halfViewWidth = Math.max(1, client.getWindow().getScaledWidth() / 2);
        halfViewHeight = Math.max(1, client.getWindow().getScaledHeight() / 2);
        circular = false;
        rotationSin = 0.0D;
        rotationCos = 1.0D;

        double radiusX = halfViewWidth / pixelsPerBlock + QUERY_MARGIN_BLOCKS;
        double radiusZ = halfViewHeight / pixelsPerBlock + QUERY_MARGIN_BLOCKS;
        buildVisibleMarkers(radiusX, radiusZ);
    }

    void prepareMinimap(
            MinimapElementRenderInfo info,
            MinimapOverMapRenderState renderState
    ) {
        minimap = true;
        double zoom = renderState == null
                ? 0.0D
                : renderState.nodesoverlay$getPixelsPerBlock();
        prepareBase(info.renderPos.x, info.renderPos.z, zoom);
        if (!style.minimapEnabled()
                || !XaeroHighlightSupport.shouldRender(info.mapDimension, true)
                || renderState == null
                || zoom <= 0.0D) {
            clearFrame();
            return;
        }

        rotationSin = renderState.nodesoverlay$getRotationSin();
        rotationCos = renderState.nodesoverlay$getRotationCos();
        halfViewWidth = Math.max(1, renderState.nodesoverlay$getHalfViewWidth());
        halfViewHeight = Math.max(1, renderState.nodesoverlay$getHalfViewHeight());
        circular = renderState.nodesoverlay$isCircular();

        double paddedHalfWidth = halfViewWidth + SCREEN_MARGIN_PIXELS;
        double paddedHalfHeight = halfViewHeight + SCREEN_MARGIN_PIXELS;
        double radiusX;
        double radiusZ;
        if (circular) {
            double radius = Math.max(paddedHalfWidth, paddedHalfHeight)
                    / pixelsPerBlock + QUERY_MARGIN_BLOCKS;
            radiusX = radius;
            radiusZ = radius;
        } else {
            // Transform all four screen-space viewport corners back to world
            // axes. This avoids omitting territories at a rotated square
            // minimap's corners.
            radiusX = (Math.abs(rotationSin) * paddedHalfWidth
                    + Math.abs(rotationCos) * paddedHalfHeight)
                    / pixelsPerBlock + QUERY_MARGIN_BLOCKS;
            radiusZ = (Math.abs(rotationCos) * paddedHalfWidth
                    + Math.abs(rotationSin) * paddedHalfHeight)
                    / pixelsPerBlock + QUERY_MARGIN_BLOCKS;
        }
        buildVisibleMarkers(radiusX, radiusZ);
    }

    private void prepareBase(
            double renderX,
            double renderZ,
            double zoom
    ) {
        XaeroOverlayRuntime.State latestState = XaeroOverlayRuntime.state();
        long revision = NodesOverlayRuntime.revision();
        if (styleRevision != revision) {
            style = MarkerStyle.from(
                    NodesOverlayRuntime.settings(),
                    NodesOverlayRuntime.isActive()
            );
            styleRevision = revision;
        }
        overviewIndex = overviewCache.get(minimap, latestState.snapshot(), style,
                latestState.style().hiddenTerritories());
        overlayState = latestState;
        MinecraftClient client = MinecraftClient.getInstance();
        localPlayerName = client == null || client.player == null
                ? ""
                : client.player.getName().getString();
        centerX = renderX;
        centerZ = renderZ;
        pixelsPerBlock = Math.max(0.0001D, zoom);
        cursor = 0;
        markers.clear();
        overviewMarkers.clear();
        territoryMarkers.clear();
        warMarkers.clear();
        portMarkers.clear();
        attackedTerritories.clear();
        labelCollisions.clear();
        overviewLabelCollisions.clear();
    }

    private void buildVisibleMarkers(double radiusX, double radiusZ) {
        NodesOverlaySnapshot snapshot = overlayState.snapshot();
        if (snapshot.isEmpty()) {
            return;
        }

        List<WarEvent> events = NodesOverlayRuntime.warTracker().attackEvents();
        for (WarEvent event : events) {
            if (event.kind() == WarEvent.Kind.ATTACK && event.territoryId() >= 0) {
                attackedTerritories.add(event.territoryId());
            }
        }

        double minX = centerX - radiusX;
        double minZ = centerZ - radiusZ;
        double maxX = centerX + radiusX;
        double maxZ = centerZ + radiusZ;
        for (TerritoryView territory : snapshot.visibleTerritoryCores(
                minX,
                minZ,
                maxX,
                maxZ
        )) {
            if (overlayState.style().hiddenTerritories().contains(territory.territory().id())) {
                continue;
            }
            if (!hasVisibleTerritoryElement(territory)) {
                continue;
            }
            XaeroMapMarker marker = XaeroMapMarker.territory(territory);
            if (isMarkerVisible(marker, SCREEN_MARGIN_PIXELS)) {
                territoryMarkers.add(marker);
            }
        }

        if (overviewLabelsVisible()) {
            for (XaeroMapMarker marker : overviewIndex) {
                if (marker.blockX() >= minX
                        && marker.blockX() <= maxX
                        && marker.blockZ() >= minZ
                        && marker.blockZ() <= maxZ
                        && isMarkerVisible(marker, OVERVIEW_LABEL_SCREEN_MARGIN)) {
                    overviewMarkers.add(marker);
                }
            }
        }

        territoryMarkers.sort(Comparator
                .<XaeroMapMarker>comparingInt(marker ->
                        style.trackedTerritories().contains(
                                marker.territory().territory().id()
                        ) ? 0 : 1)
                .thenComparingInt(marker ->
                        attackedTerritories.contains(marker.territory().territory().id())
                                ? 0 : 1)
                .thenComparingDouble(marker -> {
                    double dx = marker.blockX() - centerX;
                    double dz = marker.blockZ() - centerZ;
                    return dx * dx + dz * dz;
                })
                .thenComparingInt(marker -> marker.territory().territory().id()));

        if (style.showWarIcons()) {
            for (WarEvent event : events) {
                TerritoryView territory = event.territoryId() < 0
                        ? snapshot.territoryAtChunk(event.chunkX(), event.chunkZ())
                        : snapshot.territory(event.territoryId());
                if ((event.kind() == WarEvent.Kind.TERRITORY_CAPTURE
                        || event.kind() == WarEvent.Kind.TERRITORY_LIBERATED)
                        && territory == null) {
                    // A territory capture has no reported coordinates. Until
                    // its ID exists in world.json, drawing it at the record's
                    // placeholder (0, 0) would create a false origin marker.
                    continue;
                }
                if (territory != null
                        && overlayState.style().hiddenTerritories().contains(
                        territory.territory().id()
                )) {
                    continue;
                }
                XaeroMapMarker marker = XaeroMapMarker.war(event, territory);
                if (marker.blockX() >= minX
                        && marker.blockX() <= maxX
                        && marker.blockZ() >= minZ
                        && marker.blockZ() <= maxZ
                        && isMarkerVisible(marker, SCREEN_MARGIN_PIXELS)) {
                    warMarkers.add(marker);
                }
            }
        }

        if (style.showPorts()) {
            boolean exact = pixelsPerBlock >= style.portExactZoomThreshold();
            for (PortRecord port : NodesOverlayRuntime.ports().values()) {
                XaeroMapMarker marker = XaeroMapMarker.port(port, exact);
                if (marker.blockX() >= minX
                        && marker.blockX() <= maxX
                        && marker.blockZ() >= minZ
                        && marker.blockZ() <= maxZ
                        && isMarkerVisible(marker, SCREEN_MARGIN_PIXELS)) {
                    portMarkers.add(marker);
                }
            }
            portMarkers.sort(Comparator.comparing(
                    marker -> marker.port().name(),
                    String.CASE_INSENSITIVE_ORDER
            ));
        }

        markers.addAll(overviewMarkers);
        markers.addAll(territoryMarkers);
        markers.addAll(portMarkers);
        markers.addAll(warMarkers);
        reserveWarIcons();
    }

    private void reserveWarIcons() {
        int collisionRadius = warCollisionRadius();
        for (XaeroMapMarker marker : warMarkers) {
            ScreenPoint point = project(marker.blockX(), marker.blockZ());
            labelCollisions.reserve(
                    point.x() - collisionRadius,
                    point.y() - collisionRadius,
                    point.x() + collisionRadius,
                    point.y() + collisionRadius
            );
        }
    }

    private boolean isMarkerVisible(XaeroMapMarker marker, int margin) {
        if (!minimap) {
            ScreenPoint point = project(marker.blockX(), marker.blockZ());
            return Math.abs(point.x()) <= halfViewWidth + margin
                    && Math.abs(point.y()) <= halfViewHeight + margin;
        }
        ScreenPoint point = project(marker.blockX(), marker.blockZ());
        if (circular) {
            double radius = Math.max(halfViewWidth, halfViewHeight) + margin;
            return point.x() * point.x() + point.y() * point.y() <= radius * radius;
        }
        return Math.abs(point.x()) <= halfViewWidth + margin
                && Math.abs(point.y()) <= halfViewHeight + margin;
    }

    void clearFrame() {
        cursor = 0;
        markers.clear();
        overviewMarkers.clear();
        territoryMarkers.clear();
        warMarkers.clear();
        portMarkers.clear();
        attackedTerritories.clear();
        labelCollisions.clear();
        overviewLabelCollisions.clear();
    }

    void begin() {
        cursor = 0;
    }

    boolean hasNext() {
        return cursor < markers.size();
    }

    XaeroMapMarker next() {
        return markers.get(cursor++);
    }

    MarkerStyle style() {
        return style;
    }

    NodesOverlaySnapshot snapshot() {
        return overlayState.snapshot();
    }

    double pixelsPerBlock() {
        return pixelsPerBlock;
    }

    double rotationSin() {
        return rotationSin;
    }

    double rotationCos() {
        return rotationCos;
    }

    double localScreenX(double blockDeltaX, double blockDeltaZ) {
        double scaledX = blockDeltaX * pixelsPerBlock;
        double scaledZ = blockDeltaZ * pixelsPerBlock;
        return minimap
                ? rotationSin * scaledX - rotationCos * scaledZ
                : scaledX;
    }

    double localScreenY(double blockDeltaX, double blockDeltaZ) {
        double scaledX = blockDeltaX * pixelsPerBlock;
        double scaledZ = blockDeltaZ * pixelsPerBlock;
        return minimap
                ? rotationCos * scaledX + rotationSin * scaledZ
                : scaledZ;
    }

    boolean detailedLabelsVisible() {
        return pixelsPerBlock >= style.coreMarkerZoomThreshold();
    }

    boolean nodeIconsVisible() {
        return style.showNodeIcons() && detailedLabelsVisible();
    }

    boolean overviewLabelsVisible() {
        return overviewLabelsVisible(minimap, pixelsPerBlock, style);
    }

    static boolean overviewLabelsVisible(
            boolean minimap,
            double pixelsPerBlock,
            MarkerStyle style
    ) {
        return !minimap
                && pixelsPerBlock < style.coreMarkerZoomThreshold()
                && (style.showNationNames() || style.showOverviewTownNames());
    }

    boolean hasDetailedLabels(TerritoryView territory) {
        if (!detailedLabelsVisible()) {
            return false;
        }
        return style.showTerritoryIds()
                || territory.state() == OwnershipState.ANNEXED
                || territory.state() == OwnershipState.CLAIMED
                || territory.territory().hasDataIssues()
                || (style.showTownNames() && territory.owner() != null)
                || (style.showNationNames() && territory.ownerNation() != null)
                || (style.showNodeLabels() && !territory.territory().nodeIds().isEmpty());
    }

    boolean shouldShowCore(TerritoryView territory) {
        return switch (style.coreMarkerMode()) {
            case ALWAYS -> true;
            case ZOOMED_IN -> detailedLabelsVisible();
            case ATTACKED_ONLY ->
                    attackedTerritories.contains(territory.territory().id());
            case DISABLED -> false;
        };
    }

    boolean underAttack(TerritoryView territory) {
        return attackedTerritories.contains(territory.territory().id());
    }

    boolean claimLabel(XaeroMapMarker marker, int width, int height, int localTop) {
        ScreenPoint point = project(marker.blockX(), marker.blockZ());
        double left = point.x() - width / 2.0D - 2.0D;
        double top = point.y() + localTop - 2.0D;
        return labelCollisions.claim(left, top, left + width + 4.0D, top + height + 4.0D);
    }

    boolean claimCenteredLabel(XaeroMapMarker marker, int width, int height) {
        ScreenPoint point = project(marker.blockX(), marker.blockZ());
        double left = point.x() - width / 2.0D - 3.0D;
        double top = point.y() - height / 2.0D - 2.0D;
        return overviewLabelCollisions.claim(
                left,
                top,
                left + width + 6.0D,
                top + height + 4.0D
        );
    }

    static boolean showsWarIcon(WarEvent.Kind kind) {
        return kind == WarEvent.Kind.ATTACK;
    }

    private boolean hasVisibleTerritoryElement(TerritoryView territory) {
        return shouldShowCore(territory)
                || hasDetailedLabels(territory)
                || nodeIconsVisible() && !territory.territory().nodeIds().isEmpty();
    }

    List<NodeDefinition> nodes(TerritoryView territory) {
        if (territory.territory().nodeIds().isEmpty()) {
            return List.of();
        }
        ArrayList<NodeDefinition> result = new ArrayList<>(
                territory.territory().nodeIds().size()
        );
        for (String nodeId : territory.territory().nodeIds()) {
            NodeDefinition node = overlayState.snapshot().node(nodeId);
            if (node != null) {
                result.add(node);
            }
        }
        return result;
    }

    String territoryLabel(TerritoryView territory) {
        ArrayList<String> parts = new ArrayList<>(4);
        if (style.showTerritoryIds()) {
            parts.add("#" + territory.territory().id());
        }
        String badge = stateBadge(territory.state());
        if (!badge.isEmpty()) {
            parts.add(badge);
        }
        if (style.showTownNames() && territory.owner() != null) {
            parts.add(territory.ownerTownName());
        }
        if (style.showNationNames() && territory.ownerNation() != null) {
            parts.add(territory.ownerNationName());
        }
        return String.join(" | ", parts);
    }

    String nodeLabel(List<NodeDefinition> nodes) {
        if (!style.showNodeLabels() || nodes.isEmpty()) {
            return "";
        }
        return nodes.stream()
                .map(NodeDefinition::name)
                .map(XaeroMarkerRenderContext::displayName)
                .distinct()
                .reduce((left, right) -> left + ", " + right)
                .orElse("");
    }

    String portLabel(PortRecord port) {
        String controller = style.showPortOwnerInLabels()
                ? portControllerLabel(port)
                : null;
        return formatPortLabel(
                port,
                style.showPortNames(),
                style.showPortOwnerInLabels(),
                controller
        );
    }

    static String formatPortLabel(
            PortRecord port,
            boolean showPortNames,
            boolean showPortOwner,
            String controller
    ) {
        if (port == null || !showPortNames) {
            return "";
        }
        return !showPortOwner || controller == null || controller.isBlank()
                ? port.name()
                : port.name() + " | " + controller;
    }

    String portControllerLabel(PortRecord port) {
        WarTracker.TerritoryController controller = portController(port);
        if (controller == null) {
            return null;
        }
        return controller.town() == null ? controller.nation() : controller.town();
    }

    boolean exactPortPositionsVisible() {
        return pixelsPerBlock >= style.portExactZoomThreshold();
    }

    int markerArgb(TerritoryView territory, int alpha) {
        RgbColor color = territory.owner() == null
                ? overlayState.style().neutralColor()
                : territory.ownerColor();
        return color.argb(alpha);
    }

    int warArgb(WarEvent event, int alpha) {
        if (!style.relationshipAwareWarMarkers()) {
            RgbColor eventColor = event.color() == null ? RgbColor.NEUTRAL : event.color();
            return eventColor.argb(alpha);
        }
        RgbColor color = switch (event.relation()) {
            case FRIENDLY -> style.friendlyMarkerColor();
            case ALLIED -> style.alliedMarkerColor();
            case HOSTILE -> style.hostileMarkerColor();
            case UNKNOWN -> style.unknownMarkerColor();
        };
        return color.argb(alpha);
    }

    int portArgb(PortRecord port, int alpha) {
        WarTracker.TerritoryController controller = exactPortPositionsVisible()
                ? portController(port)
                : null;
        WarRelation relation = WarTracker.relationToController(
                controller,
                overlayState.snapshot(),
                localPlayerName
        );
        return portColor(relation, exactPortPositionsVisible(), style).argb(alpha);
    }

    private WarTracker.TerritoryController portController(PortRecord port) {
        if (port == null) {
            return null;
        }
        TerritoryView territory = overlayState.snapshot().territoryAtBlock(port.x(), port.z());
        return territory == null
                ? null
                : NodesOverlayRuntime.warTracker().controllerAtChunk(
                territory,
                port.chunkX(),
                port.chunkZ()
        );
    }

    static RgbColor portColor(WarRelation relation, boolean exact, MarkerStyle style) {
        if (!exact || relation == null || relation == WarRelation.UNKNOWN) {
            return DEFAULT_PORT_COLOR;
        }
        return switch (relation) {
            case FRIENDLY -> style.friendlyMarkerColor();
            case ALLIED -> style.alliedMarkerColor();
            case HOSTILE -> style.hostileMarkerColor();
            case UNKNOWN -> DEFAULT_PORT_COLOR;
        };
    }

    double warIconScale() {
        return XaeroMarkerDrawing.sanitizeWarIconScale(
                NodesOverlayRuntime.settings().warIconScale
        );
    }

    int warCollisionRadius() {
        return XaeroMarkerDrawing.warCollisionRadius(warIconScale());
    }

    private ScreenPoint project(double blockX, double blockZ) {
        double dx = (blockX - centerX) * pixelsPerBlock;
        double dz = (blockZ - centerZ) * pixelsPerBlock;
        if (!minimap) {
            return new ScreenPoint(dx, dz);
        }
        return new ScreenPoint(
                rotationSin * dx - rotationCos * dz,
                rotationCos * dx + rotationSin * dz
        );
    }

    static String stateBadge(OwnershipState state) {
        return switch (state) {
            case ANNEXED -> "[ANN]";
            case CLAIMED -> "[CLM]";
            default -> "";
        };
    }

    static String displayName(String value) {
        if (value == null || value.isBlank()) {
            return "Unknown";
        }
        String normalized = value.replace('_', ' ').trim();
        StringBuilder result = new StringBuilder(normalized.length());
        boolean capitalize = true;
        for (int index = 0; index < normalized.length(); index++) {
            char character = normalized.charAt(index);
            if (capitalize && Character.isLetter(character)) {
                result.append(Character.toUpperCase(character));
                capitalize = false;
            } else {
                result.append(character);
            }
            if (Character.isWhitespace(character)) {
                capitalize = true;
            }
        }
        return result.toString();
    }

    private static boolean equalsIgnoreCase(String first, String second) {
        return first != null && second != null && first.equalsIgnoreCase(second);
    }

    static List<XaeroMapMarker> buildOverviewMarkers(
            NodesOverlaySnapshot snapshot,
            MarkerStyle style,
            Set<Integer> hiddenTerritories
    ) {
        if (snapshot == null
                || snapshot.isEmpty()
                || style == null
                || !style.worldMapEnabled()
                || (!style.showNationNames() && !style.showOverviewTownNames())) {
            return List.of();
        }

        Map<String, OverviewGroup> groups = new LinkedHashMap<>();
        Set<Integer> hidden = hiddenTerritories == null
                ? Set.of()
                : hiddenTerritories;
        for (TerritoryView territory : snapshot.territories().values()) {
            if (territory.owner() == null
                    || hidden.contains(territory.territory().id())) {
                continue;
            }

            if (style.showNationNames() && territory.ownerNation() != null) {
                String label = territory.ownerNationName();
                String key = "nation:" + label.toLowerCase(java.util.Locale.ROOT);
                groups.computeIfAbsent(
                        key,
                        ignored -> new OverviewGroup(
                                label,
                                territory.ownerNation().color(),
                                territory.ownerNation().capital()
                        )
                ).add(territory);
            }
            if (style.showOverviewTownNames()) {
                // The nation label occupies its capital town's home core. Do
                // not place a second centered label at the same position when
                // both overview options are enabled.
                boolean representedByNation = style.showNationNames()
                        && territory.ownerNation() != null
                        && equalsIgnoreCase(
                        territory.ownerTownName(),
                        territory.ownerNation().capital()
                );
                if (!representedByNation) {
                    String label = territory.ownerTownName();
                    String key = "town:" + label.toLowerCase(java.util.Locale.ROOT);
                    groups.computeIfAbsent(
                            key,
                            ignored -> new OverviewGroup(
                                    label,
                                    territory.ownerColor(),
                                    territory.ownerTownName()
                            )
                    ).add(territory);
                }
            }
        }

        return groups.values().stream()
                .map(OverviewGroup::marker)
                .sorted(Comparator
                        .comparingInt(XaeroMapMarker::overviewWeight)
                        .reversed()
                        .thenComparing(
                                XaeroMapMarker::overviewLabel,
                                String.CASE_INSENSITIVE_ORDER
                        ))
                .toList();
    }

    static final class OverviewCache {
        private NodesOverlaySnapshot source;
        private Set<Integer> hidden = Set.of();
        private boolean nationNames;
        private boolean townNames;
        private List<XaeroMapMarker> markers = List.of();

        List<XaeroMapMarker> get(boolean minimap, NodesOverlaySnapshot snapshot,
                                 MarkerStyle style, Set<Integer> hiddenTerritories) {
            if (minimap || !style.worldMapEnabled()
                    || (!style.showNationNames() && !style.showOverviewTownNames())) {
                return List.of();
            }
            if (source != snapshot || !hidden.equals(hiddenTerritories)
                    || nationNames != style.showNationNames()
                    || townNames != style.showOverviewTownNames()) {
                markers = buildOverviewMarkers(snapshot, style, hiddenTerritories);
                source = snapshot;
                hidden = Set.copyOf(hiddenTerritories);
                nationNames = style.showNationNames();
                townNames = style.showOverviewTownNames();
            }
            return markers;
        }
    }

    record MarkerStyle(
            boolean worldMapEnabled,
            boolean minimapEnabled,
            boolean showTerritoryIds,
            boolean showTownNames,
            boolean showNationNames,
            boolean showOverviewTownNames,
            boolean showNodeIcons,
            boolean showNodeLabels,
            boolean showPorts,
            boolean showPortNames,
            boolean showPortOwnerInLabels,
            double portExactZoomThreshold,
            CoreMarkerMode coreMarkerMode,
            double coreMarkerZoomThreshold,
            boolean showWarIcons,
            boolean relationshipAwareWarMarkers,
            Set<Integer> trackedTerritories,
            RgbColor friendlyMarkerColor,
            RgbColor alliedMarkerColor,
            RgbColor hostileMarkerColor,
            RgbColor unknownMarkerColor
    ) {

        static MarkerStyle inactive() {
            return from(new ServerSettings(), false);
        }

        static MarkerStyle from(ServerSettings settings, boolean active) {
            return new MarkerStyle(
                    active && settings.worldMapOverlay,
                    active && settings.minimapOverlay,
                    settings.showTerritoryIds,
                    settings.showTownNames,
                    settings.showNationNames,
                    settings.showOverviewTownNames,
                    settings.showNodeIcons,
                    settings.showNodeLabels,
                    settings.showPorts,
                    settings.showPortNames,
                    settings.showPortOwnerInLabels,
                    Math.max(0.05D, settings.portExactZoomThreshold),
                    settings.coreMarkerMode == null
                            ? CoreMarkerMode.ZOOMED_IN
                            : settings.coreMarkerMode,
                    Math.max(0.05D, settings.coreMarkerZoomThreshold),
                    settings.showWarIcons,
                    settings.relationshipAwareWarMarkers,
                    settings.trackedTerritories == null
                            ? Set.of()
                            : Set.copyOf(settings.trackedTerritories),
                    colorOr(settings.friendlyMarkerColor, new RgbColor(80, 190, 110)),
                    colorOr(settings.alliedMarkerColor, new RgbColor(75, 160, 235)),
                    colorOr(settings.hostileMarkerColor, new RgbColor(225, 70, 62)),
                    colorOr(settings.unknownMarkerColor, new RgbColor(240, 180, 55))
            );
        }

        private static RgbColor colorOr(RgbColor color, RgbColor fallback) {
            return color == null ? fallback : color;
        }
    }

    private record ScreenPoint(double x, double y) {
    }

    private static final class OverviewGroup {

        private final String label;
        private final RgbColor color;
        private final String anchorTown;
        private final ArrayList<TerritoryView> territories = new ArrayList<>();
        private double weightedX;
        private double weightedZ;
        private int totalWeight;

        private OverviewGroup(String label, RgbColor color, String anchorTown) {
            this.label = label;
            this.color = color;
            this.anchorTown = anchorTown;
        }

        private void add(TerritoryView territory) {
            int weight = Math.max(1, territory.territory().chunks().length / 2);
            territories.add(territory);
            weightedX += territory.territory().coreX() * (double) weight;
            weightedZ += territory.territory().coreZ() * (double) weight;
            totalWeight += weight;
        }

        private XaeroMapMarker marker() {
            TerritoryView preferred = territories.stream()
                    .filter(territory -> equalsIgnoreCase(
                            territory.ownerTownName(),
                            anchorTown
                    ))
                    .filter(TerritoryView::townCore)
                    .min(Comparator.comparingInt(territory -> territory.territory().id()))
                    .orElseGet(() -> territories.stream()
                            .filter(territory -> equalsIgnoreCase(
                                    territory.ownerTownName(),
                                    anchorTown
                            ))
                            .min(Comparator.comparingInt(
                                    territory -> territory.territory().id()
                            ))
                            .orElse(null));
            if (preferred != null) {
                return XaeroMapMarker.overview(
                        label,
                        color,
                        totalWeight,
                        preferred.territory().coreX(),
                        preferred.territory().coreZ()
                );
            }
            double centerX = weightedX / totalWeight;
            double centerZ = weightedZ / totalWeight;
            TerritoryView anchor = territories.stream()
                    .min(Comparator.comparingDouble(territory -> {
                        double deltaX = territory.territory().coreX() - centerX;
                        double deltaZ = territory.territory().coreZ() - centerZ;
                        return deltaX * deltaX + deltaZ * deltaZ;
                    }))
                    .orElseThrow();
            return XaeroMapMarker.overview(
                    label,
                    color,
                    totalWeight,
                    anchor.territory().coreX(),
                    anchor.territory().coreZ()
            );
        }
    }

    private static final class LabelCollisionIndex {

        private static final int MAX_BOXES = 512;
        private static final int CELL_SIZE = 32;
        private final ArrayList<ScreenRectangle> boxes = new ArrayList<>();
        private final Map<Long, ArrayList<Integer>> cells = new java.util.HashMap<>();

        void clear() {
            boxes.clear();
            cells.clear();
        }

        void reserve(double left, double top, double right, double bottom) {
            if (boxes.size() < MAX_BOXES) {
                int index = boxes.size();
                boxes.add(new ScreenRectangle(left, top, right, bottom));
                forEachCell(left, top, right, bottom, key ->
                        cells.computeIfAbsent(key, ignored -> new ArrayList<>()).add(index));
            }
        }

        boolean claim(double left, double top, double right, double bottom) {
            HashSet<Integer> checked = new HashSet<>();
            int minCellX = floorCell(left);
            int maxCellX = floorCell(right);
            int minCellY = floorCell(top);
            int maxCellY = floorCell(bottom);
            for (int cellY = minCellY; cellY <= maxCellY; cellY++) {
                for (int cellX = minCellX; cellX <= maxCellX; cellX++) {
                    List<Integer> indices = cells.get(cellKey(cellX, cellY));
                    if (indices == null) {
                        continue;
                    }
                    for (int index : indices) {
                        if (!checked.add(index)) {
                            continue;
                        }
                        ScreenRectangle box = boxes.get(index);
                        if (left < box.right()
                                && right > box.left()
                                && top < box.bottom()
                                && bottom > box.top()) {
                            return false;
                        }
                    }
                }
            }
            if (boxes.size() >= MAX_BOXES) {
                return false;
            }
            reserve(left, top, right, bottom);
            return true;
        }

        private void forEachCell(
                double left,
                double top,
                double right,
                double bottom,
                java.util.function.LongConsumer consumer
        ) {
            for (int cellY = floorCell(top); cellY <= floorCell(bottom); cellY++) {
                for (int cellX = floorCell(left); cellX <= floorCell(right); cellX++) {
                    consumer.accept(cellKey(cellX, cellY));
                }
            }
        }

        private static int floorCell(double value) {
            return Math.floorDiv((int) Math.floor(value), CELL_SIZE);
        }

        private static long cellKey(int x, int y) {
            return (long) x << 32 ^ y & 0xFFFF_FFFFL;
        }
    }

    private record ScreenRectangle(double left, double top, double right, double bottom) {
    }
}
