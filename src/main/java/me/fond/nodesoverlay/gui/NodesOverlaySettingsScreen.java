package me.fond.nodesoverlay.gui;

import me.fond.nodesoverlay.NodesOverlayRuntime;
import me.fond.nodesoverlay.config.ServerSettings;
import me.fond.nodesoverlay.model.CoreMarkerMode;
import me.fond.nodesoverlay.model.RgbColor;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;

public final class NodesOverlaySettingsScreen extends Screen {

    private static final int PANEL_BACKGROUND = 0xE0121519;
    private static final int PANEL_BORDER = 0xFF39424B;
    private static final int ACCENT = 0xFF2A7681;
    private static final int ROW_HEIGHT = 24;
    private static final int BODY_TOP = 46;
    private static final int FOOTER_HEIGHT = 62;

    private final Screen parent;
    private final List<Row> rows = new ArrayList<>();
    private int panelLeft;
    private int panelRight;
    private int contentLeft;
    private int controlX;
    private int controlWidth;
    private int scroll;
    private int contentHeight;
    private boolean confirmClear;

    public NodesOverlaySettingsScreen(Screen parent) {
        super(Text.literal("Nodes Overlay"));
        this.parent = parent;
    }

    @Override
    public void tick() {
        if (!NodesOverlayRuntime.configurationAccessAllowed()) {
            if (client != null) {
                client.setScreen(new AccessUnavailableScreen(parent));
            }
            return;
        }
        super.tick();
    }

    @Override
    protected void init() {
        rows.clear();
        ServerSettings settings = NodesOverlayRuntime.settings();
        int panelWidth = Math.max(280, Math.min(620, width - 28));
        panelLeft = (width - panelWidth) / 2;
        panelRight = panelLeft + panelWidth;
        contentLeft = panelLeft + 16;
        controlWidth = Math.min(220, panelWidth / 2);
        controlX = panelRight - 16 - controlWidth;

        section("Activation");
        activationToggle(settings);
        text("Towns endpoint", settings.townsUrl, value -> settings.townsUrl = value, 512);
        text("World endpoint", settings.worldUrl, value -> settings.worldUrl = value, 512);
        text("Ports endpoint", settings.portsUrl, value -> settings.portsUrl = value, 512);

        section("Map overlays");
        toggle("World-map overlay", settings.worldMapOverlay, value -> settings.worldMapOverlay = value);
        toggle("Minimap overlay", settings.minimapOverlay, value -> settings.minimapOverlay = value);
        slider("Territory fill opacity", settings.territoryFillOpacity / 255.0D,
                value -> Integer.toString((int) Math.round(value * 255)),
                value -> settings.territoryFillOpacity = (int) Math.round(value * 255));
        slider("Border opacity", settings.borderOpacity / 255.0D,
                value -> Integer.toString((int) Math.round(value * 255)),
                value -> settings.borderOpacity = (int) Math.round(value * 255));
        slider("Internal-border opacity", settings.internalBorderOpacity / 255.0D,
                value -> Integer.toString((int) Math.round(value * 255)),
                value -> settings.internalBorderOpacity = (int) Math.round(value * 255));
        slider("Border thickness", (settings.borderThickness - 1) / 3.0D,
                value -> Integer.toString(1 + (int) Math.round(value * 3)),
                value -> settings.borderThickness = 1 + (int) Math.round(value * 3));
        slider("Occupation-stripe opacity", settings.occupationStripeOpacity / 255.0D,
                value -> Integer.toString((int) Math.round(value * 255)),
                value -> settings.occupationStripeOpacity = (int) Math.round(value * 255));
        color("Territory outline color", settings.territoryBorderColor,
                value -> settings.territoryBorderColor = value);
        action("Apply map style", button -> {
            settings.applyNodesOverlayMapStyle();
            NodesOverlayRuntime.saveSettings();
            clearAndInit();
        });

        section("Labels and markers");
        toggle("Territory IDs", settings.showTerritoryIds, value -> settings.showTerritoryIds = value);
        toggle("Town names", settings.showTownNames, value -> settings.showTownNames = value);
        toggle("Nation names", settings.showNationNames, value -> settings.showNationNames = value);
        toggle("Overview town names", settings.showOverviewTownNames,
                value -> settings.showOverviewTownNames = value);
        toggle("Node icons", settings.showNodeIcons, value -> settings.showNodeIcons = value);
        toggle("Detailed node labels", settings.showNodeLabels, value -> settings.showNodeLabels = value);
        toggle("Port markers", settings.showPorts, value -> settings.showPorts = value);
        toggle("Port names", settings.showPortNames, value -> settings.showPortNames = value);
        toggle("Port owner in labels", settings.showPortOwnerInLabels,
                value -> settings.showPortOwnerInLabels = value);
        slider("Exact port-position zoom",
                normalize(settings.portExactZoomThreshold, 0.05D, 2.0D),
                value -> String.format("%.2f px/block", denormalize(value, 0.05D, 2.0D)),
                value -> settings.portExactZoomThreshold = denormalize(value, 0.05D, 2.0D));
        cycleCoreMode(settings);
        slider("Core-marker zoom threshold",
                normalize(settings.coreMarkerZoomThreshold, 0.05D, 2.0D),
                value -> String.format("%.2f px/block", denormalize(value, 0.05D, 2.0D)),
                value -> settings.coreMarkerZoomThreshold = denormalize(value, 0.05D, 2.0D));

        section("War");
        toggle("War icons", settings.showWarIcons, value -> settings.showWarIcons = value);
        slider("War icon size",
                normalize(
                        settings.warIconScale,
                        ServerSettings.MINIMUM_WAR_ICON_SCALE,
                        ServerSettings.MAXIMUM_WAR_ICON_SCALE
                ),
                value -> String.format(
                        "%.2fx",
                        denormalize(
                                value,
                                ServerSettings.MINIMUM_WAR_ICON_SCALE,
                                ServerSettings.MAXIMUM_WAR_ICON_SCALE
                        )
                ),
                value -> settings.warIconScale = denormalize(
                        value,
                        ServerSettings.MINIMUM_WAR_ICON_SCALE,
                        ServerSettings.MAXIMUM_WAR_ICON_SCALE
                ));
        toggle("Relationship-aware attack colors", settings.relationshipAwareWarMarkers,
                value -> settings.relationshipAwareWarMarkers = value);
        toggle("World-space waypoints", settings.showWorldWaypoints, value -> settings.showWorldWaypoints = value);
        toggle("Waypoints through terrain", settings.waypointsThroughTerrain,
                value -> settings.waypointsThroughTerrain = value);
        slider("Waypoint maximum distance",
                normalize(settings.waypointMaximumDistance, 128, 20_000),
                value -> (int) Math.round(denormalize(value, 128, 20_000)) + " blocks",
                value -> settings.waypointMaximumDistance =
                        (int) Math.round(denormalize(value, 128, 20_000)));
        slider("War-history expiration",
                normalize(settings.warEventExpirationSeconds, 30, 1_800),
                value -> (int) Math.round(denormalize(value, 30, 1_800)) + " seconds",
                value -> settings.warEventExpirationSeconds =
                        (int) Math.round(denormalize(value, 30, 1_800)));
        color("Friendly marker color", settings.friendlyMarkerColor,
                value -> settings.friendlyMarkerColor = value);
        color("Allied marker color", settings.alliedMarkerColor,
                value -> settings.alliedMarkerColor = value);
        color("Hostile marker color", settings.hostileMarkerColor,
                value -> settings.hostileMarkerColor = value);
        color("Unknown marker color", settings.unknownMarkerColor,
                value -> settings.unknownMarkerColor = value);
        color("Neutral territory color", settings.neutralTerritoryColor,
                value -> settings.neutralTerritoryColor = value);

        section("Synchronization");
        slider("JSON update check",
                normalize(settings.jsonRefreshIntervalMinutes, 1, 60),
                value -> (int) Math.round(denormalize(value, 1, 60)) + " minutes",
                value -> settings.jsonRefreshIntervalMinutes =
                        (int) Math.round(denormalize(value, 1, 60)));
        slider("Forced reconciliation",
                normalize(settings.forcedReconciliationMinutes, 5, 120),
                value -> (int) Math.round(denormalize(value, 5, 120)) + " minutes",
                value -> settings.forcedReconciliationMinutes =
                        (int) Math.round(denormalize(value, 5, 120)));
        action("Unhide all territories", button -> {
            settings.hiddenTerritories.clear();
            NodesOverlayRuntime.saveSettings();
        });
        action("Remove all node waypoints", button -> {
            me.fond.nodesoverlay.integration.xaero.TerritoryWaypointManager.clear();
            button.setMessage(Text.literal("Node waypoints removed"));
        });

        int footerGap = 5;
        int footerButtonWidth = (panelWidth - 32 - footerGap * 2) / 3;
        int footerX = panelLeft + 16;
        addDrawableChild(ButtonWidget.builder(Text.literal("Refresh data"), button -> NodesOverlayRuntime.refreshNow())
                .dimensions(footerX, height - 34, footerButtonWidth, 20)
                .build());
        footerX += footerButtonWidth + footerGap;
        addDrawableChild(ButtonWidget.builder(Text.literal("Clear cache"), button -> {
                    if (confirmClear) {
                        confirmClear = false;
                        NodesOverlayRuntime.clearCache();
                        button.setMessage(Text.literal("Clear cache"));
                    } else {
                        confirmClear = true;
                        button.setMessage(Text.literal("Confirm clear").formatted(Formatting.RED));
                    }
                })
                .dimensions(footerX, height - 34, footerButtonWidth, 20)
                .build());
        footerX += footerButtonWidth + footerGap;
        addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, button -> close())
                .dimensions(footerX, height - 34, footerButtonWidth, 20)
                .build());

        relayout();
    }

    private void section(String title) {
        rows.add(new Row(title, null, true));
    }

    private void toggle(String label, boolean value, Consumer<Boolean> setter) {
        CyclingButtonWidget<Boolean> control = CyclingButtonWidget.onOffBuilder(value)
                .omitKeyText()
                .build(controlX, 0, controlWidth, 20, Text.empty(), (button, selected) -> {
                    setter.accept(selected);
                    NodesOverlayRuntime.saveSettings();
                });
        row(label, control);
    }

    private void activationToggle(ServerSettings settings) {
        CyclingButtonWidget<Boolean> control = CyclingButtonWidget.onOffBuilder(settings.enabled)
                .omitKeyText()
                .build(controlX, 0, controlWidth, 20, Text.empty(), (button, selected) -> {
                    settings.enabled = selected;
                    NodesOverlayRuntime.saveSettings();
                    clearAndInit();
                });
        row("Enable on this server", control);
    }

    private void cycleCoreMode(ServerSettings settings) {
        CyclingButtonWidget<CoreMarkerMode> control = CyclingButtonWidget
                .builder(mode -> Text.literal(mode.label()), settings.coreMarkerMode)
                .values(Arrays.asList(CoreMarkerMode.values()))
                .omitKeyText()
                .build(controlX, 0, controlWidth, 20, Text.empty(), (button, value) -> {
                    settings.coreMarkerMode = value;
                    NodesOverlayRuntime.saveSettings();
                });
        row("Core chunks", control);
    }

    private void slider(
            String label,
            double initialValue,
            DoubleFunction<String> formatter,
            DoubleConsumer setter
    ) {
        row(label, new SettingSlider(controlX, 0, controlWidth, initialValue, formatter, setter));
    }

    private void text(String label, String initial, Consumer<String> setter, int maximumLength) {
        TextFieldWidget field = new TextFieldWidget(textRenderer, controlX, 0, controlWidth, 20, Text.literal(label));
        field.setMaxLength(maximumLength);
        field.setText(initial);
        field.setChangedListener(value -> setter.accept(value.trim()));
        row(label, field);
    }

    private void color(String label, RgbColor initial, Consumer<RgbColor> setter) {
        TextFieldWidget field = new TextFieldWidget(textRenderer, controlX, 0, controlWidth, 20, Text.literal(label));
        field.setMaxLength(7);
        field.setText(String.format("#%06X", initial.rgb()));
        field.setChangedListener(value -> parseColor(value).ifPresent(color -> {
            setter.accept(color);
            NodesOverlayRuntime.changed();
        }));
        row(label, field);
    }

    private void action(String label, Consumer<ButtonWidget> action) {
        ButtonWidget button = ButtonWidget.builder(Text.literal(label), action::accept)
                .dimensions(controlX, 0, controlWidth, 20)
                .build();
        row(label, button);
    }

    private void row(String label, ClickableWidget widget) {
        rows.add(new Row(label, widget, false));
        addDrawableChild(widget);
    }

    private void relayout() {
        int y = BODY_TOP - scroll;
        int bottom = height - FOOTER_HEIGHT;
        for (Row row : rows) {
            int rowHeight = row.section ? 22 : ROW_HEIGHT;
            if (row.widget != null) {
                row.widget.setX(controlX);
                row.widget.setY(y + 1);
                row.widget.visible = y + 1 >= BODY_TOP && y + 21 <= bottom;
            }
            row.y = y;
            y += rowHeight;
        }
        contentHeight = Math.max(0, y + scroll - BODY_TOP);
        scroll = Math.max(0, Math.min(scroll, maxScroll()));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xB0000000);
        context.fill(panelLeft - 1, 18, panelRight + 1, height - 12, PANEL_BORDER);
        context.fill(panelLeft, 19, panelRight, height - 13, PANEL_BACKGROUND);
        context.fill(panelLeft, 19, panelRight, 22, ACCENT);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 27, 0xFFFFFF);

        int bottom = height - FOOTER_HEIGHT;
        context.enableScissor(panelLeft + 1, BODY_TOP, panelRight - 1, bottom);
        for (Row row : rows) {
            if (row.y + 21 < BODY_TOP || row.y > bottom) {
                continue;
            }
            if (row.section) {
                context.fill(contentLeft, row.y + 15, panelRight - 16, row.y + 16, 0x663D8791);
                context.drawText(textRenderer, row.label, contentLeft, row.y + 3, 0xFF8FD4DC, false);
            } else {
                context.drawText(
                        textRenderer,
                        textRenderer.trimToWidth(row.label, Math.max(60, controlX - contentLeft - 10)),
                        contentLeft,
                        row.y + 7,
                        0xFFE3E8EC,
                        false
                );
            }
        }
        context.disableScissor();
        context.fill(panelLeft, bottom, panelRight, bottom + 1, PANEL_BORDER);
        super.render(context, mouseX, mouseY, delta);

        String status = NodesOverlayRuntime.syncStatus().message();
        context.drawText(
                textRenderer,
                textRenderer.trimToWidth(status, panelRight - panelLeft - 32),
                panelLeft + 16,
                height - 49,
                NodesOverlayRuntime.syncStatus().state() == me.fond.nodesoverlay.sync.SyncStatus.State.WARNING
                        ? 0xFFFFA55A
                        : 0xFF9AA6AF,
                false
        );
        drawScrollbar(context);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (mouseX >= panelLeft && mouseX <= panelRight
                && mouseY >= BODY_TOP && mouseY <= height - FOOTER_HEIGHT) {
            scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.round(verticalAmount * 22)));
            relayout();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        return super.mouseClicked(click, doubled);
    }

    @Override
    public void close() {
        NodesOverlayRuntime.saveSettings();
        if (client != null) {
            client.setScreen(parent);
        }
    }

    @Override
    public void removed() {
        NodesOverlayRuntime.saveSettings();
        super.removed();
    }

    private int maxScroll() {
        return Math.max(0, contentHeight - (height - FOOTER_HEIGHT - BODY_TOP));
    }

    private void drawScrollbar(DrawContext context) {
        int maximum = maxScroll();
        if (maximum <= 0) {
            return;
        }
        int top = BODY_TOP;
        int bottom = height - FOOTER_HEIGHT;
        int trackHeight = bottom - top;
        int thumbHeight = Math.max(24, trackHeight * trackHeight / Math.max(trackHeight, contentHeight));
        int thumbY = top + (trackHeight - thumbHeight) * scroll / maximum;
        context.fill(panelRight - 6, top, panelRight - 3, bottom, 0x443A434B);
        context.fill(panelRight - 6, thumbY, panelRight - 3, thumbY + thumbHeight, ACCENT);
    }

    private static java.util.Optional<RgbColor> parseColor(String text) {
        String value = text == null ? "" : text.trim();
        if (value.startsWith("#")) {
            value = value.substring(1);
        }
        if (value.length() != 6) {
            return java.util.Optional.empty();
        }
        try {
            int rgb = Integer.parseUnsignedInt(value, 16);
            return java.util.Optional.of(new RgbColor(rgb >> 16 & 255, rgb >> 8 & 255, rgb & 255));
        } catch (NumberFormatException ignored) {
            return java.util.Optional.empty();
        }
    }

    private static double normalize(double value, double minimum, double maximum) {
        return Math.max(0.0D, Math.min(1.0D, (value - minimum) / (maximum - minimum)));
    }

    private static double denormalize(double value, double minimum, double maximum) {
        return minimum + Math.max(0.0D, Math.min(1.0D, value)) * (maximum - minimum);
    }

    private static final class Row {
        private final String label;
        private final ClickableWidget widget;
        private final boolean section;
        private int y;

        private Row(String label, ClickableWidget widget, boolean section) {
            this.label = label;
            this.widget = widget;
            this.section = section;
        }
    }

    private final class SettingSlider extends SliderWidget {
        private final DoubleFunction<String> formatter;
        private final DoubleConsumer setter;

        private SettingSlider(
                int x,
                int y,
                int width,
                double initialValue,
                DoubleFunction<String> formatter,
                DoubleConsumer setter
        ) {
            super(x, y, width, 20, Text.empty(), Math.max(0.0D, Math.min(1.0D, initialValue)));
            this.formatter = formatter;
            this.setter = setter;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Text.literal(formatter.apply(value)));
        }

        @Override
        protected void applyValue() {
            setter.accept(value);
            NodesOverlayRuntime.changed();
        }
    }
}
