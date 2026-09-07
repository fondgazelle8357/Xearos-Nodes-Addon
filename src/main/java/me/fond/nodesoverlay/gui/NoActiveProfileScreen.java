package me.fond.nodesoverlay.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;

/**
 * Mod Menu can be opened from the title screen, but Nodes Overlay settings are
 * deliberately per server. This screen prevents edits to an unsaved,
 * profile-less settings object.
 */
public final class NoActiveProfileScreen extends Screen {

    private final Screen parent;

    public NoActiveProfileScreen(Screen parent) {
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
                Text.literal("Join a server or single-player world first."),
                width / 2,
                height / 2 - 8,
                0xFFE7EDF1
        );
        context.drawCenteredTextWithShadow(
                textRenderer,
                Text.literal("Settings and cached data are saved per server profile."),
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
