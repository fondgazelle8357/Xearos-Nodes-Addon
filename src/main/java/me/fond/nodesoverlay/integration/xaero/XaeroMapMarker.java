package me.fond.nodesoverlay.integration.xaero;

import me.fond.nodesoverlay.model.PortRecord;
import me.fond.nodesoverlay.model.RgbColor;
import me.fond.nodesoverlay.model.TerritoryView;
import me.fond.nodesoverlay.war.WarEvent;

/**
 * A lightweight element supplied to Xaero's element renderers. Territory
 * markers are anchored at their core block while war markers retain the exact
 * block/chunk position reported by chat.
 */
public record XaeroMapMarker(
        Kind kind,
        TerritoryView territory,
        WarEvent warEvent,
        PortRecord port,
        String overviewLabel,
        RgbColor overviewColor,
        int overviewWeight,
        double blockX,
        double blockZ
) {

    enum Kind {
        TERRITORY,
        WAR,
        PORT,
        OVERVIEW
    }

    static XaeroMapMarker territory(TerritoryView territory) {
        return new XaeroMapMarker(
                Kind.TERRITORY,
                territory,
                null,
                null,
                null,
                null,
                0,
                territory.territory().coreX(),
                territory.territory().coreZ()
        );
    }

    static XaeroMapMarker war(WarEvent event, TerritoryView territory) {
        return new XaeroMapMarker(
                Kind.WAR,
                territory,
                event,
                null,
                null,
                null,
                0,
                event.blockX(),
                event.blockZ()
        );
    }

    static XaeroMapMarker port(PortRecord port, boolean exactPosition) {
        return new XaeroMapMarker(
                Kind.PORT,
                null,
                null,
                port,
                null,
                null,
                0,
                exactPosition ? port.x() : port.chunkCenterX(),
                exactPosition ? port.z() : port.chunkCenterZ()
        );
    }

    static XaeroMapMarker overview(
            String label,
            RgbColor color,
            int weight,
            double blockX,
            double blockZ
    ) {
        return new XaeroMapMarker(
                Kind.OVERVIEW,
                null,
                null,
                null,
                label,
                color,
                weight,
                blockX,
                blockZ
        );
    }

    boolean isTerritory() {
        return kind == Kind.TERRITORY;
    }

    boolean isPort() {
        return kind == Kind.PORT;
    }

    boolean isOverview() {
        return kind == Kind.OVERVIEW;
    }
}
