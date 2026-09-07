package me.fond.nodesoverlay.integration.xaero;

import me.fond.nodesoverlay.config.ServerSettings;
import me.fond.nodesoverlay.model.NodeDefinition;
import me.fond.nodesoverlay.war.WarMarkerType;
import me.fond.nodesoverlay.war.WarRelation;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import xaero.hud.minimap.element.render.MinimapElementGraphics;
import xaero.map.element.MapElementGraphics;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class XaeroMarkerDrawing {

    static final int NODE_ICON_SIZE = 12;
    static final int NODE_BADGE_SIZE = 14;
    static final int WAR_ICON_SCALE = 2;
    static final int WAR_BADGE_SIZE = 24;
    static final int WAR_SPRITE_SIZE = 16;
    static final int WAR_COLLISION_RADIUS = WAR_BADGE_SIZE / 2 + 1;
    static final int PORT_BADGE_SIZE = 9;
    private static final int BLACK = 0xE8000000;
    private static final int WHITE = 0xFFFFFFFF;
    private static final Identifier WAR_IRON_SWORD_SPRITE_ID =
            Identifier.ofVanilla("item/iron_sword");
    private static final Identifier WAR_DIAMOND_SWORD_SPRITE_ID =
            Identifier.ofVanilla("item/diamond_sword");
    private static final Map<String, Identifier> NODE_SPRITE_IDS =
            new ConcurrentHashMap<>();

    private XaeroMarkerDrawing() {
    }

    static void drawCore(
            MapElementGraphics graphics,
            int left,
            int top,
            int right,
            int bottom,
            int rgb
    ) {
        drawCore((x1, y1, x2, y2, color) ->
                graphics.fill(x1, y1, x2, y2, color), left, top, right, bottom, rgb);
    }

    static void drawCore(
            MinimapElementGraphics graphics,
            int left,
            int top,
            int right,
            int bottom,
            int rgb
    ) {
        drawCore((x1, y1, x2, y2, color) ->
                graphics.fill(x1, y1, x2, y2, color), left, top, right, bottom, rgb);
    }

    private static void drawCore(
            RectDrawer drawer,
            int left,
            int top,
            int right,
            int bottom,
            int rgb
    ) {
        if (right <= left || bottom <= top) {
            return;
        }
        int fill = 0x30000000 | rgb;
        int hatch = 0x88000000 | rgb;
        int border = 0xF0000000 | rgb;
        drawer.fill(left, top, right, bottom, fill);

        /*
         * The old hatch walked every fifth pixel across the complete scaled
         * core chunk. At Xaero's maximum zoom that became tens of thousands of
         * GUI draw calls for one marker every frame. Keep the useful hatch at
         * normal zoom, but cap it to a fixed draw budget and omit it when the
         * chunk is extremely large.
         */
        int width = right - left;
        int height = bottom - top;
        long area = (long) width * height;
        if (area <= 16_384L) {
            int step = area <= 4_096L ? 5 : 9;
            int draws = 0;
            for (int diagonal = left; diagonal < right + height && draws < 512; diagonal += step) {
                for (int y = top; y < bottom && draws < 512; y += 2) {
                    int x = diagonal - (y - top);
                    if (x >= left && x < right) {
                        drawer.fill(x, y, x + 1, Math.min(y + 2, bottom), hatch);
                        draws++;
                    }
                }
            }
        }

        int thickness = Math.min(2, Math.max(1, Math.min(width, height) / 8));
        drawer.fill(left, top, right, top + thickness, border);
        drawer.fill(left, bottom - thickness, right, bottom, border);
        drawer.fill(left, top + thickness, left + thickness, bottom - thickness, border);
        drawer.fill(right - thickness, top + thickness, right, bottom - thickness, border);
    }

    static void drawNode(
            MapElementGraphics graphics,
            NodeDefinition node,
            int left,
            int top,
            int ownerArgb
    ) {
        graphics.fill(left, top, left + NODE_BADGE_SIZE, top + NODE_BADGE_SIZE, BLACK);
        graphics.fill(
                left + 1,
                top + 1,
                left + NODE_BADGE_SIZE - 1,
                top + NODE_BADGE_SIZE - 1,
                ownerArgb
        );
        Sprite sprite = resolveNodeSprite(node);
        if (sprite != null) {
            graphics.blit(
                    sprite,
                    left + 1,
                    top + 1,
                    NODE_ICON_SIZE,
                    NODE_ICON_SIZE,
                    RenderPipelines.GUI_TEXTURED
            );
        } else {
            drawFallbackLetter(graphics, node, left, top);
        }
    }

    static void drawNode(
            MinimapElementGraphics graphics,
            NodeDefinition node,
            int left,
            int top,
            int ownerArgb
    ) {
        graphics.fill(left, top, left + NODE_BADGE_SIZE, top + NODE_BADGE_SIZE, BLACK);
        graphics.fill(
                left + 1,
                top + 1,
                left + NODE_BADGE_SIZE - 1,
                top + NODE_BADGE_SIZE - 1,
                ownerArgb
        );
        Sprite sprite = resolveNodeSprite(node);
        if (sprite != null) {
            graphics.blit(
                    sprite,
                    left + 1,
                    top + 1,
                    NODE_ICON_SIZE,
                    NODE_ICON_SIZE,
                    RenderPipelines.GUI_TEXTURED
            );
        } else {
            drawFallbackLetter(graphics, node, left, top);
        }
    }

    private static void drawFallbackLetter(
            MapElementGraphics graphics,
            NodeDefinition node,
            int left,
            int top
    ) {
        String letter = firstLetter(node);
        graphics.drawCenteredString(
                MinecraftClient.getInstance().textRenderer,
                letter,
                left + NODE_BADGE_SIZE / 2,
                top + 3,
                WHITE
        );
    }

    private static void drawFallbackLetter(
            MinimapElementGraphics graphics,
            NodeDefinition node,
            int left,
            int top
    ) {
        String letter = firstLetter(node);
        graphics.drawCenteredString(
                MinecraftClient.getInstance().textRenderer,
                letter,
                left + NODE_BADGE_SIZE / 2,
                top + 3,
                WHITE
        );
    }

    static void drawWar(
            MapElementGraphics graphics,
            WarMarkerType marker,
            WarRelation relation,
            int color,
            double iconScale
    ) {
        drawWarBadge((x1, y1, x2, y2, argb) ->
                graphics.fill(x1, y1, x2, y2, argb), color, iconScale);
        Sprite sprite = resolveSprite(warSwordSprite(relation));
        if (sprite != null) {
            int spriteSize = warSpriteSize(iconScale);
            graphics.pose().push();
            graphics.pose().multiply(RotationAxis.POSITIVE_Z.rotationDegrees(180.0F));
            graphics.blit(
                    sprite,
                    -spriteSize / 2,
                    -spriteSize / 2,
                    spriteSize,
                    spriteSize,
                    RenderPipelines.GUI_TEXTURED
            );
            graphics.pose().pop();
        } else {
            drawWarFallback((x1, y1, x2, y2, argb) ->
                    graphics.fill(x1, y1, x2, y2, argb), marker, iconScale);
        }
    }

    static void drawPort(MapElementGraphics graphics, boolean exact, int color) {
        drawPort((x1, y1, x2, y2, argb) ->
                graphics.fill(x1, y1, x2, y2, argb), exact, color);
    }

    static void drawPort(MinimapElementGraphics graphics, boolean exact, int color) {
        drawPort((x1, y1, x2, y2, argb) ->
                graphics.fill(x1, y1, x2, y2, argb), exact, color);
    }

    private static void drawPort(RectDrawer drawer, boolean exact, int port) {
        int outline = 0xF0000000;
        drawer.fill(-5, -5, 5, 5, outline);
        drawer.fill(-4, -4, 4, 4, port);
        drawer.fill(-2, -2, 2, 2, 0xFF5B3515);
        if (exact) {
            drawer.fill(-7, -1, -4, 1, port);
            drawer.fill(4, -1, 7, 1, port);
            drawer.fill(-1, -7, 1, -4, port);
            drawer.fill(-1, 4, 1, 7, port);
        }
    }

    static void drawWar(
            MinimapElementGraphics graphics,
            WarMarkerType marker,
            WarRelation relation,
            int color,
            double iconScale
    ) {
        drawWarBadge((x1, y1, x2, y2, argb) ->
                graphics.fill(x1, y1, x2, y2, argb), color, iconScale);
        Sprite sprite = resolveSprite(warSwordSprite(relation));
        if (sprite != null) {
            int spriteSize = warSpriteSize(iconScale);
            graphics.pose().push();
            graphics.pose().multiply(RotationAxis.POSITIVE_Z.rotationDegrees(180.0F));
            graphics.blit(
                    sprite,
                    -spriteSize / 2,
                    -spriteSize / 2,
                    spriteSize,
                    spriteSize,
                    RenderPipelines.GUI_TEXTURED
            );
            graphics.pose().pop();
        } else {
            drawWarFallback((x1, y1, x2, y2, argb) ->
                    graphics.fill(x1, y1, x2, y2, argb), marker, iconScale);
        }
    }

    static Identifier warSwordSprite(WarRelation relation) {
        return relation == WarRelation.FRIENDLY || relation == WarRelation.ALLIED
                ? WAR_DIAMOND_SWORD_SPRITE_ID
                : WAR_IRON_SWORD_SPRITE_ID;
    }

    private static void drawWarBadge(
            RectDrawer drawer,
            int color,
            double iconScale
    ) {
        int badgeSize = warBadgeSize(iconScale);
        int spriteSize = warSpriteSize(iconScale);
        drawer.fill(
                -badgeSize / 2,
                -badgeSize / 2,
                badgeSize / 2,
                badgeSize / 2,
                BLACK
        );
        drawer.fill(
                -spriteSize / 2,
                -spriteSize / 2,
                spriteSize / 2,
                spriteSize / 2,
                color
        );
    }

    private static void drawWarFallback(
            RectDrawer drawer,
            WarMarkerType marker,
            double iconScale
    ) {
        RectDrawer scaledDrawer = (left, top, right, bottom, argb) -> drawer.fill(
                scaleWarCoordinate(left, iconScale),
                scaleWarCoordinate(top, iconScale),
                scaleWarCoordinate(right, iconScale),
                scaleWarCoordinate(bottom, iconScale),
                argb
        );
        switch (marker) {
            case SWORD -> drawSword(scaledDrawer, false);
            case SHIELD -> drawShield(scaledDrawer);
            case CROSSED_SWORDS -> {
                drawSword(scaledDrawer, false);
                drawSword(scaledDrawer, true);
            }
            case FLAG -> drawFlag(scaledDrawer);
            case WARNING -> drawWarning(scaledDrawer);
        }
    }

    static int scaleWarCoordinate(int coordinate) {
        return scaleWarCoordinate(coordinate, ServerSettings.DEFAULT_WAR_ICON_SCALE);
    }

    static int scaleWarCoordinate(int coordinate, double iconScale) {
        return (int) Math.round(
                coordinate * WAR_ICON_SCALE * sanitizeWarIconScale(iconScale)
        );
    }

    static double sanitizeWarIconScale(double iconScale) {
        if (!Double.isFinite(iconScale)) {
            return ServerSettings.DEFAULT_WAR_ICON_SCALE;
        }
        return Math.max(
                ServerSettings.MINIMUM_WAR_ICON_SCALE,
                Math.min(ServerSettings.MAXIMUM_WAR_ICON_SCALE, iconScale)
        );
    }

    static int warCollisionRadius(double iconScale) {
        return warBadgeSize(iconScale) / 2 + 1;
    }

    static int warBadgeSize(double iconScale) {
        return scaledEvenSize(WAR_BADGE_SIZE, iconScale);
    }

    static int warSpriteSize(double iconScale) {
        return scaledEvenSize(WAR_SPRITE_SIZE, iconScale);
    }

    private static int scaledEvenSize(int baseSize, double iconScale) {
        int size = Math.max(2, (int) Math.round(
                baseSize * sanitizeWarIconScale(iconScale)
        ));
        return (size & 1) == 0 ? size : size + 1;
    }

    private static void drawSword(RectDrawer drawer, boolean mirrored) {
        for (int index = 0; index < 7; index++) {
            int x = mirrored ? 3 - index : -3 + index;
            int y = -3 + index;
            drawer.fill(x, y, x + 1, y + 1, WHITE);
        }
        if (mirrored) {
            drawer.fill(-2, 2, 2, 3, WHITE);
            drawer.fill(2, 3, 3, 5, WHITE);
        } else {
            drawer.fill(-2, 2, 2, 3, WHITE);
            drawer.fill(-3, 3, -2, 5, WHITE);
        }
    }

    private static void drawShield(RectDrawer drawer) {
        drawer.fill(-3, -3, 4, -2, WHITE);
        drawer.fill(-4, -2, -3, 2, WHITE);
        drawer.fill(3, -2, 4, 2, WHITE);
        drawer.fill(-3, 2, -2, 3, WHITE);
        drawer.fill(2, 2, 3, 3, WHITE);
        drawer.fill(-2, 3, 2, 4, WHITE);
        drawer.fill(-2, -1, 2, 2, 0x90FFFFFF);
    }

    private static void drawFlag(RectDrawer drawer) {
        drawer.fill(-3, -4, -2, 5, WHITE);
        drawer.fill(-2, -4, 3, -3, WHITE);
        drawer.fill(-2, -3, 2, 0, WHITE);
        drawer.fill(-4, 4, 1, 5, WHITE);
    }

    private static void drawWarning(RectDrawer drawer) {
        drawer.fill(0, -4, 1, 1, WHITE);
        drawer.fill(-1, -2, 0, 1, WHITE);
        drawer.fill(1, -2, 2, 1, WHITE);
        drawer.fill(0, 3, 1, 4, WHITE);
    }

    private static Sprite resolveNodeSprite(NodeDefinition node) {
        String icon = node.icon();
        if (icon == null || icon.isBlank()) {
            return null;
        }
        Identifier spriteId = NODE_SPRITE_IDS.computeIfAbsent(
                icon,
                XaeroMarkerDrawing::spriteIdentifier
        );
        return resolveSprite(spriteId);
    }

    private static Sprite resolveSprite(Identifier spriteId) {
        AbstractTexture texture = MinecraftClient.getInstance()
                .getTextureManager()
                .getTexture(SpriteAtlasTexture.ITEMS_ATLAS_TEXTURE);
        if (!(texture instanceof SpriteAtlasTexture atlas)) {
            return null;
        }
        Sprite sprite = atlas.getSprite(spriteId);
        return sprite == atlas.getMissingSprite() ? null : sprite;
    }

    private static Identifier spriteIdentifier(String icon) {
        String normalized = icon.toLowerCase(Locale.ROOT)
                .replace(' ', '_')
                .replace('-', '_');
        if (normalized.startsWith("mob_")) {
            normalized = normalized.substring(4) + "_spawn_egg";
        } else if (normalized.equals("broken_elytra")) {
            normalized = "elytra";
        } else if (normalized.equals("ruby")) {
            normalized = "redstone";
        }
        return Identifier.ofVanilla("item/" + normalized);
    }

    private static String firstLetter(NodeDefinition node) {
        String name = node.name() == null || node.name().isBlank()
                ? node.id()
                : node.name();
        return name == null || name.isBlank()
                ? "?"
                : name.substring(0, 1).toUpperCase(Locale.ROOT);
    }

    @FunctionalInterface
    private interface RectDrawer {
        void fill(int left, int top, int right, int bottom, int color);
    }
}
