package me.fond.nodesoverlay.integration.xaero;

import me.fond.nodesoverlay.model.TerritoryView;
import me.fond.nodesoverlay.model.ChunkCoordinate;
import net.minecraft.registry.RegistryKey;
import net.minecraft.text.Text;
import net.minecraft.world.World;
import xaero.hud.minimap.info.render.compile.InfoDisplayCompiler;
import xaero.map.highlight.AbstractHighlighter;

import java.util.List;

public final class WorldMapTerritoryHighlighter extends AbstractHighlighter
        implements NodesOverlayMinimapHighlighter {

    private final ChunkColorCache colorCache = new ChunkColorCache();

    public WorldMapTerritoryHighlighter() {
        super(true);
    }

    @Override
    public int calculateRegionHash(RegistryKey<World> dimension, int regionX, int regionZ) {
        XaeroOverlayRuntime.State state = XaeroOverlayRuntime.state();
        if (!XaeroHighlightSupport.shouldRender(dimension, state.style().worldMapEnabled())
                || !state.hasTerritoriesInRegion(regionX, regionZ)) {
            return 0;
        }
        return state.regionHash(regionX, regionZ);
    }

    @Override
    public boolean regionHasHighlights(RegistryKey<World> dimension, int regionX, int regionZ) {
        XaeroOverlayRuntime.State state = XaeroOverlayRuntime.state();
        return regionHasHighlights(
                dimension,
                regionX,
                regionZ,
                state,
                state.style().worldMapEnabled()
        );
    }

    @Override
    public boolean chunkIsHighlit(RegistryKey<World> dimension, int chunkX, int chunkZ) {
        XaeroOverlayRuntime.State state = XaeroOverlayRuntime.state();
        return chunkIsHighlit(
                dimension,
                chunkX,
                chunkZ,
                state,
                state.style().worldMapEnabled()
        );
    }

    @Override
    public int[] getChunkHighlitColor(RegistryKey<World> dimension, int chunkX, int chunkZ) {
        XaeroOverlayRuntime.State state = XaeroOverlayRuntime.state();
        return chunkColors(
                dimension,
                chunkX,
                chunkZ,
                state,
                state.style().worldMapEnabled()
        );
    }

    @Override
    public Text getBlockHighlightSubtleTooltip(
            RegistryKey<World> dimension,
            int blockX,
            int blockZ
    ) {
        return getTooltip(dimension, Math.floorDiv(blockX, 16), Math.floorDiv(blockZ, 16));
    }

    @Override
    public Text getBlockHighlightBluntTooltip(
            RegistryKey<World> dimension,
            int blockX,
            int blockZ
    ) {
        return getTooltip(dimension, Math.floorDiv(blockX, 16), Math.floorDiv(blockZ, 16));
    }

    @Override
    public void addMinimapBlockHighlightTooltips(
            List<Text> tooltips,
            RegistryKey<World> dimension,
            int blockX,
            int blockZ,
            int width
    ) {
        XaeroOverlayRuntime.State state = XaeroOverlayRuntime.state();
        if (!XaeroHighlightSupport.shouldRender(dimension, state.style().worldMapEnabled())) {
            return;
        }
        Text tooltip = XaeroHighlightSupport.tooltip(
                state.territoryAtBlock(blockX, blockZ)
        );
        if (tooltip != null) {
            tooltips.add(tooltip);
        }
    }

    private Text getTooltip(
            RegistryKey<World> dimension,
            int chunkX,
            int chunkZ
    ) {
        XaeroOverlayRuntime.State state = XaeroOverlayRuntime.state();
        if (!XaeroHighlightSupport.shouldRender(dimension, state.style().worldMapEnabled())) {
            return null;
        }
        TerritoryView territory = state.territoryAtChunk(chunkX, chunkZ);
        return XaeroHighlightSupport.tooltip(territory);
    }

    @Override
    public boolean nodesoverlay$minimapRegionHasHighlights(
            RegistryKey<World> dimension,
            int regionX,
            int regionZ
    ) {
        XaeroOverlayRuntime.State state = XaeroOverlayRuntime.state();
        return regionHasHighlights(
                dimension,
                regionX,
                regionZ,
                state,
                state.style().minimapEnabled()
        );
    }

    @Override
    public boolean nodesoverlay$minimapChunkIsHighlit(
            RegistryKey<World> dimension,
            int chunkX,
            int chunkZ
    ) {
        XaeroOverlayRuntime.State state = XaeroOverlayRuntime.state();
        return chunkIsHighlit(
                dimension,
                chunkX,
                chunkZ,
                state,
                state.style().minimapEnabled()
        );
    }

    @Override
    public int[] nodesoverlay$getMinimapChunkHighlitColor(
            RegistryKey<World> dimension,
            int chunkX,
            int chunkZ
    ) {
        XaeroOverlayRuntime.State state = XaeroOverlayRuntime.state();
        return chunkColors(
                dimension,
                chunkX,
                chunkZ,
                state,
                state.style().minimapEnabled()
        );
    }

    @Override
    public void nodesoverlay$addMinimapBlockHighlightTooltips(
            InfoDisplayCompiler compiler,
            RegistryKey<World> dimension,
            int blockX,
            int blockZ,
            int width
    ) {
        XaeroOverlayRuntime.State state = XaeroOverlayRuntime.state();
        if (!XaeroHighlightSupport.shouldRender(dimension, state.style().minimapEnabled())) {
            return;
        }
        Text tooltip = XaeroHighlightSupport.tooltip(
                state.territoryAtBlock(blockX, blockZ)
        );
        if (tooltip != null) {
            compiler.addLine(tooltip);
        }
    }

    private boolean regionHasHighlights(
            RegistryKey<World> dimension,
            int regionX,
            int regionZ,
            XaeroOverlayRuntime.State state,
            boolean enabled
    ) {
        return XaeroHighlightSupport.shouldRender(dimension, enabled)
                && state.hasTerritoriesInRegion(regionX, regionZ);
    }

    private boolean chunkIsHighlit(
            RegistryKey<World> dimension,
            int chunkX,
            int chunkZ,
            XaeroOverlayRuntime.State state,
            boolean enabled
    ) {
        return XaeroHighlightSupport.shouldRender(dimension, enabled)
                && state.territoryAtChunk(chunkX, chunkZ) != null;
    }

    private int[] chunkColors(
            RegistryKey<World> dimension,
            int chunkX,
            int chunkZ,
            XaeroOverlayRuntime.State state,
            boolean enabled
    ) {
        if (!XaeroHighlightSupport.shouldRender(dimension, enabled)) {
            return null;
        }
        long chunkKey = ChunkCoordinate.pack(chunkX, chunkZ);
        int[] cached = colorCache.get(state.regionRevision(chunkX >> 5, chunkZ >> 5), chunkKey);
        if (cached != null) {
            return cached;
        }
        int[] computed = XaeroHighlightSupport.territoryOverlayColors(
                state,
                chunkX,
                chunkZ,
                new int[256]
        );
        return computed == null
                ? null
                : colorCache.putIfAbsent(state.regionRevision(chunkX >> 5, chunkZ >> 5), chunkKey, computed);
    }
}
