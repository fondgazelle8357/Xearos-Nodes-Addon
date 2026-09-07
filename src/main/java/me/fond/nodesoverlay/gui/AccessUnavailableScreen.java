package me.fond.nodesoverlay.gui;

import me.fond.nodesoverlay.NodesOverlayRuntime;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;

/**
 * Prevents configuration access while the local player is being checked or
 * has matched an access-blacklist entry.
 */
public final class AccessUnavailableScreen extends Screen {

    private final Screen parent;

    public AccessUnavailableScreen(Screen parent) {
        super(Text.literal("Nodes Overlay"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, button -> close())
                .dimensions(width / 2 - 75, height / 2 + 32, 150, 20)
                .build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xE0101317);
        context.drawCenteredTextWithShadow(
                textRenderer,
                title,
                width / 2,
                height / 2 - 35,
                0xFFFFFFFF
        );
        context.drawCenteredTextWithShadow(
                textRenderer,
                Text.literal(NodesOverlayRuntime.accessMessage()),
                width / 2,
                height / 2 - 8,
                NodesOverlayRuntime.accessDenied() ? 0xFFFF6B63 : 0xFFFFD36A
        );
        context.drawCenteredTextWithShadow(
                textRenderer,
                Text.literal("Map overlays, waypoints, commands, and settings are unavailable."),
                width / 2,
                height / 2 + 5,
                0xFF9AA6AF
        );
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        if (client != null) {
            client.setScreen(parent);
        }
    }
}
