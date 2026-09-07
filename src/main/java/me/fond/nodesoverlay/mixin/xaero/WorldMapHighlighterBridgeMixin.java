package me.fond.nodesoverlay.mixin.xaero;

import me.fond.nodesoverlay.integration.xaero.NodesOverlayMinimapHighlighter;
import me.fond.nodesoverlay.integration.xaero.WorldMapHighlighterBridgeAccess;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xaero.hud.minimap.info.render.compile.InfoDisplayCompiler;
import xaero.map.highlight.AbstractHighlighter;

/**
 * Xaero wraps World Map highlighters for its minimap. Intercepting that wrapper
 * keeps the two overlay toggles independent and exposes the delegate so the
 * direct minimap registry hook can avoid registering a duplicate renderer.
 */
@Mixin(targets = "xaero.common.mods.WorldMapHighlighter", remap = false)
public abstract class WorldMapHighlighterBridgeMixin
        implements WorldMapHighlighterBridgeAccess {

    @Shadow
    @Final
    private AbstractHighlighter highlighter;

    @Override
    public AbstractHighlighter nodesoverlay$getWorldMapHighlighter() {
        return highlighter;
    }

    @Inject(method = "regionHasHighlights", at = @At("HEAD"), cancellable = true)
    private void nodesoverlay$useMinimapRegionSetting(
            RegistryKey<World> dimension,
            int regionX,
            int regionZ,
            CallbackInfoReturnable<Boolean> callback
    ) {
        if (highlighter instanceof NodesOverlayMinimapHighlighter nodesoverlay) {
            callback.setReturnValue(nodesoverlay.nodesoverlay$minimapRegionHasHighlights(
                    dimension,
                    regionX,
                    regionZ
            ));
        }
    }

    @Inject(method = "chunkIsHighlit", at = @At("HEAD"), cancellable = true)
    private void nodesoverlay$useMinimapChunkSetting(
            RegistryKey<World> dimension,
            int chunkX,
            int chunkZ,
            CallbackInfoReturnable<Boolean> callback
    ) {
        if (highlighter instanceof NodesOverlayMinimapHighlighter nodesoverlay) {
            callback.setReturnValue(nodesoverlay.nodesoverlay$minimapChunkIsHighlit(
                    dimension,
                    chunkX,
                    chunkZ
            ));
        }
    }

    @Inject(method = "getChunkHighlitColor", at = @At("HEAD"), cancellable = true)
    private void nodesoverlay$useMinimapChunkColors(
            RegistryKey<World> dimension,
            int chunkX,
            int chunkZ,
            CallbackInfoReturnable<int[]> callback
    ) {
        if (highlighter instanceof NodesOverlayMinimapHighlighter nodesoverlay) {
            callback.setReturnValue(nodesoverlay.nodesoverlay$getMinimapChunkHighlitColor(
                    dimension,
                    chunkX,
                    chunkZ
            ));
        }
    }

    @Inject(method = "addBlockHighlightTooltips", at = @At("HEAD"), cancellable = true)
    private void nodesoverlay$useMinimapTooltips(
            InfoDisplayCompiler compiler,
            RegistryKey<World> dimension,
            int blockX,
            int blockZ,
            int width,
            CallbackInfo callback
    ) {
        if (highlighter instanceof NodesOverlayMinimapHighlighter nodesoverlay) {
            nodesoverlay.nodesoverlay$addMinimapBlockHighlightTooltips(
                    compiler,
                    dimension,
                    blockX,
                    blockZ,
                    width
            );
            callback.cancel();
        }
    }
}
