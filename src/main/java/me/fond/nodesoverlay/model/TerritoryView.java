package me.fond.nodesoverlay.model;

import java.util.List;

public record TerritoryView(
        TerritoryDefinition territory,
        TownRecord owner,
        List<TownRecord> ownerCandidates,
        NationRecord ownerNation,
        TownRecord occupier,
        NationRecord occupierNation,
        OwnershipState state,
        RgbColor ownerColor,
        RgbColor occupierColor,
        boolean townCore
) {
    public TerritoryView {
        ownerCandidates = List.copyOf(ownerCandidates);
    }

    public String ownerTownName() {
        return owner == null ? "Unowned" : owner.name();
    }

    public String ownerNationName() {
        return ownerNation == null ? "None" : ownerNation.name();
    }

    public String occupierTownName() {
        return occupier == null ? "None" : occupier.name();
    }

    public String occupierNationName() {
        return occupierNation == null ? "None" : occupierNation.name();
    }

    public boolean occupied() {
        return occupier != null && (owner == null || !occupier.name().equalsIgnoreCase(owner.name()));
    }

    public boolean ownershipAmbiguous() {
        return ownerCandidates.size() > 1;
    }
}
