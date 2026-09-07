package me.fond.nodesoverlay.war;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class WarMessageParser {

    private static final Pattern FORMATTING_CODE =
            Pattern.compile("(?i)\\x{00C2}?\\x{00A7}[0-9A-FK-ORX]");
    private static final String PREFIX = "^\\s*\\[\\s*(?:War|Warzone|Colonization)\\s*]\\s*[:\\-]?\\s*";
    private static final String PLAYER = "(?<player>[^\\s,;:]+)";
    private static final String TOWN = "(?<town>.+?)";
    private static final String END = "\\s*[,;:.!?]*\\s*$";

    private static final Pattern ATTACK = Pattern.compile(
            PREFIX + PLAYER + "\\s+is\\s+(?:attacking|liberating|capturing\\s+warzone)\\s+" + TOWN
                    + "\\s+at\\s*\\(\\s*(?<x>-?\\d+)\\s*[,;]\\s*(?<y>-?\\d+)\\s*[,;]\\s*(?<z>-?\\d+)\\s*\\)"
                    + END,
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern ATTACK_DEFENDED = Pattern.compile(
            PREFIX + "Attack\\s+at\\s*"
                    + "\\(\\s*(?<x>-?\\d+)\\s*[,;]\\s*(?<y>-?\\d+)\\s*[,;]\\s*(?<z>-?\\d+)\\s*\\)"
                    + "\\s+(?:defended|defeated)\\s+by\\s+" + PLAYER + END,
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern FLAG_BROKEN = Pattern.compile(
            PREFIX + "(?<player>.+?)\\s+broke\\s+the\\s+flag\\s+at\\s*"
                    + "\\(\\s*(?<x>-?\\d+)\\s*[,;]\\s*(?<y>-?\\d+)\\s*[,;]\\s*(?<z>-?\\d+)\\s*\\)"
                    + END,
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern CHUNK_CAPTURE = Pattern.compile(
            PREFIX + PLAYER + "\\s+captured\\s+(?:a\\s+)?chunk\\s*"
                    + "\\(\\s*(?<x>-?\\d+)\\s*[,;]\\s*(?<z>-?\\d+)\\s*\\)\\s+from\\s+" + TOWN + END,
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern CHUNK_DEFENDED = Pattern.compile(
            PREFIX + PLAYER + "\\s+defended\\s+(?:a\\s+)?chunk\\s*"
                    + "\\(\\s*(?<x>-?\\d+)\\s*[,;]\\s*(?<z>-?\\d+)\\s*\\)"
                    + "\\s+against\\s+" + TOWN + END,
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern CHUNK_LIBERATED = Pattern.compile(
            PREFIX + PLAYER + "\\s+liberated\\s+(?:a\\s+)?chunk\\s*"
                    + "\\(\\s*(?<x>-?\\d+)\\s*[,;]\\s*(?<z>-?\\d+)\\s*\\)"
                    + "\\s+from\\s+" + TOWN + END,
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern TERRITORY_CAPTURE = Pattern.compile(
            PREFIX + PLAYER + "\\s+captured\\s+(?:a\\s+)?territory\\s*"
                    + "\\(\\s*(?:id\\s*=\\s*)?(?<id>\\d+)\\s*\\)\\s+from\\s+" + TOWN + END,
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern TERRITORY_LIBERATED = Pattern.compile(
            PREFIX + PLAYER + "\\s+liberated\\s+(?:a\\s+)?territory\\s*"
                    + "\\(\\s*(?:id\\s*=\\s*)?(?<id>\\d+)\\s*\\)"
                    + "\\s+from\\s+" + TOWN + END,
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern TOWN_CONQUERED = Pattern.compile(
            "^\\s*(?<town>.+?)\\s+has\\s+been\\s+conquered\\s+by\\s+the\\s+alliance"
                    + "\\s+led\\s+by\\s+(?<leader>.+?)" + END,
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern TOWN_PLUNDERED = Pattern.compile(
            "^\\s*Town\\s+(?<town>.+?)\\s+has\\s+been\\s+plundered\\s+by\\s+the\\s+alliance"
                    + "\\s+led\\s+by\\s+(?<leader>.+?)" + END,
            Pattern.CASE_INSENSITIVE
    );

    public Optional<WarMessage> parse(String rawMessage) {
        if (rawMessage == null || rawMessage.isBlank()) {
            return Optional.empty();
        }
        String message = FORMATTING_CODE.matcher(rawMessage).replaceAll("").trim();
        Matcher attack = ATTACK.matcher(message);
        if (attack.matches()) {
            return Optional.of(new WarMessage.Attack(
                    clean(attack.group("player")),
                    clean(attack.group("town")),
                    parseInteger(attack, "x"),
                    parseInteger(attack, "y"),
                    parseInteger(attack, "z")
            ));
        }
        Matcher attackDefended = ATTACK_DEFENDED.matcher(message);
        if (attackDefended.matches()) {
            return Optional.of(new WarMessage.AttackDefended(
                    clean(attackDefended.group("player")),
                    parseInteger(attackDefended, "x"),
                    parseInteger(attackDefended, "y"),
                    parseInteger(attackDefended, "z")
            ));
        }
        Matcher flagBroken = FLAG_BROKEN.matcher(message);
        if (flagBroken.matches()) {
            return Optional.of(new WarMessage.AttackDefended(
                    clean(flagBroken.group("player")),
                    parseInteger(flagBroken, "x"),
                    parseInteger(flagBroken, "y"),
                    parseInteger(flagBroken, "z")
            ));
        }
        Matcher chunk = CHUNK_CAPTURE.matcher(message);
        if (chunk.matches()) {
            return Optional.of(new WarMessage.ChunkCaptured(
                    clean(chunk.group("player")),
                    clean(chunk.group("town")),
                    parseInteger(chunk, "x"),
                    parseInteger(chunk, "z")
            ));
        }
        Matcher chunkDefended = CHUNK_DEFENDED.matcher(message);
        if (chunkDefended.matches()) {
            return Optional.of(new WarMessage.ChunkDefended(
                    clean(chunkDefended.group("player")),
                    clean(chunkDefended.group("town")),
                    parseInteger(chunkDefended, "x"),
                    parseInteger(chunkDefended, "z")
            ));
        }
        Matcher chunkLiberated = CHUNK_LIBERATED.matcher(message);
        if (chunkLiberated.matches()) {
            return Optional.of(new WarMessage.ChunkLiberated(
                    clean(chunkLiberated.group("player")),
                    clean(chunkLiberated.group("town")),
                    parseInteger(chunkLiberated, "x"),
                    parseInteger(chunkLiberated, "z")
            ));
        }
        Matcher territory = TERRITORY_CAPTURE.matcher(message);
        if (territory.matches()) {
            return Optional.of(new WarMessage.TerritoryCaptured(
                    clean(territory.group("player")),
                    clean(territory.group("town")),
                    parseInteger(territory, "id")
            ));
        }
        Matcher territoryLiberated = TERRITORY_LIBERATED.matcher(message);
        if (territoryLiberated.matches()) {
            return Optional.of(new WarMessage.TerritoryLiberated(
                    clean(territoryLiberated.group("player")),
                    clean(territoryLiberated.group("town")),
                    parseInteger(territoryLiberated, "id")
            ));
        }
        return Optional.empty();
    }

    public Optional<TownWarOutcome> parseTownOutcome(String rawMessage) {
        if (rawMessage == null || rawMessage.isBlank()) {
            return Optional.empty();
        }
        String message = FORMATTING_CODE.matcher(rawMessage).replaceAll("").trim();
        Matcher conquered = TOWN_CONQUERED.matcher(message);
        if (conquered.matches()) {
            return Optional.of(new TownWarOutcome(
                    TownWarOutcome.Kind.CONQUERED,
                    clean(conquered.group("town")),
                    clean(conquered.group("leader"))
            ));
        }
        Matcher plundered = TOWN_PLUNDERED.matcher(message);
        if (plundered.matches()) {
            return Optional.of(new TownWarOutcome(
                    TownWarOutcome.Kind.PLUNDERED,
                    clean(plundered.group("town")),
                    clean(plundered.group("leader"))
            ));
        }
        return Optional.empty();
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim().replaceAll("[,;:.!?]+$", "");
    }

    private static int parseInteger(Matcher matcher, String group) {
        return Integer.parseInt(matcher.group(group));
    }
}
