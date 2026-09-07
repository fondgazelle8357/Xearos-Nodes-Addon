package me.fond.nodesoverlay.integration.xaero;

/**
 * Read-only view of Xaero Minimap's already-computed over-map transform. The
 * backing fields are exposed by a version-pinned mixin so core chunk geometry
 * uses exactly the same rotation, zoom and clipping dimensions as Xaero.
 */
public interface MinimapOverMapRenderState {

    double nodesoverlay$getRotationSin();

    double nodesoverlay$getRotationCos();

    double nodesoverlay$getPixelsPerBlock();

    int nodesoverlay$getHalfViewWidth();

    int nodesoverlay$getHalfViewHeight();

    boolean nodesoverlay$isCircular();
}
