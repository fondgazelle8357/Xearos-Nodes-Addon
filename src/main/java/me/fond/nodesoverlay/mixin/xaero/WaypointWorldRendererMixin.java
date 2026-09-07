package me.fond.nodesoverlay.mixin.xaero;

import me.fond.nodesoverlay.integration.xaero.WaypointScalePolicy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Desc;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xaero.common.graphics.renderer.multitexture.MultiTextureRenderTypeRendererProvider;
import xaero.common.minimap.waypoints.Waypoint;
import xaero.hud.minimap.element.render.MinimapElementGraphics;
import xaero.hud.minimap.element.render.MinimapElementRenderInfo;
import xaero.hud.minimap.waypoint.render.world.WaypointWorldRenderer;

/**
 * Applies distance scaling while Xaero renders any world-space waypoint. The
 * original Xaero scales are restored after every element so nearby waypoints
 * retain the user's configured size.
 */
@Mixin(value = WaypointWorldRenderer.class, remap = false)
public abstract class WaypointWorldRendererMixin {

    @Shadow
    private float iconScale;

    @Shadow
    private int distanceTextScale;

    @Shadow
    private int nameScale;

    @Unique
    private boolean nodesoverlay$distantScaleApplied;

    @Unique
    private float nodesoverlay$originalIconScale;

    @Unique
    private int nodesoverlay$originalDistanceTextScale;

    @Unique
    private int nodesoverlay$originalNameScale;

    @Inject(
            target = @Desc(
                    value = "renderElement",
                    ret = boolean.class,
                    args = {
                            Waypoint.class,
                            boolean.class,
                            boolean.class,
                            double.class,
                            float.class,
                            double.class,
                            double.class,
                            MinimapElementRenderInfo.class,
                            MinimapElementGraphics.class,
                            net.minecraft.client.render.VertexConsumerProvider.Immediate.class
                    }
            ),
            at = @At("HEAD"),
            remap = false
    )
    private void nodesoverlay$applyDistantScale(
            Waypoint waypoint,
            boolean highlighted,
            boolean outOfBounds,
            double optionalDepth,
            float optionalScale,
            double partialX,
            double partialY,
            MinimapElementRenderInfo renderInfo,
            MinimapElementGraphics graphics,
            net.minecraft.client.render.VertexConsumerProvider.Immediate buffers,
            CallbackInfoReturnable<Boolean> callback
    ) {
        nodesoverlay$distantScaleApplied = false;
        if (waypoint == null
                || renderInfo == null
                || renderInfo.renderEntityPos == null
                || !WaypointScalePolicy.isDistant(
                        waypoint,
                        renderInfo.renderEntityPos.x,
                        renderInfo.renderEntityPos.z
                )) {
            return;
        }

        nodesoverlay$originalIconScale = iconScale;
        nodesoverlay$originalDistanceTextScale = distanceTextScale;
        nodesoverlay$originalNameScale = nameScale;

        iconScale *= WaypointScalePolicy.DISTANT_SCALE;
        distanceTextScale = WaypointScalePolicy.distantScale(distanceTextScale);
        nameScale = WaypointScalePolicy.distantScale(nameScale);
        nodesoverlay$distantScaleApplied = true;
    }

    @Inject(
            target = @Desc(
                    value = "renderElement",
                    ret = boolean.class,
                    args = {
                            Waypoint.class,
                            boolean.class,
                            boolean.class,
                            double.class,
                            float.class,
                            double.class,
                            double.class,
                            MinimapElementRenderInfo.class,
                            MinimapElementGraphics.class,
                            net.minecraft.client.render.VertexConsumerProvider.Immediate.class
                    }
            ),
            at = @At("RETURN"),
            remap = false
    )
    private void nodesoverlay$restoreNormalScale(
            Waypoint waypoint,
            boolean highlighted,
            boolean outOfBounds,
            double optionalDepth,
            float optionalScale,
            double partialX,
            double partialY,
            MinimapElementRenderInfo renderInfo,
            MinimapElementGraphics graphics,
            net.minecraft.client.render.VertexConsumerProvider.Immediate buffers,
            CallbackInfoReturnable<Boolean> callback
    ) {
        if (!nodesoverlay$distantScaleApplied) {
            return;
        }

        iconScale = nodesoverlay$originalIconScale;
        distanceTextScale = nodesoverlay$originalDistanceTextScale;
        nameScale = nodesoverlay$originalNameScale;
        nodesoverlay$distantScaleApplied = false;
    }
}
