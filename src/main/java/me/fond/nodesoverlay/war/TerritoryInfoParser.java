package me.fond.nodesoverlay.war;

import java.time.Instant;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Collects the opening ownership rows from the server's multi-line territory
 * information response. Minecraft delivers each row as a separate game
 * message, so the parser retains the territory header until the Occupier row.
 */
public final class TerritoryInfoParser {

    private static final Pattern FORMATTING_CODE =
            Pattern.compile("(?i)\\x{00C2}?\\x{00A7}[0-9A-FK-ORX]");
    private static final Pattern HEADER = Pattern.compile(
            "^\\s*Territory\\s*\\(\\s*id\\s*=\\s*(\\d+)\\s*\\)\\s*:\\s*$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern TOWN = Pattern.compile(
            "^\\s*-?\\s*Town\\s*:\\s*(.*?)\\s*$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern OCCUPIER = Pattern.compile(
            "^\\s*-?\\s*Occupier\\s*:\\s*(.*?)\\s*$",
            Pattern.CASE_INSENSITIVE
    );

    private Integer territoryId;
    private String ownerTown;

    public synchronized Optional<TerritoryInfoObservation> accept(String rawLine) {
        if (rawLine == null || rawLine.isBlank()) {
            return Optional.empty();
        }
        String line = FORMATTING_CODE.matcher(rawLine).replaceAll("").trim();
        Matcher header = HEADER.matcher(line);
        if (header.matches()) {
            territoryId = Integer.parseInt(header.group(1));
            ownerTown = null;
            return Optional.empty();
        }
        if (territoryId == null) {
            return Optional.empty();
        }

        Matcher town = TOWN.matcher(line);
        if (town.matches()) {
            ownerTown = normalize(town.group(1));
            return Optional.empty();
        }

        Matcher occupier = OCCUPIER.matcher(line);
        if (!occupier.matches()) {
            return Optional.empty();
        }
        TerritoryInfoObservation observation = new TerritoryInfoObservation(
                territoryId,
                ownerTown,
                normalize(occupier.group(1)),
                Instant.now()
        );
        reset();
        return Optional.of(observation);
    }

    public synchronized void reset() {
        territoryId = null;
        ownerTown = null;
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.trim();
        return cleaned.isEmpty()
                || cleaned.equalsIgnoreCase("none")
                || cleaned.equalsIgnoreCase("null")
                || cleaned.equalsIgnoreCase("n/a")
                || cleaned.equals("-")
                ? null
                : cleaned;
    }
}
