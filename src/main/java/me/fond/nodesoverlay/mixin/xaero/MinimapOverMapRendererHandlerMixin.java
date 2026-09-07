package me.fond.nodesoverlay.mixin.xaero;

import me.fond.nodesoverlay.integration.xaero.NodesOverlayMinimapMarkerRenderer;
import me.fond.nodesoverlay.integration.xaero.MinimapOverMapRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xaero.common.HudMod;
import xaero.hud.minimap.element.render.MinimapElementGraphics;
import xaero.hud.minimap.element.render.MinimapElementRenderer;
import xaero.hud.minimap.element.render.over.MinimapElementOverMapRendererHandler;

import java.util.List;

/**
 * Registers the minimap marker layer and exposes the exact transform Xaero
 * computed for the current minimap frame.
 */
@Mixin(value = MinimapElementOverMapRendererHandler.class, remap = false)
public abstract class MinimapOverMapRendererHandlerMixin
        implements MinimapOverMapRenderState {

    @Inject(method = "<init>", at = @At("RETURN"))
    private void nodesoverlay$registerMarkerRenderer(
            HudMod modMain,
            List<MinimapElementRenderer<?, ?>> renderers,
            MinimapElementGraphics graphics,
            double[] partialTranslate,
            CallbackInfo callback
    ) {
        ((MinimapElementOverMapRendererHandler) (Object) this).add(
                new NodesOverlayMinimapMarkerRenderer()
        );
    }

    @Override
    @Accessor("ps")
    public abstract double nodesoverlay$getRotationSin();

    @Override
    @Accessor("pc")
    public abstract double nodesoverlay$getRotationCos();

    @Override
    @Accessor("zoom")
    public abstract double nodesoverlay$getPixelsPerBlock();

    @Override
    @Accessor("halfViewW")
    public abstract int nodesoverlay$getHalfViewWidth();

    @Override
    @Accessor("halfViewH")
    public abstract int nodesoverlay$getHalfViewHeight();

    @Override
    @Accessor("circle")
    public abstract boolean nodesoverlay$isCircular();
}
