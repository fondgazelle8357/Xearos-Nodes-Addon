package me.fond.nodesoverlay.port;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Collects the multi-line response produced by {@code /port info}. Minecraft
 * delivers each line as a separate game message, so parsing is intentionally
 * stateful and completes when the final Enemy access row arrives.
 */
public final class PortInfoParser {

    private static final Pattern FORMATTING_CODE =
            Pattern.compile("(?i)\\x{00C2}?\\x{00A7}[0-9A-FK-ORX]");
    private static final Pattern HEADER = Pattern.compile(
            "^\\s*Port\\s+(.+?)\\s*:\\s*$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern COORDINATES = Pattern.compile(
            "^\\s*-?\\s*\\(\\s*x\\s*,\\s*z\\s*\\)\\s*:\\s*"
                    + "\\(\\s*(-?\\d+)\\s*,\\s*(-?\\d+)\\s*\\)\\s*$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern OWNER = Pattern.compile(
            "^\\s*-?\\s*Owner\\s*:\\s*(.+?)\\s*$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern ACCESS = Pattern.compile(
            "^\\s*-?\\s*(Ally|Neutral|Enemy)\\s*:\\s*(true|false)"
                    + "(?:\\s*\\(\\s*Cost\\s*:\\s*(.+?)\\s*\\))?\\s*$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern GROUP = Pattern.compile("^\\s*-\\s*([^:]+?)\\s*$");

    private Builder current;
    private boolean readingGroups;

    public synchronized Optional<PortObservation> accept(String rawLine) {
        if (rawLine == null || rawLine.isBlank()) {
            return Optional.empty();
        }
        String line = FORMATTING_CODE.matcher(rawLine).replaceAll("").trim();
        Matcher header = HEADER.matcher(line);
        if (header.matches()) {
            PortObservation completed = finishIfUsable();
            current = new Builder(header.group(1).trim());
            readingGroups = false;
            return Optional.ofNullable(completed);
        }
        if (current == null) {
            return Optional.empty();
        }

        Matcher coordinates = COORDINATES.matcher(line);
        if (coordinates.matches()) {
            current.x = Integer.parseInt(coordinates.group(1));
            current.z = Integer.parseInt(coordinates.group(2));
            current.hasCoordinates = true;
            readingGroups = false;
            return Optional.empty();
        }
        if (line.matches("(?i)^\\s*-?\\s*Groups\\s*:\\s*$")) {
            readingGroups = true;
            return Optional.empty();
        }

        Matcher owner = OWNER.matcher(line);
        if (owner.matches()) {
            current.owner = cleanValue(owner.group(1));
            readingGroups = false;
            return Optional.empty();
        }

        Matcher access = ACCESS.matcher(line);
        if (access.matches()) {
            readingGroups = false;
            boolean allowed = Boolean.parseBoolean(access.group(2));
            String relation = access.group(1).toLowerCase();
            switch (relation) {
                case "ally" -> {
                    current.ally = allowed;
                    current.allyCost = cleanValue(access.group(3));
                }
                case "neutral" -> current.neutral = allowed;
                case "enemy" -> current.enemy = allowed;
                default -> {
                }
            }
            return relation.equals("enemy")
                    ? Optional.ofNullable(finishIfUsable())
                    : Optional.empty();
        }

        if (readingGroups) {
            Matcher group = GROUP.matcher(line);
            if (group.matches()) {
                current.groups.add(cleanValue(group.group(1)));
            }
        }
        return Optional.empty();
    }

    public synchronized void reset() {
        current = null;
        readingGroups = false;
    }

    private PortObservation finishIfUsable() {
        if (current == null || !current.hasCoordinates) {
            return null;
        }
        PortObservation result = new PortObservation(
                current.name,
                Set.copyOf(current.groups),
                current.x,
                current.z,
                current.owner,
                current.ally,
                current.neutral,
                current.enemy,
                current.allyCost,
                Instant.now()
        );
        current = null;
        readingGroups = false;
        return result;
    }

    private static String cleanValue(String value) {
        return value == null ? null : value.trim();
    }

    private static final class Builder {
        private final String name;
        private final Set<String> groups = new LinkedHashSet<>();
        private int x;
        private int z;
        private boolean hasCoordinates;
        private String owner;
        private Boolean ally;
        private Boolean neutral;
        private Boolean enemy;
        private String allyCost;

        private Builder(String name) {
            this.name = name;
        }
    }
}
