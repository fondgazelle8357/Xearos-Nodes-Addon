package me.fond.nodesoverlay.integration.xaero;

import me.fond.nodesoverlay.NodesOverlayRuntime;
import me.fond.nodesoverlay.model.NodeDefinition;
import me.fond.nodesoverlay.model.PortRecord;
import me.fond.nodesoverlay.model.TerritoryDefinition;
import me.fond.nodesoverlay.model.TerritoryView;
import me.fond.nodesoverlay.war.WarEvent;
import me.fond.nodesoverlay.war.WarMarkerType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import xaero.lib.client.gui.widget.Tooltip;
import xaero.map.element.MapElementGraphics;
import xaero.map.element.render.ElementReader;
import xaero.map.element.render.ElementRenderInfo;
import xaero.map.element.render.ElementRenderLocation;
import xaero.map.element.render.ElementRenderProvider;
import xaero.map.element.render.ElementRenderer;
import xaero.map.graphics.renderer.multitexture.MultiTextureRenderTypeRendererProvider;

import java.util.ArrayList;
import java.util.List;

/**
 * Xaero World Map element layer for core chunks, node resources, compact
 * ownership labels and live war markers.
 */
public final class NodesOverlayWorldMapMarkerRenderer extends ElementRenderer<
        XaeroMapMarker,
        XaeroMarkerRenderContext,
        NodesOverlayWorldMapMarkerRenderer> {

    private static final int MAX_NODE_ICONS = 6;
    private static final float OVERVIEW_LABEL_SCALE = 1.25F;
    private static final int OVERVIEW_LABEL_HEIGHT = 12;

    public NodesOverlayWorldMapMarkerRenderer() {
        this(new XaeroMarkerRenderContext());
    }

    private NodesOverlayWorldMapMarkerRenderer(XaeroMarkerRenderContext context) {
        super(context, new Provider(), new Reader());
    }

    @Override
    public void preRender(
            ElementRenderInfo info,
            VertexConsumerProvider.Immediate vanillaBuffers,
            MultiTextureRenderTypeRendererProvider xaeroBuffers,
            boolean shadow
    ) {
        context.prepareWorldMap(info);
    }

    @Override
    public void postRender(
            ElementRenderInfo info,
            VertexConsumerProvider.Immediate vanillaBuffers,
            MultiTextureRenderTypeRendererProvider xaeroBuffers,
            boolean shadow
    ) {
    }

    @Override
    public void renderElementShadow(
            XaeroMapMarker marker,
            boolean hovered,
            float optionalScale,
            double partialX,
            double partialY,
            ElementRenderInfo info,
            MapElementGraphics graphics,
            VertexConsumerProvider.Immediate vanillaBuffers,
            MultiTextureRenderTypeRendererProvider xaeroBuffers
    ) {
    }

    @Override
    public boolean renderElement(
            XaeroMapMarker marker,
            boolean hovered,
            double optionalDepth,
            float optionalScale,
            double partialX,
            double partialY,
            ElementRenderInfo info,
            MapElementGraphics graphics,
            VertexConsumerProvider.Immediate vanillaBuffers,
            MultiTextureRenderTypeRendererProvider xaeroBuffers
    ) {
        MatrixStack pose = graphics.pose();
        pose.push();
        pose.translate(partialX, partialY, 0.0D);
        switch (marker.kind()) {
            case WAR -> renderWar(marker.warEvent(), graphics);
            case PORT -> renderPort(marker, graphics);
            case TERRITORY -> renderTerritory(marker, hovered, graphics);
            case OVERVIEW -> renderOverviewLabel(marker, graphics);
        }
        pose.pop();
        return true;
    }

    private void renderWar(WarEvent event, MapElementGraphics graphics) {
        WarMarkerType icon = displayedWarMarker(event);
        XaeroMarkerDrawing.drawWar(
                graphics,
                icon,
                event.relation(),
                context.warArgb(event, 245),
                context.warIconScale()
        );
    }

    private void renderOverviewLabel(
            XaeroMapMarker marker,
            MapElementGraphics graphics
    ) {
        String label = marker.overviewLabel();
        if (label == null || label.isBlank()) {
            return;
        }
        TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
        int textWidth = textRenderer.getWidth(label);
        int scaledWidth = ceil(textWidth * OVERVIEW_LABEL_SCALE);
        if (!context.claimCenteredLabel(
                marker,
                scaledWidth,
                OVERVIEW_LABEL_HEIGHT
        )) {
            return;
        }

        MatrixStack pose = graphics.pose();
        pose.push();
        pose.scale(OVERVIEW_LABEL_SCALE, OVERVIEW_LABEL_SCALE, 1.0F);
        graphics.drawString(
                textRenderer,
                label,
                -textWidth / 2,
                -5,
                0xFFFFFFFF,
                true
        );
        pose.pop();
    }

    private void renderPort(XaeroMapMarker marker, MapElementGraphics graphics) {
        PortRecord port = marker.port();
        boolean exact = context.exactPortPositionsVisible();
        int color = context.portArgb(port, 255);
        XaeroMarkerDrawing.drawPort(graphics, exact, color);
        String label = context.portLabel(port);
        if (label.isEmpty()) {
            return;
        }
        TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
        int width = textRenderer.getWidth(label);
        if (!context.claimLabel(marker, width, 10, 10)) {
            return;
        }
        int left = -width / 2;
        graphics.fill(left - 3, 8, left + width + 3, 20, 0xC4000000);
        graphics.drawString(textRenderer, label, left, 10, color, true);
    }

    private void renderTerritory(
            XaeroMapMarker marker,
            boolean hovered,
            MapElementGraphics graphics
    ) {
        TerritoryView territory = marker.territory();
        TerritoryDefinition definition = territory.territory();
        double zoom = context.pixelsPerBlock();
        boolean coreVisible = context.shouldShowCore(territory);

        int coreLeft = floor((definition.coreChunkX() * 16.0D - definition.coreX()) * zoom);
        int coreTop = floor((definition.coreChunkZ() * 16.0D - definition.coreZ()) * zoom);
        int coreRight = ceil(((definition.coreChunkX() + 1) * 16.0D
                - definition.coreX()) * zoom);
        int coreBottom = ceil(((definition.coreChunkZ() + 1) * 16.0D
                - definition.coreZ()) * zoom);

        if (coreVisible) {
            XaeroMarkerDrawing.drawCore(
                    graphics,
                    coreLeft,
                    coreTop,
                    coreRight,
                    coreBottom,
                    context.markerArgb(territory, 255) & 0x00FF_FFFF
            );
            int centerX = floor((definition.coreChunkX() * 16.0D + 8.0D
                    - definition.coreX()) * zoom);
            int centerY = floor((definition.coreChunkZ() * 16.0D + 8.0D
                    - definition.coreZ()) * zoom);
            if (Math.min(coreRight - coreLeft, coreBottom - coreTop) >= 8) {
                drawCoreLetter(graphics, centerX, centerY);
            }
        }

        List<NodeDefinition> nodes = context.nodeIconsVisible()
                || (context.detailedLabelsVisible() && context.style().showNodeLabels())
                ? context.nodes(territory)
                : List.of();
        if (context.nodeIconsVisible() && !nodes.isEmpty()) {
            renderNodes(
                    territory,
                    nodes,
                    coreVisible ? Math.min(-18, coreTop - 16) : -18,
                    graphics
            );
        }

        if (context.detailedLabelsVisible() || (hovered && coreVisible)) {
            int labelTop = coreVisible ? Math.max(9, coreBottom + 3) : 9;
            renderLabels(marker, territory, nodes, labelTop, hovered, graphics);
        }
    }

    private void renderNodes(
            TerritoryView territory,
            List<NodeDefinition> nodes,
            int top,
            MapElementGraphics graphics
    ) {
        int iconCount = Math.min(nodes.size(), MAX_NODE_ICONS);
        int rowWidth = iconCount * (XaeroMarkerDrawing.NODE_BADGE_SIZE + 1) - 1;
        int left = -rowWidth / 2;
        int ownerColor = context.markerArgb(territory, 225);
        for (int index = 0; index < iconCount; index++) {
            XaeroMarkerDrawing.drawNode(
                    graphics,
                    nodes.get(index),
                    left + index * (XaeroMarkerDrawing.NODE_BADGE_SIZE + 1),
                    top,
                    ownerColor
            );
        }
        if (nodes.size() > MAX_NODE_ICONS) {
            String extra = "+" + (nodes.size() - MAX_NODE_ICONS);
            graphics.drawString(
                    MinecraftClient.getInstance().textRenderer,
                    extra,
                    left + rowWidth + 2,
                    top + 3,
                    0xFFFFFFFF,
                    true
            );
        }
    }

    private void renderLabels(
            XaeroMapMarker marker,
            TerritoryView territory,
            List<NodeDefinition> nodes,
            int top,
            boolean hovered,
            MapElementGraphics graphics
    ) {
        boolean detailed = context.detailedLabelsVisible();
        String territoryLine = detailed ? context.territoryLabel(territory) : "";
        String nodeLine = detailed ? context.nodeLabel(nodes) : "";
        String coreLine = hovered && context.shouldShowCore(territory)
                ? "Core " + territory.territory().coreX() + ", "
                + territory.territory().coreZ() + "  (chunk "
                + territory.territory().coreChunkX() + ", "
                + territory.territory().coreChunkZ() + ')'
                : "";
        String issueLine = hovered && territory.territory().hasDataIssues()
                ? "Bugged node | Missing: "
                + String.join(", ", territory.territory().dataIssues())
                : "";
        if (territoryLine.isEmpty() && nodeLine.isEmpty()
                && issueLine.isEmpty() && coreLine.isEmpty()) {
            return;
        }

        TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
        int width = Math.max(
                Math.max(
                        territoryLine.isEmpty() ? 0 : textRenderer.getWidth(territoryLine),
                        nodeLine.isEmpty() ? 0 : textRenderer.getWidth(nodeLine)
                ),
                Math.max(
                        issueLine.isEmpty() ? 0 : textRenderer.getWidth(issueLine),
                        coreLine.isEmpty() ? 0 : textRenderer.getWidth(coreLine)
                )
        );
        int lineCount = (territoryLine.isEmpty() ? 0 : 1)
                + (nodeLine.isEmpty() ? 0 : 1)
                + (issueLine.isEmpty() ? 0 : 1)
                + (coreLine.isEmpty() ? 0 : 1);
        int height = lineCount * 10;
        if (!context.claimLabel(marker, width, height, top)) {
            return;
        }

        int left = -width / 2;
        graphics.fill(left - 3, top - 2, left + width + 3, top + height, 0xC4000000);
        graphics.fill(
                left - 3,
                top - 2,
                left - 1,
                top + height,
                context.markerArgb(territory, 255)
        );
        int lineY = top;
        if (!territoryLine.isEmpty()) {
            graphics.drawString(textRenderer, territoryLine, left, lineY, 0xFFFFFFFF, true);
            lineY += 10;
        }
        if (!nodeLine.isEmpty()) {
            graphics.drawString(textRenderer, nodeLine, left, lineY, 0xFFE8E8E8, true);
            lineY += 10;
        }
        if (!issueLine.isEmpty()) {
            graphics.drawString(textRenderer, issueLine, left, lineY, 0xFFFFA657, true);
            lineY += 10;
        }
        if (!coreLine.isEmpty()) {
            graphics.drawString(textRenderer, coreLine, left, lineY, 0xFFFFD966, true);
        }
    }

    private static void drawCoreLetter(
            MapElementGraphics graphics,
            int centerX,
            int centerY
    ) {
        graphics.fill(centerX - 4, centerY - 5, centerX + 5, centerY + 5, 0xB8000000);
        graphics.drawCenteredString(
                MinecraftClient.getInstance().textRenderer,
                "C",
                centerX,
                centerY - 4,
                0xFFFFFFFF
        );
    }

    private WarMarkerType displayedWarMarker(WarEvent event) {
        return event.kind() == WarEvent.Kind.ATTACK
                ? WarMarkerType.SWORD
                : event.marker();
    }

    @Override
    public boolean shouldRender(ElementRenderLocation location, boolean shadow) {
        return !shadow
                && location == ElementRenderLocation.WORLD_MAP
                && NodesOverlayRuntime.isActive();
    }

    @Override
    public int getOrder() {
        return 250;
    }

    @Override
    public boolean shouldBeDimScaled() {
        return false;
    }

    private static int floor(double value) {
        return (int) Math.floor(value);
    }

    private static int ceil(double value) {
        return (int) Math.ceil(value);
    }

    private static final class Provider extends ElementRenderProvider<
            XaeroMapMarker,
            XaeroMarkerRenderContext> {

        @Override
        public void begin(
                ElementRenderLocation location,
                XaeroMarkerRenderContext context
        ) {
            context.begin();
        }

        @Override
        public boolean hasNext(
                ElementRenderLocation location,
                XaeroMarkerRenderContext context
        ) {
            return context.hasNext();
        }

        @Override
        public XaeroMapMarker getNext(
                ElementRenderLocation location,
                XaeroMarkerRenderContext context
        ) {
            return context.next();
        }

        @Override
        public void end(
                ElementRenderLocation location,
                XaeroMarkerRenderContext context
        ) {
        }
    }

    private static final class Reader extends ElementReader<
            XaeroMapMarker,
            XaeroMarkerRenderContext,
            NodesOverlayWorldMapMarkerRenderer> {

        @Override
        public boolean isHidden(
                XaeroMapMarker marker,
                XaeroMarkerRenderContext context
        ) {
            if (marker.isOverview()) {
                return !context.overviewLabelsVisible();
            }
            if (marker.kind() == XaeroMapMarker.Kind.WAR) {
                return !context.style().showWarIcons();
            }
            if (marker.kind() == XaeroMapMarker.Kind.PORT) {
                return !context.style().showPorts();
            }
            boolean labels = context.hasDetailedLabels(marker.territory());
            boolean nodeIcons = context.nodeIconsVisible()
                    && !marker.territory().territory().nodeIds().isEmpty();
            return !context.shouldShowCore(marker.territory())
                    && !nodeIcons
                    && !labels;
        }

        @Override
        public double getRenderX(
                XaeroMapMarker marker,
                XaeroMarkerRenderContext context,
                float partialTicks
        ) {
            return marker.blockX();
        }

        @Override
        public double getRenderZ(
                XaeroMapMarker marker,
                XaeroMarkerRenderContext context,
                float partialTicks
        ) {
            return marker.blockZ();
        }

        @Override
        public int getInteractionBoxLeft(
                XaeroMapMarker marker,
                XaeroMarkerRenderContext context,
                float partialTicks
        ) {
            if (marker.isOverview()) {
                return -128;
            }
            if (marker.kind() == XaeroMapMarker.Kind.WAR) {
                return -context.warCollisionRadius();
            }
            if (!marker.isTerritory()) {
                return -7;
            }
            TerritoryDefinition definition = marker.territory().territory();
            if (!context.shouldShowCore(marker.territory())) {
                return -9;
            }
            return Math.min(-9, floor(
                    (definition.coreChunkX() * 16.0D - definition.coreX())
                            * context.pixelsPerBlock()
            ) - 2);
        }

        @Override
        public int getInteractionBoxRight(
                XaeroMapMarker marker,
                XaeroMarkerRenderContext context,
                float partialTicks
        ) {
            if (marker.isOverview()) {
                return 128;
            }
            if (marker.kind() == XaeroMapMarker.Kind.WAR) {
                return context.warCollisionRadius();
            }
            if (!marker.isTerritory()) {
                return 7;
            }
            TerritoryDefinition definition = marker.territory().territory();
            if (!context.shouldShowCore(marker.territory())) {
                return 9;
            }
            return Math.max(9, ceil(
                    ((definition.coreChunkX() + 1) * 16.0D - definition.coreX())
                            * context.pixelsPerBlock()
            ) + 2);
        }

        @Override
        public int getInteractionBoxTop(
                XaeroMapMarker marker,
                XaeroMarkerRenderContext context,
                float partialTicks
        ) {
            if (marker.isOverview()) {
                return -16;
            }
            if (marker.kind() == XaeroMapMarker.Kind.WAR) {
                return -context.warCollisionRadius();
            }
            if (!marker.isTerritory()) {
                return -7;
            }
            TerritoryDefinition definition = marker.territory().territory();
            if (!context.shouldShowCore(marker.territory())) {
                return -9;
            }
            return Math.min(-9, floor(
                    (definition.coreChunkZ() * 16.0D - definition.coreZ())
                            * context.pixelsPerBlock()
            ) - 2);
        }

        @Override
        public int getInteractionBoxBottom(
                XaeroMapMarker marker,
                XaeroMarkerRenderContext context,
                float partialTicks
        ) {
            if (marker.isOverview()) {
                return 16;
            }
            if (marker.kind() == XaeroMapMarker.Kind.WAR) {
                return context.warCollisionRadius();
            }
            if (!marker.isTerritory()) {
                return 7;
            }
            TerritoryDefinition definition = marker.territory().territory();
            if (!context.shouldShowCore(marker.territory())) {
                return 9;
            }
            return Math.max(9, ceil(
                    ((definition.coreChunkZ() + 1) * 16.0D - definition.coreZ())
                            * context.pixelsPerBlock()
            ) + 2);
        }

        @Override
        public int getRenderBoxLeft(
                XaeroMapMarker marker,
                XaeroMarkerRenderContext context,
                float partialTicks
        ) {
            return getInteractionBoxLeft(marker, context, partialTicks) - 4;
        }

        @Override
        public int getRenderBoxRight(
                XaeroMapMarker marker,
                XaeroMarkerRenderContext context,
                float partialTicks
        ) {
            return getInteractionBoxRight(marker, context, partialTicks) + 4;
        }

        @Override
        public int getRenderBoxTop(
                XaeroMapMarker marker,
                XaeroMarkerRenderContext context,
                float partialTicks
        ) {
            return getInteractionBoxTop(marker, context, partialTicks) - 20;
        }

        @Override
        public int getRenderBoxBottom(
                XaeroMapMarker marker,
                XaeroMarkerRenderContext context,
                float partialTicks
        ) {
            return getInteractionBoxBottom(marker, context, partialTicks) + 24;
        }

        @Override
        public int getLeftSideLength(XaeroMapMarker marker, MinecraftClient client) {
            return client.textRenderer.getWidth(getMenuName(marker));
        }

        @Override
        public String getMenuName(XaeroMapMarker marker) {
            if (marker.isOverview()) {
                return marker.overviewLabel();
            }
            if (marker.territory() == null) {
                return marker.isPort()
                        ? "Port " + marker.port().name()
                        : "War marker";
            }
            return "Territory #" + marker.territory().territory().id();
        }

        @Override
        public String getFilterName(XaeroMapMarker marker) {
            return getMenuName(marker);
        }

        @Override
        public int getMenuTextFillLeftPadding(XaeroMapMarker marker) {
            return 0;
        }

        @Override
        public int getRightClickTitleBackgroundColor(XaeroMapMarker marker) {
            if (marker.isOverview()) {
                return marker.overviewColor().argb(255);
            }
            if (marker.territory() == null) {
                return marker.isPort() ? 0xFFFFC34D : 0xFF707070;
            }
            return marker.territory().ownerColor().argb(255);
        }

        @Override
        public boolean shouldScaleBoxWithOptionalScale() {
            return false;
        }

        @Override
        public boolean isInteractable(
                ElementRenderLocation location,
                XaeroMapMarker marker
        ) {
            return location == ElementRenderLocation.WORLD_MAP && marker.isTerritory();
        }

        @Override
        public Tooltip getTooltip(
                XaeroMapMarker marker,
                XaeroMarkerRenderContext context,
                boolean overMenu
        ) {
            if (marker.isPort()) {
                PortRecord port = marker.port();
                List<String> tooltipLines = new ArrayList<>();
                tooltipLines.add("Port " + port.name());
                tooltipLines.add("Location: " + port.x() + ", " + port.z());
                String controller = context.portControllerLabel(port);
                if (controller != null) {
                    tooltipLines.add("Node controller: " + controller);
                }
                if (!port.groups().isEmpty()) {
                    tooltipLines.add("Groups: " + String.join(", ", port.groups()));
                }
                if (port.observedInGame()) {
                    tooltipLines.add("Updated from /port info");
                }
                return new Tooltip(Text.literal(xaeroTooltipText(tooltipLines)));
            }
            if (!marker.isTerritory()) {
                return null;
            }
            TerritoryView territory = marker.territory();
            TerritoryDefinition definition = territory.territory();
            StringBuilder heading = new StringBuilder()
                    .append("Territory #")
                    .append(definition.id());
            String badge = XaeroMarkerRenderContext.stateBadge(territory.state());
            if (!badge.isEmpty()) {
                heading.append(' ').append(badge);
            }
            List<String> tooltipLines = new ArrayList<>();
            tooltipLines.add(heading.toString());
            tooltipLines.add(
                    "State: " + XaeroMarkerRenderContext.displayName(
                            territory.state().name().toLowerCase()
                    )
            );
            tooltipLines.add(
                    "Owner: " + territory.ownerTownName()
                            + " (" + territory.ownerNationName() + ")"
            );
            if (definition.hasDataIssues()) {
                tooltipLines.add("Bugged node");
                tooltipLines.add("Missing: " + String.join(", ", definition.dataIssues()));
            }
            tooltipLines.add("Core: " + definition.coreX() + ", " + definition.coreZ());
            tooltipLines.add(
                    "Core chunk: "
                            + definition.coreChunkX()
                            + ", "
                            + definition.coreChunkZ()
            );
            if (context.underAttack(territory)) {
                tooltipLines.add("Under attack");
            }
            return new Tooltip(Text.literal(xaeroTooltipText(tooltipLines)));
        }
    }

    /**
     * Xaero's tooltip splitter recognizes a line feed only after the current
     * word has been finalized. A separating space prevents the control
     * character from being rendered as Minecraft's visible "LF" glyph.
     */
    static String xaeroTooltipText(List<String> lines) {
        return String.join(" \n", lines);
    }
}
