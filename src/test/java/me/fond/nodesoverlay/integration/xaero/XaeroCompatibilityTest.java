package me.fond.nodesoverlay.integration.xaero;

import org.junit.jupiter.api.Test;
import xaero.common.HudMod;
import xaero.common.mods.WorldMapHighlighter;
import xaero.hud.minimap.element.render.MinimapElementGraphics;
import xaero.hud.minimap.element.render.over.MinimapElementOverMapRendererHandler;
import xaero.map.element.MapElementGraphics;
import xaero.map.element.MapElementRenderHandler;
import xaero.map.element.render.ElementRenderLocation;
import xaero.map.gui.GuiMap;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Fails the build when a pinned Xaero update no longer exposes one of the
 * private members used by the version-specific Mixins.
 */
class XaeroCompatibilityTest {

    @Test
    void worldMapHooksMatchPinnedXaeroApi() throws ReflectiveOperationException {
        assertNotNull(xaero.map.highlight.HighlighterRegistry.class
                .getDeclaredMethod("end"));
        assertNotNull(MapElementRenderHandler.class.getDeclaredConstructor(
                List.class,
                ElementRenderLocation.class,
                MapElementGraphics.class
        ));
        assertNotNull(GuiMap.class.getDeclaredMethod(
                "mapClicked",
                int.class,
                int.class,
                int.class
        ));
        assertNotNull(GuiMap.class.getDeclaredField("mouseBlockPosX"));
        assertNotNull(GuiMap.class.getDeclaredField("mouseBlockPosZ"));
        assertNotNull(GuiMap.class.getDeclaredField("mouseBlockDim"));
        assertNotNull(GuiMap.class.getDeclaredField("viewedOnMousePress"));
        assertNotNull(GuiMap.class.getDeclaredField("cameraDestination"));
        assertNotNull(WorldMapHighlighter.class.getDeclaredField("highlighter"));
        assertNotNull(WorldMapHighlighter.class.getDeclaredMethod(
                "regionHasHighlights",
                net.minecraft.registry.RegistryKey.class,
                int.class,
                int.class
        ));
        assertNotNull(WorldMapHighlighter.class.getDeclaredMethod(
                "chunkIsHighlit",
                net.minecraft.registry.RegistryKey.class,
                int.class,
                int.class
        ));
        assertNotNull(WorldMapHighlighter.class.getDeclaredMethod(
                "getChunkHighlitColor",
                net.minecraft.registry.RegistryKey.class,
                int.class,
                int.class
        ));
    }

    @Test
    void minimapHooksMatchPinnedXaeroApi() throws ReflectiveOperationException {
        assertNotNull(xaero.common.minimap.highlight.HighlighterRegistry.class
                .getDeclaredMethod("end"));
        assertNotNull(MinimapElementOverMapRendererHandler.class
                .getDeclaredConstructor(
                        HudMod.class,
                        List.class,
                        MinimapElementGraphics.class,
                        double[].class
                ));
        assertNotNull(MinimapElementOverMapRendererHandler.class
                .getDeclaredField("ps"));
        assertNotNull(MinimapElementOverMapRendererHandler.class
                .getDeclaredField("pc"));
        assertNotNull(MinimapElementOverMapRendererHandler.class
                .getDeclaredField("zoom"));
        assertNotNull(MinimapElementOverMapRendererHandler.class
                .getDeclaredField("halfViewW"));
        assertNotNull(MinimapElementOverMapRendererHandler.class
                .getDeclaredField("halfViewH"));
        assertNotNull(MinimapElementOverMapRendererHandler.class
                .getDeclaredField("circle"));
    }
}
