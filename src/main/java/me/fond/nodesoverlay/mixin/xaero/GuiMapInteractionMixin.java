package me.fond.nodesoverlay.mixin.xaero;

import me.fond.nodesoverlay.NodesOverlayRuntime;
import me.fond.nodesoverlay.gui.TerritoryInfoScreen;
import me.fond.nodesoverlay.integration.xaero.NodesOverlayMapNavigation;
import me.fond.nodesoverlay.integration.xaero.XaeroMapMarker;
import me.fond.nodesoverlay.model.TerritoryView;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xaero.map.element.HoveredMapElementHolder;
import xaero.map.gui.GuiMap;

@Mixin(value = GuiMap.class, remap = false)
public abstract class GuiMapInteractionMixin implements NodesOverlayMapNavigation {

    @Shadow
    private RegistryKey<World> mouseBlockDim;

    @Shadow
    private HoveredMapElementHolder<?, ?> viewedOnMousePress;

    @Shadow
    private int mouseBlockPosX;

    @Shadow
    private int mouseBlockPosZ;

    @Shadow
    private int[] cameraDestination;

    /**
     * Right-click keeps Xaero's normal left-drag navigation intact. Only a
     * right-click that resolves to a mapped territory is consumed.
     */
    @Inject(method = "mapClicked(III)V", at = @At("HEAD"), cancellable = true, remap = false)
    private void nodesoverlay$openTerritoryPanel(
            int button,
            int mouseX,
            int mouseY,
            CallbackInfo callback
    ) {
        if (!NodesOverlayRuntime.isActive()
                || button != 1
                || mouseBlockDim == null
                || !World.OVERWORLD.equals(mouseBlockDim)) {
            return;
        }
        if (viewedOnMousePress != null
                && !(viewedOnMousePress.getElement() instanceof XaeroMapMarker)) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        TerritoryView territory = NodesOverlayRuntime.snapshot()
                .territoryAtBlock(mouseBlockPosX, mouseBlockPosZ);
        if (territory == null) {
            return;
        }
        client.setScreen(new TerritoryInfoScreen((Screen) (Object) this, territory.territory().id()));
        callback.cancel();
    }

    @Override
    public void nodesoverlay$focus(int blockX, int blockZ) {
        cameraDestination = new int[]{blockX, blockZ};
    }
}
