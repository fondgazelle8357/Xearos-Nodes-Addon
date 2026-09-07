package me.fond.nodesoverlay.mixin.xaero;

import me.fond.nodesoverlay.integration.xaero.NodesOverlayWorldMapMarkerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xaero.map.element.MapElementGraphics;
import xaero.map.element.MapElementRenderHandler;
import xaero.map.element.render.ElementRenderLocation;
import xaero.map.element.render.ElementRenderer;

import java.util.List;

/**
 * Registers the version-pinned Nodes Overlay element layer when Xaero constructs
 * its world-map handler.
 */
@Mixin(value = MapElementRenderHandler.class, remap = false)
public abstract class WorldMapElementRenderHandlerMixin {

    @Inject(method = "<init>", at = @At("RETURN"))
    private void nodesoverlay$registerMarkerRenderer(
            List<ElementRenderer<?, ?, ?>> renderers,
            ElementRenderLocation location,
            MapElementGraphics graphics,
            CallbackInfo callback
    ) {
        if (location == ElementRenderLocation.WORLD_MAP) {
            ((MapElementRenderHandler) (Object) this).add(
                    new NodesOverlayWorldMapMarkerRenderer()
            );
        }
    }
}
