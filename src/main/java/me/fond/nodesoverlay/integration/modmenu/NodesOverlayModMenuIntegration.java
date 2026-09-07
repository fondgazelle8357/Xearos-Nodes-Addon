package me.fond.nodesoverlay.integration.modmenu;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import me.fond.nodesoverlay.NodesOverlayRuntime;
import me.fond.nodesoverlay.gui.NodesOverlaySettingsScreen;
import me.fond.nodesoverlay.gui.NoActiveProfileScreen;

public final class NodesOverlayModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> {
            if (NodesOverlayRuntime.serverAddress() == null) {
                return new NoActiveProfileScreen(parent);
            }
            return new NodesOverlaySettingsScreen(parent);
        };
    }
}
