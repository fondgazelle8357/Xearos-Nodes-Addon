package me.fond.nodesoverlay.mixin.xaero;

import me.fond.nodesoverlay.integration.xaero.MinimapOverlayHighlighter;
import me.fond.nodesoverlay.integration.xaero.WorldMapHighlighterBridgeAccess;
import me.fond.nodesoverlay.integration.xaero.WorldMapTerritoryHighlighter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xaero.common.minimap.highlight.HighlighterRegistry;

@Mixin(value = HighlighterRegistry.class, remap = false)
public abstract class MinimapHighlighterRegistryMixin {

    @Unique
    private boolean nodesoverlay$registered;

    @Inject(method = "end", at = @At("HEAD"))
    private void nodesoverlay$registerHighlighters(CallbackInfo callbackInfo) {
        if (nodesoverlay$registered) {
            return;
        }

        HighlighterRegistry registry = (HighlighterRegistry) (Object) this;
        boolean alreadyBridged = registry.getHighlighters().stream()
                .filter(WorldMapHighlighterBridgeAccess.class::isInstance)
                .map(WorldMapHighlighterBridgeAccess.class::cast)
                .anyMatch(bridge ->
                        bridge.nodesoverlay$getWorldMapHighlighter()
                                instanceof WorldMapTerritoryHighlighter);
        if (!alreadyBridged) {
            registry.register(new MinimapOverlayHighlighter());
        }
        nodesoverlay$registered = true;
    }
}
