package me.fond.nodesoverlay.war;

public record TownWarOutcome(
        Kind kind,
        String town,
        String allianceLeader
) {

    public enum Kind {
        CONQUERED,
        PLUNDERED
    }
}
