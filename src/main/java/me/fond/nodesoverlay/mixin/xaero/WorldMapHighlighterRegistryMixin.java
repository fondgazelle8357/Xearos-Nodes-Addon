package me.fond.nodesoverlay.mixin.xaero;

import me.fond.nodesoverlay.integration.xaero.WorldMapTerritoryHighlighter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xaero.map.highlight.HighlighterRegistry;

@Mixin(value = HighlighterRegistry.class, remap = false)
public abstract class WorldMapHighlighterRegistryMixin {

    @Unique
    private boolean nodesoverlay$registered;

    @Inject(method = "end", at = @At("HEAD"))
    private void nodesoverlay$registerHighlighters(CallbackInfo callbackInfo) {
        if (nodesoverlay$registered) {
            return;
        }

        HighlighterRegistry registry = (HighlighterRegistry) (Object) this;
        registry.register(new WorldMapTerritoryHighlighter());
        nodesoverlay$registered = true;
    }
}
