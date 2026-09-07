package me.fond.nodesoverlay.access;

import me.fond.nodesoverlay.config.ServerSettings;

import java.util.Set;
import java.util.UUID;

/**
 * Applies the configured UUID deny list to the authenticated Minecraft
 * session. Access resolution is deliberately independent from map data so a
 * towns.json download, cache miss or stale town membership cannot change it.
 */
public final class AccessBlacklist {

    private AccessBlacklist() {
    }

    /**
     * A disabled profile must remain configurable so its activation control
     * can be turned back on. Once enabled, configuration is protected by the
     * same UUID decision as every other addon feature.
     */
    public static boolean canConfigure(boolean profileEnabled, Decision decision) {
        return !profileEnabled || decision != null && decision.allowed();
    }

    public static Decision evaluate(
            UUID playerUuid,
            String playerName,
            ServerSettings settings
    ) {
        String player = clean(playerName);
        if (playerUuid == null) {
            return Decision.pending();
        }

        String canonicalUuid = playerUuid.toString();
        String uuidMatch = matchUuid(settings.blacklistedUuids, playerUuid);
        if (uuidMatch != null) {
            return Decision.denied(uuidMatch, player, canonicalUuid);
        }
        return Decision.allowed(player, canonicalUuid);
    }

    private static String matchUuid(Set<String> blacklist, UUID candidate) {
        if (blacklist == null || candidate == null) {
            return null;
        }
        for (String entry : blacklist) {
            UUID parsed = parseUuid(entry);
            if (candidate.equals(parsed)) {
                return parsed.toString();
            }
        }
        return null;
    }

    private static UUID parseUuid(String value) {
        String cleaned = clean(value);
        if (cleaned.length() == 32) {
            cleaned = cleaned.substring(0, 8) + "-"
                    + cleaned.substring(8, 12) + "-"
                    + cleaned.substring(12, 16) + "-"
                    + cleaned.substring(16, 20) + "-"
                    + cleaned.substring(20);
        }
        try {
            return UUID.fromString(cleaned);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    public enum State {
        PENDING,
        ALLOWED,
        DENIED
    }

    public enum Scope {
        NONE,
        UUID
    }

    public record Decision(
            State state,
            Scope scope,
            String matchedValue,
            String playerName,
            String playerUuid
    ) {

        public Decision {
            state = state == null ? State.PENDING : state;
            scope = scope == null ? Scope.NONE : scope;
            matchedValue = clean(matchedValue);
            playerName = clean(playerName);
            playerUuid = clean(playerUuid);
        }

        public static Decision pending() {
            return new Decision(State.PENDING, Scope.NONE, "", "", "");
        }

        public static Decision allowed(String playerName, String playerUuid) {
            return new Decision(
                    State.ALLOWED,
                    Scope.NONE,
                    "",
                    playerName,
                    playerUuid
            );
        }

        public static Decision denied(
                String matchedUuid,
                String playerName,
                String playerUuid
        ) {
            return new Decision(
                    State.DENIED,
                    Scope.UUID,
                    matchedUuid,
                    playerName,
                    playerUuid
            );
        }

        public boolean allowed() {
            return state == State.ALLOWED;
        }

        public boolean denied() {
            return state == State.DENIED;
        }

        public String message() {
            if (state == State.PENDING) {
                return "Checking the local Minecraft session UUID.";
            }
            if (state == State.ALLOWED) {
                return "Nodes Overlay access allowed.";
            }
            return "Nodes Overlay is unavailable: blacklisted UUID "
                    + matchedValue + ".";
        }
    }
}
