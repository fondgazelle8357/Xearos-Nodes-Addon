package me.fond.nodesoverlay.war;

import java.util.List;

/**
 * Produces the exact chat lines used by the server-facing parser. Debug
 * commands display these locally and then feed them through the normal client
 * message handler, so parser and lifecycle behavior are tested together.
 */
public final class WarDebugMessages {

    private WarDebugMessages() {
    }

    public static String attack(
            String player,
            String town,
            int blockX,
            int blockY,
            int blockZ
    ) {
        return "[War] " + player + " is attacking " + town + " at ("
                + blockX + ", " + blockY + ", " + blockZ + ")";
    }

    public static String attackDefended(
            String defender,
            int blockX,
            int blockY,
            int blockZ
    ) {
        return "[War] Attack at (" + blockX + ", " + blockY + ", " + blockZ
                + ") defended by " + defender;
    }

    public static String chunkCaptured(
            String player,
            String town,
            int chunkX,
            int chunkZ
    ) {
        return "[War] " + player + " captured chunk (" + chunkX + ", " + chunkZ
                + ") from " + town + "!";
    }

    public static String chunkDefended(
            String player,
            String occupier,
            int chunkX,
            int chunkZ
    ) {
        return "[War] " + player + " defended chunk (" + chunkX + ", " + chunkZ
                + ") against " + occupier + "!";
    }

    public static String chunkLiberated(
            String player,
            String occupier,
            int chunkX,
            int chunkZ
    ) {
        return "[War] " + player + " liberated chunk (" + chunkX + ", " + chunkZ
                + ") from " + occupier + "!";
    }

    public static String territoryCaptured(
            String player,
            String town,
            int territoryId
    ) {
        return "[War] " + player + " captured territory (id=" + territoryId
                + ") from " + town + "!";
    }

    public static String territoryLiberated(
            String player,
            String occupier,
            int territoryId
    ) {
        return "[War] " + player + " liberated territory (id=" + territoryId
                + ") from " + occupier + "!";
    }

    public static List<TimedMessage> fullLifecycle(
            String player,
            String town,
            String occupier,
            int territoryId,
            int blockX,
            int blockY,
            int blockZ,
            int chunkX,
            int chunkZ,
            int stepSeconds
    ) {
        int step = Math.max(0, stepSeconds);
        return List.of(
                new TimedMessage(0, attack(player, town, blockX, blockY, blockZ)),
                new TimedMessage(step, attackDefended(player, blockX, blockY, blockZ)),
                new TimedMessage(step * 2, attack(player, town, blockX, blockY, blockZ)),
                new TimedMessage(step * 3, chunkCaptured(player, town, chunkX, chunkZ)),
                new TimedMessage(step * 4, chunkDefended(player, occupier, chunkX, chunkZ)),
                new TimedMessage(step * 5, chunkCaptured(player, town, chunkX, chunkZ)),
                new TimedMessage(step * 6, chunkLiberated(player, occupier, chunkX, chunkZ)),
                new TimedMessage(step * 7, attack(player, town, blockX, blockY, blockZ)),
                new TimedMessage(step * 8, territoryCaptured(player, town, territoryId)),
                new TimedMessage(step * 9, territoryLiberated(player, occupier, territoryId))
        );
    }

    public record TimedMessage(int delaySeconds, String text) {

        public TimedMessage {
            delaySeconds = Math.max(0, delaySeconds);
        }
    }
}
