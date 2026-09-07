package me.fond.nodesoverlay.integration.xaero;

import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;
import xaero.hud.minimap.info.render.compile.InfoDisplayCompiler;

/**
 * Lets Xaero's World Map-to-Minimap adapter invoke a NodesOverlay highlighter with
 * the independent minimap setting instead of the world-map setting.
 */
public interface NodesOverlayMinimapHighlighter {

    boolean nodesoverlay$minimapRegionHasHighlights(
            RegistryKey<World> dimension,
            int regionX,
            int regionZ
    );

    boolean nodesoverlay$minimapChunkIsHighlit(
            RegistryKey<World> dimension,
            int chunkX,
            int chunkZ
    );

    int[] nodesoverlay$getMinimapChunkHighlitColor(
            RegistryKey<World> dimension,
            int chunkX,
            int chunkZ
    );

    void nodesoverlay$addMinimapBlockHighlightTooltips(
            InfoDisplayCompiler compiler,
            RegistryKey<World> dimension,
            int blockX,
            int blockZ,
            int width
    );
}
