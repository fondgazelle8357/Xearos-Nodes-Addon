package me.fond.nodesoverlay.integration.xaero;

import xaero.map.highlight.AbstractHighlighter;

/**
 * Implemented on Xaero's minimap adapter by a Mixin so registration can avoid
 * installing a second copy of an already-bridged NodesOverlay highlighter.
 */
public interface WorldMapHighlighterBridgeAccess {

    AbstractHighlighter nodesoverlay$getWorldMapHighlighter();
}
