package me.fond.nodesoverlay.gui;

import me.fond.nodesoverlay.NodesOverlayRuntime;
import me.fond.nodesoverlay.integration.xaero.NodesOverlayMapNavigation;
import me.fond.nodesoverlay.integration.xaero.TerritoryWaypointManager;
import me.fond.nodesoverlay.model.NodeDefinition;
import me.fond.nodesoverlay.model.TerritoryDefinition;
import me.fond.nodesoverlay.model.TerritoryView;
import me.fond.nodesoverlay.war.WarEvent;
import me.fond.nodesoverlay.war.LiveCapture;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public final class TerritoryInfoScreen extends Screen {

    private static final int PANEL_BACKGROUND = 0xE6121519;
    private static final int PANEL_BORDER = 0xFF3B4650;
    private static final int MUTED = 0xFF9AA6AF;
    private static final int VALUE = 0xFFE7EDF1;
    private static final int SECTION = 0xFF7DD2DD;
    private static final int WARNING = 0xFFFFA657;
    private static final int BODY_TOP = 52;

    private final Screen parent;
    private final int territoryId;
    private int panelLeft;
    private int panelRight;
    private int bodyBottom;
    private int scroll;
    private int contentHeight;
    private String transientMessage;
    private long transientMessageUntil;
    private ButtonWidget waypointButton;
    private ButtonWidget hideButton;
    private ButtonWidget trackButton;

    public TerritoryInfoScreen(Screen parent, int territoryId) {
        super(Text.literal("Territory " + territoryId));
        this.parent = parent;
        this.territoryId = territoryId;
    }

    @Override
    protected void init() {
        int panelWidth = Math.max(280, Math.min(620, width - 24));
        panelLeft = (width - panelWidth) / 2;
        panelRight = panelLeft + panelWidth;
        int gap = 5;
        int innerWidth = panelWidth - 28;
        int[] buttonX = new int[5];
        int[] buttonY = new int[5];
        int[] buttonWidth = new int[5];
        if (panelWidth < 460) {
            int topWidth = (innerWidth - gap * 2) / 3;
            int bottomWidth = (innerWidth - gap) / 2;
            int topY = height - 67;
            int bottomY = height - 42;
            for (int index = 0; index < 3; index++) {
                buttonX[index] = panelLeft + 14 + index * (topWidth + gap);
                buttonY[index] = topY;
                buttonWidth[index] = topWidth;
            }
            for (int index = 3; index < 5; index++) {
                buttonX[index] = panelLeft + 14 + (index - 3) * (bottomWidth + gap);
                buttonY[index] = bottomY;
                buttonWidth[index] = bottomWidth;
            }
            bodyBottom = topY - 12;
        } else {
            int width = (innerWidth - gap * 4) / 5;
            for (int index = 0; index < 5; index++) {
                buttonX[index] = panelLeft + 14 + index * (width + gap);
                buttonY[index] = height - 42;
                buttonWidth[index] = width;
            }
            bodyBottom = height - 54;
        }
        waypointButton = addDrawableChild(ButtonWidget.builder(Text.literal("Waypoint"), button -> toggleWaypoint())
                .dimensions(buttonX[0], buttonY[0], buttonWidth[0], 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Focus core"), button -> focusCore())
                .dimensions(buttonX[1], buttonY[1], buttonWidth[1], 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Copy core"), button -> copyCore())
                .dimensions(buttonX[2], buttonY[2], buttonWidth[2], 20).build());
        hideButton = addDrawableChild(ButtonWidget.builder(Text.literal("Hide"), button -> toggleHidden())
                .dimensions(buttonX[3], buttonY[3], buttonWidth[3], 20).build());
        trackButton = addDrawableChild(ButtonWidget.builder(Text.literal("Track"), button -> toggleTracked())
                .dimensions(buttonX[4], buttonY[4], buttonWidth[4], 20).build());
        updateButtons();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        TerritoryView view = NodesOverlayRuntime.snapshot().territory(territoryId);
        int accent = view == null ? 0xFF707070 : 0xFF000000 | view.ownerColor().rgb();
        context.fill(0, 0, width, height, 0x8A000000);
        context.fill(panelLeft - 1, 18, panelRight + 1, height - 12, PANEL_BORDER);
        context.fill(panelLeft, 19, panelRight, height - 13, PANEL_BACKGROUND);
        context.fill(panelLeft, 19, panelRight, 23, accent);
        context.drawTextWithShadow(textRenderer, title, panelLeft + 14, 31, 0xFFFFFF);
        if (view != null) {
            String subtitle = view.ownerTownName() + " · " + view.ownerNationName() + " · " + view.state();
            context.drawText(
                    textRenderer,
                    textRenderer.trimToWidth(subtitle, panelRight - panelLeft - 150),
                    panelLeft + 110,
                    31,
                    MUTED,
                    false
            );
        }

        List<DisplayLine> lines = buildLines(view);
        int y = BODY_TOP - scroll;
        context.enableScissor(panelLeft + 8, BODY_TOP, panelRight - 8, bodyBottom);
        for (DisplayLine line : lines) {
            if (line.section) {
                y += 5;
                if (y >= BODY_TOP - 12 && y <= bodyBottom) {
                    context.drawText(textRenderer, line.text, panelLeft + 14, y, SECTION, false);
                    int start = panelLeft + 20 + textRenderer.getWidth(line.text);
                    context.fill(start, y + 5, panelRight - 14, y + 6, 0x554A6970);
                }
                y += 14;
                continue;
            }
            List<net.minecraft.text.OrderedText> wrapped =
                    textRenderer.wrapLines(Text.literal(line.text), panelRight - panelLeft - 28);
            for (net.minecraft.text.OrderedText wrappedLine : wrapped) {
                if (y >= BODY_TOP - 10 && y <= bodyBottom) {
                    context.drawText(textRenderer, wrappedLine, panelLeft + 14, y, line.color, false);
                }
                y += 11;
            }
            y += 2;
        }
        context.disableScissor();
        contentHeight = Math.max(0, y + scroll - BODY_TOP);
        scroll = Math.max(0, Math.min(scroll, maxScroll()));
        context.fill(panelLeft, bodyBottom, panelRight, bodyBottom + 1, PANEL_BORDER);

        if (transientMessage != null && System.currentTimeMillis() < transientMessageUntil) {
            context.drawCenteredTextWithShadow(
                    textRenderer,
                    transientMessage,
                    width / 2,
                    bodyBottom + 2,
                    0xFFB7E6C3
            );
        }
        updateButtons();
        super.render(context, mouseX, mouseY, delta);
        drawScrollbar(context);
    }

    private List<DisplayLine> buildLines(TerritoryView view) {
        List<DisplayLine> lines = new ArrayList<>();
        if (view == null) {
            lines.add(new DisplayLine("This territory is no longer present in the current data snapshot.", WARNING, false));
            return lines;
        }
        TerritoryDefinition territory = view.territory();
        section(lines, "General");
        value(lines, "Territory ID", Integer.toString(territory.id()));
        value(lines, "Node type", territory.nodeIds().isEmpty()
                ? "None"
                : String.join(", ", territory.nodeIds()));
        value(lines, "Territory size", territory.size() + " chunks");
        value(lines, "Core block", territory.coreX() + ", " + territory.coreZ());
        value(lines, "Core chunk", territory.coreChunkX() + ", " + territory.coreChunkZ());
        value(lines, "Neighbours", territory.neighbors().isEmpty()
                ? "None"
                : territory.neighbors().stream().map(String::valueOf).collect(Collectors.joining(", ")));

        section(lines, "Ownership");
        LiveCapture liveTerritoryCapture =
                NodesOverlayRuntime.warTracker().captureForTerritory(territoryId);
        value(lines, "Owning town", view.ownerTownName());
        value(lines, "Owning nation", view.ownerNationName());
        value(lines, "Color", String.format("#%06X", view.ownerColor().rgb()));
        value(lines, "Occupying town", liveTerritoryCapture == null
                ? view.occupierTownName()
                : fallback(liveTerritoryCapture.occupierTown()));
        value(lines, "Occupying nation", liveTerritoryCapture == null
                ? view.occupierNationName()
                : fallback(liveTerritoryCapture.occupierNation()));
        value(lines, "State", liveTerritoryCapture == null
                ? view.state().name()
                : "CAPTURED (live chat, awaiting reconciliation)");
        if (view.ownershipAmbiguous()) {
            value(lines, "Owner candidates", view.ownerCandidates().stream()
                    .map(candidate -> candidate.name())
                    .collect(Collectors.joining(", ")));
        }

        section(lines, "Production");
        if (territory.nodeIds().isEmpty()) {
            lines.add(new DisplayLine("No production node.", MUTED, false));
        }
        for (String nodeId : territory.nodeIds()) {
            NodeDefinition node = NodesOverlayRuntime.snapshot().node(nodeId);
            if (node == null) {
                lines.add(new DisplayLine(nodeId + ": definition unavailable", WARNING, false));
                continue;
            }
            lines.add(new DisplayLine(node.name() + "  [icon: " + node.icon() + "]", VALUE, false));
            value(lines, "Priority", Integer.toString(node.priority()));
            map(lines, "Cost", node.cost());
            map(lines, "Income", node.income());
            map(lines, "Ores", node.ores());
            map(lines, "Crops", node.crops());
            map(lines, "Animals", node.animals());
            map(lines, "Other modifiers", node.modifiers());
        }

        section(lines, "War status");
        List<WarEvent> warEvents = NodesOverlayRuntime.warTracker().eventsForTerritory(territoryId);
        List<WarEvent> attacks = warEvents.stream()
                .filter(event -> event.kind() == WarEvent.Kind.ATTACK)
                .toList();
        value(lines, "Status", attacks.isEmpty() ? "Safe / no active chat event" : "Under attack");
        if (!warEvents.isEmpty()) {
            WarEvent latest = warEvents.getFirst();
            value(lines, "Acting player", latest.attackingPlayer());
            value(lines, "Acting town", fallback(latest.attackingTown()));
            value(lines, "Acting nation", fallback(latest.attackingNation()));
            value(lines, "Latest result", eventDescription(latest));
            value(lines, "Latest event", age(latest.updatedAt()) + " ago");
            value(lines, "Last location", latest.blockX() + ", " + latest.blockY() + ", " + latest.blockZ());
            String captured = warEvents.stream()
                    .filter(event -> event.kind() == WarEvent.Kind.CHUNK_CAPTURE)
                    .map(event -> "(" + event.chunkX() + ", " + event.chunkZ() + ")")
                    .collect(Collectors.joining(", "));
            value(lines, "Recently captured chunks", captured.isBlank() ? "None" : captured);
        } else {
            lines.add(new DisplayLine("No unexpired war events were parsed for this territory.", MUTED, false));
        }
        return lines;
    }

    private static String eventDescription(WarEvent event) {
        return switch (event.kind()) {
            case ATTACK -> "Attack";
            case ATTACK_DEFENDED -> "Attack defended";
            case CHUNK_CAPTURE -> "Chunk captured";
            case CHUNK_DEFENDED -> "Chunk defended";
            case CHUNK_LIBERATED -> "Chunk liberated";
            case TERRITORY_CAPTURE -> "Territory captured";
            case TERRITORY_LIBERATED -> "Territory liberated";
        };
    }

    private void toggleWaypoint() {
        TerritoryView view = NodesOverlayRuntime.snapshot().territory(territoryId);
        if (view == null) {
            return;
        }
        if (NodesOverlayRuntime.warTracker().hasActiveAttackForTerritory(territoryId)) {
            if (NodesOverlayRuntime.warTracker().hasAutomaticWaypointForTerritory(territoryId)) {
                NodesOverlayRuntime.warTracker().removeWaypointForTerritory(territoryId);
                message("Automatic attack waypoint removed");
            } else {
                NodesOverlayRuntime.warTracker().restoreWaypointForTerritory(territoryId);
                message("Automatic attack waypoint restored");
            }
            updateButtons();
            return;
        }
        if (TerritoryWaypointManager.has(territoryId)) {
            TerritoryWaypointManager.remove(territoryId);
            message("Waypoint removed");
        } else {
            WarEvent latest = NodesOverlayRuntime.warTracker().eventsForTerritory(territoryId).stream()
                    .max(Comparator.comparing(WarEvent::updatedAt))
                    .orElse(null);
            message(TerritoryWaypointManager.create(view, latest)
                    ? "Temporary Xaero waypoint created"
                    : "Xaero waypoint session is unavailable");
        }
        updateButtons();
    }

    private void focusCore() {
        TerritoryView view = NodesOverlayRuntime.snapshot().territory(territoryId);
        if (view == null) {
            return;
        }
        if (parent instanceof NodesOverlayMapNavigation navigation) {
            navigation.nodesoverlay$focus(view.territory().coreX(), view.territory().coreZ());
            close();
        } else {
            message("Map navigation is unavailable");
        }
    }

    private void copyCore() {
        TerritoryView view = NodesOverlayRuntime.snapshot().territory(territoryId);
        if (view == null || client == null) {
            return;
        }
        client.keyboard.setClipboard(view.territory().coreX() + " " + view.territory().coreZ());
        message("Core coordinates copied");
    }

    private void toggleHidden() {
        if (!NodesOverlayRuntime.settings().hiddenTerritories.remove(territoryId)) {
            NodesOverlayRuntime.settings().hiddenTerritories.add(territoryId);
        }
        NodesOverlayRuntime.saveSettings();
        updateButtons();
    }

    private void toggleTracked() {
        if (!NodesOverlayRuntime.settings().trackedTerritories.remove(territoryId)) {
            NodesOverlayRuntime.settings().trackedTerritories.add(territoryId);
        }
        NodesOverlayRuntime.saveSettings();
        updateButtons();
    }

    private void updateButtons() {
        if (waypointButton != null) {
            boolean hasActiveAttack = NodesOverlayRuntime.warTracker().hasActiveAttackForTerritory(territoryId);
            boolean hasPoint = hasActiveAttack
                    ? NodesOverlayRuntime.warTracker().hasAutomaticWaypointForTerritory(territoryId)
                    : TerritoryWaypointManager.has(territoryId);
            waypointButton.setMessage(Text.literal(
                    hasPoint ? "Remove point" : "Waypoint"
            ));
        }
        if (hideButton != null) {
            hideButton.setMessage(Text.literal(
                    NodesOverlayRuntime.settings().hiddenTerritories.contains(territoryId) ? "Unhide" : "Hide"
            ));
        }
        if (trackButton != null) {
            trackButton.setMessage(Text.literal(
                    NodesOverlayRuntime.settings().trackedTerritories.contains(territoryId) ? "Untrack" : "Track"
            ));
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (mouseX >= panelLeft && mouseX <= panelRight && mouseY >= BODY_TOP && mouseY <= bodyBottom) {
            scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.round(verticalAmount * 20)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void close() {
        if (client != null) {
            client.setScreen(parent);
        }
    }

    private void drawScrollbar(DrawContext context) {
        int maximum = maxScroll();
        if (maximum <= 0) {
            return;
        }
        int track = bodyBottom - BODY_TOP;
        int thumb = Math.max(20, track * track / Math.max(track, contentHeight));
        int y = BODY_TOP + (track - thumb) * scroll / maximum;
        context.fill(panelRight - 6, BODY_TOP, panelRight - 3, bodyBottom, 0x443A434B);
        context.fill(panelRight - 6, y, panelRight - 3, y + thumb, SECTION);
    }

    private int maxScroll() {
        return Math.max(0, contentHeight - (bodyBottom - BODY_TOP));
    }

    private void message(String text) {
        transientMessage = text;
        transientMessageUntil = System.currentTimeMillis() + 2_500L;
    }

    private static void section(List<DisplayLine> lines, String title) {
        lines.add(new DisplayLine(title, SECTION, true));
    }

    private static void value(List<DisplayLine> lines, String label, String value) {
        lines.add(new DisplayLine(label + ": " + fallback(value), VALUE, false));
    }

    private static void map(List<DisplayLine> lines, String label, Map<String, Double> values) {
        if (values.isEmpty()) {
            value(lines, label, "None");
            return;
        }
        String formatted = values.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + " " + formatRate(entry.getValue()))
                .collect(Collectors.joining(", "));
        value(lines, label, formatted);
    }

    private static String formatRate(double value) {
        if (Math.rint(value) == value) {
            return Long.toString(Math.round(value));
        }
        return String.format(Locale.ROOT, "%.3f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private static String age(Instant timestamp) {
        long seconds = Math.max(0L, Duration.between(timestamp, Instant.now()).toSeconds());
        if (seconds < 60) {
            return seconds + "s";
        }
        if (seconds < 3_600) {
            return seconds / 60 + "m " + seconds % 60 + "s";
        }
        return seconds / 3_600 + "h " + seconds % 3_600 / 60 + "m";
    }

    private static String fallback(String value) {
        return value == null || value.isBlank() ? "Unknown" : value;
    }

    private record DisplayLine(String text, int color, boolean section) {
    }
}
