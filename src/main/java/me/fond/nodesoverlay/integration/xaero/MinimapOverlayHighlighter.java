package me.fond.nodesoverlay.integration.xaero;

import net.minecraft.registry.RegistryKey;
import net.minecraft.text.Text;
import net.minecraft.world.World;
import xaero.common.minimap.highlight.AbstractHighlighter;
import xaero.hud.minimap.info.render.compile.InfoDisplayCompiler;

/**
 * Fallback used only when Xaero World Map has not bridged the shared NodesOverlay
 * highlighter into the minimap registry.
 */
public final class MinimapOverlayHighlighter extends AbstractHighlighter {

    private final WorldMapTerritoryHighlighter delegate = new WorldMapTerritoryHighlighter();

    public MinimapOverlayHighlighter() {
        super(true);
    }

    @Override
    public boolean regionHasHighlights(RegistryKey<World> dimension, int regionX, int regionZ) {
        return delegate.nodesoverlay$minimapRegionHasHighlights(
                dimension,
                regionX,
                regionZ
        );
    }

    @Override
    public boolean chunkIsHighlit(RegistryKey<World> dimension, int chunkX, int chunkZ) {
        return delegate.nodesoverlay$minimapChunkIsHighlit(dimension, chunkX, chunkZ);
    }

    @Override
    public int[] getChunkHighlitColor(RegistryKey<World> dimension, int chunkX, int chunkZ) {
        return delegate.nodesoverlay$getMinimapChunkHighlitColor(
                dimension,
                chunkX,
                chunkZ
        );
    }

    @Override
    public void addBlockHighlightTooltips(
            InfoDisplayCompiler compiler,
            RegistryKey<World> dimension,
            int blockX,
            int blockZ,
            int width
    ) {
        delegate.nodesoverlay$addMinimapBlockHighlightTooltips(
                compiler,
                dimension,
                blockX,
                blockZ,
                width
        );
    }
}
