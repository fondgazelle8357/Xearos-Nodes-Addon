package me.fond.nodesoverlay.integration.xaero;

import me.fond.nodesoverlay.war.WarEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WarIconVisibilityTest {

    @Test
    void onlyAnActiveAttackUsesAMapIcon() {
        assertTrue(XaeroMarkerRenderContext.showsWarIcon(WarEvent.Kind.ATTACK));
        assertFalse(XaeroMarkerRenderContext.showsWarIcon(WarEvent.Kind.ATTACK_DEFENDED));
        assertFalse(XaeroMarkerRenderContext.showsWarIcon(WarEvent.Kind.CHUNK_CAPTURE));
        assertFalse(XaeroMarkerRenderContext.showsWarIcon(WarEvent.Kind.CHUNK_DEFENDED));
        assertFalse(XaeroMarkerRenderContext.showsWarIcon(WarEvent.Kind.CHUNK_LIBERATED));
        assertFalse(XaeroMarkerRenderContext.showsWarIcon(WarEvent.Kind.TERRITORY_CAPTURE));
        assertFalse(XaeroMarkerRenderContext.showsWarIcon(WarEvent.Kind.TERRITORY_LIBERATED));
    }
}
