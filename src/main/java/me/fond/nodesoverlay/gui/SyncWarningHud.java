package me.fond.nodesoverlay.gui;

import me.fond.nodesoverlay.NodesOverlayRuntime;
import me.fond.nodesoverlay.sync.SyncStatus;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

@SuppressWarnings("deprecation")
public final class SyncWarningHud implements HudRenderCallback {

    @Override
    public void onHudRender(DrawContext context, RenderTickCounter tickCounter) {
        if (!NodesOverlayRuntime.isActive()) {
            return;
        }
        SyncStatus status = NodesOverlayRuntime.syncStatus();
        if (status.state() != SyncStatus.State.WARNING) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        String message = "Nodes Overlay: " + status.message();
        int maximumWidth = Math.max(120, client.getWindow().getScaledWidth() / 2);
        String shown = client.textRenderer.trimToWidth(message, maximumWidth);
        int width = client.textRenderer.getWidth(shown);
        int x = client.getWindow().getScaledWidth() - width - 8;
        int y = 8;
        context.fill(x - 4, y - 3, x + width + 4, y + 11, 0xB0301B12);
        context.drawTextWithShadow(client.textRenderer, shown, x, y, 0xFFFFA657);
    }
}
