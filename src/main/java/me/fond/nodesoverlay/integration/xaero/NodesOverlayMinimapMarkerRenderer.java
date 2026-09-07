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
import net.minecraft.util.math.RotationAxis;
import xaero.common.HudMod;
import xaero.common.graphics.renderer.multitexture.MultiTextureRenderTypeRendererProvider;
import xaero.hud.minimap.element.render.MinimapElementGraphics;
import xaero.hud.minimap.element.render.MinimapElementReader;
import xaero.hud.minimap.element.render.MinimapElementRenderInfo;
import xaero.hud.minimap.element.render.MinimapElementRenderLocation;
import xaero.hud.minimap.element.render.MinimapElementRenderProvider;
import xaero.hud.minimap.element.render.MinimapElementRenderer;
import xaero.hud.minimap.element.render.over.MinimapElementOverMapRendererHandler;

import java.util.List;

/**
 * Xaero Minimap over-map element layer. It consumes the over-map handler's
 * exact transform so core chunks rotate with the terrain while icons and text
 * remain screen-upright.
 */
public final class NodesOverlayMinimapMarkerRenderer extends MinimapElementRenderer<
        XaeroMapMarker,
        XaeroMarkerRenderContext> {

    private static final int MAX_NODE_ICONS = 4;

    public NodesOverlayMinimapMarkerRenderer() {
        this(new XaeroMarkerRenderContext());
    }

    private NodesOverlayMinimapMarkerRenderer(XaeroMarkerRenderContext context) {
        super(new Reader(), new Provider(), context);
    }

    @Override
    public void preRender(
            MinimapElementRenderInfo info,
            VertexConsumerProvider.Immediate vanillaBuffers,
            MultiTextureRenderTypeRendererProvider xaeroBuffers
    ) {
        context.prepareMinimap(info, currentRenderState());
    }

    @Override
    public void postRender(
            MinimapElementRenderInfo info,
            VertexConsumerProvider.Immediate vanillaBuffers,
            MultiTextureRenderTypeRendererProvider xaeroBuffers
    ) {
    }

    @Override
    public boolean renderElement(
            XaeroMapMarker marker,
            boolean highlighted,
            boolean outOfBounds,
            double optionalDepth,
            float optionalScale,
            double partialX,
            double partialY,
            MinimapElementRenderInfo info,
            MinimapElementGraphics graphics,
            VertexConsumerProvider.Immediate vanillaBuffers
    ) {
        if (outOfBounds) {
            return false;
        }

        MatrixStack pose = graphics.pose();
        pose.push();
        pose.translate(partialX, partialY, optionalDepth);
        switch (marker.kind()) {
            case WAR -> renderWar(marker.warEvent(), graphics);
            case PORT -> renderPort(marker, graphics);
            case TERRITORY -> renderTerritory(marker, highlighted, graphics);
            case OVERVIEW -> {
                // Overview labels are intentionally world-map-only.
            }
        }
        pose.pop();
        return true;
    }

    private void renderWar(WarEvent event, MinimapElementGraphics graphics) {
        WarMarkerType icon = displayedWarMarker(event);
        XaeroMarkerDrawing.drawWar(
                graphics,
                icon,
                event.relation(),
                context.warArgb(event, 245),
                context.warIconScale()
        );
    }

    private void renderPort(XaeroMapMarker marker, MinimapElementGraphics graphics) {
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
            boolean highlighted,
            MinimapElementGraphics graphics
    ) {
        TerritoryView territory = marker.territory();
        TerritoryDefinition definition = territory.territory();
        double zoom = context.pixelsPerBlock();
        boolean coreVisible = context.shouldShowCore(territory);

        double leftBlocks = definition.coreChunkX() * 16.0D - definition.coreX();
        double topBlocks = definition.coreChunkZ() * 16.0D - definition.coreZ();
        int coreLeft = floor(leftBlocks * zoom);
        int coreTop = floor(topBlocks * zoom);
        int coreRight = ceil((leftBlocks + 16.0D) * zoom);
        int coreBottom = ceil((topBlocks + 16.0D) * zoom);
        double rightBlocks = leftBlocks + 16.0D;
        double bottomBlocks = topBlocks + 16.0D;
        int coreScreenTop = floor(Math.min(
                Math.min(
                        context.localScreenY(leftBlocks, topBlocks),
                        context.localScreenY(rightBlocks, topBlocks)
                ),
                Math.min(
                        context.localScreenY(leftBlocks, bottomBlocks),
                        context.localScreenY(rightBlocks, bottomBlocks)
                )
        ));
        int coreScreenBottom = ceil(Math.max(
                Math.max(
                        context.localScreenY(leftBlocks, topBlocks),
                        context.localScreenY(rightBlocks, topBlocks)
                ),
                Math.max(
                        context.localScreenY(leftBlocks, bottomBlocks),
                        context.localScreenY(rightBlocks, bottomBlocks)
                )
        ));

        if (coreVisible) {
            MatrixStack pose = graphics.pose();
            pose.push();
            float angle = (float) Math.atan2(
                    context.rotationCos(),
                    context.rotationSin()
            );
            pose.multiply(RotationAxis.POSITIVE_Z.rotation(angle));
            XaeroMarkerDrawing.drawCore(
                    graphics,
                    coreLeft,
                    coreTop,
                    coreRight,
                    coreBottom,
                    context.markerArgb(territory, 255) & 0x00FF_FFFF
            );
            pose.pop();

            double centerOffsetX = definition.coreChunkX() * 16.0D + 8.0D
                    - definition.coreX();
            double centerOffsetZ = definition.coreChunkZ() * 16.0D + 8.0D
                    - definition.coreZ();
            int centerX = floor(context.localScreenX(centerOffsetX, centerOffsetZ));
            int centerY = floor(context.localScreenY(centerOffsetX, centerOffsetZ));
            if ((coreRight - coreLeft) >= 8) {
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
                    coreVisible ? Math.min(-20, coreScreenTop - 16) : -20,
                    graphics
            );
        }

        if (context.detailedLabelsVisible() || (highlighted && coreVisible)) {
            int labelTop = coreVisible ? Math.max(8, coreScreenBottom + 3) : 8;
            renderLabels(
                    marker,
                    territory,
                    nodes,
                    labelTop,
                    highlighted,
                    graphics
            );
        }
    }

    private void renderNodes(
            TerritoryView territory,
            List<NodeDefinition> nodes,
            int top,
            MinimapElementGraphics graphics
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
            graphics.drawString(
                    MinecraftClient.getInstance().textRenderer,
                    "+" + (nodes.size() - MAX_NODE_ICONS),
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
            boolean highlighted,
            MinimapElementGraphics graphics
    ) {
        boolean detailed = context.detailedLabelsVisible();
        String territoryLine = detailed ? context.territoryLabel(territory) : "";
        String nodeLine = detailed ? context.nodeLabel(nodes) : "";
        String coreLine = highlighted && context.shouldShowCore(territory)
                ? "Core " + territory.territory().coreX() + ", "
                + territory.territory().coreZ() + "  (chunk "
                + territory.territory().coreChunkX() + ", "
                + territory.territory().coreChunkZ() + ')'
                : "";
        String issueLine = highlighted && territory.territory().hasDataIssues()
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
            MinimapElementGraphics graphics,
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

    private static MinimapOverMapRenderState currentRenderState() {
        if (HudMod.INSTANCE == null || HudMod.INSTANCE.getMinimap() == null) {
            return null;
        }
        MinimapElementOverMapRendererHandler handler =
                HudMod.INSTANCE.getMinimap().getOverMapRendererHandler();
        Object mixedHandler = handler;
        return mixedHandler instanceof MinimapOverMapRenderState state
                ? state
                : null;
    }

    @Override
    public boolean shouldRender(MinimapElementRenderLocation location) {
        return location == MinimapElementRenderLocation.OVER_MINIMAP
                && NodesOverlayRuntime.isActive()
                && NodesOverlayRuntime.settings().minimapOverlay;
    }

    @Override
    public int getOrder() {
        return 150;
    }

    private static int floor(double value) {
        return (int) Math.floor(value);
    }

    private static int ceil(double value) {
        return (int) Math.ceil(value);
    }

    private static final class Provider extends MinimapElementRenderProvider<
            XaeroMapMarker,
            XaeroMarkerRenderContext> {

        @Override
        public void begin(
                MinimapElementRenderLocation location,
                XaeroMarkerRenderContext context
        ) {
            context.begin();
        }

        @Override
        public boolean hasNext(
                MinimapElementRenderLocation location,
                XaeroMarkerRenderContext context
        ) {
            return context.hasNext();
        }

        @Override
        public XaeroMapMarker getNext(
                MinimapElementRenderLocation location,
                XaeroMarkerRenderContext context
        ) {
            return context.next();
        }

        @Override
        public void end(
                MinimapElementRenderLocation location,
                XaeroMarkerRenderContext context
        ) {
        }
    }

    private static final class Reader extends MinimapElementReader<
            XaeroMapMarker,
            XaeroMarkerRenderContext> {

        @Override
        public boolean isHidden(
                XaeroMapMarker marker,
                XaeroMarkerRenderContext context
        ) {
            if (marker.isOverview()) {
                return true;
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
        public double getRenderY(
                XaeroMapMarker marker,
                XaeroMarkerRenderContext context,
                float partialTicks
        ) {
            return 0.0D;
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
            return -8;
        }

        @Override
        public int getInteractionBoxRight(
                XaeroMapMarker marker,
                XaeroMarkerRenderContext context,
                float partialTicks
        ) {
            return 8;
        }

        @Override
        public int getInteractionBoxTop(
                XaeroMapMarker marker,
                XaeroMarkerRenderContext context,
                float partialTicks
        ) {
            return -24;
        }

        @Override
        public int getInteractionBoxBottom(
                XaeroMapMarker marker,
                XaeroMarkerRenderContext context,
                float partialTicks
        ) {
            return 24;
        }

        @Override
        public int getRenderBoxLeft(
                XaeroMapMarker marker,
                XaeroMarkerRenderContext context,
                float partialTicks
        ) {
            return -80;
        }

        @Override
        public int getRenderBoxRight(
                XaeroMapMarker marker,
                XaeroMarkerRenderContext context,
                float partialTicks
        ) {
            return 80;
        }

        @Override
        public int getRenderBoxTop(
                XaeroMapMarker marker,
                XaeroMarkerRenderContext context,
                float partialTicks
        ) {
            return -32;
        }

        @Override
        public int getRenderBoxBottom(
                XaeroMapMarker marker,
                XaeroMarkerRenderContext context,
                float partialTicks
        ) {
            return 40;
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
            return marker.territory() == null
                    ? marker.isPort() ? 0xFFFFC34D : 0xFF707070
                    : marker.territory().ownerColor().argb(255);
        }

        @Override
        public boolean shouldScaleBoxWithOptionalScale() {
            return false;
        }
    }
}
