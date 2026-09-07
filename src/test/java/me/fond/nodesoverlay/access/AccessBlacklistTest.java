package me.fond.nodesoverlay.access;

import me.fond.nodesoverlay.config.ServerSettings;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccessBlacklistTest {

    private static final UUID BLOCKED_UUID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void blocksConfiguredUuidWithoutMapData() {
        ServerSettings settings = new ServerSettings();
        settings.blacklistedUuids.add(BLOCKED_UUID.toString());
        settings.normalize();

        AccessBlacklist.Decision blocked = AccessBlacklist.evaluate(
                BLOCKED_UUID,
                "RenamedAccount",
                settings
        );

        assertTrue(blocked.denied());
        assertEquals(AccessBlacklist.Scope.UUID, blocked.scope());
        assertEquals(BLOCKED_UUID.toString(), blocked.matchedValue());
        assertEquals("RenamedAccount", blocked.playerName());
    }

    @Test
    void allowsUnlistedUuidImmediatelyWithoutTownsData() {
        UUID allowedUuid = UUID.fromString("00000000-0000-0000-0000-000000000002");

        AccessBlacklist.Decision decision = AccessBlacklist.evaluate(
                allowedUuid,
                "AllowedPlayer",
                new ServerSettings()
        );

        assertTrue(decision.allowed());
        assertEquals(allowedUuid.toString(), decision.playerUuid());
    }

    @Test
    void waitsOnlyUntilTheLocalSessionUuidExists() {
        AccessBlacklist.Decision decision = AccessBlacklist.evaluate(
                null,
                "AllowedPlayer",
                new ServerSettings()
        );

        assertEquals(AccessBlacklist.State.PENDING, decision.state());
        assertEquals("Checking the local Minecraft session UUID.", decision.message());
    }

    @Test
    void acceptsUndashedUuidEntriesAndIgnoresInvalidOnes() {
        ServerSettings settings = new ServerSettings();
        settings.blacklistedUuids.clear();
        settings.blacklistedUuids.add("00000000000000000000000000000001");
        settings.blacklistedUuids.add("not-a-uuid");

        AccessBlacklist.Decision decision = AccessBlacklist.evaluate(
                BLOCKED_UUID,
                "Player",
                settings
        );

        assertTrue(decision.denied());
        settings.normalize();
        assertEquals(1, settings.blacklistedUuids.size());
        assertTrue(settings.blacklistedUuids.contains(BLOCKED_UUID.toString()));
    }

    @Test
    void disabledProfileRemainsConfigurableWithoutBypassingEnabledBlacklist() {
        assertTrue(AccessBlacklist.canConfigure(
                false,
                AccessBlacklist.Decision.pending()
        ));
        assertFalse(AccessBlacklist.canConfigure(
                true,
                AccessBlacklist.Decision.pending()
        ));
        assertFalse(AccessBlacklist.canConfigure(
                true,
                AccessBlacklist.Decision.denied(
                        BLOCKED_UUID.toString(),
                        "Player",
                        BLOCKED_UUID.toString()
                )
        ));
        assertTrue(AccessBlacklist.canConfigure(
                true,
                AccessBlacklist.Decision.allowed(
                        "Player",
                        UUID.randomUUID().toString()
                )
        ));
    }
}
