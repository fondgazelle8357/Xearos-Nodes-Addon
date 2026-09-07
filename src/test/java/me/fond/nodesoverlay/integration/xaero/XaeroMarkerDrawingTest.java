package me.fond.nodesoverlay.integration.xaero;

import me.fond.nodesoverlay.config.ServerSettings;
import me.fond.nodesoverlay.model.RgbColor;
import me.fond.nodesoverlay.war.WarRelation;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class XaeroMarkerDrawingTest {

    @Test
    void keepsCurrentWarIconGeometryAtDefaultScale() {
        assertEquals(2, XaeroMarkerDrawing.WAR_ICON_SCALE);
        assertEquals(-12, XaeroMarkerDrawing.scaleWarCoordinate(-6));
        assertEquals(12, XaeroMarkerDrawing.scaleWarCoordinate(6));
        assertEquals(24, XaeroMarkerDrawing.WAR_BADGE_SIZE);
        assertEquals(16, XaeroMarkerDrawing.WAR_SPRITE_SIZE);
        assertEquals(13, XaeroMarkerDrawing.WAR_COLLISION_RADIUS);
        assertEquals(1.0D, XaeroMarkerDrawing.sanitizeWarIconScale(1.0D));
        assertEquals(13, XaeroMarkerDrawing.warCollisionRadius(1.0D));
        assertEquals(24, XaeroMarkerDrawing.warBadgeSize(1.0D));
        assertEquals(16, XaeroMarkerDrawing.warSpriteSize(1.0D));
    }

    @Test
    void clampsWarIconScaleAndScalesCollisionBounds() {
        assertEquals(0.5D, XaeroMarkerDrawing.sanitizeWarIconScale(0.1D));
        assertEquals(2.0D, XaeroMarkerDrawing.sanitizeWarIconScale(4.0D));
        assertEquals(1.0D, XaeroMarkerDrawing.sanitizeWarIconScale(Double.NaN));
        assertEquals(7, XaeroMarkerDrawing.warCollisionRadius(0.5D));
        assertEquals(19, XaeroMarkerDrawing.warCollisionRadius(1.5D));
        assertEquals(25, XaeroMarkerDrawing.warCollisionRadius(2.0D));
        assertEquals(12, XaeroMarkerDrawing.warBadgeSize(0.5D));
        assertEquals(36, XaeroMarkerDrawing.warBadgeSize(1.5D));
        assertEquals(48, XaeroMarkerDrawing.warBadgeSize(2.0D));
        assertEquals(8, XaeroMarkerDrawing.warSpriteSize(0.5D));
        assertEquals(24, XaeroMarkerDrawing.warSpriteSize(1.5D));
        assertEquals(32, XaeroMarkerDrawing.warSpriteSize(2.0D));
        assertEquals(-6, XaeroMarkerDrawing.scaleWarCoordinate(-6, 0.5D));
        assertEquals(18, XaeroMarkerDrawing.scaleWarCoordinate(6, 1.5D));
        assertEquals(24, XaeroMarkerDrawing.scaleWarCoordinate(6, 2.0D));
    }

    @Test
    void usesDiamondSwordsForFriendlyAndAlliedAttacks() {
        Identifier diamondSword = Identifier.ofVanilla("item/diamond_sword");
        Identifier ironSword = Identifier.ofVanilla("item/iron_sword");

        assertEquals(
                diamondSword,
                XaeroMarkerDrawing.warSwordSprite(WarRelation.FRIENDLY)
        );
        assertEquals(
                diamondSword,
                XaeroMarkerDrawing.warSwordSprite(WarRelation.ALLIED)
        );
        assertEquals(
                ironSword,
                XaeroMarkerDrawing.warSwordSprite(WarRelation.HOSTILE)
        );
        assertEquals(
                ironSword,
                XaeroMarkerDrawing.warSwordSprite(WarRelation.UNKNOWN)
        );
    }

    @Test
    void colorsOnlyZoomedInPortsByOwnerRelationship() {
        ServerSettings settings = new ServerSettings();
        settings.friendlyMarkerColor = new RgbColor(10, 200, 20);
        settings.alliedMarkerColor = new RgbColor(20, 80, 220);
        settings.hostileMarkerColor = new RgbColor(220, 30, 20);
        XaeroMarkerRenderContext.MarkerStyle style =
                XaeroMarkerRenderContext.MarkerStyle.from(settings, true);
        RgbColor defaultPort = new RgbColor(255, 195, 77);

        assertEquals(
                settings.friendlyMarkerColor,
                XaeroMarkerRenderContext.portColor(WarRelation.FRIENDLY, true, style)
        );
        assertEquals(
                settings.alliedMarkerColor,
                XaeroMarkerRenderContext.portColor(WarRelation.ALLIED, true, style)
        );
        assertEquals(
                settings.hostileMarkerColor,
                XaeroMarkerRenderContext.portColor(WarRelation.HOSTILE, true, style)
        );
        assertEquals(
                defaultPort,
                XaeroMarkerRenderContext.portColor(WarRelation.UNKNOWN, true, style)
        );
        assertEquals(
                defaultPort,
                XaeroMarkerRenderContext.portColor(WarRelation.HOSTILE, false, style)
        );
    }
}
